// English —— mirrors the exact key tree of zh-CN.js (dev-time assertion in
// useI18n.js checks this). Product names, commands and URLs stay literal.
export default {
    meta: {
        title: 'Fly Narwhal 2.0 · A Fresh Start | Third-party fnOS Media Desktop Client',
        description:
            'Fly Narwhal 2.0 — a third-party desktop client for fnOS media services. Rewritten natively in Flutter, with a built-in mpv playback core, available on Windows, macOS and Linux, and featuring smart intro/outro skipping and danmaku.',
    },

    common: {
        brand: 'Fly Narwhal',
        backHome: 'Back to home',
        language: 'Language',
    },

    nav: {
        homeAria: 'Fly Narwhal home',
        logoAria: 'Fly Narwhal',
        linksAria: 'Page navigation',
        links: {
            renew: 'What’s new in 2.0',
            screenshots: 'Screenshots',
            features: 'Features',
            player: 'Playback core',
            server: 'Server',
            changelog: 'Changelog',
            faq: 'FAQ',
            guide: 'User guide',
        },
        getAuthCode: 'Get auth code',
        download: 'Download',
    },

    hero: {
        iconAlt: 'Fly Narwhal app icon',
        brand: 'Fly Narwhal 2.0 · A Fresh Start',
        titleLine1: 'Put fnOS media',
        titleLine2: 'right on your desktop',
        lede: 'A third-party desktop client for fnOS media services. Version 2.0 is a fresh start: rewritten natively in Flutter with a built-in mpv playback core, covering Windows, macOS and Linux, with smart intro/outro skipping and danmaku.',
        download: 'Download',
        getAuthCode: 'Get auth code',
        guide: 'User guide',
        scrollHint: 'Scroll down',
    },

    renew: {
        eyebrow: '2.0 · A Fresh Start',
        titleLine1: 'Not a patch —',
        titleLine2: 'a complete rebuild.',
        lede: 'From the playback core to the visual language, Fly Narwhal 2.0 rebuilds the desktop viewing experience from the ground up.',
        pillars: [
            {
                title: 'Native Flutter rewrite',
                desc: 'Say goodbye to the JVM runtime. Rendering and window scheduling sit closer to the system itself, so the package is lighter and runs faster.',
            },
            {
                title: 'mpv playback core',
                desc: 'GPU-accelerated decoding straight to the screen, HDR dynamic range preserved as-is, and CPU usage sharply reduced.',
            },
            {
                title: 'Liquid Glass visuals',
                desc: 'A new design language with Liquid Glass textures and a modern layout for a more translucent feel.',
            },
            {
                title: 'Every platform covered',
                desc: 'Linux support is new: three platforms, seven formats, ready to use out of the box.',
            },
        ],
    },

    screenshots: {
        eyebrow: 'Screenshots',
        h2: 'It looks like a native app',
        lede: 'Version 2.0 is a fresh start: the desktop client is rebuilt in Flutter, no longer depending on the JVM runtime, with window interaction and rendering that sit closer to the system itself.',
        switchAria: 'Switch preview image',
        shots: [
            {
                alt: 'Fly Narwhal sign-in screen with a Liquid Glass texture',
                title: 'Sign in',
                desc: 'Supports both fnOS ID and NAS sign-in, with the address, port and HTTPS access all configurable directly.',
            },
            {
                alt: 'Fly Narwhal media library home screen',
                title: 'Media library',
                desc: 'The home screen brings together continue-watching and library categories, browsing movies, series and anime by category and tag.',
            },
            {
                alt: 'Fly Narwhal player with bilingual subtitles and playback controls',
                title: 'Player',
                desc: 'A built-in mpv playback core supporting GPU-accelerated decoding, PGS/SUP subtitles and HDR dynamic range.',
            },
            {
                alt: 'Fly Narwhal playing an 8K high-bitrate source',
                title: '8K and high bitrate',
                desc: 'Decodes high-bitrate sources natively and, with GPU acceleration, plays them directly on the desktop — no server-side transcoding required.',
            },
        ],
    },

    features: {
        eyebrow: 'Features',
        h2Line1: 'Built again, from scratch,',
        h2Line2: 'for desktop viewing',
        lede: 'The playback core, subtitles, skipping and danmaku are not bolt-on extras — each is designed from the ground up around desktop usage.',
        items: [
            {
                title: 'Native Flutter rewrite',
                desc: 'The 2.x desktop client is fully rewritten, dropping the JVM runtime overhead. The startup path, player initialization and first-frame rendering are all optimized for a lighter package that opens faster.',
            },
            {
                title: 'Built-in mpv playback core',
                desc: 'Based on media_kit and full libmpv, with GPU-accelerated and local decoding. macOS and Linux ship with complete decoding libraries and handle HDR, HLG and Dolby Vision directly.',
            },
            {
                title: 'PGS / SUP subtitles',
                desc: 'Supports HDMV PGS Blu-ray subtitle decoding and works with both external and embedded subtitles, letting you switch subtitles and audio tracks at any point during playback.',
            },
            {
                title: 'Smart intro/outro skipping',
                desc: 'The companion server analyzes each episode to determine the intro and outro ranges, which are then skipped automatically — no more scrubbing the timeline by hand.',
            },
            {
                title: 'Danmaku and playback progress',
                desc: 'Fetches and caches danmaku, and works with playback progress and continue-watching to resume across multiple devices.',
            },
            {
                title: 'Three platforms, two architectures',
                desc: 'Full coverage across Windows x64, macOS Intel / Apple Silicon and Linux x64 / arm64, shipping exe, dmg, deb, rpm, pkg.tar.zst and AppImage.',
            },
        ],
        stats: [
            { label: 'Desktop platforms' },
            { label: 'Package formats' },
            { label: 'CPU architectures' },
        ],
    },

    player: {
        eyebrow: 'Playback core',
        titleLine1: 'A cinema-grade playback core,',
        titleLine2: 'built into your desktop.',
        lede: 'Based on the same playback core as mpv, blockbusters in 8K and HDR play smoothly right on your computer — no server compute, no stutter.',
        videoAria: 'Fly Narwhal playing an 8K 60fps HDR high-bitrate source',
        tag: '8K · 60fps · HDR · Direct GPU output',
        note: 'Hardware-decoded 8K HDR playback is coming in a future release — stay tuned',
        chipsAria: 'Playback capabilities',
        chips: [
            'GPU decoding',
            'HDR',
            'HLG',
            'Dolby Vision',
            'PGS / SUP subtitles',
            '8K high bitrate',
            'Direct-link playback',
            'HLS fallback',
            'Audio track switching',
        ],
        compare: [
            {
                title: 'RGBA read-back rendering',
                desc: 'Limited by Compose Desktop’s rendering model, hardware-decoded frames had to be read back to the CPU before rendering. CPU usage was high, and HDR sources were forced into SDR on the server.',
            },
            {
                title: 'Direct GPU output',
                desc: 'libmpv’s hardware-decoded output goes straight to the screen, CPU usage drops sharply, and the source’s original dynamic range is preserved — HDR is no longer distorted.',
            },
        ],
    },

    danmu: {
        eyebrow: 'Danmaku',
        h2Line1: 'More sources,',
        h2Line2: 'smarter matching, steadier fetching',
        lede: 'Sources now cover nine platforms plus a third-party fallback, title matching is configurable, and every batch runs through a post-processing pipeline whose settings apply instantly.',
        items: [
            {
                tag: 'Sources',
                title: 'Nine platforms, plus a fallback',
                desc: 'Added Migu Video, dandanplay (official service and public relay as two channels, each with its own switch and a preferred pick — the relay needs no platform credentials), and a dandanplay-compatible third-party fallback server.',
            },
            {
                tag: 'Matching',
                title: 'Configurable title matching',
                desc: 'Custom title mappings, noise-word stripping before search, a strict title mode, and a per-platform search priority order.',
            },
            {
                tag: 'Pipeline',
                title: 'Danmaku post-processing',
                desc: 'Before delivery, danmaku go through timeline offset, duplicate-text removal, blockword filtering, capped sampling, mode conversion and color remapping; settings apply instantly, and failures fall back to the raw results.',
            },
            {
                tag: 'Cache',
                title: 'Tiered caching',
                desc: 'Empty results are never cached, sparse results expire after an hour, and full results are kept for 30 days — one failed or premature fetch no longer gets pinned as “no danmaku” for weeks.',
            },
            {
                tag: 'Stability',
                title: 'Steadier fetching',
                desc: 'All platform sources share one bounded thread pool with a 60-second per-segment timeout, so a single stuck fetch can no longer drag down a whole episode; Youku tokens refresh and retry on expiry, and Sohu segments now follow the real duration.',
            },
            {
                tag: 'Fixes',
                title: 'A batch of fetch & display fixes',
                desc: 'Fixed Bilibili segmented fetches, iQiyi parsing and episode resolution, Youku video IDs, Douban ID parsing, whole seasons sharing one episode’s danmaku, and wrong fallbacks to episode one — and danmaku no longer appear before playback starts.',
            },
        ],
        platforms: ['Bilibili', 'iQiyi', 'Youku', 'Tencent', 'Mango TV', 'Sohu', 'Migu', 'dandanplay', 'Fallback'],
    },

    skip: {
        eyebrow: 'Smart skip',
        h2Line1: 'Intros, recaps, next-episode previews —',
        h2Line2: 'all skippable',
        lede: 'Skipping now extends beyond intros and outros to recaps, next-episode previews and mid-roll ads; replaced sources are re-analyzed automatically.',
        caption: 'The in-player “Smart skip” panel: each category has its own switch, and an automatic skip can still be undone.',
        items: [
            {
                tag: 'Detection',
                title: 'More skippable segments',
                desc: 'Recaps and next-episode previews joined the smart analysis, and a single episode can carry multiple mid-roll ad segments, so skip coverage reaches more sources.',
            },
            {
                tag: 'Accuracy',
                title: 'More accurate detection',
                desc: 'Fewer false positives, plus a fix for the chapter-name regex that had never matched since the first release and was silently disabling intro/outro markers.',
            },
            {
                tag: 'Re-analysis',
                title: 'Auto re-analysis on replacement',
                desc: 'Analysis records store the media file’s modification time, so a file replaced at the same path invalidates the old fingerprint and is re-analyzed automatically — no manual cleanup.',
            },
            {
                tag: 'Cache',
                title: 'Smarter fingerprint cache',
                desc: 'Fingerprints are cached per analysis window and only rebuilt when window-affecting settings change; episodes with an unknown duration no longer waste an analysis run.',
            },
            {
                tag: 'Snapping',
                title: 'Reliable keyframe snapping',
                desc: 'VP9 sources get a proper keyframe filter, scan logs no longer swallow keyframe output, and snap points are clipped back inside the search window.',
            },
            {
                tag: 'Setup',
                title: 'One-tap markers',
                desc: 'During playback you can set the current position as the intro end or outro start with one tap; skip switches apply per item instantly, and the scan timeout is configurable.',
            },
        ],
    },

    more: {
        eyebrow: 'More capabilities',
        h2Line1: 'Every detail,',
        h2Line2: 'designed for watching',
        items: [
            { title: 'Folder view', desc: 'Browse the media files on your NAS by directory and find what you want to watch even without scraping.' },
            { title: 'Live TV', desc: 'Live channels start on a tap, with channel switching and volume handled in the same playback view.' },
            { title: 'Cloud-drive playback', desc: 'Videos on cloud drives play online directly — no need to transfer them first.' },
            { title: 'Media info panel', desc: 'See the codec, bitrate and language of every video stream, audio stream and subtitle track at a glance.' },
            { title: 'STRM direct playback', desc: 'Aligned with the fnOS media web flow: parse the STRM file and play straight from the cloud address.' },
            { title: 'Playback details', desc: 'Decoding method, dropped frames and buffer status are all visible in real time.' },
            { title: 'Advanced playback options', desc: 'Force H.264 and SDR tone mapping, as a fallback for older devices and unusual sources.' },
            { title: 'Audio passthrough', desc: 'With HDMI / S-PDIF passthrough on, compressed tracks like AC3, DTS and TrueHD are handed to your receiver as raw bitstreams, keeping the original surround staging; applies to direct-play original audio only, with transcoded audio falling back to local decoding.' },
        ],
        liveChannel: 'CCTV-8 Drama · 1080i',
    },

    security: {
        eyebrow: 'Security',
        titleLine1: 'Your passwords,',
        titleLine2: 'never written to disk in plain text.',
        lede: 'Passwords in your sign-in history are authenticated and encrypted before they reach the disk. Even if the local file is copied away, not a single plaintext password can be recovered.',
        vaultLabel: 'Sign-in history · NAS-Home',
        encrypted: 'Encrypted',
        saltMeta: 'random salt · nonce',
        guards: ['Auth-tag verification', 'Ciphertext integrity check', 'Sensitive memory zeroing', 'Auto-clear on key errors', 'Invalidated on tampering'],
        steps: [
            { title: 'HKDF key derivation', desc: 'Independent encryption keys are derived from the master key via HKDF, never touching the original passphrase directly.' },
            { title: 'Random salt / nonce', desc: 'Every encryption uses a fresh random value, so the same password always yields a completely different ciphertext.' },
            { title: 'AES-256-GCM authenticated encryption', desc: 'An authentication tag is generated alongside the ciphertext, so any tampering is detected.' },
            { title: 'Ciphertext safely on disk', desc: 'Only ciphertext ever reaches the disk; once a write completes, the sensitive memory is zeroed immediately.' },
        ],
    },

    server: {
        eyebrow: 'Server',
        h2: 'From 470 MB to 120 MB, with nothing cut',
        lede: 'The server behind danmaku, intro/outro analysis and authorization has been slimmed down twice over: memory use is down to about a quarter of what it was, startup takes about 0.2 seconds, and no feature was removed.',
        milestones: [
            { label: 'Before', value: '≈ 472 MB', note: 'The pre-optimization baseline' },
            { label: 'Native binary rebuild', value: '≈ 165 MB', note: 'Rebuilt as a native program, ~0.2 s startup' },
            { label: 'After memory tuning · now', value: '≈ 120 MB', note: '≈120 MB covers the whole program including system components; the data area is capped at 64 MB and measured at ~24 MB' },
        ],
        measures: [
            { title: 'Load on demand', desc: 'Program parts now load only when used, instead of being fully initialized at startup.' },
            { title: 'Smaller database cache', desc: 'The database’s built-in cache cap was cut from 64 MB to 8 MB, so it no longer eats most of the memory.' },
            { title: 'Fewer background threads', desc: 'Concurrent request threads were cut from 200 to 24 — plenty for a NAS — freeing the memory held by idle threads.' },
            { title: 'A firm memory ceiling', desc: 'The program gets a 64 MB memory cap, and idle memory is handed back to the system instead of piling up.' },
            { title: 'Lighter queries', desc: 'Analysis lookups no longer drag along ~50 KB of unused data; at 48-way concurrency the memory peak fell from 35 MB to 30.5 MB and memory cleanups halved.' },
            { title: 'No Java required', desc: 'Releases are native executables, so the NAS no longer needs Java installed; both processor architectures are built separately.' },
        ],
        footnote: 'Measured on the same NAS with a 768-episode library: memory peaked at 35 MB under 24/48-way concurrency, never touching the 64 MB cap, with no memory cleanup while idle.',
    },

    platforms: {
        title: 'Three platforms · Two architectures · Seven formats',
        lede: 'Whatever combination your desktop is, there is a package waiting that works out of the box.',
        notes: {
            windows: 'Both editions support in-app updates; the portable edition runs right after unzipping',
            macos: 'A universal .dmg covering both chip generations',
            linux: 'deb / rpm / Arch / AppImage fully covered',
        },
        badges: {
            mirror: 'Mirror accelerated',
            autoUpdate: 'Auto update',
            portable: 'Portable',
        },
    },

    download: {
        eyebrow: 'Download',
        h2: 'Download',
        h2WithTag: 'Download {tag}',
        lede: 'Choose the package that matches your system. Downloads go through a mirror for speed, and the links on this page always point to the latest stable release.',
        tabsAria: 'Choose platform',
        archLabel: 'Architecture',
        archAria: 'Choose architecture',
        recommended: 'Recommended',
        download: 'Download',
        empty: 'No package is available for this combination yet — head to',
        emptyTail: 'to take a look.',
        macNoteToggle: 'Blocked by the system on first launch?',
        notes: {
            windows: 'Both the installer and the portable edition support in-app auto-update; the portable edition runs right after unzipping, with no installation needed.',
            macos: 'Release builds are ad-hoc signed. If the first launch warns that the developer cannot be verified, remove the quarantine flag and start it.',
            linux: 'Pick deb / rpm / pkg.tar.zst by distribution, or use the universal AppImage.',
        },
        macNoteBody1: 'Release builds are ad-hoc signed, so in most cases macOS only warns that the developer cannot be verified. If it says the app is “damaged”, move it into',
        macNoteBody2: 'and run this in Terminal:',
        macNoteTail: 'Versions installed through the in-app auto-update carry no quarantine flag, so this is usually unnecessary.',
        publishedNote: '{published}All packages and the SHA256SUMS checksum file are on the ',
        publishedNoteTail: '.',
        releasesPage: 'GitHub Releases page',
    },

    changelog: {
        eyebrow: 'Changelog',
        h2Line1: 'Always evolving —',
        h2Line2: 'every release on the record.',
        lede: 'The full changes in the latest release, plus the entire update history since launch.',
        viewAll: 'Full changelog for {channel}',
        viewAllDesc: 'From {oldest} to today, {count} releases — every addition, improvement and fix, all recorded here.',
        viewAllDescNoOldest: '{count} releases in total — every release, all recorded here.',
        sourceAria: 'Changelog source',
        channels: {
            client: 'client',
            server: 'server',
        },
        categories: {
            Added: 'Added',
            Changed: 'Changed',
            Fixed: 'Fixed',
        },
        latestBadge: 'Latest',
        error: 'The changelog could not be loaded right now — possibly a network issue.',
        loadingAria: 'Loading changelog',
        gotoGithub: 'View the full changelog on GitHub →',
        backHome: 'Back to home',
    },

    timeline: {
        crumb: '{brand} · {channel} changelog',
        eyebrow: 'All releases',
        title: 'Every release leaves a trace.',
        lede: '{count} releases in total — every addition, improvement and fix, all recorded here.',
        backHome: 'Back to home',
        backToHome: 'Back to the Fly Narwhal home page',
        latestBadge: 'Latest',
        gotoGithub: 'View on GitHub →',
    },

    faq: {
        eyebrow: 'FAQ',
        h2: 'Want to know a little more?',
        items: [
            {
                q: 'Does video playback support hardware decoding?',
                a: 'The player is based on media_kit / libmpv and has GPU acceleration. The end result depends on the platform, drivers, video format and system environment; experiences such as HDR, subtitles and audio track switching are still being refined, so please judge by actual release behavior.',
            },
            {
                q: 'Does it support signing in with an FN ID or through the NAS?',
                a: 'Yes. The current sign-in flow covers both FN ID and NAS sign-in, and the address, port and HTTPS can all be configured directly.',
            },
            {
                q: 'Can I use the fnOS self-signed certificate over HTTPS?',
                a: 'Yes. When the server certificate fails verification (self-signed, expired, or a mismatched domain), a dialog appears offering to trust this certificate, trust it for this session only, or cancel. Once trusted, the certificate fingerprint is remembered and you are asked again when the server changes certificates; trusted certificates can be managed under “Settings → Privacy & Security”.',
            },
            {
                q: 'Does it support direct-link playback?',
                a: 'Except for Dolby Vision Profile 5, playback defaults to direct linking at the original quality; in some failure cases it automatically falls back to HLS.',
            },
            {
                q: 'macOS warns “developer cannot be verified” or “app is damaged” on first launch?',
                a: 'Release builds are ad-hoc signed, so in most cases macOS only warns that the developer cannot be verified. If it says the app is damaged, move it into /Applications and run xattr -dr com.apple.quarantine /Applications/FlyNarwhal.app in Terminal. Versions installed through the in-app auto-update carry no quarantine flag. The download section says the same thing.',
            },
            {
                q: 'Is this project made by the fnOS team?',
                a: 'No. This project is a third-party media client built by fnOS enthusiasts and is not affiliated with the fnOS media team. Please be sure to comply with the relevant terms of service before using it.',
            },
        ],
    },

    cta: {
        kicker: 'Fly Narwhal 2.0 · A Fresh Start',
        titleLine1: 'Put fnOS media',
        titleLine2: 'on every screen you own.',
        lede: 'Open source, free, always evolving. Download it now and watch tonight’s show on it.',
        download: 'Download',
        getAuthCode: 'Get auth code',
        linksAria: 'Project links',
        repoClient: 'Client repo',
        repoServer: 'Server repo',
        allReleases: 'All releases',
    },

    footer: {
        contributorsAria: 'Contributors',
        contributors: 'Contributors',
        commitsTip: '{login} · {commits} commits',
        moreContributors: '{count} more contributors',
        viewAllContributors: 'View all contributors',
        contributorsCount: '{count} in total · contribution graph →',
        bannerAria: 'Fly Narwhal × XIAOBO NETWORK × fnOS Developer Open Platform FNOSP',
        tagline: 'A third-party desktop client for fnOS media services',
        linksAria: 'Related links',
        links: {
            clientRepo: 'Client repo',
            serverRepo: 'Server repo',
            allReleases: 'All releases',
            changelog: 'Changelog',
            issues: 'Report an issue',
            credits: 'Acknowledgements',
            guide: 'User guide',
        },
        disclaimer: 'This project is a third-party media client built by fnOS enthusiasts and is not affiliated with the fnOS media team. Please be sure to comply with the relevant terms of service before using it.',
        copyright: '© {year} FNOSP · Released under the AGPL-3.0 open-source license',
    },

    credits: {
        backHome: 'Back to home',
        crumb: 'Fly Narwhal · Acknowledgements',
        eyebrow: 'Acknowledgements',
        title: 'Standing on the shoulders of open source.',
        lede: 'Fly Narwhal is lifted up by {count} open-source projects — from the decoding core to the window transparency effects. This list records every library referenced or used, and the role it plays in the product.',
        licenseBefore: 'Fly Narwhal itself is open-sourced under the',
        licenseLink: 'AGPL-3.0',
        licenseMiddle: 'license. The projects above each follow their own original licenses; if anything is missing or wrong, corrections are welcome via a',
        licenseLinkTail: 'GitHub submission',
        licenseAfter: '.',
        backToHome: 'Back to the Fly Narwhal home page',
        groups: {
            clientFramework: 'Client · App framework',
            clientMedia: 'Client · Playback & networking',
            server: 'Server',
            webFrontend: 'Server · Web frontend',
        },
        notes: {
            flutter: 'Cross-platform UI framework',
            fluentUi: 'Windows Fluent Design-style controls',
            liquidGlass: 'Liquid Glass material controls',
            acrylic: 'Window acrylic / mica transparency effects',
            windowManager: 'Frameless windows and custom title bars',
            canvasDanmaku: 'Danmaku rendering component',
            lottie: 'Vector animation playback',
            flutterSvg: 'SVG icon and illustration rendering',
            mediaKit: 'Cross-platform audio/video playback solution',
            ffmpeg: 'Decoding core',
            dio: 'HTTP client',
            riverpod: 'State management and dependency injection',
            talker: 'Logging and network request debugging',
            sharedPreferences: 'Local preference storage',
            cryptography: 'Encryption, decryption and digest algorithms',
            inappwebview: 'Embedded web sign-in',
            springBoot: 'Application framework and embedded web container',
            graalvm: 'Native executable compilation',
            mybatisPlus: 'Data access layer',
            h2: 'Embedded database',
            mapstruct: 'Object-mapping code generation',
            jackson: 'JSON serialization',
            jsoup: 'HTML parsing',
            brotli: 'Response decompression',
            introSkipper: 'Jellyfin plugin that automatically detects and skips intros and outros',
            fnosTv: 'A web front end built on the fnOS media API; the danmaku feature is referenced from it',
            danmuApi: 'A multi-platform danmaku aggregation service referenced for the server-side danmaku fetching',
            vue: 'Web UI framework',
            vite: 'Frontend build tool and dev server',
            vitePluginVue: 'Vue single-file component support for Vite',
        },
    },

    guide: {
        crumb: 'Fly Narwhal · User guide',
        eyebrow: 'User guide',
        title: 'Set it up once, watch smarter.',
        lede: 'From connecting the Fly Narwhal server, to smart intro/outro analysis and skipping, to danmaku display and sources — this guide walks you through every related setting in the Fly Narwhal client.',
        backHome: 'Back to home',
        backToHome: 'Back to the Fly Narwhal home page',
        tocAria: 'Table of contents',
        toc: {
            server: 'Server setup',
            smartSkip: 'Smart intro/outro skip',
            danmaku: 'Danmaku setup',
        },
        server: {
            title: 'Enable and configure the Fly Narwhal server',
            lead: 'The Fly Narwhal server runs on your fnOS NAS and powers intro/outro analysis, danmaku and more. Smart skip and danmaku only become available after the client is connected to it.',
            prereq: 'Prerequisite: the latest version of the Fly Narwhal client is installed.',
            steps: [
                {
                    title: 'Turn on the server switch',
                    desc: 'Open “Settings” → “Server” in the client and turn on “Enable Fly Narwhal server”. The remaining configuration cards unfold once the switch is on.',
                },
                {
                    title: 'Fill in the server address',
                    desc: 'Enter the full server URL in “Fly Narwhal server address”, e.g. http://192.168.1.1:5365. Press Enter or click outside the field to save.',
                },
                {
                    title: 'Fill in the auth code',
                    desc: 'Click “Fill in auth code”. Open Fly Narwhal in fnOS, click “Get auth code” in the top-right corner, then copy the code into the dialog and press “OK”.',
                },
                {
                    title: 'Test the connection',
                    desc: 'Click the “Test” button next to the address field. On success you will see “Fly Narwhal server connected, current server version: x.y.z”; on failure the toast names the reason, such as an invalid or unreachable address.',
                },
            ],
            tips: [
                'Smart skip, danmaku and the other server features only activate when all three are in place: the enable switch, the server address and the auth code.',
                'The server configuration is separate from the client’s “Sign in” page (IP:Port, username/password, access code); they do not affect each other.',
            ],
        },
        smartSkip: {
            title: 'Analyze and skip intros/outros automatically',
            lead: 'The server analyzes the video and audio features of your episodes to locate intro and outro ranges; the player then skips them automatically — no more dragging the progress bar.',
            sections: [
                {
                    title: 'Step 1 · Start a smart analysis',
                    points: [
                        'On a show’s detail page click “⋯ More actions” → “Smart analyze intro/outro” to submit the whole series; the “⋯” menu on a season poster analyzes that season only.',
                        'The same entry is available in the “⋯” menus of the season detail page, Favorites and the media library.',
                        'After submitting you will see “Intro/outro analysis task submitted”; the season detail page shows “Smart analysis: {status}” (not analyzed, preparing, queued, analyzing, partially done, completed, failed) and polls automatically for the analysis status.',
                        'TV shows only — movies have no such entry; videos from cloud drives or STRM files cannot use smart analysis.',
                    ],
                },
                {
                    title: 'Step 2 · Turn on smart skip',
                    points: [
                        'Play any episode, open “Settings” → “Skip intro/outro” in the player control bar, and turn on “Smart skip intro/outro”.',
                        'When playback reaches the start of an intro-like segment it skips immediately and shows “Intro skipped automatically” at the bottom-left, with an “Undo 5” countdown — you have 5 seconds to jump back.',
                        'At the start of the outro a countdown reads “Skipping outro in N seconds” and can be cancelled; with auto-play next enabled it reads “Playing next episode in N seconds” instead (“Ending playback in N seconds” while the next episode is not ready yet).',
                        'Open “Smart skip settings” to pick which segment types to skip: intro, outro, recap, next-episode preview and ads.',
                    ],
                },
                {
                    title: 'Tune the analysis parameters (optional)',
                    points: [
                        'Under “Settings” → “Server” → “Smart skip configuration”, click “Configure” (client version ≥ 2.4.0 required).',
                        'Detection modes: five independent switches for detecting intros, outros, recaps, next-episode previews and ads.',
                        'Duration limits: minimum and maximum lengths for intros and outros (seconds); boundary offsets: intro start/end offset and outro end offset (±60 s).',
                        'Advanced: prefer fingerprint matching, fallback black-frame analyzer and anime mode; “Restore defaults” is always one click away.',
                    ],
                },
                {
                    title: 'Manual mode (no server needed)',
                    points: [
                        'On the same “Skip intro/outro” page, set “Intro length” and “Outro length” by hand (0–600 s).',
                        'One-click shortcuts while playing: “Set current time as intro” and “Set remaining time as outro”.',
                        '“Reset” in the top-right clears both lengths; the manual sliders are disabled while smart skip is on.',
                    ],
                },
            ],
            tips: [
                'Analysis results are stored per season — analyze once, reuse forever; the smart-skip switch is remembered per user.',
            ],
        },
        danmaku: {
            title: 'Danmaku configuration',
            lead: 'Danmaku are fetched and aggregated by the Fly Narwhal server. Once connected, you can turn danmaku on in the player and tune both the display and the sources to taste.',
            sections: [
                {
                    title: 'Turn danmaku on in the player',
                    points: [
                        'Complete the server setup in chapter 1 first — otherwise the player shows no danmaku controls at all.',
                        'Click the “Turn danmaku on” button in the player control bar to load danmaku for the current episode; click again to turn them off.',
                        'The neighbouring “Danmaku settings” button opens the display panel.',
                    ],
                },
                {
                    title: 'Display settings',
                    points: [
                        'Display area: five steps — 10% / 25% / 50% / 75% / 100%; opacity: continuous 0–100%.',
                        'Font size: 50%–170%; scroll speed: five steps from very slow to very fast.',
                        '“Advanced settings” offers “Sync danmaku speed with playback rate” and “Show danmaku debug info”.',
                    ],
                },
                {
                    title: 'Danmaku sources (optional)',
                    points: [
                        'Under “Settings” → “Server” (client version ≥ 2.4.0 required), two extra source types can be added.',
                        '“dandanplay source”: point at a dandanplay relay server to supplement anime danmaku; enter the relay address and save, or clear the field and save to disable it.',
                        '“Fallback danmaku servers”: tried in order whenever all direct sources come back empty; any third-party server compatible with the dandanplay protocol works. Add servers (name optional, address must start with http:// or https://), then toggle, edit or delete each one.',
                    ],
                },
            ],
            tips: [
                'Direct sources such as bilibili, iQiyi and Migu are aggregated by the server; the client has no per-platform switches.',
                'If loading fails you will see “Danmaku request failed, please check the Fly Narwhal server configuration” — revisit chapter 1 first.',
            ],
        },
    },

    authCode: {
        title: 'Auth code',
        exists: 'An auth code already exists. To regenerate it, go to File Manager -> App Files -> App.Native.flyNarwhalServer -> the data directory and delete the "auth_code" file, then request it again.',
        once: 'This auth code is shown only once — copy it now and keep it safe!',
        fetchFailed: 'Request failed',
        unknownError: 'Unknown error',
        requestFailed: 'Request failed',
        connectFailed:
            'Could not connect to the Fly Narwhal server (/api/config/auth-code). Make sure the server is running and try again; if this is only a static page preview, request the auth code after the server has started.',
        close: 'Close',
        copy: 'Copy',
        copied: 'Copied',
    },

    release: {
        viewChangelog: 'View changelog on GitHub',
        loading: 'Fetching the latest version…',
        versionLatest: 'Latest version {tag} · View changelog',
        published: 'Current latest version {tag}, published on {date}.',
        allArchs: 'All architectures',
        gotoDownloadPage: 'Go to the download page',
        gotoDownloadPageDesc: 'Pick a package on GitHub Releases',
        formats: {
            exe: { name: 'Windows installer', desc: 'One-click install on this machine' },
            zip: { name: 'Portable archive', desc: 'Unzip and run, no install needed' },
            dmg: { name: 'Disk image', desc: 'Drag into Applications to use' },
            deb: { name: 'Debian / Ubuntu', desc: 'Install via apt / dpkg' },
            rpm: { name: 'RHEL / Fedora', desc: 'Install via dnf / yum' },
            zst: { name: 'Arch Linux', desc: 'Install via pacman' },
            appimage: { name: 'Universal format', desc: 'Single file, runs on any distribution' },
        },
    },

    theme: {
        aria: 'Theme',
        system: 'Follow system',
        light: 'Light',
        dark: 'Dark',
        labelSystem: 'Theme: follow system (currently {current})',
        label: 'Theme: {current}',
    },
}
