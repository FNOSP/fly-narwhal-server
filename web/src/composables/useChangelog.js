import { computed, onMounted, ref, watch } from 'vue'
import { useRelease } from './useRelease'
import { useI18n } from './useI18n'

/**
 * Changelog channels: the client app (FlyNarwhal) and this server each keep a
 * CHANGELOG.md in their own public GitHub repo. The landing page and the
 * timeline page fetch them live so the page never ships a stale copy, and let
 * the visitor switch between the two.
 *
 * Per user decision both channels try jsDelivr first with raw.githubusercontent
 * as the fallback. (Earlier testing found jsDelivr merely 301-redirects to raw
 * rather than serving an independent content cache, so the order mostly costs
 * one extra hop — kept anyway for consistency with mainland-China reachability
 * where raw is often blocked outright.)
 *
 * Order matters within a channel: the loop stops at the first source that
 * answers, so a cached-but-old copy in front would hide new releases
 * indefinitely.
 */
const CHANNELS = {
    client: {
        sources: [
            'https://cdn.jsdelivr.net/gh/FNOSP/FlyNarwhal@master/CHANGELOG.md',
            'https://raw.githubusercontent.com/FNOSP/FlyNarwhal/master/CHANGELOG.md',
        ],
        url: 'https://github.com/FNOSP/FlyNarwhal/blob/master/CHANGELOG.md',
    },
    server: {
        sources: [
            'https://cdn.jsdelivr.net/gh/FNOSP/fly-narwhal-server@master/CHANGELOG.md',
            'https://raw.githubusercontent.com/FNOSP/fly-narwhal-server/master/CHANGELOG.md',
        ],
        url: 'https://github.com/FNOSP/fly-narwhal-server/blob/master/CHANGELOG.md',
    },
}

const CHANNEL_STORAGE_KEY = 'fwn:changelog:channel'

function readStoredChannel() {
    try {
        const v = localStorage.getItem(CHANNEL_STORAGE_KEY)
        if (v && CHANNELS[v]) return v
    } catch {
        // localStorage unavailable (private mode etc.) — fall back to default.
    }
    return 'client'
}

/**
 * Module-level shared state: ChangelogSection (landing page) and TimelinePage
 * (#/timeline) are never mounted at the same time, so a single ref carries the
 * visitor's choice across the hash route — pick 服务端 on the landing page and
 * the timeline opens on the server log too. Persisted so a refresh keeps it.
 */
const channel = ref(readStoredChannel())

export function setChannel(next) {
    if (!CHANNELS[next] || next === channel.value) return
    channel.value = next
    try {
        localStorage.setItem(CHANNEL_STORAGE_KEY, next)
    } catch {
        // Non-fatal: the switch still works for this page session.
    }
}

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
                // The changelog stays in its source language regardless of the
                // UI locale, so no translated text ever reaches this parser and
                // the full-width/half-width colon split stays safe.
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

// 每个渠道各自保留上次取回的正文摘要与解析结果，切换回来时内容没变就
// 跳过重复解析。只在本页面会话内有效——刷新即重新校验。
const cache = {
    client: { digest: null, result: null },
    server: { digest: null, result: null },
}

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
async function fetchChangelogText(sources) {
    for (const url of sources) {
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
 * 取回并解析指定渠道的更新日志。每次调用都会向源站校验一次（不是本地 TTL
 * 缓存），这样刷新页面总能同步到 GitHub 上的最新版本。内容没变时跳过重复解析。
 */
async function fetchAll(ch) {
    const entry = cache[ch]
    const text = await fetchChangelogText(CHANNELS[ch].sources)
    if (text === null) {
        // 网络全挂时优先给上次的内容，旧日志好过「无法加载」。
        return entry.result || { error: true, latest: null, history: [] }
    }

    const digest = await digestOf(text)
    // 摘要不可用（无 crypto.subtle）时退化为每次都重新解析。
    if (digest !== null && digest === entry.digest && entry.result) return entry.result

    const result = buildResult(text)
    entry.digest = digest
    entry.result = result
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

    const { t } = useI18n()

    const channelLabel = computed(() => t.value.changelog.channels[channel.value])
    const changelogUrl = computed(() => CHANNELS[channel.value].url)

    // 下载区用的是 20 分钟 localStorage 缓存，更新日志则是每次刷新都取回。
    // 刚发版时下载区会暂时停在上一版，两个区域当场对不上；发现更新日志的
    // 版本更高就让下载区立刻重新拉一次，而不是干等缓存过期。
    // 只对客户端渠道有意义——下载区跟的是客户端 Release，服务端版本号无关。
    const release = useRelease()
    let refreshed = false

    async function load(ch) {
        loading.value = true
        const result = await fetchAll(ch)
        // 请求期间用户又切走了：丢弃过期结果，让新渠道的请求接管。
        if (ch !== channel.value) return
        error.value = result.error
        latest.value = result.latest
        history.value = result.history
        loading.value = false

        if (ch === 'client' && result.latest?.version && release.tag.value) {
            // 更新日志的版本更高 = 下载区还停在缓存里的上一版，立刻重取。
            if (!refreshed && compareVersions(result.latest.version, release.tag.value) > 0) {
                refreshed = true
                release.refresh()
            }
        }
    }

    onMounted(() => load(channel.value))
    watch(channel, (ch) => load(ch))

    return { channel, setChannel, channelLabel, changelogUrl, loading, error, latest, history }
}
