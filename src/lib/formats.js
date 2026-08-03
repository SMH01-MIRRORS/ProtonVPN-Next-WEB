/**
 * Client formats.
 *
 * The same tunnel can be written three ways and every popular client wants its
 * own dialect:
 *
 *  - `amneziawg` — the `.conf` the Android app and the AmneziaWG clients read,
 *    rendered by `conf.js`.
 *  - `wiresock`  — the Windows client. It cannot take a raw `I1` packet, but it
 *    can build one itself from a domain, hence `Id`/`Ip`/`Ib` instead.
 *  - `clash`     — a YAML document (mihomo/Clash.Meta) where the obfuscation
 *    lives under `amnezia-wg-option` with lower-case keys.
 *
 * Everything shared — addresses, DNS, allowed IPs — comes from `conf.js`, so a
 * setting changed in the UI reaches all three formats.
 */

import {
	DEFAULT_MTU,
	DEFAULT_PORT,
	CLIENT_ADDRESS,
	CLIENT_ADDRESS_V6,
	buildConfig,
	interfaceAddress,
	resolveDns,
	splitList,
} from "./conf.js"

/** Formats offered in the UI. `bundle` describes how a bulk export is packed. */
export const CLIENT_FORMATS = [
	{
		id: "amneziawg",
		labelKey: "gen_format_amneziawg",
		descKey: "gen_format_amneziawg_desc",
		extension: "conf",
		bundle: "files",
	},
	{
		id: "wiresock",
		labelKey: "gen_format_wiresock",
		descKey: "gen_format_wiresock_desc",
		extension: "conf",
		bundle: "files",
	},
	{
		id: "clash",
		labelKey: "gen_format_clash",
		descKey: "gen_format_clash_desc",
		extension: "yaml",
		bundle: "single",
	},
]

export function formatById(id) {
	return CLIENT_FORMATS.find((format) => format.id === id) ?? CLIENT_FORMATS[0]
}

/**
 * Domains WireSock can imitate. They are ordinary, high-traffic sites that no
 * filter can block wholesale, which is the entire point of the disguise.
 */
export const SNI_DOMAINS = [
	"apteka.ru",
	"psbank.ru",
	"lenta.ru",
	"www.pochta.ru",
	"rzd.ru",
	"rutube.ru",
	"gosuslugi.ru",
	"ozon.ru",
]

/** Protocol WireSock imitates on the first packet (`Ip`). */
export const WIRESOCK_PROTOCOLS = [
	{ id: "quic", labelKey: "gen_sni_ip_quic" },
	{ id: "dns", labelKey: "gen_sni_ip_dns" },
]

/** Client fingerprint WireSock imitates (`Ib`). */
export const WIRESOCK_BROWSERS = [
	{ id: "chrome", labelKey: "gen_sni_ib_chrome" },
	{ id: "firefox", labelKey: "gen_sni_ib_firefox" },
	{ id: "curl", labelKey: "gen_sni_ib_curl" },
]

/** A domain to imitate. Picking at random keeps exported files from matching. */
export function randomSni(random = Math.random) {
	return SNI_DOMAINS[Math.floor(random() * SNI_DOMAINS.length)] ?? SNI_DOMAINS[0]
}

/** Fields WireSock understands, in the order its own examples print them. */
const WIRESOCK_AWG_ORDER = ["S1", "S2", "Jc", "Jmin", "Jmax", "H1", "H2", "H3", "H4"]

/** Obfuscation fields mihomo reads, mapped to their lower-case Clash names. */
const CLASH_AWG_ORDER = [
	"Jc",
	"Jmin",
	"Jmax",
	"S1",
	"S2",
	"H1",
	"H2",
	"H3",
	"H4",
	"I1",
	"I2",
	"I3",
	"I4",
	"I5",
]

export const CLASH_GROUP_NAME = "Proton"
export const CLASH_GROUP_ICON =
	"https://res.cloudinary.com/dbulfrlrz/image/upload/v1703162849/static/logos/icons/vpn_f9embt.svg"
export const CLASH_TEST_URL = "http://speed.cloudflare.com/"
export const CLASH_TEST_INTERVAL = 300

function filled(value) {
	return value !== undefined && value !== null && String(value).trim() !== ""
}

function hasObfuscation(awgParams) {
	return Object.values(awgParams ?? {}).some(filled)
}

/**
 * The flag emoji for an ISO 3166-1 alpha-2 code, built from regional indicator
 * symbols so no lookup table can fall behind Proton's server list. Codes that
 * are not two letters (Proton uses a few) get no emoji.
 */
export function flagEmoji(code) {
	const normalised = String(code ?? "").trim().toUpperCase()
	if (!/^[A-Z]{2}$/.test(normalised)) return ""
	return String.fromCodePoint(...[...normalised].map((letter) => 0x1f1a5 + letter.charCodeAt(0)))
}

/**
 * The name a proxy gets in Clash: `NL-FREE#120` becomes `🇳🇱 NL-120`. The tier
 * marker and the hash carry no meaning inside a proxy list, and the flag makes
 * a list of eighty servers readable at a glance.
 */
export function clashProxyName(server) {
	const cleaned = String(server?.name ?? "")
		.replace(/free/gi, "")
		.replace(/[#_\s]+/g, "-")
		.replace(/-+/g, "-")
		.replace(/^-|-$/g, "")
	const label = cleaned || String(server?.exitCountry ?? "proxy")
	const emoji = flagEmoji(server?.exitCountry)
	return emoji ? `${emoji} ${label}` : label
}

/**
 * WireSock configuration.
 *
 * WireSock refuses a literal `I1`, so the packet is described instead: `Id` is
 * the domain to imitate, `Ip` the protocol and `Ib` the client fingerprint.
 * The client then builds the packet itself at connection time.
 */
export function buildWiresockConfig({
	server,
	privateKey,
	awgParams = {},
	dnsId = "cloudflare",
	customDns = "",
	mtu = DEFAULT_MTU,
	port = DEFAULT_PORT,
	allowedIps = "0.0.0.0/0, ::/0",
	ipv6 = true,
	sniDomain = "",
	sniProtocol = "quic",
	sniBrowser = "curl",
}) {
	const lines = [
		`# ${server.name}`,
		"[Interface]",
		`PrivateKey = ${privateKey}`,
		`Address = ${interfaceAddress(ipv6)}`,
		`DNS = ${resolveDns({ dnsId, customDns, ipv6 })}`,
	]

	if (mtu) lines.push(`MTU = ${mtu}`)

	if (hasObfuscation(awgParams)) {
		for (const key of WIRESOCK_AWG_ORDER) {
			if (filled(awgParams[key])) lines.push(`${key} = ${awgParams[key]}`)
		}
		if (filled(sniDomain)) {
			lines.push(`Id = ${String(sniDomain).trim()}`)
			lines.push(`Ip = ${sniProtocol}`)
			lines.push(`Ib = ${sniBrowser}`)
		}
	}

	lines.push(
		"",
		"[Peer]",
		`PublicKey = ${server.publicKey}`,
		`AllowedIPs = ${allowedIps}`,
		`Endpoint = ${server.entryIp}:${port || DEFAULT_PORT}`,
	)

	return `${lines.join("\n")}\n`
}

/**
 * One Clash proxy, as YAML lines already indented for a `proxies:` list.
 *
 * Addresses and resolvers are quoted: a bare `2a07:b944::2:1` inside a flow
 * sequence is ambiguous for strict YAML parsers.
 */
export function clashProxyLines({
	server,
	privateKey,
	awgParams = {},
	dnsId = "cloudflare",
	customDns = "",
	mtu = DEFAULT_MTU,
	port = DEFAULT_PORT,
	allowedIps = "0.0.0.0/0, ::/0",
	ipv6 = true,
	name,
}) {
	const allowed = splitList(allowedIps)
		.map((entry) => `'${entry}'`)
		.join(", ")
	const dns = splitList(resolveDns({ dnsId, customDns, ipv6 }))
		.map((entry) => `'${entry}'`)
		.join(", ")

	const lines = [
		`  - name: ${name ?? clashProxyName(server)}`,
		"    type: wireguard",
		`    server: ${server.entryIp}`,
		`    port: ${port || DEFAULT_PORT}`,
		`    ip: ${CLIENT_ADDRESS}`,
	]

	if (ipv6) lines.push(`    ipv6: '${CLIENT_ADDRESS_V6}'`)

	lines.push(
		`    private-key: ${privateKey}`,
		`    public-key: ${server.publicKey}`,
		`    allowed-ips: [${allowed}]`,
		"    udp: true",
	)

	if (mtu) lines.push(`    mtu: ${mtu}`)

	lines.push("    remote-dns-resolve: true", `    dns: [${dns}]`)

	if (hasObfuscation(awgParams)) {
		const options = CLASH_AWG_ORDER.filter((key) => filled(awgParams[key]))
		if (options.length) {
			lines.push("    amnezia-wg-option:")
			for (const key of options) {
				lines.push(`      ${key.toLowerCase()}: ${awgParams[key]}`)
			}
		}
	}

	return lines
}

/**
 * A complete Clash document: every proxy plus a selector group holding them.
 *
 * @param entries one `clashProxyLines` argument object per server
 */
export function buildClashDocument(entries) {
	const proxies = []
	const names = []
	const used = new Set()

	for (const entry of entries) {
		// Proton reuses server names across cities often enough that a list of
		// eighty proxies can collide, and Clash rejects duplicates outright.
		let name = entry.name ?? clashProxyName(entry.server)
		if (used.has(name)) {
			let suffix = 2
			while (used.has(`${name} (${suffix})`)) suffix += 1
			name = `${name} (${suffix})`
		}
		used.add(name)
		names.push(name)
		proxies.push(...clashProxyLines({ ...entry, name }))
	}

	const lines = ["proxies:", ...proxies, "", "proxy-groups:", `  - name: ${CLASH_GROUP_NAME}`, "    type: select", `    icon: ${CLASH_GROUP_ICON}`, "    proxies:"]

	for (const name of names) lines.push(`      - ${name}`)

	lines.push(`    url: '${CLASH_TEST_URL}'`, "    unified-delay: true", `    interval: ${CLASH_TEST_INTERVAL}`)

	return `${lines.join("\n")}\n`
}

/**
 * Renders one server in the chosen format. Clash returns a whole document so a
 * single generated proxy is usable on its own.
 */
export function renderConfig({ format = "amneziawg", ...options }) {
	if (format === "wiresock") return buildWiresockConfig(options)
	if (format === "clash") return buildClashDocument([options])
	return buildConfig(options)
}
