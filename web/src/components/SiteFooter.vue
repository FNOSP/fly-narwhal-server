<script setup>
import { computed } from 'vue'
import bannerSvg from '../assets/co-brand-banner.svg?raw'
import { useContributors } from '../composables/useContributors'
import { useI18n, format } from '../composables/useI18n'

const { t } = useI18n()

const year = new Date().getFullYear()

// Avatars beyond this cap collapse into a "+N" chip linking to the full graph.
const AVATAR_CAP = 14

const { groups, labels } = useContributors()

// GitHub 头像 API 支持 s= 参数取指定尺寸；列表接口返回的 URL 已带 ?v=4，
// 这里追加尺寸参数拿到 2x 图，避免在高分屏上发糊。
const avatarSrc = (c) => c.avatar + '&s=80'

// External repo links and the internal hash-route pages, kept in one list
// so the nav grid renders in a stable order. hrefs stay literal; labels come
// from the catalog.
const links = [
    { key: 'clientRepo', href: 'https://github.com/FNOSP/FlyNarwhal', external: true },
    { key: 'serverRepo', href: 'https://github.com/FNOSP/fly-narwhal-server', external: true },
    { key: 'allReleases', href: 'https://github.com/FNOSP/FlyNarwhal/releases', external: true },
    { key: 'changelog', href: '#/timeline' },
    { key: 'issues', href: 'https://github.com/FNOSP/FlyNarwhal/issues', external: true },
    { key: 'credits', href: '#/credits' },
    { key: 'guide', href: '#/guide' },
]

const commitsTip = (c) => format(t.value.footer.commitsTip, { login: c.login, commits: c.commits })
const moreTip = (n) => format(t.value.footer.moreContributors, { count: n })
const countLabel = (n) => format(t.value.footer.contributorsCount, { count: n })
const copyright = computed(() => format(t.value.footer.copyright, { year }))
</script>

<template>
    <footer class="footer">
        <div class="shell footer__inner">
            <section class="footer__contributors" :aria-label="t.footer.contributorsAria">
                <h2 class="footer__contributors-title">{{ t.footer.contributors }}</h2>
                <div
                    v-for="g in groups.filter((x) => x.contributors.length)"
                    :key="g.key"
                    class="footer__contrib-row"
                >
                    <span class="footer__contrib-label">{{ labels[g.key] }}</span>
                    <ul class="footer__avatars">
                        <li
                            v-for="c in g.contributors.slice(0, AVATAR_CAP)"
                            :key="c.login"
                            class="footer__avatar"
                            :data-tip="commitsTip(c)"
                        >
                            <a :href="c.profile" target="_blank" rel="noopener noreferrer" :aria-label="c.login">
                                <img :src="avatarSrc(c)" :alt="c.login" width="40" height="40" loading="lazy" />
                            </a>
                        </li>
                        <li
                            v-if="g.contributors.length > AVATAR_CAP"
                            class="footer__avatar footer__avatar--more"
                            :data-tip="moreTip(g.contributors.length - AVATAR_CAP)"
                        >
                            <a :href="g.graphUrl" target="_blank" rel="noopener noreferrer" :aria-label="t.footer.viewAllContributors">
                                +{{ g.contributors.length - AVATAR_CAP }}
                            </a>
                        </li>
                    </ul>
                    <a
                        class="footer__more"
                        :href="g.graphUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                    >
                        {{ countLabel(g.contributors.length) }}
                    </a>
                </div>
            </section>

            <div class="footer__top">
                <div class="footer__brand">
                    <!-- Inlined (not <img>) so the SVG's ink paths can be
                         currentColor and follow the theme, while the brand
                         icon tiles keep their literal colors. -->
                    <div
                        class="footer__banner"
                        role="img"
                        :aria-label="t.footer.bannerAria"
                        v-html="bannerSvg"
                    ></div>
                    <p class="footer__tagline">{{ t.footer.tagline }}</p>
                </div>

                <nav class="footer__links" :aria-label="t.footer.linksAria">
                    <a
                        v-for="l in links"
                        :key="l.key"
                        :href="l.href"
                        v-bind="l.external ? { target: '_blank', rel: 'noopener noreferrer' } : {}"
                    >{{ t.footer.links[l.key] }}</a>
                </nav>
            </div>

            <div class="footer__bottom">
                <p class="footer__disclaimer">{{ t.footer.disclaimer }}</p>
                <p class="footer__copy">{{ copyright }}</p>
            </div>
        </div>
    </footer>
</template>

<style scoped>
.footer {
    padding: clamp(44px, 5vw, 60px) 0 34px;
    /* Follows the page theme; the inlined banner below draws its ink with
       currentColor so it adapts along with this background. */
    background: var(--bg);
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
    align-items: center;
    justify-content: space-between;
    gap: 24px 40px;
    /* Divider between the contributors row above and the brand/links block. */
    padding-top: 26px;
    border-top: 1px solid var(--hairline);
}

.footer__brand {
    display: grid;
    gap: 12px;
    justify-items: start;
}

.footer__banner {
    /* The banner's ink paths are currentColor, so the wordmarks follow the
       theme while the colored icon tiles stay untouched. */
    color: var(--ink);
}

/* The svg is injected via v-html, so its nodes carry no scoped attribute —
   :deep() is required for the sizing to reach them. */
.footer__banner :deep(svg) {
    display: block;
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

/* ---------- contributors (one row per repo) ---------- */

.footer__contributors {
    display: grid;
    gap: 12px;
}

.footer__contributors-title {
    font-size: 13px;
    font-weight: 650;
    letter-spacing: 0.04em;
    color: var(--ink);
}

.footer__contrib-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 20px;
}

.footer__contrib-label {
    width: 3.2em;
    flex: none;
    font-size: 13px;
    font-weight: 550;
    color: var(--ink-muted);
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
    box-shadow: 0 0 0 2.5px var(--bg);
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
    box-shadow: 0 0 0 2.5px var(--bg), 0 10px 22px rgba(0, 0, 0, 0.16);
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
    /* Token pair flips in dark theme: light chip with dark text. */
    background: var(--tooltip-bg);
    color: var(--tooltip-ink);
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
