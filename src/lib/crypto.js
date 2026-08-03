/**
 * WireGuard key material, derived exactly the way the CLI does it
 * (`pvpn_cli/crypto.py`).
 *
 * Proton registers an Ed25519 public key and the WireGuard private key is the
 * clamped SHA-512 of that key's seed. Everything happens in the browser through
 * WebCrypto; the private key never leaves the page.
 */

export class Ed25519UnsupportedError extends Error {
	constructor() {
		super("Ed25519 is not available in this browser")
		this.name = "Ed25519UnsupportedError"
	}
}

function toBase64(bytes) {
	let binary = ""
	for (const byte of bytes) binary += String.fromCharCode(byte)
	return btoa(binary)
}

function toPem(spki) {
	const body = toBase64(new Uint8Array(spki))
	const lines = body.match(/.{1,64}/g) ?? []
	return ["-----BEGIN PUBLIC KEY-----", ...lines, "-----END PUBLIC KEY-----", ""].join("\n")
}

/**
 * Extracts the 32-byte seed from a PKCS#8 Ed25519 key.
 *
 * WebCrypto refuses to export a private key as raw bytes, but the PKCS#8
 * encoding of an Ed25519 key ends with an OCTET STRING holding the seed.
 */
function seedFromPkcs8(pkcs8) {
	const bytes = new Uint8Array(pkcs8)
	if (bytes.length < 34) throw new Error("Unexpected PKCS#8 length")
	return bytes.slice(bytes.length - 32)
}

/**
 * Generates a key pair.
 *
 * @returns {Promise<{wireGuardPrivateKey: string, publicKeyPem: string}>}
 *   `wireGuardPrivateKey` is base64 and belongs in the `.conf`;
 *   `publicKeyPem` is what gets sent to `/vpn/v1/certificate`.
 */
export async function generateVpnKeys() {
	let keyPair
	try {
		keyPair = await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"])
	} catch {
		throw new Ed25519UnsupportedError()
	}

	const pkcs8 = await crypto.subtle.exportKey("pkcs8", keyPair.privateKey)
	const spki = await crypto.subtle.exportKey("spki", keyPair.publicKey)

	const seed = seedFromPkcs8(pkcs8)
	const digest = new Uint8Array(await crypto.subtle.digest("SHA-512", seed))

	// Curve25519 clamping, identical to the CLI implementation.
	const secret = digest.slice(0, 32)
	secret[0] &= 248
	secret[31] &= 127
	secret[31] |= 64

	return {
		wireGuardPrivateKey: toBase64(secret),
		publicKeyPem: toPem(spki),
	}
}
