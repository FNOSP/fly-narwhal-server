<script setup>
import { useI18n } from '../composables/useI18n'
import trickplayShot from '../assets/img/screenshot-trickplay.webp'

const { t } = useI18n()
</script>

<template>
    <section id="thumbnails" class="thumb">
        <div class="shell">
            <div class="section-head thumb__head">
                <p v-reveal class="eyebrow">{{ t.thumbnails.eyebrow }}</p>
                <h2 v-reveal:mask="80" class="h2">
                    <span class="line-mask"><span style="--line-delay: 0ms">{{ t.thumbnails.h2Line1 }}</span></span>
                    <span class="line-mask"><span style="--line-delay: 110ms">{{ t.thumbnails.h2Line2 }}</span></span>
                </h2>
                <p v-reveal="160" class="lede thumb__lede">{{ t.thumbnails.lede }}</p>
            </div>

            <div class="thumb__grid">
                <figure v-reveal class="thumb__shot">
                    <img
                        :src="trickplayShot"
                        :alt="t.thumbnails.alt"
                        width="2000"
                        height="838"
                        loading="lazy"
                        decoding="async"
                    />
                    <figcaption class="thumb__caption">{{ t.thumbnails.caption }}</figcaption>
                </figure>

                <article v-reveal="80" class="thumb__cell thumb__cell--gen">
                    <span class="thumb__tag">{{ t.thumbnails.items[0].tag }}</span>
                    <h3 class="thumb__title">{{ t.thumbnails.items[0].title }}</h3>
                    <p class="thumb__desc">{{ t.thumbnails.items[0].desc }}</p>
                </article>
                <article v-reveal="160" class="thumb__cell">
                    <span class="thumb__tag">{{ t.thumbnails.items[1].tag }}</span>
                    <h3 class="thumb__title">{{ t.thumbnails.items[1].title }}</h3>
                    <p class="thumb__desc">{{ t.thumbnails.items[1].desc }}</p>
                </article>
                <article v-reveal="240" class="thumb__cell">
                    <span class="thumb__tag">{{ t.thumbnails.items[2].tag }}</span>
                    <h3 class="thumb__title">{{ t.thumbnails.items[2].title }}</h3>
                    <p class="thumb__desc">{{ t.thumbnails.items[2].desc }}</p>
                </article>
                <article v-reveal="320" class="thumb__cell">
                    <span class="thumb__tag">{{ t.thumbnails.items[3].tag }}</span>
                    <h3 class="thumb__title">{{ t.thumbnails.items[3].title }}</h3>
                    <p class="thumb__desc">{{ t.thumbnails.items[3].desc }}</p>
                </article>
            </div>
        </div>
    </section>
</template>

<style scoped>
.thumb {
    padding: clamp(72px, 11vw, 140px) 0;
    background: var(--bg);
}

.thumb__head {
    text-align: center;
}

.thumb__lede {
    margin: 16px auto 0;
}

/* Bento: the screenshot spans the full row, then a wide generation card
   beside preview, with performance and smart sharing the row below. */
.thumb__grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
}

.thumb__shot {
    grid-column: span 2;
    position: relative;
}

.thumb__shot img {
    display: block;
    width: 100%;
    height: auto;
    border-radius: var(--radius-lg);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-md);
}

.thumb__caption {
    margin-top: 12px;
    text-align: center;
    font-size: 13px;
    line-height: 1.6;
    color: var(--ink-muted);
}

.thumb__cell {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 24px 26px;
    border-radius: var(--radius-md);
    border: 1px solid var(--hairline);
    background: var(--surface);
    transition: box-shadow 0.35s var(--ease), border-color 0.35s var(--ease);
}

.thumb__cell:hover {
    box-shadow: var(--shadow-md);
    border-color: rgba(0, 122, 255, 0.24);
}

.thumb__cell--gen {
    background: var(--grad-panel);
}

.thumb__tag {
    align-self: flex-start;
    padding: 5px 11px;
    border-radius: 8px;
    background: var(--brand-tint);
    color: var(--brand);
    font-size: 12.5px;
    font-weight: 650;
}

.thumb__title {
    font-size: 16.5px;
    font-weight: 650;
}

.thumb__desc {
    font-size: 14px;
    line-height: 1.68;
    color: var(--ink-muted);
}

@media (max-width: 900px) {
    .thumb__grid {
        grid-template-columns: 1fr;
    }

    .thumb__shot {
        grid-column: span 1;
    }
}
</style>