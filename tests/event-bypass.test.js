import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { test } from "node:test"

// The Android app reads this file from every website mirror, so a typo here
// breaks the Event bypass on devices that already shipped. Validate the shape
// the app parses, not just "is it JSON".
const config = JSON.parse(
	readFileSync(new URL("../public/event-bypass.json", import.meta.url), "utf8"),
)

// The app refuses configs newer than this, so bumping the file without shipping
// an app that understands it would disable the bypass instead of breaking it.
const SUPPORTED_VERSION = 2

test("event-bypass.json carries a supported version and a timestamp", () => {
	assert.ok(
		config.version <= SUPPORTED_VERSION,
		`version ${config.version} is newer than the app supports (${SUPPORTED_VERSION})`,
	)
	assert.ok(!Number.isNaN(Date.parse(config.updatedAt)), "updatedAt must be ISO-8601")
})

test("event-bypass.json publishes a list of bypasses", () => {
	assert.ok(Array.isArray(config.events), "events must be an array")
	assert.ok(config.events.length > 0, "publish at least one bypass, or the app has nothing to offer")

	for (const event of config.events) {
		assert.equal(typeof event, "object")
		assert.ok(event.id.length > 0, "id must not be empty: it is how the app remembers the choice")
		assert.ok(event.name.length > 0, "name is shown in the app UI")
		assert.equal(typeof event.enabled, "boolean")
		assert.equal(typeof event.url, "string")
	}
})

test("ids are unique so the app can tell the bypasses apart", () => {
	const ids = config.events.map((event) => event.id)
	assert.equal(new Set(ids).size, ids.length, "duplicate id in events")
})

test("an enabled bypass points at an https endpoint ending with a slash", () => {
	for (const event of config.events) {
		if (!event.enabled) {
			// A disabled entry may keep its URL: that is how a bypass is parked
			// without losing its address. The app skips it either way.
			continue
		}

		const url = new URL(event.url)
		assert.equal(url.protocol, "https:", `${event.id}: the app refuses plaintext endpoints`)
		assert.ok(
			event.url.endsWith("/"),
			`${event.id}: the app treats the URL as a Retrofit base URL, so it must end with /`,
		)
	}
})
