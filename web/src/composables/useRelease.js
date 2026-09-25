import { computed, onMounted, ref } from 'vue'

const REPO = 'FNOSP/FlyNarwhal'
const MIRROR_PREFIX = 'https://ghfast.top/'
// Fallback version used when the GitHub API is unreachable, so the page still
// offers working pinned download links. Must be a STABLE tag — the
// list-by-tag endpoint returns prereleases too. Keep in sync with releases.
const FALLBACK_TAG = 'v2.3.3'

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
        if (!res.ok) throw new Error('HTTP ' + res.status)
        return res.json()
    }

    /**
     * Fallback when the GitHub API is unreachable (rate limit / network): pin the
     * known-good FALLBACK_TAG assets via the list-by-tag endpoint, which may still
     * be cached; if that also fails, link to the releases page so users are never
     * stuck without a way to download.
     */
    async function load() {
        try {
            applyRelease(await fetchRelease('releases/latest'))
        } catch (e) {
            console.error('获取最新版本失败，使用回退版本链接', e)
            try {
                applyRelease(await fetchRelease('releases/tags/' + FALLBACK_TAG))
            } catch (e2) {
                console.error('回退版本获取也失败', e2)
                applyReleasesPageFallback()
            }
        }
    }

    onMounted(load)

    return { loading, tag, releaseUrl, error, platformGroups, versionLabel, publishedLabel, osRules: OS_RULES, load }
}
