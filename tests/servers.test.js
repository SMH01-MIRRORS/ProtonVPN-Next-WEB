import assert from "node:assert/strict"
import { test } from "node:test"

import { averageLoad, countriesOf, fastestServer, serversByCountry } from "../src/lib/servers.js"

const SERVERS = [
	{ id: "nl1", name: "NL-FREE#1", exitCountry: "NL", load: 80, score: 1.5 },
	{ id: "nl2", name: "NL-FREE#2", exitCountry: "NL", load: 20, score: 1.2 },
	{ id: "us1", name: "US-FREE#1", exitCountry: "US", load: 10, score: 3.4 },
	{ id: "jp1", name: "JP-FREE#1", exitCountry: "JP", load: 55, score: 0.9 },
]

test("the fastest server is the best scored one, not merely the emptiest", () => {
	// US is the least loaded but far away; the app follows the score instead.
	assert.equal(fastestServer(SERVERS).id, "jp1")
})

test("an equal score is broken by load", () => {
	const tied = [
		{ id: "a", exitCountry: "NL", load: 70, score: 1 },
		{ id: "b", exitCountry: "NL", load: 30, score: 1 },
	]

	assert.equal(fastestServer(tied).id, "b")
})

test("servers without a score never win over servers that have one", () => {
	const mixed = [
		{ id: "unknown", exitCountry: "NL", load: 1 },
		{ id: "scored", exitCountry: "NL", load: 90, score: 5 },
	]

	assert.equal(fastestServer(mixed).id, "scored")
})

test("an empty list has no fastest server", () => {
	assert.equal(fastestServer([]), null)
})

test("countries are grouped and ordered by their best server", () => {
	const groups = serversByCountry(SERVERS)

	assert.deepEqual(
		groups.map((group) => group.country),
		["JP", "NL", "US"],
	)
	assert.equal(groups[1].servers.length, 2)
	assert.equal(groups[1].fastest.id, "nl2")
})

test("each country reports the mean load of its servers", () => {
	assert.equal(averageLoad([{ load: 10 }, { load: 21 }]), 16)
	assert.equal(averageLoad([{ load: 10 }, {}]), 10, "servers without a load are ignored")
	assert.equal(averageLoad([{}]), null)
})

test("the country list stays unique and sorted", () => {
	assert.deepEqual(countriesOf(SERVERS), ["JP", "NL", "US"])
})
