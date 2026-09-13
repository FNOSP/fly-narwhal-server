import { ref } from 'vue'

/**
 * Auth-code retrieval. The endpoint returns the code exactly once: a later call
 * reports that a code already exists and must be reset by deleting the
 * `auth_code` file next to the server binary.
 */
export function useAuthCode() {
    const open = ref(false)
    const title = ref('授权码')
    const desc = ref('')
    const code = ref('')
    const copied = ref(false)

    function show({ title: t, desc: d, code: c = '' }) {
        title.value = t
        desc.value = d
        code.value = c
        copied.value = false
        open.value = true
    }

    function close() {
        open.value = false
    }

    async function request() {
        try {
            const response = await fetch('/api/config/auth-code', { method: 'POST' })
            const result = await response.json()
            if (result.code === 200) {
                if (result.data === 'exists') {
                    show({
                        title: '授权码',
                        desc: '授权码已存在。如需重新生成，请删除飞鲸服务端可执行文件所在目录下的 auth_code 文件后再次获取。',
                    })
                } else {
                    show({ title: '授权码', desc: '此授权码只展示一次，请立即复制并妥善保存！', code: result.data })
                }
            } else {
                show({ title: '获取失败', desc: result.msg || '未知错误' })
            }
        } catch (e) {
            // A plain fetch failure means the API never answered — the landing
            // page is most often opened against the static preview without the
            // server, or the server is simply down. Say that plainly instead of
            // dumping the raw TypeError.
            show({
                title: '请求失败',
                desc: '无法连接到飞鲸服务端（/api/config/auth-code）。请确认服务端正在运行后重试；若当前只是静态页面预览，请在服务端启动后再获取授权码。',
            })
        }
    }

    async function copy() {
        if (!code.value) return
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(code.value)
            } else {
                const input = document.getElementById('auth-code-input')
                input.focus()
                input.select()
                document.execCommand('copy')
                input.setSelectionRange(0, 0)
            }
            copied.value = true
        } catch (e) {
            copied.value = false
        }
        setTimeout(() => (copied.value = false), 1200)
    }

    return { open, title, desc, code, copied, request, copy, close }
}
