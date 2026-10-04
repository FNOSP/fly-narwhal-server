// 简体中文 —— 源语言目录。所有 key 用稳定的英文标识，文案为仓库原有简体中文。
// en.js / zh-TW.js 必须镜像同一棵 key 树（开发期由 useI18n.js 断言）。
export default {
    meta: {
        title: '飞鲸影视 2.0 · 焕新出发 | 第三方飞牛影视桌面客户端',
        description:
            '飞鲸影视 2.0 焕新出发——面向飞牛影视服务的第三方桌面客户端。Flutter 原生重写，内置 mpv 播放内核，覆盖 Windows、macOS、Linux，支持智能跳过片头片尾与弹幕。',
    },

    common: {
        brand: '飞鲸影视',
        backHome: '返回首页',
        language: '语言',
    },

    nav: {
        homeAria: '飞鲸影视首页',
        logoAria: '飞鲸影视',
        linksAria: '页面导航',
        links: {
            renew: '2.0 焕新',
            screenshots: '界面预览',
            features: '核心特性',
            player: '播放内核',
            server: '服务端',
            changelog: '更新日志',
            faq: '常见问题',
        },
        getAuthCode: '获取授权码',
        download: '下载客户端',
    },

    hero: {
        iconAlt: '飞鲸影视应用图标',
        brand: '飞鲸影视 2.0 · 焕新出发',
        titleLine1: '把飞牛影视',
        titleLine2: '装进你的桌面',
        lede: '面向飞牛影视服务的第三方桌面客户端。2.0 焕新出发：Flutter 原生重写，内置 mpv 播放内核，覆盖 Windows、macOS 与 Linux，支持智能跳过片头片尾与弹幕。',
        download: '下载客户端',
        getAuthCode: '获取授权码',
        scrollHint: '向下滚动',
    },

    renew: {
        eyebrow: '2.0 · 焕新出发',
        titleLine1: '不是小修小补，',
        titleLine2: '是一次彻底的重做。',
        lede: '从播放内核到视觉语言，飞鲸影视 2.0 把桌面观影体验从头再做了一遍。',
        pillars: [
            {
                title: 'Flutter 原生重写',
                desc: '告别 JVM 运行时，渲染与窗口调度更贴近系统原生，安装包更轻、运行更高效。',
            },
            {
                title: 'mpv 播放内核',
                desc: 'GPU 硬解直出屏幕，HDR 动态范围原样保留，CPU 占用大幅下降。',
            },
            {
                title: 'Liquid Glass 视觉',
                desc: '全新设计语言，Acrylic 毛玻璃质感配合现代布局，整体更通透。',
            },
            {
                title: '全平台覆盖',
                desc: '新增 Linux 支持，三端七格式，开箱即用。',
            },
        ],
    },

    screenshots: {
        eyebrow: '界面预览',
        h2: '看起来，就是原生应用',
        lede: '2.0 焕新出发：用 Flutter 重新构建桌面端，不再依赖 JVM 运行时，窗口交互与渲染都更贴近系统本身。',
        switchAria: '预览图切换',
        shots: [
            {
                alt: '飞鲸影视登录页，Liquid Glass 毛玻璃质感',
                title: '登录',
                desc: '支持飞牛 ID 与 NAS 登录，连接地址、端口与 HTTPS 安全访问都可直接配置。',
            },
            {
                alt: '飞鲸影视媒体库首页',
                title: '媒体库',
                desc: '首页聚合继续观看与媒体库分类，电影、剧集、动漫按分类与标签浏览。',
            },
            {
                alt: '飞鲸影视播放器，含中英双语字幕与播放控制条',
                title: '播放器',
                desc: '内置 mpv 播放内核，支持 GPU 硬解、PGS/SUP 字幕与 HDR 动态范围。',
            },
            {
                alt: '飞鲸影视播放 8K 高码率片源',
                title: '8K 与高码率',
                desc: '原生解码高码率片源，配合显卡硬解在桌面端直接播放，无需服务端转码。',
            },
        ],
    },

    features: {
        eyebrow: '核心特性',
        h2Line1: '为桌面观影',
        h2Line2: '重新做一遍',
        lede: '播放内核、字幕、跳过与弹幕都不是外壳功能，而是从头按桌面端的用法设计的。',
        items: [
            {
                title: 'Flutter 原生重写',
                desc: '2.x 全面重写桌面端，告别 JVM 运行时的额外开销。启动链路、播放器初始化与首帧渲染都经过优化，安装包更轻、打开更快。',
            },
            {
                title: '内置 mpv 播放内核',
                desc: '基于 media_kit 与完整 libmpv，支持 GPU 硬解与本地解码，macOS / Linux 打包完整解码库，可直接处理 HDR、HLG 与 Dolby Vision。',
            },
            {
                title: 'PGS / SUP 字幕',
                desc: '支持 HDMV PGS 蓝光字幕解码，兼容外挂与内封字幕，可在播放中随时切换字幕与音轨。',
            },
            {
                title: '智能跳过片头片尾',
                desc: '由配套服务端分析剧集并给出片头片尾区间，播放时自动跳过，追剧不再手动拖进度条。',
            },
            {
                title: '弹幕与播放进度',
                desc: '拉取并缓存弹幕，配合播放进度与继续观看，多设备之间接续播放。',
            },
            {
                title: '三端双架构',
                desc: 'Windows x64、macOS Intel / Apple Silicon、Linux x64 / arm64 全平台覆盖，提供 exe、dmg、deb、rpm、pkg.tar.zst 与 AppImage。',
            },
        ],
        stats: [
            { label: '桌面平台' },
            { label: '安装包格式' },
            { label: 'CPU 架构组合' },
        ],
    },

    player: {
        eyebrow: '播放内核',
        titleLine1: '影院级播放内核，',
        titleLine2: '装进你的桌面。',
        lede: '基于 mpv 同款播放内核，8K、HDR 等大片直接在你的电脑上流畅播放，不占服务器计算资源，不卡顿。',
        videoAria: '飞鲸影视播放 8K 60fps HDR 高码率片源',
        tag: '8K · 60fps · HDR · 硬解直出',
        note: '硬解播放 8K HDR 视频的能力将在未来版本推出，敬请期待',
        chipsAria: '播放能力',
        chips: [
            'GPU 硬解',
            'HDR',
            'HLG',
            'Dolby Vision',
            'PGS / SUP 字幕',
            '8K 高码率',
            '直链播放',
            'HLS 回退',
            '音轨切换',
        ],
        compare: [
            {
                title: 'RGBA 回读渲染',
                desc: '受限于 Compose Desktop 渲染机制，硬解画面需回读 CPU 再渲染。CPU 占用高，HDR 片源被迫由服务端转成 SDR。',
            },
            {
                title: 'GPU 硬解直出',
                desc: 'libmpv 硬解输出直达屏幕，CPU 占用显著降低，原片动态范围原样保留，HDR 不再失真。',
            },
        ],
    },

    more: {
        eyebrow: '更多能力',
        h2Line1: '细节之处，',
        h2Line2: '处处都是为观影设计的',
        items: [
            { title: '文件夹视图', desc: '直接按目录浏览 NAS 上的媒体文件，不经刮削也能找到想看的片源。' },
            { title: 'Live TV', desc: '直播频道即点即播，换台与音量都在同一个播放界面内完成。' },
            { title: '网盘视频播放', desc: '网盘里的视频无需转存，在线直接起播。' },
            { title: '媒体信息面板', desc: '视频流、音频流与字幕轨的编码、码率、语言一目了然。' },
            { title: 'STRM 直连播放', desc: '对齐飞牛影视 Web 端流程，解析 STRM 文件后直连云端地址播放。' },
            { title: '播放详细信息', desc: '解码方式、丢帧与缓冲状态实时可见。' },
            { title: '进阶播放选项', desc: '强制 H.264、SDR 色调映射，为老设备与特殊片源兜底。' },
            { title: '流畅过渡动画', desc: '动画链路重构精简，页面切换与播放交互更顺滑。' },
        ],
        liveChannel: 'CCTV-8 电视剧 · 1080i',
    },

    security: {
        eyebrow: '安全',
        titleLine1: '你的密码，',
        titleLine2: '不再明文落盘。',
        lede: '登录历史中的密码经认证加密后才写入磁盘。本地文件即使被拷走，也拿不到任何一个明文口令。',
        vaultLabel: '登录历史 · NAS-Home',
        encrypted: '已加密',
        saltMeta: 'salt · nonce 随机',
        guards: ['认证标签校验', '密文完整性检查', '敏感内存零化', '密钥异常自动清除', '篡改即失效'],
        steps: [
            { title: 'HKDF 派生密钥', desc: '主密钥经 HKDF 派生出独立加密密钥，不与原始口令直接接触。' },
            { title: '随机 Salt / Nonce', desc: '每次加密使用全新随机值，同一密码也得到完全不同的密文。' },
            { title: 'AES-256-GCM 认证加密', desc: '加密同时生成认证标签，密文被篡改即可被检测。' },
            { title: '密文安全落盘', desc: '磁盘上只有密文；写入完成后，敏感内存立即零化。' },
        ],
    },

    server: {
        eyebrow: '服务端',
        h2: '客户端之外的一半',
        lede: '飞鲸影视服务端以 GraalVM 原生二进制形式发布，运行在飞牛 NAS 上，负责弹幕、片头片尾分析与授权。无需安装 Java，解压后即可运行。',
        capabilities: [
            {
                tag: '弹幕',
                title: '拉取与缓存',
                desc: '客户端请求弹幕时由服务端统一拉取并落库缓存，同一剧集重复播放不再重复请求源站，也避免了跨域与限流问题。',
            },
            {
                tag: '分析',
                title: '片头片尾检测',
                desc: '服务端用 ffmpeg 分析剧集章节与音画特征，计算片头片尾区间并写入数据库，客户端播放时据此自动跳过。',
            },
            {
                tag: '鉴权',
                title: '授权码',
                desc: '客户端与服务端之间用一次性展示的授权码建立信任，配合请求签名校验，避免服务端被未授权的客户端调用。',
            },
        ],
        diagramClient: '客户端',
        diagramServer: '飞鲸服务端',
        diagramClientSub: 'Windows · macOS · Linux',
        diagramServerSub: '飞牛 NAS · 原生二进制',
        logSteps: ['拉取弹幕', 'ffmpeg 分析', '缓存落库', '签名校验', '返回结果'],
    },

    platforms: {
        title: '三端 · 双架构 · 七格式',
        lede: '无论你的桌面是什么组合，都有一个开箱即用的安装包在等着。',
        notes: {
            windows: '两种版本均支持应用内更新，便携版解压即用',
            macos: '通用 .dmg，覆盖两代芯片',
            linux: 'deb / rpm / Arch / AppImage 全覆盖',
        },
        // 无扩展名的品牌词（.exe/.dmg 等）不翻译，仅这三项是文案。
        badges: {
            mirror: '镜像加速',
            autoUpdate: '自动更新',
            portable: '便携版',
        },
    },

    download: {
        eyebrow: '下载',
        h2: '下载客户端',
        h2WithTag: '下载客户端 {tag}',
        lede: '选择与你系统匹配的安装包。下载经由镜像加速，页面上的链接始终指向最新稳定版。',
        tabsAria: '选择平台',
        archLabel: '架构',
        archAria: '选择架构',
        recommended: '推荐',
        download: '下载',
        empty: '该组合暂无可用安装包，可前往',
        emptyTail: '查看。',
        macNoteToggle: '首次打开被系统阻止？',
        notes: {
            windows: '安装包与便携版均支持应用内自动更新；便携版解压即用，无需安装。',
            macos: '发布版为临时签名，首次打开若提示无法验证开发者，可移除隔离标记后启动。',
            linux: '按发行版选择 deb / rpm / pkg.tar.zst，或使用通用的 AppImage。',
        },
        // macOS 说明展开区（<code> 路径与命令为 token，不翻译）。
        macNoteBody1: '发布版本会对应用做临时签名，多数情况下只会提示“无法验证开发者”。若提示“已损坏”，把应用放入',
        macNoteBody2: '后在终端执行：',
        macNoteTail: '通过应用内自动更新安装的版本不带隔离标记，通常无需执行。',
        publishedNote: '{published}全部安装包及 SHA256SUMS 校验文件见 ',
        publishedNoteTail: '。',
        releasesPage: 'GitHub Releases 页面',
    },

    changelog: {
        eyebrow: '更新日志',
        h2Line1: '持续进化，',
        h2Line2: '每个版本都有据可查。',
        lede: '最新版本的完整变化，以及发布以来的全部更新记录。',
        viewAll: '{channel}全部版本更新日志',
        viewAllDesc: '从 {oldest} 到今天共 {count} 个版本，每一次发版的完整记录都在这里。',
        viewAllDescNoOldest: '共 {count} 个版本，每一次发版的完整记录都在这里。',
        sourceAria: '更新日志来源',
        channels: {
            client: '客户端',
            server: '服务端',
        },
        categories: {
            Added: '新增',
            Changed: '改进',
            Fixed: '修复',
        },
        latestBadge: '最新版',
        error: '更新日志暂时无法加载，可能是网络原因。',
        loadingAria: '更新日志加载中',
        gotoGithub: '前往 GitHub 查看完整更新日志 →',
        backHome: '返回首页',
    },

    timeline: {
        crumb: '{brand} · {channel}更新日志',
        eyebrow: '全部版本',
        title: '每一次发版，都有迹可循。',
        lede: '共 {count} 个版本，每一次新增、改进与修复，都完整记录在这里。',
        backHome: '返回首页',
        backToHome: '返回飞鲸影视首页',
        latestBadge: '最新版',
        gotoGithub: '前往 GitHub 查看 →',
    },

    faq: {
        eyebrow: '常见问题',
        h2: '还想多了解一点？',
        items: [
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
                a: '支持。当服务器证书校验不通过（自签名、已过期或域名不匹配）时，会弹框提示，可选择信任此证书、仅本次信任或取消访问。信任后会记录证书指纹，服务器更换证书时会重新询问；已信任的证书可在「设置 → 隐私与安全」中管理。',
            },
            {
                q: '支持直链播放吗？',
                a: '除 Dolby Vision Profile 5 之外，原画质下默认直链播放；部分播放失败场景会自动回退到 HLS。',
            },
            {
                q: 'macOS 首次打开提示“无法验证开发者”或“已损坏”？',
                a: '发布版对应用做了临时签名，多数情况只会提示“无法验证开发者”。若提示“已损坏”，把应用放入 /Applications 后在终端执行 xattr -dr com.apple.quarantine /Applications/FlyNarwhal.app 即可。通过应用内自动更新安装的版本不带隔离标记。下载区也有同样的说明。',
            },
            {
                q: '这个项目是飞牛官方出品的吗？',
                a: '不是。本项目为飞牛 OS 爱好者开发的第三方影视客户端，与飞牛影视官方无关。使用前请确保遵守相关服务条款。',
            },
        ],
    },

    cta: {
        kicker: '飞鲸影视 2.0 · 焕新出发',
        titleLine1: '把飞牛影视，',
        titleLine2: '装进你的每一块屏幕。',
        lede: '开源、免费、持续进化。现在下载，今晚的剧就用它看。',
        download: '下载客户端',
        getAuthCode: '获取授权码',
        linksAria: '项目链接',
        repoClient: '客户端仓库',
        repoServer: '服务端仓库',
        allReleases: '全部版本',
    },

    footer: {
        contributorsAria: '贡献者',
        contributors: '贡献者',
        commitsTip: '{login} · {commits} 次提交',
        moreContributors: '还有 {count} 位贡献者',
        viewAllContributors: '查看全部贡献者',
        contributorsCount: '共 {count} 位 · 贡献图谱 →',
        bannerAria: '飞鲸影视 × XIAOBO NETWORK × 飞牛开发者开放平台 FNOSP',
        tagline: '面向飞牛影视服务的第三方桌面客户端',
        linksAria: '相关链接',
        links: {
            clientRepo: '客户端仓库',
            serverRepo: '服务端仓库',
            allReleases: '全部版本',
            changelog: '更新日志',
            issues: '问题反馈',
            credits: '开源致谢',
        },
        disclaimer: '本项目为飞牛 OS 爱好者开发的第三方影视客户端，与飞牛影视官方无关。使用前请确保遵守相关服务条款。',
        copyright: '© {year} FNOSP · 基于 AGPL-3.0 开源协议发布',
    },

    credits: {
        backHome: '返回首页',
        crumb: '飞鲸影视 · 开源致谢',
        eyebrow: '开源致谢',
        title: '站在开源的肩膀上。',
        lede: '飞鲸影视由 {count} 个开源项目托举而成——从解码内核到窗口透明效果。这份名单记录每一个被参考或使用的库，以及它在产品中承担的角色。',
        licenseBefore: '飞鲸影视自身基于',
        licenseLink: 'AGPL-3.0',
        licenseMiddle: '协议开源。上述项目各自遵循其原始许可证；如有遗漏或错误，欢迎在',
        licenseLinkTail: 'GitHub 提交指正',
        licenseAfter: '。',
        backToHome: '返回飞鲸影视首页',
        groups: {
            clientFramework: '客户端 · 应用框架',
            clientMedia: '客户端 · 播放与网络',
            server: '服务端',
            webFrontend: '服务端 · 网页前端',
        },
        // 项目 role 说明，与 assets/credits.js 的 items 一一对应（按顺序）。
        notes: {
            flutter: '跨平台 UI 框架',
            fluentUi: 'Windows Fluent Design 风格控件',
            liquidGlass: 'Liquid Glass 毛玻璃材质控件',
            acrylic: '窗口亚克力 / 云母透明效果',
            windowManager: '无边框窗口与自绘标题栏',
            canvasDanmaku: '弹幕渲染组件',
            lottie: '矢量动画播放',
            flutterSvg: 'SVG 图标与插图渲染',
            mediaKit: '跨平台音视频播放方案',
            ffmpeg: '解码内核',
            dio: 'HTTP 客户端',
            riverpod: '状态管理与依赖注入',
            talker: '日志与网络请求调试',
            sharedPreferences: '本地配置存储',
            cryptography: '加解密与摘要算法',
            inappwebview: '内嵌网页登录',
            springBoot: '应用框架与内嵌 Web 容器',
            graalvm: '原生可执行文件编译',
            mybatisPlus: '数据访问层',
            h2: '内嵌数据库',
            mapstruct: '对象映射代码生成',
            jackson: 'JSON 序列化',
            jsoup: 'HTML 解析',
            brotli: '响应解压',
            introSkipper: '自动检测并跳过片头片尾的 Jellyfin 插件',
            fnosTv: '基于飞牛影视接口开发的网页端，弹幕功能参考',
            vue: '网页端 UI 框架',
            vite: '前端构建工具与开发服务器',
            vitePluginVue: 'Vite 的 Vue 单文件组件支持',
        },
    },

    authCode: {
        title: '授权码',
        exists: '授权码已存在。如需重新生成，请删除飞鲸服务端可执行文件所在目录下的 auth_code 文件后再次获取。',
        once: '此授权码只展示一次，请立即复制并妥善保存！',
        fetchFailed: '获取失败',
        unknownError: '未知错误',
        requestFailed: '请求失败',
        connectFailed:
            '无法连接到飞鲸服务端（/api/config/auth-code）。请确认服务端正在运行后重试；若当前只是静态页面预览，请在服务端启动后再获取授权码。',
        close: '关闭',
        copy: '复制',
        copied: '已复制',
    },

    release: {
        viewChangelog: '查看 GitHub 更新日志',
        loading: '正在获取最新版本…',
        versionLatest: '最新版本 {tag} · 查看更新日志',
        published: '当前最新版本 {tag}，发布于 {date}。',
        allArchs: '全部架构',
        gotoDownloadPage: '前往下载页面',
        gotoDownloadPageDesc: '在 GitHub Releases 选择安装包',
        formats: {
            exe: { name: 'Windows 安装包', desc: '一键安装到本机' },
            zip: { name: '便携压缩包', desc: '解压即用，无需安装' },
            dmg: { name: '磁盘映像', desc: '拖入「应用程序」即可使用' },
            deb: { name: 'Debian / Ubuntu', desc: 'apt / dpkg 安装' },
            rpm: { name: 'RHEL / Fedora', desc: 'dnf / yum 安装' },
            zst: { name: 'Arch Linux', desc: 'pacman 安装' },
            appimage: { name: '通用格式', desc: '单文件，任意发行版可运行' },
        },
    },

    theme: {
        aria: '主题',
        system: '跟随系统',
        light: '浅色',
        dark: '深色',
        labelSystem: '主题：跟随系统（当前{current}）',
        label: '主题：{current}',
    },
}
