/**
 * 带 TTL 的 localStorage 缓存 + 并发去重。
 *
 * 「下载」与「更新日志」都需要同一套机制：把结果存进 localStorage、
 * 未过期就不发请求、网络失败时用旧数据兜底。放在这里而不是各自的
 * composable 里，免得两处实现逐渐走样（原先更新日志有并发去重、
 * 下载没有，就是这种走样的结果）。
 *
 * 纯逻辑模块，不依赖 Vue，也不持有响应式状态。
 */

const NS = 'fwn:'

// 同一 key 的并发调用共享这一个 Promise。用 Map 而不是单个槽位，
// 这样两个功能互不干扰。
const inflight = new Map()

function storage() {
    try {
        return window.localStorage
    } catch {
        // 隐私模式下访问 localStorage 本身就可能抛异常。
        return null
    }
}

function readRecord(key) {
    const store = storage()
    if (!store) return null
    try {
        const raw = store.getItem(NS + key)
        if (!raw) return null
        const rec = JSON.parse(raw)
        // 损坏或结构不对的条目一律当作不存在，避免一条坏数据把页面卡死。
        if (!rec || typeof rec !== 'object' || rec.savedAt === undefined) return null
        return rec
    } catch {
        return null
    }
}

/** 读取未过期的缓存数据；已过期、不存在或解析失败都返回 null。 */
export function readFresh(key, ttlMs) {
    const rec = readRecord(key)
    if (!rec) return null
    return Date.now() - rec.savedAt < ttlMs ? rec.data : null
}

/** 读取任意缓存数据（忽略过期时间），供网络失败时兜底展示。 */
export function readAny(key) {
    const rec = readRecord(key)
    return rec ? rec.data : null
}

/** 写入缓存。写失败（隐私模式、配额满）静默忽略，退化为「无缓存、照常请求」。 */
export function write(key, data) {
    const store = storage()
    if (!store) return
    try {
        store.setItem(NS + key, JSON.stringify({ v: 1, savedAt: Date.now(), data }))
    } catch {
        // 忽略：缓存只是优化，写不进去不影响功能。
    }
}

/** 清空本模块管理的全部缓存键，供调试使用。 */
export function purge() {
    const store = storage()
    if (!store) return
    try {
        const keys = []
        for (let i = 0; i < store.length; i++) {
            const k = store.key(i)
            if (k && k.startsWith(NS)) keys.push(k)
        }
        for (const k of keys) store.removeItem(k)
    } catch {
        // 忽略。
    }
}

/**
 * 取数封装：未过期缓存直接返回（不发网络请求）；否则交给 fetcher，
 * 成功写缓存；失败时若存在旧缓存则用旧值兜底，完全没有才抛错。
 *
 * 返回 { data, stale }，stale 表示这是网络失败后回退的旧数据。
 */
export function cachedFetch(key, ttlMs, fetcher) {
    const fresh = readFresh(key, ttlMs)
    if (fresh !== null) return Promise.resolve({ data: fresh, stale: false })

    const existing = inflight.get(key)
    if (existing) return existing

    const p = (async () => {
        try {
            const data = await fetcher()
            write(key, data)
            return { data, stale: false }
        } catch (e) {
            // 旧数据严格优于错误页：用户至少还能看到上次的版本和下载链接。
            const stale = readAny(key)
            if (stale !== null) return { data: stale, stale: true }
            throw e
        } finally {
            inflight.delete(key)
        }
    })()

    inflight.set(key, p)
    return p
}