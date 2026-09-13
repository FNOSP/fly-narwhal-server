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
 * Keep-a-Changelog layout: `## [X.Y.Z] - date` sections containing
 * `### Category` groups of `- **Title**: description` bullets, optional
 * `> quote` notes and bare intro paragraphs.
 */
function parseChangelog(text) {
    const versions = []
    for (const section of text.split(/^##\s+/m).slice(1)) {
        const lines = section.split('\n')
        const header = (lines.shift() || '').trim()
        const m = header.match(/^\[([^\]]+)\](?:\s*-\s*(.+))?$/)
        if (!m) continue
        const version = { version: m[1], date: (m[2] || '').trim(), notes: [], intro: [], categories: [] }
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
            if (line.startsWith('>')) {
                const quote = line.replace(/^>\s?/, '').trim()
                if (quote) version.notes.push(parseInline(quote))
                continue
            }
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
                continue
            }
            if (!current) version.intro.push(parseInline(line))
        }
        versions.push(version)
    }
    return versions
}

export function useChangelog() {
    const loading = ref(true)
    const error = ref(false)
    // The newest release section: first versioned entry that carries content
    // (skips the always-present, usually empty `[Unreleased]` bucket).
    const latest = ref(null)
    const history = ref([])

    async function load() {
        loading.value = true
        error.value = false
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
        if (text === null) {
            loading.value = false
            error.value = true
            return
        }
        const versions = parseChangelog(text)
        latest.value =
            versions.find((v) => v.version !== 'Unreleased' && (v.categories.length || v.intro.length)) ||
            versions[0] ||
            null
        history.value = versions.filter((v) => v.version !== 'Unreleased')
        loading.value = false
    }

    onMounted(load)

    return { loading, error, latest, history }
}
