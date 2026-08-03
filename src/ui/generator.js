/**
 * Config generator wizard: state and flow.
 *
 * Guest session -> server picked from the flag cards -> obfuscation and network
 * settings -> registered certificate -> downloadable `.conf`. Every call goes
 * through the CORS proxy served next to the site under `/api`; the private key
 * never leaves the tab.
 *
 * The session, the server list and the tier are cached for a day
 * (`lib/session.js`), so the usual flow is to log in once and then produce as
 * many configurations as wanted. Each configuration still gets its own fresh
 * key pair and its own certificate; only the login and the server list are
 * reused.
 *
 * Rendering lives in `generator-view.js`.
 */

import { t, onLanguageChange } from "../i18n/index.js"
import { ApiError, ProxyUnreachableError } from "../lib/api.js"
import { loginAsGuest, VerificationExhaustedError } from "../lib/auth.js"
import { Ed25519UnsupportedError, generateVpnKeys } from "../lib/crypto.js"
import { registerCertificate } from "../lib/cert.js"
import { advancedFromPreset, generateHeaderProtectionKey, nextI1, presetById } from "../lib/awg.js"
import { DomainI1UnsupportedError, generateI1FromDomain } from "../lib/quic.js"
import { DEFAULT_MTU, DEFAULT_PORT, buildConfig, configFileName, downloadConfig } from "../lib/conf.js"
import {
	FASTEST_ID,
	TIER_FREE,
	fastestServer,
	fetchLoads,
	fetchMaxTier,
	fetchServers,
	prepareServers,
} from "../lib/servers.js"
import {
	clearCachedSession,
	hoursRemaining,
	loadCachedSession,
	loadsAreStale,
	saveCachedSession,
	updateCachedServers,
} from "../lib/session.js"
import {
	ALL_COUNTRIES,
	button,
	countryPicker,
	element,
	networkCard,
	notices,
	obfuscationCard,
	serverPicker,
	stepBar,
} from "./generator-view.js"

const STEPS = [
	{ id: "login", labelKey: "gen_step_login" },
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
	i1: "gen_progress_i1",
}

function errorKeyFor(error) {
	if (error instanceof ProxyUnreachableError) return "gen_error_proxy"
	if (error instanceof VerificationExhaustedError) return "gen_error_verification"
	if (error instanceof Ed25519UnsupportedError) return "gen_error_crypto"
	if (error instanceof DomainI1UnsupportedError) return "gen_error_domain"
	return "gen_error_generic"
}

/**
 * A cached session eventually stops being accepted, either because it expired
 * early or because Proton dropped it. That is not a real failure: the cache is
 * cleared and the visitor is sent back to the login step.
 */
function isSessionRejected(error) {
	return error instanceof ApiError && (error.status === 401 || error.code === 401)
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
		cache: null,
		country: ALL_COUNTRIES,
		serverId: FASTEST_ID,
		presetId: "vpn-next-default",
		advanced: false,
		advancedParams: advancedFromPreset("vpn-next-default"),
		domain: "",
		dnsId: "cloudflare",
		customDns: "",
		mtu: DEFAULT_MTU,
		port: DEFAULT_PORT,
		allowedIps: "0.0.0.0/0",
		extendedCert: true,
		configText: "",
		configServer: null,
		certExpiry: null,
		copied: false,
		generatedCount: 0,
	}

	/* ---------- session ---------- */

	function restoreCachedSession() {
		const cache = loadCachedSession()
		if (!cache) return

		state.cache = cache
		state.session = cache.session
		state.profile = cache.profile
		state.maxTier = cache.maxTier
		state.servers = cache.servers
		state.step = "settings"

		// Load figures age far faster than the session, so a returning visitor
		// quietly gets fresh numbers instead of yesterday's.
		if (loadsAreStale(cache)) refreshServers({ quiet: true })
	}

	function dropSession() {
		clearCachedSession()
		state.cache = null
		state.session = null
		state.profile = null
		state.servers = []
		state.maxTier = TIER_FREE
		state.step = "login"
	}

	function fail(error) {
		if (error?.name === "AbortError") return

		if (isSessionRejected(error)) {
			dropSession()
			state.errorKey = "gen_error_session_expired"
		} else {
			state.errorKey = errorKeyFor(error)
		}

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
					if (!PROGRESS_KEYS[stage]) return
					state.progressKey = PROGRESS_KEYS[stage]
					render()
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
			state.cache = saveCachedSession({
				session: state.session,
				profile: state.profile,
				maxTier: state.maxTier,
				servers: state.servers,
			})

			state.step = "settings"
			state.busy = false
			state.progressKey = null
			render()
		} catch (error) {
			fail(error)
		}
	}

	async function refreshServers({ quiet = false } = {}) {
		if (!state.session) return

		if (!quiet) {
			state.busy = true
			state.progressKey = PROGRESS_KEYS.servers
			render()
		}

		try {
			const context = { profile: state.profile, session: state.session }
			const [logicals, loads] = await Promise.all([fetchServers(context), fetchLoads(context)])

			state.servers = prepareServers(logicals, loads, state.maxTier)
			state.cache = updateCachedServers(state.servers) ?? state.cache
			state.busy = false
			state.progressKey = null
			render()
		} catch (error) {
			// A background refresh must never throw away a usable cached list.
			if (quiet && !isSessionRejected(error)) return
			fail(error)
		}
	}

	/* ---------- selection ---------- */

	function visibleServers() {
		return state.country === ALL_COUNTRIES
			? state.servers
			: state.servers.filter((server) => server.exitCountry === state.country)
	}

	/** Resolves the picker selection, including the "fastest" pseudo-entry. */
	function selectedServer() {
		const candidates = visibleServers()
		if (state.serverId === FASTEST_ID) return fastestServer(candidates)
		return candidates.find((server) => server.id === state.serverId) ?? fastestServer(candidates)
	}

	function obfuscationParams() {
		return state.advanced ? { ...state.advancedParams } : presetById(state.presetId).params()
	}

	const handlers = {
		setAdvanced(value) {
			// Opening the editor should show what the preset was doing, not a
			// blank form.
			if (value && !state.advanced) state.advancedParams = advancedFromPreset(state.presetId)
			state.advanced = value
			render()
		},
		setPreset(id) {
			state.presetId = id
			render()
		},
		setParam(key, value) {
			// Deliberately no re-render: the caret must stay where it is.
			state.advancedParams = { ...state.advancedParams, [key]: value }
		},
		resetAdvanced() {
			state.advancedParams = advancedFromPreset(state.presetId)
			render()
		},
		generateHeaderKey() {
			state.advancedParams = { ...state.advancedParams, HeaderProtectionKey: generateHeaderProtectionKey() }
			render()
		},
		rotateI1() {
			state.advancedParams = { ...state.advancedParams, I1: nextI1(state.advancedParams.I1) }
			render()
		},
		setDomain(value) {
			state.domain = value
		},
		async applyDomainI1() {
			const domain = state.domain.trim()
			if (!domain) return

			state.busy = true
			state.errorKey = null
			state.progressKey = PROGRESS_KEYS.i1
			render()

			try {
				const i1 = await generateI1FromDomain(domain)
				state.advancedParams = { ...state.advancedParams, I1: i1 }
				state.busy = false
				state.progressKey = null
				render()
			} catch (error) {
				fail(error)
			}
		},
		setDns(id) {
			state.dnsId = id
			render()
		},
		setCustomDns(value) {
			state.customDns = value
		},
		setPort(value) {
			state.port = value
			render()
		},
		setMtu(value) {
			state.mtu = value
		},
		setAllowedIps(value) {
			state.allowedIps = value
		},
		setExtendedCert(value) {
			state.extendedCert = value
		},
	}

	/* ---------- generation ---------- */

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
				awgParams: obfuscationParams(),
				dnsId: state.dnsId,
				customDns: state.customDns,
				mtu: state.mtu,
				port: state.port,
				allowedIps: state.allowedIps,
			})
			state.certExpiry = certificate.expirationTime
			state.configServer = server
			state.generatedCount += 1
			state.copied = false
			state.step = "config"
			state.busy = false
			state.progressKey = null
			render()
		} catch (error) {
			fail(error)
		}
	}

	/** Back to the settings step with the session intact, for the next config. */
	function generateAnother() {
		state.step = "settings"
		state.configText = ""
		state.certExpiry = null
		state.configServer = null
		state.copied = false
		state.errorKey = null
		render()
	}

	function startOver() {
		dropSession()
		state.configText = ""
		state.certExpiry = null
		state.configServer = null
		state.errorKey = null
		state.copied = false
		state.generatedCount = 0
		render()
	}

	/* ---------- steps ---------- */

	function renderLogin() {
		const card = element("div", "card")

		const warning = element("div", "rounded-xl border border-brand-light/30 bg-brand/10 p-4")
		warning.append(
			element("p", "text-sm font-semibold text-brand-light", t("gen_warning_title")),
			element("p", "mt-2 text-sm text-slate-300", t("gen_warning_text")),
		)

		card.append(
			warning,
			element("p", "mt-4 text-xs text-slate-500", t("gen_cache_explainer")),
			button("btn-primary mt-6", t("gen_start"), startSession, { disabled: state.busy }),
			notices(state),
		)
		return card
	}

	function renderSessionSummary() {
		const card = element("div", "card")

		const header = element("div", "flex flex-wrap items-start justify-between gap-3")
		header.append(element("p", "text-sm font-semibold text-white", t("gen_session_ready")))
		if (state.cache) {
			header.append(element("span", "badge", `${t("gen_cached")} \u00b7 ${hoursRemaining(state.cache)} ${t("gen_cache_hours")}`))
		}
		card.append(header)

		const list = element("dl", "mt-4 grid gap-4 sm:grid-cols-4")
		const rows = [
			[t("gen_device_profile"), state.profile?.model ?? state.profile?.id ?? "\u2014"],
			[t("gen_tier"), state.maxTier === TIER_FREE ? t("gen_tier_free") : String(state.maxTier)],
			[t("gen_servers_count"), String(state.servers.length)],
			[t("gen_generated_count"), String(state.generatedCount)],
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

		const actions = element("div", "mt-5 flex flex-wrap gap-3")
		actions.append(
			button("btn-ghost btn-sm", t("gen_refresh"), () => refreshServers(), { disabled: state.busy }),
			button("btn-ghost btn-sm", t("gen_new_session"), startOver, { disabled: state.busy }),
		)
		card.append(actions)

		return card
	}

	function renderSettings() {
		const wrapper = element("div", "space-y-6")
		const candidates = visibleServers()

		wrapper.append(
			renderSessionSummary(),
			countryPicker({
				servers: state.servers,
				selected: state.country,
				onSelect: (country) => {
					state.country = country
					state.serverId = FASTEST_ID
					render()
				},
			}),
			serverPicker({
				servers: candidates,
				fastest: fastestServer(candidates),
				fastestId: FASTEST_ID,
				selectedId: state.serverId,
				onSelect: (id) => {
					state.serverId = id
					render()
				},
			}),
			obfuscationCard({
				advanced: state.advanced,
				presetId: state.presetId,
				params: state.advancedParams,
				busy: state.busy,
				domain: state.domain,
				handlers,
			}),
			networkCard({
				dnsId: state.dnsId,
				customDns: state.customDns,
				port: state.port,
				mtu: state.mtu,
				allowedIps: state.allowedIps,
				extendedCert: state.extendedCert,
				handlers,
			}),
		)

		const footer = element("div")
		footer.append(
			button("btn-primary", state.busy ? t("gen_generating") : t("gen_generate"), generate, {
				disabled: state.busy || candidates.length === 0,
			}),
			notices(state),
		)
		wrapper.append(footer)

		return wrapper
	}

	function renderConfig() {
		const card = element("div", "card")
		card.append(element("h3", "text-sm font-semibold text-white", t("gen_result_title")))

		if (state.configServer) {
			card.append(element("p", "mt-1 text-xs text-slate-500", state.configServer.name))
		}
		if (state.certExpiry) {
			const expires = new Date(state.certExpiry * 1000).toLocaleString(document.documentElement.lang)
			card.append(element("p", "mt-1 text-xs text-slate-500", `${t("gen_cert_expires")}: ${expires}`))
		}

		const code = element("pre", "code-block mt-4")
		code.append(element("code", "", state.configText))
		card.append(code)

		const actions = element("div", "mt-5 flex flex-wrap gap-3")
		actions.append(
			button("btn-primary", t("gen_download_conf"), () => {
				downloadConfig(state.configText, configFileName(state.configServer))
			}),
			button("btn-ghost", state.copied ? t("gen_copied") : t("gen_copy"), async () => {
				await navigator.clipboard.writeText(state.configText)
				state.copied = true
				render()
			}),
			// The session is still good, so another config is one click away.
			button("btn-ghost", t("gen_another"), generateAnother),
			button("btn-ghost", t("gen_restart"), startOver),
		)
		card.append(actions, notices(state))

		return card
	}

	function render() {
		root.replaceChildren()

		const section = element("div", "container-page")
		section.append(
			element("h2", "section-title", t("gen_title")),
			element("p", "section-subtitle", t("gen_subtitle")),
			stepBar(STEPS, state.step),
		)

		if (state.step === "login") section.append(renderLogin())
		else if (state.step === "settings") section.append(renderSettings())
		else section.append(renderConfig())

		root.append(section)
	}

	restoreCachedSession()
	render()
	onLanguageChange(render)
}
