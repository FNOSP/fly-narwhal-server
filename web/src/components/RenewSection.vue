<script setup>
import { computed, ref } from 'vue'
import { useScrollProgress } from '../composables/useParallax'

const root = ref(null)
const { progress } = useScrollProgress(root, { start: 1, end: -1 })

// The oversized "2.0" watermark drifts slower than the page — cheap depth.
const watermarkY = computed(() => `${(progress.value - 0.5) * -90}px`)
const watermarkOpacity = computed(() => 0.05 + Math.sin(Math.min(1, Math.max(0, progress.value)) * Math.PI) * 0.06)

const pillars = [
    {
        no: '01',
        title: 'Flutter 原生重写',
        desc: '告别 JVM 运行时，渲染与窗口调度更贴近系统原生，安装包更轻、运行更高效。',
    },
    {
        no: '02',
        title: 'mpv 播放内核',
        desc: 'GPU 硬解直出屏幕，HDR 动态范围原样保留，CPU 占用大幅下降。',
    },
    {
        no: '03',
        title: 'Liquid Glass 视觉',
        desc: '全新设计语言，Acrylic 毛玻璃质感配合现代布局，整体更通透。',
    },
    {
        no: '04',
        title: '全平台覆盖',
        desc: '新增 Linux 支持，三端七格式，开箱即用。',
    },
]
</script>

<template>
    <section id="renew" ref="root" class="renew">
        <div
            class="renew__watermark"
            :style="{ transform: `translate3d(0, ${watermarkY}, 0)`, opacity: watermarkOpacity }"
            aria-hidden="true"
        >
            2.0
        </div>

        <div class="shell renew__inner">
            <p v-reveal class="eyebrow renew__eyebrow">2.0 · 焕新出发</p>

            <h2 v-reveal:mask class="renew__title">
                <span class="line-mask"><span style="--line-delay: 0ms">不是小修小补，</span></span>
                <span class="line-mask"><span style="--line-delay: 120ms">是一次彻底的重做。</span></span>
            </h2>

            <p v-reveal="220" class="renew__lede">
                从播放内核到视觉语言，飞鲸影视 2.0 把桌面观影体验从头再做了一遍。
            </p>

            <div class="renew__pillars">
                <article v-for="(p, i) in pillars" :key="p.no" v-reveal="i * 110" class="pillar">
                    <span class="pillar__no">{{ p.no }}</span>
                    <h3 class="pillar__title">{{ p.title }}</h3>
                    <p class="pillar__desc">{{ p.desc }}</p>
                </article>
            </div>
        </div>
    </section>
</template>

<style scoped>
.renew {
    --ink: #F5F5F7;
    position: relative;
    padding: clamp(88px, 12vw, 150px) 0;
    background: linear-gradient(180deg, #0A0A10 0%, #0D1220 60%, #0A0A10 100%);
    color: var(--ink);
    overflow: hidden;
    text-align: center;
}

.renew__watermark {
    position: absolute;
    top: 50%;
    left: 50%;
    translate: -50% -58%;
    font-size: min(46vw, 560px);
    font-weight: 800;
    letter-spacing: -0.04em;
    line-height: 1;
    color: #FFFFFF;
    pointer-events: none;
    user-select: none;
    will-change: transform, opacity;
}

.renew__inner {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
}

.renew__eyebrow {
    color: #6CB2FF;
}

.renew__title {
    font-size: clamp(30px, 4.8vw, 56px);
    font-weight: 700;
    letter-spacing: -0.025em;
    line-height: 1.16;
}

.renew__lede {
    margin-top: 18px;
    font-size: clamp(15.5px, 1.6vw, 18.5px);
    line-height: 1.7;
    color: #A1A1A6;
    max-width: 52ch;
}

.renew__pillars {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-top: clamp(44px, 6vw, 72px);
    width: 100%;
    text-align: left;
}

.pillar {
    padding: 26px 24px 28px;
    border-radius: var(--radius-lg);
    border: 1px solid rgba(255, 255, 255, 0.09);
    background: rgba(255, 255, 255, 0.035);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
    transition: border-color 0.35s var(--ease), background-color 0.35s var(--ease),
        transform 0.35s var(--ease);
}

.pillar.is-revealed:hover {
    border-color: rgba(0, 122, 255, 0.45);
    background: rgba(0, 122, 255, 0.08);
    transform: translateY(-4px);
}

.pillar__no {
    display: block;
    font-size: 13px;
    font-weight: 800;
    font-variant-numeric: tabular-nums;
    letter-spacing: 0.08em;
    color: #5E9EFF;
    margin-bottom: 14px;
}

.pillar__title {
    font-size: 17px;
    font-weight: 650;
    letter-spacing: -0.01em;
    margin-bottom: 9px;
}

.pillar__desc {
    font-size: 13.5px;
    line-height: 1.7;
    color: #A1A1A6;
}

@media (max-width: 980px) {
    .renew__pillars {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (max-width: 560px) {
    .renew__pillars {
        grid-template-columns: 1fr;
    }
}

@media (prefers-reduced-motion: reduce) {
    .renew__watermark {
        transform: none !important;
    }

    .pillar.is-revealed:hover {
        transform: none;
    }
}
</style>
