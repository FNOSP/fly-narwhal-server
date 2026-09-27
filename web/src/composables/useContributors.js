import { onMounted, ref } from 'vue'
import { cachedFetch } from '../lib/cachedJsonStore'

const REPO = 'FNOSP/FlyNarwhal'
const CONTRIBUTORS_KEY = 'contributors'

// 贡献者名单的变化频率远低于版本发布，缓存 1 小时足够；同样受未认证
// 60 次/小时（按出口 IP 计）的限额约束，所以和下载区共用一套
// localStorage 兜底机制——限流或断网时退到上次的名单而不是整块消失。
const CONTRIBUTORS_TTL = 60 * 60 * 1000

const loading = ref(true)
const contributors = ref([])

let loadingPromise = null

async function fetchContributors() {
    const res = await fetch(`https://api.github.com/repos/${REPO}/contributors?per_page=100`, {
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
    loadingPromise = (async () => {
        try {
            const { data } = await cachedFetch(CONTRIBUTORS_KEY, CONTRIBUTORS_TTL, fetchContributors)
            contributors.value = data
        } catch (e) {
            // 无缓存且网络失败：贡献者区整块不渲染，页脚其余部分不受影响。
            console.error('获取贡献者列表失败', e)
        } finally {
            loading.value = false
            loadingPromise = null
        }
    })()
    return loadingPromise
}

export function useContributors() {
    onMounted(load)
    return { loading, contributors }
}
