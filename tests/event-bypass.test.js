import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { test } from "node:test"

// The Android app reads this file from every website mirror, so a typo here
// breaks the Event bypass on devices that already shipped. Validate the shape
// the app parses, not just "is it JSON".
const config = JSON.parse(
	readFileSync(new URL("../public/event-bypass.json", import.meta.url), "utf8"),
)

test("event-bypass.json carries a supported version and a timestamp", () => {
	assert.equal(config.version, 1)
	assert.ok(!Number.isNaN(Date.parse(config.updatedAt)), "updatedAt must be ISO-8601")
})

test("event-bypass.json describes exactly one event bypass", () => {
	assert.equal(typeof config.event, "object")
	assert.ok(config.event, "event must not be null")
	assert.ok(config.event.id.length > 0, "id must not be empty")
	assert.ok(config.event.name.length > 0, "name is shown in the app UI")
	assert.equal(typeof config.event.enabled, "boolean")
	assert.equal(typeof config.event.url, "string")
})

test("an enabled event bypass points at an https endpoint", () => {
	if (!config.event.enabled) {
		assert.equal(config.event.url, "", "a disabled bypass must not ship a URL")
		return
	}

	const url = new URL(config.event.url)
	assert.equal(url.protocol, "https:", "the app refuses plaintext endpoints")
	assert.ok(
		config.event.url.endsWith("/"),
		"the app treats the URL as a Retrofit base URL, so it must end with /",
	)
})
