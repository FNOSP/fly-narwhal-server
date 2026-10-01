import { onMounted, ref } from 'vue'
import { cachedFetch } from '../lib/cachedJsonStore'

// Two repos, rendered as two labelled rows in the footer. Cache keys stay
// separate so a stale client list never masquerades as the server one.
const GROUPS = [
    { key: 'client', label: '客户端', repo: 'FNOSP/FlyNarwhal', cacheKey: 'contributors' },
    { key: 'server', label: '服务端', repo: 'FNOSP/fly-narwhal-server', cacheKey: 'contributors-server' },
]

const GRAPH_URL = (repo) => `https://github.com/${repo}/graphs/contributors`

// 贡献者名单的变化频率远低于版本发布，缓存 1 小时足够；同样受未认证
// 60 次/小时（按出口 IP 计）的限额约束，所以和下载区共用一套
// localStorage 兜底机制——限流或断网时退到上次的名单而不是整块消失。
const CONTRIBUTORS_TTL = 60 * 60 * 1000

const loading = ref(true)
// One entry per group: { key, label, graphUrl, contributors }. A group whose
// fetch failed AND has no cache keeps an empty list so its row hides itself.
const groups = ref(GROUPS.map((g) => ({ ...g, graphUrl: GRAPH_URL(g.repo), contributors: [] })))

let loadingPromise = null

async function fetchContributors(repo) {
    const res = await fetch(`https://api.github.com/repos/${repo}/contributors?per_page=100`, {
        headers: { Accept: 'application/vnd.github+json' },
    })
    // 403/429 是未认证限额用尽，重试无意义，直接交给缓存兜底。
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const list = await res.json()
    // 匿名条目（没有 GitHub 账号的提交者）没有 login，无头像可展示也无
    // 主页可跳转，直接滤掉。
    return list
        .filter((c) => c.login)
        .map((c) => ({
            login: c.login,
            avatar: c.avatar_url,
            profile: c.html_url,
            commits: c.contributions,
        }))
}

function load() {
    if (loadingPromise) return loadingPromise
    // The two repos are fetched independently: one rate-limited response
    // must not take down the other group's row.
    loadingPromise = Promise.allSettled(
        GROUPS.map(async (g) => {
            const { data } = await cachedFetch(g.cacheKey, CONTRIBUTORS_TTL, () => fetchContributors(g.repo))
            return { key: g.key, data }
        })
    ).then((results) => {
        for (const r of results) {
            if (r.status === 'fulfilled') {
                const group = groups.value.find((g) => g.key === r.value.key)
                group.contributors = r.value.data
            } else {
                // 无缓存且网络失败：该组整行不渲染，页脚其余部分不受影响。
                console.error('获取贡献者列表失败', r.reason)
            }
        }
        loading.value = false
        loadingPromise = null
    })
    return loadingPromise
}

export function useContributors() {
    onMounted(load)
    return { loading, groups }
}
