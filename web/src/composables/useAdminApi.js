import { apiBase } from './useApiBase'

/**
 * Fetch wrapper for the admin API. Requests travel the same origin prefix the
 * page was loaded under (fnOS gateway /app/<name>), so the fnOS session cookie
 * rides along and the gateway injects the trusted identity headers.
 *
 * Error semantics: 404 means the request did not come through the gateway
 * (direct port access) — the page must be opened from the fnOS desktop entry;
 * 403 means the logged-in fnOS user is not an administrator.
 */
export class AdminApiError extends Error {
    constructor(kind, message) {
        super(message)
        this.kind = kind // 'not-gateway' | 'not-admin' | 'server' | 'network'
    }
}

async function request(path, { method = 'GET', body } = {}) {
    let response
    try {
        response = await fetch(`${apiBase()}${path}`, {
            method,
            headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
            body: body !== undefined ? JSON.stringify(body) : undefined,
        })
    } catch (e) {
        throw new AdminApiError('network', '无法连接到服务端')
    }
    if (response.status === 404) {
        throw new AdminApiError('not-gateway', '请从 fnOS 桌面的「飞鲸影视管理」入口打开本页')
    }
    if (response.status === 403) {
        throw new AdminApiError('not-admin', '需要管理员权限')
    }
    let result
    try {
        result = await response.json()
    } catch (e) {
        throw new AdminApiError('server', '服务端响应异常')
    }
    if (result.code !== 200) {
        throw new AdminApiError('server', result.msg || '请求失败')
    }
    return result.data
}

export function useAdminApi() {
    return {
        session: () => request('/api/admin/session'),
        listCodes: () => request('/api/admin/codes'),
        createCode: (name, expireDays, scope) =>
            request('/api/admin/codes', { method: 'POST', body: { name, expireDays, scope } }),
        setStatus: (id, enabled) =>
            request(`/api/admin/codes/${id}/status`, { method: 'POST', body: { enabled } }),
        setScope: (id, scope) =>
            request(`/api/admin/codes/${id}/scope`, { method: 'POST', body: { scope } }),
        deleteCode: (id) => request(`/api/admin/codes/${id}`, { method: 'DELETE' }),
        accessLogs: (codeId, limit = 200) =>
            request(`/api/admin/access/logs?limit=${limit}${codeId != null ? `&codeId=${codeId}` : ''}`),
        overview: () => request('/api/admin/overview'),
        setLegacyAuth: (enabled) =>
            request('/api/admin/legacy-auth', { method: 'POST', body: { enabled } }),
    }
}