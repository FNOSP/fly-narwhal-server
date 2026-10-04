<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()

// Icons and the numeric stat values are structural; the text comes from `t`.
const featureIcons = ['layers', 'play', 'subtitle', 'skip', 'danmu', 'devices']
const statValues = [3, 7, 2]

const root = ref(null)

// Counting numbers, started once the stat row scrolls into view.
const shown = ref(statValues.map(() => 0))
let counterObserver = null
let raf = 0

function runCounters() {
    const start = performance.now()
    const duration = 1400
    const tick = (now) => {
        const tn = Math.min(1, (now - start) / duration)
        // easeOutExpo keeps the last digits from crawling.
        const eased = tn === 1 ? 1 : 1 - Math.pow(2, -10 * tn)
        shown.value = statValues.map((v) => Math.round(v * eased))
        if (tn < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
}

onMounted(() => {
    const el = root.value
    if (!el) return
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) {
        shown.value = statValues.slice()
        return
    }
    counterObserver = new IntersectionObserver(
        (entries) => {
            if (!entries.some((e) => e.isIntersecting)) return
            counterObserver.disconnect()
            counterObserver = null
            runCounters()
        },
        { threshold: 0.4 },
    )
    counterObserver.observe(el)
})

onBeforeUnmount(() => {
    if (counterObserver) counterObserver.disconnect()
    if (raf) cancelAnimationFrame(raf)
})

// Pointer-tracked tilt. Kept shallow so text stays crisp on the tilted face.
function tilt(e) {
    const card = e.currentTarget
    const rect = card.getBoundingClientRect()
    const px = (e.clientX - rect.left) / rect.width - 0.5
    const py = (e.clientY - rect.top) / rect.height - 0.5
    card.style.setProperty('--rx', `${(-py * 6).toFixed(2)}deg`)
    card.style.setProperty('--ry', `${(px * 6).toFixed(2)}deg`)
}

function untilt(e) {
    const card = e.currentTarget
    card.style.setProperty('--rx', '0deg')
    card.style.setProperty('--ry', '0deg')
}
</script>

<template>
    <section id="features" class="features">
        <div class="shell">
            <div class="section-head features__head">
                <p v-reveal class="eyebrow">{{ t.features.eyebrow }}</p>
                <h2 v-reveal="80" class="h2">
                    {{ t.features.h2Line1 }}<br />{{ t.features.h2Line2 }}
                </h2>
                <p v-reveal="160" class="lede">
                    {{ t.features.lede }}
                </p>
            </div>

            <div class="features__grid">
                <article
                    v-for="(f, i) in t.features.items"
                    :key="f.title"
                    v-reveal="(i % 3) * 90"
                    class="card"
                    @mousemove="tilt"
                    @mouseleave="untilt"
                >
                    <div class="card__glyph">
                        <svg v-if="featureIcons[i] === 'layers'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M12 3l9 5-9 5-9-5 9-5z" /><path d="M3 13l9 5 9-5" />
                        </svg>
                        <svg v-else-if="featureIcons[i] === 'play'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="9" /><path d="M10 8.5l6 3.5-6 3.5z" />
                        </svg>
                        <svg v-else-if="featureIcons[i] === 'subtitle'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                            <rect x="3" y="5" width="18" height="14" rx="3" /><path d="M7 14h4M14 14h3" />
                        </svg>
                        <svg v-else-if="featureIcons[i] === 'skip'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M5 5l8 7-8 7z" /><path d="M19 5v14" />
                        </svg>
                        <svg v-else-if="featureIcons[i] === 'danmu'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                            <rect x="3" y="6" width="18" height="12" rx="3" /><path d="M7 10h7M7 14h4" />
                        </svg>
                        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                            <rect x="2" y="4" width="14" height="11" rx="2" /><path d="M6 19h9" /><rect x="17" y="9" width="5" height="11" rx="1.6" />
                        </svg>
                    </div>
                    <h3 class="card__title">{{ f.title }}</h3>
                    <p class="card__desc">{{ f.desc }}</p>
                </article>
            </div>

            <div ref="root" class="stats">
                <div v-for="(s, i) in t.features.stats" :key="s.label" class="stats__item">
                    <div class="stats__value">{{ shown[i] }}</div>
                    <div class="stats__label">{{ s.label }}</div>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.features {
    position: relative;
    padding: clamp(72px, 11vw, 140px) 0;
    background: linear-gradient(180deg, var(--bg) 0%, var(--surface) 100%);
}

.features__head {
    text-align: center;
}

.features__head .lede {
    margin: 16px auto 0;
}

.features__grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 22px;
}

.card {
    position: relative;
    padding: 30px 28px 32px;
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    transform-style: preserve-3d;
    transition: transform 0.4s var(--ease), box-shadow 0.4s var(--ease);
    /* Overrides the reveal translate so tilt and reveal do not fight. */
    --rx: 0deg;
    --ry: 0deg;
}

.card.is-revealed {
    transform: perspective(900px) rotateX(var(--rx)) rotateY(var(--ry));
}

.card:hover {
    box-shadow: var(--shadow-md);
}

.card__glyph {
    width: 44px;
    height: 44px;
    display: grid;
    place-items: center;
    border-radius: 13px;
    background: var(--brand-tint);
    color: var(--brand);
    margin-bottom: 20px;
}

.card__glyph svg {
    width: 23px;
    height: 23px;
}

.card__title {
    font-size: 18px;
    font-weight: 650;
    letter-spacing: -0.01em;
    margin-bottom: 10px;
}

.card__desc {
    font-size: 14.5px;
    line-height: 1.68;
    color: var(--ink-muted);
}

.stats {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 28px;
    margin-top: clamp(48px, 6vw, 76px);
    padding-top: clamp(36px, 4.5vw, 52px);
    border-top: 1px solid var(--hairline);
    text-align: center;
}

.stats__value {
    font-size: clamp(34px, 4.4vw, 50px);
    font-weight: 700;
    letter-spacing: -0.03em;
    color: var(--brand);
    line-height: 1;
    font-variant-numeric: tabular-nums;
}

.stats__label {
    margin-top: 10px;
    font-size: 14px;
    color: var(--ink-muted);
}

@media (max-width: 640px) {
    .card {
        padding: 26px 22px 28px;
    }
}

/* Tilt is decoration; drop it when the platform disables motion. */
@media (prefers-reduced-motion: reduce) {
    .card.is-revealed {
        transform: none;
    }
}
</style>
