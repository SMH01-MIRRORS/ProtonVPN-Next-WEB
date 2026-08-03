/**
 * Certificate registration, ported from `pvpn_cli/vpn.py:register_cert`.
 *
 * Registering the public key is what authorises it on the Proton infrastructure.
 * `Mode: "persistent"` requests the extended certificate, which stays valid far
 * longer than the default one and is what makes an exported `.conf` usable.
 */

import { apiCall } from "./api.js"

export async function registerCertificate({ profile, session, publicKeyPem, extended = true, signal }) {
	const body = { ClientPublicKey: publicKeyPem }
	if (extended) {
		// Only sent for the extended certificate; the CLI omits it otherwise.
		body.Mode = "persistent"
	}

	const payload = await apiCall("/vpn/v1/certificate", {
		method: "POST",
		profile,
		session,
		body,
		signal,
	})

	return {
		certificate: payload.Certificate ?? null,
		expirationTime: payload.ExpirationTime ?? null,
		refreshTime: payload.RefreshTime ?? null,
		serverPublicKeyMode: extended ? "persistent" : "session",
	}
}
