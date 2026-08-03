import { strict as assert } from "node:assert"
import { test } from "node:test"

import {
	DEVICE_PROFILES,
	SPOOFED_APP_VERSION,
	baseHeaders,
	buildChallengePayload,
	javaStringHashCode,
	profileRotation,
	userAgentFor,
} from "../src/lib/spoof.js"
import { AWG_KEY_ORDER, parseAwgString, vpnNextDefault } from "../src/lib/awg.js"

test("the Java hashCode is reproduced exactly, including overflow", () => {
	// Reference values from java.lang.String.hashCode.
	assert.equal(javaStringHashCode(""), 0)
	assert.equal(javaStringHashCode("a"), 97)
	assert.equal(javaStringHashCode("hello"), 99162322)

	// Long input: must stay a signed 32-bit int rather than growing unbounded.
	const long = javaStringHashCode("281474976710655:galaxys23")
	assert.ok(Number.isInteger(long))
	assert.ok(long >= -2147483648 && long <= 2147483647)
})

test("every device profile is complete, unique and self-consistent", () => {
	assert.ok(DEVICE_PROFILES.length > 1, "captcha rotation needs more than one profile")

	const ids = new Set()
	for (const profile of DEVICE_PROFILES) {
		assert.ok(profile.id, "profile needs an id")
		assert.ok(profile.model, `${profile.id} needs a model`)
		assert.ok(profile.androidVersion, `${profile.id} needs an Android version`)
		assert.ok(profile.timezone.includes("/"), `${profile.id} needs an IANA timezone`)
		assert.equal(profile.regionCode.length, 2, `${profile.id} needs an ISO region`)
		assert.ok(Array.isArray(profile.keyboards) && profile.keyboards.length > 0)
		assert.ok(!ids.has(profile.id), `duplicate profile id ${profile.id}`)
		ids.add(profile.id)
	}
})

test("the rotation covers every profile exactly once", () => {
	// The starting point is randomised, so only the coverage is guaranteed.
	for (let attempt = 0; attempt < 20; attempt += 1) {
		const rotation = profileRotation()
		assert.equal(rotation.length, DEVICE_PROFILES.length)
		assert.equal(new Set(rotation.map((item) => item.id)).size, DEVICE_PROFILES.length)
	}
})

test("request headers spoof the official Android app", () => {
	const profile = DEVICE_PROFILES[0]
	const headers = baseHeaders(profile)

	assert.equal(headers["x-pm-appversion"], `android-vpn@${SPOOFED_APP_VERSION}-dev+play`)
	assert.equal(headers["x-pm-apiversion"], "4")
	assert.equal(headers.Accept, "application/vnd.protonmail.v1+json")
	assert.equal(
		userAgentFor(profile),
		`PVPN/${SPOOFED_APP_VERSION} (Android ${profile.androidVersion}; ${profile.manufacturer} ${profile.model})`,
	)
})

test("the challenge frame keeps the shape Proton expects", () => {
	const profile = DEVICE_PROFILES[2]
	const frame = buildChallengePayload(profile).Payload["vpn-android-v4-challenge-0"]

	assert.equal(frame.type, "me.proton.core.challenge.data.frame.ChallengeFrame.Device")
	assert.equal(frame.v, SPOOFED_APP_VERSION)
	assert.equal(frame.appLang, profile.language)
	assert.equal(frame.regionCode, profile.regionCode)
	assert.equal(frame.timezoneOffset, profile.timezoneOffset)
	// deviceName is an int hash, not a string, exactly like the Android client.
	assert.equal(typeof frame.deviceName, "number")
	assert.equal(frame.isJailbreak, false)
})

test("different profiles produce different device fingerprints", () => {
	const first = buildChallengePayload(DEVICE_PROFILES[0]).Payload["vpn-android-v4-challenge-0"]
	const second = buildChallengePayload(DEVICE_PROFILES[1]).Payload["vpn-android-v4-challenge-0"]

	assert.notEqual(first.deviceName, second.deviceName)
})

test("AWG parameters round-trip through the CLI string format", () => {
	const params = vpnNextDefault()
	const serialized = AWG_KEY_ORDER.filter((key) => params[key])
		.map((key) => `${key}=${params[key]}`)
		.join(", ")

	const parsed = parseAwgString(serialized)

	assert.equal(parsed.Jc, "3")
	assert.equal(parsed.Jmin, "1")
	assert.equal(parsed.H4, "4")
	assert.equal(parsed.I1, params.I1)
})

test("a preset name is accepted in place of a parameter list", () => {
	assert.deepEqual(parseAwgString("preset-off").Jc, "0")
	assert.deepEqual(parseAwgString(""), {})
})
