import { computed, ref } from 'vue'

/**
 * Light/dark theme preference: an explicit 'light' or 'dark' pick, or
 * 'system' (the default) which follows the OS setting and keeps following it
 * live. Storing 'system' is the same as never having stored anything, but the
 * key stays explicit so the head script and this module agree.
 *
 * The initial `data-theme` attribute is applied before first paint by an inline
 * script in index.html (same storage key, same system fallback) so a reload
 * never flashes the wrong theme. This module reads that attribute as its
 * initial value so JS state and DOM never disagree.
 *
 * Module-level shared state (same pattern as useChangelog.js): SiteNav,
 * TimelinePage and CreditsPage are never mounted at the same time, so one ref
 * carries the visitor's choice across hash-route swaps.
 */
const THEME_STORAGE_KEY = 'fwn:ui:theme'
const THEME_COLOR_META = { light: '#F5F5F7', dark: '#0A0A0C' }
export const THEME_MODES = ['system', 'light', 'dark']

function systemTheme() {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function readStoredPreference() {
    try {
        const stored = localStorage.getItem(THEME_STORAGE_KEY)
        if (THEME_MODES.includes(stored)) return stored
    } catch {
        // localStorage unavailable (private mode etc.) — fall through.
    }
    return null
}

function readInitialPreference() {
    const stored = readStoredPreference()
    if (stored) return stored
    // No (valid) stored value: the head script already resolved the OS setting
    // into data-theme. Reuse it so the first render matches what was painted.
    const attr = document.documentElement.getAttribute('data-theme')
    if (attr === 'light' || attr === 'dark') {
        return attr === systemTheme() ? 'system' : attr
    }
    return 'system'
}

const preference = ref(readInitialPreference())
// The theme actually painted: the preference itself, or the OS setting when
// the preference is 'system'.
const theme = computed(() => (preference.value === 'system' ? systemTheme() : preference.value))
const isDark = computed(() => theme.value === 'dark')

function applyTheme(mode) {
    document.documentElement.setAttribute('data-theme', mode)
    // Keep the mobile browser chrome in sync with the actual theme. A single
    // JS-updated meta beats dual media-query metas, which would follow the
    // system instead of the visitor's choice.
    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.setAttribute('content', THEME_COLOR_META[mode])
}

function writePreference(mode) {
    try {
        localStorage.setItem(THEME_STORAGE_KEY, mode)
    } catch {
        // Non-fatal: the choice still holds for this page session.
    }
}

/**
 * Pick a theme: 'system' follows the OS live, 'light'/'dark' pin it.
 */
export function setThemeMode(mode) {
    if (!THEME_MODES.includes(mode)) return
    preference.value = mode
    writePreference(mode)
    applyTheme(theme.value)
}

/** Flip to the opposite of what is currently painted, pinning it explicitly. */
export function toggleTheme() {
    setThemeMode(isDark.value ? 'light' : 'dark')
}

// Follow OS-level changes live, but only while the visitor has not pinned a
// theme of their own. The new value comes from the event, not a fresh
// matchMedia() read: the event carries the transition the browser just made,
// while a re-query can lag it (and a long-lived query object can miss it).
const darkQuery = window.matchMedia('(prefers-color-scheme: dark)')
darkQuery.addEventListener('change', (e) => {
    if (preference.value !== 'system') return
    applyTheme(e.matches ? 'dark' : 'light')
})

export function useTheme() {
    return { preference, theme, isDark, setThemeMode, toggleTheme }
}
