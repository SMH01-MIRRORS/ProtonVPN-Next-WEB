/**
 * Keeps a guest session, its server list and the account tier in the browser
 * for a day so that generating a second configuration does not mean sitting
 * through another anonymous login and another server fetch.
 *
 * Everything lives in `localStorage` under one key and is rewritten as a whole,
 * so a partially written or hand-edited entry is discarded rather than merged.
 * The stored access token is a throwaway guest credential, but it is still a
 * credential: it is scoped to this origin, expires with the cache, and is wiped
 * as soon as Proton rejects it.
 */

const STORAGE_KEY = "pvpn-next.generator.session.v1"

/** Proton guest sessions outlive this comfortably; a day is the product choice. */
export const CACHE_TTL_MS = 24 * 60 * 60 * 1000

/** Server load is the part that goes stale fastest, so it is refreshed sooner. */
export const LOADS_TTL_MS = 15 * 60 * 1000

function storage() {
	try {
		// Private windows and blocked-cookie setups throw on access rather than
		// returning null, and a generator that cannot cache is still usable.
		return globalThis.localStorage ?? null
	} catch {
		return null
	}
}

function isSession(value) {
	return (
		typeof value?.accessToken === "string" &&
		value.accessToken.length > 0 &&
		typeof value?.uid === "string" &&
		value.uid.length > 0
	)
}

/**
 * Reads the cache, returning null when it is missing, unreadable, written by an
 * older shape, or older than a day.
 */
export function loadCachedSession(now = Date.now()) {
	const store = storage()
	if (!store) return null

	let parsed
	try {
		const raw = store.getItem(STORAGE_KEY)
		if (!raw) return null
		parsed = JSON.parse(raw)
	} catch {
		return null
	}

	if (!parsed || typeof parsed !== "object") return null
	if (typeof parsed.createdAt !== "number") return null
	if (!isSession(parsed.session)) return null
	if (!Array.isArray(parsed.servers)) return null

	if (now - parsed.createdAt >= CACHE_TTL_MS) {
		clearCachedSession()
		return null
	}

	return {
		session: parsed.session,
		profile: parsed.profile ?? null,
		maxTier: typeof parsed.maxTier === "number" ? parsed.maxTier : 0,
		servers: parsed.servers,
		createdAt: parsed.createdAt,
		loadsUpdatedAt: typeof parsed.loadsUpdatedAt === "number" ? parsed.loadsUpdatedAt : parsed.createdAt,
		expiresAt: parsed.createdAt + CACHE_TTL_MS,
	}
}

/** Writes a freshly established session; resets the one-day clock. */
export function saveCachedSession({ session, profile, maxTier, servers }, now = Date.now()) {
	const store = storage()
	if (!store || !isSession(session)) return null

	const entry = {
		session: { accessToken: session.accessToken, uid: session.uid },
		profile: profile ?? null,
		maxTier: maxTier ?? 0,
		servers: servers ?? [],
		createdAt: now,
		loadsUpdatedAt: now,
	}

	try {
		store.setItem(STORAGE_KEY, JSON.stringify(entry))
	} catch {
		// A full or restricted quota should not break generation, only caching.
		return null
	}

	return { ...entry, expiresAt: now + CACHE_TTL_MS, loadsUpdatedAt: now }
}

/**
 * Replaces the server list without touching the session's expiry, so refreshing
 * load figures does not extend or shorten the cached day.
 */
export function updateCachedServers(servers, now = Date.now()) {
	const store = storage()
	const existing = loadCachedSession(now)
	if (!store || !existing) return null

	const entry = {
		session: existing.session,
		profile: existing.profile,
		maxTier: existing.maxTier,
		servers,
		createdAt: existing.createdAt,
		loadsUpdatedAt: now,
	}

	try {
		store.setItem(STORAGE_KEY, JSON.stringify(entry))
	} catch {
		return null
	}

	return { ...entry, expiresAt: existing.expiresAt }
}

export function clearCachedSession() {
	try {
		storage()?.removeItem(STORAGE_KEY)
	} catch {
		/* nothing to clean up if storage is unavailable */
	}
}

/** True when the cached load figures are old enough to be worth refetching. */
export function loadsAreStale(cache, now = Date.now()) {
	if (!cache) return true
	return now - cache.loadsUpdatedAt >= LOADS_TTL_MS
}

/** Whole hours left before the cached session is dropped, for the UI. */
export function hoursRemaining(cache, now = Date.now()) {
	if (!cache) return 0
	return Math.max(0, Math.round((cache.expiresAt - now) / (60 * 60 * 1000)))
}

export const __testing = { STORAGE_KEY }
