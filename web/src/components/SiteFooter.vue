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

// External repo links and the two internal hash-route pages, kept in one list
// so the nav grid renders in a stable order.
const links = [
    { label: '客户端仓库', href: 'https://github.com/FNOSP/FlyNarwhal', external: true },
    { label: '服务端仓库', href: 'https://github.com/FNOSP/fly-narwhal-server', external: true },
    { label: '全部版本', href: 'https://github.com/FNOSP/FlyNarwhal/releases', external: true },
    { label: '更新日志', href: '#/timeline' },
    { label: '问题反馈', href: 'https://github.com/FNOSP/FlyNarwhal/issues', external: true },
    { label: '开源致谢', href: '#/credits' },
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
                    <a
                        v-for="l in links"
                        :key="l.label"
                        :href="l.href"
                        v-bind="l.external ? { target: '_blank', rel: 'noopener noreferrer' } : {}"
                    >{{ l.label }}</a>
                </nav>
            </div>

            <section v-if="contributors.length" class="footer__contributors" aria-label="贡献者">
                <h2 class="footer__contributors-label">贡献者</h2>
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
                <a
                    class="footer__more"
                    :href="CONTRIBUTORS_PAGE"
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    共 {{ contributors.length }} 位 · 贡献图谱 →
                </a>
            </section>

            <div class="footer__bottom">
                <p class="footer__disclaimer">本项目为飞牛 OS 爱好者开发的第三方影视客户端，与飞牛影视官方无关。使用前请确保遵守相关服务条款。</p>
                <p class="footer__copy">© {{ year }} FNOSP · 基于 AGPL-3.0 开源协议发布</p>
            </div>
        </div>
    </footer>
</template>

<style scoped>
.footer {
    padding: clamp(44px, 5vw, 60px) 0 34px;
    /* Light surface on purpose: the brand banner is authored with white plates and
       dark ink for light backgrounds. Putting it on a dark footer is what made the
       plates read as a solid white block. */
    background: #F5F5F7;
    border-top: 1px solid var(--hairline);
    color: var(--ink-muted);
}

.footer__inner {
    display: grid;
    gap: 30px;
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
    gap: 11px 40px;
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

/* ---------- contributors (single-line strip) ---------- */

.footer__contributors {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 20px;
    padding-top: 26px;
    border-top: 1px solid var(--hairline);
}

.footer__contributors-label {
    font-size: 13px;
    font-weight: 650;
    letter-spacing: 0.04em;
    color: var(--ink);
}

.footer__avatars {
    list-style: none;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    row-gap: 10px;
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
    width: 36px;
    height: 36px;
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
    font-size: 12px;
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

.footer__more {
    margin-left: auto;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    color: var(--ink-muted);
    transition: color 0.22s var(--ease);
}

.footer__more:hover {
    color: var(--brand);
}

/* ---------- bottom bar ---------- */

.footer__bottom {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px 32px;
    padding-top: 22px;
    border-top: 1px solid var(--hairline);
    font-size: 12.5px;
    line-height: 1.7;
}

.footer__disclaimer {
    flex: 1 1 380px;
    min-width: 0;
}

.footer__copy {
    opacity: 0.7;
    white-space: nowrap;
}

@media (max-width: 640px) {
    .footer__links {
        width: 100%;
    }

    .footer__more {
        margin-left: 0;
        width: 100%;
    }

    .footer__copy {
        white-space: normal;
    }
}
</style>
