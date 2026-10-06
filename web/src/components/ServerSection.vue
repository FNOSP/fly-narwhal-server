<script setup>
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()

// Bar widths are proportional to the measured RSS values (472 / 165 / 120 MB);
// labels and numbers come from the catalog by position.
const barWidths = ['100%', '35%', '25%']
</script>

<template>
    <section id="server" class="server">
        <div class="shell">
            <div class="section-head server__head">
                <p v-reveal class="eyebrow">{{ t.server.eyebrow }}</p>
                <h2 v-reveal="80" class="h2">{{ t.server.h2 }}</h2>
                <p v-reveal="160" class="lede">
                    {{ t.server.lede }}
                </p>
            </div>

            <div v-reveal="120" class="server__funnel">
                <div
                    v-for="(m, i) in t.server.milestones"
                    :key="m.label"
                    v-reveal="i * 140"
                    class="mile"
                    :class="{ 'mile--now': i === t.server.milestones.length - 1 }"
                    :style="{ '--w': barWidths[i] }"
                >
                    <div class="mile__row">
                        <span class="mile__label">{{ m.label }}</span>
                        <span class="mile__value">{{ m.value }}</span>
                    </div>
                    <div class="mile__bar">
                        <span class="mile__fill"></span>
                    </div>
                    <p class="mile__note">{{ m.note }}</p>
                </div>
            </div>

            <div class="server__measures">
                <article
                    v-for="(c, i) in t.server.measures"
                    :key="c.title"
                    v-reveal="(i % 3) * 100"
                    class="measure"
                >
                    <h3 class="measure__title">{{ c.title }}</h3>
                    <p class="measure__desc">{{ c.desc }}</p>
                </article>
            </div>

            <p v-reveal class="server__footnote">{{ t.server.footnote }}</p>
        </div>
    </section>
</template>

<style scoped>
.server {
    padding: clamp(72px, 11vw, 140px) 0;
    background: var(--surface);
}

.server__head {
    text-align: center;
}

.server__head .lede {
    margin: 16px auto 0;
}

.server__funnel {
    max-width: 760px;
    margin: clamp(40px, 5vw, 60px) auto 0;
    display: grid;
    gap: 26px;
    padding: clamp(24px, 3.5vw, 40px);
    border-radius: var(--radius-lg);
    background: var(--grad-panel);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-md);
}

.mile__row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 10px;
}

.mile__label {
    font-size: 14px;
    font-weight: 650;
    color: var(--ink-soft);
}

.mile__value {
    font-size: clamp(20px, 2.6vw, 27px);
    font-weight: 750;
    letter-spacing: -0.02em;
    font-variant-numeric: tabular-nums;
}

.mile--now .mile__label,
.mile--now .mile__value {
    color: var(--brand);
}

.mile__bar {
    height: 14px;
    border-radius: 999px;
    background: var(--field);
    border: 1px solid var(--hairline);
    overflow: hidden;
}

.mile__fill {
    display: block;
    height: 100%;
    width: 0;
    border-radius: 999px;
    background: linear-gradient(90deg, rgba(0, 122, 255, 0.55), var(--brand));
    transition: width 1.1s var(--ease);
    transition-delay: var(--delay, 0ms);
}

.mile.is-revealed .mile__fill {
    width: var(--w);
}

.mile--now .mile__fill {
    background: linear-gradient(90deg, var(--brand), #6CB2FF);
}

.mile__note {
    margin-top: 8px;
    font-size: 13px;
    color: var(--ink-muted);
}

.server__measures {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    margin-top: clamp(28px, 4vw, 44px);
}

.measure {
    padding: 24px 26px;
    border-radius: var(--radius-md);
    border: 1px solid var(--hairline);
    background: var(--bg);
    transition: box-shadow 0.35s var(--ease), border-color 0.35s var(--ease);
}

.measure:hover {
    box-shadow: var(--shadow-md);
    border-color: rgba(0, 122, 255, 0.24);
}

.measure__title {
    font-size: 16px;
    font-weight: 650;
    margin-bottom: 8px;
}

.measure__desc {
    font-size: 14px;
    line-height: 1.68;
    color: var(--ink-muted);
}

.server__footnote {
    margin-top: clamp(24px, 3vw, 36px);
    text-align: center;
    font-size: 13px;
    line-height: 1.7;
    color: var(--ink-muted);
    max-width: 62ch;
    margin-left: auto;
    margin-right: auto;
}

@media (max-width: 900px) {
    .server__measures {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (max-width: 560px) {
    .server__measures {
        grid-template-columns: 1fr;
    }
}

@media (prefers-reduced-motion: reduce) {
    .mile__fill {
        transition: none;
        width: var(--w);
    }
}
</style>
