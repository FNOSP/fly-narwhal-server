<script setup>
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

                <!-- entry to the full timeline page -->
                <div v-reveal="120" class="clg-more">
                    <a class="clg-more__link" href="#/timeline">
                        <div class="clg-more__art" aria-hidden="true">
                            <span v-for="n in 3" :key="n" class="clg-more__dot" :class="{ 'clg-more__dot--end': n === 3 }"></span>
                        </div>
                        <div class="clg-more__body">
                            <h3 class="clg-more__title">全部版本更新日志</h3>
                            <p class="clg-more__desc">
                                从 2.0.0-alpha 到今天共 {{ history.length }} 个版本，每一次发版的完整记录都在这里。
                            </p>
                        </div>
                        <svg class="clg-more__arrow" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M5 12h14M13 6l6 6-6 6" />
                        </svg>
                    </a>
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

/* ---------- entry to the timeline page ---------- */

.clg-more {
    max-width: 860px;
    margin: 26px auto 0;
}

.clg-more__link {
    display: flex;
    align-items: center;
    gap: clamp(18px, 3vw, 28px);
    padding: clamp(20px, 3vw, 28px) clamp(22px, 3.5vw, 34px);
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    color: var(--ink);
    transition: transform 0.35s var(--ease), box-shadow 0.35s var(--ease),
        border-color 0.35s var(--ease);
}

.clg-more__link:hover {
    transform: translateY(-3px);
    box-shadow: var(--shadow-md);
    border-color: rgba(0, 122, 255, 0.3);
}

/* Mini vertical timeline preview. */
.clg-more__art {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 11px;
    padding: 2px 0;
    flex-shrink: 0;
}

.clg-more__art::before {
    content: '';
    position: absolute;
    top: 6px;
    bottom: 6px;
    width: 2px;
    border-radius: 1px;
    background: linear-gradient(180deg, var(--brand), rgba(0, 122, 255, 0.15));
}

.clg-more__dot {
    position: relative;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--brand);
    opacity: 0.55;
}

.clg-more__dot--end {
    width: 13px;
    height: 13px;
    opacity: 1;
    box-shadow: 0 0 0 4px rgba(0, 122, 255, 0.16);
}

.clg-more__title {
    font-size: 18px;
    font-weight: 650;
    letter-spacing: -0.01em;
    margin-bottom: 6px;
}

.clg-more__desc {
    font-size: 14px;
    line-height: 1.65;
    color: var(--ink-muted);
}

.clg-more__arrow {
    flex-shrink: 0;
    margin-left: auto;
    color: var(--brand);
    transition: transform 0.35s var(--ease);
}

.clg-more__link:hover .clg-more__arrow {
    transform: translateX(6px);
}

@media (max-width: 560px) {
    .clg-more__art {
        display: none;
    }
}

@media (prefers-reduced-motion: reduce) {
    .skel__bar {
        animation: none;
    }

    .clg-more__link:hover {
        transform: none;
    }
}
</style>
