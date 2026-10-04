<script setup>
import { computed } from 'vue'
import { PLATFORM_LOGOS } from '../assets/platforms'
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()

// Platform names and architecture labels are language-neutral tokens; only the
// note under each card is copy.
const platformDefs = [
    { os: 'windows', name: 'Windows', arch: ['x64', 'ARM64'] },
    { os: 'macos', name: 'macOS', arch: ['Intel x64', 'Apple Silicon'] },
    { os: 'linux', name: 'Linux', arch: ['x64', 'arm64'] },
]

const platforms = computed(() =>
    platformDefs.map((p) => ({ ...p, note: t.value.platforms.notes[p.os] })),
)

// The marquee track is rendered twice; the animation shifts it exactly one
// copy's width so the loop is seamless. Extensions stay literal; the three
// badge words follow the UI language.
const formats = computed(() => [
    '.exe', '.dmg', '.deb', '.rpm', '.pkg.tar.zst', '.AppImage', 'SHA256SUMS',
    t.value.platforms.badges.mirror,
    t.value.platforms.badges.autoUpdate,
    t.value.platforms.badges.portable,
])
</script>

<template>
    <section id="platforms" class="plats">
        <div class="shell">
            <div class="plats__head">
                <h2 v-reveal class="plats__title">{{ t.platforms.title }}</h2>
                <p v-reveal="100" class="plats__lede">
                    {{ t.platforms.lede }}
                </p>
            </div>

            <div class="plats__grid">
                <article v-for="(p, i) in platforms" :key="p.os" v-reveal="i * 110" class="plat">
                    <svg
                        class="plat__logo"
                        :viewBox="PLATFORM_LOGOS[p.os].viewBox"
                        aria-hidden="true"
                        :fill="PLATFORM_LOGOS[p.os].stroke ? 'none' : 'currentColor'"
                        :stroke="PLATFORM_LOGOS[p.os].stroke ? 'currentColor' : 'none'"
                        stroke-width="1.5"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                    >
                        <path :d="PLATFORM_LOGOS[p.os].path" />
                    </svg>
                    <h3 class="plat__name">{{ p.name }}</h3>
                    <div class="plat__arch">
                        <span v-for="a in p.arch" :key="a">{{ a }}</span>
                    </div>
                    <p class="plat__note">{{ p.note }}</p>
                </article>
            </div>
        </div>

        <div class="marquee" aria-hidden="true">
            <div class="marquee__track">
                <span v-for="(f, i) in [...formats, ...formats]" :key="i" class="marquee__chip">{{ f }}</span>
            </div>
        </div>
    </section>
</template>

<style scoped>
.plats {
    padding: clamp(64px, 9vw, 110px) 0 0;
    background: var(--surface);
    overflow: hidden;
}

.plats__head {
    text-align: center;
    margin-bottom: clamp(36px, 5vw, 56px);
}

.plats__title {
    font-size: clamp(26px, 3.6vw, 42px);
    font-weight: 700;
    letter-spacing: -0.02em;
}

.plats__lede {
    margin-top: 14px;
    font-size: clamp(15px, 1.5vw, 17.5px);
    color: var(--ink-muted);
}

.plats__grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 18px;
    margin-bottom: clamp(48px, 6vw, 72px);
}

.plat {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 10px;
    padding: 34px 24px 30px;
    border-radius: var(--radius-lg);
    background: var(--bg);
    border: 1px solid var(--hairline);
    transition: transform 0.4s var(--ease), box-shadow 0.4s var(--ease),
        background-color 0.4s var(--ease);
}

.plat.is-revealed:hover {
    transform: translateY(-5px);
    background: var(--surface-2);
    box-shadow: var(--shadow-md);
}

.plat__logo {
    width: 38px;
    height: 38px;
    color: var(--logo-gray);
    transition: color 0.3s var(--ease);
}

.plat:hover .plat__logo {
    color: var(--brand);
}

.plat__name {
    font-size: 18px;
    font-weight: 650;
}

.plat__arch {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 7px;
}

.plat__arch span {
    padding: 3px 11px;
    border-radius: 999px;
    background: var(--brand-tint);
    color: var(--brand);
    font-size: 12px;
    font-weight: 650;
}

.plat__note {
    font-size: 13px;
    line-height: 1.6;
    color: var(--ink-muted);
}

/* ---------- marquee ---------- */

.marquee {
    position: relative;
    padding: 20px 0 clamp(56px, 7vw, 84px);
    overflow: hidden;
    mask-image: linear-gradient(90deg, transparent, #000 10%, #000 90%, transparent);
    -webkit-mask-image: linear-gradient(90deg, transparent, #000 10%, #000 90%, transparent);
}

.marquee__track {
    display: flex;
    gap: 12px;
    width: max-content;
    animation: marquee 36s linear infinite;
}

.marquee:hover .marquee__track {
    animation-play-state: paused;
}

@keyframes marquee {
    to {
        transform: translateX(-50%);
    }
}

.marquee__chip {
    padding: 9px 20px;
    border-radius: 999px;
    border: 1px solid var(--hairline);
    background: var(--bg);
    font-size: 13.5px;
    font-weight: 600;
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    color: var(--ink-soft);
    white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
    .marquee__track {
        animation: none;
        width: 100%;
        justify-content: center;
        flex-wrap: wrap;
    }

    /* Second copy is only there for the loop; hide it when static. */
    .marquee__chip:nth-child(n + 11) {
        display: none;
    }

    .plat.is-revealed:hover {
        transform: none;
    }
}
</style>
