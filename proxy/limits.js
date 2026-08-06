/**
 * Quota rules for the Proton API proxy.
 *
 * The generator is a public page in front of somebody else's rate limits, so a
 * visitor holding down "refresh" burns the quota for everyone. Counting in the
 * browser cannot fix that: anything the page enforces can be edited away in the
 * dev console, and a forged response is just as easy. So the proxy counts, and
 * the page is never asked to cooperate.
 *
 * Two properties matter and both are handled here rather than at the call site:
 *
 *  - Exceeding a quota is not an error. The proxy replays the caller's own last
 *    successful response, so a spammed button shows the same data instead of a
 *    failure. Nothing another visitor fetched is ever served: replays are keyed
 *    by identity.
 *  - Quotas are consumed on success only. Proton answers a login with a captcha
 *    challenge often enough that counting attempts would lock a visitor out of
 *    a session they never received. A separate, much looser attempt ceiling
 *    keeps that from becoming a spam channel.
 *
 * Plain JavaScript rather than TypeScript on purpose: the same file is imported
 * by the Deno server, by the Cloudflare Worker and by the node test suite.
 */

const HOUR_MS = 60 * 60 * 1000

/**
 * One entry per protected endpoint.
 *
 * `/auth/v4/sessions` and `/auth/v4/credentialless` are two rules rather than
 * one: a guest login calls them in sequence, and a shared counter would block
 * the second call and replay the first call's answer in its place.
 */
export const RULES = [
	{
		id: "session-open",
		limit: 1,
		windowMs: 24 * HOUR_MS,
		matches: (pathname) => pathname === "/auth/v4/sessions",
	},
	{
		id: "session-upgrade",
		limit: 1,
		windowMs: 24 * HOUR_MS,
		matches: (pathname) => pathname === "/auth/v4/credentialless",
	},
	{
		id: "logicals",
		limit: 1,
		windowMs: 2 * HOUR_MS,
		matches: (pathname) => pathname.startsWith("/vpn/v2/logicals"),
	},
	{
		id: "loads",
		limit: 3,
		windowMs: 2 * HOUR_MS,
		matches: (pathname) => pathname.startsWith("/vpn/v1/loads"),
	},
	{
		// A certificate is issued for a key, not for a server, and the page keeps
		// one for a day. A handful still has to get through: a cleared cache or an
		// expired certificate legitimately needs a new one.
		id: "certificate",
		limit: 3,
		windowMs: 24 * HOUR_MS,
		matches: (pathname) => pathname.startsWith("/vpn/v1/certificate"),
	},
]

/**
 * How many rejected calls a caller may make per window before the proxy stops
 * forwarding them at all.
 *
 * Failures do not consume the quota, so without a ceiling a script could hammer
 * a failing endpoint forever. A multiple of the quota leaves plenty of room for
 * Proton's captcha rotation, which is the only legitimate source of failures.
 */
export const ATTEMPT_MULTIPLIER = 8

/** The rule guarding a path, or null when the path is not rate limited. */
export function ruleFor(pathname) {
	return RULES.find((rule) => rule.matches(pathname)) ?? null
}

/**
 * A counter that has expired reads as absent, so a stale record left behind by
 * a store without precise expiry cannot keep a caller blocked.
 */
function activeRecord(record, now) {
	if (!record || typeof record !== "object") return null
	if (typeof record.resetAt !== "number" || record.resetAt <= now) return null
	return {
		used: typeof record.used === "number" ? record.used : 0,
		attempts: typeof record.attempts === "number" ? record.attempts : 0,
		resetAt: record.resetAt,
	}
}

/**
 * Decides what to do with one call, given the counters stored for it.
 *
 * @param options.rule    the matched rule
 * @param options.records counters for every scope the caller belongs to,
 *   as `{ scope, record }`; a caller is normally counted by cookie and by IP at
 *   the same time, and the strictest scope decides.
 * @returns `{ decision, resetAt, blockedScope }` where `decision` is
 *   `"forward"`, `"replay"` (quota spent, serve the caller's cached answer) or
 *   `"reject"` (attempt ceiling reached).
 */
export function evaluate({ rule, records = [], now = Date.now() }) {
	let decision = "forward"
	let blockedScope = null
	let resetAt = now + rule.windowMs

	for (const { scope, record } of records) {
		const active = activeRecord(record, now)
		if (!active) continue

		if (active.attempts >= rule.limit * ATTEMPT_MULTIPLIER) {
			// Reject wins outright: this caller is not making progress anyway.
			return { decision: "reject", resetAt: active.resetAt, blockedScope: scope }
		}

		if (active.used >= rule.limit && decision === "forward") {
			decision = "replay"
			blockedScope = scope
			resetAt = active.resetAt
		}
	}

	return { decision, resetAt, blockedScope }
}

/**
 * The counter to store after a call, given the one that was there.
 *
 * @param options.success whether the upstream call succeeded; only a success
 *   spends the quota, everything else merely counts as an attempt.
 */
export function nextRecord({ rule, record, now = Date.now(), success }) {
	const active = activeRecord(record, now)
	const base = active ?? { used: 0, attempts: 0, resetAt: now + rule.windowMs }

	return {
		used: base.used + (success ? 1 : 0),
		attempts: base.attempts + 1,
		resetAt: base.resetAt,
	}
}

/** Remaining quota for a caller, for the informational response header. */
export function remainingFor({ rule, records = [], now = Date.now() }) {
	let remaining = rule.limit
	for (const { record } of records) {
		const active = activeRecord(record, now)
		if (!active) continue
		remaining = Math.min(remaining, Math.max(0, rule.limit - active.used))
	}
	return remaining
}

/** Storage key for a counter. */
export function counterKey(ruleId, scope, identity) {
	return `count:${ruleId}:${scope}:${identity}`
}

/**
 * Storage key for a replayable response.
 *
 * Keyed by scope as well as identity: a visitor who clears cookies keeps their
 * IP, and the IP-scoped copy is what lets the proxy answer them without
 * spending a second guest session.
 */
export function replayKey(ruleId, scope, identity) {
	return `replay:${ruleId}:${scope}:${identity}`
}

/**
 * Whether an upstream answer counts as a success worth storing.
 *
 * Proton reports failures inside a 200 response as often as it does with a
 * status code, so both are checked; `Code` 1000 is its "OK".
 */
export function isSuccessfulPayload(status, body) {
	if (status < 200 || status >= 300) return false
	if (!body) return false
	try {
		const parsed = JSON.parse(body)
		return parsed?.Code === 1000
	} catch {
		return false
	}
}
