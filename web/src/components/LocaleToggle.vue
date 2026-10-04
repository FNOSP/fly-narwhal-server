<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from '../composables/useI18n'

const { locale, t, setLocale } = useI18n()

const open = ref(false)
const root = ref(null)

// Menu labels are endonyms — a language's own name, never translated, so a
// visitor who cannot yet read the UI can still find their language.
const options = [
    { code: 'zh-CN', label: '简体中文' },
    { code: 'zh-TW', label: '繁體中文' },
    { code: 'en', label: 'English' },
]

const label = computed(() => t.value.common.language)

function pick(code) {
    setLocale(code)
    open.value = false
}

function onDocumentPointerDown(event) {
    if (root.value && !root.value.contains(event.target)) open.value = false
}

function onKeydown(event) {
    if (event.key === 'Escape') open.value = false
}

onMounted(() => {
    document.addEventListener('pointerdown', onDocumentPointerDown)
    document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
    document.removeEventListener('pointerdown', onDocumentPointerDown)
    document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
    <div ref="root" class="locale-toggle">
        <button
            type="button"
            class="locale-toggle__trigger"
            :aria-label="label"
            :title="label"
            aria-haspopup="menu"
            :aria-expanded="open"
            @click="open = !open"
        >
            <!-- Globe: the icon is a language affordance, not a status; the
                 active language is shown by the check in the menu below. -->
            <svg class="locale-toggle__icon" viewBox="0 0 24 24" width="18" height="18" fill="none"
                stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="9" />
                <path d="M3 12h18" />
                <path d="M12 3c2.5 2.4 3.8 5.6 3.8 9s-1.3 6.6-3.8 9c-2.5-2.4-3.8-5.6-3.8-9S9.5 5.4 12 3z" />
            </svg>
        </button>

        <div v-if="open" class="locale-menu" role="menu" :aria-label="label">
            <button
                v-for="option in options"
                :key="option.code"
                type="button"
                role="menuitemradio"
                :aria-checked="locale === option.code"
                class="locale-menu__item"
                :class="{ 'is-active': locale === option.code }"
                @click="pick(option.code)"
            >
                <span class="locale-menu__label">{{ option.label }}</span>
                <svg v-if="locale === option.code" class="locale-menu__check" viewBox="0 0 24 24" width="15" height="15"
                    fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"
                    aria-hidden="true">
                    <path d="m5 12.5 4.5 4.5L19 7" />
                </svg>
            </button>
        </div>
    </div>
</template>

<style scoped>
.locale-toggle {
    position: relative;
    flex-shrink: 0;
}

.locale-toggle__trigger {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    color: var(--ink-soft);
    transition: background-color 0.25s var(--ease), color 0.25s var(--ease);
}

.locale-toggle__trigger:hover {
    background: var(--field);
    color: var(--brand);
}

.locale-toggle__icon {
    display: block;
}

.locale-menu {
    position: absolute;
    top: calc(100% + 8px);
    right: 0;
    z-index: 1;
    min-width: 132px;
    padding: 5px;
    border-radius: var(--radius-sm);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-md);
}

.locale-menu__item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    width: 100%;
    padding: 8px 10px;
    border-radius: 8px;
    font-size: 13.5px;
    font-weight: 500;
    color: var(--ink-soft);
    text-align: left;
    white-space: nowrap;
    transition: background-color 0.18s var(--ease), color 0.18s var(--ease);
}

.locale-menu__item:hover {
    background: var(--field);
    color: var(--ink);
}

.locale-menu__item.is-active {
    color: var(--brand);
}

.locale-menu__check {
    flex-shrink: 0;
}
</style>
