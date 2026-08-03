import { strict as assert } from "node:assert"
import { test } from "node:test"

import { LAN_EXCLUDED_IPS, splitList } from "../src/lib/conf.js"
import {
	SNI_DOMAINS,
	buildClashDocument,
	buildWiresockConfig,
	clashProxyName,
	flagEmoji,
	randomSni,
	renderConfig,
} from "../src/lib/formats.js"

const server = {
	id: "nl-120",
	name: "NL-FREE#120",
	exitCountry: "NL",
	city: "Amsterdam",
	entryIp: "149.34.244.169",
	publicKey: "TH87YVmOQBoo1Mir13INlDzvTOlvsi9dWmAp+IF3bRg=",
}

const awgParams = {
	Jc: "3",
	Jmin: "1",
	Jmax: "3",
	S1: "0",
	S2: "0",
	S3: "0",
	S4: "0",
	H1: "1",
	H2: "2",
	H3: "3",
	H4: "4",
	I1: "<b 0xce00>",
}

const base = {
	server,
	privateKey: "wNUNRE7JIKz8yhyjs6Xp2pzkFrlMcESHIFuBlecFqUo=",
	awgParams,
	dnsId: "proton",
	mtu: "1420",
	port: 51820,
	allowedIps: "0.0.0.0/0, ::/0",
	ipv6: true,
}

test("WireSock config carries both address families and the imitation fields", () => {
	const text = buildWiresockConfig({ ...base, sniDomain: "apteka.ru", sniProtocol: "quic", sniBrowser: "curl" })

	assert.match(text, /^# NL-FREE#120\n\[Interface\]\n/)
	assert.match(text, /Address = 10\.2\.0\.2\/32, 2a07:b944::2:2\/128/)
	assert.match(text, /DNS = 10\.2\.0\.1, 2a07:b944::2:1/)
	assert.match(text, /MTU = 1420/)
	assert.match(text, /Id = apteka\.ru\nIp = quic\nIb = curl/)
	assert.match(text, /Endpoint = 149\.34\.244\.169:51820/)

	// WireSock builds the packet from the domain, so a literal I1 must not
	// reach it, and neither must the fields it has no idea about.
	assert.ok(!text.includes("I1 ="))
	assert.ok(!text.includes("S3 ="))
})

test("WireSock keeps IPv6 out when it is switched off", () => {
	const text = buildWiresockConfig({ ...base, ipv6: false, sniDomain: "apteka.ru" })

	assert.match(text, /Address = 10\.2\.0\.2\/32\n/)
	assert.match(text, /DNS = 10\.2\.0\.1\n/)
})

test("WireSock omits the imitation fields without obfuscation", () => {
	const text = buildWiresockConfig({ ...base, awgParams: {}, sniDomain: "apteka.ru" })

	assert.ok(!text.includes("Id ="))
	assert.ok(!text.includes("Jc ="))
})

test("Clash document holds a proxy and a selector group", () => {
	const text = buildClashDocument([base])

	assert.match(text, /^proxies:\n {2}- name: \uD83C\uDDF3\uD83C\uDDF1 NL-120\n/)
	assert.match(text, / {4}type: wireguard\n/)
	assert.match(text, / {4}server: 149\.34\.244\.169\n {4}port: 51820\n/)
	assert.match(text, / {4}ip: 10\.2\.0\.2\/32\n {4}ipv6: '2a07:b944::2:2\/128'\n/)
	assert.match(text, / {4}allowed-ips: \['0\.0\.0\.0\/0', '::\/0'\]\n/)
	assert.match(text, / {4}dns: \['10\.2\.0\.1', '2a07:b944::2:1'\]\n/)
	assert.match(text, / {4}amnezia-wg-option:\n {6}jc: 3\n {6}jmin: 1\n {6}jmax: 3\n {6}s1: 0\n {6}s2: 0\n/)
	assert.match(text, / {6}h4: 4\n {6}i1: <b 0xce00>\n/)
	assert.match(text, /proxy-groups:\n {2}- name: Proton\n {4}type: select\n/)
	assert.match(text, / {6}- \uD83C\uDDF3\uD83C\uDDF1 NL-120\n/)
	assert.match(text, / {4}url: 'http:\/\/speed\.cloudflare\.com\/'\n {4}unified-delay: true\n {4}interval: 300\n/)

	// mihomo has no s3/s4, and writing them would break the parse.
	assert.ok(!text.includes("s3:"))
})

test("Clash renames colliding proxies", () => {
	const text = buildClashDocument([base, { ...base, server: { ...server, id: "nl-120-b" } }])

	assert.match(text, /- name: \uD83C\uDDF3\uD83C\uDDF1 NL-120 \(2\)/)
})

test("the LAN exclusion reaches every format", () => {
	const entries = splitList(LAN_EXCLUDED_IPS)
	assert.equal(entries.length, 56)
	assert.ok(!entries.includes("0.0.0.0/0"))
	assert.ok(entries.includes("192.169.0.0/16"))
	assert.ok(entries.includes("ff00::/8"))

	const amnezia = renderConfig({ ...base, format: "amneziawg", allowedIps: LAN_EXCLUDED_IPS })
	assert.match(amnezia, /AllowedIPs = 1\.0\.0\.0\/8, 2\.0\.0\.0\/7, /)

	const wiresock = renderConfig({ ...base, format: "wiresock", allowedIps: LAN_EXCLUDED_IPS })
	assert.match(wiresock, /AllowedIPs = 1\.0\.0\.0\/8, /)

	const clash = renderConfig({ ...base, format: "clash", allowedIps: LAN_EXCLUDED_IPS })
	assert.match(clash, /allowed-ips: \['1\.0\.0\.0\/8', '2\.0\.0\.0\/7', /)
	assert.ok(clash.includes("'ff00::/8'"))
})

test("renderConfig falls back to the AmneziaWG file", () => {
	const text = renderConfig({ ...base, format: "amneziawg" })

	assert.match(text, /^\[Interface\]\n/)
	assert.match(text, /PersistentKeepalive = 25\n$/)
})

test("proxy names lose the tier marker and gain a flag", () => {
	assert.equal(clashProxyName(server), "\uD83C\uDDF3\uD83C\uDDF1 NL-120")
	assert.equal(clashProxyName({ name: "US-TX#3", exitCountry: "US" }), "\uD83C\uDDFA\uD83C\uDDF8 US-TX-3")
	assert.equal(flagEmoji(""), "")
	assert.equal(flagEmoji("ZZZ"), "")
})

test("the imitated domain comes from the known pool", () => {
	assert.ok(SNI_DOMAINS.includes(randomSni(() => 0)))
	assert.ok(SNI_DOMAINS.includes(randomSni(() => 0.999)))
})
