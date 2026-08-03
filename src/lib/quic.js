/**
 * Builds an AmneziaWG `I1` packet that mimics a QUIC Initial carrying a TLS
 * ClientHello for a chosen domain, ported from the Android client
 * (`utils/crypto/QuicI1Generator.kt`) and the CLI (`pvpn_cli/crypto.py`).
 *
 * A DPI box that inspects the first UDP packet of the handshake sees what looks
 * like the start of an HTTP/3 connection to that domain. The packet is only a
 * decoy: nothing ever answers it, so the domain choice only matters for how
 * unremarkable it looks on the network being crossed.
 *
 * The byte layout must match the other clients exactly, so the structure below
 * follows the same order and the same deliberate simplifications (an empty
 * source connection id, an empty token, a one-byte packet number).
 */

const QUIC_SALT = new Uint8Array([
	0x38, 0x76, 0x2c, 0xf7, 0xf5, 0x59, 0x34, 0xb3, 0x4d, 0x17, 0x9a, 0xe6, 0xa4, 0xc8, 0x0c, 0xad,
	0xcc, 0xbb, 0x7f, 0x0a,
])

const TAG_LENGTH = 16

export class DomainI1UnsupportedError extends Error {
	constructor() {
		super("WebCrypto is unavailable in this context")
		this.name = "DomainI1UnsupportedError"
	}
}

function crypto() {
	const subtle = globalThis.crypto?.subtle
	if (!subtle) throw new DomainI1UnsupportedError()
	return subtle
}

function concat(...chunks) {
	const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0)
	const output = new Uint8Array(total)
	let offset = 0
	for (const chunk of chunks) {
		output.set(chunk, offset)
		offset += chunk.length
	}
	return output
}

function uint16(value) {
	return new Uint8Array([(value >> 8) & 0xff, value & 0xff])
}

function uint24(value) {
	return new Uint8Array([(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff])
}

export function toHex(bytes) {
	return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("")
}

/** QUIC variable-length integer encoding (RFC 9000 §16). */
export function varint(value) {
	if (value < 0x40) return new Uint8Array([value])
	if (value < 0x4000) return uint16(value | 0x4000)
	if (value < 0x40000000) {
		const tagged = (value | 0x80000000) >>> 0
		return new Uint8Array([(tagged >>> 24) & 0xff, (tagged >>> 16) & 0xff, (tagged >>> 8) & 0xff, tagged & 0xff])
	}
	throw new RangeError("varint too large for a decoy packet")
}

function varintLength(value) {
	if (value < 0x40) return 1
	if (value < 0x4000) return 2
	if (value < 0x40000000) return 4
	return 8
}

/** A TLS ClientHello carrying nothing but the server name extension. */
export function clientHello(domain, randomBytes) {
	const name = new TextEncoder().encode(domain)

	// server_name extension: list length, name type 0, then the host name.
	const serverNameList = concat(uint16(name.length + 3), new Uint8Array([0]), uint16(name.length), name)
	const sniExtension = concat(uint16(0), uint16(serverNameList.length), serverNameList)
	const extensions = concat(uint16(sniExtension.length), sniExtension)

	// Legacy version, client random, then empty session id / cipher suites /
	// compression methods. The four zero bytes cover all three, exactly as the
	// other clients write them.
	const body = concat(new Uint8Array([0x03, 0x03]), randomBytes, new Uint8Array([0, 0, 0, 0]), extensions)

	return concat(new Uint8Array([0x01]), uint24(body.length), body)
}

function cryptoFrame(data, offset = 0) {
	return concat(new Uint8Array([0x06]), varint(offset), varint(data.length), data)
}

async function hmacSha256(key, data) {
	const handle = await crypto().importKey("raw", key, { name: "HMAC", hash: "SHA-256" }, false, ["sign"])
	return new Uint8Array(await crypto().sign("HMAC", handle, data))
}

/**
 * The HKDF-Expand-Label shape used by the other clients: a single HMAC block
 * truncated to the requested length. Real HKDF would iterate, but every label
 * here asks for 32 bytes or fewer, so one block is all that is ever produced.
 */
async function deriveSecret(key, length, label) {
	const labelBytes = new TextEncoder().encode(`tls13 ${label}`)
	const info = concat(uint16(length), new Uint8Array([labelBytes.length]), labelBytes, new Uint8Array([0x00, 0x01]))
	return (await hmacSha256(key, info)).slice(0, length)
}

/**
 * Encrypts one AES block with the raw block cipher. WebCrypto has no ECB mode,
 * so a single-block CBC encryption with a zero IV is used: for the first block
 * CBC XORs the IV into the plaintext, which with a zero IV is the block cipher
 * itself. CBC appends a padding block, which is discarded.
 */
async function aesBlock(key, block) {
	const handle = await crypto().importKey("raw", key, { name: "AES-CBC" }, false, ["encrypt"])
	const encrypted = await crypto().encrypt({ name: "AES-CBC", iv: new Uint8Array(16) }, handle, block)
	return new Uint8Array(encrypted).slice(0, 16)
}

async function aesGcmEncrypt(key, nonce, plaintext, additionalData) {
	const handle = await crypto().importKey("raw", key, { name: "AES-GCM" }, false, ["encrypt"])
	const encrypted = await crypto().encrypt(
		{ name: "AES-GCM", iv: nonce, additionalData, tagLength: TAG_LENGTH * 8 },
		handle,
		plaintext,
	)
	return new Uint8Array(encrypted)
}

/**
 * Assembles and protects the Initial packet.
 *
 * @param randomSource injected so tests can pin the output; production passes
 *   `crypto.getRandomValues`.
 */
export async function quicInitial(domain, randomSource) {
	const dcid = randomSource(new Uint8Array(1))
	const scid = new Uint8Array(0)
	const token = new Uint8Array(0)
	const packetNumber = new Uint8Array([0])

	const payload = cryptoFrame(clientHello(domain, randomSource(new Uint8Array(32))))

	// The AEAD needs at least a full sample to protect the header. A real
	// ClientHello is far longer than that, so this only guards degenerate input.
	let padding = 0
	if (packetNumber.length + payload.length + TAG_LENGTH < 20) {
		padding = 20 - packetNumber.length - payload.length - TAG_LENGTH
	}

	const lengthField = varint(packetNumber.length + payload.length + padding + TAG_LENGTH)
	const header = concat(
		new Uint8Array([0xc0 | (packetNumber.length - 1)]),
		new Uint8Array([0, 0, 0, 1]), // QUIC version 1
		new Uint8Array([dcid.length]),
		dcid,
		new Uint8Array([scid.length]),
		scid,
		new Uint8Array([token.length]),
		token,
		lengthField,
		packetNumber,
	)

	const initialSecret = await hmacSha256(QUIC_SALT, dcid)
	const clientSecret = await deriveSecret(initialSecret, 32, "client in")
	const key = await deriveSecret(clientSecret, 16, "quic key")
	const iv = await deriveSecret(clientSecret, 12, "quic iv")
	const headerProtection = await deriveSecret(clientSecret, 16, "quic hp")

	const nonce = iv.slice()
	for (let index = 0; index < packetNumber.length; index += 1) {
		nonce[nonce.length - 1 - index] ^= packetNumber[packetNumber.length - 1 - index]
	}

	const encrypted = await aesGcmEncrypt(key, nonce, concat(payload, new Uint8Array(padding)), header)

	// Header protection samples the ciphertext at a fixed offset past the packet
	// number, then masks the first byte's low bits and the packet number itself.
	const sample = encrypted.slice(4 - packetNumber.length, 20 - packetNumber.length)
	const mask = await aesBlock(headerProtection, sample)

	const protectedHeader = header.slice()
	protectedHeader[0] ^= mask[0] & 0x0f
	const packetNumberOffset = header.length - packetNumber.length
	for (let index = 0; index < packetNumber.length; index += 1) {
		protectedHeader[packetNumberOffset + index] ^= mask[1 + index]
	}

	return concat(protectedHeader, encrypted)
}

/** Returns the `<b 0x…>` literal an AmneziaWG configuration expects for `I1`. */
export async function generateI1FromDomain(domain, randomSource = (buffer) => globalThis.crypto.getRandomValues(buffer)) {
	const trimmed = (domain ?? "").trim()
	if (!trimmed) throw new RangeError("a domain is required")

	const packet = await quicInitial(trimmed, randomSource)
	return `<b 0x${toHex(packet)}>`
}

export const __testing = { varintLength }
