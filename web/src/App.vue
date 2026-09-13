<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import SiteNav from './components/SiteNav.vue'
import HeroSection from './components/HeroSection.vue'
import RenewSection from './components/RenewSection.vue'
import ScreenshotSection from './components/ScreenshotSection.vue'
import FeatureSection from './components/FeatureSection.vue'
import PlayerSection from './components/PlayerSection.vue'
import MoreFeaturesSection from './components/MoreFeaturesSection.vue'
import SecuritySection from './components/SecuritySection.vue'
import ServerSection from './components/ServerSection.vue'
import PlatformsSection from './components/PlatformsSection.vue'
import DownloadSection from './components/DownloadSection.vue'
import ChangelogSection from './components/ChangelogSection.vue'
import FaqSection from './components/FaqSection.vue'
import CtaSection from './components/CtaSection.vue'
import SiteFooter from './components/SiteFooter.vue'
import AuthCodeModal from './components/AuthCodeModal.vue'
import { useRelease } from './composables/useRelease'
import { useAuthCode } from './composables/useAuthCode'

const { loading, tag, releaseUrl, platformGroups, versionLabel, publishedLabel, osRules } = useRelease()
const auth = useAuthCode()

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
    measure()
})

onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll)
    window.removeEventListener('resize', onScroll)
    if (frame) cancelAnimationFrame(frame)
})

const navSolid = computed(() => scrolled.value > 40)

function scrollToDownload() {
    document.getElementById('download')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
    <SiteNav :solid="navSolid" :progress="docProgress" @download="scrollToDownload" @auth="auth.request" />

    <main>
        <HeroSection :version-label="versionLabel" :release-url="releaseUrl" @download="scrollToDownload" @auth="auth.request" />
        <RenewSection />
        <ScreenshotSection />
        <FeatureSection />
        <PlayerSection />
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
        <CtaSection :version-label="versionLabel" @download="scrollToDownload" @auth="auth.request" />
    </main>

    <SiteFooter />

    <AuthCodeModal
        :open="auth.open.value"
        :title="auth.title.value"
        :desc="auth.desc.value"
        :code="auth.code.value"
        :copied="auth.copied.value"
        @close="auth.close"
        @copy="auth.copy"
    />
</template>
