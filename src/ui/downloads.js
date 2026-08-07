/**
 * Download section UI.
 *
 * A platform selector comes first. Android renders the published matrix
 * (channel × flavour × build type) driven by `public/update.json`; Windows and
 * Linux render their interface selector (GUI / CLI) with a "coming soon" card,
 * because nothing is published for them yet.
 */

import { t, onLanguageChange } from "../i18n/index.js"
import {
	CHANNELS,
	FLAVORS,
	BUILD_TYPES,
	DEFAULT_PLATFORM,
	DEFAULT_SURFACE,
	PLATFORMS,
	allBuilds,
	buildFor,
	fetchUpdateMetadata,
	isPlatformAvailable,
	platformOf,
	resolveSurface,
	surfacesFor,
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

function groupLabel(labelKey) {
	const label = document.createElement("p")
	label.className = "text-xs uppercase tracking-wide text-slate-500"
	label.dataset.t = labelKey
	label.textContent = t(labelKey)
	return label
}

function groupHint(text) {
	const hint = document.createElement("p")
	hint.className = "mt-2 text-xs text-slate-500"
	hint.textContent = text
	return hint
}

/**
 * Renders the section into `root`.
 * Selection state lives here; the section re-renders on every change and on
 * every language switch.
 */
export function mountDownloads(root) {
	const state = {
		platform: DEFAULT_PLATFORM,
		surface: resolveSurface(DEFAULT_PLATFORM, DEFAULT_SURFACE),
		channel: "stable",
		flavor: "standard",
		buildType: "release",
		metadata: null,
		status: "loading", // loading | ready | error
	}

	function select(key, value) {
		state[key] = value
		// A platform may not offer the interface that was selected before it.
		if (key === "platform") {
			state.surface = resolveSurface(value, state.surface)
		}
		render()
	}

	function renderStatusCard(messageKey) {
		const card = document.createElement("div")
		card.className = "card text-sm text-slate-400"
		card.dataset.t = messageKey
		card.textContent = t(messageKey)
		return card
	}

	function renderPlatformChooser() {
		const card = document.createElement("div")
		card.className = "card"

		const group = selectorGroup({
			items: PLATFORMS,
			selected: state.platform,
			onSelect: (value) => select("platform", value),
		})
		group.classList.add("mt-2")

		card.append(
			groupLabel("dl_platform"),
			group,
			groupHint(descriptionFor(PLATFORMS, state.platform)),
		)
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

	/** Android: the published matrix, or the loading / error placeholder. */
	function renderMatrix() {
		if (state.status === "loading") return renderStatusCard("dl_loading")
		if (state.status === "error") return renderStatusCard("dl_error")

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

			block.append(group, groupHint(descriptionFor(items, state[key])))
			chooser.append(block)
		}

		const summary = document.createElement("p")
		summary.className = "text-xs text-slate-500"
		summary.textContent = `${publishedCount(state.metadata)} / ${
			CHANNELS.length * FLAVORS.length * BUILD_TYPES.length
		}`
		chooser.append(summary)

		grid.append(chooser, renderBuildCard())
		return grid
	}

	/** Windows / Linux: interface selector plus the "coming soon" notice. */
	function renderComingSoon() {
		const card = document.createElement("div")
		card.className = "card"

		const surfaces = surfacesFor(state.platform)
		if (surfaces.length > 1) {
			const group = selectorGroup({
				items: surfaces,
				selected: state.surface,
				onSelect: (value) => select("surface", value),
			})
			group.classList.add("mt-2")

			card.append(
				groupLabel("dl_surface"),
				group,
				groupHint(descriptionFor(surfaces, state.surface)),
			)
		}

		const notice = document.createElement("div")
		notice.className = "mt-6"

		const badge = document.createElement("span")
		badge.className = "badge"
		badge.dataset.t = "dl_coming_soon"
		badge.textContent = t("dl_coming_soon")

		const text = document.createElement("p")
		text.className = "mt-3 text-sm text-slate-400"
		text.dataset.t = "dl_coming_soon_desc"
		text.textContent = t("dl_coming_soon_desc")

		notice.append(badge, text)
		card.append(notice)

		return card
	}

	function render() {
		root.replaceChildren()

		const layout = document.createElement("div")
		layout.className = "space-y-5"
		layout.append(
			renderPlatformChooser(),
			isPlatformAvailable(state.platform) ? renderMatrix() : renderComingSoon(),
		)

		const hint = document.createElement("p")
		hint.className = "mt-6 text-xs text-slate-500"
		hint.textContent = t(platformOf(state.platform).hintKey)

		root.append(layout, hint)
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
