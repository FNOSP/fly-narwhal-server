<script setup>
import { computed } from 'vue'
import ThemeToggle from './ThemeToggle.vue'
import LocaleToggle from './LocaleToggle.vue'
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()

// Chapter content comes straight from the catalog: each chapter carries the
// same shape (title/lead + optional prereq, steps, sections, tips), so the
// template renders any mix of them. Structure mirrors the faq.items[]
// precedent — long-form copy lives in the locale files, not in assets/.
const chapters = computed(() => {
    const g = t.value.guide
    return [
        { id: 'guide-server', toc: g.toc.server, ...g.server },
        { id: 'guide-smart-skip', toc: g.toc.smartSkip, ...g.smartSkip },
        { id: 'guide-danmaku', toc: g.toc.danmaku, ...g.danmaku },
    ]
})

// In-page TOC must NOT use hash anchors: any hash that isn't `#/guide` makes
// App.vue's routeFromHash() fall back to the landing page. Scroll via JS and
// leave the hash untouched.
function scrollToChapter(id) {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
    <div class="gpage">
        <header class="gpage__bar shell">
            <a class="gpage__back" href="#top">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M19 12H5M11 18l-6-6 6-6" />
                </svg>
                {{ t.guide.backHome }}
            </a>
            <div class="gpage__actions">
                <span class="gpage__crumb">{{ t.guide.crumb }}</span>
                <LocaleToggle />
                <ThemeToggle />
            </div>
        </header>

        <main class="shell gpage__main">
            <div class="gpage__head">
                <p class="eyebrow">{{ t.guide.eyebrow }}</p>
                <h1 class="gpage__title">{{ t.guide.title }}</h1>
                <p class="gpage__lede">
                    {{ t.guide.lede }}
                </p>
            </div>

            <nav class="gpage__toc" :aria-label="t.guide.tocAria">
                <button
                    v-for="c in chapters"
                    :key="c.id"
                    type="button"
                    class="gpage__toc-chip"
                    @click="scrollToChapter(c.id)"
                >
                    {{ c.toc }}
                </button>
            </nav>

            <div class="gpage__chapters">
                <section
                    v-for="(c, i) in chapters"
                    :id="c.id"
                    :key="c.id"
                    v-reveal="i * 80"
                    class="gpage__chapter"
                >
                    <header class="gpage__chapter-head">
                        <span class="gpage__chapter-num" aria-hidden="true">{{ String(i + 1).padStart(2, '0') }}</span>
                        <div class="gpage__chapter-heading">
                            <h2 class="gpage__chapter-title">{{ c.title }}</h2>
                            <p class="gpage__chapter-lead">{{ c.lead }}</p>
                        </div>
                    </header>

                    <div class="gpage__body">
                        <p v-if="c.prereq" class="gpage__prereq">
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                <circle cx="12" cy="12" r="10" />
                                <path d="M12 16v-4M12 8h.01" />
                            </svg>
                            {{ c.prereq }}
                        </p>

                        <ol v-if="c.steps?.length" class="gpage__steps">
                            <li v-for="s in c.steps" :key="s.title" class="gpage__step">
                                <h3 class="gpage__step-title">{{ s.title }}</h3>
                                <p class="gpage__step-desc">{{ s.desc }}</p>
                            </li>
                        </ol>

                        <div v-if="c.sections?.length" class="gpage__sections">
                            <section v-for="s in c.sections" :key="s.title" class="gpage__section">
                                <h3 class="gpage__section-title">{{ s.title }}</h3>
                                <ul class="gpage__points">
                                    <li v-for="p in s.points" :key="p">{{ p }}</li>
                                </ul>
                            </section>
                        </div>

                        <aside v-if="c.tips?.length" class="gpage__tips">
                            <p v-for="tip in c.tips" :key="tip" class="gpage__tip">
                                <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                    <path d="M9 18h6M10 22h4M12 2a7 7 0 0 0-4 12.7c.6.5 1 1.4 1 2.3h6c0-.9.4-1.8 1-2.3A7 7 0 0 0 12 2z" />
                                </svg>
                                <span>{{ tip }}</span>
                            </p>
                        </aside>
                    </div>
                </section>
            </div>

            <footer class="gpage__foot">
                <a class="btn btn-ghost" href="#top">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M19 12H5M11 18l-6-6 6-6" />
                    </svg>
                    {{ t.guide.backToHome }}
                </a>
            </footer>
        </main>
    </div>
</template>

<style scoped>
.gpage {
    min-height: 100svh;
    background: var(--bg);
}

/* ---------- top bar (mirrors TimelinePage chrome) ---------- */

.gpage__bar {
    position: sticky;
    top: 0;
    z-index: 100;
    height: var(--nav-h);
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: var(--chrome-glass);
    backdrop-filter: saturate(180%) blur(20px);
    -webkit-backdrop-filter: saturate(180%) blur(20px);
    border-bottom: 1px solid var(--hairline);
}

.gpage__actions {
    display: flex;
    align-items: center;
    gap: 12px;
}

.gpage__back {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: 14px;
    font-weight: 600;
    color: var(--ink-soft);
    transition: color 0.2s var(--ease);
}

.gpage__back:hover {
    color: var(--brand);
}

.gpage__crumb {
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: var(--ink-muted);
}

/* ---------- head + TOC ---------- */

.gpage__main {
    padding: clamp(56px, 8vw, 96px) 24px clamp(60px, 8vw, 96px);
}

.gpage__head {
    text-align: center;
    max-width: 640px;
    margin: 0 auto clamp(28px, 4vw, 40px);
}

.gpage__title {
    font-size: clamp(30px, 4.6vw, 52px);
    font-weight: 700;
    letter-spacing: -0.025em;
    line-height: 1.14;
}

.gpage__lede {
    margin-top: 16px;
    font-size: clamp(15px, 1.6vw, 18px);
    line-height: 1.7;
    color: var(--ink-muted);
}

.gpage__toc {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;
    margin: 0 auto clamp(44px, 6vw, 72px);
}

.gpage__toc-chip {
    padding: 8px 18px;
    border-radius: 999px;
    border: 1px solid var(--hairline);
    background: var(--surface);
    color: var(--ink-soft);
    font-size: 13.5px;
    font-weight: 600;
    cursor: pointer;
    transition: color 0.2s var(--ease), background-color 0.2s var(--ease), border-color 0.2s var(--ease);
}

.gpage__toc-chip:hover {
    color: var(--brand-deep);
    background: var(--brand-tint);
    border-color: transparent;
}

/* ---------- chapters ---------- */

.gpage__chapters {
    max-width: 860px;
    margin: 0 auto;
    display: grid;
    gap: 22px;
}

.gpage__chapter {
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    overflow: hidden;
    /* Sticky bar offset so TOC scrolling lands below it. */
    scroll-margin-top: calc(var(--nav-h) + 20px);
}

.gpage__chapter-head {
    display: flex;
    align-items: flex-start;
    gap: 16px;
    padding: 24px clamp(20px, 3vw, 30px);
    border-bottom: 1px solid var(--hairline);
    background: linear-gradient(180deg, var(--brand-wash), transparent);
}

.gpage__chapter-num {
    flex: none;
    font-size: 22px;
    font-weight: 700;
    letter-spacing: 0.02em;
    line-height: 1.2;
    color: var(--brand);
    font-variant-numeric: tabular-nums;
}

.gpage__chapter-title {
    font-size: clamp(18px, 2.2vw, 22px);
    font-weight: 700;
    letter-spacing: -0.01em;
    line-height: 1.3;
}

.gpage__chapter-lead {
    margin-top: 8px;
    font-size: 14.5px;
    line-height: 1.7;
    color: var(--ink-muted);
}

.gpage__body {
    display: grid;
    gap: 26px;
    padding: 26px clamp(20px, 3vw, 30px) 28px;
}

/* ---------- prereq note ---------- */

.gpage__prereq {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    justify-self: start;
    padding: 8px 14px;
    border-radius: var(--radius-md);
    background: var(--brand-wash);
    color: var(--brand-deep);
    font-size: 13.5px;
    font-weight: 600;
}

.gpage__prereq svg {
    flex: none;
    color: var(--brand);
}

/* ---------- numbered steps ---------- */

.gpage__steps {
    list-style: none;
    counter-reset: gstep;
    display: grid;
    gap: 4px;
}

.gpage__step {
    counter-increment: gstep;
    display: grid;
    grid-template-columns: 30px 1fr;
    gap: 4px 14px;
    padding: 12px 0;
}

.gpage__step + .gpage__step {
    border-top: 1px solid var(--hairline);
}

.gpage__step::before {
    content: counter(gstep);
    grid-row: span 2;
    width: 26px;
    height: 26px;
    margin-top: 1px;
    border-radius: 50%;
    background: var(--brand-tint);
    color: var(--brand-deep);
    font-size: 13px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    display: flex;
    align-items: center;
    justify-content: center;
}

.gpage__step-title {
    font-size: 15px;
    font-weight: 650;
    line-height: 1.5;
}

.gpage__step-desc {
    font-size: 14px;
    line-height: 1.75;
    color: var(--ink-muted);
}

/* ---------- sub-sections with bullet points ---------- */

.gpage__sections {
    display: grid;
    gap: 24px;
}

.gpage__section-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 650;
    margin-bottom: 10px;
}

.gpage__section-title::before {
    content: '';
    flex: none;
    width: 4px;
    height: 15px;
    border-radius: 2px;
    background: var(--brand);
}

.gpage__points {
    list-style: none;
    display: grid;
    gap: 8px;
}

.gpage__points li {
    position: relative;
    padding-left: 18px;
    font-size: 14px;
    line-height: 1.75;
    color: var(--ink-soft);
}

.gpage__points li::before {
    content: '';
    position: absolute;
    left: 2px;
    top: 0.68em;
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--brand);
    opacity: 0.55;
}

/* ---------- tips ---------- */

.gpage__tips {
    display: grid;
    gap: 10px;
    padding: 18px 20px;
    border-radius: var(--radius-md);
    border: 1px dashed var(--hairline-strong);
}

.gpage__tip {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    font-size: 13.5px;
    line-height: 1.7;
    color: var(--ink-muted);
}

.gpage__tip svg {
    flex: none;
    margin-top: 3px;
    color: var(--brand);
}

/* ---------- foot ---------- */

.gpage__foot {
    display: flex;
    justify-content: center;
    margin-top: clamp(44px, 6vw, 64px);
}

/* ---------- responsive ---------- */

@media (max-width: 640px) {
    .gpage__chapter-head {
        padding: 18px;
        gap: 12px;
    }

    .gpage__body {
        padding: 20px 18px 22px;
    }

    .gpage__step {
        grid-template-columns: 26px 1fr;
        gap: 2px 10px;
    }
}

@media (prefers-reduced-motion: reduce) {
    .gpage__toc-chip,
    .gpage__back {
        transition: none;
    }
}
</style>
