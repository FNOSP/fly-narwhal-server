<script setup>
import { computed, ref } from 'vue'
import { useScrollProgress } from '../composables/useParallax'

defineProps({
    versionLabel: { type: String, default: '' },
    releaseUrl: { type: String, default: '' },
})

const emit = defineEmits(['download', 'auth'])

const root = ref(null)
// Progress 0 while the hero sits at the top of the page, reaching 1 as its
// bottom-edge scrolls roughly one viewport up. Drives the fade, the lift and
// the backdrop drift together.
const { progress } = useScrollProgress(root, { start: 0, end: -1 })

const backdropY = computed(() => `${progress.value * 90}px`)
const contentY = computed(() => `${progress.value * -40}px`)
const contentOpacity = computed(() => Math.max(0, 1 - progress.value * 1.15))
const glowScale = computed(() => 1 + progress.value * 0.35)
</script>

<template>
    <section id="top" ref="root" class="hero">
        <div class="hero__backdrop" :style="{ transform: `translate3d(0, ${backdropY}, 0)` }">
            <div class="hero__glow" :style="{ transform: `scale(${glowScale})` }"></div>
            <div class="hero__grid"></div>
        </div>

        <div
            class="hero__content shell"
            :style="{ transform: `translate3d(0, ${contentY}, 0)`, opacity: contentOpacity }"
        >
            <img class="hero__mark" src="/img/app-icon.png" alt="飞鲸影视应用图标" width="512" height="512" />

            <p class="hero__brand">飞鲸影视 2.0 · 焕新出发</p>

            <h1 class="hero__title">
                <span class="line-mask"><span style="--hero-delay: 0.12s">把飞牛影视</span></span>
                <span class="line-mask"><span style="--hero-delay: 0.24s" class="hero__title-grad">装进你的桌面</span></span>
            </h1>

            <p class="hero__lede">
                面向飞牛影视服务的第三方桌面客户端。2.0 焕新出发：Flutter 原生重写，
                内置 mpv 播放内核，覆盖 Windows、macOS 与 Linux，支持智能跳过片头片尾与弹幕。
            </p>

            <div class="hero__cta">
                <button class="btn btn-primary btn-lg" type="button" @click="emit('download')">
                    下载客户端
                </button>
                <button class="btn btn-ghost btn-lg" type="button" @click="emit('auth')">获取授权码</button>
            </div>

            <a
                class="hero__badge"
                :href="releaseUrl"
                target="_blank"
                rel="noopener noreferrer"
            >
                <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor" aria-hidden="true">
                    <path
                        d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1zm0 12.5a5.5 5.5 0 1 1 0-11 5.5 5.5 0 0 1 0 11zM7.25 4h1.5v4.5l3 1.8-.75 1.24L7.25 9.5V4z"
                    />
                </svg>
                <span>{{ versionLabel }}</span>
            </a>
        </div>

        <div class="hero__scroll-hint" :style="{ opacity: contentOpacity }" aria-hidden="true">
            <span>向下滚动</span>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
        </div>
    </section>
</template>

<style scoped>
.hero {
    position: relative;
    min-height: 100svh;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    padding: calc(var(--nav-h) + 40px) 0 60px;
}

.hero__backdrop {
    position: absolute;
    inset: -15% 0;
    z-index: 0;
    will-change: transform;
}

/* Light base that the scroll-linked gradient fades over, matching the light
   theme of the app screenshots shown further down the page. */
.hero__backdrop::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, #FFFFFF 0%, #F5F5F7 55%, #EDF1F7 100%);
}

.hero__glow {
    position: absolute;
    top: 6%;
    left: 50%;
    width: min(1100px, 130vw);
    aspect-ratio: 1.6 / 1;
    translate: -50% 0;
    background: radial-gradient(
        circle at 50% 40%,
        rgba(0, 122, 255, 0.24) 0%,
        rgba(0, 122, 255, 0.1) 38%,
        rgba(0, 122, 255, 0) 68%
    );
    filter: blur(12px);
    will-change: transform;
}

.hero__grid {
    position: absolute;
    inset: 0;
    background-image: linear-gradient(rgba(0, 0, 0, 0.028) 1px, transparent 1px),
        linear-gradient(90deg, rgba(0, 0, 0, 0.028) 1px, transparent 1px);
    background-size: 64px 64px;
    mask-image: radial-gradient(circle at 50% 34%, #000 0%, transparent 72%);
    -webkit-mask-image: radial-gradient(circle at 50% 34%, #000 0%, transparent 72%);
}

.hero__content {
    position: relative;
    z-index: 1;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    will-change: transform, opacity;
}

.hero__mark {
    width: clamp(76px, 9vw, 104px);
    height: auto;
    border-radius: 24%;
    box-shadow: 0 18px 44px rgba(0, 122, 255, 0.24);
    margin-bottom: 22px;
}

.hero__brand {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 7px 18px;
    border-radius: 999px;
    border: 1px solid rgba(0, 122, 255, 0.22);
    background: rgba(0, 122, 255, 0.07);
    font-size: 14px;
    font-weight: 650;
    letter-spacing: 0.04em;
    color: var(--brand-deep);
    margin-bottom: 20px;
}

.hero__title {
    font-size: clamp(38px, 7vw, 78px);
    font-weight: 700;
    letter-spacing: -0.03em;
    line-height: 1.08;
    margin-bottom: 22px;
}

.hero__title-grad {
    background: linear-gradient(94deg, #007AFF 8%, #5E5CE6 55%, #BF5AF2 98%);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
}

.hero__lede {
    font-size: clamp(16px, 1.7vw, 20px);
    color: var(--ink-muted);
    line-height: 1.68;
    max-width: 620px;
    margin-bottom: 34px;
}

.hero__cta {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    justify-content: center;
    margin-bottom: 26px;
}

.hero__badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 7px 16px;
    border-radius: 999px;
    background: var(--brand-tint);
    color: var(--brand);
    font-size: 13.5px;
    font-weight: 600;
    transition: background-color 0.25s var(--ease);
}

.hero__badge:hover {
    background: rgba(0, 122, 255, 0.18);
}

.hero__scroll-hint {
    position: absolute;
    bottom: 26px;
    left: 50%;
    translate: -50% 0;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--ink-muted);
    letter-spacing: 0.06em;
}

.hero__scroll-hint svg {
    animation: hint-bob 2s var(--ease) infinite;
}

/* ---------- on-load entrance ----------
   The hero is above the fold at page load, so it animates on mount rather
   than via the scroll-triggered reveal used further down the page. */
.hero__mark {
    animation: hero-pop 0.9s var(--ease) both;
}

.hero__brand {
    animation: hero-rise 0.7s var(--ease) 0.05s both;
}

.hero__title .line-mask > span {
    animation: hero-line 1s var(--ease) both;
    animation-delay: var(--hero-delay, 0s);
}

.hero__lede {
    animation: hero-rise 0.7s var(--ease) 0.45s both;
}

.hero__cta {
    animation: hero-rise 0.7s var(--ease) 0.55s both;
}

.hero__badge {
    animation: hero-rise 0.7s var(--ease) 0.65s both;
}

/* The hint's own opacity stays scroll-driven; only its children animate in. */
.hero__scroll-hint span {
    animation: hero-rise 0.7s var(--ease) 0.95s both;
}

.hero__scroll-hint svg {
    animation: hero-rise 0.7s var(--ease) 0.95s both, hint-bob 2s var(--ease) 1.8s infinite;
}

@keyframes hero-pop {
    from {
        opacity: 0;
        transform: scale(0.82) translateY(16px);
    }
    to {
        opacity: 1;
        transform: none;
    }
}

@keyframes hero-rise {
    from {
        opacity: 0;
        transform: translateY(22px);
    }
    to {
        opacity: 1;
        transform: none;
    }
}

@keyframes hero-line {
    from {
        transform: translateY(115%);
    }
    to {
        transform: none;
    }
}

@keyframes hint-bob {
    0%,
    100% {
        transform: translateY(0);
    }
    50% {
        transform: translateY(5px);
    }
}

@media (prefers-reduced-motion: reduce) {
    /* Drop every entrance/loop animation; main.css already resets the
       line-mask transforms so nothing stays hidden. */
    .hero__mark,
    .hero__brand,
    .hero__title .line-mask > span,
    .hero__lede,
    .hero__cta,
    .hero__badge,
    .hero__scroll-hint span,
    .hero__scroll-hint svg {
        animation: none;
    }

    /* No scroll-linked fade: the content must stay fully legible. */
    .hero__content {
        opacity: 1 !important;
        transform: none !important;
    }

    .hero__backdrop,
    .hero__glow {
        transform: none !important;
    }
}

@media (max-width: 520px) {
    .hero__cta {
        width: 100%;
        flex-direction: column;
    }

    .hero__cta .btn {
        width: 100%;
    }
}
</style>
