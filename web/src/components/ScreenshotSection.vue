<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useScrollProgress } from '../composables/useParallax'
import { useI18n } from '../composables/useI18n'

const { t } = useI18n()

// Sourced from the client repo's README on master (img/*.png), resized to 1800px
// wide and re-encoded as JPEG — the originals are 3600×2250 and ~7MB each.
// Paths are fixed; alt/title/desc come from the catalog by position.
const shotSrcs = [
    '/img/screenshot-login.jpg',
    '/img/screenshot-home.jpg',
    '/img/screenshot-player.jpg',
    '/img/screenshot-player-8k.jpg',
]

const shots = computed(() => t.value.screenshots.shots.map((s, i) => ({ ...s, src: shotSrcs[i] })))

const stage = ref(null)
const { progress } = useScrollProgress(stage, { start: 1, end: 0 })

// Window push-in: starts slightly small and settles to full size as the section
// centres in the viewport.
const stageScale = computed(() => 0.9 + progress.value * 0.1)
const stageLift = computed(() => `${(1 - progress.value) * 40}px`)
const backdrop = computed(() => progress.value)
const active = ref(0)

let frame = 0
function onScroll() {
    if (frame) return
    frame = requestAnimationFrame(() => {
        frame = 0
        const el = stage.value
        if (!el) return
        const rect = el.getBoundingClientRect()
        const center = rect.top + rect.height / 2
        const vh = window.innerHeight || 1
        const fromCenter = Math.abs(center - vh / 2)
        // Pick the shot whose center is nearest the viewport center.
        const count = shots.value.length
        const step = rect.height / count
        const idx = Math.min(count - 1, Math.max(0, Math.floor((vh / 2 - rect.top) / step)))
        active.value = fromCenter < rect.height ? idx : active.value
    })
}

onMounted(() => {
    window.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
})

onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll)
    if (frame) cancelAnimationFrame(frame)
})
</script>

<template>
    <section id="screenshots" ref="stage" class="shots">
        <div class="shots__aurora" :style="{ opacity: backdrop }"></div>

        <div class="shell shots__head">
            <p v-reveal class="eyebrow">{{ t.screenshots.eyebrow }}</p>
            <h2 v-reveal="80" class="h2">{{ t.screenshots.h2 }}</h2>
            <p v-reveal="160" class="lede">
                {{ t.screenshots.lede }}
            </p>
        </div>

        <div class="shell">
            <div
                v-reveal
                class="shots__stage"
                :style="{ transform: `scale(${stageScale}) translateY(${stageLift})` }"
            >
                <figure v-for="(shot, i) in shots" :key="shot.src" class="shot" :class="{ 'shot--on': active === i }">
                    <div class="shot__chrome">
                        <span class="shot__dot"></span>
                        <span class="shot__dot"></span>
                        <span class="shot__dot"></span>
                    </div>
                    <img
                        :src="shot.src"
                        :alt="shot.alt"
                        loading="lazy"
                        decoding="async"
                    />
                </figure>

                <div class="shots__caption">
                    <h3 class="shots__caption-title">{{ shots[active].title }}</h3>
                    <p class="shots__caption-desc">{{ shots[active].desc }}</p>
                </div>

                <div class="shots__dots" role="tablist" :aria-label="t.screenshots.switchAria">
                    <button
                        v-for="(shot, i) in shots"
                        :key="shot.title"
                        class="shots__dot"
                        :class="{ 'shots__dot--on': active === i }"
                        type="button"
                        role="tab"
                        :aria-selected="active === i"
                        :aria-label="shot.title"
                        @click="active = i"
                    ></button>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.shots {
    position: relative;
    padding: clamp(72px, 11vw, 140px) 0 clamp(60px, 8vw, 100px);
    overflow: hidden;
}

.shots__aurora {
    position: absolute;
    inset: 0;
    background: radial-gradient(900px 420px at 50% 0%, rgba(0, 122, 255, 0.1), transparent 70%);
    pointer-events: none;
}

.shots__head {
    position: relative;
    text-align: center;
    margin-bottom: clamp(36px, 5vw, 60px);
}

.shots__head .lede {
    margin: 16px auto 0;
}

.shots__stage {
    position: relative;
    display: grid;
    transition: transform 0.18s linear;
    will-change: transform;
    padding-bottom: 96px;
}

.shot {
    position: relative;
    grid-area: 1 / 1;
    border-radius: var(--radius-md);
    overflow: hidden;
    background: var(--surface);
    box-shadow: var(--shadow-lg);
    border: 1px solid var(--hairline);
    opacity: 0;
    transform: translateY(18px);
    transition: opacity 0.6s var(--ease), transform 0.6s var(--ease);
    pointer-events: none;
}

.shot--on {
    position: relative;
    opacity: 1;
    transform: none;
    pointer-events: auto;
}

.shot__chrome {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 11px 14px;
    background: var(--chrome-bar);
    border-bottom: 1px solid var(--hairline);
}

.shot__dot {
    width: 11px;
    height: 11px;
    border-radius: 50%;
    background: var(--dot);
}

.shot img {
    width: 100%;
    height: auto;
    display: block;
}

.shots__caption {
    position: absolute;
    inset: auto 0 4px;
    text-align: center;
    padding: 0 20px;
}

.shots__caption-title {
    font-size: 20px;
    font-weight: 600;
    margin-bottom: 6px;
}

.shots__caption-desc {
    font-size: 15px;
    color: var(--ink-muted);
    max-width: 560px;
    margin: 0 auto;
    line-height: 1.6;
}

.shots__dots {
    position: absolute;
    inset: auto 0 58px;
    display: flex;
    justify-content: center;
    gap: 8px;
}

.shots__dot {
    width: 8px;
    height: 8px;
    padding: 0;
    border-radius: 50%;
    background: var(--dot);
    transition: background-color 0.3s var(--ease), transform 0.3s var(--ease);
}

.shots__dot--on {
    background: var(--brand);
    transform: scale(1.25);
}

@media (max-width: 640px) {
    .shots__stage {
        padding-bottom: 132px;
    }

    .shot__chrome {
        padding: 8px 10px;
    }

    .shot__dot {
        width: 9px;
        height: 9px;
    }

    .shots__caption-title {
        font-size: 17px;
    }

    .shots__caption-desc {
        font-size: 13.5px;
    }
}
</style>
