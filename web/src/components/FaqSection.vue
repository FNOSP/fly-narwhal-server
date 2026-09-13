<script setup>
import { ref } from 'vue'

const faqs = [
    {
        q: '播放视频支持硬件解码吗？',
        a: '播放器基于 media_kit / libmpv，具备 GPU 加速能力。最终效果取决于平台、驱动、视频格式与系统环境；HDR、字幕与音轨切换等体验仍在持续优化，请以实际版本表现为准。',
    },
    {
        q: '支持用 FN ID 或通过 NAS 登录吗？',
        a: '支持。当前登录流程同时覆盖 FN ID 与 NAS 登录两种场景，连接地址、端口与 HTTPS 都可以直接配置。',
    },
    {
        q: '可以用飞牛 OS 的自签证书走 HTTPS 吗？',
        a: '暂不支持自签证书直连。如果启用了 HTTPS，请使用受系统信任的证书。',
    },
    {
        q: '支持直链播放吗？',
        a: '除 Dolby Vision Profile 5 之外，原画质下默认直链播放；部分播放失败场景会自动回退到 HLS，尽量不打断观看。',
    },
    {
        q: 'macOS 首次打开提示“无法验证开发者”或“已损坏”？',
        a: '发布版对应用做了临时签名，多数情况只会提示“无法验证开发者”。若提示“已损坏”，把应用放入 /Applications 后在终端执行 xattr -dr com.apple.quarantine /Applications/FlyNarwhal.app 即可。通过应用内自动更新安装的版本不带隔离标记。下载区也有同样的说明。',
    },
    {
        q: '这个项目是飞牛官方出品的吗？',
        a: '不是。本项目为飞牛 OS 爱好者开发的第三方影视客户端，与飞牛影视官方无关。使用前请确保遵守相关服务条款。',
    },
]

const open = ref(0)

function toggle(i) {
    open.value = open.value === i ? -1 : i
}
</script>

<template>
    <section id="faq" class="faq">
        <div class="shell faq__shell">
            <div class="section-head faq__head">
                <p v-reveal class="eyebrow">常见问题</p>
                <h2 v-reveal="80" class="h2">还想多了解一点？</h2>
            </div>

            <div v-reveal="140" class="faq__list">
                <div
                    v-for="(f, i) in faqs"
                    :key="f.q"
                    class="faq__item"
                    :class="{ 'faq__item--open': open === i }"
                >
                    <button
                        class="faq__q"
                        type="button"
                        :aria-expanded="open === i"
                        @click="toggle(i)"
                    >
                        <span>{{ f.q }}</span>
                        <svg class="faq__icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                            <path d="M12 5v14" />
                            <path d="M5 12h14" class="faq__icon-bar" />
                        </svg>
                    </button>
                    <div class="faq__panel">
                        <div class="faq__panel-inner">
                            <p class="faq__a">{{ f.a }}</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.faq {
    padding: clamp(72px, 11vw, 140px) 0;
    background: var(--bg);
}

.faq__shell {
    max-width: 860px;
}

.faq__head {
    text-align: center;
    margin-bottom: clamp(32px, 4vw, 48px);
}

.faq__list {
    display: grid;
    gap: 12px;
}

.faq__item {
    border-radius: var(--radius-md);
    background: var(--surface);
    border: 1px solid var(--hairline);
    overflow: hidden;
    transition: border-color 0.3s var(--ease), box-shadow 0.3s var(--ease);
}

.faq__item--open {
    border-color: rgba(0, 122, 255, 0.32);
    box-shadow: var(--shadow-md);
}

.faq__q {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    width: 100%;
    padding: 20px 24px;
    text-align: left;
    font-size: 16px;
    font-weight: 620;
    color: var(--ink);
}

.faq__icon {
    flex-shrink: 0;
    color: var(--brand);
    transition: transform 0.35s var(--ease);
}

.faq__item--open .faq__icon {
    transform: rotate(90deg);
}

.faq__icon-bar {
    transition: opacity 0.3s var(--ease);
}

.faq__item--open .faq__icon-bar {
    opacity: 0;
}

/* 0fr → 1fr gives a smooth height transition without measuring content. */
.faq__panel {
    display: grid;
    grid-template-rows: 0fr;
    transition: grid-template-rows 0.42s var(--ease);
}

.faq__item--open .faq__panel {
    grid-template-rows: 1fr;
}

.faq__panel-inner {
    overflow: hidden;
}

.faq__a {
    padding: 0 24px 22px;
    font-size: 14.5px;
    line-height: 1.78;
    color: var(--ink-muted);
}

@media (max-width: 560px) {
    .faq__q {
        padding: 17px 18px;
        font-size: 15px;
    }

    .faq__a {
        padding: 0 18px 18px;
    }
}
</style>
