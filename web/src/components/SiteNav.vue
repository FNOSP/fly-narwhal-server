<script setup>
defineProps({
    solid: { type: Boolean, default: false },
    // Whole-page reading progress, 0..1, drawn as a hairline under the nav.
    progress: { type: Number, default: 0 },
})

const emit = defineEmits(['download', 'auth'])

const links = [
    { href: '#renew', label: '2.0 焕新' },
    { href: '#screenshots', label: '界面预览' },
    { href: '#features', label: '核心特性' },
    { href: '#player', label: '播放内核' },
    { href: '#server', label: '服务端' },
    { href: '#changelog', label: '更新日志' },
    { href: '#faq', label: '常见问题' },
]
</script>

<template>
    <header class="nav" :class="{ 'nav--solid': solid }">
        <div class="nav__inner shell">
            <a class="nav__brand" href="#top" aria-label="飞鲸影视首页">
                <img src="/img/FNarwhal_login.svg" alt="飞鲸影视" class="nav__logo" />
            </a>

            <nav class="nav__links" aria-label="页面导航">
                <a v-for="link in links" :key="link.href" :href="link.href" class="nav__link">
                    {{ link.label }}
                </a>
            </nav>

            <div class="nav__actions">
                <button class="btn btn-ghost nav__auth" type="button" @click="emit('auth')">获取授权码</button>
                <button class="btn btn-primary" type="button" @click="emit('download')">下载</button>
            </div>
        </div>

        <div class="nav__progress" aria-hidden="true">
            <div class="nav__progress-fill" :style="{ transform: `scaleX(${progress})` }"></div>
        </div>
    </header>
</template>

<style scoped>
.nav {
    position: fixed;
    inset: 0 0 auto 0;
    z-index: 100;
    height: var(--nav-h);
    display: flex;
    align-items: center;
    /* Fades in as the hero scrolls away; the buttons themselves never hide. */
    background: rgba(245, 245, 247, 0);
    border-bottom: 1px solid transparent;
    transition: background-color 0.35s var(--ease), border-color 0.35s var(--ease),
        backdrop-filter 0.35s var(--ease);
}

.nav--solid {
    background: rgba(245, 245, 247, 0.78);
    border-bottom-color: var(--hairline);
    backdrop-filter: saturate(180%) blur(20px);
    -webkit-backdrop-filter: saturate(180%) blur(20px);
}

.nav__inner {
    display: flex;
    align-items: center;
    gap: 20px;
    width: 100%;
}

.nav__brand {
    display: flex;
    align-items: center;
    flex-shrink: 0;
}

.nav__logo {
    height: 30px;
    width: auto;
}

.nav__links {
    display: flex;
    align-items: center;
    gap: 20px;
    margin-left: 14px;
}

.nav__link {
    font-size: 13.5px;
    font-weight: 500;
    color: var(--ink-soft);
    transition: color 0.2s var(--ease);
    white-space: nowrap;
}

.nav__link:hover {
    color: var(--brand);
}

.nav__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    /* Pins the action buttons to the right edge, per the floating-button spec. */
    margin-left: auto;
}

.nav__actions .btn {
    padding: 9px 18px;
    font-size: 14px;
}

/* Reading-progress hairline pinned to the nav's bottom edge. */
.nav__progress {
    position: absolute;
    inset: auto 0 0 0;
    height: 2px;
    opacity: 0;
    transition: opacity 0.35s var(--ease);
}

.nav--solid .nav__progress {
    opacity: 1;
}

.nav__progress-fill {
    height: 100%;
    transform-origin: 0 50%;
    background: linear-gradient(90deg, var(--brand), #5E5CE6 70%, #BF5AF2);
    will-change: transform;
}

@media (max-width: 1140px) {
    .nav__links {
        display: none;
    }
}

@media (max-width: 520px) {
    .nav__logo {
        height: 24px;
    }

    .nav__actions .btn {
        padding: 8px 13px;
        font-size: 13px;
    }

    .nav__inner {
        gap: 10px;
        padding: 0 14px;
    }
}

@media (max-width: 380px) {
    .nav__brand {
        display: none;
    }

    .nav__actions {
        width: 100%;
        justify-content: flex-end;
    }
}
</style>
