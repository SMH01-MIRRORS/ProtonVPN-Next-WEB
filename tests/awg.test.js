import assert from "node:assert/strict"
import { test } from "node:test"

import {
	ADVANCED_GROUPS,
	ADVANCED_KEY_ORDER,
	AWG_KEY_ORDER,
	DEFAULT_I1,
	I1_PRESETS,
	advancedFieldKeys,
	advancedFromPreset,
	generateHeaderProtectionKey,
	nextI1,
	orderedAwgEntries,
	parseAwgString,
	presetById,
	vpnNextDefault,
} from "../src/lib/awg.js"
import { buildConfig } from "../src/lib/conf.js"
import { flagUrl } from "../src/lib/flags.js"

test("the app's stock I1 packets are all distinct and the default is one of them", () => {
	assert.equal(I1_PRESETS.length, 5)
	assert.equal(new Set(I1_PRESETS).size, I1_PRESETS.length)
	assert.equal(DEFAULT_I1, I1_PRESETS[0])
	assert.ok(I1_PRESETS.every((packet) => packet.startsWith("<b 0x")))
})

test("rotating I1 always hands back a different packet", () => {
	let current = DEFAULT_I1
	for (let step = 0; step < 20; step += 1) {
		const next = nextI1(current)
		assert.notEqual(next, current)
		assert.ok(I1_PRESETS.includes(next))
		current = next
	}
})

test("rotating from an unknown packet still lands on a known one", () => {
	assert.ok(I1_PRESETS.includes(nextI1("<b 0xdeadbeef>")))
	assert.ok(I1_PRESETS.includes(nextI1("")))
})

test("the advanced editor covers every field the file writer can emit", () => {
	const editable = advancedFieldKeys()
	for (const key of [...AWG_KEY_ORDER, ...ADVANCED_KEY_ORDER]) {
		assert.ok(editable.includes(key), `${key} is missing from the advanced editor`)
	}
	assert.equal(new Set(editable).size, editable.length, "no field is offered twice")
})

test("every advanced field carries a translation key rather than literal text", () => {
	for (const group of ADVANCED_GROUPS) {
		assert.match(group.titleKey, /^gen_adv_group_/)
		for (const field of group.fields) {
			assert.match(field.labelKey, /^gen_adv_/)
			assert.ok(["number", "text", "long-text"].includes(field.type))
		}
	}
})

test("opening advanced mode starts from the chosen preset, with 2.0 fields blank", () => {
	const params = advancedFromPreset("preset-high")

	assert.equal(params.Jc, "20")
	assert.equal(params.Jmin, "400")
	assert.equal(params.Jmax, "800")
	assert.equal(params.I1, DEFAULT_I1)
	for (const key of ADVANCED_KEY_ORDER) assert.equal(params[key], "")
})

test("the junk presets match the ones in the Android view model", () => {
	assert.deepEqual(
		["Jc", "Jmin", "Jmax"].map((key) => presetById("vpn-next-default").params()[key]),
		["3", "1", "3"],
	)
	assert.deepEqual(
		["Jc", "Jmin", "Jmax"].map((key) => presetById("preset-medium").params()[key]),
		["10", "50", "100"],
	)
	assert.deepEqual(
		["Jc", "Jmin", "Jmax"].map((key) => presetById("preset-high").params()[key]),
		["20", "400", "800"],
	)
})

test("a header protection key is 32 base64-encoded bytes", () => {
	const key = generateHeaderProtectionKey((buffer) => buffer.fill(7))

	assert.equal(Buffer.from(key, "base64").length, 32)
	assert.ok(Buffer.from(key, "base64").every((byte) => byte === 7))
})

test("empty advanced fields never reach the configuration file", () => {
	const params = advancedFromPreset("vpn-next-default")
	const keys = orderedAwgEntries(params).map(([key]) => key)

	for (const key of ADVANCED_KEY_ORDER) assert.ok(!keys.includes(key))
	assert.ok(keys.includes("Jc"))
	assert.ok(keys.includes("I1"))
})

test("filled advanced fields are written after the standard ones", () => {
	const params = { ...vpnNextDefault(), HeaderProtectionKey: "a2V5", ContentPaddingAddition: "32", RekeyAfterTime: "120" }
	const keys = orderedAwgEntries(params).map(([key]) => key)

	assert.ok(keys.indexOf("HeaderProtectionKey") > keys.indexOf("I1"))
	assert.deepEqual(keys.slice(-3), ["HeaderProtectionKey", "ContentPaddingAddition", "RekeyAfterTime"])
})

test("the advanced values land in the exported .conf", () => {
	const text = buildConfig({
		server: { publicKey: "pub", entryIp: "1.2.3.4", name: "NL-FREE#1", exitCountry: "NL" },
		privateKey: "priv",
		awgParams: { ...vpnNextDefault(), HeaderProtectionKey: "a2V5", MaxHandshakeAttempts: "5" },
	})

	assert.match(text, /^HeaderProtectionKey = a2V5$/m)
	assert.match(text, /^MaxHandshakeAttempts = 5$/m)
	assert.ok(!text.includes("RekeyAfterTime"), "untouched fields stay out of the file")
})

test("parsing a preset name still works for the shorthand the CLI accepts", () => {
	assert.equal(parseAwgString("preset-high").Jmax, "800")
	assert.equal(parseAwgString("jc=7, jmin=2").Jc, "7")
	assert.deepEqual(parseAwgString(""), {})
})

test("flag assets are addressed by lower-case country code", () => {
	assert.equal(flagUrl("NL"), "/flags/nl.svg")
	assert.equal(flagUrl("fastest"), "/flags/fastest.svg")
	assert.equal(flagUrl(""), "/flags/fastest.svg")
})
