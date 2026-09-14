import { onMounted, ref } from 'vue'

/**
 * Client changelog, fetched live so the page never ships a stale copy.
 * The jsDelivr CDN mirror comes first — raw.githubusercontent is often
 * unreachable from mainland China — with raw as the fallback.
 */
const SOURCES = [
    'https://cdn.jsdelivr.net/gh/FNOSP/FlyNarwhal@master/CHANGELOG.md',
    'https://raw.githubusercontent.com/FNOSP/FlyNarwhal/master/CHANGELOG.md',
]

export const CHANGELOG_URL = 'https://github.com/FNOSP/FlyNarwhal/blob/master/CHANGELOG.md'

/**
 * Parses inline markdown into renderable nodes (text / bold / code / link)
 * so the component can render with real elements instead of v-html.
 */
function parseInline(text) {
    const nodes = []
    const re = /(\*\*([^*]+)\*\*)|(`([^`]+)`)|(\[([^\]]+)\]\(([^)]+)\))/g
    let last = 0
    let m
    while ((m = re.exec(text))) {
        if (m.index > last) nodes.push({ t: 'text', v: text.slice(last, m.index) })
        // Bold content is re-parsed so links/code inside **…** render too.
        if (m[2] !== undefined) nodes.push({ t: 'bold', children: parseInline(m[2]) })
        else if (m[4] !== undefined) nodes.push({ t: 'code', v: m[4] })
        else nodes.push({ t: 'link', v: m[6], href: m[7] })
        last = re.lastIndex
    }
    if (last < text.length) nodes.push({ t: 'text', v: text.slice(last) })
    return nodes
}

/**
 * Keep-a-Changelog layout: `## [X.Y.Z] - date` sections holding
 * `### Category` groups of `- **Title**: description` bullets. Quote notes
 * and intro paragraphs are intentionally skipped — the page only shows the
 * Added / Changed / Fixed content.
 */
function parseChangelog(text) {
    const versions = []
    for (const section of text.split(/^##\s+/m).slice(1)) {
        const lines = section.split('\n')
        const header = (lines.shift() || '').trim()
        const m = header.match(/^\[([^\]]+)\](?:\s*-\s*(.+))?$/)
        if (!m) continue
        const version = { version: m[1], date: (m[2] || '').trim(), categories: [] }
        let current = null
        for (const raw of lines) {
            const line = raw.replace(/\s+$/, '')
            if (!line.trim()) continue
            const h3 = line.match(/^###\s+(.+)$/)
            if (h3) {
                current = { name: h3[1].trim(), items: [] }
                version.categories.push(current)
                continue
            }
            if (line.startsWith('>')) continue
            const item = line.match(/^[-*]\s+(.+)$/)
            if (item) {
                if (!current) {
                    current = { name: '', items: [] }
                    version.categories.push(current)
                }
                const body = item[1]
                const titled = body.match(/^\*\*(.+?)\*\*\s*[：:]\s*(.*)$/)
                current.items.push(
                    titled
                        ? { title: parseInline(titled[1]), desc: parseInline(titled[2]) }
                        : { title: [], desc: parseInline(body) },
                )
            }
        }
        versions.push(version)
    }
    return versions
}

// Module-level cache: the landing card and the timeline page share one fetch.
let cached = null
let inflight = null

async function fetchAll() {
    if (cached) return cached
    if (inflight) return inflight
    inflight = (async () => {
        try {
            let text = null
            for (const url of SOURCES) {
                try {
                    const res = await fetch(url)
                    if (res.ok) {
                        text = await res.text()
                        break
                    }
                } catch {
                    // CDN down or blocked — try the next source.
                }
            }
            if (text === null) return { error: true }
            const versions = parseChangelog(text)
            return {
                error: false,
                // The newest release: first versioned entry that carries content
                // (skips the always-present, usually empty `[Unreleased]` bucket).
                latest:
                    versions.find((v) => v.version !== 'Unreleased' && v.categories.some((c) => c.items.length)) ||
                    versions[0] ||
                    null,
                history: versions.filter((v) => v.version !== 'Unreleased'),
            }
        } finally {
            inflight = null
        }
    })()
    const result = await inflight
    if (!result.error) cached = result
    return result
}

export function useChangelog() {
    const loading = ref(true)
    const error = ref(false)
    const latest = ref(null)
    const history = ref([])

    onMounted(async () => {
        const result = await fetchAll()
        error.value = result.error
        latest.value = result.latest
        history.value = result.history
        loading.value = false
    })

    return { loading, error, latest, history }
}
