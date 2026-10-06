<script setup>
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()
</script>

<template>
    <section id="skip" class="skip">
        <div class="shell">
            <div class="section-head skip__head">
                <p v-reveal class="eyebrow">{{ t.skip.eyebrow }}</p>
                <h2 v-reveal="80" class="h2">
                    {{ t.skip.h2Line1 }}<br />{{ t.skip.h2Line2 }}
                </h2>
                <p v-reveal="160" class="lede">
                    {{ t.skip.lede }}
                </p>
            </div>

            <figure v-reveal="120" class="skip__figure">
                <img
                    src="/img/screenshot-intro-skip.jpg"
                    alt="智能跳过配置"
                    width="2000"
                    height="1124"
                    loading="lazy"
                />
                <figcaption class="skip__caption">{{ t.skip.caption }}</figcaption>
            </figure>

            <div class="skip__grid">
                <article
                    v-for="(c, i) in t.skip.items"
                    :key="c.title"
                    v-reveal="(i % 3) * 100"
                    class="skip__card"
                >
                    <span class="skip__tag">{{ c.tag }}</span>
                    <h3 class="skip__title">{{ c.title }}</h3>
                    <p class="skip__desc">{{ c.desc }}</p>
                </article>
            </div>
        </div>
    </section>
</template>

<style scoped>
.skip {
    padding: clamp(72px, 11vw, 140px) 0;
    background: var(--surface);
}

.skip__head {
    text-align: center;
}

.skip__head .lede {
    margin: 16px auto 0;
}

.skip__figure {
    margin: clamp(40px, 5vw, 60px) auto 0;
    max-width: 960px;
}

.skip__figure img {
    display: block;
    width: 100%;
    height: auto;
    border-radius: var(--radius-lg);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-md);
}

.skip__caption {
    margin-top: 12px;
    text-align: center;
    font-size: 13px;
    line-height: 1.6;
    color: var(--ink-muted);
}

.skip__grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    margin-top: clamp(36px, 4.5vw, 52px);
}

.skip__card {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 24px 26px;
    border-radius: var(--radius-md);
    border: 1px solid var(--hairline);
    background: var(--bg);
    transition: box-shadow 0.35s var(--ease), border-color 0.35s var(--ease);
}

.skip__card:hover {
    box-shadow: var(--shadow-md);
    border-color: rgba(0, 122, 255, 0.24);
}

.skip__tag {
    align-self: flex-start;
    padding: 5px 11px;
    border-radius: 8px;
    background: var(--brand-tint);
    color: var(--brand);
    font-size: 12.5px;
    font-weight: 650;
}

.skip__title {
    font-size: 16.5px;
    font-weight: 650;
}

.skip__desc {
    font-size: 14px;
    line-height: 1.68;
    color: var(--ink-muted);
}

@media (max-width: 900px) {
    .skip__grid {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (max-width: 560px) {
    .skip__grid {
        grid-template-columns: 1fr;
    }
}
</style>
