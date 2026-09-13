import { onBeforeUnmount, onMounted, ref } from 'vue'

const prefersReducedMotion = () =>
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * Tracks how far the page has scrolled within a given element, as a 0..1
 * progress value. A single rAF-throttled scroll listener is shared by every
 * caller so adding sections does not add listeners.
 */
const subscribers = new Set()
let listening = false
let frame = 0

function flush() {
    frame = 0
    for (const fn of subscribers) fn()
}

function schedule() {
    if (frame) return
    frame = window.requestAnimationFrame(flush)
}

function subscribe(fn) {
    subscribers.add(fn)
    if (!listening) {
        window.addEventListener('scroll', schedule, { passive: true })
        window.addEventListener('resize', schedule, { passive: true })
        listening = true
    }
}

function unsubscribe(fn) {
    subscribers.delete(fn)
    if (subscribers.size === 0 && listening) {
        window.removeEventListener('scroll', schedule)
        window.removeEventListener('resize', schedule)
        listening = false
        if (frame) {
            window.cancelAnimationFrame(frame)
            frame = 0
        }
    }
}

/**
 * @param {import('vue').Ref<HTMLElement|null>} targetRef
 * @param {{ start?: number, end?: number }} options
 *        `start` maps to progress 0 and `end` to progress 1. Both are offsets in
 *        viewport heights, measured between the viewport top and the element's
 *        top edge. `start` must be greater than `end`: at `start` the element is
 *        still below the fold, at `end` it has moved that far above it.
 * @returns {{ progress: import('vue').Ref<number> }}
 */
export function useScrollProgress(targetRef, options = {}) {
    // Defaults describe a section you scroll *through*: 0 while its top is still
    // a full viewport below, 1 once its top reaches the viewport top. Callers
    // that want a shorter window pass tighter numbers.
    const { start = 1, end = 0 } = options
    const progress = ref(0)

    const measure = () => {
        const el = targetRef.value
        if (!el) return
        const rect = el.getBoundingClientRect()
        const vh = window.innerHeight || 1
        const span = (start - end) * vh
        // The element's top edge relative to the viewport top is `rect.top`;
        // progress runs from `start * vh` (element still below the fold) down to
        // `end * vh` (element scrolled above it).
        const raw = span === 0 ? 1 : (start * vh - rect.top) / span
        progress.value = Math.min(1, Math.max(0, raw))
    }

    onMounted(() => {
        // Measure once regardless of motion preference: callers that skip the
        // effect still read `progress` for a static resting value.
        measure()
        if (prefersReducedMotion()) return
        subscribe(measure)
    })

    onBeforeUnmount(() => unsubscribe(measure))

    return { progress }
}

/**
 * Vertical parallax offset in pixels, derived from a scroll progress value.
 * Positive `distance` moves the element against the scroll direction.
 */
export function useParallax(targetRef, { distance = 80, ...options } = {}) {
    const { progress } = useScrollProgress(targetRef, options)
    return { progress, offset: (p) => (p - 0.5) * distance * -2 }
}
