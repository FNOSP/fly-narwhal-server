import { computed, onMounted, ref } from 'vue'
import { cachedFetch } from '../lib/cachedJsonStore'

const REPO = 'FNOSP/FlyNarwhal'
const MIRROR_PREFIX = 'https://ghfast.top/'

// GitHub 的未认证限额是 60 次/小时且按出口 IP 计数，同一出口下的所有访客
// 共用这份配额。缓存 20 分钟把「每次刷新一次请求」压到「每浏览器 20 分钟
// 至多一次」。代价是新版本发布后最长 20 分钟内页面仍显示上一版。
const RELEASE_TTL = 20 * 60 * 1000
const RELEASE_KEY = 'release:latest'

// Fallback versions used when the GitHub API is unreachable (rate limit /
// network), newest first. Any one of them gives users real pinned download
// links, so the page degrades to a slightly older release rather than to a
// bare link. Must be STABLE tags — the list-by-tag endpoint returns
// prereleases too. A single constant goes stale on every release; a ladder
// only needs opportunistically refreshing and self-heals if the newest 404s.
const FALLBACK_TAGS = ['v2.3.6', 'v2.3.5', 'v2.3.3']

const RELEASES_PAGE = `https://github.com/${REPO}/releases/latest`

// Display order of the platform tabs, and the per-platform architecture
// labels shown in the arch toggle. macOS reads friendlier as Intel / Apple
// Silicon than x86_64 / ARM64; the asset filenames still use the generic keys.
const OS_RULES = [
    { os: 'windows', label: 'Windows', pattern: /_Windows_/i, archs: [
        { key: 'amd64', label: 'x86_64' },
        { key: 'aarch64', label: 'ARM64' },
    ] },
    { os: 'macos', label: 'macOS', pattern: /_MacOS_/i, archs: [
        { key: 'amd64', label: 'Intel' },
        { key: 'aarch64', label: 'Apple Silicon' },
    ] },
    { os: 'linux', label: 'Linux', pattern: /_Linux_/i, archs: [
        { key: 'amd64', label: 'x86_64' },
        { key: 'aarch64', label: 'ARM64' },
    ] },
]

const FORMAT_ORDER = { exe: 0, dmg: 1, deb: 2, rpm: 3, zst: 4, appimage: 5, zip: 9 }

// Per-format presentation for the download rows. `ext` is the badge text,
// `name`/`desc` are the human labels, `primary` marks the recommended pick for
// its platform (exe / dmg / deb). AppImage stays untagged so Linux never shows
// two “推荐” rows next to each other.
const FORMATS = {
    exe: { ext: '.exe', name: 'Windows 安装包', desc: '一键安装到本机', primary: true },
    zip: { ext: '.zip', name: '便携压缩包', desc: '解压即用，无需安装' },
    dmg: { ext: '.dmg', name: '磁盘映像', desc: '拖入「应用程序」即可使用', primary: true },
    deb: { ext: '.deb', name: 'Debian / Ubuntu', desc: 'apt / dpkg 安装', primary: true },
    rpm: { ext: '.rpm', name: 'RHEL / Fedora', desc: 'dnf / yum 安装' },
    zst: { ext: 'pkg', name: 'Arch Linux', desc: 'pacman 安装' },
    appimage: { ext: 'AppImage', name: '通用格式', desc: '单文件，任意发行版可运行' },
}

const ARCH_ALIASES = { amd64: 'amd64', x64: 'amd64', aarch64: 'aarch64', arm64: 'aarch64' }

function assetExt(name) {
    const lower = name.toLowerCase()
    if (lower.endsWith('.tar.zst')) return 'zst'
    const idx = lower.lastIndexOf('.')
    return idx >= 0 ? lower.slice(idx + 1) : ''
}

const formatOf = (name) => FORMATS[assetExt(name)] || null

const archKey = (name) => {
    const m = name.toLowerCase().match(/_(amd64|x64|aarch64|arm64)_/)
    return m ? ARCH_ALIASES[m[1]] : ''
}

const sortAssets = (assets) =>
    assets
        .slice()
        .sort((a, b) => (FORMAT_ORDER[assetExt(a.name)] ?? 99) - (FORMAT_ORDER[assetExt(b.name)] ?? 99))

function buildRow(asset, fmtKey) {
    const f = FORMATS[fmtKey]
    return {
        key: fmtKey,
        ext: f.ext,
        name: f.name,
        desc: f.desc,
        primary: !!f.primary,
        url: MIRROR_PREFIX + asset.browser_download_url,
        file: asset.name,
    }
}

/**
 * Bucket a release's assets into the console's three-level shape:
 * `platforms[os] = { archs: [...], byArch: { [arch]: Row[] } }`. Each platform
 * only lists the architectures that actually have assets, and the arch toggle
 * is hidden when a platform ships a single architecture.
 */
function buildPlatforms(releaseAssets) {
    const platforms = {}
    for (const rule of OS_RULES) {
        const matched = sortAssets(
            releaseAssets.filter((a) => formatOf(a.name) && rule.pattern.test('_' + a.name + '_')),
        )
        const byArch = {}
        for (const arch of rule.archs) byArch[arch.key] = []
        for (const asset of matched) {
            const arch = archKey(asset.name)
            if (byArch[arch]) byArch[arch].push(buildRow(asset, assetExt(asset.name)))
        }
        platforms[rule.os] = {
            archs: rule.archs.filter((a) => byArch[a.key].length),
            byArch,
        }
    }
    return platforms
}

export function useRelease() {
    const loading = ref(true)
    const tag = ref('')
    const publishedAt = ref(null)
    const releaseUrl = ref(RELEASES_PAGE)
    const error = ref(false)
    const platformGroups = ref({})

    const versionLabel = computed(() => {
        if (error.value) return '查看 GitHub 更新日志 →'
        if (!tag.value) return '正在获取最新版本…'
        return `最新版本 ${tag.value} · 查看更新日志 →`
    })

    const publishedLabel = computed(() => {
        if (error.value || !publishedAt.value) return ''
        const date = new Date(publishedAt.value).toLocaleDateString('zh-CN')
        return `当前最新版本 ${tag.value}，发布于 ${date}。`
    })

    function applyRelease(release) {
        tag.value = release.tag_name
        publishedAt.value = release.published_at
        releaseUrl.value = release.html_url
        platformGroups.value = buildPlatforms(release.assets)
        error.value = false
        loading.value = false
    }

    /** Every platform falls back to a single link to the releases page. */
    function applyReleasesPageFallback() {
        const next = {}
        for (const rule of OS_RULES) {
            next[rule.os] = {
                archs: [{ key: 'any', label: '全部架构' }],
                byArch: {
                    any: [
                        {
                            key: 'link',
                            ext: '↗',
                            name: '前往下载页面',
                            desc: '在 GitHub Releases 选择安装包',
                            primary: true,
                            url: RELEASES_PAGE,
                            file: '',
                        },
                    ],
                },
            }
        }
        platformGroups.value = next
        error.value = true
        loading.value = false
    }

    async function fetchRelease(path) {
        const res = await fetch(`https://api.github.com/repos/${REPO}/${path}`, {
            headers: { Accept: 'application/vnd.github+json' },
        })
        // 403/429 表示未认证限额用尽（按出口 IP 计数）。等待和重试都没用，
        // 直接交给上层回退。
        if (!res.ok) throw new Error('HTTP ' + res.status)
        return res.json()
    }

    /**
     * One network round trip: latest, then the FALLBACK_TAGS ladder newest
     * first. Throws only when every source failed.
     */
    async function fetchReleaseWithFallback() {
        try {
            return await fetchRelease('releases/latest')
        } catch (e) {
            console.error('获取最新版本失败，尝试回退版本', e)
            for (const tag of FALLBACK_TAGS) {
                try {
                    return await fetchRelease('releases/tags/' + tag)
                } catch (e2) {
                    console.error('回退版本获取也失败', tag, e2)
                }
            }
            throw new Error('all release sources failed')
        }
    }

    /**
     * Loads inside a 20-minute window. A fresh cache entry resolves without
     * touching the network; an expired one refetches; and if that refetch
     * fails, cachedFetch hands back the previous release so the page still
     * shows real download buttons instead of the generic fallback.
     */
    async function load() {
        try {
            const { data } = await cachedFetch(RELEASE_KEY, RELEASE_TTL, fetchReleaseWithFallback)
            applyRelease(data)
        } catch (e) {
            // Nothing cached and every source failed — link to the releases page.
            console.error('获取最新版本失败，使用回退版本链接', e)
            applyReleasesPageFallback()
        }
    }

    onMounted(load)

    return { loading, tag, releaseUrl, error, platformGroups, versionLabel, publishedLabel, osRules: OS_RULES, load }
}
