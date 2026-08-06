/**
 * Where the proxy keeps its quota counters and the responses it replays.
 *
 * The site runs on two hosts with very different storage, and the quota code
 * should not know which one it is on, so everything goes through one tiny
 * interface: `get(key)`, `put(key, value, ttlMs)`, `delete(key)`, values are
 * JSON, expiry is best-effort. Counters carry their own `resetAt`, so a store
 * that expires late cannot keep a caller blocked past the window.
 *
 * Backends, in the order each deployment prefers them:
 *
 *  - Deno KV, when the runtime offers it. Consistent across isolates and
 *    regions, which is what the 24-hour session quota needs.
 *  - A Cloudflare KV namespace, when one is bound as `QUOTA` in `wrangler.jsonc`.
 *  - The Cloudflare Cache API. Available with no configuration at all, shared
 *    between isolates in a colo but not between colos, so a determined visitor
 *    could get one extra session per region they reach. That is a far better
 *    failure mode than a quota that does not exist, and binding a KV namespace
 *    upgrades it away.
 *  - An in-process map, for local development and the test suite.
 */

/** Best-effort JSON store backed by a plain Map. */
export function memoryStore() {
	const entries = new Map()

	return {
		id: "memory",
		async get(key) {
			const entry = entries.get(key)
			if (!entry) return null
			if (entry.expiresAt <= Date.now()) {
				entries.delete(key)
				return null
			}
			return entry.value
		},
		async put(key, value, ttlMs) {
			entries.set(key, { value, expiresAt: Date.now() + ttlMs })
		},
		async delete(key) {
			entries.delete(key)
		},
	}
}

/** Deno KV, used by the Deno Deploy deployment. */
export function denoKvStore(kv) {
	return {
		id: "deno-kv",
		async get(key) {
			const entry = await kv.get(["pvpn-quota", key])
			return entry?.value ?? null
		},
		async put(key, value, ttlMs) {
			await kv.set(["pvpn-quota", key], value, { expireIn: ttlMs })
		},
		async delete(key) {
			await kv.delete(["pvpn-quota", key])
		},
	}
}

/** A bound Cloudflare KV namespace. */
export function workersKvStore(namespace) {
	return {
		id: "workers-kv",
		async get(key) {
			return await namespace.get(key, "json")
		},
		async put(key, value, ttlMs) {
			// Workers KV rejects anything under a minute, and every window here is
			// measured in hours, so the floor only matters in tests.
			const expirationTtl = Math.max(60, Math.ceil(ttlMs / 1000))
			await namespace.put(key, JSON.stringify(value), { expirationTtl })
		},
		async delete(key) {
			await namespace.delete(key)
		},
	}
}

/**
 * The Cloudflare Cache API dressed up as a key-value store.
 *
 * The cache is keyed by URL, so each entry is stored as a synthetic request on
 * a domain that is never resolved. `Cache-Control` carries the expiry, which is
 * why the entries do not need their own sweeping.
 */
export function cacheApiStore(cache) {
	const urlFor = (key) => "https://quota.pvpn-next.invalid/" + encodeURIComponent(key)

	return {
		id: "cache-api",
		async get(key) {
			const hit = await cache.match(new Request(urlFor(key)))
			if (!hit) return null
			try {
				return await hit.json()
			} catch {
				return null
			}
		},
		async put(key, value, ttlMs) {
			const seconds = Math.max(1, Math.ceil(ttlMs / 1000))
			await cache.put(
				new Request(urlFor(key)),
				new Response(JSON.stringify(value), {
					headers: {
						"content-type": "application/json",
						"cache-control": `max-age=${seconds}`,
					},
				}),
			)
		},
		async delete(key) {
			await cache.delete(new Request(urlFor(key)))
		},
	}
}

/**
 * Picks the best store available on Deno, falling back to memory when KV is
 * unavailable (an old runtime, or `deno run` without the unstable flag).
 */
export async function createDenoStore() {
	try {
		if (typeof Deno !== "undefined" && typeof Deno.openKv === "function") {
			return denoKvStore(await Deno.openKv())
		}
	} catch {
		/* fall through to the in-process store */
	}
	return memoryStore()
}

/** Picks the best store available to the Worker for this request. */
export function createWorkerStore(env) {
	if (env?.QUOTA && typeof env.QUOTA.get === "function") return workersKvStore(env.QUOTA)
	if (typeof caches !== "undefined" && caches.default) return cacheApiStore(caches.default)
	return memoryStore()
}
