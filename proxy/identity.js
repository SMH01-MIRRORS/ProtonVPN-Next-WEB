/**
 * Who the proxy is counting.
 *
 * Two identities are tracked for every call and both are derived on the server:
 *
 *  - a cookie the proxy issues and signs. The page never sets it, cannot read
 *    it (`HttpOnly`) and cannot forge one: an edited value fails the signature
 *    check and is treated as no cookie at all.
 *  - the caller's IP address, hashed with the same secret so the store never
 *    holds a raw address.
 *
 * Clearing cookies therefore does not reset a quota, and neither does a private
 * window: the IP-scoped counter survives both. Sharing an IP with a whole
 * office is the cost of that, which is why the quotas are per-endpoint and
 * generous enough for real use, and why exceeding one replays a cached answer
 * instead of failing.
 */

const COOKIE_NAME = "pvpn_quota"
const COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60

/** Scope names used in storage keys and in the diagnostics header. */
export const SCOPE_COOKIE = "cookie"
export const SCOPE_IP = "ip"

const encoder = new TextEncoder()

function toBase64Url(bytes) {
	let binary = ""
	for (const byte of new Uint8Array(bytes)) binary += String.fromCharCode(byte)
	return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

async function hmac(secret, message) {
	const key = await crypto.subtle.importKey(
		"raw",
		encoder.encode(secret),
		{ name: "HMAC", hash: "SHA-256" },
		false,
		["sign"],
	)
	return toBase64Url(await crypto.subtle.sign("HMAC", key, encoder.encode(message)))
}

/** Constant-time comparison, so a signature cannot be guessed byte by byte. */
function equals(first, second) {
	if (first.length !== second.length) return false
	let difference = 0
	for (let index = 0; index < first.length; index += 1) {
		difference |= first.charCodeAt(index) ^ second.charCodeAt(index)
	}
	return difference === 0
}

export function parseCookies(header) {
	const jar = new Map()
	for (const part of String(header ?? "").split(";")) {
		const separator = part.indexOf("=")
		if (separator < 1) continue
		jar.set(part.slice(0, separator).trim(), part.slice(separator + 1).trim())
	}
	return jar
}

/**
 * Reads the signed cookie.
 *
 * @returns the identifier it carries, or null when it is missing, malformed or
 *   signed with anything other than this deployment's secret.
 */
export async function readSignedCookie(header, secret) {
	const raw = parseCookies(header).get(COOKIE_NAME)
	if (!raw) return null

	const separator = raw.lastIndexOf(".")
	if (separator < 1) return null

	const id = raw.slice(0, separator)
	const signature = raw.slice(separator + 1)
	if (!/^[A-Za-z0-9_-]{16,64}$/.test(id)) return null

	return equals(await hmac(secret, id), signature) ? id : null
}

/** A fresh random identifier, signed so the browser cannot alter it. */
export async function issueCookie(secret) {
	const bytes = new Uint8Array(16)
	crypto.getRandomValues(bytes)
	const id = toBase64Url(bytes)

	return {
		id,
		header: [
			`${COOKIE_NAME}=${id}.${await hmac(secret, id)}`,
			"Path=/",
			`Max-Age=${COOKIE_MAX_AGE_SECONDS}`,
			// HttpOnly is the point: the generator has no reason to read this, and
			// script access is exactly how a client-side workaround would start.
			"HttpOnly",
			"Secure",
			"SameSite=Lax",
		].join("; "),
	}
}

/**
 * The caller's address.
 *
 * `cf-connecting-ip` is set by Cloudflare and cannot be spoofed from outside;
 * the others are checked for the Deno deployment and for local development.
 * A forwarded chain lists the client first, so only the first entry is used.
 */
export function clientAddress(request, fallback = "") {
	const direct = request.headers.get("cf-connecting-ip") ?? request.headers.get("x-real-ip")
	if (direct) return direct.trim()

	const forwarded = request.headers.get("x-forwarded-for")
	if (forwarded) return forwarded.split(",")[0].trim()

	return fallback
}

/** Hashed address, so the quota store never holds a raw IP. */
export async function addressIdentity(address, secret) {
	if (!address) return null
	return (await hmac(secret, `ip:${address}`)).slice(0, 32)
}

/**
 * Resolves both identities for a request, issuing a cookie when there is none
 * that verifies.
 *
 * @returns `{ scopes, setCookie }` where `scopes` is the list the quota code
 *   counts against and `setCookie` is a header value to attach, if any.
 */
export async function resolveIdentity(request, { secret, address = "" } = {}) {
	const existing = await readSignedCookie(request.headers.get("cookie"), secret)
	const issued = existing ? null : await issueCookie(secret)
	const cookieId = existing ?? issued.id

	const scopes = [{ scope: SCOPE_COOKIE, identity: cookieId }]

	const hashedAddress = await addressIdentity(clientAddress(request, address), secret)
	if (hashedAddress) scopes.push({ scope: SCOPE_IP, identity: hashedAddress })

	return { scopes, setCookie: issued?.header ?? null }
}
