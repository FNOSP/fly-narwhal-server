<script setup>
// Bento grid of the "功能更完整" items from the client README. Two cards carry
// small built-in visuals; the rest stay typographic so the grid breathes.
const items = [
    {
        key: 'folder',
        span: 'span3',
        title: '文件夹视图',
        desc: '直接按目录浏览 NAS 上的媒体文件，不经刮削也能找到想看的片源。',
        visual: 'tree',
    },
    {
        key: 'livetv',
        span: 'span3 dark',
        title: 'Live TV',
        desc: '直播频道即点即播，换台与音量都在同一个播放界面内完成。',
        visual: 'live',
    },
    {
        key: 'pan',
        span: 'span2',
        title: '网盘视频播放',
        desc: '网盘里的视频无需转存，在线直接起播。',
    },
    {
        key: 'mediainfo',
        span: 'span2',
        title: '媒体信息面板',
        desc: '视频流、音频流与字幕轨的编码、码率、语言一目了然。',
        visual: 'spec',
    },
    {
        key: 'fullscreen',
        span: 'span2',
        title: 'Windows 伪全屏',
        desc: '全屏播放时，其他应用窗口仍可正常叠放显示。',
    },
    {
        key: 'detail',
        span: 'span2',
        title: '播放详细信息',
        desc: '解码方式、丢帧与缓冲状态实时可见。',
    },
    {
        key: 'advanced',
        span: 'span2',
        title: '进阶播放选项',
        desc: '强制 H.264、SDR 色调映射，为老设备与特殊片源兜底。',
    },
    {
        key: 'motion',
        span: 'span2',
        title: '流畅过渡动画',
        desc: '动画链路重构精简，页面切换与播放交互更顺滑。',
    },
]

const tree = [
    { name: '电影', depth: 0, dir: true },
    { name: '星际穿越.2014.4K.HDR.mkv', depth: 1, dir: false },
    { name: '剧集', depth: 0, dir: true },
    { name: '三体 (2023)', depth: 1, dir: true },
    { name: 'S01E01 · 科学边界.mkv', depth: 2, dir: false },
]

const specs = [
    { k: 'VIDEO', v: 'HEVC · 4K · 62 Mbps' },
    { k: 'AUDIO', v: 'TrueHD 7.1 · zh' },
    { k: 'SUBS', v: 'PGS · ASS · SRT' },
]
</script>

<template>
    <section id="more" class="more">
        <div class="shell">
            <div class="section-head more__head">
                <p v-reveal class="eyebrow">更多能力</p>
                <h2 v-reveal="80" class="h2">
                    细节之处，<br />处处都是为观影设计的
                </h2>
            </div>

            <div class="bento">
                <article
                    v-for="(it, i) in items"
                    :key="it.key"
                    v-reveal="(i % 3) * 90"
                    class="bento__card"
                    :class="[`bento__card--${it.span.split(' ')[0]}`, { 'bento__card--dark': it.span.includes('dark') }]"
                >
                    <div v-if="it.visual === 'tree'" class="bento__visual" aria-hidden="true">
                        <p v-for="n in tree" :key="n.name" class="tree__row" :style="{ '--d': n.depth }">
                            <svg v-if="n.dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" />
                            </svg>
                            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="3" y="5" width="18" height="14" rx="2.4" /><path d="M10 9.5l5 2.5-5 2.5z" />
                            </svg>
                            <span>{{ n.name }}</span>
                        </p>
                    </div>

                    <div v-else-if="it.visual === 'live'" class="bento__visual bento__visual--live" aria-hidden="true">
                        <span class="live__dot"></span>
                        <span class="live__label">LIVE</span>
                        <span class="live__channel">CCTV-8 电视剧 · 1080i</span>
                        <span class="live__bars">
                            <i v-for="n in 5" :key="n" :style="{ '--i': n }"></i>
                        </span>
                    </div>

                    <div v-else-if="it.visual === 'spec'" class="bento__visual bento__visual--spec" aria-hidden="true">
                        <p v-for="s in specs" :key="s.k" class="spec__row">
                            <span class="spec__k">{{ s.k }}</span>
                            <span class="spec__v">{{ s.v }}</span>
                        </p>
                    </div>

                    <h3 class="bento__title">{{ it.title }}</h3>
                    <p class="bento__desc">{{ it.desc }}</p>
                </article>
            </div>
        </div>
    </section>
</template>

<style scoped>
.more {
    padding: clamp(72px, 11vw, 140px) 0;
    background: var(--bg);
}

.more__head {
    text-align: center;
}

.bento {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 18px;
}

.bento__card {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 26px 26px 28px;
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    overflow: hidden;
    transition: transform 0.4s var(--ease), box-shadow 0.4s var(--ease);
}

.bento__card.is-revealed:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-md);
}

.bento__card--span3 {
    grid-column: span 3;
}

.bento__card--span2 {
    grid-column: span 2;
}

.bento__card--dark {
    background: linear-gradient(160deg, #14141C 0%, #0B0B12 100%);
    border-color: rgba(255, 255, 255, 0.1);
    color: #F5F5F7;
}

.bento__card--dark .bento__desc {
    color: #A1A1A6;
}

.bento__title {
    font-size: 17.5px;
    font-weight: 650;
    letter-spacing: -0.01em;
}

.bento__desc {
    font-size: 14px;
    line-height: 1.68;
    color: var(--ink-muted);
}

/* ---------- visuals ---------- */

.bento__visual {
    margin-bottom: 8px;
}

.tree__row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 5px 0;
    margin-left: calc(var(--d) * 18px);
    font-size: 12.5px;
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    color: var(--ink-soft);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.tree__row svg {
    width: 15px;
    height: 15px;
    flex-shrink: 0;
    color: var(--brand);
    opacity: 0.8;
}

.bento__visual--live {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 16px;
    border-radius: var(--radius-md);
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.09);
}

.live__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #FF453A;
    animation: live-pulse 1.6s var(--ease) infinite;
}

@keyframes live-pulse {
    0%,
    100% {
        box-shadow: 0 0 0 0 rgba(255, 69, 58, 0.5);
    }
    55% {
        box-shadow: 0 0 0 7px rgba(255, 69, 58, 0);
    }
}

.live__label {
    font-size: 11.5px;
    font-weight: 800;
    letter-spacing: 0.12em;
    color: #FF6961;
}

.live__channel {
    font-size: 12.5px;
    color: #A1A1A6;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.live__bars {
    margin-left: auto;
    display: flex;
    align-items: flex-end;
    gap: 3px;
    height: 16px;
}

.live__bars i {
    width: 3px;
    border-radius: 2px;
    background: #6CB2FF;
    animation: eq 1.1s var(--ease) infinite alternate;
    animation-delay: calc(var(--i) * 0.12s);
    height: 40%;
}

@keyframes eq {
    from {
        height: 25%;
    }
    to {
        height: 100%;
    }
}

.bento__visual--spec {
    display: grid;
    gap: 7px;
    padding: 14px 16px;
    border-radius: var(--radius-md);
    background: rgba(0, 0, 0, 0.035);
    border: 1px solid var(--hairline);
}

.spec__row {
    display: flex;
    gap: 12px;
    font-size: 11.5px;
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
}

.spec__k {
    color: var(--brand);
    font-weight: 700;
    min-width: 46px;
}

.spec__v {
    color: var(--ink-soft);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

@media (max-width: 980px) {
    .bento {
        grid-template-columns: repeat(4, 1fr);
    }

    .bento__card--span3 {
        grid-column: span 4;
    }

    .bento__card--span2 {
        grid-column: span 2;
    }
}

@media (max-width: 620px) {
    .bento {
        grid-template-columns: 1fr;
    }

    .bento__card--span3,
    .bento__card--span2 {
        grid-column: span 1;
    }
}

@media (prefers-reduced-motion: reduce) {
    .live__dot,
    .live__bars i {
        animation: none;
    }

    .bento__card.is-revealed:hover {
        transform: none;
    }
}
</style>
