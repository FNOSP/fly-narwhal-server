<script setup>
import { computed, ref } from 'vue'
import { useScrollProgress } from '../composables/useParallax'

defineProps({
    versionLabel: { type: String, default: '' },
})

const emit = defineEmits(['download', 'auth'])

const root = ref(null)
const { progress } = useScrollProgress(root, { start: 1, end: -0.4 })

// The two glow orbs drift apart as the section scrolls in, so the closing
// frame never sits completely still.
const orbAY = computed(() => `${(1 - progress.value) * -60}px`)
const orbBY = computed(() => `${(1 - progress.value) * 70}px`)
</script>

<template>
    <section id="cta" ref="root" class="cta">
        <div class="cta__orb cta__orb--a" :style="{ transform: `translate3d(0, ${orbAY}, 0)` }" aria-hidden="true"></div>
        <div class="cta__orb cta__orb--b" :style="{ transform: `translate3d(0, ${orbBY}, 0)` }" aria-hidden="true"></div>

        <div class="shell cta__inner">
            <p v-reveal class="cta__kicker">飞鲸影视 2.0 · 焕新出发<span v-if="versionLabel"> · {{ versionLabel }}</span></p>

            <h2 v-reveal:mask class="cta__title">
                <span class="line-mask"><span style="--line-delay: 0ms">把飞牛影视，</span></span>
                <span class="line-mask"><span style="--line-delay: 110ms">装进你的每一块屏幕。</span></span>
            </h2>

            <p v-reveal="200" class="cta__lede">
                开源、免费、持续进化。现在下载，今晚的剧就用它看。
            </p>

            <div v-reveal="280" class="cta__actions">
                <button class="btn btn-primary btn-lg" type="button" @click="emit('download')">
                    下载客户端
                </button>
                <button class="btn btn-lg cta__ghost" type="button" @click="emit('auth')">获取授权码</button>
            </div>

            <nav v-reveal="360" class="cta__links" aria-label="项目链接">
                <a href="https://github.com/FNOSP/FlyNarwhal" target="_blank" rel="noopener noreferrer">客户端仓库</a>
                <span class="cta__sep" aria-hidden="true">·</span>
                <a href="https://github.com/FNOSP/fly-narwhal-server" target="_blank" rel="noopener noreferrer">服务端仓库</a>
                <span class="cta__sep" aria-hidden="true">·</span>
                <a href="https://github.com/FNOSP/FlyNarwhal/releases" target="_blank" rel="noopener noreferrer">全部版本</a>
            </nav>
        </div>
    </section>
</template>

<style scoped>
.cta {
    --ink: #F5F5F7;
    position: relative;
    padding: clamp(96px, 14vw, 180px) 0;
    background: linear-gradient(180deg, #07070D 0%, #0C1024 55%, #0A0E1E 100%);
    color: var(--ink);
    overflow: hidden;
    text-align: center;
}

.cta__orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(70px);
    pointer-events: none;
    will-change: transform;
}

.cta__orb--a {
    top: -14%;
    left: 8%;
    width: min(560px, 60vw);
    aspect-ratio: 1;
    background: radial-gradient(circle, rgba(0, 122, 255, 0.4), transparent 68%);
}

.cta__orb--b {
    bottom: -22%;
    right: 6%;
    width: min(620px, 66vw);
    aspect-ratio: 1;
    background: radial-gradient(circle, rgba(94, 92, 230, 0.34), transparent 68%);
}

.cta__inner {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
}

.cta__kicker {
    font-size: 14px;
    font-weight: 650;
    letter-spacing: 0.06em;
    color: #8FC5FF;
    margin-bottom: 22px;
}

.cta__title {
    font-size: clamp(34px, 6vw, 68px);
    font-weight: 700;
    letter-spacing: -0.03em;
    line-height: 1.14;
    background: linear-gradient(94deg, #FFFFFF 20%, #9CC9FF 60%, #B9A7FF 95%);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
    margin-bottom: 22px;
}

.cta__lede {
    font-size: clamp(15.5px, 1.7vw, 19px);
    color: #A1A1A6;
    max-width: 46ch;
    line-height: 1.7;
    margin-bottom: 38px;
}

.cta__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    justify-content: center;
    margin-bottom: 30px;
}

.cta__ghost {
    background: rgba(255, 255, 255, 0.1);
    color: #F5F5F7;
    border: 1px solid rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
}

.cta__ghost:hover {
    background: rgba(255, 255, 255, 0.18);
    transform: translateY(-2px);
}

.cta__links {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 13.5px;
}

.cta__links a {
    color: #8FB8E8;
    transition: color 0.25s var(--ease);
}

.cta__links a:hover {
    color: #FFFFFF;
}

.cta__sep {
    color: rgba(255, 255, 255, 0.28);
}

@media (prefers-reduced-motion: reduce) {
    .cta__orb {
        transform: none !important;
    }
}

@media (max-width: 520px) {
    .cta__actions {
        width: 100%;
        flex-direction: column;
    }

    .cta__actions .btn {
        width: 100%;
    }
}
</style>
