// Open-source projects referenced or used by FlyNarwhal, grouped by role.
// Rendered on the standalone #/credits page; the footer only links to it.
//
// `name`/`url` are proper nouns and URLs and stay literal. The group label and
// each project's role note live in the i18n catalog (`credits.groups` keyed by
// `groupKey`, `credits.notes` keyed by `noteKey`) so they follow the UI
// language. `creditCount` is just the total, used in the intro sentence.
export const creditGroups = [
    { groupKey: 'clientFramework', items: [
        { name: 'Flutter', url: 'https://flutter.dev', noteKey: 'flutter' },
        { name: 'fluent_ui', url: 'https://github.com/bdlukaa/fluent_ui', noteKey: 'fluentUi' },
        { name: 'liquid_glass_widgets', url: 'https://github.com/liquid-glass-widgets/liquid_glass_widgets', noteKey: 'liquidGlass' },
        { name: 'flutter_acrylic', url: 'https://github.com/kikuchy/flutter_acrylic', noteKey: 'acrylic' },
        { name: 'window_manager', url: 'https://github.com/leanflutter/window_manager', noteKey: 'windowManager' },
        { name: 'canvas_danmaku', url: 'https://github.com/Predidit/canvas_danmaku', noteKey: 'canvasDanmaku' },
        { name: 'lottie', url: 'https://github.com/xvrh/lottie-flutter', noteKey: 'lottie' },
        { name: 'flutter_svg', url: 'https://github.com/dnfield/flutter_svg', noteKey: 'flutterSvg' },
    ]},
    { groupKey: 'clientMedia', items: [
        { name: 'media_kit / libmpv', url: 'https://github.com/media-kit/media-kit', noteKey: 'mediaKit' },
        { name: 'FFmpeg', url: 'https://ffmpeg.org', noteKey: 'ffmpeg' },
        { name: 'dio', url: 'https://github.com/cfug/dio', noteKey: 'dio' },
        { name: 'Riverpod', url: 'https://github.com/rrousselGit/riverpod', noteKey: 'riverpod' },
        { name: 'talker', url: 'https://github.com/Frezyx/talker', noteKey: 'talker' },
        { name: 'shared_preferences', url: 'https://github.com/flutter/packages/tree/main/packages/shared_preferences', noteKey: 'sharedPreferences' },
        { name: 'cryptography', url: 'https://github.com/dint-dev/cryptography', noteKey: 'cryptography' },
        { name: 'flutter_inappwebview', url: 'https://github.com/pichillilorenzo/flutter_inappwebview', noteKey: 'inappwebview' },
    ]},
    { groupKey: 'server', items: [
        { name: 'Spring Boot', url: 'https://spring.io/projects/spring-boot', noteKey: 'springBoot' },
        { name: 'GraalVM Native Image', url: 'https://www.graalvm.org', noteKey: 'graalvm' },
        { name: 'MyBatis-Plus', url: 'https://github.com/baomidou/mybatis-plus', noteKey: 'mybatisPlus' },
        { name: 'H2 Database', url: 'https://h2database.com', noteKey: 'h2' },
        { name: 'MapStruct', url: 'https://mapstruct.org', noteKey: 'mapstruct' },
        { name: 'Jackson', url: 'https://github.com/FasterXML/jackson', noteKey: 'jackson' },
        { name: 'Jsoup', url: 'https://jsoup.org', noteKey: 'jsoup' },
        { name: 'Brotli', url: 'https://github.com/google/brotli', noteKey: 'brotli' },
        { name: 'intro-skipper', url: 'https://github.com/intro-skipper/intro-skipper', noteKey: 'introSkipper' },
        { name: 'fnos-tv', url: 'https://github.com/thshu/fnos-tv', noteKey: 'fnosTv' },
        { name: 'danmu_api', url: 'https://github.com/huangxd-/danmu_api', noteKey: 'danmuApi' },
    ]},
    { groupKey: 'webFrontend', items: [
        { name: 'Vue', url: 'https://vuejs.org', noteKey: 'vue' },
        { name: 'Vite', url: 'https://vite.dev', noteKey: 'vite' },
        { name: '@vitejs/plugin-vue', url: 'https://github.com/vitejs/vite-plugin-vue', noteKey: 'vitePluginVue' },
    ]},
]

export const creditCount = creditGroups.reduce((n, g) => n + g.items.length, 0)
