<script setup>
import { computed } from 'vue'
import { useTheme } from '../composables/useTheme'

const { isDark, toggleTheme } = useTheme()

const label = computed(() => (isDark.value ? '切换到浅色模式' : '切换到深色模式'))
</script>

<template>
    <!-- No global theme transition on swap: a temporary `* { transition }`
         would clobber the .reveal/.line-mask transition shorthands mid-scroll
         and break the entrance animations. The swap is instant, like apple.com. -->
    <button type="button" class="theme-toggle" :aria-label="label" :title="label" @click="toggleTheme">
        <!-- Moon: shown in light mode, clicking goes dark. -->
        <svg v-if="!isDark" class="theme-toggle__icon" viewBox="0 0 24 24" width="18" height="18" fill="none"
            stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
        </svg>
        <!-- Sun: shown in dark mode, clicking goes light. -->
        <svg v-else class="theme-toggle__icon" viewBox="0 0 24 24" width="18" height="18" fill="none"
            stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
            <circle cx="12" cy="12" r="4.5" />
            <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5 5l1.5 1.5M17.5 17.5L19 19M19 5l-1.5 1.5M6.5 17.5L5 19" />
        </svg>
    </button>
</template>

<style scoped>
.theme-toggle {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    border-radius: 50%;
    color: var(--ink-soft);
    transition: background-color 0.25s var(--ease), color 0.25s var(--ease);
}

.theme-toggle:hover {
    background: var(--field);
    color: var(--brand);
}

.theme-toggle__icon {
    display: block;
}
</style>
