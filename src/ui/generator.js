/**
 * Config generator wizard.
 *
 * Guest session → server list with live load → obfuscation and network settings
 * → registered certificate → downloadable `.conf`. Everything happens in the
 * browser; the API calls go through the CORS proxies whose sources live in
 * `proxy/` on the `protonvpn-next-dev` branch.
 */

import { t, onLanguageChange } from "../i18n/index.js"
import { ProxyUnreachableError } from "../lib/api.js"
import { loginAsGuest, VerificationExhaustedError } from "../lib/auth.js"
import { Ed25519UnsupportedError, generateVpnKeys } from "../lib/crypto.js"
import { registerCertificate } from "../lib/cert.js"
import { OBFUSCATION_PRESETS, presetById } from "../lib/awg.js"
import {
	AVAILABLE_PORTS,
	DEFAULT_MTU,
	DEFAULT_PORT,
	DNS_PROFILES,
	buildConfig,
	configFileName,
	downloadConfig,
} from "../lib/conf.js"
import {
	TIER_FREE,
	countriesOf,
	fetchLoads,
	fetchMaxTier,
	fetchServers,
	prepareServers,
} from "../lib/servers.js"

const STEPS = [
	{ id: "login", labelKey: "gen_step_login" },
	{ id: "servers", labelKey: "gen_step_servers" },
	{ id: "settings", labelKey: "gen_step_settings" },
	{ id: "config", labelKey: "gen_step_config" },
]

const PROGRESS_KEYS = {
	session: "gen_progress_session",
	credentialless: "gen_progress_credentialless",
	rotating: "gen_progress_rotating",
	servers: "gen_progress_servers",
	keys: "gen_progress_keys",
	cert: "gen_progress_cert",
}

function errorKeyFor(error) {
	if (error instanceof ProxyUnreachableError) return "gen_error_proxy"
	if (error instanceof VerificationExhaustedError) return "gen_error_verification"
	if (error instanceof Ed25519UnsupportedError) return "gen_error_crypto"
	return "gen_error_generic"
}

function countryName(code) {
	try {
		const display = new Intl.DisplayNames([document.documentElement.lang], {
			type: "region",
		})
		return display.of(code) ?? code
	} catch {
		return code
	}
}

function element(tag, className, text) {
	const node = document.createElement(tag)
	if (className) node.className = className
	if (text !== undefined) node.textContent = text
	return node
}

function labelledField(labelKey, control) {
	const wrapper = element("label", "block")
	wrapper.append(element("span", "field-label", t(labelKey)), control)
	return wrapper
}

function option(value, text, selected) {
	const node = document.createElement("option")
	node.value = String(value)
	node.textContent = text
	node.selected = selected
	return node
}

export function mountGenerator(root) {
	const state = {
		step: "login",
		busy: false,
		progressKey: null,
		errorKey: null,
		session: null,
		profile: null,
		maxTier: TIER_FREE,
		servers: [],
		country: "all",
		serverId: null,
		presetId: "vpn-next-default",
		dnsId: "cloudflare",
		customDns: "",
		mtu: DEFAULT_MTU,
		port: DEFAULT_PORT,
		allowedIps: "0.0.0.0/0",
		extendedCert: true,
		configText: "",
		certExpiry: null,
		copied: false,
	}

	function visibleServers() {
		return state.country === "all"
			? state.servers
			: state.servers.filter((server) => server.exitCountry === state.country)
	}

	function selectedServer() {
		const candidates = visibleServers()
		return (
			candidates.find((server) => server.id === state.serverId) ??
			candidates[0] ??
			null
		)
	}

	function fail(error) {
		if (error?.name === "AbortError") return
		state.errorKey = errorKeyFor(error)
		state.busy = false
		state.progressKey = null
		render()
	}

	async function startSession() {
		state.busy = true
		state.errorKey = null
		render()

		try {
			const session = await loginAsGuest({
				onProgress: ({ stage }) => {
					if (PROGRESS_KEYS[stage]) {
						state.progressKey = PROGRESS_KEYS[stage]
						render()
					}
				},
			})

			state.session = { accessToken: session.accessToken, uid: session.uid }
			state.profile = session.profile

			state.progressKey = PROGRESS_KEYS.servers
			render()

			const context = { profile: state.profile, session: state.session }
			const [maxTier, logicals, loads] = await Promise.all([
				fetchMaxTier(context),
				fetchServers(context),
				fetchLoads(context),
			])

			state.maxTier = maxTier
			state.servers = prepareServers(logicals, loads, maxTier)
			state.serverId = state.servers[0]?.id ?? null
			state.step = "settings"
			state.busy = false
			state.progressKey = null
			render()
		} catch (error) {
			fail(error)
		}
	}

	async function refreshLoads() {
		if (!state.session) return
		state.busy = true
		state.progressKey = PROGRESS_KEYS.servers
		render()

		try {
			const context = { profile: state.profile, session: state.session }
			const [logicals, loads] = await Promise.all([
				fetchServers(context),
				fetchLoads(context),
			])
			state.servers = prepareServers(logicals, loads, state.maxTier)
			state.busy = false
			state.progressKey = null
			render()
		} catch (error) {
			fail(error)
		}
	}

	async function generate() {
		const server = selectedServer()
		if (!server) return

		state.busy = true
		state.errorKey = null
		state.progressKey = PROGRESS_KEYS.keys
		render()

		try {
			const keys = await generateVpnKeys()

			state.progressKey = PROGRESS_KEYS.cert
			render()

			const certificate = await registerCertificate({
				profile: state.profile,
				session: state.session,
				publicKeyPem: keys.publicKeyPem,
				extended: state.extendedCert,
			})

			state.configText = buildConfig({
				server,
				privateKey: keys.wireGuardPrivateKey,
				awgParams: presetById(state.presetId).params(),
				dnsId: state.dnsId,
				customDns: state.customDns,
				mtu: state.mtu,
				port: state.port,
				allowedIps: state.allowedIps,
			})
			state.certExpiry = certificate.expirationTime
			state.serverId = server.id
			state.step = "config"
			state.busy = false
			state.progressKey = null
			render()
		} catch (error) {
			fail(error)
		}
	}

	function reset() {
		state.step = "login"
		state.session = null
		state.profile = null
		state.servers = []
		state.serverId = null
		state.configText = ""
		state.certExpiry = null
		state.errorKey = null
		state.copied = false
		render()
	}

	/* ---------- rendering ---------- */

	function renderSteps() {
		const list = element("ol", "mb-8 flex flex-wrap gap-2 text-xs")
		const activeIndex = STEPS.findIndex((step) => step.id === state.step)

		STEPS.forEach((step, index) => {
			const item = element(
				"li",
				`rounded-lg px-3 py-1.5 ${
					index <= activeIndex
						? "bg-brand/25 text-white"
						: "bg-white/5 text-slate-500"
				}`,
				`${index + 1}. ${t(step.labelKey)}`,
			)
			if (index === activeIndex) item.setAttribute("aria-current", "step")
			list.append(item)
		})

		return list
	}

	function renderNotices() {
		const fragment = document.createDocumentFragment()

		if (state.progressKey && state.busy) {
			fragment.append(
				element(
					"p",
					"mt-4 text-sm text-brand-light",
					t(state.progressKey),
				),
			)
		}
		if (state.errorKey) {
			const alert = element(
				"p",
				"mt-4 rounded-lg border border-red-400/30 bg-red-500/10 px-4 py-3 text-sm text-red-200",
				t(state.errorKey),
			)
			alert.setAttribute("role", "alert")
			fragment.append(alert)
		}

		return fragment
	}

	function renderLogin() {
		const card = element("div", "card")

		const warning = element(
			"div",
			"rounded-xl border border-brand-light/30 bg-brand/10 p-4",
		)
		warning.append(
			element(
				"p",
				"text-sm font-semibold text-brand-light",
				t("gen_warning_title"),
			),
			element("p", "mt-2 text-sm text-slate-300", t("gen_warning_text")),
		)

		const start = element("button", "btn-primary mt-6", t("gen_start"))
		start.type = "button"
		start.disabled = state.busy
		start.addEventListener("click", startSession)

		card.append(warning, start, renderNotices())
		return card
	}

	function renderSessionSummary() {
		const card = element("div", "card")
		card.append(
			element(
				"p",
				"text-sm font-semibold text-white",
				t("gen_session_ready"),
			),
		)

		const list = element("dl", "mt-4 grid gap-4 sm:grid-cols-3")
		const rows = [
			[t("gen_device_profile"), state.profile?.model ?? state.profile?.id ?? "\u2014"],
			[
				t("gen_tier"),
				state.maxTier === TIER_FREE ? t("gen_tier_free") : String(state.maxTier),
			],
			[t("gen_servers_count"), String(state.servers.length)],
		]

		for (const [term, value] of rows) {
			const cell = element("div")
			cell.append(
				element("dt", "text-xs uppercase tracking-wide text-slate-500", term),
				element("dd", "mt-1 text-sm text-white", value),
			)
			list.append(cell)
		}

		card.append(list)
		return card
	}

	function renderServerPicker() {
		const card = element("div", "card space-y-5")

		const countrySelect = document.createElement("select")
		countrySelect.className = "field"
		countrySelect.append(
			option("all", t("gen_country_all"), state.country === "all"),
		)
		for (const code of countriesOf(state.servers)) {
			countrySelect.append(
				option(code, countryName(code), state.country === code),
			)
		}
		countrySelect.addEventListener("change", (event) => {
			state.country = event.target.value
			state.serverId = null
			render()
		})

		const current = selectedServer()
		const serverSelect = document.createElement("select")
		serverSelect.className = "field"
		for (const server of visibleServers()) {
			const load = server.load === null ? "\u2014" : `${server.load}%`
			serverSelect.append(
				option(
					server.id,
					`${server.name} \u00b7 ${t("gen_load")} ${load}`,
					current?.id === server.id,
				),
			)
		}
		serverSelect.addEventListener("change", (event) => {
			state.serverId = event.target.value
			render()
		})

		const refresh = element("button", "btn-ghost btn-sm", t("gen_refresh"))
		refresh.type = "button"
		refresh.disabled = state.busy
		refresh.addEventListener("click", refreshLoads)

		card.append(
			labelledField("gen_country", countrySelect),
			labelledField("gen_server", serverSelect),
			refresh,
		)
		return card
	}

	function renderObfuscation() {
		const card = element("div", "card")
		card.append(
			element("h3", "text-sm font-semibold text-white", t("gen_obf_title")),
		)

		const group = element("div", "mt-4 flex flex-wrap gap-2")
		for (const preset of OBFUSCATION_PRESETS) {
			const button = element(
				"button",
				`chip ${preset.id === state.presetId ? "chip-active" : ""}`,
				t(preset.labelKey),
			)
			button.type = "button"
			button.setAttribute("aria-pressed", String(preset.id === state.presetId))
			button.addEventListener("click", () => {
				state.presetId = preset.id
				render()
			})
			group.append(button)
		}

		card.append(
			group,
			element(
				"p",
				"mt-3 text-xs text-slate-500",
				t(presetById(state.presetId).descriptionKey),
			),
		)
		return card
	}

	function renderNetwork() {
		const card = element("div", "card space-y-5")
		card.append(
			element("h3", "text-sm font-semibold text-white", t("gen_network_title")),
		)

		const dnsSelect = document.createElement("select")
		dnsSelect.className = "field"
		for (const profile of DNS_PROFILES) {
			dnsSelect.append(
				option(profile.id, t(profile.labelKey), state.dnsId === profile.id),
			)
		}
		dnsSelect.append(
			option("custom", t("gen_dns_custom"), state.dnsId === "custom"),
		)
		dnsSelect.addEventListener("change", (event) => {
			state.dnsId = event.target.value
			render()
		})
		card.append(labelledField("gen_dns_title", dnsSelect))

		if (state.dnsId === "custom") {
			const customInput = document.createElement("input")
			customInput.className = "field"
			customInput.type = "text"
			customInput.value = state.customDns
			customInput.placeholder = t("gen_dns_custom_placeholder")
			customInput.addEventListener("input", (event) => {
				state.customDns = event.target.value
			})
			card.append(labelledField("gen_dns_custom", customInput))
		}

		const mtuInput = document.createElement("input")
		mtuInput.className = "field"
		mtuInput.type = "number"
		mtuInput.min = "1280"
		mtuInput.max = "1500"
		mtuInput.value = state.mtu
		mtuInput.addEventListener("input", (event) => {
			state.mtu = event.target.value
		})

		const portSelect = document.createElement("select")
		portSelect.className = "field"
		for (const port of AVAILABLE_PORTS) {
			portSelect.append(option(port, String(port), Number(state.port) === port))
		}
		portSelect.addEventListener("change", (event) => {
			state.port = Number(event.target.value)
		})

		const allowedInput = document.createElement("input")
		allowedInput.className = "field"
		allowedInput.type = "text"
		allowedInput.value = state.allowedIps
		allowedInput.addEventListener("input", (event) => {
			state.allowedIps = event.target.value
		})

		const grid = element("div", "grid gap-4 sm:grid-cols-3")
		grid.append(
			labelledField("gen_mtu", mtuInput),
			labelledField("gen_port", portSelect),
			labelledField("gen_allowed_ips", allowedInput),
		)
		card.append(grid)

		const certToggle = element("label", "flex items-start gap-3")
		const checkbox = document.createElement("input")
		checkbox.type = "checkbox"
		checkbox.className = "mt-1 size-4 accent-[var(--color-brand)]"
		checkbox.checked = state.extendedCert
		checkbox.addEventListener("change", (event) => {
			state.extendedCert = event.target.checked
		})

		const certText = element("span")
		certText.append(
			element("span", "block text-sm text-white", t("gen_extended_cert")),
			element(
				"span",
				"block text-xs text-slate-500",
				t("gen_extended_cert_desc"),
			),
		)
		certToggle.append(checkbox, certText)
		card.append(certToggle)

		return card
	}

	function renderSettings() {
		const wrapper = element("div", "space-y-5")
		wrapper.append(
			renderSessionSummary(),
			renderServerPicker(),
			renderObfuscation(),
			renderNetwork(),
		)

		const actions = element("div", "flex flex-wrap gap-3")
		const generateButton = element(
			"button",
			"btn-primary",
			state.busy ? t("gen_generating") : t("gen_generate"),
		)
		generateButton.type = "button"
		generateButton.disabled = state.busy || !selectedServer()
		generateButton.addEventListener("click", generate)

		const restart = element("button", "btn-ghost", t("gen_restart"))
		restart.type = "button"
		restart.addEventListener("click", reset)

		actions.append(generateButton, restart)
		wrapper.append(actions, renderNotices())
		return wrapper
	}

	function renderConfig() {
		const wrapper = element("div", "space-y-5")
		const card = element("div", "card")

		card.append(
			element("h3", "text-sm font-semibold text-white", t("gen_result_title")),
		)

		if (state.certExpiry) {
			const expires = new Date(state.certExpiry * 1000)
			card.append(
				element(
					"p",
					"mt-1 text-xs text-slate-500",
					`${t("gen_cert_expires")}: ${expires.toLocaleString()}`,
				),
			)
		}

		const pre = element("pre", "code-block mt-4", state.configText)
		card.append(pre)

		const actions = element("div", "mt-5 flex flex-wrap gap-3")

		const download = element("button", "btn-primary", t("gen_download_conf"))
		download.type = "button"
		download.addEventListener("click", () => {
			const server = selectedServer()
			if (server) downloadConfig(state.configText, configFileName(server))
		})

		const copy = element(
			"button",
			"btn-ghost",
			state.copied ? t("gen_copied") : t("gen_copy"),
		)
		copy.type = "button"
		copy.addEventListener("click", async () => {
			try {
				await navigator.clipboard.writeText(state.configText)
				state.copied = true
				render()
			} catch {
				/* clipboard may be blocked; the text is selectable in the page */
			}
		})

		const restart = element("button", "btn-ghost", t("gen_restart"))
		restart.type = "button"
		restart.addEventListener("click", reset)

		actions.append(download, copy, restart)
		card.append(actions)

		wrapper.append(card, renderNotices())
		return wrapper
	}

	function render() {
		root.replaceChildren()
		root.append(renderSteps())

		if (state.step === "login") root.append(renderLogin())
		else if (state.step === "config") root.append(renderConfig())
		else root.append(renderSettings())
	}

	onLanguageChange(render)
	render()
}
