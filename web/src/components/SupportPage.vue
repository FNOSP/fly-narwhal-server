<script setup>
import ThemeToggle from './ThemeToggle.vue'
import LocaleToggle from './LocaleToggle.vue'
import { useI18n } from '../composables/useI18n'
import donateQr from '../assets/img/donate-qr.jpg'
import donateMascot from '../assets/img/donate-mascot.gif'

const { t } = useI18n()

const CLIENT_REPO = 'https://github.com/FNOSP/FlyNarwhal'
const SERVER_REPO = 'https://github.com/FNOSP/fly-narwhal-server'
const ISSUES_URL = `${CLIENT_REPO}/issues`
</script>

<template>
    <div class="supage">
        <header class="supage__bar shell">
            <a class="supage__back" href="#top">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M19 12H5M11 18l-6-6 6-6" />
                </svg>
                {{ t.support.backHome }}
            </a>
            <div class="supage__actions">
                <span class="supage__crumb">{{ t.support.crumb }}</span>
                <LocaleToggle />
                <ThemeToggle />
            </div>
        </header>

        <main class="shell supage__main">
            <div class="supage__head" v-reveal>
                <p class="eyebrow">{{ t.support.eyebrow }}</p>
                <h1 class="supage__title">{{ t.support.title }}</h1>
                <p class="supage__lede">{{ t.support.lede }}</p>
            </div>

            <div class="supage__cards">
                <!-- Star card -->
                <section class="supage__card supage__card--star" v-reveal="60">
                    <div class="supage__card-head">
                        <span class="supage__icon-badge supage__icon-badge--star" aria-hidden="true">
                            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                                <path d="M12 2.5l2.9 6.3 6.9.7-5.2 4.6 1.5 6.8L12 17.6l-6.1 3.3 1.5-6.8-5.2-4.6 6.9-.7z" />
                            </svg>
                        </span>
                        <h2 class="supage__card-title">{{ t.support.starTitle }}</h2>
                    </div>
                    <p class="supage__card-desc">{{ t.support.starDesc }}</p>

                    <a class="btn btn-primary supage__cta" :href="CLIENT_REPO" target="_blank" rel="noopener noreferrer">
                        <svg viewBox="0 0 24 24" width="17" height="17" fill="currentColor" aria-hidden="true">
                            <path d="M12 2C6.48 2 2 6.58 2 12.25c0 4.53 2.87 8.37 6.84 9.73.5.1.68-.22.68-.49 0-.24-.01-1.04-.01-1.89-2.78.51-3.5-.69-3.72-1.32-.12-.31-.64-1.32-1.1-1.58-.37-.21-.9-.72-.01-.73.84-.01 1.44.79 1.64 1.12.96 1.65 2.49 1.19 3.1.9.09-.72.37-1.19.67-1.46-2.48-.29-5.07-1.27-5.07-5.63 0-1.24.43-2.26 1.14-3.06-.11-.29-.5-1.46.11-3.04 0 0 .94-.31 3.08 1.17a10.4 10.4 0 0 1 5.6 0c2.14-1.48 3.08-1.17 3.08-1.17.61 1.58.22 2.75.11 3.04.71.8 1.14 1.81 1.14 3.06 0 4.37-2.6 5.34-5.08 5.62.4.36.75 1.05.75 2.12 0 1.53-.01 2.76-.01 3.14 0 .27.18.6.69.49A10.02 10.02 0 0 0 22 12.25C22 6.58 17.52 2 12 2z" />
                        </svg>
                        {{ t.support.starCta }}
                    </a>

                    <p class="supage__card-note">{{ t.support.starNote }}</p>
                    <div class="supage__repo-links">
                        <a :href="CLIENT_REPO" target="_blank" rel="noopener noreferrer">{{ t.support.starClientRepo }}</a>
                        <span aria-hidden="true">·</span>
                        <a :href="SERVER_REPO" target="_blank" rel="noopener noreferrer">{{ t.support.starServerRepo }}</a>
                    </div>
                </section>

                <!-- Donate card -->
                <section class="supage__card supage__card--donate" v-reveal="120">
                    <div class="supage__card-head">
                        <span class="supage__icon-badge supage__icon-badge--donate" aria-hidden="true">
                            <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M18 8h1a3 3 0 0 1 0 6h-1" />
                                <path d="M4 8h14v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V8z" />
                                <path d="M8 2v2M12 2v2M16 2v2" />
                            </svg>
                        </span>
                        <h2 class="supage__card-title">{{ t.support.donateTitle }}</h2>
                    </div>
                    <p class="supage__card-desc">{{ t.support.donateDesc }}</p>

                    <div class="supage__donate-row">
                        <figure class="supage__qr">
                            <img :src="donateQr" :alt="t.support.donateQrAlt" loading="lazy" />
                            <figcaption>{{ t.support.donateQrCaption }}</figcaption>
                        </figure>
                        <figure class="supage__mascot">
                            <img :src="donateMascot" :alt="t.support.donateMascotAlt" loading="lazy" />
                        </figure>
                    </div>

                    <ol class="supage__steps">
                        <li v-for="(step, i) in t.support.donateSteps" :key="i">{{ step }}</li>
                    </ol>
                </section>
            </div>

            <aside class="supage__issues" v-reveal="60">
                <div class="supage__issues-text">
                    <h3 class="supage__issues-title">{{ t.support.issuesTitle }}</h3>
                    <p class="supage__issues-desc">{{ t.support.issuesDesc }}</p>
                </div>
                <a class="btn btn-ghost supage__issues-cta" :href="ISSUES_URL" target="_blank" rel="noopener noreferrer">
                    {{ t.support.issuesCta }}
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M7 17L17 7M9 7h8v8" />
                    </svg>
                </a>
            </aside>

            <p class="supage__closing">{{ t.support.closingNote }}</p>

            <footer class="supage__foot">
                <a class="btn btn-ghost" href="#top">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M19 12H5M11 18l-6-6 6-6" />
                    </svg>
                    {{ t.support.backToHome }}
                </a>
            </footer>
        </main>
    </div>
</template>

<style scoped>
.supage {
    min-height: 100svh;
    background: var(--bg);
}

/* ---------- top bar (mirrors CreditsPage/GuidePage chrome) ---------- */

.supage__bar {
    position: sticky;
    top: 0;
    z-index: 100;
    height: var(--nav-h);
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: var(--chrome-glass);
    backdrop-filter: saturate(180%) blur(20px);
    -webkit-backdrop-filter: saturate(180%) blur(20px);
    border-bottom: 1px solid var(--hairline);
}

.supage__actions {
    display: flex;
    align-items: center;
    gap: 12px;
}

.supage__back {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: 14px;
    font-weight: 600;
    color: var(--ink-soft);
    transition: color 0.2s var(--ease);
}

.supage__back:hover {
    color: var(--brand);
}

.supage__crumb {
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: var(--ink-muted);
}

/* ---------- head ---------- */

.supage__main {
    padding: clamp(56px, 8vw, 96px) 24px clamp(60px, 8vw, 96px);
}

.supage__head {
    text-align: center;
    max-width: 660px;
    margin: 0 auto clamp(44px, 6vw, 72px);
}

.supage__title {
    font-size: clamp(30px, 4.6vw, 52px);
    font-weight: 700;
    letter-spacing: -0.025em;
    line-height: 1.14;
}

.supage__lede {
    margin-top: 16px;
    font-size: clamp(15px, 1.6vw, 18px);
    line-height: 1.7;
    color: var(--ink-muted);
}

/* ---------- cards ---------- */

.supage__cards {
    max-width: 980px;
    margin: 0 auto;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 22px;
    align-items: start;
}

.supage__card {
    display: flex;
    flex-direction: column;
    height: 100%;
    border-radius: var(--radius-lg);
    background: var(--surface);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
    padding: clamp(24px, 3vw, 32px);
}

.supage__card--star {
    background: linear-gradient(180deg, var(--brand-wash), var(--surface) 42%);
}

.supage__card--donate {
    background: linear-gradient(180deg, rgba(255, 149, 0, 0.06), var(--surface) 42%);
}

html[data-theme='dark'] .supage__card--donate {
    background: linear-gradient(180deg, rgba(255, 159, 10, 0.09), var(--surface) 42%);
}

.supage__card-head {
    display: flex;
    align-items: center;
    gap: 14px;
}

.supage__icon-badge {
    flex: none;
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border-radius: 50%;
}

.supage__icon-badge--star {
    background: var(--brand-tint);
    color: var(--brand-deep);
}

.supage__icon-badge--donate {
    background: rgba(255, 149, 0, 0.14);
    color: var(--orange-deep);
}

html[data-theme='dark'] .supage__icon-badge--donate {
    background: rgba(255, 159, 10, 0.18);
}

.supage__card-title {
    font-size: clamp(18px, 2.1vw, 21px);
    font-weight: 700;
    letter-spacing: -0.01em;
    line-height: 1.3;
}

.supage__card-desc {
    margin-top: 16px;
    font-size: 14.5px;
    line-height: 1.75;
    color: var(--ink-muted);
}

/* Star card: the CTA and repo links sit at the bottom so both cards' actions
   land on roughly the same baseline despite different body lengths. */
.supage__cta {
    margin-top: 24px;
    align-self: flex-start;
}

.supage__card-note {
    margin-top: auto;
    padding-top: 20px;
    font-size: 12.5px;
    color: var(--ink-muted);
}

.supage__repo-links {
    margin-top: 6px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
}

.supage__repo-links a {
    color: var(--ink-soft);
    transition: color 0.2s var(--ease);
}

.supage__repo-links a:hover {
    color: var(--brand);
}

.supage__repo-links span {
    color: var(--ink-muted);
}

/* ---------- donate: QR + mascot ---------- */

.supage__donate-row {
    margin-top: 22px;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
}

.supage__qr,
.supage__mascot {
    margin: 0;
    display: flex;
    flex-direction: column;
}

.supage__qr img,
.supage__mascot img {
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
    border-radius: var(--radius-md);
    border: 1px solid var(--hairline);
    box-shadow: var(--shadow-sm);
}

/* The mascot art is landscape (377x300); contain keeps the whole character
   visible instead of cropping it to the square like the QR photo. */
.supage__mascot img {
    object-fit: contain;
    background: var(--field);
}

.supage__qr figcaption {
    margin-top: 10px;
    text-align: center;
    font-size: 12.5px;
    font-weight: 600;
    color: var(--ink-muted);
}

.supage__steps {
    margin-top: auto;
    padding-top: 22px;
    list-style: none;
    counter-reset: sstep;
    display: grid;
    gap: 10px;
}

.supage__steps li {
    counter-increment: sstep;
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13.5px;
    color: var(--ink-soft);
}

.supage__steps li::before {
    content: counter(sstep);
    flex: none;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: rgba(255, 149, 0, 0.14);
    color: var(--orange-deep);
    font-size: 12px;
    font-weight: 700;
    display: grid;
    place-items: center;
}

html[data-theme='dark'] .supage__steps li::before {
    background: rgba(255, 159, 10, 0.18);
}

/* ---------- issues strip ---------- */

.supage__issues {
    max-width: 980px;
    margin: 22px auto 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    padding: 22px clamp(20px, 3vw, 30px);
    border-radius: var(--radius-md);
    border: 1px dashed var(--hairline-strong);
}

.supage__issues-title {
    font-size: 15px;
    font-weight: 650;
}

.supage__issues-desc {
    margin-top: 6px;
    font-size: 13.5px;
    line-height: 1.7;
    color: var(--ink-muted);
    max-width: 60ch;
}

.supage__issues-cta {
    flex: none;
}

/* ---------- closing + foot ---------- */

.supage__closing {
    max-width: 640px;
    margin: clamp(36px, 5vw, 52px) auto 0;
    text-align: center;
    font-size: 14.5px;
    color: var(--ink-muted);
}

.supage__foot {
    display: flex;
    justify-content: center;
    margin-top: 24px;
}

/* ---------- responsive ---------- */

@media (max-width: 860px) {
    .supage__cards {
        grid-template-columns: 1fr;
        max-width: 480px;
    }

    .supage__card-note {
        margin-top: 20px;
    }

    .supage__steps {
        margin-top: 22px;
    }
}

@media (max-width: 640px) {
    .supage__issues {
        flex-direction: column;
        align-items: flex-start;
    }

    .supage__issues-cta {
        align-self: stretch;
    }
}

@media (max-width: 420px) {
    .supage__donate-row {
        grid-template-columns: 1fr;
    }

    .supage__mascot {
        display: none;
    }
}

@media (prefers-reduced-motion: reduce) {
    .supage__back,
    .supage__repo-links a {
        transition: none;
    }
}
</style>