<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useTheme } from '../composables/useTheme'

const { preference, isDark, setThemeMode } = useTheme()

const open = ref(false)
const root = ref(null)

// 'system' shows its resolved theme in the trigger icon; the menu still marks
// the actual preference so the visitor can see they're on "follow system".
const options = [
    { mode: 'system', label: '跟随系统' },
    { mode: 'light', label: '浅色' },
    { mode: 'dark', label: '深色' },
]

// The trigger is a status indicator, not a one-click flip: the icon names the
// theme currently painted, and the menu below is where a choice gets made. So
// the moon means "you are in dark mode", not "click for dark".
const currentLabel = computed(() => (isDark.value ? '深色' : '浅色'))
const label = computed(() =>
    preference.value === 'system' ? `主题：跟随系统（当前${currentLabel.value}）` : `主题：${currentLabel.value}`
)

function pick(mode) {
    setThemeMode(mode)
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
    <!-- No global theme transition on swap: a temporary `* { transition }`
         would clobber the .reveal/.line-mask transition shorthands mid-scroll
         and break the entrance animations. The swap is instant, like apple.com. -->
    <div ref="root" class="theme-toggle">
        <button
            type="button"
            class="theme-toggle__trigger"
            :aria-label="label"
            :title="label"
            aria-haspopup="menu"
            :aria-expanded="open"
            @click="open = !open"
        >
            <!-- Moon: the page is dark, so the icon reads as "you're in dark mode". -->
            <svg v-if="isDark" class="theme-toggle__icon" viewBox="0 0 24 24" width="18" height="18" fill="none"
                stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
            </svg>
            <!-- Sun: the page is light. -->
            <svg v-else class="theme-toggle__icon" viewBox="0 0 24 24" width="18" height="18" fill="none"
                stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
                <circle cx="12" cy="12" r="4.5" />
                <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5 5l1.5 1.5M17.5 17.5L19 19M19 5l-1.5 1.5M6.5 17.5L5 19" />
            </svg>
        </button>

        <div v-if="open" class="theme-menu" role="menu" aria-label="主题">
            <button
                v-for="option in options"
                :key="option.mode"
                type="button"
                role="menuitemradio"
                :aria-checked="preference === option.mode"
                class="theme-menu__item"
                :class="{ 'is-active': preference === option.mode }"
                @click="pick(option.mode)"
            >
                <span class="theme-menu__label">{{ option.label }}</span>
                <svg v-if="preference === option.mode" class="theme-menu__check" viewBox="0 0 24 24" width="15" height="15"
                    fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"
                    aria-hidden="true">
                    <path d="m5 12.5 4.5 4.5L19 7" />
                </svg>
            </button>
        </div>
    </div>
</template>

<style scoped>
.theme-toggle {
    position: relative;
    flex-shrink: 0;
}

.theme-toggle__trigger {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    color: var(--ink-soft);
    transition: background-color 0.25s var(--ease), color 0.25s var(--ease);
}

.theme-toggle__trigger:hover {
    background: var(--field);
    color: var(--brand);
}

.theme-toggle__icon {
    display: block;
}

.theme-menu {
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

.theme-menu__item {
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
    transition: background-color 0.18s var(--ease), color 0.18s var(--ease);
}

.theme-menu__item:hover {
    background: var(--field);
    color: var(--ink);
}

.theme-menu__item.is-active {
    color: var(--brand);
}

.theme-menu__check {
    flex-shrink: 0;
}
</style>