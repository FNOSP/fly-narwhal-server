<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import SiteNav from './components/SiteNav.vue'
import HeroSection from './components/HeroSection.vue'
import RenewSection from './components/RenewSection.vue'
import ScreenshotSection from './components/ScreenshotSection.vue'
import FeatureSection from './components/FeatureSection.vue'
import PlayerSection from './components/PlayerSection.vue'
import DanmuSection from './components/DanmuSection.vue'
import SkipSection from './components/SkipSection.vue'
import ThumbnailSection from './components/ThumbnailSection.vue'
import MoreFeaturesSection from './components/MoreFeaturesSection.vue'
import SecuritySection from './components/SecuritySection.vue'
import ServerSection from './components/ServerSection.vue'
import PlatformsSection from './components/PlatformsSection.vue'
import DownloadSection from './components/DownloadSection.vue'
import ChangelogSection from './components/ChangelogSection.vue'
import FaqSection from './components/FaqSection.vue'
import CtaSection from './components/CtaSection.vue'
import TimelinePage from './components/TimelinePage.vue'
import CreditsPage from './components/CreditsPage.vue'
import GuidePage from './components/GuidePage.vue'
import SupportPage from './components/SupportPage.vue'
import AdminPage from './components/admin/AdminPage.vue'
import SiteFooter from './components/SiteFooter.vue'
import { useRelease } from './composables/useRelease'

const { loading, tag, releaseUrl, platformGroups, versionLabel, publishedLabel, osRules } = useRelease()

// The old one-shot bootstrap code is gone; "获取授权码" now opens the admin
// page (same SPA) where per-client codes are created.
function openAdmin() {
    window.location.hash = '#/admin'
}

// Standalone pages live behind `#/timeline` (full changelog), `#/credits`
// (open-source acknowledgements), `#/guide` (usage guide) and `#/support`
// (support the author); everything else renders the landing page.
// A tiny hash router — no dependency — keeps section anchors like `#features`
// working as before.
function routeFromHash() {
    const hash = window.location.hash
    if (hash.startsWith('#/timeline')) return 'timeline'
    if (hash.startsWith('#/credits')) return 'credits'
    if (hash.startsWith('#/guide')) return 'guide'
    if (hash.startsWith('#/support')) return 'support'
    if (hash.startsWith('#/admin')) return 'admin'
    return 'home'
}

const route = ref(routeFromHash())

function onHashChange() {
    const next = routeFromHash()
    if (next !== route.value) {
        route.value = next
        nextTick(() => window.scrollTo(0, 0))
        return
    }
    if (next === 'home') {
        // Landing anchors: after a view swap the target may not have existed
        // when the browser tried to jump, so jump again once it is mounted.
        const id = window.location.hash.slice(1)
        nextTick(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
    }
}

// Nav goes opaque once the hero has scrolled past; the buttons stay pinned and
// readable the whole way down either way. `docProgress` additionally feeds the
// reading-progress hairline under the nav.
const scrolled = ref(0)
const docProgress = ref(0)
let frame = 0

function measure() {
    frame = 0
    scrolled.value = window.scrollY || 0
    const doc = document.documentElement
    const max = doc.scrollHeight - window.innerHeight
    docProgress.value = max > 0 ? Math.min(1, Math.max(0, scrolled.value / max)) : 0
}

function onScroll() {
    if (frame) return
    frame = requestAnimationFrame(measure)
}

onMounted(() => {
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll, { passive: true })
    window.addEventListener('hashchange', onHashChange)
    measure()
})

onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll)
    window.removeEventListener('resize', onScroll)
    window.removeEventListener('hashchange', onHashChange)
    if (frame) cancelAnimationFrame(frame)
})

const navSolid = computed(() => scrolled.value > 40)

function scrollToDownload() {
    document.getElementById('download')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
    <TimelinePage v-if="route === 'timeline'" />
    <CreditsPage v-else-if="route === 'credits'" />
    <GuidePage v-else-if="route === 'guide'" />
    <SupportPage v-else-if="route === 'support'" />
    <AdminPage v-else-if="route === 'admin'" />

    <template v-else>
        <SiteNav :solid="navSolid" :progress="docProgress" @download="scrollToDownload" @auth="openAdmin" />

        <main>
            <HeroSection :version-label="versionLabel" @download="scrollToDownload" @auth="openAdmin" />
            <RenewSection />
            <ScreenshotSection />
            <FeatureSection />
            <PlayerSection />
            <DanmuSection />
            <SkipSection />
            <ThumbnailSection />
            <MoreFeaturesSection />
            <SecuritySection />
            <ServerSection />
            <PlatformsSection />
            <DownloadSection
                :platform-groups="platformGroups"
                :loading="loading"
                :tag="tag"
                :published-label="publishedLabel"
                :release-url="releaseUrl"
                :os-rules="osRules"
            />
            <ChangelogSection />
            <FaqSection />
            <CtaSection @download="scrollToDownload" @auth="openAdmin" />
        </main>

        <SiteFooter />

    </template>
</template>
