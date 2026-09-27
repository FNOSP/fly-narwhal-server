import { onMounted, ref } from 'vue'
import { useRelease } from './useRelease'

/**
 * Client changelog, fetched live so the page never ships a stale copy.
 *
 * raw.githubusercontent comes first: it is the authoritative copy and only
 * carries a 5-minute HTTP cache. jsDelivr used to be first but is not a
 * separate content cache at all — it 301-redirects to raw — so leading with
 * it bought nothing and cost an extra hop. It stays as the fallback because
 * raw is often unreachable from mainland China.
 *
 * Order matters: the loop stops at the first source that answers, so a
 * cached-but-old copy in front would hide new releases indefinitely.
 */
const SOURCES = [
    'https://raw.githubusercontent.com/FNOSP/FlyNarwhal/master/CHANGELOG.md',
    'https://cdn.jsdelivr.net/gh/FNOSP/FlyNarwhal@master/CHANGELOG.md',
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

// 上次取回的正文摘要与解析结果，用于避免重复解析同一份内容。
// 只在本页面会话内有效——刷新即重新校验。
let cachedDigest = null
let cachedResult = null

/**
 * 逐个源尝试，返回正文文本；全部失败返回 null。
 *
 * 必须用 cache: 'no-store'。raw 只发 max-age=300，默认的 cache 模式会让
 * 浏览器直接拿自己那份缓存应答，导致刷新后最多 5 分钟看不到新版本。
 *
 * 为什么不用条件请求（If-None-Match）：raw 虽然发 etag，但没有
 * Access-Control-Expose-Headers，浏览器不允许 JS 读取它，条件请求无法
 * 发起；raw 也不发 Last-Modified。实测唯一可靠的做法就是每次都取回正文。
 * 正文约 22KB，代价可以接受。注意 jsDelivr 的 ?query 缓存穿透同样无效
 * （它只是个 301 跳转到 raw）。
 */
async function fetchChangelogText() {
    for (const url of SOURCES) {
        try {
            const res = await fetch(url, { cache: 'no-store' })
            if (res.ok) return await res.text()
        } catch {
            // CDN down or blocked — try the next source.
        }
    }
    return null
}

/** 正文的 SHA-256 摘要，用来判断内容是否变化。 */
async function digestOf(text) {
    try {
        const buf = new TextEncoder().encode(text)
        const hash = await crypto.subtle.digest('SHA-256', buf)
        return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, '0')).join('')
    } catch {
        return null
    }
}

function buildResult(text) {
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
}

/**
 * 取回并解析更新日志。每次调用都会向服务端校验一次（不是本地 TTL 缓存），
 * 这样刷新页面总能同步到 GitHub 上的最新版本。内容没变时跳过重复解析。
 */
async function fetchAll() {
    const text = await fetchChangelogText()
    if (text === null) {
        // 网络全挂时优先给上次的内容，旧日志好过「无法加载」。
        return cachedResult || { error: true, latest: null, history: [] }
    }

    const digest = await digestOf(text)
    // 摘要不可用（无 crypto.subtle）时退化为每次都重新解析。
    if (digest !== null && digest === cachedDigest && cachedResult) return cachedResult

    const result = buildResult(text)
    cachedDigest = digest
    cachedResult = result
    return result
}

/**
 * 比较「2.3.6」「v2.3.6-rc.1」这类版本号，返回负数 / 0 / 正数。
 *
 * 只取前导数字段（截断到预发布标记），够覆盖本项目的发版方式；无法解析时
 * 退化成字符串比较，保证永远有一个确定的顺序而不是抛错。
 */
function compareVersions(a, b) {
    const parse = (v) => String(v).replace(/^v/i, '').split(/[-+]/)[0].split('.').map((s) => parseInt(s, 10))
    const pa = parse(a)
    const pb = parse(b)
    if (pa.some(Number.isNaN) || pb.some(Number.isNaN)) return String(a).localeCompare(String(b))
    for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
        const d = (pa[i] || 0) - (pb[i] || 0)
        if (d) return d
    }
    return 0
}

export function useChangelog() {
    const loading = ref(true)
    const error = ref(false)
    const latest = ref(null)
    const history = ref([])

    // 下载区用的是 20 分钟 localStorage 缓存，更新日志则是每次刷新都取回。
    // 刚发版时下载区会暂时停在上一版，两个区域当场对不上；发现更新日志的
    // 版本更高就让下载区立刻重新拉一次，而不是干等缓存过期。
    const release = useRelease()
    let refreshed = false

    onMounted(async () => {
        const result = await fetchAll()
        error.value = result.error
        latest.value = result.latest
        history.value = result.history
        loading.value = false

        if (result.latest?.version && release.tag.value) {
            // 更新日志的版本更高 = 下载区还停在缓存里的上一版，立刻重取。
            if (!refreshed && compareVersions(result.latest.version, release.tag.value) > 0) {
                refreshed = true
                release.refresh()
            }
        }
    })

    return { loading, error, latest, history }
}
