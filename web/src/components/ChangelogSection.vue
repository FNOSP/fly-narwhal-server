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

// Accordion over the full history; one version open at a time.
const open = ref(null)

function toggle(version) {
    open.value = open.value === version ? null : version
}
</script>

<template>
    <section id="changelog" class="clg">
        <div class="shell">
            <div class="section-head clg__head">
                <p v-reveal class="eyebrow">更新日志</p>
                <h2 v-reveal:mask class="h2">
                    <span class="line-mask"><span style="--line-delay: 0ms">持续进化，</span></span>
                    <span class="line-mask"><span style="--line-delay: 110ms">每个版本都有据可查。</span></span>
                </h2>
                <p v-reveal="180" class="lede">
                    最新版本的完整变化，以及发布以来的全部更新记录。
                    记录来自客户端仓库的
                    <a :href="CHANGELOG_URL" target="_blank" rel="noopener noreferrer">CHANGELOG.md</a>。
                </p>
            </div>

            <!-- loading skeleton -->
            <div v-if="loading" class="clg__skeleton" aria-label="更新日志加载中">
                <div class="skel skel--latest">
                    <div class="skel__bar skel__bar--w30"></div>
                    <div class="skel__bar"></div>
                    <div class="skel__bar"></div>
                    <div class="skel__bar skel__bar--w60"></div>
                </div>
                <div class="skel">
                    <div class="skel__bar skel__bar--w40"></div>
                    <div class="skel__bar"></div>
                    <div class="skel__bar skel__bar--w80"></div>
                </div>
            </div>

            <!-- fetch failed -->
            <div v-else-if="error" v-reveal class="clg__error">
                <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5h.01" />
                </svg>
                <p>更新日志暂时无法加载，可能是网络原因。</p>
                <a :href="CHANGELOG_URL" target="_blank" rel="noopener noreferrer">前往 GitHub 查看完整更新日志 →</a>
            </div>

            <template v-else>
                <!-- latest version, front and centre -->
                <article v-if="latest" v-reveal class="clg-latest">
                    <header class="clg-latest__head">
                        <div class="clg-latest__ver">
                            v{{ latest.version }}
                            <span class="clg-latest__badge">最新版</span>
                        </div>
                        <time v-if="latest.date" class="clg-latest__date">{{ latest.date }}</time>
                    </header>

                    <div v-for="(note, i) in latest.notes" :key="i" class="clg-note">
                        <ChangelogText :nodes="note" />
                    </div>

                    <p v-for="(para, i) in latest.intro" :key="`i${i}`" class="clg-intro">
                        <ChangelogText :nodes="para" />
                    </p>

                    <div v-for="cat in latest.categories" :key="cat.name" class="clg-cat">
                        <span class="clg-tag" :style="{ color: catMeta(cat.name).color, background: catMeta(cat.name).bg }">
                            {{ catMeta(cat.name).label }}
                        </span>
                        <ul class="clg-list">
                            <li v-for="(item, i) in cat.items" :key="i" class="clg-item">
                                <strong v-if="item.title.length" class="clg-item__title">
                                    <ChangelogText :nodes="item.title" />
                                </strong>
                                <span class="clg-item__desc"><ChangelogText :nodes="item.desc" /></span>
                            </li>
                        </ul>
                    </div>
                </article>

                <!-- full history -->
                <div class="clg-history">
                    <h3 v-reveal class="clg-history__title">全部版本</h3>

                    <div v-for="(v, i) in history" :key="v.version" v-reveal="Math.min(i, 4) * 70" class="clg-ver" :class="{ 'clg-ver--open': open === v.version }">
                        <button class="clg-ver__head" type="button" :aria-expanded="open === v.version" @click="toggle(v.version)">
                            <span class="clg-ver__v">v{{ v.version }}</span>
                            <time v-if="v.date" class="clg-ver__date">{{ v.date }}</time>
                            <span class="clg-ver__counts">
                                <span
                                    v-for="cat in v.categories.filter((c) => c.items.length)"
                                    :key="cat.name"
                                    class="clg-count"
                                >
                                    <i :style="{ background: catMeta(cat.name).color }"></i>
                                    {{ catMeta(cat.name).label }} {{ cat.items.length }}
                                </span>
                            </span>
                            <svg class="clg-ver__chevron" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                <path d="M6 9l6 6 6-6" />
                            </svg>
                        </button>

                        <div class="clg-ver__panel">
                            <div class="clg-ver__inner">
                                <div v-for="(note, ni) in v.notes" :key="ni" class="clg-note clg-note--sm">
                                    <ChangelogText :nodes="note" />
                                </div>
                                <p v-for="(para, pi) in v.intro" :key="`p${pi}`" class="clg-intro">
                                    <ChangelogText :nodes="para" />
                                </p>
                                <div v-for="cat in v.categories" :key="cat.name" class="clg-cat">
                                    <span class="clg-tag clg-tag--sm" :style="{ color: catMeta(cat.name).color, background: catMeta(cat.name).bg }">
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
                    </div>
                </div>
            </template>
        </div>
    </section>
</template>

<style scoped>
.clg {
    padding: clamp(72px, 11vw, 140px) 0;
    /* Continues from the white end of the download section and settles into
       the grey FAQ section below. */
    background: linear-gradient(180deg, #FFFFFF 0%, var(--bg) 55%);
}

.clg__head {
    text-align: center;
}

.clg__head .lede {
    margin: 16px auto 0;
}

/* ---------- skeleton ---------- */

.clg__skeleton {
    display: grid;
    gap: 22px;
    max-width: 860px;
    margin: 0 auto;
}

.skel {
    padding: 30px 32px;
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

.skel--latest .skel__bar:first-child {
    height: 30px;
    width: 220px;
    border-radius: 10px;
}

.skel__bar--w30 { width: 30%; }
.skel__bar--w40 { width: 40%; }
.skel__bar--w60 { width: 60%; }
.skel__bar--w80 { width: 80%; }

@keyframes shimmer {
    to {
        background-position: -120% 0;
    }
}

/* ---------- error ---------- */

.clg__error {
    max-width: 620px;
    margin: 0 auto;
    padding: 40px 32px;
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    text-align: center;
    display: grid;
    justify-items: center;
    gap: 12px;
    color: var(--ink-muted);
}

.clg__error svg {
    color: #FF9500;
}

.clg__error p {
    font-size: 15px;
}

.clg__error a {
    font-size: 14px;
    font-weight: 600;
}

/* ---------- latest card ---------- */

.clg-latest {
    position: relative;
    max-width: 860px;
    margin: 0 auto;
    padding: clamp(28px, 4vw, 44px) clamp(24px, 4.5vw, 52px) clamp(30px, 4vw, 46px);
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-md);
    overflow: hidden;
}

.clg-latest::before {
    content: '';
    position: absolute;
    inset: 0 0 auto 0;
    height: 3px;
    background: linear-gradient(90deg, var(--brand), #5E5CE6 55%, #BF5AF2);
}

.clg-latest__head {
    display: flex;
    align-items: baseline;
    gap: 16px;
    flex-wrap: wrap;
    margin-bottom: 8px;
}

.clg-latest__ver {
    font-size: clamp(30px, 4vw, 42px);
    font-weight: 800;
    letter-spacing: -0.02em;
    font-variant-numeric: tabular-nums;
    display: inline-flex;
    align-items: center;
    gap: 12px;
}

.clg-latest__badge {
    padding: 5px 13px;
    border-radius: 999px;
    background: linear-gradient(92deg, var(--brand), #5E5CE6);
    color: #fff;
    font-size: 12.5px;
    font-weight: 700;
    letter-spacing: 0.05em;
}

.clg-latest__date {
    font-size: 14px;
    color: var(--ink-muted);
    font-variant-numeric: tabular-nums;
}

/* ---------- shared blocks ---------- */

.clg-note {
    margin-top: 14px;
    padding: 12px 16px;
    border-radius: var(--radius-sm);
    background: rgba(0, 122, 255, 0.06);
    border: 1px solid rgba(0, 122, 255, 0.14);
    font-size: 13.5px;
    line-height: 1.75;
    color: var(--ink-soft);
}

.clg-note :deep(a) {
    font-weight: 600;
}

.clg-note--sm {
    margin-top: 0;
    margin-bottom: 16px;
    font-size: 12.5px;
}

.clg-intro {
    margin-top: 16px;
    font-size: 15px;
    line-height: 1.8;
    color: var(--ink-soft);
}

.clg-cat {
    margin-top: clamp(22px, 3vw, 30px);
}

.clg-tag {
    display: inline-block;
    padding: 5px 13px;
    border-radius: 999px;
    font-size: 12.5px;
    font-weight: 700;
    letter-spacing: 0.03em;
    margin-bottom: 14px;
}

.clg-tag--sm {
    padding: 3px 10px;
    font-size: 11.5px;
    margin-bottom: 10px;
}

.clg-list {
    list-style: none;
    display: grid;
    gap: 12px;
}

.clg-item {
    position: relative;
    padding-left: 18px;
    font-size: 14.5px;
    line-height: 1.75;
}

.clg-item::before {
    content: '';
    position: absolute;
    left: 2px;
    top: 0.72em;
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.22);
}

.clg-item__title {
    font-weight: 650;
    color: var(--ink);
    margin-right: 4px;
}

.clg-item__desc {
    color: var(--ink-muted);
}

.clg-item :deep(.clg-code),
.clg-latest :deep(.clg-code) {
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    font-size: 0.9em;
    background: rgba(0, 0, 0, 0.055);
    padding: 1px 6px;
    border-radius: 5px;
    color: var(--ink-soft);
}

/* ---------- history ---------- */

.clg-history {
    max-width: 860px;
    margin: clamp(56px, 8vw, 96px) auto 0;
}

.clg-history__title {
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--ink-muted);
    text-align: center;
    margin-bottom: 22px;
}

.clg-ver {
    border-radius: var(--radius-md);
    background: var(--surface);
    border: 1px solid var(--hairline);
    overflow: hidden;
    transition: border-color 0.3s var(--ease), box-shadow 0.3s var(--ease);
}

.clg-ver + .clg-ver {
    margin-top: 10px;
}

.clg-ver--open {
    border-color: rgba(0, 122, 255, 0.32);
    box-shadow: var(--shadow-md);
}

.clg-ver__head {
    display: flex;
    align-items: center;
    gap: 14px;
    width: 100%;
    padding: 16px 20px;
    text-align: left;
}

.clg-ver__v {
    font-size: 16px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.01em;
}

.clg-ver__date {
    font-size: 13px;
    color: var(--ink-muted);
    font-variant-numeric: tabular-nums;
}

.clg-ver__counts {
    display: flex;
    gap: 12px;
    margin-left: auto;
}

.clg-count {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    font-weight: 600;
    color: var(--ink-muted);
    white-space: nowrap;
}

.clg-count i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    opacity: 0.85;
}

.clg-ver__chevron {
    flex-shrink: 0;
    color: var(--ink-muted);
    transition: transform 0.35s var(--ease);
}

.clg-ver--open .clg-ver__chevron {
    transform: rotate(180deg);
    color: var(--brand);
}

.clg-ver__panel {
    display: grid;
    grid-template-rows: 0fr;
    transition: grid-template-rows 0.45s var(--ease);
}

.clg-ver--open .clg-ver__panel {
    grid-template-rows: 1fr;
}

.clg-ver__inner {
    overflow: hidden;
    padding: 0 20px;
}

.clg-ver--open .clg-ver__inner {
    padding: 4px 20px 26px;
}

.clg-ver__inner .clg-item {
    font-size: 13.5px;
}

@media (max-width: 640px) {
    .clg-ver__counts {
        display: none;
    }

    .clg-ver__head {
        padding: 15px 16px;
        gap: 10px;
    }

    .clg-ver__inner {
        padding: 0 16px;
    }

    .clg-ver--open .clg-ver__inner {
        padding: 4px 16px 22px;
    }

    .clg-item {
        font-size: 13.5px;
    }
}

@media (prefers-reduced-motion: reduce) {
    .skel__bar {
        animation: none;
    }
}
</style>
