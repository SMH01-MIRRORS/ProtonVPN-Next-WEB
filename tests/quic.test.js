import assert from "node:assert/strict"
import { test } from "node:test"

import { clientHello, generateI1FromDomain, quicInitial, toHex, varint, __testing } from "../src/lib/quic.js"

/** Fixed "randomness" so a generated packet can be compared byte for byte. */
const deterministic = (buffer) => buffer.fill(7)

test("variable-length integers use the shortest QUIC encoding", () => {
	assert.equal(__testing.varintLength(0), 1)
	assert.equal(__testing.varintLength(63), 1)
	assert.equal(__testing.varintLength(64), 2)
	assert.equal(__testing.varintLength(16383), 2)
	assert.equal(__testing.varintLength(16384), 4)

	assert.equal(varint(0).length, 1)
	assert.equal(varint(64).length, 2)
	assert.equal(varint(16384).length, 4)
})

test("varints carry the two-bit length prefix QUIC readers expect", () => {
	assert.equal(varint(1)[0] >> 6, 0b00)
	assert.equal(varint(300)[0] >> 6, 0b01)
	assert.equal(varint(20000)[0] >> 6, 0b10)
})

test("hex output is lower case and two characters per byte", () => {
	assert.equal(toHex(new Uint8Array([0, 15, 16, 255])), "000f10ff")
})

test("the fake ClientHello carries the domain as an SNI", () => {
	const hello = clientHello("www.google.com", new Uint8Array(32).fill(7))
	const bytes = Buffer.from(hello)

	assert.equal(bytes[0], 0x01, "handshake type is client_hello")
	assert.ok(bytes.includes(Buffer.from("www.google.com", "ascii")), "the domain appears in the SNI extension")
})

test("a longer domain produces a longer handshake", () => {
	const short = clientHello("a.io", new Uint8Array(32).fill(7))
	const long = clientHello("a-much-longer-domain-name.example.com", new Uint8Array(32).fill(7))

	assert.ok(long.length > short.length)
})

test("the initial packet is a long-header QUIC packet", async () => {
	const packet = await quicInitial("www.google.com", deterministic)

	assert.equal(packet[0] & 0x80, 0x80, "long header form bit is set")
	assert.ok(packet.length >= 20, "a QUIC initial is padded to at least 20 bytes")
})

test("an I1 value is emitted in the <b 0x...> form the app writes", async () => {
	const i1 = await generateI1FromDomain("www.google.com", deterministic)

	assert.match(i1, /^<b 0x[0-9a-f]+>$/)
})

test("the same domain and randomness always give the same packet", async () => {
	const first = await generateI1FromDomain("www.google.com", deterministic)
	const second = await generateI1FromDomain("www.google.com", deterministic)

	assert.equal(first, second)
})

test("different domains give different packets", async () => {
	const google = await generateI1FromDomain("www.google.com", deterministic)
	const cloudflare = await generateI1FromDomain("www.cloudflare.com", deterministic)

	assert.notEqual(google, cloudflare)
})

test("real randomness makes two packets for one domain differ", async () => {
	const first = await generateI1FromDomain("www.google.com")
	const second = await generateI1FromDomain("www.google.com")

	assert.notEqual(first, second)
})
