import assert from "node:assert/strict"
import { test } from "node:test"

import { openQuotaGate } from "../proxy/quota.js"
import { memoryStore } from "../proxy/store.js"

const SECRET = "test-secret"
const OK = JSON.stringify({ Code: 1000, LogicalServers: [{ ID: "a" }] })
const CAPTCHA = JSON.stringify({ Code: 9001, Details: {} })

/**
 * One visitor: the same address throughout, and the cookie the proxy issued on
 * the first call carried into every later one, the way a browser would.
 */
function visitor({ address = "203.0.113.7", cookie = null } = {}) {
	let jar = cookie

	return {
		get cookie() {
			return jar
		},
		request() {
			const headers = { "cf-connecting-ip": address }
			if (jar) headers.cookie = jar
			return new Request("https://example.invalid/api", { headers })
		},
		keep(setCookie) {
			if (setCookie) jar = setCookie.split(";")[0]
		},
	}
}

/** A full round trip: open the gate, and commit an upstream answer if allowed. */
async function call(store, who, pathname, answer = { status: 200, body: OK }) {
	const gate = await openQuotaGate(who.request(), pathname, { store, secret: SECRET })
	who.keep(gate.setCookie)
	if (!gate.blocked) await gate.commit(answer.status, answer.body)
	return gate
}

test("unprotected paths pass straight through", async () => {
	const store = memoryStore()
	const gate = await openQuotaGate(visitor().request(), "/vpn/v2", { store, secret: SECRET })

	assert.equal(gate.rule, null)
	assert.equal(gate.blocked, null)
	assert.deepEqual(gate.headers, {})
})

test("a first call is forwarded and reports what is left", async () => {
	const store = memoryStore()
	const gate = await call(store, visitor(), "/vpn/v1/loads")

	assert.equal(gate.blocked, null)
	assert.equal(gate.headers["x-pvpn-quota"], "loads")
	assert.equal(gate.headers["x-pvpn-quota-limit"], "3")
	assert.equal(gate.headers["x-pvpn-quota-remaining"], "3")
})

test("a spammed button gets the same data back instead of an error", async () => {
	const store = memoryStore()
	const who = visitor()

	await call(store, who, "/vpn/v2/logicals")
	const second = await call(store, who, "/vpn/v2/logicals")

	assert.equal(second.blocked.status, 200)
	assert.equal(second.blocked.body, OK, "the caller's own last answer, replayed")
	assert.equal(second.headers["x-pvpn-quota-state"], "replayed")
	assert.equal(second.headers["x-pvpn-quota-remaining"], "0")
})

test("the quota holds when the caller throws their cookie away", async () => {
	const store = memoryStore()
	const first = visitor()
	await call(store, first, "/vpn/v2/logicals")

	// Same address, no cookie: a private window, or the dev console clearing it.
	const reset = visitor()
	const gate = await call(store, reset, "/vpn/v2/logicals")

	assert.ok(gate.blocked, "a new cookie does not buy a new quota")
	assert.equal(gate.blocked.body, OK, "answered from the address-scoped copy")
})

test("one visitor is never served another visitor's data", async () => {
	const store = memoryStore()
	const mine = JSON.stringify({ Code: 1000, mine: true })

	await call(store, visitor({ address: "203.0.113.7" }), "/vpn/v2/logicals", { status: 200, body: mine })
	const other = await call(store, visitor({ address: "198.51.100.4" }), "/vpn/v2/logicals")

	assert.equal(other.blocked, null, "a different visitor has their own quota")
})

test("a captcha does not cost the session a visitor never received", async () => {
	const store = memoryStore()
	const who = visitor()

	await call(store, who, "/auth/v4/sessions", { status: 200, body: CAPTCHA })
	const retry = await call(store, who, "/auth/v4/sessions")

	assert.equal(retry.blocked, null, "the retry after a challenge must get through")
	assert.equal(retry.headers["x-pvpn-quota-remaining"], "1")
})

test("a failure is never stored as something to replay", async () => {
	const store = memoryStore()
	const who = visitor()

	await call(store, who, "/auth/v4/sessions", { status: 200, body: CAPTCHA })
	await call(store, who, "/auth/v4/sessions")
	const third = await call(store, who, "/auth/v4/sessions")

	assert.equal(third.blocked.body, OK, "the good answer, not the challenge")
})

test("a caller with nothing cached is told to come back later", async () => {
	const store = memoryStore()
	const who = visitor()

	// Three successful certificate calls spend the day's quota.
	for (let index = 0; index < 3; index += 1) {
		await call(store, who, "/vpn/v1/certificate")
	}
	// A different key would need a different certificate, but the quota is gone.
	const gate = await call(store, who, "/vpn/v1/certificate")

	assert.equal(gate.headers["x-pvpn-quota-state"], "replayed")

	const bare = memoryStore()
	await bare.put("count:certificate:cookie:x", { used: 9, attempts: 9, resetAt: Date.now() + 60_000 }, 60_000)
	const fresh = await openQuotaGate(visitor().request(), "/vpn/v1/certificate", { store: bare, secret: SECRET })
	assert.equal(fresh.blocked, null, "another caller's counter is not this caller's")
})

test("the rate-limit body says when to come back and nothing more", async () => {
	const store = memoryStore()
	const who = visitor()

	// Hammer without ever succeeding until the attempt ceiling is reached.
	let last = null
	for (let index = 0; index < 12; index += 1) {
		last = await call(store, who, "/auth/v4/credentialless", { status: 422, body: CAPTCHA })
	}

	assert.equal(last.headers["x-pvpn-quota-state"], "rejected")
	assert.equal(last.blocked.status, 429)

	const payload = JSON.parse(last.blocked.body)
	assert.equal(payload.Code, 0)
	assert.ok(payload.RetryAfterMinutes > 0)
})

test("without a store the proxy still works, it just cannot count", async () => {
	const gate = await openQuotaGate(visitor().request(), "/vpn/v2/logicals", { secret: SECRET })

	assert.equal(gate.rule, null)
	assert.equal(gate.blocked, null)
	assert.ok(gate.setCookie, "the identity is still established for later")
})
