<script setup>
import { computed, ref } from 'vue'
import { PLATFORM_LOGOS as LOGOS } from '../assets/platforms'
import { useI18n, format } from '../composables/useI18n'

const props = defineProps({
    platformGroups: { type: Object, default: () => ({}) },
    loading: { type: Boolean, default: true },
    publishedLabel: { type: String, default: '' },
    releaseUrl: { type: String, default: '' },
    tag: { type: String, default: '' },
    osRules: { type: Array, required: true },
})

const { t } = useI18n()

// Per-OS note shown under the console; resolved from the catalog live.
const osNote = computed(() => t.value.download.notes[displayOs.value])
const headTitle = computed(() =>
    props.tag ? format(t.value.download.h2WithTag, { tag: props.tag }) : t.value.download.h2,
)

// Best-effort guess of the visitor's platform + architecture so the console
// opens on their own download. Detection is only a default — the tabs and
// arch toggle let anyone override it.
function detectOs() {
    const p = (navigator.platform || '').toLowerCase()
    const ua = (navigator.userAgent || '').toLowerCase()
    if (/mac|iphone|ipad|ipod/.test(p) || ua.includes('mac os x')) return 'macos'
    if (ua.includes('windows') || p.startsWith('win')) return 'windows'
    if (ua.includes('linux') || p.includes('linux')) return 'linux'
    return ''
}

function detectArch() {
    // Apple Silicon is the hard case: browsers report navigator.platform as
    // "MacIntel" and leave the architecture out of the UA string, so on an M-series
    // Mac a UA check alone wrongly returns Intel. The GPU gives it away — Apple's
    // own silicon exposes an "Apple <Mx>"/"Apple GPU" renderer, whereas Intel Macs
    // report Intel / AMD / NVIDIA (or the legacy "Apple Intel …" iGPU, which we
    // exclude so it still reads as Intel).
    try {
        const canvas = document.createElement('canvas')
        const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl')
        if (gl) {
            const dbg = gl.getExtension('WEBGL_debug_renderer_info')
            const renderer = String(
                dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER) || '',
            )
            if (/apple/i.test(renderer) && !/intel/i.test(renderer)) return 'aarch64'
        }
    } catch {
        /* no WebGL available — fall through to the UA check */
    }
    return /arm|aarch64/i.test(navigator.userAgent || '') ? 'aarch64' : 'amd64'
}

const guessed = detectOs()
const guessedArch = guessed ? detectArch() : ''
const activeOs = ref(guessed || 'windows')
const archChoice = ref(guessed ? { [guessed]: guessedArch } : {})
const noteOpen = ref(false)

// The visitor's actual platform + architecture, captured once at load.
// “推荐” only makes sense for the exact package that matches their machine —
// same platform AND same chip — so the tag/highlight is gated on both, not on
// whichever tab or arch toggle is currently selected.
const visitorOs = ref(guessed)
const visitorArch = ref(guessedArch)

// The shown platform is the selected one when it actually has packages;
// otherwise (still loading, or the detected OS has no assets) fall back to the
// first platform that does, so the console never opens on an empty panel.
const displayOs = computed(() => {
    const groups = props.platformGroups
    if (groups[activeOs.value]?.archs?.length) return activeOs.value
    const first = props.osRules.find((r) => groups[r.os]?.archs?.length)
    return first ? first.os : activeOs.value
})

const platform = computed(() => props.platformGroups[displayOs.value])
const archList = computed(() => platform.value?.archs ?? [])
const activeArch = computed(() => {
    const list = archList.value
    if (!list.length) return ''
    const chosen = archChoice.value[displayOs.value]
    return list.some((a) => a.key === chosen) ? chosen : list[0].key
})
const rows = computed(() => platform.value?.byArch[activeArch.value] ?? [])

// Recommendation (tag + brand highlight) only appears on the row that matches
// the visitor's own platform AND architecture; every other row renders neutral.
const isOwnPlatform = computed(
    () =>
        !!visitorOs.value &&
        displayOs.value === visitorOs.value &&
        activeArch.value === visitorArch.value,
)

function selectOs(os) {
    activeOs.value = os
    noteOpen.value = false
}

function selectArch(key) {
    archChoice.value = { ...archChoice.value, [displayOs.value]: key }
}

function toggleNote() {
    noteOpen.value = !noteOpen.value
}
</script>

<template>
    <section id="download" class="dl">
        <div class="shell">
            <div class="section-head dl__head">
                <p v-reveal class="eyebrow">{{ t.download.eyebrow }}</p>
                <h2 v-reveal="80" class="h2">
                    {{ headTitle }}
                </h2>
                <p v-reveal="160" class="lede">
                    {{ t.download.lede }}
                </p>
            </div>

            <div v-reveal="120" class="console">
                <!-- platform tabs -->
                <div class="console__tabs">
                    <div class="seg seg--platform" role="tablist" :aria-label="t.download.tabsAria">
                        <button
                            v-for="rule in osRules"
                            :key="rule.os"
                            type="button"
                            role="tab"
                            class="seg__btn"
                            :class="{ 'is-on': displayOs === rule.os }"
                            :aria-selected="displayOs === rule.os"
                            @click="selectOs(rule.os)"
                        >
                            <svg
                                class="seg__logo"
                                :viewBox="LOGOS[rule.os].viewBox"
                                aria-hidden="true"
                                :fill="LOGOS[rule.os].stroke ? 'none' : 'currentColor'"
                                :stroke="LOGOS[rule.os].stroke ? 'currentColor' : 'none'"
                                stroke-width="1.6"
                                stroke-linecap="round"
                                stroke-linejoin="round"
                            >
                                <path :d="LOGOS[rule.os].path" />
                            </svg>
                            <span>{{ rule.label }}</span>
                        </button>
                    </div>
                </div>

                <div class="console__body">
                    <!-- architecture toggle -->
                    <div v-if="!loading && archList.length > 1" class="arch">
                        <span class="arch__label">{{ t.download.archLabel }}</span>
                        <div class="seg seg--arch" role="tablist" :aria-label="t.download.archAria">
                            <button
                                v-for="arch in archList"
                                :key="arch.key"
                                type="button"
                                role="tab"
                                class="seg__btn seg__btn--arch"
                                :class="{ 'is-on': activeArch === arch.key }"
                                :aria-selected="activeArch === arch.key"
                                @click="selectArch(arch.key)"
                            >
                                {{ arch.label }}
                            </button>
                        </div>
                    </div>

                    <!-- loading skeleton -->
                    <div v-if="loading" class="rows">
                        <div v-for="n in 3" :key="n" class="row row--skeleton">
                            <span class="row__chip"></span>
                            <span class="row__meta">
                                <span class="row__name"></span>
                                <span class="row__desc"></span>
                            </span>
                            <span class="row__cta"></span>
                        </div>
                    </div>

                    <!-- format rows -->
                    <div v-else-if="rows.length" class="rows">
                        <a
                            v-for="row in rows"
                            :key="row.key"
                            class="row"
                            :class="{ 'row--primary': row.primary && isOwnPlatform }"
                            :href="row.url"
                            target="_blank"
                            rel="noopener noreferrer"
                            :title="row.file || row.name"
                        >
                            <span class="row__chip">{{ row.ext }}</span>
                            <span class="row__meta">
                                <span class="row__name">
                                    {{ row.name }}
                                    <em v-if="row.primary && isOwnPlatform" class="row__tag">{{ t.download.recommended }}</em>
                                </span>
                                <span class="row__desc">{{ row.desc }}</span>
                            </span>
                            <span class="row__cta">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1"
                                    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                    <path d="M12 3v12" /><path d="M8 11l4 4 4-4" /><path d="M5 21h14" />
                                </svg>
                                {{ t.download.download }}
                            </span>
                        </a>
                    </div>

                    <div v-else class="rows-empty">
                        {{ t.download.empty }}
                        <a :href="releaseUrl" target="_blank" rel="noopener noreferrer">GitHub Releases</a>
                        {{ t.download.emptyTail }}
                    </div>
                </div>

                <footer class="console__foot">
                    <button
                        v-if="displayOs === 'macos'"
                        class="console__note-toggle"
                        type="button"
                        :aria-expanded="noteOpen"
                        @click="toggleNote"
                    >
                        {{ t.download.macNoteToggle }}
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
                        </svg>
                    </button>
                    <div v-if="displayOs === 'macos' && noteOpen" class="console__note">
                        {{ t.download.macNoteBody1 }}
                        <code>/Applications</code> {{ t.download.macNoteBody2 }}
                        <code class="console__cmd">xattr -dr com.apple.quarantine /Applications/FlyNarwhal.app</code>
                        {{ t.download.macNoteTail }}
                    </div>
                    <p v-else class="console__tip">{{ osNote }}</p>
                </footer>
            </div>

            <p v-if="publishedLabel" class="dl__note">
                {{ format(t.download.publishedNote, { published: publishedLabel }) }}
                <a :href="releaseUrl" target="_blank" rel="noopener noreferrer">{{ t.download.releasesPage }}</a>{{ t.download.publishedNoteTail }}
            </p>
        </div>
    </section>
</template>

<style scoped>
.dl {
    padding: clamp(72px, 11vw, 140px) 0;
    background: var(--bg);
}

.dl__head {
    text-align: center;
}

.dl__head .lede {
    margin: 16px auto 0;
}

/* ---------- console ---------- */

.console {
    max-width: 880px;
    margin: 0 auto;
    background: var(--surface);
    border: 1px solid var(--hairline);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-md);
    overflow: hidden;
}

.console__tabs {
    display: flex;
    justify-content: center;
    padding: 22px 24px 20px;
    border-bottom: 1px solid var(--hairline);
}

/* segmented control — iOS-style track with a white selected pill */
.seg {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding: 4px;
    background: var(--bg);
    border: 1px solid var(--hairline);
    border-radius: 999px;
}

.seg__btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 9px 18px;
    border-radius: 999px;
    font-size: 14.5px;
    font-weight: 600;
    color: var(--ink-muted);
    white-space: nowrap;
    transition: color 0.25s var(--ease), background-color 0.25s var(--ease),
        box-shadow 0.25s var(--ease);
}

.seg__btn:hover {
    color: var(--ink-soft);
}

.seg__btn.is-on {
    background: var(--surface-2);
    color: var(--ink);
    box-shadow: var(--shadow-sm);
}

.seg__logo {
    width: 18px;
    height: 18px;
    color: var(--ink-muted);
    transition: color 0.25s var(--ease);
}

.seg__btn.is-on .seg__logo {
    color: var(--brand);
}

.seg--arch .seg__btn {
    padding: 7px 16px;
    font-size: 13.5px;
}

/* ---------- body ---------- */

.console__body {
    padding: 20px 28px 8px;
}

.arch {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 16px;
}

.arch__label {
    font-size: 12.5px;
    font-weight: 650;
    letter-spacing: 0.02em;
    color: var(--ink-muted);
}

/* ---------- format rows ---------- */

.rows {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.row {
    display: grid;
    grid-template-columns: auto 1fr auto;
    align-items: center;
    gap: 16px;
    padding: 14px 16px;
    border: 1px solid var(--hairline);
    border-radius: var(--radius-md);
    background: var(--surface-2);
    color: inherit;
    transition: transform 0.25s var(--ease), box-shadow 0.25s var(--ease),
        border-color 0.25s var(--ease);
}

.row:hover {
    transform: translateY(-2px);
    border-color: rgba(0, 122, 255, 0.4);
    box-shadow: var(--shadow-md);
}

.row--primary {
    background: var(--brand-tint);
    border-color: rgba(0, 122, 255, 0.28);
}

.row--primary:hover {
    border-color: rgba(0, 122, 255, 0.5);
}

.row__chip {
    min-width: 72px;
    padding: 8px 12px;
    text-align: center;
    border-radius: 10px;
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    font-size: 13px;
    font-weight: 700;
    background: var(--bg);
    color: var(--ink-soft);
    border: 1px solid var(--hairline);
}

.row--primary .row__chip {
    background: var(--brand);
    color: #fff;
    border-color: var(--brand);
}

.row__meta {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
}

.row__name {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 650;
    color: var(--ink);
}

.row__tag {
    font-style: normal;
    font-size: 11px;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    background: var(--brand);
    color: #fff;
}

.row__desc {
    font-size: 12.5px;
    color: var(--ink-muted);
}

.row__cta {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 9px 16px;
    border-radius: 999px;
    font-size: 13.5px;
    font-weight: 600;
    background: var(--surface-2);
    color: var(--brand);
    border: 1px solid rgba(0, 122, 255, 0.3);
}

.row--primary .row__cta {
    background: var(--brand);
    color: #fff;
    border-color: var(--brand);
}

.row__cta svg {
    width: 15px;
    height: 15px;
}

/* loading skeleton */
.row--skeleton {
    pointer-events: none;
}

.row--skeleton .row__chip,
.row--skeleton .row__name,
.row--skeleton .row__desc,
.row--skeleton .row__cta {
    background: var(--bg);
    border-color: transparent;
    color: transparent;
    animation: shimmer 1.4s ease-in-out infinite;
}

.row--skeleton .row__chip { min-height: 38px; }
.row--skeleton .row__name { height: 15px; width: 40%; }
.row--skeleton .row__desc { height: 12px; width: 60%; }
.row--skeleton .row__cta { min-width: 78px; min-height: 36px; }

@keyframes shimmer {
    0%, 100% { opacity: 0.5; }
    50% { opacity: 1; }
}

.rows-empty {
    padding: 26px 8px;
    font-size: 14px;
    color: var(--ink-muted);
    text-align: center;
}

/* ---------- footer ---------- */

.console__foot {
    padding: 16px 28px 22px;
    border-top: 1px solid var(--hairline);
}

.console__tip {
    font-size: 12.5px;
    line-height: 1.6;
    color: var(--ink-muted);
}

.console__note-toggle {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12.5px;
    font-weight: 600;
    color: var(--brand);
    padding: 0;
}

.console__note-toggle svg {
    transition: transform 0.25s var(--ease);
}

.console__note-toggle[aria-expanded='true'] svg {
    transform: rotate(180deg);
}

.console__note {
    margin-top: 10px;
    font-size: 12.5px;
    line-height: 1.7;
    color: var(--ink-muted);
}

.console__note code {
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    background: var(--code-bg);
    padding: 1px 5px;
    border-radius: 5px;
}

.console__cmd {
    display: block;
    margin-top: 8px;
    padding: 9px 11px;
    font-size: 11.5px;
    word-break: break-all;
    line-height: 1.55;
}

.dl__note {
    margin-top: 34px;
    text-align: center;
    font-size: 13.5px;
    color: var(--ink-muted);
    line-height: 1.7;
}

/* ---------- responsive ---------- */

@media (max-width: 560px) {
    .console__tabs {
        padding: 16px 14px 14px;
    }

    .seg--platform {
        width: 100%;
    }

    .seg--platform .seg__btn {
        flex: 1;
        justify-content: center;
        padding: 9px 8px;
    }

    .console__body,
    .console__foot {
        padding-left: 16px;
        padding-right: 16px;
    }

    .arch {
        flex-wrap: wrap;
    }

    .row {
        grid-template-columns: auto 1fr;
        gap: 12px;
    }

    .row__cta {
        grid-column: 1 / -1;
        justify-content: center;
    }
}

@media (prefers-reduced-motion: reduce) {
    .row--skeleton .row__chip,
    .row--skeleton .row__name,
    .row--skeleton .row__desc,
    .row--skeleton .row__cta {
        animation: none;
    }
}
</style>
