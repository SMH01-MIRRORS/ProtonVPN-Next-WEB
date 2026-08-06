import assert from "node:assert/strict"
import { test } from "node:test"

import {
	addressIdentity,
	clientAddress,
	issueCookie,
	parseCookies,
	readSignedCookie,
	resolveIdentity,
	SCOPE_COOKIE,
	SCOPE_IP,
} from "../proxy/identity.js"

const SECRET = "test-secret"

/** A request the way the proxy receives one, with the headers under test. */
function request(headers = {}) {
	return new Request("https://example.invalid/api/vpn/v1/loads", { headers })
}

test("a cookie jar is read the way a browser writes it", () => {
	const jar = parseCookies("a=1; pvpn_quota=id.sig; empty=")

	assert.equal(jar.get("a"), "1")
	assert.equal(jar.get("pvpn_quota"), "id.sig")
	assert.equal(jar.get("empty"), "")
	assert.equal(parseCookies(undefined).size, 0)
})

test("the issued cookie cannot be read or replayed by the page", async () => {
	const { header } = await issueCookie(SECRET)

	assert.match(header, /^pvpn_quota=/)
	assert.ok(header.includes("HttpOnly"), "script access is how a workaround would start")
	assert.ok(header.includes("Secure"))
	assert.ok(header.includes("SameSite=Lax"))
	assert.ok(header.includes("Path=/"))
})

test("a cookie the proxy issued verifies on the way back", async () => {
	const { id, header } = await issueCookie(SECRET)
	const value = header.split(";")[0].slice("pvpn_quota=".length)

	assert.equal(await readSignedCookie(`pvpn_quota=${value}`, SECRET), id)
})

test("an edited cookie is treated as no cookie at all", async () => {
	const { id, header } = await issueCookie(SECRET)
	const value = header.split(";")[0].slice("pvpn_quota=".length)
	const signature = value.slice(value.lastIndexOf(".") + 1)

	// Somebody in the dev console trying to look like a different visitor.
	assert.equal(await readSignedCookie(`pvpn_quota=AAAAAAAAAAAAAAAAAAAA.${signature}`, SECRET), null)
	assert.equal(await readSignedCookie(`pvpn_quota=${id}.forged`, SECRET), null)
	assert.equal(await readSignedCookie(`pvpn_quota=${value}`, "another-secret"), null)
	assert.equal(await readSignedCookie("pvpn_quota=no-signature", SECRET), null)
	assert.equal(await readSignedCookie("", SECRET), null)
})

test("the address comes from the header the platform controls", () => {
	assert.equal(clientAddress(request({ "cf-connecting-ip": "203.0.113.7" })), "203.0.113.7")
	assert.equal(clientAddress(request({ "x-real-ip": "203.0.113.8" })), "203.0.113.8")
	// A forwarded chain lists the client first; the rest are proxies.
	assert.equal(clientAddress(request({ "x-forwarded-for": "203.0.113.9, 70.41.3.18" })), "203.0.113.9")
	assert.equal(clientAddress(request(), "127.0.0.1"), "127.0.0.1")
})

test("Cloudflare's header wins over one the caller can set", () => {
	const spoofed = request({ "cf-connecting-ip": "203.0.113.7", "x-forwarded-for": "198.51.100.1" })
	assert.equal(clientAddress(spoofed), "203.0.113.7")
})

test("the store never holds a raw address", async () => {
	const hashed = await addressIdentity("203.0.113.7", SECRET)

	assert.equal(hashed.length, 32)
	assert.ok(!hashed.includes("203.0.113.7"))
	assert.equal(hashed, await addressIdentity("203.0.113.7", SECRET), "stable for one visitor")
	assert.notEqual(hashed, await addressIdentity("203.0.113.8", SECRET))
	assert.equal(await addressIdentity("", SECRET), null)
})

test("a first visit is given a cookie and counted by address too", async () => {
	const { scopes, setCookie } = await resolveIdentity(request({ "cf-connecting-ip": "203.0.113.7" }), {
		secret: SECRET,
	})

	assert.ok(setCookie, "the visitor needs an identity to be counted by")
	assert.deepEqual(
		scopes.map((entry) => entry.scope),
		[SCOPE_COOKIE, SCOPE_IP],
	)
})

test("a returning visitor keeps the identity they were given", async () => {
	const first = await resolveIdentity(request({ "cf-connecting-ip": "203.0.113.7" }), { secret: SECRET })
	const cookie = first.setCookie.split(";")[0]

	const second = await resolveIdentity(request({ cookie, "cf-connecting-ip": "203.0.113.7" }), { secret: SECRET })

	assert.equal(second.setCookie, null, "no need to reissue one that verifies")
	assert.equal(second.scopes[0].identity, first.scopes[0].identity)
})

test("a forged cookie earns a fresh one rather than a free quota", async () => {
	const { scopes, setCookie } = await resolveIdentity(request({ cookie: "pvpn_quota=made.up" }), { secret: SECRET })

	assert.ok(setCookie)
	assert.notEqual(scopes[0].identity, "made")
})

test("an unknown address leaves the cookie scope to do the counting", async () => {
	const { scopes } = await resolveIdentity(request(), { secret: SECRET })

	assert.deepEqual(
		scopes.map((entry) => entry.scope),
		[SCOPE_COOKIE],
	)
})
