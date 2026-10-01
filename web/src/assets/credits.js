// Open-source projects referenced or used by FlyNarwhal, grouped by role.
// Rendered on the standalone #/credits page; the footer only links to it.
export const creditGroups = [
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

export const creditCount = creditGroups.reduce((n, g) => n + g.items.length, 0)
