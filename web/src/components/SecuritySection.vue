<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()

// Step numbers are language-neutral; titles/descs come from the catalog.
const stepNos = ['01', '02', '03', '04']

// Scramble demo: a readable password collapses into ciphertext once, when the
// panel scrolls into view. The demo strings are fixed tokens, not UI copy.
const plain = 'FeiNiu@2026'
const cipher = 'Kq3#vX8$mZr1!pL6&nW0'
const shown = ref(plain)
const demo = ref(null)
const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*?+=~'

let observer = null
let timer = 0

function runScramble() {
    const start = performance.now()
    const duration = 1500
    timer = window.setInterval(() => {
        const t = Math.min(1, (performance.now() - start) / duration)
        // Characters lock in from left to right; the tail keeps churning.
        const locked = Math.floor(t * cipher.length)
        let out = ''
        for (let i = 0; i < cipher.length; i++) {
            if (i < plain.length && t < 0.12) {
                out += plain[i]
            } else if (i < locked) {
                out += cipher[i]
            } else {
                out += GLYPHS[Math.floor(Math.random() * GLYPHS.length)]
            }
        }
        shown.value = out
        if (t >= 1) {
            shown.value = cipher
            clearInterval(timer)
            timer = 0
        }
    }, 45)
}

onMounted(() => {
    const el = demo.value
    if (!el) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        shown.value = cipher
        return
    }
    observer = new IntersectionObserver(
        (entries) => {
            if (!entries.some((e) => e.isIntersecting)) return
            observer.disconnect()
            observer = null
            runScramble()
        },
        { threshold: 0.5 },
    )
    observer.observe(el)
})

onBeforeUnmount(() => {
    if (observer) observer.disconnect()
    if (timer) clearInterval(timer)
})
</script>

<template>
    <section id="security" class="sec">
        <div class="sec__aurora" aria-hidden="true"></div>

        <div class="shell">
            <div class="section-head sec__head">
                <p v-reveal class="eyebrow">{{ t.security.eyebrow }}</p>
                <h2 v-reveal:mask class="h2">
                    <span class="line-mask"><span style="--line-delay: 0ms">{{ t.security.titleLine1 }}</span></span>
                    <span class="line-mask"><span style="--line-delay: 110ms">{{ t.security.titleLine2 }}</span></span>
                </h2>
                <p v-reveal="160" class="lede">
                    {{ t.security.lede }}
                </p>
            </div>

            <div class="sec__layout">
                <div ref="demo" class="sec__panel">
                    <div class="vault">
                        <div class="vault__row">
                            <span class="vault__label">{{ t.security.vaultLabel }}</span>
                            <span class="vault__lock" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                    <rect x="5" y="10" width="14" height="10" rx="2.4" />
                                    <path d="M8 10V7.5a4 4 0 0 1 8 0V10" />
                                    <path d="M12 14v2.5" />
                                </svg>
                                {{ t.security.encrypted }}
                            </span>
                        </div>
                        <code class="vault__value">{{ shown }}</code>
                        <div class="vault__meta">
                            <span>AES-256-GCM</span>
                            <span>HKDF-SHA256</span>
                            <span>{{ t.security.saltMeta }}</span>
                        </div>
                    </div>

                    <ul class="sec__guards">
                        <li v-for="(g, i) in t.security.guards" :key="g" v-reveal="i * 80" class="guard">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M4.5 12.5l5 5 10-11" />
                            </svg>
                            {{ g }}
                        </li>
                    </ul>
                </div>

                <ol class="sec__steps">
                    <li v-for="(s, i) in t.security.steps" :key="stepNos[i]" v-reveal="i * 110" class="step">
                        <span class="step__k">{{ stepNos[i] }}</span>
                        <div class="step__body">
                            <h3 class="step__title">{{ s.title }}</h3>
                            <p class="step__desc">{{ s.desc }}</p>
                        </div>
                    </li>
                </ol>
            </div>
        </div>
    </section>
</template>

<style scoped>
.sec {
    position: relative;
    padding: clamp(72px, 11vw, 140px) 0;
    /* Eases out of the grey bento section above and ends on the solid section
       surface so the seam into the ServerSection below disappears. */
    background: linear-gradient(180deg, var(--bg) 0%, var(--surface) 20%);
    overflow: hidden;
}

.sec__aurora {
    position: absolute;
    top: -20%;
    right: -10%;
    width: min(760px, 70vw);
    aspect-ratio: 1.4 / 1;
    background: radial-gradient(
        circle at 50% 50%,
        rgba(94, 92, 230, 0.12) 0%,
        rgba(0, 122, 255, 0.07) 45%,
        transparent 70%
    );
    filter: blur(20px);
    pointer-events: none;
}

.sec__head {
    position: relative;
    text-align: center;
}

.sec__head .lede {
    margin: 16px auto 0;
}

.sec__layout {
    position: relative;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: clamp(28px, 4vw, 56px);
    align-items: start;
}

/* ---------- left panel ---------- */

.sec__panel {
    position: sticky;
    top: calc(var(--nav-h) + 40px);
}

.vault {
    padding: 24px 26px 22px;
    border-radius: var(--radius-lg);
    background: var(--glass);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-md);
    backdrop-filter: blur(20px) saturate(160%);
    -webkit-backdrop-filter: blur(20px) saturate(160%);
}

.vault__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 16px;
}

.vault__label {
    font-size: 13.5px;
    font-weight: 600;
    color: var(--ink-soft);
}

.vault__lock {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 4px 11px;
    border-radius: 999px;
    background: var(--green-tint);
    color: var(--green);
    font-size: 12px;
    font-weight: 650;
}

.vault__lock svg {
    width: 13px;
    height: 13px;
}

.vault__value {
    display: block;
    padding: 14px 16px;
    border-radius: var(--radius-sm);
    background: #101018;
    color: #7DD37D;
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    font-size: clamp(13px, 1.5vw, 16px);
    letter-spacing: 0.04em;
    word-break: break-all;
    min-height: 52px;
}

.vault__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 14px;
}

.vault__meta span {
    padding: 4px 10px;
    border-radius: 7px;
    background: var(--field);
    font-size: 11.5px;
    font-weight: 600;
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    color: var(--ink-muted);
}

.sec__guards {
    list-style: none;
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 20px;
}

.guard {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 8px 14px;
    border-radius: 999px;
    background: var(--surface);
    border: 1px solid var(--hairline);
    font-size: 13px;
    font-weight: 550;
    color: var(--ink-soft);
}

.guard svg {
    width: 14px;
    height: 14px;
    color: #34C759;
}

/* ---------- right steps ---------- */

.sec__steps {
    list-style: none;
    display: grid;
    gap: 14px;
    counter-reset: step;
}

.step {
    display: flex;
    gap: 18px;
    padding: 24px 26px;
    border-radius: var(--radius-md);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    transition: border-color 0.35s var(--ease), box-shadow 0.35s var(--ease);
}

.step:hover {
    border-color: rgba(0, 122, 255, 0.3);
    box-shadow: var(--shadow-md);
}

.step__k {
    flex-shrink: 0;
    font-size: 15px;
    font-weight: 800;
    font-variant-numeric: tabular-nums;
    color: var(--brand);
    opacity: 0.75;
    padding-top: 1px;
}

.step__title {
    font-size: 16.5px;
    font-weight: 650;
    margin-bottom: 7px;
}

.step__desc {
    font-size: 14px;
    line-height: 1.7;
    color: var(--ink-muted);
}

@media (max-width: 900px) {
    .sec__layout {
        grid-template-columns: 1fr;
    }

    .sec__panel {
        position: static;
    }
}
</style>
