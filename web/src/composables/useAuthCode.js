import { ref } from 'vue'
import { useI18n } from './useI18n'

/**
 * Auth-code retrieval. The endpoint returns the code exactly once: a later call
 * reports that a code already exists and must be reset by deleting the
 * `auth_code` file next to the server binary.
 *
 * Messages are resolved from the catalog when a request completes, so the text
 * follows the language active at that moment. The modal is a one-shot: an open
 * dialog keeps the wording it was shown with, which is fine for a code the
 * visitor copies immediately.
 */
export function useAuthCode() {
    const { t } = useI18n()
    const open = ref(false)
    const title = ref('')
    const desc = ref('')
    const code = ref('')
    const copied = ref(false)

    function show({ title: tt, desc: d, code: c = '' }) {
        title.value = tt
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
                        title: t.value.authCode.title,
                        desc: t.value.authCode.exists,
                    })
                } else {
                    show({ title: t.value.authCode.title, desc: t.value.authCode.once, code: result.data })
                }
            } else {
                show({ title: t.value.authCode.fetchFailed, desc: result.msg || t.value.authCode.unknownError })
            }
        } catch (e) {
            // A plain fetch failure means the API never answered — the landing
            // page is most often opened against the static preview without the
            // server, or the server is simply down. Say that plainly instead of
            // dumping the raw TypeError.
            show({
                title: t.value.authCode.requestFailed,
                desc: t.value.authCode.connectFailed,
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
