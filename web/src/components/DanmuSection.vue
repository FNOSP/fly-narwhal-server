<script setup>
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()

// Structural only: the first card is wider and carries the platform chips,
// the last spans the full row; title/desc come from the catalog by position.
const spans = ['span3 chips', 'span3', 'span2', 'span2', 'span2', 'span6']
</script>

<template>
    <section id="danmu" class="danmu">
        <div class="shell">
            <div class="section-head danmu__head">
                <p v-reveal class="eyebrow">{{ t.danmu.eyebrow }}</p>
                <h2 v-reveal="80" class="h2">
                    {{ t.danmu.h2Line1 }}<br />{{ t.danmu.h2Line2 }}
                </h2>
                <p v-reveal="160" class="lede">
                    {{ t.danmu.lede }}
                </p>
            </div>

            <div class="danmu__grid">
                <article
                    v-for="(c, i) in t.danmu.items"
                    :key="c.title"
                    v-reveal="(i % 3) * 100"
                    class="danmu__card"
                    :class="`danmu__card--${spans[i].split(' ')[0]}`"
                >
                    <span class="danmu__tag">{{ c.tag }}</span>
                    <h3 class="danmu__title">{{ c.title }}</h3>
                    <p class="danmu__desc">{{ c.desc }}</p>
                    <ul v-if="spans[i].includes('chips')" class="danmu__chips" aria-label="platforms">
                        <li v-for="p in t.danmu.platforms" :key="p" class="danmu__chip">{{ p }}</li>
                    </ul>
                </article>
            </div>
        </div>
    </section>
</template>

<style scoped>
.danmu {
    padding: clamp(72px, 11vw, 140px) 0;
    background: var(--bg);
}

.danmu__head {
    text-align: center;
}

.danmu__head .lede {
    margin: 16px auto 0;
}

.danmu__grid {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 16px;
    margin-top: clamp(40px, 5vw, 60px);
}

.danmu__card {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 26px;
    border-radius: var(--radius-lg);
    border: 1px solid var(--hairline);
    background: var(--surface);
    box-shadow: var(--shadow-sm);
    transition: box-shadow 0.35s var(--ease), border-color 0.35s var(--ease);
}

.danmu__card:hover {
    box-shadow: var(--shadow-md);
    border-color: rgba(0, 122, 255, 0.24);
}

.danmu__card--span3 {
    grid-column: span 3;
}

.danmu__card--span2 {
    grid-column: span 2;
}

.danmu__card--span6 {
    grid-column: span 6;
}

.danmu__tag {
    align-self: flex-start;
    padding: 5px 11px;
    border-radius: 8px;
    background: var(--brand-tint);
    color: var(--brand);
    font-size: 12.5px;
    font-weight: 650;
}

.danmu__title {
    font-size: 17px;
    font-weight: 650;
}

.danmu__desc {
    font-size: 14.5px;
    line-height: 1.68;
    color: var(--ink-muted);
}

.danmu__chips {
    list-style: none;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 6px;
}

.danmu__chip {
    padding: 4px 11px;
    border-radius: 999px;
    border: 1px solid var(--hairline);
    background: var(--field);
    font-size: 12.5px;
    color: var(--ink-soft);
}

@media (max-width: 980px) {
    .danmu__grid {
        grid-template-columns: repeat(4, 1fr);
    }

    .danmu__card--span3 {
        grid-column: span 4;
    }

    .danmu__card--span2 {
        grid-column: span 2;
    }

    .danmu__card--span6 {
        grid-column: span 4;
    }
}

@media (max-width: 620px) {
    .danmu__grid {
        grid-template-columns: 1fr;
    }

    .danmu__card--span3,
    .danmu__card--span2,
    .danmu__card--span6 {
        grid-column: span 1;
    }
}
</style>
