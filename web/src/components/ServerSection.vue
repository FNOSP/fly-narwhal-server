<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useScrollProgress } from '../composables/useParallax'

const capabilities = [
    {
        tag: '弹幕',
        title: '拉取与缓存',
        desc: '客户端请求弹幕时由服务端统一拉取并落库缓存，同一剧集重复播放不再重复请求源站，也避免了跨域与限流问题。',
        endpoint: 'GET /api/danmu/get',
    },
    {
        tag: '分析',
        title: '片头片尾检测',
        desc: '服务端用 ffmpeg 分析剧集章节与音画特征，计算片头片尾区间并写入数据库，客户端播放时据此自动跳过。',
        endpoint: 'POST /api/analysis/analyze',
    },
    {
        tag: '鉴权',
        title: '授权码',
        desc: '客户端与服务端之间用一次性展示的授权码建立信任，配合请求签名校验，避免服务端被未授权的客户端调用。',
        endpoint: 'POST /api/config/auth-code',
    },
]

const root = ref(null)
const { progress } = useScrollProgress(root, { start: 1, end: 0.15 })

const codeLine = ref(0)
let timer = 0

const diagramY = () => `${(1 - progress.value) * 26}px`

onMounted(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        codeLine.value = 99
        return
    }
    timer = window.setInterval(() => {
        codeLine.value = (codeLine.value + 1) % 5
    }, 2200)
})

onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
})
</script>

<template>
    <section id="server" ref="root" class="server">
        <div class="shell">
            <div class="section-head server__head">
                <p v-reveal class="eyebrow">服务端</p>
                <h2 v-reveal="80" class="h2">客户端之外的一半</h2>
                <p v-reveal="160" class="lede">
                    飞鲸影视服务端以 GraalVM 原生二进制形式发布，运行在飞牛 NAS 上，负责弹幕、片头片尾分析与授权。
                    无需安装 Java，解压后即可运行。
                </p>
            </div>

            <div class="server__layout">
                <div class="server__list">
                    <article
                        v-for="(c, i) in capabilities"
                        :key="c.title"
                        v-reveal="i * 100"
                        class="cap"
                    >
                        <span class="cap__tag">{{ c.tag }}</span>
                        <div class="cap__body">
                            <h3 class="cap__title">{{ c.title }}</h3>
                            <p class="cap__desc">{{ c.desc }}</p>
                            <code class="cap__endpoint">{{ c.endpoint }}</code>
                        </div>
                    </article>
                </div>

                <div class="server__diagram" :style="{ transform: `translateY(${diagramY()})` }">
                    <div class="diagram">
                        <div class="diagram__node">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="2" y="4" width="20" height="13" rx="2.4" /><path d="M8 21h8M12 17v4" />
                            </svg>
                            <span>客户端</span>
                            <small>Windows · macOS · Linux</small>
                        </div>

                        <div class="diagram__flow">
                            <span v-for="n in 3" :key="n" class="diagram__packet" :style="{ '--i': n }"></span>
                        </div>

                        <div class="diagram__node diagram__node--server">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="3" y="3" width="18" height="7" rx="2" /><rect x="3" y="14" width="18" height="7" rx="2" />
                                <path d="M7 6.5h.01M7 17.5h.01" />
                            </svg>
                            <span>飞鲸服务端</span>
                            <small>飞牛 NAS · 原生二进制</small>
                        </div>

                        <ul class="diagram__log" aria-hidden="true">
                            <li v-for="(step, i) in ['拉取弹幕', 'ffmpeg 分析', '缓存落库', '签名校验', '返回结果']" :key="step" :class="{ 'is-on': codeLine === i }">
                                <span class="diagram__log-dot"></span>{{ step }}
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.server {
    padding: clamp(72px, 11vw, 140px) 0;
    background: #FFFFFF;
}

.server__head {
    text-align: center;
}

.server__head .lede {
    margin: 16px auto 0;
}

.server__layout {
    display: grid;
    grid-template-columns: 1.05fr 0.95fr;
    gap: clamp(32px, 4vw, 56px);
    align-items: start;
}

.server__list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.cap {
    display: flex;
    gap: 18px;
    padding: 24px 26px;
    border-radius: var(--radius-md);
    border: 1px solid var(--hairline);
    background: var(--bg);
    transition: box-shadow 0.35s var(--ease), border-color 0.35s var(--ease);
}

.cap:hover {
    box-shadow: var(--shadow-md);
    border-color: rgba(0, 122, 255, 0.24);
}

.cap__tag {
    flex-shrink: 0;
    align-self: flex-start;
    padding: 5px 11px;
    border-radius: 8px;
    background: var(--brand-tint);
    color: var(--brand);
    font-size: 12.5px;
    font-weight: 650;
}

.cap__title {
    font-size: 17px;
    font-weight: 650;
    margin-bottom: 8px;
}

.cap__desc {
    font-size: 14.5px;
    line-height: 1.68;
    color: var(--ink-muted);
    margin-bottom: 12px;
}

.cap__endpoint {
    display: inline-block;
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    font-size: 12.5px;
    color: var(--ink-soft);
    background: rgba(0, 0, 0, 0.045);
    padding: 4px 9px;
    border-radius: 7px;
}

.server__diagram {
    position: sticky;
    top: calc(var(--nav-h) + 32px);
    transition: transform 0.15s linear;
    will-change: transform;
}

.diagram {
    padding: 30px 26px;
    border-radius: var(--radius-lg);
    background: linear-gradient(165deg, #FFFFFF, #F0F3F8);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-md);
}

.diagram__node {
    display: grid;
    justify-items: center;
    gap: 4px;
    padding: 20px;
    border-radius: var(--radius-md);
    background: #fff;
    border: 1px solid var(--hairline);
}

.diagram__node--server {
    background: linear-gradient(180deg, rgba(0, 122, 255, 0.08), rgba(0, 122, 255, 0.03));
    border-color: rgba(0, 122, 255, 0.22);
}

.diagram__node svg {
    width: 26px;
    height: 26px;
    color: var(--brand);
    margin-bottom: 6px;
}

.diagram__node span {
    font-size: 15px;
    font-weight: 650;
}

.diagram__node small {
    font-size: 12px;
    color: var(--ink-muted);
}

.diagram__flow {
    display: flex;
    justify-content: center;
    gap: 7px;
    padding: 14px 0;
}

.diagram__packet {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--brand);
    opacity: 0.28;
    animation: packet 1.8s var(--ease) infinite;
    animation-delay: calc(var(--i) * 0.28s);
}

@keyframes packet {
    0%,
    100% {
        opacity: 0.2;
        transform: scale(0.8);
    }
    45% {
        opacity: 1;
        transform: scale(1.25);
    }
}

.diagram__log {
    list-style: none;
    margin-top: 20px;
    display: grid;
    gap: 8px;
}

.diagram__log li {
    display: flex;
    align-items: center;
    gap: 9px;
    font-size: 13px;
    color: var(--ink-muted);
    transition: color 0.4s var(--ease);
}

.diagram__log li.is-on {
    color: var(--ink);
    font-weight: 600;
}

.diagram__log-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    opacity: 0.45;
    transition: opacity 0.4s var(--ease), transform 0.4s var(--ease);
}

.diagram__log li.is-on .diagram__log-dot {
    opacity: 1;
    transform: scale(1.4);
}

@media (prefers-reduced-motion: reduce) {
    .diagram__packet {
        animation: none;
        opacity: 0.6;
    }
}

@media (max-width: 900px) {
    .server__layout {
        grid-template-columns: 1fr;
    }

    .server__diagram {
        position: static;
    }
}

@media (max-width: 560px) {
    .cap {
        flex-direction: column;
        gap: 12px;
        padding: 20px;
    }
}
</style>
