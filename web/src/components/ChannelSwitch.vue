<script setup>
/**
 * Segmented 客户端 / 服务端 switch used by both the landing-page changelog
 * section and the timeline page. The parent owns the value (it comes from
 * useChangelog's shared, persisted channel state).
 */
defineProps({
    modelValue: { type: String, required: true },
})
const emit = defineEmits(['update:modelValue'])

const OPTIONS = [
    { value: 'client', label: '客户端' },
    { value: 'server', label: '服务端' },
]
</script>

<template>
    <div class="chsw" role="tablist" aria-label="更新日志来源">
        <span
            class="chsw__thumb"
            :class="{ 'chsw__thumb--right': modelValue === 'server' }"
            aria-hidden="true"
        ></span>
        <button
            v-for="o in OPTIONS"
            :key="o.value"
            type="button"
            role="tab"
            class="chsw__btn"
            :class="{ 'chsw__btn--on': modelValue === o.value }"
            :aria-selected="modelValue === o.value"
            @click="emit('update:modelValue', o.value)"
        >
            {{ o.label }}
        </button>
    </div>
</template>

<style scoped>
.chsw {
    position: relative;
    display: inline-flex;
    padding: 4px;
    border-radius: 999px;
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    isolation: isolate;
}

.chsw__thumb {
    position: absolute;
    z-index: -1;
    top: 4px;
    bottom: 4px;
    left: 4px;
    width: calc(50% - 4px);
    border-radius: 999px;
    background: linear-gradient(92deg, var(--brand), #5E5CE6);
    box-shadow: 0 2px 8px rgba(0, 122, 255, 0.3);
    transition: transform 0.35s var(--ease);
}

.chsw__thumb--right {
    transform: translateX(100%);
}

.chsw__btn {
    position: relative;
    padding: 8px 22px;
    border: 0;
    border-radius: 999px;
    background: transparent;
    font: inherit;
    font-size: 14px;
    font-weight: 600;
    letter-spacing: 0.02em;
    color: var(--ink-muted);
    cursor: pointer;
    transition: color 0.25s var(--ease);
    white-space: nowrap;
}

.chsw__btn:hover {
    color: var(--ink);
}

.chsw__btn--on,
.chsw__btn--on:hover {
    color: #fff;
}

.chsw__btn:focus-visible {
    outline: 2px solid var(--brand);
    outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
    .chsw__thumb {
        transition: none;
    }
}
</style>
