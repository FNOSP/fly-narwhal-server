<script setup>
import { computed, ref } from 'vue'
import { useScrollProgress } from '../composables/useParallax'

const root = ref(null)
// 0 while the section is below the fold, 1 once it has scrolled fully past.
const { progress } = useScrollProgress(root, { start: 1, end: -1 })

// Bell curve: the glow peaks when the stage is centred in the viewport.
const glow = computed(() => {
    const p = Math.min(1, Math.max(0, progress.value))
    return Math.sin(p * Math.PI)
})
const stageScale = computed(() => 0.93 + glow.value * 0.07)
const stageY = computed(() => `${(1 - progress.value) * 36}px`)
const glowOpacity = computed(() => 0.25 + glow.value * 0.75)

const chips = [
    'GPU 硬解',
    'HDR',
    'HLG',
    'Dolby Vision',
    'PGS / SUP 字幕',
    '8K 高码率',
    '直链播放',
    'HLS 回退',
    '音轨切换',
]

const compare = [
    {
        gen: '1.x',
        title: 'RGBA 回读渲染',
        desc: '受限于 Compose Desktop 渲染机制，硬解画面需回读 CPU 再渲染。CPU 占用高，HDR 片源被迫由服务端转成 SDR。',
        bad: true,
    },
    {
        gen: '2.0',
        title: 'GPU 硬解直出',
        desc: 'libmpv 硬解输出直达屏幕，CPU 占用显著降低，原片动态范围原样保留，HDR 不再失真。',
        bad: false,
    },
]
</script>

<template>
    <section id="player" ref="root" class="player">
        <div class="player__glow" :style="{ opacity: glowOpacity }" aria-hidden="true"></div>

        <div class="shell">
            <div class="player__head">
                <p v-reveal class="eyebrow player__eyebrow">播放内核</p>
                <h2 v-reveal:mask class="h2 player__title">
                    <span class="line-mask"><span style="--line-delay: 0ms">影院级播放内核，</span></span>
                    <span class="line-mask"><span style="--line-delay: 110ms">装进你的桌面。</span></span>
                </h2>
                <p v-reveal="160" class="lede player__lede">
                    基于 media_kit 与全量 libmpv 构建。macOS 与 Linux 打包完整解码库，
                    高码率片源本地直接处理，不再依赖服务端转码。
                </p>
            </div>

            <div
                class="player__stage"
                :style="{ transform: `scale(${stageScale}) translate3d(0, ${stageY}, 0)` }"
            >
                <div class="player__frame">
                    <div class="player__chrome">
                        <span class="player__dot"></span>
                        <span class="player__dot"></span>
                        <span class="player__dot"></span>
                        <span class="player__tag">8K · HDR · 硬解直出</span>
                    </div>
                    <img
                        src="/img/screenshot-player-8k.jpg"
                        alt="飞鲸影视播放 8K HDR 高码率片源"
                        loading="lazy"
                        decoding="async"
                    />
                    <div class="player__scanline" aria-hidden="true"></div>
                </div>
            </div>

            <ul class="player__chips" aria-label="播放能力">
                <li v-for="(c, i) in chips" :key="c" v-reveal="i * 60" class="chip">{{ c }}</li>
            </ul>

            <div class="player__compare">
                <article
                    v-for="(c, i) in compare"
                    :key="c.gen"
                    v-reveal="i * 120"
                    class="gen"
                    :class="{ 'gen--good': !c.bad }"
                >
                    <span class="gen__badge">{{ c.gen }}</span>
                    <h3 class="gen__title">{{ c.title }}</h3>
                    <p class="gen__desc">{{ c.desc }}</p>
                </article>
            </div>
        </div>
    </section>
</template>

<style scoped>
.player {
    --ink: #F5F5F7;
    --ink-muted: #A1A1A6;
    position: relative;
    padding: clamp(88px, 12vw, 160px) 0;
    background: linear-gradient(180deg, #0B0B12 0%, #050508 45%, #0A0A14 100%);
    color: var(--ink);
    overflow: hidden;
}

.player__glow {
    position: absolute;
    top: 18%;
    left: 50%;
    width: min(1200px, 140vw);
    aspect-ratio: 1.8 / 1;
    translate: -50% 0;
    background: radial-gradient(
        circle at 50% 45%,
        rgba(0, 122, 255, 0.34) 0%,
        rgba(94, 92, 230, 0.16) 40%,
        transparent 70%
    );
    filter: blur(30px);
    pointer-events: none;
    will-change: opacity;
}

.player__head {
    position: relative;
    text-align: center;
    margin-bottom: clamp(44px, 6vw, 72px);
}

.player__eyebrow {
    color: #6CB2FF;
}

.player__title {
    font-size: clamp(32px, 5vw, 58px);
}

.player__lede {
    margin: 18px auto 0;
    color: var(--ink-muted);
}

.player__stage {
    position: relative;
    will-change: transform;
    transition: transform 0.2s linear;
}

.player__frame {
    position: relative;
    border-radius: var(--radius-lg);
    overflow: hidden;
    border: 1px solid rgba(255, 255, 255, 0.12);
    box-shadow: 0 40px 120px rgba(0, 60, 160, 0.35), 0 8px 32px rgba(0, 0, 0, 0.6);
    background: #000;
}

.player__chrome {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 12px 16px;
    background: rgba(255, 255, 255, 0.06);
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.player__dot {
    width: 11px;
    height: 11px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.18);
}

.player__tag {
    margin-left: auto;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: #8FC5FF;
}

.player__frame img {
    width: 100%;
    height: auto;
}

/* A slow light sweep across the "screen" — reads as the picture coming alive. */
.player__scanline {
    position: absolute;
    inset: 0;
    background: linear-gradient(
        105deg,
        transparent 30%,
        rgba(255, 255, 255, 0.07) 46%,
        rgba(255, 255, 255, 0.12) 50%,
        rgba(255, 255, 255, 0.07) 54%,
        transparent 70%
    );
    background-size: 250% 100%;
    animation: sweep 7s var(--ease) infinite;
    pointer-events: none;
}

@keyframes sweep {
    0% {
        background-position: 180% 0;
    }
    55%,
    100% {
        background-position: -80% 0;
    }
}

.player__chips {
    list-style: none;
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;
    margin-top: clamp(40px, 5vw, 60px);
}

.chip {
    padding: 8px 18px;
    border-radius: 999px;
    border: 1px solid rgba(255, 255, 255, 0.14);
    background: rgba(255, 255, 255, 0.05);
    font-size: 13.5px;
    font-weight: 550;
    color: #D6D6DB;
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    transition: border-color 0.3s var(--ease), background-color 0.3s var(--ease),
        color 0.3s var(--ease);
}

.chip:hover {
    border-color: rgba(0, 122, 255, 0.55);
    background: rgba(0, 122, 255, 0.14);
    color: #fff;
}

.player__compare {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 20px;
    margin-top: clamp(52px, 7vw, 88px);
}

.gen {
    padding: 28px 28px 30px;
    border-radius: var(--radius-lg);
    border: 1px solid rgba(255, 255, 255, 0.1);
    background: rgba(255, 255, 255, 0.035);
}

.gen--good {
    border-color: rgba(0, 122, 255, 0.4);
    background: linear-gradient(165deg, rgba(0, 122, 255, 0.14), rgba(94, 92, 230, 0.06));
    box-shadow: 0 20px 60px rgba(0, 90, 220, 0.18);
}

.gen__badge {
    display: inline-block;
    padding: 4px 12px;
    border-radius: 999px;
    font-size: 12.5px;
    font-weight: 700;
    letter-spacing: 0.03em;
    background: rgba(255, 255, 255, 0.1);
    color: var(--ink-muted);
    margin-bottom: 16px;
}

.gen--good .gen__badge {
    background: var(--brand);
    color: #fff;
}

.gen__title {
    font-size: 19px;
    font-weight: 650;
    letter-spacing: -0.01em;
    margin-bottom: 10px;
}

.gen__desc {
    font-size: 14.5px;
    line-height: 1.72;
    color: var(--ink-muted);
}

@media (prefers-reduced-motion: reduce) {
    .player__scanline {
        animation: none;
    }

    .player__stage {
        transform: none !important;
    }
}

@media (max-width: 560px) {
    .gen {
        padding: 22px 20px 24px;
    }

    .player__tag {
        display: none;
    }
}
</style>
