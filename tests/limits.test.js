import assert from "node:assert/strict"
import { test } from "node:test"

import {
	ATTEMPT_MULTIPLIER,
	counterKey,
	evaluate,
	isSuccessfulPayload,
	nextRecord,
	remainingFor,
	replayKey,
	ruleFor,
} from "../proxy/limits.js"

const HOUR = 60 * 60 * 1000
const NOW = 1_000_000

/** Counter as the store would hold it after `used` successful calls. */
function spent(rule, used, { attempts = used, now = NOW } = {}) {
	return { used, attempts, resetAt: now + rule.windowMs }
}

test("every protected endpoint maps to the quota the product asks for", () => {
	const expected = [
		["/auth/v4/sessions", "session-open", 1, 24 * HOUR],
		["/auth/v4/credentialless", "session-upgrade", 1, 24 * HOUR],
		["/vpn/v2/logicals?WithState=true", "logicals", 1, 2 * HOUR],
		["/vpn/v1/loads", "loads", 3, 2 * HOUR],
		["/vpn/v1/certificate", "certificate", 3, 24 * HOUR],
	]

	for (const [pathname, id, limit, windowMs] of expected) {
		const rule = ruleFor(pathname)
		assert.ok(rule, `${pathname} should be rate limited`)
		assert.equal(rule.id, id)
		assert.equal(rule.limit, limit)
		assert.equal(rule.windowMs, windowMs)
	}
})

test("a guest login spends two separate quotas, not one shared counter", () => {
	// Both calls happen back to back during one login. Sharing a counter would
	// block the upgrade and replay the session response in its place.
	assert.notEqual(ruleFor("/auth/v4/sessions").id, ruleFor("/auth/v4/credentialless").id)
})

test("paths nobody has to be protected from are forwarded untouched", () => {
	assert.equal(ruleFor("/vpn/v2"), null)
	assert.equal(ruleFor("/core/v4/captcha"), null)
	assert.equal(ruleFor("/__proxy/health"), null)
})

test("a caller within quota is forwarded upstream", () => {
	const rule = ruleFor("/vpn/v1/loads")
	const records = [
		{ scope: "cookie", record: spent(rule, 2) },
		{ scope: "ip", record: spent(rule, 1) },
	]

	assert.equal(evaluate({ rule, records, now: NOW }).decision, "forward")
})

test("the strictest scope decides, so clearing cookies changes nothing", () => {
	const rule = ruleFor("/vpn/v1/loads")
	// A fresh cookie with the address already at its limit: the visitor opened a
	// private window, which is exactly what the second scope exists for.
	const records = [
		{ scope: "cookie", record: null },
		{ scope: "ip", record: spent(rule, 3) },
	]

	const { decision, blockedScope } = evaluate({ rule, records, now: NOW })
	assert.equal(decision, "replay")
	assert.equal(blockedScope, "ip")
})

test("going over quota replays instead of failing", () => {
	const rule = ruleFor("/vpn/v2/logicals")
	const records = [{ scope: "cookie", record: spent(rule, 1) }]

	const { decision, resetAt } = evaluate({ rule, records, now: NOW })
	assert.equal(decision, "replay")
	assert.equal(resetAt, NOW + 2 * HOUR, "the caller is told when the window ends")
})

test("an expired counter reads as no counter at all", () => {
	const rule = ruleFor("/vpn/v2/logicals")
	const stale = { used: 99, attempts: 99, resetAt: NOW - 1 }

	assert.equal(evaluate({ rule, records: [{ scope: "cookie", record: stale }], now: NOW }).decision, "forward")
})

test("a caller who only ever fails is eventually cut off", () => {
	const rule = ruleFor("/auth/v4/sessions")
	const hammering = { used: 0, attempts: rule.limit * ATTEMPT_MULTIPLIER, resetAt: NOW + rule.windowMs }

	const { decision, blockedScope } = evaluate({
		rule,
		records: [{ scope: "ip", record: hammering }],
		now: NOW,
	})
	assert.equal(decision, "reject", "failures are free, but not infinitely so")
	assert.equal(blockedScope, "ip")
})

test("a rejection outranks a replay: that caller is making no progress", () => {
	const rule = ruleFor("/auth/v4/sessions")
	const records = [
		{ scope: "cookie", record: spent(rule, 1) },
		{ scope: "ip", record: { used: 0, attempts: rule.limit * ATTEMPT_MULTIPLIER, resetAt: NOW + rule.windowMs } },
	]

	assert.equal(evaluate({ rule, records, now: NOW }).decision, "reject")
})

test("only a success spends the quota; a captcha merely counts as an attempt", () => {
	const rule = ruleFor("/auth/v4/sessions")

	const failed = nextRecord({ rule, record: null, now: NOW, success: false })
	assert.equal(failed.used, 0, "a visitor sent a captcha has not received a session")
	assert.equal(failed.attempts, 1)

	const succeeded = nextRecord({ rule, record: failed, now: NOW, success: true })
	assert.equal(succeeded.used, 1)
	assert.equal(succeeded.attempts, 2)
})

test("retrying inside a window does not push the window forward", () => {
	const rule = ruleFor("/vpn/v1/loads")
	const first = nextRecord({ rule, record: null, now: NOW, success: true })
	const second = nextRecord({ rule, record: first, now: NOW + HOUR, success: true })

	assert.equal(second.resetAt, first.resetAt, "the window is anchored to the first call")
})

test("the remaining figure reports the strictest scope", () => {
	const rule = ruleFor("/vpn/v1/loads")
	const records = [
		{ scope: "cookie", record: spent(rule, 1) },
		{ scope: "ip", record: spent(rule, 2) },
	]

	assert.equal(remainingFor({ rule, records, now: NOW }), 1)
	assert.equal(remainingFor({ rule, records: [], now: NOW }), rule.limit)
})

test("Proton's in-band failures are not mistaken for successes", () => {
	assert.equal(isSuccessfulPayload(200, JSON.stringify({ Code: 1000 })), true)
	// A human-verification challenge arrives as a perfectly healthy 200.
	assert.equal(isSuccessfulPayload(200, JSON.stringify({ Code: 9001 })), false)
	assert.equal(isSuccessfulPayload(429, JSON.stringify({ Code: 1000 })), false)
	assert.equal(isSuccessfulPayload(200, "not json"), false)
	assert.equal(isSuccessfulPayload(200, ""), false)
})

test("keys keep counters and replays apart, per rule and per caller", () => {
	assert.notEqual(counterKey("loads", "cookie", "a"), counterKey("loads", "ip", "a"))
	assert.notEqual(counterKey("loads", "cookie", "a"), counterKey("loads", "cookie", "b"))
	assert.notEqual(counterKey("loads", "cookie", "a"), replayKey("loads", "cookie", "a"))
})
