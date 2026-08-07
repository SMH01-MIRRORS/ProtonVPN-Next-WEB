import { strict as assert } from "node:assert"
import { test } from "node:test"

import {
	BUILD_TYPES,
	CHANNELS,
	FLAVORS,
	PLATFORMS,
	allBuilds,
	buildFor,
	isPlatformAvailable,
	metadataKey,
	platformOf,
	resolveSurface,
	surfacesFor,
} from "../src/lib/downloads.js"

const metadata = {
	stable: {
		release: {
			versionCode: 605160081,
			versionName: "12.0.0-alpha2",
			url: "https://r2.example/VPN-Next/app-stable-standard-release.apk",
			changelog: "notes",
		},
		privacyRelease: {
			versionCode: 605160081,
			versionName: "12.0.0-alpha2",
			url: "https://r2.example/VPN-Next/app-stable-privacy-release.apk",
			changelog: "notes",
		},
	},
	nightly: {
		debug: {
			versionCode: 605160309,
			versionName: "12.0.0-nightly",
			url: "https://r2.example/VPN-Next-TEST/app-nightly-standard-debug.apk",
			changelog: "",
		},
	},
}

test("standard keeps the legacy keys the Android updater reads", () => {
	assert.equal(metadataKey("standard", "release"), "release")
	assert.equal(metadataKey("standard", "debug"), "debug")
})

test("privacy uses its own keys so the updater ignores them", () => {
	assert.equal(metadataKey("privacy", "release"), "privacyRelease")
	assert.equal(metadataKey("privacy", "debug"), "privacyDebug")
})

test("a published build is resolved with its checksum link", () => {
	const build = buildFor(metadata, "stable", "privacy", "release")

	assert.equal(build.versionCode, 605160081)
	assert.equal(build.url, metadata.stable.privacyRelease.url)
	assert.equal(build.sha256Url, `${metadata.stable.privacyRelease.url}.sha256`)
})

test("a missing variant resolves to null instead of throwing", () => {
	assert.equal(buildFor(metadata, "nightly", "privacy", "release"), null)
	assert.equal(buildFor({}, "stable", "standard", "release"), null)
	assert.equal(buildFor(undefined, "stable", "standard", "release"), null)
})

test("the matrix always covers every combination", () => {
	const builds = allBuilds(metadata)

	assert.equal(builds.length, CHANNELS.length * FLAVORS.length * BUILD_TYPES.length)
	assert.equal(builds.filter((item) => item.build).length, 3)
})

test("only android is published today", () => {
	assert.deepEqual(
		PLATFORMS.filter((platform) => platform.available).map((platform) => platform.id),
		["android"],
	)
	assert.equal(isPlatformAvailable("android"), true)
	assert.equal(isPlatformAvailable("windows"), false)
	assert.equal(isPlatformAvailable("linux"), false)
})

test("desktop platforms offer both interfaces, android only the app", () => {
	assert.deepEqual(surfacesFor("android").map((surface) => surface.id), ["gui"])
	assert.deepEqual(surfacesFor("windows").map((surface) => surface.id), ["gui", "cli"])
	assert.deepEqual(surfacesFor("linux").map((surface) => surface.id), ["gui", "cli"])
})

test("an unsupported interface falls back to the first one", () => {
	assert.equal(resolveSurface("windows", "cli"), "cli")
	assert.equal(resolveSurface("android", "cli"), "gui")
	assert.equal(resolveSurface("linux", "nope"), "gui")
})

test("an unknown platform falls back to android", () => {
	assert.equal(platformOf("nope").id, "android")
	assert.equal(platformOf("windows").hintKey, "dl_hint_desktop")
})
