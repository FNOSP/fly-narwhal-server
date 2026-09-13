<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useScrollProgress } from '../composables/useParallax'

// Sourced from the client repo's README on master (img/*.png), resized to 1800px
// wide and re-encoded as JPEG — the originals are 3600×2250 and ~7MB each.
const shots = [
    {
        src: '/img/screenshot-login.jpg',
        alt: '飞鲸影视登录页，Liquid Glass 毛玻璃质感',
        title: '登录',
        desc: '支持飞牛 ID 与 NAS 登录，连接地址、端口与 HTTPS 安全访问都可直接配置。',
    },
    {
        src: '/img/screenshot-home.jpg',
        alt: '飞鲸影视媒体库首页',
        title: '媒体库',
        desc: '首页聚合继续观看与媒体库分类，电影、剧集、动漫按分类与标签浏览。',
    },
    {
        src: '/img/screenshot-player.jpg',
        alt: '飞鲸影视播放器，含中英双语字幕与播放控制条',
        title: '播放器',
        desc: '内置 mpv 播放内核，支持 GPU 硬解、PGS/SUP 字幕与 HDR 动态范围。',
    },
    {
        src: '/img/screenshot-player-8k.jpg',
        alt: '飞鲸影视播放 8K 高码率片源',
        title: '8K 与高码率',
        desc: '原生解码高码率片源，配合显卡硬解在桌面端直接播放，无需服务端转码。',
    },
]

const stage = ref(null)
const { progress } = useScrollProgress(stage, { start: 1, end: 0 })

// Window push-in: starts slightly small and settles to full size as the section
// centres in the viewport.
const stageScale = computed(() => 0.9 + progress.value * 0.1)
const stageLift = computed(() => `${(1 - progress.value) * 40}px`)
const backdrop = computed(() => progress.value)
const active = ref(0)

let frame = 0
function onScroll() {
    if (frame) return
    frame = requestAnimationFrame(() => {
        frame = 0
        const el = stage.value
        if (!el) return
        const rect = el.getBoundingClientRect()
        const center = rect.top + rect.height / 2
        const vh = window.innerHeight || 1
        const fromCenter = Math.abs(center - vh / 2)
        // Pick the shot whose center is nearest the viewport center.
        const step = rect.height / shots.length
        const idx = Math.min(shots.length - 1, Math.max(0, Math.floor((vh / 2 - rect.top) / step)))
        active.value = fromCenter < rect.height ? idx : active.value
    })
}

onMounted(() => {
    window.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
})

onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll)
    if (frame) cancelAnimationFrame(frame)
})
</script>

<template>
    <section id="screenshots" ref="stage" class="shots">
        <div class="shots__aurora" :style="{ opacity: backdrop }"></div>

        <div class="shell shots__head">
            <p v-reveal class="eyebrow">界面预览</p>
            <h2 v-reveal="80" class="h2">看起来，就是原生应用</h2>
            <p v-reveal="160" class="lede">
                2.0 焕新出发：用 Flutter 重新构建桌面端，不再依赖 JVM 运行时，窗口交互与渲染都更贴近系统本身。
            </p>
        </div>

        <div class="shell">
            <div
                v-reveal
                class="shots__stage"
                :style="{ transform: `scale(${stageScale}) translateY(${stageLift})` }"
            >
                <figure v-for="(shot, i) in shots" :key="shot.src" class="shot" :class="{ 'shot--on': active === i }">
                    <div class="shot__chrome">
                        <span class="shot__dot"></span>
                        <span class="shot__dot"></span>
                        <span class="shot__dot"></span>
                    </div>
                    <img
                        :src="shot.src"
                        :alt="shot.alt"
                        loading="lazy"
                        decoding="async"
                    />
                </figure>

                <div class="shots__caption">
                    <h3 class="shots__caption-title">{{ shots[active].title }}</h3>
                    <p class="shots__caption-desc">{{ shots[active].desc }}</p>
                </div>

                <div class="shots__dots" role="tablist" aria-label="预览图切换">
                    <button
                        v-for="(shot, i) in shots"
                        :key="shot.title"
                        class="shots__dot"
                        :class="{ 'shots__dot--on': active === i }"
                        type="button"
                        role="tab"
                        :aria-selected="active === i"
                        :aria-label="shot.title"
                        @click="active = i"
                    ></button>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.shots {
    position: relative;
    padding: clamp(72px, 11vw, 140px) 0 clamp(60px, 8vw, 100px);
    overflow: hidden;
}

.shots__aurora {
    position: absolute;
    inset: 0;
    background: radial-gradient(900px 420px at 50% 0%, rgba(0, 122, 255, 0.1), transparent 70%);
    pointer-events: none;
}

.shots__head {
    position: relative;
    text-align: center;
    margin-bottom: clamp(36px, 5vw, 60px);
}

.shots__head .lede {
    margin: 16px auto 0;
}

.shots__stage {
    position: relative;
    display: grid;
    transition: transform 0.18s linear;
    will-change: transform;
    padding-bottom: 96px;
}

.shot {
    position: relative;
    grid-area: 1 / 1;
    border-radius: var(--radius-md);
    overflow: hidden;
    background: #fff;
    box-shadow: var(--shadow-lg);
    border: 1px solid rgba(255, 255, 255, 0.7);
    opacity: 0;
    transform: translateY(18px);
    transition: opacity 0.6s var(--ease), transform 0.6s var(--ease);
    pointer-events: none;
}

.shot--on {
    position: relative;
    opacity: 1;
    transform: none;
    pointer-events: auto;
}

.shot__chrome {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 11px 14px;
    background: #EDEDF0;
    border-bottom: 1px solid var(--hairline);
}

.shot__dot {
    width: 11px;
    height: 11px;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.14);
}

.shot img {
    width: 100%;
    height: auto;
    display: block;
}

.shots__caption {
    position: absolute;
    inset: auto 0 4px;
    text-align: center;
    padding: 0 20px;
}

.shots__caption-title {
    font-size: 20px;
    font-weight: 600;
    margin-bottom: 6px;
}

.shots__caption-desc {
    font-size: 15px;
    color: var(--ink-muted);
    max-width: 560px;
    margin: 0 auto;
    line-height: 1.6;
}

.shots__dots {
    position: absolute;
    inset: auto 0 58px;
    display: flex;
    justify-content: center;
    gap: 8px;
}

.shots__dot {
    width: 8px;
    height: 8px;
    padding: 0;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.16);
    transition: background-color 0.3s var(--ease), transform 0.3s var(--ease);
}

.shots__dot--on {
    background: var(--brand);
    transform: scale(1.25);
}

@media (max-width: 640px) {
    .shots__stage {
        padding-bottom: 132px;
    }

    .shot__chrome {
        padding: 8px 10px;
    }

    .shot__dot {
        width: 9px;
        height: 9px;
    }

    .shots__caption-title {
        font-size: 17px;
    }

    .shots__caption-desc {
        font-size: 13.5px;
    }
}
</style>
