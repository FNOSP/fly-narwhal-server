import { computed, ref } from 'vue'
import zhCN from '../i18n/zh-CN'
import zhTW from '../i18n/zh-TW'
import en from '../i18n/en'

/**
 * UI language preference: one of the three codes in LOCALES. The visitor's
 * explicit pick is stored under `fwn:ui:locale`; with nothing stored we fall
 * back to navigator.language (zh-TW/HK/Hant → Traditional, other zh →
 * Simplified, anything else → English).
 *
 * The initial `lang` attribute is applied before first paint by an inline
 * script in index.html (same storage key, same detection order) so a reload
 * never flashes the wrong language. This module reads that attribute as its
 * initial value so JS state and DOM never disagree — same arrangement as
 * useTheme.js and data-theme.
 *
 * Module-level shared state (same pattern as useTheme.js / useRelease.js):
 * App.vue, every section, and both standalone hash-route pages import this via
 * independent paths, so a single ref has to live at module scope. Built inside
 * the exported function, two import paths would each hold their own ref and a
 * switch on one page would not reach the other.
 */
const LOCALE_STORAGE_KEY = 'fwn:ui:locale'
export const LOCALES = ['zh-CN', 'zh-TW', 'en']

const CATALOGS = { 'zh-CN': zhCN, 'zh-TW': zhTW, en }

// BCP 47 tags for Intl/toLocaleDateString — 'en' alone is accepted but 'en-US'
// pins the date format so it never drifts with the runtime's default.
const INTL_LOCALE = { 'zh-CN': 'zh-CN', 'zh-TW': 'zh-TW', en: 'en-US' }

function detectLocale() {
    for (const tag of navigator.languages || [navigator.language || '']) {
        const s = String(tag).toLowerCase()
        if (/^zh-(tw|hk|mo|hant)/.test(s)) return 'zh-TW'
        if (s.startsWith('zh')) return 'zh-CN'
        if (s.startsWith('en')) return 'en'
    }
    return 'en'
}

function readStoredLocale() {
    try {
        const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
        if (LOCALES.includes(stored)) return stored
    } catch {
        // localStorage unavailable (private mode etc.) — fall through.
    }
    return null
}

function readInitialLocale() {
    const stored = readStoredLocale()
    if (stored) return stored
    // No (valid) stored value: the head script already resolved the detection
    // into documentElement.lang. Reuse it so the first render matches what was
    // painted; only if it is missing or foreign do we detect afresh.
    const attr = document.documentElement.getAttribute('lang')
    if (LOCALES.includes(attr)) return attr
    return detectLocale()
}

const locale = ref(readInitialLocale())
// The active message catalog. Components read `t.section.key` in templates.
const t = computed(() => CATALOGS[locale.value])
const intlLocale = computed(() => INTL_LOCALE[locale.value])

/** Fill `{name}` slots. Params that are missing render as empty, not "undefined". */
export function format(str, params) {
    if (!str || !params) return str
    return str.replace(/\{(\w+)\}/g, (_, k) => (params[k] ?? ''))
}

function applyMeta(loc) {
    document.documentElement.setAttribute('lang', loc)
    document.title = CATALOGS[loc].meta.title
    const meta = document.querySelector('meta[name="description"]')
    if (meta) meta.setAttribute('content', CATALOGS[loc].meta.description)
}

function writeLocale(loc) {
    try {
        localStorage.setItem(LOCALE_STORAGE_KEY, loc)
    } catch {
        // Non-fatal: the choice still holds for this page session.
    }
}

/**
 * Pick a language. Validated against LOCALES; a no-op pick is ignored.
 */
export function setLocale(loc) {
    if (!LOCALES.includes(loc) || loc === locale.value) return
    locale.value = loc
    writeLocale(loc)
    applyMeta(loc)
}

export function useI18n() {
    return { locale, t, intlLocale, setLocale, format }
}

// Title/description are not set by the head script (it only writes lang), so
// sync them once here on module load — same as useTheme's bottom-level effect.
applyMeta(locale.value)

// Dev-only key-tree check: a missing or extra key in a translation would
// otherwise render as blank/undefined at runtime. Compare every locale against
// the zh-CN source and warn on the first divergence per path.
if (import.meta.env.DEV) {
    const walk = (base, other, path) => {
        for (const key of Object.keys(base)) {
            const here = `${path}.${key}`
            if (!(key in other)) {
                console.warn(`[i18n] missing key: ${here}`)
            } else if (Array.isArray(base[key]) && Array.isArray(other[key])) {
                if (base[key].length !== other[key].length) {
                    console.warn(`[i18n] array length mismatch: ${here} (zh-CN ${base[key].length} vs ${other[key].length})`)
                }
                base[key].forEach((item, i) => {
                    if (item && typeof item === 'object') walk(item, other[key][i] || {}, `${here}[${i}]`)
                })
            } else if (base[key] && typeof base[key] === 'object') {
                walk(base[key], other[key] || {}, here)
            }
        }
    }
    for (const code of LOCALES) {
        if (code !== 'zh-CN') walk(zhCN, CATALOGS[code], code)
    }
}
