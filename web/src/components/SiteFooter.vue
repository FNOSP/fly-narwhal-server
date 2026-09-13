<script setup>
const year = new Date().getFullYear()

const credits = [
    { name: 'intro-skipper', url: 'https://github.com/intro-skipper/intro-skipper', note: '自动检测并跳过片头片尾的 Jellyfin 插件' },
    { name: 'fnos-tv', url: 'https://github.com/thshu/fnos-tv', note: '基于飞牛影视接口开发的网页端' },
    { name: 'media_kit / libmpv', url: 'https://github.com/media-kit/media-kit', note: '跨平台音视频播放方案' },
    { name: 'Font Awesome Free', url: 'https://fontawesome.com/license/free', note: 'Windows / Linux 品牌图标（CC BY 4.0）' },
]
</script>

<template>
    <footer class="footer">
        <div class="shell footer__inner">
            <div class="footer__brand">
                <img
                    class="footer__banner"
                    src="/img/fly_narwhal_banner.svg"
                    alt="飞鲸影视 × 飞牛开发者开放平台 FNOSP"
                    width="753"
                    height="80"
                />
                <p class="footer__tagline">面向飞牛影视服务的第三方桌面客户端</p>
            </div>

            <nav class="footer__links" aria-label="相关链接">
                <a href="https://github.com/FNOSP/FlyNarwhal" target="_blank" rel="noopener noreferrer">客户端仓库</a>
                <a href="https://github.com/FNOSP/fly-narwhal-server" target="_blank" rel="noopener noreferrer">服务端仓库</a>
                <a href="https://github.com/FNOSP/FlyNarwhal/releases" target="_blank" rel="noopener noreferrer">全部版本</a>
                <a href="https://github.com/FNOSP/FlyNarwhal/issues" target="_blank" rel="noopener noreferrer">问题反馈</a>
            </nav>

            <div class="footer__credits">
                <span class="footer__credits-label">致谢</span>
                <ul>
                    <li v-for="c in credits" :key="c.name">
                        <a :href="c.url" target="_blank" rel="noopener noreferrer">{{ c.name }}</a>
                        <span>{{ c.note }}</span>
                    </li>
                </ul>
            </div>

            <div class="footer__legal">
                <p>本项目为飞牛 OS 爱好者开发的第三方影视客户端，与飞牛影视官方无关。使用前请确保遵守相关服务条款。</p>
                <p class="footer__copy">© {{ year }} FNOSP · 基于开源协议发布</p>
            </div>
        </div>
    </footer>
</template>

<style scoped>
.footer {
    padding: clamp(56px, 7vw, 84px) 0 42px;
    /* Light surface on purpose: the brand banner is authored with white plates and
       dark ink for light backgrounds. Putting it on a dark footer is what made the
       plates read as a solid white block. */
    background: #F5F5F7;
    border-top: 1px solid var(--hairline);
    color: var(--ink-muted);
}

.footer__inner {
    display: grid;
    gap: 34px;
}

.footer__brand {
    display: grid;
    gap: 14px;
    justify-items: start;
}

.footer__banner {
    width: min(340px, 74vw);
    height: auto;
}

.footer__tagline {
    font-size: 13.5px;
}

.footer__links {
    display: flex;
    flex-wrap: wrap;
    gap: 12px 28px;
    padding-bottom: 30px;
    border-bottom: 1px solid var(--hairline);
}

.footer__links a {
    color: var(--ink-soft);
    font-size: 14.5px;
    font-weight: 500;
    transition: color 0.22s var(--ease);
}

.footer__links a:hover {
    color: var(--brand);
}

.footer__credits {
    font-size: 13.5px;
    line-height: 1.75;
}

.footer__credits-label {
    display: block;
    color: var(--ink);
    font-weight: 600;
    margin-bottom: 12px;
}

.footer__credits ul {
    list-style: none;
    display: grid;
    gap: 8px;
}

.footer__credits a {
    color: var(--ink-soft);
    font-weight: 500;
}

.footer__credits a:hover {
    color: var(--brand);
}

.footer__credits span::before {
    content: ' — ';
    opacity: 0.6;
}

.footer__legal {
    padding-top: 28px;
    border-top: 1px solid var(--hairline);
    font-size: 12.5px;
    line-height: 1.75;
}

.footer__copy {
    margin-top: 10px;
    opacity: 0.7;
}

@media (max-width: 560px) {
    .footer__links {
        gap: 12px 20px;
    }
}
</style>
