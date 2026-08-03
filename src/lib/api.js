/**
 * Proton API access for the browser.
 *
 * The API cannot be called directly from a page (no CORS headers on
 * vpn-api.proton.me), so every request goes through one of the proxies the CLI
 * already uses. Cloudflare is deliberately not in this list: Proton throttles it
 * much more aggressively than the other two. Deployable sources for both proxies
 * live in `proxy/` on the `protonvpn-next-dev` branch, which is also where the
 * Android client's copy of these URLs lives; they must answer CORS preflight.
 */

import { baseHeaders } from "./spoof.js"

// Tried in order until one answers. Deno Deploy is the only entry: it is wired
// to the repository and, unlike Netlify, is reachable from Russia, where most
// of the users are.
export const API_ENDPOINTS = [
	{ id: "deno", url: "https://protonvpn-next-mirror.smh01-mirrors.deno.net" },
]

/** Proton API codes that mean "prove you are human". */
export const CODE_HUMAN_VERIFICATION = 9001
export const CODE_CAPTCHA_EXPIRED = 12087
export const CODE_OK = 1000

export class ApiError extends Error {
	constructor(message, { code = null, status = null, body = null } = {}) {
		super(message)
		this.name = "ApiError"
		this.code = code
		this.status = status
		this.body = body
	}

	/** True when the API wants human verification, which triggers a profile swap. */
	get needsVerification() {
		return this.code === CODE_HUMAN_VERIFICATION || this.code === CODE_CAPTCHA_EXPIRED
	}
}

/** Raised when no proxy could be reached at all. */
export class ProxyUnreachableError extends Error {
	constructor(failures) {
		super("No API proxy could be reached")
		this.name = "ProxyUnreachableError"
		this.failures = failures
	}
}

/**
 * Performs one API call, trying each proxy in order.
 *
 * Only transport failures fall through to the next proxy. A valid API response,
 * including an error payload, is returned as-is so the caller can react to the
 * Proton error code.
 */
export async function apiRequest(path, { method = "GET", profile, session = null, body = null, extraHeaders = {}, signal } = {}) {
	const headers = { ...baseHeaders(profile), ...extraHeaders }

	if (session?.accessToken) {
		headers.Authorization = `Bearer ${session.accessToken}`
	}
	if (session?.uid) {
		headers["x-pm-uid"] = session.uid
	}

	const failures = []

	for (const endpoint of API_ENDPOINTS) {
		let response
		try {
			response = await fetch(`${endpoint.url}${path}`, {
				method,
				headers,
				body: body === null ? undefined : JSON.stringify(body),
				signal,
				credentials: "omit",
				mode: "cors",
			})
		} catch (error) {
			if (error?.name === "AbortError") throw error
			// A CORS rejection is indistinguishable from a network failure here, so
			// both simply move on to the next proxy.
			failures.push({ endpoint: endpoint.id, reason: String(error) })
			continue
		}

		let payload = null
		const raw = await response.text()
		try {
			payload = raw ? JSON.parse(raw) : {}
		} catch {
			failures.push({ endpoint: endpoint.id, reason: `Malformed response (HTTP ${response.status})` })
			continue
		}

		return { payload, status: response.status, endpoint: endpoint.id }
	}

	throw new ProxyUnreachableError(failures)
}

/** Same as `apiRequest`, but turns a non-1000 API code into an `ApiError`. */
export async function apiCall(path, options) {
	const { payload, status } = await apiRequest(path, options)

	if (payload?.Code !== CODE_OK) {
		throw new ApiError(payload?.Error || `API error (HTTP ${status})`, {
			code: payload?.Code ?? null,
			status,
			body: payload,
		})
	}

	return payload
}
