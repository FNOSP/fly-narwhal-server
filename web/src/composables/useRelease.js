import { computed, onMounted, ref } from 'vue'

const REPO = 'FNOSP/FlyNarwhal'
const MIRROR_PREFIX = 'https://ghfast.top/'
// Fallback version used when the GitHub API is unreachable, so the page still
// offers working pinned download links. Must be a STABLE tag — the
// list-by-tag endpoint returns prereleases too. Keep in sync with releases.
const FALLBACK_TAG = 'v2.3.3'

const RELEASES_PAGE = `https://github.com/${REPO}/releases/latest`

// Display order: earlier entries render first (primary) within each group.
const OS_RULES = [
    {
        os: 'windows',
        label: 'Windows',
        pattern: /_Windows_/i,
        group: [
            { key: 'amd64', label: '下载 x86_64' },
            { key: 'aarch64', label: '下载 ARM64' },
        ],
    },
    {
        os: 'macos',
        label: 'macOS',
        pattern: /_MacOS_/i,
        group: [
            { key: 'amd64', label: '下载 Intel' },
            { key: 'aarch64', label: '下载 Apple Silicon' },
        ],
    },
    {
        os: 'linux',
        label: 'Linux',
        pattern: /_Linux_/i,
        group: [
            { key: 'amd64', label: 'x86_64 (amd64)' },
            { key: 'aarch64', label: 'ARM64 (aarch64)' },
        ],
    },
]

const FORMAT_ORDER = { exe: 0, dmg: 1, deb: 2, rpm: 3, zst: 4, appimage: 5, zip: 9 }

// `section` 0 is the primary install format, 1 the alternate distro packages,
// 3 the portable archives (rendered as secondary buttons).
const FORMAT_LABELS = {
    exe: { sub: '.exe 安装包', section: 0 },
    dmg: { sub: '.dmg', section: 0 },
    deb: { sub: 'deb · Debian / Ubuntu', short: 'deb', section: 1 },
    rpm: { sub: 'rpm · RHEL / Fedora', short: 'rpm', section: 1 },
    zst: { sub: 'pkg · Arch Linux', short: 'pkg', section: 1 },
    appimage: { sub: 'AppImage · 通用格式', short: 'AppImage', section: 1 },
    zip: { sub: '.zip 压缩包', section: 3 },
}

const ARCH_ALIASES = { amd64: 'amd64', x64: 'amd64', aarch64: 'aarch64', arm64: 'aarch64' }

function assetExt(name) {
    const lower = name.toLowerCase()
    if (lower.endsWith('.tar.zst')) return 'zst'
    const idx = lower.lastIndexOf('.')
    return idx >= 0 ? lower.slice(idx + 1) : ''
}

const formatOf = (name) => FORMAT_LABELS[assetExt(name)] || null

const archKey = (name) => {
    const m = name.toLowerCase().match(/_(amd64|x64|aarch64|arm64)_/)
    return m ? ARCH_ALIASES[m[1]] : ''
}

const sortAssets = (assets) =>
    assets
        .slice()
        .sort((a, b) => (FORMAT_ORDER[assetExt(a.name)] ?? 99) - (FORMAT_ORDER[assetExt(b.name)] ?? 99))

function buildButton(asset, { label, sub, secondary = false, title = '' } = {}) {
    return {
        url: MIRROR_PREFIX + (asset ? asset.browser_download_url : ''),
        label,
        sub,
        secondary,
        tip: title || sub || '',
    }
}

/** Bucket a release's assets per OS card, split by architecture key. */
function collectAssets(releaseAssets) {
    const result = {}
    for (const rule of OS_RULES) {
        const matched = sortAssets(
            releaseAssets.filter((a) => formatOf(a.name) && rule.pattern.test('_' + a.name + '_')),
        )
        const byKey = {}
        for (const arch of rule.group) byKey[arch.key] = []
        const others = []
        for (const asset of matched) {
            const arch = archKey(asset.name)
            if (byKey[arch]) byKey[arch].push(asset)
            else others.push(asset)
        }
        result[rule.os] = { byKey, others }
    }
    return result
}

/**
 * Turn one OS card's bucket into the button groups the template renders.
 * Linux groups by architecture then lists one button per package format;
 * the other platforms list one button per architecture, splitting portable
 * archives out as secondary buttons.
 */
function groupsFor(rule, bucket) {
    const groups = []

    if (rule.os === 'linux') {
        for (const arch of rule.group) {
            const list = bucket.byKey[arch.key]
            if (!list.length) continue
            groups.push({
                title: arch.label,
                buttons: list.map((asset) => {
                    const fmt = formatOf(asset.name)
                    return buildButton(asset, { label: fmt.short, sub: fmt.sub, title: fmt.sub })
                }),
            })
        }
    } else {
        const primary = []
        const secondary = []
        for (const arch of rule.group) {
            const list = bucket.byKey[arch.key]
            if (!list.length) continue
            const portableCount = list.filter((a) => formatOf(a.name).section === 3).length
            for (const asset of list) {
                const fmt = formatOf(asset.name)
                const isPortable = fmt.section === 3
                const suffix = isPortable ? (portableCount > 1 ? '（便携版）' : ' 便携版') : ''
                const button = buildButton(asset, { label: arch.label + suffix, sub: fmt.sub })
                ;(isPortable ? secondary : primary).push(button)
            }
        }
        if (primary.length) groups.push({ title: '', buttons: primary })
        // Portable archives render in the same group as the installers, flagged
        // secondary, so the card stays a single visual cluster.
        if (secondary.length) {
            if (groups.length) groups[groups.length - 1].buttons.push(...secondary)
            else groups.push({ title: '', buttons: secondary })
        }
    }

    for (const asset of bucket.others) {
        const fmt = formatOf(asset.name)
        groups.push({
            title: '',
            buttons: [buildButton(asset, { label: '下载其他格式', sub: fmt.sub, secondary: true })],
        })
    }

    return groups
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
        const collected = collectAssets(release.assets)
        const next = {}
        for (const rule of OS_RULES) next[rule.os] = groupsFor(rule, collected[rule.os])
        platformGroups.value = next
        error.value = false
        loading.value = false
    }

    /** Every card falls back to a single link to the releases page. */
    function applyReleasesPageFallback() {
        const next = {}
        for (const rule of OS_RULES) {
            next[rule.os] = [
                { title: '', buttons: [{ url: RELEASES_PAGE, label: '前往下载页面', sub: '', secondary: false, tip: '' }] },
            ]
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
