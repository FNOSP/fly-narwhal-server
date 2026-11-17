<script setup>
import { computed, onMounted, ref } from 'vue'
import ThemeToggle from '../ThemeToggle.vue'
import LocaleToggle from '../LocaleToggle.vue'
import AuthCodeModal from '../AuthCodeModal.vue'
import { useI18n } from '../../composables/useI18n'
import { useAdminApi, AdminApiError } from '../../composables/useAdminApi'

const { t } = useI18n()
const api = useAdminApi()

const session = ref(null)
const sessionError = ref(null)
const codes = ref([])
const logs = ref([])
const overview = ref(null)
const loading = ref(false)
const actionError = ref('')
const tab = ref('codes')

// Create dialog state
const createOpen = ref(false)
const createName = ref('')
const createExpiry = ref('30')
const createCustomDays = ref('90')
const createScope = ref('READ_WRITE')
const creating = ref(false)

// Show-once modal state (reuses AuthCodeModal)
const onceOpen = ref(false)
const onceCode = ref('')
const onceCopied = ref(false)

// Delete confirmation
const deleteTarget = ref(null)

const expiryOptions = computed(() => [
    { value: '7', label: t.value.admin.expiry7 },
    { value: '30', label: t.value.admin.expiry30 },
    { value: '90', label: t.value.admin.expiry90 },
    { value: '365', label: t.value.admin.expiry365 },
    { value: 'custom', label: t.value.admin.expiryCustom },
    { value: 'permanent', label: t.value.admin.expiryPermanent },
])

function fmtTime(value) {
    if (!value) return '—'
    const d = new Date(value)
    if (Number.isNaN(d.getTime())) return String(value)
    return d.toLocaleString()
}

function codeStatus(code) {
    if (code.status === 'DISABLED') return t.value.admin.statusDisabled
    if (!code.permanent && code.expireTime && new Date(code.expireTime).getTime() < Date.now()) {
        return t.value.admin.statusExpired
    }
    return t.value.admin.statusActive
}

async function loadAll() {
    loading.value = true
    actionError.value = ''
    try {
        const [codeList, logList, ov] = await Promise.all([
            api.listCodes(),
            api.accessLogs(null, 200),
            api.overview(),
        ])
        codes.value = codeList || []
        logs.value = logList || []
        overview.value = ov
    } catch (e) {
        actionError.value = e.message
    } finally {
        loading.value = false
    }
}

onMounted(async () => {
    try {
        session.value = await api.session()
        await loadAll()
    } catch (e) {
        sessionError.value = e
    }
})

async function run(action) {
    actionError.value = ''
    try {
        await action()
        await loadAll()
    } catch (e) {
        actionError.value = e.message
    }
}

function openCreate() {
    createName.value = ''
    createExpiry.value = '30'
    createCustomDays.value = '90'
    createScope.value = 'READ_WRITE'
    createOpen.value = true
}

async function submitCreate() {
    const name = createName.value.trim()
    if (!name) return
    let days = null
    if (createExpiry.value === 'custom') {
        days = parseInt(createCustomDays.value, 10)
        if (!Number.isFinite(days) || days < 1) return
    } else if (createExpiry.value !== 'permanent') {
        days = parseInt(createExpiry.value, 10)
    }
    creating.value = true
    try {
        const created = await api.createCode(name, days, createScope.value)
        createOpen.value = false
        onceCode.value = created.code
        onceCopied.value = false
        onceOpen.value = true
        await loadAll()
    } catch (e) {
        actionError.value = e.message
    } finally {
        creating.value = false
    }
}

async function copyOnce() {
    try {
        await navigator.clipboard.writeText(onceCode.value)
        onceCopied.value = true
        setTimeout(() => (onceCopied.value = false), 1200)
    } catch (e) {
        onceCopied.value = false
    }
}

function closeOnce() {
    onceOpen.value = false
    onceCode.value = ''
}

const toggleLegacy = () => run(async () => {
    const next = !(overview.value && overview.value.legacyAuthEnabled)
    if (!next && !window.confirm(t.value.admin.legacyDisableWarning)) return
    await api.setLegacyAuth(next)
})
</script>

<template>
    <div class="adpage">
        <header class="adpage__bar shell">
            <a class="adpage__back" href="#top">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M19 12H5M11 18l-6-6 6-6" />
                </svg>
                {{ t.common.backHome }}
            </a>
            <div class="adpage__actions">
                <span class="adpage__crumb">{{ t.admin.crumb }}</span>
                <LocaleToggle />
                <ThemeToggle />
            </div>
        </header>

        <main class="shell adpage__main">
            <div class="adpage__head">
                <p class="eyebrow">{{ t.admin.eyebrow }}</p>
                <h1 class="adpage__title">{{ t.admin.title }}</h1>
                <p class="adpage__lede">{{ t.admin.lede }}</p>
            </div>

            <!-- Gate: not opened through the gateway, or not an admin -->
            <section v-if="sessionError" class="adpage__gate">
                <p class="adpage__gate-text">
                    {{ sessionError.kind === 'not-admin' ? t.admin.notAdmin : t.admin.notGateway }}
                </p>
            </section>

            <template v-else-if="session">
                <p class="adpage__session">{{ t.admin.signedInAs }} <strong>{{ session.username }}</strong></p>

                <p v-if="actionError" class="adpage__error">{{ actionError }}</p>

                <!-- Overview / legacy switch -->
                <section v-if="overview" class="adpage__overview">
                    <div class="adpage__stat">
                        <span class="adpage__stat-num">{{ overview.activeCodes }} / {{ overview.totalCodes }}</span>
                        <span class="adpage__stat-label">{{ t.admin.activeCodes }}</span>
                    </div>
                    <div class="adpage__stat" :class="{ 'adpage__stat--warn': overview.legacyRequests24h > 0 }">
                        <span class="adpage__stat-num">{{ overview.legacyRequests24h }}</span>
                        <span class="adpage__stat-label">{{ t.admin.legacyRequests24h }}（{{ overview.legacyDistinctIps24h }} IP）</span>
                    </div>
                    <label class="adpage__legacy">
                        <input type="checkbox" :checked="overview.legacyAuthEnabled" @change="toggleLegacy" />
                        <span>{{ t.admin.legacyToggle }}</span>
                    </label>
                </section>

                <div class="adpage__tabs">
                    <button type="button" :class="{ active: tab === 'codes' }" @click="tab = 'codes'">{{ t.admin.tabCodes }}</button>
                    <button type="button" :class="{ active: tab === 'access' }" @click="tab = 'access'">{{ t.admin.tabAccess }}</button>
                </div>

                <!-- Codes panel -->
                <section v-if="tab === 'codes'" class="adpage__panel">
                    <div class="adpage__panel-head">
                        <h2>{{ t.admin.codesTitle }}</h2>
                        <button class="btn btn-primary" type="button" @click="openCreate">{{ t.admin.createCode }}</button>
                    </div>
                    <p v-if="loading" class="adpage__muted">{{ t.admin.loading }}</p>
                    <p v-else-if="codes.length === 0" class="adpage__muted">{{ t.admin.noCodes }}</p>
                    <div v-else class="adpage__table-wrap">
                        <table class="adpage__table">
                            <thead>
                                <tr>
                                    <th>{{ t.admin.colName }}</th>
                                    <th>{{ t.admin.colScope }}</th>
                                    <th>{{ t.admin.colStatus }}</th>
                                    <th>{{ t.admin.colExpiry }}</th>
                                    <th>{{ t.admin.colDevice }}</th>
                                    <th>{{ t.admin.colLastUsed }}</th>
                                    <th>{{ t.admin.colActions }}</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="code in codes" :key="code.id">
                                    <td class="adpage__name">{{ code.name }}</td>
                                    <td>
                                        <span class="adpage__badge" :class="code.scope === 'READ_ONLY' ? 'adpage__badge--ro' : 'adpage__badge--rw'">
                                            {{ code.scope === 'READ_ONLY' ? t.admin.scopeReadOnly : t.admin.scopeReadWrite }}
                                        </span>
                                    </td>
                                    <td>{{ codeStatus(code) }}</td>
                                    <td>{{ code.permanent ? t.admin.expiryPermanent : fmtTime(code.expireTime) }}</td>
                                    <td>{{ code.boundDeviceLabel || code.boundDeviceId || t.admin.unbound }}</td>
                                    <td>
                                        <template v-if="code.lastUsedTime">{{ fmtTime(code.lastUsedTime) }}<br /><span class="adpage__muted">{{ code.lastUsedIp }}</span></template>
                                        <template v-else>—</template>
                                    </td>
                                    <td class="adpage__actions">
                                        <button type="button" @click="run(() => api.setStatus(code.id, code.status === 'DISABLED'))">
                                            {{ code.status === 'DISABLED' ? t.admin.enable : t.admin.disable }}
                                        </button>
                                        <button type="button" @click="run(() => api.setScope(code.id, code.scope === 'READ_ONLY' ? 'READ_WRITE' : 'READ_ONLY'))">
                                            {{ code.scope === 'READ_ONLY' ? t.admin.makeReadWrite : t.admin.makeReadOnly }}
                                        </button>
                                        <button type="button" :disabled="!code.boundDeviceId" @click="run(() => api.unbind(code.id))">{{ t.admin.unbind }}</button>
                                        <button type="button" class="adpage__danger" @click="deleteTarget = code">{{ t.admin.delete }}</button>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </section>

                <!-- Access panel -->
                <section v-else class="adpage__panel">
                    <div class="adpage__panel-head">
                        <h2>{{ t.admin.accessTitle }}</h2>
                        <button class="btn" type="button" @click="loadAll">{{ t.admin.refresh }}</button>
                    </div>
                    <p v-if="logs.length === 0" class="adpage__muted">{{ t.admin.noLogs }}</p>
                    <div v-else class="adpage__table-wrap">
                        <table class="adpage__table">
                            <thead>
                                <tr>
                                    <th>{{ t.admin.colTime }}</th>
                                    <th>{{ t.admin.colClient }}</th>
                                    <th>{{ t.admin.colIp }}</th>
                                    <th>{{ t.admin.colVersion }}</th>
                                    <th>{{ t.admin.colEvent }}</th>
                                    <th>{{ t.admin.colHits }}</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="log in logs" :key="log.id"
                                    :class="{ 'adpage__row--warn': log.event === 'DEVICE_MISMATCH' || log.event === 'READ_ONLY_DENIED' || log.event === 'BAD_SIGN' }">
                                    <td>{{ fmtTime(log.lastTime) }}</td>
                                    <td>
                                        <template v-if="log.authMode === 'LEGACY'">
                                            <span class="adpage__badge adpage__badge--legacy">{{ t.admin.legacyBadge }}</span>
                                        </template>
                                        <template v-else>{{ log.codeName || t.admin.unknownCode }}</template>
                                    </td>
                                    <td>{{ log.clientIp }}</td>
                                    <td>{{ log.clientVersion || '—' }}</td>
                                    <td>{{ t.admin.events[log.event] || log.event }}</td>
                                    <td>{{ log.hitCount }}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </section>
            </template>
        </main>

        <!-- Create dialog -->
        <Teleport to="body">
            <Transition name="modal">
                <div v-if="createOpen" class="overlay" @click.self="createOpen = false">
                    <div class="modal" role="dialog" aria-modal="true">
                        <header class="modal__head">
                            <h2 class="modal__title">{{ t.admin.createCode }}</h2>
                        </header>
                        <div class="modal__body adpage__form">
                            <label class="adpage__field">
                                <span>{{ t.admin.nameLabel }}</span>
                                <input v-model="createName" type="text" maxlength="128" :placeholder="t.admin.namePlaceholder" />
                            </label>
                            <label class="adpage__field">
                                <span>{{ t.admin.expiryLabel }}</span>
                                <select v-model="createExpiry">
                                    <option v-for="opt in expiryOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                                </select>
                            </label>
                            <label v-if="createExpiry === 'custom'" class="adpage__field">
                                <span>{{ t.admin.customDaysLabel }}</span>
                                <input v-model="createCustomDays" type="number" min="1" max="36500" />
                            </label>
                            <label class="adpage__field">
                                <span>{{ t.admin.scopeLabel }}</span>
                                <select v-model="createScope">
                                    <option value="READ_WRITE">{{ t.admin.scopeReadWrite }}</option>
                                    <option value="READ_ONLY">{{ t.admin.scopeReadOnly }}</option>
                                </select>
                            </label>
                            <p class="adpage__muted">{{ t.admin.scopeHint }}</p>
                            <div class="adpage__form-actions">
                                <button class="btn" type="button" @click="createOpen = false">{{ t.admin.cancel }}</button>
                                <button class="btn btn-primary" type="button" :disabled="creating || !createName.trim()" @click="submitCreate">
                                    {{ creating ? t.admin.creating : t.admin.create }}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </Transition>
        </Teleport>

        <!-- Delete confirmation -->
        <Teleport to="body">
            <Transition name="modal">
                <div v-if="deleteTarget" class="overlay" @click.self="deleteTarget = null">
                    <div class="modal" role="dialog" aria-modal="true">
                        <header class="modal__head">
                            <h2 class="modal__title">{{ t.admin.delete }}</h2>
                        </header>
                        <div class="modal__body">
                            <p class="modal__desc">{{ t.admin.deleteConfirm.replace('{name}', deleteTarget.name) }}</p>
                            <div class="adpage__form-actions">
                                <button class="btn" type="button" @click="deleteTarget = null">{{ t.admin.cancel }}</button>
                                <button class="btn adpage__danger-btn" type="button"
                                    @click="run(() => api.deleteCode(deleteTarget.id)).then(() => { deleteTarget = null })">
                                    {{ t.admin.delete }}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </Transition>
        </Teleport>

        <!-- Show-once code modal -->
        <AuthCodeModal
            :open="onceOpen"
            :title="t.admin.createdTitle"
            :desc="t.authCode.once"
            :code="onceCode"
            :copied="onceCopied"
            @close="closeOnce"
            @copy="copyOnce"
        />
    </div>
</template>

<style scoped>
.adpage {
    min-height: 100vh;
}

.adpage__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 18px;
}

.adpage__back {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13.5px;
    color: var(--ink-muted);
}

.adpage__back:hover {
    color: var(--ink);
}

.adpage__actions {
    display: flex;
    align-items: center;
    gap: 10px;
}

.adpage__crumb {
    font-size: 12.5px;
    color: var(--ink-muted);
}

.adpage__main {
    padding: 34px 0 80px;
}

.adpage__title {
    font-size: 30px;
    font-weight: 700;
    margin: 6px 0 10px;
}

.adpage__lede {
    color: var(--ink-muted);
    font-size: 14px;
    line-height: 1.7;
    max-width: 720px;
}

.adpage__gate {
    margin-top: 30px;
    padding: 26px;
    border: 1px solid var(--hairline);
    border-radius: var(--radius-md);
    background: var(--surface);
}

.adpage__gate-text {
    font-size: 14.5px;
    line-height: 1.7;
}

.adpage__session {
    margin-top: 18px;
    font-size: 13px;
    color: var(--ink-muted);
}

.adpage__error {
    margin-top: 14px;
    padding: 12px 16px;
    border-radius: var(--radius-sm);
    background: rgba(255, 59, 48, 0.1);
    color: #d70015;
    font-size: 13.5px;
}

.adpage__overview {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 22px;
    margin-top: 20px;
    padding: 18px 22px;
    border: 1px solid var(--hairline);
    border-radius: var(--radius-md);
    background: var(--surface);
}

.adpage__stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.adpage__stat-num {
    font-size: 20px;
    font-weight: 700;
}

.adpage__stat--warn .adpage__stat-num {
    color: #c93400;
}

.adpage__stat-label {
    font-size: 12px;
    color: var(--ink-muted);
}

.adpage__legacy {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;
    font-size: 13px;
    cursor: pointer;
}

.adpage__tabs {
    display: flex;
    gap: 8px;
    margin-top: 26px;
}

.adpage__tabs button {
    padding: 9px 18px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--hairline);
    background: var(--surface);
    font-size: 13.5px;
    font-weight: 600;
    color: var(--ink-muted);
}

.adpage__tabs button.active {
    background: var(--brand);
    border-color: var(--brand);
    color: #fff;
}

.adpage__panel {
    margin-top: 18px;
    padding: 22px;
    border: 1px solid var(--hairline);
    border-radius: var(--radius-md);
    background: var(--surface);
}

.adpage__panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
}

.adpage__panel-head h2 {
    font-size: 16px;
    font-weight: 650;
}

.adpage__muted {
    color: var(--ink-muted);
    font-size: 13px;
}

.adpage__table-wrap {
    overflow-x: auto;
}

.adpage__table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
}

.adpage__table th {
    text-align: left;
    padding: 8px 10px;
    color: var(--ink-muted);
    font-weight: 600;
    border-bottom: 1px solid var(--hairline);
    white-space: nowrap;
}

.adpage__table td {
    padding: 10px;
    border-bottom: 1px solid var(--hairline);
    vertical-align: top;
}

.adpage__name {
    font-weight: 600;
}

.adpage__badge {
    display: inline-block;
    padding: 2px 9px;
    border-radius: 999px;
    font-size: 11.5px;
    font-weight: 600;
}

.adpage__badge--rw {
    background: var(--brand-tint);
    color: var(--brand-deep);
}

.adpage__badge--ro {
    background: rgba(255, 149, 0, 0.14);
    color: #c93400;
}

.adpage__badge--legacy {
    background: rgba(255, 59, 48, 0.12);
    color: #d70015;
}

.adpage__row--warn td {
    background: rgba(255, 149, 0, 0.06);
}

.adpage__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.adpage__actions button {
    padding: 5px 10px;
    border-radius: 8px;
    border: 1px solid var(--hairline);
    background: var(--surface-2);
    font-size: 12px;
    white-space: nowrap;
}

.adpage__actions button:disabled {
    opacity: 0.4;
}

.adpage__danger {
    color: #d70015;
}

.adpage__danger-btn {
    background: #d70015;
    border-color: #d70015;
    color: #fff;
}

.adpage__form {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.adpage__field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
}

.adpage__field input,
.adpage__field select {
    padding: 10px 12px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--hairline);
    background: var(--input-bg);
    color: var(--ink);
    font-size: 13.5px;
    outline: none;
}

.adpage__field input:focus,
.adpage__field select:focus {
    border-color: var(--brand);
}

.adpage__form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 6px;
}

.overlay {
    position: fixed;
    inset: 0;
    z-index: 9999;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
    background: rgba(0, 0, 0, 0.4);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
}

.modal {
    width: 100%;
    max-width: 480px;
    background: var(--surface);
    border-radius: var(--radius-md);
    border: 1px solid var(--hairline);
    box-shadow: 0 24px 70px rgba(0, 0, 0, 0.28);
    overflow: hidden;
}

.modal__head {
    padding: 18px 20px 0;
}

.modal__title {
    font-size: 16.5px;
    font-weight: 650;
}

.modal__body {
    padding: 14px 20px 22px;
}

.modal__desc {
    font-size: 13.5px;
    line-height: 1.7;
    color: var(--ink-muted);
    margin-bottom: 14px;
}

.modal-enter-active,
.modal-leave-active {
    transition: opacity 0.25s var(--ease);
}

.modal-enter-from,
.modal-leave-to {
    opacity: 0;
}
</style>