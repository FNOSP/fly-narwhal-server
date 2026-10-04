<script setup>
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
]
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

            <div class="nav__actions">
                <LocaleToggle />
                <ThemeToggle />
                <button class="btn btn-ghost nav__auth" type="button" @click="emit('auth')">{{ t.nav.getAuthCode }}</button>
                <button class="btn btn-primary" type="button" @click="emit('download')">{{ t.nav.download }}</button>
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
}

@media (max-width: 520px) {
    .nav__logo :deep(svg) {
        height: 24px;
    }

    .nav__actions .btn {
        padding: 8px 13px;
        font-size: 13px;
    }

    .nav__inner {
        gap: 10px;
        padding: 0 14px;
    }
}

@media (max-width: 380px) {
    .nav__brand {
        display: none;
    }

    .nav__actions {
        width: 100%;
        justify-content: flex-end;
    }
}
</style>
