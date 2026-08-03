import { strict as assert } from "node:assert"
import { test } from "node:test"

import {
	CLIENT_ADDRESS,
	DEFAULT_MTU,
	DEFAULT_PORT,
	buildConfig,
	configFileName,
	dnsProfileById,
} from "../src/lib/conf.js"
import { presetById, vpnNextDefault } from "../src/lib/awg.js"

const server = {
	id: "abc",
	name: "NL-FREE#1",
	exitCountry: "NL",
	entryIp: "203.0.113.10",
	publicKey: "serverPublicKey=",
}

test("plain WireGuard config has no obfuscation parameters", () => {
	const text = buildConfig({ server, privateKey: "privateKey=", awgParams: {} })

	assert.match(text, /^\[Interface\]\n/)
	assert.ok(text.includes(`Address = ${CLIENT_ADDRESS}`))
	assert.ok(text.includes(`MTU = ${DEFAULT_MTU}`))
	assert.ok(text.includes(`Endpoint = 203.0.113.10:${DEFAULT_PORT}`))
	assert.ok(text.includes("AllowedIPs = 0.0.0.0/0"))
	assert.ok(text.includes("PersistentKeepalive = 25"))
	assert.ok(!text.includes("Jc ="))
})

test("the exported file never contains the CLI's internal delimiter", () => {
	const text = buildConfig({ server, privateKey: "privateKey=" })
	assert.ok(!text.includes("---END---"))
})

test("obfuscation parameters are written in AmneziaWG order", () => {
	const text = buildConfig({
		server,
		privateKey: "privateKey=",
		awgParams: vpnNextDefault(),
	})

	const keys = text
		.split("\n")
		.filter((line) => /^(Jc|Jmin|Jmax|S\d|H\d|I\d) = /.test(line))
		.map((line) => line.split(" = ")[0])

	// Same set and same order the CLI writes in `connection.py`.
	assert.deepEqual(keys, [
		"Jc",
		"Jmin",
		"Jmax",
		"S1",
		"S2",
		"S3",
		"S4",
		"H1",
		"H2",
		"H3",
		"H4",
		"I1",
	])
	// I2..I5 are empty in the default profile and must be omitted entirely.
	assert.ok(!text.includes("I2 = "))
})

test("the off preset keeps header rewriting but disables junk packets", () => {
	const params = presetById("preset-off").params()
	assert.equal(params.Jc, "0")
	assert.equal(params.Jmin, "0")
	assert.equal(params.Jmax, "0")
	assert.equal(params.H1, "1")
})

test("a custom DNS value overrides the selected profile", () => {
	const text = buildConfig({
		server,
		privateKey: "privateKey=",
		dnsId: "proton",
		customDns: " 9.9.9.9 ",
	})
	assert.ok(text.includes("DNS = 9.9.9.9"))
})

test("known DNS profiles resolve, unknown ones fall back", () => {
	assert.equal(dnsProfileById("proton").servers, "10.2.0.1")
	assert.equal(dnsProfileById("nope").id, "proton")
})

test("the file name is filesystem safe", () => {
	assert.equal(configFileName(server), "pvpn-next-NL-FREE-1.conf")
})
