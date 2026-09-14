<script setup>
import { ref } from 'vue'
import ChangelogText from './ChangelogText.vue'
import { useChangelog, CHANGELOG_URL } from '../composables/useChangelog'

const { loading, error, latest, history } = useChangelog()

const CAT_META = {
    Added: { label: '新增', color: '#248A3D', bg: 'rgba(52, 199, 89, 0.12)' },
    Changed: { label: '改进', color: '#B25000', bg: 'rgba(255, 149, 0, 0.14)' },
    Fixed: { label: '修复', color: '#0062CC', bg: 'rgba(0, 122, 255, 0.1)' },
}

function catMeta(name) {
    return CAT_META[name] || { label: name, color: 'var(--ink-soft)', bg: 'rgba(0, 0, 0, 0.05)' }
}

// Cards stay collapsed (version + date + counts) until hovered; on touch
// devices, where hover is unreliable, a tap pins a card open instead.
const open = ref(null)

function toggle(version) {
    open.value = open.value === version ? null : version
}
</script>

<template>
    <div class="tlpage">
        <header class="tlpage__bar shell">
            <a class="tlpage__back" href="#top">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M19 12H5M11 18l-6-6 6-6" />
                </svg>
                返回首页
            </a>
            <span class="tlpage__crumb">飞鲸影视 · 更新日志</span>
        </header>

        <main class="shell tlpage__main">
            <div class="tlpage__head">
                <p class="eyebrow">全部版本</p>
                <h1 class="tlpage__title">时间轴上的每一次发版</h1>
                <p class="tlpage__lede">
                    共 {{ history.length || '…' }} 个版本，数据来自客户端仓库
                    <a :href="CHANGELOG_URL" target="_blank" rel="noopener noreferrer">CHANGELOG.md</a>，打开页面时实时获取。
                </p>
            </div>

            <!-- loading skeleton -->
            <div v-if="loading" class="tlpage__skeleton" aria-label="更新日志加载中">
                <div v-for="n in 3" :key="n" class="skel">
                    <div class="skel__bar skel__bar--w30"></div>
                    <div class="skel__bar"></div>
                    <div class="skel__bar skel__bar--w70"></div>
                </div>
            </div>

            <!-- fetch failed -->
            <div v-else-if="error" class="tlpage__error">
                <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5h.01" />
                </svg>
                <p>更新日志暂时无法加载，可能是网络原因。</p>
                <div class="tlpage__error-actions">
                    <a :href="CHANGELOG_URL" target="_blank" rel="noopener noreferrer">前往 GitHub 查看 →</a>
                    <a href="#top">返回首页</a>
                </div>
            </div>

            <!-- vertical timeline -->
            <div v-else class="tl">
                <div
                    v-for="(v, i) in history"
                    :key="v.version"
                    v-reveal="Math.min(i, 5) * 60"
                    class="tl__item"
                    :class="{ 'tl__item--open': open === v.version }"
                >
                    <div class="tl__rail" aria-hidden="true">
                        <span class="tl__dot" :class="{ 'tl__dot--latest': latest && v.version === latest.version }"></span>
                    </div>

                    <article class="tl__card" @click="toggle(v.version)">
                        <header class="tl__head">
                            <span class="tl__v">v{{ v.version }}</span>
                            <time v-if="v.date" class="tl__date">{{ v.date }}</time>
                            <span v-if="latest && v.version === latest.version" class="tl__badge">最新版</span>
                            <span class="tl__counts">
                                <span
                                    v-for="cat in v.categories.filter((c) => c.items.length)"
                                    :key="cat.name"
                                    class="tl__count"
                                >
                                    <i :style="{ background: catMeta(cat.name).color }"></i>
                                    {{ catMeta(cat.name).label }} {{ cat.items.length }}
                                </span>
                            </span>
                            <svg class="tl__chevron" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                <path d="M6 9l6 6 6-6" />
                            </svg>
                        </header>

                        <div class="tl__panel">
                            <div class="tl__panel-inner">
                                <div v-for="cat in v.categories" :key="cat.name" class="clg-cat">
                                    <span class="clg-tag" :style="{ color: catMeta(cat.name).color, background: catMeta(cat.name).bg }">
                                        {{ catMeta(cat.name).label }}
                                    </span>
                                    <ul class="clg-list">
                                        <li v-for="(item, ii) in cat.items" :key="ii" class="clg-item">
                                            <strong v-if="item.title.length" class="clg-item__title">
                                                <ChangelogText :nodes="item.title" />
                                            </strong>
                                            <span class="clg-item__desc"><ChangelogText :nodes="item.desc" /></span>
                                        </li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </article>
                </div>
            </div>

            <footer class="tlpage__foot">
                <a class="btn btn-ghost" href="#top">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M19 12H5M11 18l-6-6 6-6" />
                    </svg>
                    返回飞鲸影视首页
                </a>
            </footer>
        </main>
    </div>
</template>

<style scoped>
.tlpage {
    min-height: 100svh;
    background: var(--bg);
}

/* ---------- top bar ---------- */

.tlpage__bar {
    position: sticky;
    top: 0;
    z-index: 100;
    height: var(--nav-h);
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: rgba(245, 245, 247, 0.8);
    backdrop-filter: saturate(180%) blur(20px);
    -webkit-backdrop-filter: saturate(180%) blur(20px);
    border-bottom: 1px solid var(--hairline);
}

.tlpage__back {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: 14px;
    font-weight: 600;
    color: var(--ink-soft);
    transition: color 0.2s var(--ease);
}

.tlpage__back:hover {
    color: var(--brand);
}

.tlpage__crumb {
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: var(--ink-muted);
}

/* ---------- head ---------- */

.tlpage__main {
    padding: clamp(56px, 8vw, 96px) 24px clamp(60px, 8vw, 96px);
}

.tlpage__head {
    text-align: center;
    margin-bottom: clamp(44px, 6vw, 72px);
}

.tlpage__title {
    font-size: clamp(30px, 4.6vw, 52px);
    font-weight: 700;
    letter-spacing: -0.025em;
    line-height: 1.14;
}

.tlpage__lede {
    margin-top: 16px;
    font-size: clamp(15px, 1.6vw, 18px);
    line-height: 1.7;
    color: var(--ink-muted);
}

/* ---------- skeleton / error ---------- */

.tlpage__skeleton {
    display: grid;
    gap: 22px;
    max-width: 780px;
    margin: 0 auto;
    padding-left: 42px;
}

.skel {
    padding: 28px 30px;
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    display: grid;
    gap: 14px;
}

.skel__bar {
    height: 14px;
    border-radius: 7px;
    background: linear-gradient(90deg, rgba(0, 0, 0, 0.05) 25%, rgba(0, 0, 0, 0.09) 45%, rgba(0, 0, 0, 0.05) 65%);
    background-size: 220% 100%;
    animation: shimmer 1.4s linear infinite;
}

.skel__bar--w30 { width: 30%; }
.skel__bar--w70 { width: 70%; }

@keyframes shimmer {
    to {
        background-position: -120% 0;
    }
}

.tlpage__error {
    max-width: 560px;
    margin: 0 auto;
    padding: 40px 32px;
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    text-align: center;
    display: grid;
    justify-items: center;
    gap: 14px;
    color: var(--ink-muted);
}

.tlpage__error svg {
    color: #FF9500;
}

.tlpage__error-actions {
    display: flex;
    gap: 22px;
    font-size: 14px;
    font-weight: 600;
}

/* ---------- vertical timeline ---------- */

.tl {
    max-width: 780px;
    margin: 0 auto;
}

.tl__item {
    position: relative;
    display: grid;
    grid-template-columns: 26px 1fr;
    gap: clamp(16px, 2.5vw, 26px);
    padding-bottom: 26px;
}

.tl__item:last-child {
    padding-bottom: 0;
}

/* The rail line runs from the dot down to the next item's dot. */
.tl__rail {
    position: relative;
}

.tl__rail::before {
    content: '';
    position: absolute;
    top: 16px;
    bottom: -10px;
    left: 50%;
    translate: -50% 0;
    width: 2px;
    background: linear-gradient(180deg, rgba(0, 122, 255, 0.35), rgba(0, 0, 0, 0.09));
}

.tl__item:last-child .tl__rail::before {
    display: none;
}

.tl__dot {
    position: absolute;
    top: 4px;
    left: 50%;
    translate: -50% 0;
    width: 13px;
    height: 13px;
    border-radius: 50%;
    background: #fff;
    border: 3px solid var(--brand);
    box-shadow: 0 0 0 4px rgba(0, 122, 255, 0.1);
}

.tl__dot--latest {
    border-color: #5E5CE6;
    box-shadow: 0 0 0 5px rgba(94, 92, 230, 0.16);
    animation: latest-pulse 2.4s var(--ease) infinite;
}

@keyframes latest-pulse {
    0%,
    100% {
        box-shadow: 0 0 0 5px rgba(94, 92, 230, 0.16);
    }
    50% {
        box-shadow: 0 0 0 9px rgba(94, 92, 230, 0.06);
    }
}

.tl__card {
    position: relative;
    padding: clamp(22px, 3vw, 30px) clamp(20px, 3vw, 32px);
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    cursor: pointer;
    transition: box-shadow 0.35s var(--ease), border-color 0.35s var(--ease);
}

.tl__card:hover {
    box-shadow: var(--shadow-md);
    border-color: rgba(0, 122, 255, 0.24);
}

/* Details stay folded until hover; a tap pins a card open on touch devices,
   where hover never fires. */
.tl__panel {
    display: grid;
    grid-template-rows: 0fr;
    transition: grid-template-rows 0.5s var(--ease);
}

.tl__panel-inner {
    overflow: hidden;
}

.tl__item:hover .tl__panel,
.tl__item--open .tl__panel,
.tl__card:focus-within .tl__panel {
    grid-template-rows: 1fr;
}

.tl__chevron {
    position: absolute;
    top: clamp(24px, 3vw, 32px);
    right: clamp(18px, 3vw, 28px);
    color: var(--ink-muted);
    transition: transform 0.35s var(--ease), color 0.35s var(--ease);
}

.tl__item:hover .tl__chevron,
.tl__item--open .tl__chevron {
    transform: rotate(180deg);
    color: var(--brand);
}

.tl__head {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
    /* Leave room for the absolutely-positioned chevron. */
    padding-right: 32px;
}

.tl__v {
    font-size: 21px;
    font-weight: 750;
    letter-spacing: -0.015em;
    font-variant-numeric: tabular-nums;
}

.tl__date {
    font-size: 13.5px;
    color: var(--ink-muted);
    font-variant-numeric: tabular-nums;
}

.tl__badge {
    padding: 3px 10px;
    border-radius: 999px;
    background: linear-gradient(92deg, var(--brand), #5E5CE6);
    color: #fff;
    font-size: 11.5px;
    font-weight: 700;
    letter-spacing: 0.05em;
}

.tl__counts {
    display: flex;
    gap: 12px;
    margin-left: auto;
}

.tl__count {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    font-weight: 600;
    color: var(--ink-muted);
    white-space: nowrap;
}

.tl__count i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    opacity: 0.85;
}

.tlpage__foot {
    display: flex;
    justify-content: center;
    margin-top: clamp(44px, 6vw, 64px);
}

@media (max-width: 640px) {
    .tl__item {
        grid-template-columns: 20px 1fr;
        gap: 12px;
    }

    .tl__dot {
        width: 11px;
        height: 11px;
    }

    .tl__counts {
        margin-left: 0;
        width: 100%;
    }

    .tlpage__skeleton {
        padding-left: 32px;
    }
}

@media (prefers-reduced-motion: reduce) {
    .skel__bar {
        animation: none;
    }

    .tl__dot--latest {
        animation: none;
    }
}
</style>
