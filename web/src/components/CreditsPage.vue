<script setup>
import { computed } from 'vue'
import ThemeToggle from './ThemeToggle.vue'
import LocaleToggle from './LocaleToggle.vue'
import { creditGroups, creditCount } from '../assets/credits'
import { useI18n, format } from '../composables/useI18n'

const { t } = useI18n()

const lede = computed(() => format(t.value.credits.lede, { count: creditCount }))
const noteOf = (key) => t.value.credits.notes[key]
</script>

<template>
    <div class="crpage">
        <header class="crpage__bar shell">
            <a class="crpage__back" href="#top">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M19 12H5M11 18l-6-6 6-6" />
                </svg>
                {{ t.credits.backHome }}
            </a>
            <div class="crpage__actions">
                <span class="crpage__crumb">{{ t.credits.crumb }}</span>
                <LocaleToggle />
                <ThemeToggle />
            </div>
        </header>

        <main class="shell crpage__main">
            <div class="crpage__head">
                <p class="eyebrow">{{ t.credits.eyebrow }}</p>
                <h1 class="crpage__title">{{ t.credits.title }}</h1>
                <p class="crpage__lede">
                    {{ lede }}
                </p>
            </div>

            <div class="crpage__groups">
                <section
                    v-for="(g, i) in creditGroups"
                    :key="g.groupKey"
                    v-reveal="i * 80"
                    class="crpage__group"
                >
                    <h2 class="crpage__group-title">
                        {{ t.credits.groups[g.groupKey] }}
                        <span class="crpage__group-count">{{ g.items.length }}</span>
                    </h2>
                    <ul class="crpage__list">
                        <li v-for="c in g.items" :key="c.name" class="crpage__item">
                            <a class="crpage__name" :href="c.url" target="_blank" rel="noopener noreferrer">
                                {{ c.name }}
                                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                    <path d="M7 17L17 7M9 7h8v8" />
                                </svg>
                            </a>
                            <span class="crpage__note">{{ noteOf(c.noteKey) }}</span>
                        </li>
                    </ul>
                </section>
            </div>

            <aside class="crpage__license">
                <p>
                    {{ t.credits.licenseBefore }}
                    <a href="https://www.gnu.org/licenses/agpl-3.0.html" target="_blank" rel="noopener noreferrer">{{ t.credits.licenseLink }}</a>
                    {{ t.credits.licenseMiddle }}
                    <a href="https://github.com/FNOSP/fly-narwhal-server/issues" target="_blank" rel="noopener noreferrer">{{ t.credits.licenseLinkTail }}</a>{{ t.credits.licenseAfter }}
                </p>
            </aside>

            <footer class="crpage__foot">
                <a class="btn btn-ghost" href="#top">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M19 12H5M11 18l-6-6 6-6" />
                    </svg>
                    {{ t.credits.backToHome }}
                </a>
            </footer>
        </main>
    </div>
</template>

<style scoped>
.crpage {
    min-height: 100svh;
    background: var(--bg);
}

/* ---------- top bar (mirrors TimelinePage chrome) ---------- */

.crpage__bar {
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

.crpage__actions {
    display: flex;
    align-items: center;
    gap: 12px;
}

.crpage__back {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: 14px;
    font-weight: 600;
    color: var(--ink-soft);
    transition: color 0.2s var(--ease);
}

.crpage__back:hover {
    color: var(--brand);
}

.crpage__crumb {
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: var(--ink-muted);
}

/* ---------- head ---------- */

.crpage__main {
    padding: clamp(56px, 8vw, 96px) 24px clamp(60px, 8vw, 96px);
}

.crpage__head {
    text-align: center;
    max-width: 640px;
    margin: 0 auto clamp(44px, 6vw, 72px);
}

.crpage__title {
    font-size: clamp(30px, 4.6vw, 52px);
    font-weight: 700;
    letter-spacing: -0.025em;
    line-height: 1.14;
}

.crpage__lede {
    margin-top: 16px;
    font-size: clamp(15px, 1.6vw, 18px);
    line-height: 1.7;
    color: var(--ink-muted);
}

/* ---------- groups ---------- */

.crpage__groups {
    max-width: 860px;
    margin: 0 auto;
    display: grid;
    gap: 22px;
}

.crpage__group {
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    overflow: hidden;
}

.crpage__group-title {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 20px clamp(20px, 3vw, 30px);
    font-size: 15.5px;
    font-weight: 650;
    letter-spacing: 0.01em;
    border-bottom: 1px solid var(--hairline);
    background: linear-gradient(180deg, var(--brand-wash), transparent);
}

.crpage__group-count {
    padding: 1px 9px;
    border-radius: 999px;
    background: var(--brand-tint);
    color: var(--brand-deep);
    font-size: 12px;
    font-weight: 650;
    font-variant-numeric: tabular-nums;
}

.crpage__list {
    list-style: none;
}

.crpage__item {
    display: grid;
    grid-template-columns: minmax(160px, 240px) 1fr;
    gap: 6px 24px;
    align-items: baseline;
    padding: 13px clamp(20px, 3vw, 30px);
    transition: background-color 0.2s var(--ease);
}

.crpage__item + .crpage__item {
    border-top: 1px solid var(--hairline);
}

.crpage__item:hover {
    background: var(--brand-wash);
}

.crpage__name {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 14.5px;
    font-weight: 600;
    color: var(--ink);
    transition: color 0.2s var(--ease);
}

.crpage__name svg {
    opacity: 0;
    translate: -3px 3px;
    transition: opacity 0.2s var(--ease), translate 0.2s var(--ease);
}

.crpage__item:hover .crpage__name {
    color: var(--brand);
}

.crpage__item:hover .crpage__name svg {
    opacity: 1;
    translate: 0 0;
}

.crpage__note {
    font-size: 13.5px;
    line-height: 1.6;
    color: var(--ink-muted);
}

/* ---------- license note + foot ---------- */

.crpage__license {
    max-width: 860px;
    margin: 36px auto 0;
    padding: 20px 26px;
    border-radius: var(--radius-md);
    border: 1px dashed var(--hairline-strong);
    font-size: 13.5px;
    line-height: 1.8;
    color: var(--ink-muted);
    text-align: center;
}

.crpage__license a {
    color: var(--ink-soft);
    font-weight: 600;
}

.crpage__license a:hover {
    color: var(--brand);
}

.crpage__foot {
    display: flex;
    justify-content: center;
    margin-top: clamp(44px, 6vw, 64px);
}

/* ---------- responsive ---------- */

@media (max-width: 640px) {
    .crpage__item {
        grid-template-columns: 1fr;
        gap: 2px;
        padding: 14px 18px;
    }

    .crpage__group-title {
        padding: 16px 18px;
    }
}

@media (prefers-reduced-motion: reduce) {
    .crpage__name svg,
    .crpage__item {
        transition: none;
    }
}
</style>
