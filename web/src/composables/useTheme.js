import { computed, ref } from 'vue'

/**
 * Light/dark theme switch.
 *
 * The initial `data-theme` attribute is applied before first paint by an inline
 * script in index.html (same storage key, same system-preference fallback) so a
 * reload with a stored dark choice never flashes light. This module reads that
 * attribute as its initial value so JS state and DOM never disagree.
 *
 * Module-level shared state (same pattern as useChangelog.js): SiteNav,
 * TimelinePage and CreditsPage are never mounted at the same time, so one ref
 * carries the visitor's choice across hash-route swaps. Only an explicit toggle
 * is persisted; absence of the key means "follow the system" at every load, and
 * the matchMedia listener below keeps following it live until the visitor picks.
 */
const THEME_STORAGE_KEY = 'fwn:ui:theme'
const THEME_COLOR_META = { light: '#F5F5F7', dark: '#0A0A0C' }

function readInitialTheme() {
    const attr = document.documentElement.getAttribute('data-theme')
    if (attr === 'light' || attr === 'dark') return attr
    // Head script did not run (e.g. component tests) — redo its fallbacks.
    try {
        const stored = localStorage.getItem(THEME_STORAGE_KEY)
        if (stored === 'light' || stored === 'dark') return stored
    } catch {
        // localStorage unavailable (private mode etc.) — fall through.
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

const theme = ref(readInitialTheme())
const isDark = computed(() => theme.value === 'dark')

function applyTheme(next) {
    document.documentElement.setAttribute('data-theme', next)
    // Keep the mobile browser chrome in sync with the actual theme. A single
    // JS-updated meta beats dual media-query metas, which would follow the
    // system instead of the visitor's explicit choice.
    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.setAttribute('content', THEME_COLOR_META[next])
}

function hasStoredPreference() {
    try {
        const stored = localStorage.getItem(THEME_STORAGE_KEY)
        return stored === 'light' || stored === 'dark'
    } catch {
        return false
    }
}

// Follow OS-level theme changes live, but only while the visitor has not made
// an explicit choice on this site.
const darkQuery = window.matchMedia('(prefers-color-scheme: dark)')
darkQuery.addEventListener('change', (e) => {
    if (hasStoredPreference()) return
    theme.value = e.matches ? 'dark' : 'light'
    applyTheme(theme.value)
})

export function toggleTheme() {
    theme.value = isDark.value ? 'light' : 'dark'
    applyTheme(theme.value)
    try {
        localStorage.setItem(THEME_STORAGE_KEY, theme.value)
    } catch {
        // Non-fatal: the switch still works for this page session.
    }
}

export function useTheme() {
    return { theme, isDark, toggleTheme }
}
