/**
 * `v-reveal` — adds `is-revealed` once the element scrolls into view, which CSS
 * uses to transition the element into place.
 *
 * A directive is used rather than a template ref: refs bound to a function get
 * re-invoked on every re-render, and inside `v-for` they collapse into an array,
 * which makes registering/unregistering the observer error-prone. The directive
 * binds to the real DOM node once, on mount.
 *
 * Usage:
 *   <div v-reveal>…</div>
 *   <div v-reveal="120">…</div>          <!-- 120ms stagger delay -->
 *   <h2 v-reveal:mask>…</h2>             <!-- line-mask variant: children with
 *                                             .line-mask slide up line by line -->
 */
let observer = null

function ensureObserver() {
    if (observer) return observer
    observer = new IntersectionObserver(
        (entries) => {
            for (const entry of entries) {
                if (!entry.isIntersecting) continue
                entry.target.classList.add('is-revealed')
                // Reveal once; replaying on every pass reads as flicker.
                observer.unobserve(entry.target)
            }
        },
        { rootMargin: '0px 0px -10% 0px', threshold: 0.1 },
    )
    return observer
}

export const vReveal = {
    mounted(el, binding) {
        if (binding.value) el.style.setProperty('--delay', `${binding.value}ms`)
        el.classList.add('reveal')
        // `v-reveal:mask` keeps the container still and animates masked lines
        // inside it instead of fading the whole block.
        if (binding.arg === 'mask') el.classList.add('reveal--mask')
        ensureObserver().observe(el)
    },
    unmounted(el) {
        if (observer) observer.unobserve(el)
    },
}

export default vReveal
