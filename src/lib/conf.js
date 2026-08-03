/**
 * `.conf` rendering, matching the file the CLI hands to its engine
 * (`pvpn_cli/cli/commands/connection.py`).
 *
 * The CLI appends an internal `---END---` delimiter for its own parser; an
 * exported file must not contain it, so it is omitted here.
 */

import { orderedAwgEntries } from "./awg.js"

/** The client address Proton assigns to every WireGuard peer. */
export const CLIENT_ADDRESS = "10.2.0.2/32"
export const DEFAULT_MTU = "1280"
export const DEFAULT_PORT = 51820

/** DNS choices; `labelKey` resolves through the translation files. */
export const DNS_PROFILES = [
	{ id: "proton", labelKey: "gen_dns_proton", servers: "10.2.0.1" },
	{ id: "cloudflare", labelKey: "gen_dns_cloudflare", servers: "1.1.1.1, 1.0.0.1" },
	{ id: "adguard", labelKey: "gen_dns_adguard", servers: "94.140.14.14, 94.140.15.15" },
	{ id: "google", labelKey: "gen_dns_google", servers: "8.8.8.8, 8.8.4.4" },
]

/** Ports Proton accepts for WireGuard; useful when 51820 is blocked. */
export const AVAILABLE_PORTS = [51820, 443, 88, 1224, 4569, 5060, 80]

export function dnsProfileById(id) {
	return DNS_PROFILES.find((profile) => profile.id === id) ?? DNS_PROFILES[0]
}

/**
 * Builds the configuration text.
 *
 * @param options.server prepared server from `servers.js`
 * @param options.privateKey base64 WireGuard private key
 * @param options.awgParams obfuscation parameters, possibly empty
 */
export function buildConfig({
	server,
	privateKey,
	awgParams = {},
	dnsId = "cloudflare",
	customDns = "",
	mtu = DEFAULT_MTU,
	port = DEFAULT_PORT,
	allowedIps = "0.0.0.0/0",
	keepalive = 25,
}) {
	const dns = customDns.trim() ? customDns.trim() : dnsProfileById(dnsId).servers

	const lines = ["[Interface]", `PrivateKey = ${privateKey}`, `Address = ${CLIENT_ADDRESS}`, `DNS = ${dns}`]

	if (mtu) lines.push(`MTU = ${mtu}`)

	for (const [key, value] of orderedAwgEntries(awgParams)) {
		lines.push(`${key} = ${value}`)
	}

	lines.push(
		"",
		"[Peer]",
		`PublicKey = ${server.publicKey}`,
		`Endpoint = ${server.entryIp}:${port || DEFAULT_PORT}`,
		`AllowedIPs = ${allowedIps}`,
		`PersistentKeepalive = ${keepalive}`,
	)

	return `${lines.join("\n")}\n`
}

/** A filesystem-safe name such as `pvpn-next-NL-FREE-1.conf`. */
export function configFileName(server) {
	const safeName = String(server.name ?? server.exitCountry).replace(/[^A-Za-z0-9_-]+/g, "-")
	return `pvpn-next-${safeName}.conf`
}

/** Triggers a browser download without ever putting the key on a server. */
export function downloadConfig(text, fileName) {
	const blob = new Blob([text], { type: "text/plain;charset=utf-8" })
	const url = URL.createObjectURL(blob)
	const link = document.createElement("a")
	link.href = url
	link.download = fileName
	document.body.append(link)
	link.click()
	link.remove()
	URL.revokeObjectURL(url)
}
