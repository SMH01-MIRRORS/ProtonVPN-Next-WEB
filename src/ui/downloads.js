/**
 * Download matrix UI: channel × flavour × build type, driven by
 * `public/update.json`.
 */

import { t, onLanguageChange } from "../i18n/index.js"
import {
	CHANNELS,
	FLAVORS,
	BUILD_TYPES,
	allBuilds,
	buildFor,
	fetchUpdateMetadata,
} from "../lib/downloads.js"

function publishedCount(metadata) {
	return allBuilds(metadata).filter((item) => item.build).length
}

function selectorGroup({ items, selected, onSelect }) {
	const wrapper = document.createElement("div")
	wrapper.className = "flex flex-wrap gap-2"

	for (const item of items) {
		const button = document.createElement("button")
		button.type = "button"
		button.className = `chip ${item.id === selected ? "chip-active" : ""}`
		button.dataset.t = item.labelKey
		button.textContent = t(item.labelKey)
		button.setAttribute("aria-pressed", String(item.id === selected))
		button.addEventListener("click", () => onSelect(item.id))
		wrapper.append(button)
	}

	return wrapper
}

function descriptionFor(items, id) {
	const item = items.find((candidate) => candidate.id === id)
	return item ? t(item.descriptionKey) : ""
}

/**
 * Renders the matrix into `root`.
 * Selection state lives here; the section re-renders on every change and on
 * every language switch.
 */
export function mountDownloads(root) {
	const state = {
		channel: "stable",
		flavor: "standard",
		buildType: "release",
		metadata: null,
		status: "loading", // loading | ready | error
	}

	function select(key, value) {
		state[key] = value
		render()
	}

	function renderStatusCard(messageKey) {
		const card = document.createElement("div")
		card.className = "card text-sm text-slate-400"
		card.dataset.t = messageKey
		card.textContent = t(messageKey)
		return card
	}

	function renderBuildCard() {
		const build = buildFor(
			state.metadata,
			state.channel,
			state.flavor,
			state.buildType,
		)

		const card = document.createElement("div")
		card.className = "card"

		if (!build) {
			const empty = document.createElement("p")
			empty.className = "text-sm text-slate-400"
			empty.textContent = t("dl_unavailable")
			card.append(empty)
			return card
		}

		const meta = document.createElement("dl")
		meta.className = "grid gap-4 sm:grid-cols-2"

		for (const [labelKey, value] of [
			["dl_version", build.versionName ?? "\u2014"],
			["dl_build_number", build.versionCode ?? "\u2014"],
		]) {
			const term = document.createElement("dt")
			term.className = "text-xs uppercase tracking-wide text-slate-500"
			term.textContent = t(labelKey)

			const detail = document.createElement("dd")
			detail.className = "mt-1 font-mono text-sm text-white"
			detail.textContent = String(value)

			const cell = document.createElement("div")
			cell.append(term, detail)
			meta.append(cell)
		}
		card.append(meta)

		if (build.changelog) {
			const details = document.createElement("details")
			details.className = "mt-5"

			const summary = document.createElement("summary")
			summary.className = "cursor-pointer text-sm text-brand-light"
			summary.textContent = t("dl_changelog")

			const body = document.createElement("pre")
			body.className =
				"mt-3 whitespace-pre-wrap text-sm leading-relaxed text-slate-400"
			body.textContent = build.changelog

			details.append(summary, body)
			card.append(details)
		}

		const actions = document.createElement("div")
		actions.className = "mt-6 flex flex-wrap items-center gap-3"

		const download = document.createElement("a")
		download.className = "btn-primary"
		download.href = build.url
		download.rel = "noopener noreferrer"
		download.textContent = t("dl_download")

		const checksum = document.createElement("a")
		checksum.className = "btn-ghost btn-sm"
		checksum.href = build.sha256Url
		checksum.rel = "noopener noreferrer"
		checksum.target = "_blank"
		checksum.textContent = t("dl_checksum")

		actions.append(download, checksum)
		card.append(actions)

		return card
	}

	function render() {
		root.replaceChildren()

		if (state.status === "loading") {
			root.append(renderStatusCard("dl_loading"))
			return
		}
		if (state.status === "error") {
			root.append(renderStatusCard("dl_error"))
			return
		}

		const grid = document.createElement("div")
		grid.className = "grid gap-5 lg:grid-cols-[1fr_1.1fr]"

		const chooser = document.createElement("div")
		chooser.className = "card space-y-6"

		for (const [items, key] of [
			[CHANNELS, "channel"],
			[FLAVORS, "flavor"],
			[BUILD_TYPES, "buildType"],
		]) {
			const block = document.createElement("div")

			const group = selectorGroup({
				items,
				selected: state[key],
				onSelect: (value) => select(key, value),
			})

			const hint = document.createElement("p")
			hint.className = "mt-2 text-xs text-slate-500"
			hint.textContent = descriptionFor(items, state[key])

			block.append(group, hint)
			chooser.append(block)
		}

		const summary = document.createElement("p")
		summary.className = "text-xs text-slate-500"
		summary.textContent = `${publishedCount(state.metadata)} / ${
			CHANNELS.length * FLAVORS.length * BUILD_TYPES.length
		}`
		chooser.append(summary)

		grid.append(chooser, renderBuildCard())
		root.append(grid)
	}

	onLanguageChange(render)
	render()

	fetchUpdateMetadata()
		.then((metadata) => {
			state.metadata = metadata
			state.status = "ready"
		})
		.catch(() => {
			state.status = "error"
		})
		.finally(render)
}
