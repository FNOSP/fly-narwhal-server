<script setup>
import { ref } from 'vue'
import { PLATFORM_LOGOS as LOGOS } from '../assets/platforms'

const props = defineProps({
    platformGroups: { type: Object, default: () => ({}) },
    loading: { type: Boolean, default: true },
    publishedLabel: { type: String, default: '' },
    releaseUrl: { type: String, default: '' },
    tag: { type: String, default: '' },
    osRules: { type: Array, required: true },
})

const OS_NOTE = {
    windows: '安装包支持自动更新，也可下载便携版解压即用。',
    macos: '发布版为临时签名，首次打开若提示无法验证开发者，可移除隔离标记后启动。',
    linux: '按发行版选择 deb / rpm / pkg.tar.zst，或使用通用的 AppImage。',
}

const noteOpen = ref(null)

function toggleNote(os) {
    noteOpen.value = noteOpen.value === os ? null : os
}

function groupsFor(os) {
    return props.platformGroups[os] || []
}

function hasAny(os) {
    return groupsFor(os).some((g) => g.buttons.length > 0)
}
</script>

<template>
    <section id="download" class="dl">
        <div class="shell">
            <div class="section-head dl__head">
                <p v-reveal class="eyebrow">下载</p>
                <h2 v-reveal="80" class="h2">
                    {{ tag ? `下载客户端 ${tag}` : '下载客户端' }}
                </h2>
                <p v-reveal="160" class="lede">
                    选择与你系统匹配的安装包。下载经由镜像加速，页面上的链接始终指向最新稳定版。
                </p>
            </div>

            <div class="dl__grid">
                <article
                    v-for="(rule, i) in osRules"
                    :key="rule.os"
                    v-reveal="i * 110"
                    class="dlcard"
                >
                    <header class="dlcard__head">
                        <svg
                            class="dlcard__logo"
                            :viewBox="LOGOS[rule.os].viewBox"
                            aria-hidden="true"
                            :fill="LOGOS[rule.os].stroke ? 'none' : 'currentColor'"
                            :stroke="LOGOS[rule.os].stroke ? 'currentColor' : 'none'"
                            stroke-width="1.6"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                        >
                            <path :d="LOGOS[rule.os].path" />
                        </svg>
                        <h3 class="dlcard__name">{{ rule.label }}</h3>
                    </header>

                    <div class="dlcard__body">
                        <div v-if="loading" class="dlcard__hint">正在获取最新版本…</div>

                        <template v-else-if="hasAny(rule.os)">
                            <div
                                v-for="(group, gi) in groupsFor(rule.os)"
                                :key="gi"
                                class="dlgroup"
                            >
                                <div v-if="group.title" class="dlgroup__title">{{ group.title }}</div>
                                <div class="dlgroup__buttons" :class="{ 'dlgroup__buttons--pkg': rule.os === 'linux' }">
                                    <a
                                        v-for="(btn, bi) in group.buttons"
                                        :key="bi"
                                        class="dlbtn"
                                        :class="{ 'dlbtn--ghost': btn.secondary }"
                                        :href="btn.url"
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        :title="btn.tip"
                                    >
                                        <span class="dlbtn__label">{{ btn.label }}</span>
                                        <span v-if="btn.sub" class="dlbtn__sub">{{ btn.sub }}</span>
                                    </a>
                                </div>
                            </div>
                        </template>

                        <div v-else class="dlcard__hint">暂无可用安装包</div>
                    </div>

                    <footer class="dlcard__foot">
                        <button
                            v-if="rule.os === 'macos'"
                            class="dlcard__note-toggle"
                            type="button"
                            :aria-expanded="noteOpen === rule.os"
                            @click="toggleNote(rule.os)"
                        >
                            首次打开被系统阻止？
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
                            </svg>
                        </button>
                        <div v-if="rule.os === 'macos' && noteOpen === 'macos'" class="dlcard__note">
                            发布版本会对应用做临时签名，多数情况下只会提示“无法验证开发者”。若提示“已损坏”，把应用放入
                            <code>/Applications</code> 后在终端执行：
                            <code class="dlcard__cmd">xattr -dr com.apple.quarantine /Applications/FlyNarwhal.app</code>
                            通过应用内自动更新安装的版本不带隔离标记，通常无需执行。
                        </div>
                        <p v-else class="dlcard__tip">{{ OS_NOTE[rule.os] }}</p>
                    </footer>
                </article>
            </div>

            <p v-if="publishedLabel" class="dl__note">
                {{ publishedLabel }}全部安装包及 SHA256SUMS 校验文件见
                <a :href="releaseUrl" target="_blank" rel="noopener noreferrer">GitHub Releases 页面</a>。
            </p>
        </div>
    </section>
</template>

<style scoped>
.dl {
    padding: clamp(72px, 11vw, 140px) 0;
    background: linear-gradient(180deg, var(--bg) 0%, #FFFFFF 60%);
}

.dl__head {
    text-align: center;
}

.dl__head .lede {
    margin: 16px auto 0;
}

.dl__grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(310px, 1fr));
    gap: 22px;
    align-items: start;
}

.dlcard {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 28px 26px 24px;
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    transition: transform 0.4s var(--ease), box-shadow 0.4s var(--ease);
}

.dlcard.is-revealed:hover {
    transform: translateY(-5px);
    box-shadow: var(--shadow-md);
}

.dlcard__head {
    display: flex;
    align-items: center;
    gap: 13px;
    margin-bottom: 22px;
}

.dlcard__logo {
    width: 30px;
    height: 30px;
    /* Driven through `color` so the same rule serves the filled macOS glyph and
       the stroked Windows/Linux glyphs. */
    color: #6E6E73;
    flex-shrink: 0;
}

.dlcard__name {
    font-size: 21px;
    font-weight: 650;
    letter-spacing: -0.01em;
}

.dlcard__body {
    flex: 1;
}

.dlcard__hint {
    font-size: 13.5px;
    color: var(--ink-muted);
    padding: 10px 0;
}

.dlgroup + .dlgroup {
    margin-top: 16px;
}

.dlgroup__title {
    font-size: 12.5px;
    font-weight: 650;
    letter-spacing: 0.02em;
    color: var(--ink-muted);
    margin-bottom: 9px;
}

.dlgroup__buttons {
    display: flex;
    flex-direction: column;
    gap: 9px;
}

.dlgroup__buttons--pkg {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dlbtn {
    display: flex;
    align-items: baseline;
    justify-content: center;
    gap: 7px;
    flex-wrap: wrap;
    padding: 11px 14px;
    border-radius: var(--radius-sm);
    background: var(--brand);
    color: #fff;
    font-size: 14.5px;
    font-weight: 600;
    text-align: center;
    transition: background-color 0.25s var(--ease), transform 0.25s var(--ease),
        box-shadow 0.25s var(--ease);
}

.dlbtn:hover {
    background: var(--brand-deep);
    transform: translateY(-1px);
    box-shadow: 0 8px 18px rgba(0, 122, 255, 0.28);
}

.dlbtn__sub {
    font-size: 12px;
    font-weight: 500;
    opacity: 0.86;
}

.dlbtn--ghost {
    background: transparent;
    color: var(--brand);
    border: 1px solid rgba(0, 122, 255, 0.36);
}

.dlbtn--ghost:hover {
    background: var(--brand-tint);
    box-shadow: none;
}

.dlbtn--ghost .dlbtn__sub {
    opacity: 0.75;
}

.dlcard__foot {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid var(--hairline);
}

.dlcard__tip {
    font-size: 12.5px;
    line-height: 1.6;
    color: var(--ink-muted);
}

.dlcard__note-toggle {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12.5px;
    font-weight: 600;
    color: var(--brand);
    padding: 0;
}

.dlcard__note-toggle svg {
    transition: transform 0.25s var(--ease);
}

.dlcard__note-toggle[aria-expanded='true'] svg {
    transform: rotate(180deg);
}

.dlcard__note {
    margin-top: 10px;
    font-size: 12.5px;
    line-height: 1.7;
    color: var(--ink-muted);
}

.dlcard__note code {
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    background: rgba(0, 0, 0, 0.05);
    padding: 1px 5px;
    border-radius: 5px;
}

.dlcard__cmd {
    display: block;
    margin-top: 8px;
    padding: 9px 11px;
    font-size: 11.5px;
    word-break: break-all;
    line-height: 1.55;
}

.dl__note {
    margin-top: 34px;
    text-align: center;
    font-size: 13.5px;
    color: var(--ink-muted);
    line-height: 1.7;
}

@media (max-width: 400px) {
    .dlgroup__buttons--pkg {
        grid-template-columns: 1fr;
    }
}
</style>
