<script setup>
import { ref } from 'vue'
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()

const open = ref(0)

function toggle(i) {
    open.value = open.value === i ? -1 : i
}
</script>

<template>
    <section id="faq" class="faq">
        <div class="shell faq__shell">
            <div class="section-head faq__head">
                <p v-reveal class="eyebrow">{{ t.faq.eyebrow }}</p>
                <h2 v-reveal="80" class="h2">{{ t.faq.h2 }}</h2>
            </div>

            <div v-reveal="140" class="faq__list">
                <div
                    v-for="(f, i) in t.faq.items"
                    :key="f.q"
                    class="faq__item"
                    :class="{ 'faq__item--open': open === i }"
                >
                    <button
                        class="faq__q"
                        type="button"
                        :aria-expanded="open === i"
                        @click="toggle(i)"
                    >
                        <span>{{ f.q }}</span>
                        <svg class="faq__icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                            <path d="M12 5v14" />
                            <path d="M5 12h14" class="faq__icon-bar" />
                        </svg>
                    </button>
                    <div class="faq__panel">
                        <div class="faq__panel-inner">
                            <p class="faq__a">{{ f.a }}</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.faq {
    padding: clamp(72px, 11vw, 140px) 0;
    background: var(--bg);
}

.faq__shell {
    max-width: 860px;
}

.faq__head {
    text-align: center;
    margin-bottom: clamp(32px, 4vw, 48px);
}

.faq__list {
    display: grid;
    gap: 12px;
}

.faq__item {
    border-radius: var(--radius-md);
    background: var(--surface);
    border: 1px solid var(--hairline);
    overflow: hidden;
    transition: border-color 0.3s var(--ease), box-shadow 0.3s var(--ease);
}

.faq__item--open {
    border-color: rgba(0, 122, 255, 0.32);
    box-shadow: var(--shadow-md);
}

.faq__q {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    width: 100%;
    padding: 20px 24px;
    text-align: left;
    font-size: 16px;
    font-weight: 620;
    color: var(--ink);
}

.faq__icon {
    flex-shrink: 0;
    color: var(--brand);
    transition: transform 0.35s var(--ease);
}

.faq__item--open .faq__icon {
    transform: rotate(90deg);
}

.faq__icon-bar {
    transition: opacity 0.3s var(--ease);
}

.faq__item--open .faq__icon-bar {
    opacity: 0;
}

/* 0fr → 1fr gives a smooth height transition without measuring content. */
.faq__panel {
    display: grid;
    grid-template-rows: 0fr;
    transition: grid-template-rows 0.42s var(--ease);
}

.faq__item--open .faq__panel {
    grid-template-rows: 1fr;
}

.faq__panel-inner {
    overflow: hidden;
}

.faq__a {
    padding: 0 24px 22px;
    font-size: 14.5px;
    line-height: 1.78;
    color: var(--ink-muted);
}

@media (max-width: 560px) {
    .faq__q {
        padding: 17px 18px;
        font-size: 15px;
    }

    .faq__a {
        padding: 0 18px 18px;
    }
}
</style>
