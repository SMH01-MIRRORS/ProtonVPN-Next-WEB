import { strict as assert } from "node:assert"
import { test } from "node:test"

import { createZip, crc32 } from "../src/lib/zip.js"

const encoder = new TextEncoder()
const decoder = new TextDecoder()

test("crc32 matches the reference values", () => {
	assert.equal(crc32(encoder.encode("hello")), 0x3610a686)
	assert.equal(crc32(encoder.encode("")), 0)
})

test("the archive is a valid stored ZIP", () => {
	const bytes = createZip([
		{ name: "NL-1.conf", text: "[Interface]\n" },
		{ name: "NL-2.conf", text: "[Interface]\n" },
	])
	const view = new DataView(bytes.buffer)

	assert.equal(view.getUint32(0, true), 0x04034b50)

	// End of central directory: both entries counted, offsets consistent.
	const end = bytes.length - 22
	assert.equal(view.getUint32(end, true), 0x06054b50)
	assert.equal(view.getUint16(end + 8, true), 2)
	assert.equal(view.getUint16(end + 10, true), 2)

	const centralOffset = view.getUint32(end + 16, true)
	assert.equal(view.getUint32(centralOffset, true), 0x02014b50)
	assert.equal(view.getUint32(end + 12, true), end - centralOffset)

	const text = decoder.decode(bytes)
	assert.ok(text.includes("NL-1.conf"))
	assert.ok(text.includes("NL-2.conf"))
	assert.ok(text.includes("[Interface]"))
})

test("the same input always produces the same archive", () => {
	const files = [{ name: "a.conf", text: "one" }]
	assert.deepEqual(createZip(files), createZip(files))
})

test("an empty archive is still well formed", () => {
	const bytes = createZip([])
	assert.equal(bytes.length, 22)
	assert.equal(new DataView(bytes.buffer).getUint32(0, true), 0x06054b50)
})
