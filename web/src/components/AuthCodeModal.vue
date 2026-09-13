<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
    open: { type: Boolean, default: false },
    title: { type: String, default: '授权码' },
    desc: { type: String, default: '' },
    code: { type: String, default: '' },
    copied: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'copy'])

const input = ref(null)

function onKeydown(e) {
    if (e.key === 'Escape' && props.open) emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

// Focus the code so it can be selected manually if the clipboard API is blocked.
watch(
    () => props.open,
    (isOpen) => {
        if (!isOpen) return
        requestAnimationFrame(() => {
            if (input.value) input.value.select()
        })
    },
)
</script>

<template>
    <Teleport to="body">
        <Transition name="modal">
            <div v-if="open" class="overlay" @click.self="emit('close')">
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="auth-title">
                    <header class="modal__head">
                        <h2 id="auth-title" class="modal__title">{{ title }}</h2>
                        <button class="modal__close" type="button" aria-label="关闭" @click="emit('close')">
                            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" />
                            </svg>
                        </button>
                    </header>

                    <div class="modal__body">
                        <p class="modal__desc">{{ desc }}</p>

                        <div v-if="code" class="code-row">
                            <input id="auth-code-input" ref="input" class="code-row__input" type="text" readonly :value="code" @focus="$event.target.select()" />
                            <button class="code-row__copy" type="button" @click="emit('copy')">
                                {{ copied ? '已复制' : '复制' }}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>

<style scoped>
.overlay {
    position: fixed;
    inset: 0;
    z-index: 9999;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
    background: rgba(0, 0, 0, 0.42);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
}

.modal {
    width: 100%;
    max-width: 560px;
    background: var(--surface);
    border-radius: var(--radius-md);
    box-shadow: 0 24px 70px rgba(0, 0, 0, 0.28);
    overflow: hidden;
}

.modal__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 18px 20px 0;
}

.modal__title {
    font-size: 16.5px;
    font-weight: 650;
}

.modal__close {
    display: grid;
    place-items: center;
    padding: 6px;
    border-radius: 10px;
    color: var(--ink-muted);
    transition: background-color 0.2s var(--ease), color 0.2s var(--ease);
}

.modal__close:hover {
    background: rgba(0, 0, 0, 0.05);
    color: var(--ink);
}

.modal__body {
    padding: 14px 20px 22px;
}

.modal__desc {
    font-size: 13.5px;
    line-height: 1.7;
    color: var(--ink-muted);
    margin-bottom: 14px;
}

.code-row {
    display: flex;
    align-items: center;
    gap: 10px;
}

.code-row__input {
    flex: 1;
    min-width: 0;
    padding: 12px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--hairline);
    background: rgba(245, 245, 247, 0.8);
    color: var(--ink);
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    font-size: 13px;
    outline: none;
}

.code-row__input:focus {
    border-color: var(--brand);
}

.code-row__copy {
    flex-shrink: 0;
    padding: 12px 18px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--hairline);
    background: #fff;
    font-size: 13px;
    font-weight: 600;
    transition: transform 0.2s var(--ease), box-shadow 0.2s var(--ease);
}

.code-row__copy:hover {
    transform: translateY(-1px);
    box-shadow: var(--shadow-md);
}

.modal-enter-active,
.modal-leave-active {
    transition: opacity 0.25s var(--ease);
}

.modal-enter-active .modal,
.modal-leave-active .modal {
    transition: transform 0.3s var(--ease);
}

.modal-enter-from,
.modal-leave-to {
    opacity: 0;
}

.modal-enter-from .modal,
.modal-leave-to .modal {
    transform: translateY(14px) scale(0.97);
}
</style>
