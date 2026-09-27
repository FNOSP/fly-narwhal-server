<script setup>
import { computed } from 'vue'
import { useContributors } from '../composables/useContributors'

const year = new Date().getFullYear()

const CONTRIBUTORS_PAGE = 'https://github.com/FNOSP/FlyNarwhal/graphs/contributors'
// Avatars beyond this cap collapse into a "+N" chip linking to the full graph.
const AVATAR_CAP = 14

const { contributors } = useContributors()

const visibleContributors = computed(() => contributors.value.slice(0, AVATAR_CAP))
const hiddenCount = computed(() => Math.max(0, contributors.value.length - AVATAR_CAP))

// GitHub 头像 API 支持 s= 参数取指定尺寸；列表接口返回的 URL 已带 ?v=4，
// 这里追加尺寸参数拿到 2x 图，避免在高分屏上发糊。
const avatarSrc = (c) => c.avatar + '&s=80'

const credits = [
    { group: '客户端 · 应用框架', items: [
        { name: 'Flutter', url: 'https://flutter.dev', note: '跨平台 UI 框架' },
        { name: 'fluent_ui', url: 'https://github.com/bdlukaa/fluent_ui', note: 'Windows Fluent Design 风格控件' },
        { name: 'liquid_glass_widgets', url: 'https://github.com/liquid-glass-widgets/liquid_glass_widgets', note: 'Liquid Glass 毛玻璃材质控件' },
        { name: 'flutter_acrylic', url: 'https://github.com/kikuchy/flutter_acrylic', note: '窗口亚克力 / 云母透明效果' },
        { name: 'window_manager', url: 'https://github.com/leanflutter/window_manager', note: '无边框窗口与自绘标题栏' },
        { name: 'canvas_danmaku', url: 'https://github.com/Predidit/canvas_danmaku', note: '弹幕渲染组件' },
        { name: 'lottie', url: 'https://github.com/xvrh/lottie-flutter', note: '矢量动画播放' },
        { name: 'flutter_svg', url: 'https://github.com/dnfield/flutter_svg', note: 'SVG 图标与插图渲染' },
    ]},
    { group: '客户端 · 播放与网络', items: [
        { name: 'media_kit / libmpv', url: 'https://github.com/media-kit/media-kit', note: '跨平台音视频播放方案' },
        { name: 'FFmpeg', url: 'https://ffmpeg.org', note: '解码内核' },
        { name: 'dio', url: 'https://github.com/cfug/dio', note: 'HTTP 客户端' },
        { name: 'Riverpod', url: 'https://github.com/rrousselGit/riverpod', note: '状态管理与依赖注入' },
        { name: 'talker', url: 'https://github.com/Frezyx/talker', note: '日志与网络请求调试' },
        { name: 'shared_preferences', url: 'https://github.com/flutter/packages/tree/main/packages/shared_preferences', note: '本地配置存储' },
        { name: 'cryptography', url: 'https://github.com/dint-dev/cryptography', note: '加解密与摘要算法' },
        { name: 'flutter_inappwebview', url: 'https://github.com/pichillilorenzo/flutter_inappwebview', note: '内嵌网页登录' },
    ]},
    { group: '服务端', items: [
        { name: 'Spring Boot', url: 'https://spring.io/projects/spring-boot', note: '应用框架与内嵌 Web 容器' },
        { name: 'GraalVM Native Image', url: 'https://www.graalvm.org', note: '原生可执行文件编译' },
        { name: 'MyBatis-Plus', url: 'https://github.com/baomidou/mybatis-plus', note: '数据访问层' },
        { name: 'H2 Database', url: 'https://h2database.com', note: '内嵌数据库' },
        { name: 'MapStruct', url: 'https://mapstruct.org', note: '对象映射代码生成' },
        { name: 'Jackson', url: 'https://github.com/FasterXML/jackson', note: 'JSON 序列化' },
        { name: 'Jsoup', url: 'https://jsoup.org', note: 'HTML 解析' },
        { name: 'Brotli', url: 'https://github.com/google/brotli', note: '响应解压' },
        { name: 'intro-skipper', url: 'https://github.com/intro-skipper/intro-skipper', note: '自动检测并跳过片头片尾的 Jellyfin 插件' },
        { name: 'fnos-tv', url: 'https://github.com/thshu/fnos-tv', note: '基于飞牛影视接口开发的网页端，弹幕功能参考' },
    ]},
]
</script>

<template>
    <footer class="footer">
        <div class="shell footer__inner">
            <div class="footer__top">
                <div class="footer__brand">
                    <img
                        class="footer__banner"
                        src="/img/co-brand-banner.svg"
                        alt="飞鲸影视 × XIAOBO NETWORK × 飞牛开发者开放平台 FNOSP"
                        width="1370"
                        height="100"
                    />
                    <p class="footer__tagline">面向飞牛影视服务的第三方桌面客户端</p>
                </div>

                <nav class="footer__links" aria-label="相关链接">
                    <a href="https://github.com/FNOSP/FlyNarwhal" target="_blank" rel="noopener noreferrer">客户端仓库</a>
                    <a href="https://github.com/FNOSP/fly-narwhal-server" target="_blank" rel="noopener noreferrer">服务端仓库</a>
                    <a href="https://github.com/FNOSP/FlyNarwhal/releases" target="_blank" rel="noopener noreferrer">全部版本</a>
                    <a href="https://github.com/FNOSP/FlyNarwhal/issues" target="_blank" rel="noopener noreferrer">问题反馈</a>
                </nav>
            </div>

            <section v-if="contributors.length" class="footer__contributors" aria-label="贡献者">
                <div class="footer__section-head">
                    <h2>贡献者</h2>
                    <a
                        class="footer__more"
                        :href="CONTRIBUTORS_PAGE"
                        target="_blank"
                        rel="noopener noreferrer"
                    >
                        共 {{ contributors.length }} 位 · 贡献图谱 →
                    </a>
                </div>

                <ul class="footer__avatars">
                    <li
                        v-for="c in visibleContributors"
                        :key="c.login"
                        class="footer__avatar"
                        :data-tip="`${c.login} · ${c.commits} 次提交`"
                    >
                        <a :href="c.profile" target="_blank" rel="noopener noreferrer" :aria-label="c.login">
                            <img :src="avatarSrc(c)" :alt="c.login" width="40" height="40" loading="lazy" />
                        </a>
                    </li>
                    <li
                        v-if="hiddenCount > 0"
                        class="footer__avatar footer__avatar--more"
                        :data-tip="`还有 ${hiddenCount} 位贡献者`"
                    >
                        <a :href="CONTRIBUTORS_PAGE" target="_blank" rel="noopener noreferrer" aria-label="查看全部贡献者">
                            +{{ hiddenCount }}
                        </a>
                    </li>
                </ul>
            </section>

            <section class="footer__credits">
                <span class="footer__credits-label">本项目参考或使用以下开源项目</span>
                <div class="footer__credit-groups">
                    <div v-for="g in credits" :key="g.group" class="footer__credit-group">
                        <span class="footer__credit-group-title">{{ g.group }}</span>
                        <ul>
                            <li v-for="c in g.items" :key="c.name">
                                <a :href="c.url" target="_blank" rel="noopener noreferrer">{{ c.name }}</a>
                                <span>{{ c.note }}</span>
                            </li>
                        </ul>
                    </div>
                </div>
            </section>

            <div class="footer__legal">
                <p>本项目为飞牛 OS 爱好者开发的第三方影视客户端，与飞牛影视官方无关。使用前请确保遵守相关服务条款。</p>
                <p class="footer__copy">© {{ year }} FNOSP · 基于 AGPL-3.0 开源协议发布</p>
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
    gap: 40px;
}

/* ---------- brand + links ---------- */

.footer__top {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 24px 40px;
}

.footer__brand {
    display: grid;
    gap: 12px;
    justify-items: start;
}

.footer__banner {
    width: min(430px, 80vw);
    height: auto;
}

.footer__tagline {
    font-size: 13.5px;
}

.footer__links {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 36px;
    padding-top: 6px;
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

/* ---------- shared section chrome ---------- */

.footer__section-head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;
}

.footer__section-head h2 {
    font-size: 15px;
    font-weight: 650;
    color: var(--ink);
    letter-spacing: 0.01em;
}

.footer__more {
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    color: var(--ink-muted);
    transition: color 0.22s var(--ease);
}

.footer__more:hover {
    color: var(--brand);
}

/* ---------- contributors ---------- */

.footer__contributors {
    padding-top: 36px;
    border-top: 1px solid var(--hairline);
}

.footer__avatars {
    list-style: none;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    row-gap: 12px;
}

.footer__avatar {
    position: relative;
    margin-left: -10px;
}

.footer__avatar:first-child {
    margin-left: 0;
}

.footer__avatar a {
    display: block;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    /* The ring matches the footer surface so overlapping circles read as separate chips. */
    box-shadow: 0 0 0 2.5px #F5F5F7;
    transition: transform 0.22s var(--ease), box-shadow 0.22s var(--ease);
}

.footer__avatar img {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
}

.footer__avatar:hover {
    z-index: 2;
}

.footer__avatar:hover a {
    transform: translateY(-4px) scale(1.12);
    box-shadow: 0 0 0 2.5px #F5F5F7, 0 10px 22px rgba(0, 0, 0, 0.16);
}

.footer__avatar--more a {
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--brand-tint);
    color: var(--brand-deep);
    font-size: 12.5px;
    font-weight: 650;
}

/* Pure-CSS tooltip: the chip carries its data in data-tip so no extra markup or
   JS state is needed for hover info. */
.footer__avatar::after {
    content: attr(data-tip);
    position: absolute;
    bottom: calc(100% + 10px);
    left: 50%;
    translate: -50% 4px;
    padding: 5px 10px;
    border-radius: 8px;
    background: var(--ink);
    color: #fff;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.35;
    white-space: nowrap;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.18s var(--ease), translate 0.18s var(--ease);
    z-index: 3;
}

.footer__avatar:hover::after {
    opacity: 1;
    translate: -50% 0;
}

/* ---------- credits ---------- */

.footer__credits {
    padding-top: 36px;
    border-top: 1px solid var(--hairline);
    font-size: 13.5px;
    line-height: 1.75;
    /* Container-driven, not viewport-driven: the side-by-side switch follows the
       actual width of the credits area, which the footer grid may narrow. */
    container-type: inline-size;
}

.footer__credits-label {
    display: block;
    color: var(--ink);
    font-size: 15px;
    font-weight: 650;
    margin-bottom: 14px;
}

.footer__credit-groups {
    display: grid;
    gap: 18px;
}

@container (min-width: 820px) {
    .footer__credit-groups {
        /* The client has far more entries than the server, so its two categories
           take the first two tracks and the server takes the remaining two. */
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 0 36px;
    }

    .footer__credit-group:nth-child(1) { grid-column: 1; grid-row: 1; }
    .footer__credit-group:nth-child(2) { grid-column: 2; grid-row: 1; }
    .footer__credit-group:nth-child(3) { grid-column: 3 / 5; grid-row: 1; }
}

.footer__credits ul {
    list-style: none;
    display: grid;
    gap: 8px;
}

.footer__credit-group-title {
    display: block;
    color: var(--ink-muted);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.05em;
    margin-bottom: 6px;
}

.footer__credits a {
    color: var(--ink-soft);
    font-weight: 500;
}

.footer__credits a:hover {
    color: var(--brand);
}

/* Only the note spans inside list items get the em-dash lead-in; the heading and
   the group titles are spans too, and the label must not be prefixed. */
.footer__credits li span::before {
    content: ' — ';
    opacity: 0.6;
}

/* ---------- legal ---------- */

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

@media (max-width: 640px) {
    .footer__links {
        width: 100%;
    }
}
</style>
