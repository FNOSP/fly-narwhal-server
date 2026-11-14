<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import ThemeToggle from './ThemeToggle.vue'
import LocaleToggle from './LocaleToggle.vue'
import logoSvg from '../assets/FNarwhal_login.svg?raw'
import { useI18n } from '../composables/useI18n'

defineProps({
    solid: { type: Boolean, default: false },
    // Whole-page reading progress, 0..1, drawn as a hairline under the nav.
    progress: { type: Number, default: 0 },
})

const emit = defineEmits(['download', 'auth'])

const { t } = useI18n()

// hrefs stay literal; labels come from the catalog.
const links = [
    { href: '#renew', key: 'renew' },
    { href: '#screenshots', key: 'screenshots' },
    { href: '#features', key: 'features' },
    { href: '#player', key: 'player' },
    { href: '#server', key: 'server' },
    { href: '#changelog', key: 'changelog' },
    { href: '#faq', key: 'faq' },
    { href: '#/guide', key: 'guide' },
]

// Below the desktop breakpoint the inline link row is replaced by this
// button; hovering it (mouse) or tapping it (touch/keyboard) reveals the
// same links in a dropdown. Kept open across the short gap between the
// trigger and the panel so moving the pointer down doesn't dismiss it.
const menuOpen = ref(false)
const menuRoot = ref(null)
let closeTimer = null

function cancelClose() {
    if (closeTimer) {
        clearTimeout(closeTimer)
        closeTimer = null
    }
}

function openMenu() {
    cancelClose()
    menuOpen.value = true
}

function scheduleClose() {
    cancelClose()
    closeTimer = setTimeout(() => {
        menuOpen.value = false
    }, 140)
}

function toggleMenu() {
    cancelClose()
    menuOpen.value = !menuOpen.value
}

function closeMenu() {
    cancelClose()
    menuOpen.value = false
}

function pickLink() {
    closeMenu()
}

function onDocumentPointerDown(event) {
    if (menuRoot.value && !menuRoot.value.contains(event.target)) closeMenu()
}

function onKeydown(event) {
    if (event.key === 'Escape') closeMenu()
}

onMounted(() => {
    document.addEventListener('pointerdown', onDocumentPointerDown)
    document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
    cancelClose()
    document.removeEventListener('pointerdown', onDocumentPointerDown)
    document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
    <header class="nav" :class="{ 'nav--solid': solid }">
        <div class="nav__inner shell">
            <a class="nav__brand" href="#top" :aria-label="t.nav.homeAria">
                <!-- Inlined so the wordmark paths can follow --logo-ink while
                     the whale icon keeps its original colors in both themes. -->
                <div class="nav__logo" role="img" :aria-label="t.nav.logoAria" v-html="logoSvg"></div>
            </a>

            <nav class="nav__links" :aria-label="t.nav.linksAria">
                <a v-for="link in links" :key="link.href" :href="link.href" class="nav__link">
                    {{ t.nav.links[link.key] }}
                </a>
            </nav>

            <div
                ref="menuRoot"
                class="nav__menu"
                :class="{ 'is-open': menuOpen }"
                @mouseenter="openMenu"
                @mouseleave="scheduleClose"
            >
                <button
                    type="button"
                    class="nav__menu-trigger"
                    :aria-label="t.nav.menuAria"
                    :title="t.nav.menuAria"
                    aria-haspopup="true"
                    :aria-expanded="menuOpen"
                    @click="toggleMenu"
                    @focus="openMenu"
                >
                    <span class="nav__menu-bars" aria-hidden="true">
                        <span></span><span></span><span></span>
                    </span>
                </button>

                <div class="nav__menu-panel" role="menu" :aria-label="t.nav.linksAria">
                    <a
                        v-for="link in links"
                        :key="link.href"
                        :href="link.href"
                        class="nav__menu-link"
                        role="menuitem"
                        @click="pickLink"
                    >
                        {{ t.nav.links[link.key] }}
                    </a>
                </div>
            </div>

            <div class="nav__actions">
                <LocaleToggle />
                <ThemeToggle />
                <button class="btn btn-ghost nav__auth" type="button" @click="emit('auth')">{{ t.nav.getAuthCode }}</button>
                <button class="btn btn-primary nav__download" type="button" @click="emit('download')">{{ t.nav.download }}</button>
            </div>
        </div>

        <div class="nav__progress" aria-hidden="true">
            <div class="nav__progress-fill" :style="{ transform: `scaleX(${progress})` }"></div>
        </div>
    </header>
</template>

<style scoped>
.nav {
    position: fixed;
    inset: 0 0 auto 0;
    z-index: 100;
    height: var(--nav-h);
    display: flex;
    align-items: center;
    /* Fades in as the hero scrolls away; the buttons themselves never hide. */
    background: transparent;
    border-bottom: 1px solid transparent;
    transition: background-color 0.35s var(--ease), border-color 0.35s var(--ease),
        backdrop-filter 0.35s var(--ease);
}

.nav--solid {
    background: var(--chrome-glass);
    border-bottom-color: var(--hairline);
    backdrop-filter: saturate(180%) blur(20px);
    -webkit-backdrop-filter: saturate(180%) blur(20px);
}

.nav__inner {
    display: flex;
    align-items: center;
    gap: 20px;
    width: 100%;
}

.nav__brand {
    display: flex;
    align-items: center;
    flex-shrink: 0;
}

/* Only the wordmark flips (--logo-ink via currentColor-style var on the
   inlined SVG's paths); the whale icon stays colored in dark mode. The svg
   comes through v-html, so :deep() is needed for sizing. */
.nav__logo {
    --logo-ink: #4A5568;
    display: block;
}

html[data-theme='dark'] .nav__logo {
    --logo-ink: #F5F5F7;
}

.nav__logo :deep(svg) {
    display: block;
    height: 30px;
    width: auto;
}

.nav__links {
    display: flex;
    align-items: center;
    gap: 20px;
    margin-left: 14px;
}

.nav__link {
    font-size: 13.5px;
    font-weight: 500;
    color: var(--ink-soft);
    transition: color 0.2s var(--ease);
    white-space: nowrap;
}

.nav__link:hover {
    color: var(--brand);
}

.nav__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    /* Pins the action buttons to the right edge, per the floating-button spec. */
    margin-left: auto;
}

.nav__actions .btn {
    padding: 9px 18px;
    font-size: 14px;
}

/* Hamburger fallback for widths where the inline link row no longer fits.
   Sits just right of the brand, taking the link row's place in the layout;
   mirrors ThemeToggle/LocaleToggle's circular icon-button chrome so it reads
   as native to the bar rather than a bolted-on control. Pulls back most of
   .nav__inner's flex gap so it hugs the logo instead of floating in space. */
.nav__menu {
    display: none;
    position: relative;
    flex-shrink: 0;
    margin-left: -12px;
}

.nav__menu-trigger {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    color: var(--ink-soft);
    transition: background-color 0.25s var(--ease), color 0.25s var(--ease);
}

.nav__menu-trigger:hover,
.nav__menu.is-open .nav__menu-trigger {
    background: var(--field);
    color: var(--brand);
}

.nav__menu-bars {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    width: 16px;
    height: 14px;
}

.nav__menu-bars span {
    display: block;
    width: 100%;
    height: 1.8px;
    border-radius: 2px;
    background: currentColor;
    transition: transform 0.28s var(--ease), opacity 0.2s var(--ease);
}

.nav__menu.is-open .nav__menu-bars span:nth-child(1) {
    transform: translateY(5.8px) rotate(45deg);
}

.nav__menu.is-open .nav__menu-bars span:nth-child(2) {
    opacity: 0;
    transform: scaleX(0.3);
}

.nav__menu.is-open .nav__menu-bars span:nth-child(3) {
    transform: translateY(-5.8px) rotate(-45deg);
}

.nav__menu-panel {
    position: absolute;
    top: calc(100% + 10px);
    left: 0;
    z-index: 1;
    min-width: 208px;
    max-height: min(70vh, 480px);
    overflow-y: auto;
    padding: 6px;
    border-radius: var(--radius-sm);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-md);
    opacity: 0;
    visibility: hidden;
    transform: translateY(-6px) scale(0.97);
    transform-origin: top left;
    pointer-events: none;
    transition: opacity 0.2s var(--ease), transform 0.2s var(--ease), visibility 0s linear 0.2s;
}

/* Bridges the visual gap to the trigger so moving the pointer from the icon
   down into the panel doesn't cross dead space and dismiss it mid-move. */
.nav__menu-panel::before {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: -10px;
    height: 10px;
}

.nav__menu.is-open .nav__menu-panel {
    opacity: 1;
    visibility: visible;
    transform: none;
    pointer-events: auto;
    transition: opacity 0.22s var(--ease), transform 0.22s var(--ease), visibility 0s;
}

.nav__menu-link {
    display: block;
    width: 100%;
    padding: 9px 12px;
    border-radius: 8px;
    font-size: 13.5px;
    font-weight: 500;
    color: var(--ink-soft);
    text-align: left;
    white-space: nowrap;
    transition: background-color 0.18s var(--ease), color 0.18s var(--ease);
}

.nav__menu-link:hover {
    background: var(--field);
    color: var(--ink);
}

/* Reading-progress hairline pinned to the nav's bottom edge. */
.nav__progress {
    position: absolute;
    inset: auto 0 0 0;
    height: 2px;
    opacity: 0;
    transition: opacity 0.35s var(--ease);
}

.nav--solid .nav__progress {
    opacity: 1;
}

.nav__progress-fill {
    height: 100%;
    transform-origin: 0 50%;
    background: linear-gradient(90deg, var(--brand), #5E5CE6 70%, #BF5AF2);
    will-change: transform;
}

@media (max-width: 1140px) {
    .nav__links {
        display: none;
    }

    .nav__menu {
        display: block;
    }
}

@media (max-width: 520px) {
    .nav__logo :deep(svg) {
        height: 24px;
    }

    /* The hero right below already carries both CTAs at full width, so the
       bar drops them here rather than duplicating them into the menu too. */
    .nav__auth,
    .nav__download {
        display: none;
    }

    .nav__inner {
        gap: 10px;
        padding: 0 14px;
    }

    /* Same net ~8px brand-to-menu gap as the wider breakpoint, adjusted for
       this breakpoint's smaller flex gap. */
    .nav__menu {
        margin-left: -2px;
    }
}
</style>
