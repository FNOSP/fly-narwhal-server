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
        guide: '使用说明',
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
                desc: '全新设计语言，Liquid Glass 液态玻璃质感配合现代布局，整体更通透。',
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
                alt: '飞鲸影视登录页，Liquid Glass 液态玻璃质感',
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

    danmu: {
        eyebrow: '弹幕',
        h2Line1: '更多片源，',
        h2Line2: '更准的匹配与更稳的抓取',
        lede: '片源扩充到九个平台再加一个第三方兜底，标题匹配可以按需调整，抓取到的弹幕还会经过一条配置即时生效的后处理管线。',
        items: [
            {
                tag: '片源',
                title: '九个平台，外加第三方兜底',
                desc: '新增咪咕视频、弹弹play（官方服务与公共中继两个通道，可各自开关并指定首选，中继无需开放平台凭据），以及一个弹弹play 兼容的第三方兜底弹幕服务器。',
            },
            {
                tag: '匹配',
                title: '标题匹配可配置',
                desc: '支持自定义标题映射、搜索前剔除噪声词、严格标题模式，并可指定各平台的搜索优先级。',
            },
            {
                tag: '后处理',
                title: '弹幕后处理管线',
                desc: '弹幕下发前经过时间轴偏移、同文本去重、屏蔽词过滤、限量采样、模式转换与颜色重映射；配置改动即时生效，处理失败自动降级为原始结果。',
            },
            {
                tag: '缓存',
                title: '缓存分级',
                desc: '空结果不再写入缓存，稀疏结果 1 小时过期、完整结果缓存 30 天；一次失败或过早的拉取不会再被长期钉死成「无弹幕」。',
            },
            {
                tag: '稳定',
                title: '抓取更稳',
                desc: '各平台源共享一个有界线程池，分段拉取 60 秒超时，单个卡死的抓取不再拖垮整集；优酷令牌过期自动刷新重试，搜狐按真实时长补齐分段。',
            },
            {
                tag: '修复',
                title: '一批拉取与显示修复',
                desc: '修复哔哩哔哩分段拉取、爱奇艺解析与剧集定位、优酷视频 ID、豆瓣 ID 解析、整季共用单集弹幕与错误兜底到第一集等问题；弹幕也不再于开播前抢跑显示。',
            },
        ],
        platforms: ['哔哩哔哩', '爱奇艺', '优酷', '腾讯视频', '芒果TV', '搜狐视频', '咪咕视频', '弹弹play', '第三方兜底'],
    },

    skip: {
        eyebrow: '智能跳过',
        h2Line1: '片头、前情提要、下集预告，',
        h2Line2: '都可以跳过',
        lede: '跳过范围从片头片尾扩展到前情提要、下集预告与中插广告；换过的片源会自动重新分析。',
        caption: '播放器内的「智能跳过配置」：每一类都可单独开关，自动跳过后仍可撤销。',
        items: [
            {
                tag: '识别',
                title: '更多可跳过的片段',
                desc: '前情提要与下集预告加入智能分析；单集还支持标记多个中插广告段，片头片尾跳过覆盖更多片源。',
            },
            {
                tag: '精度',
                title: '检测更准',
                desc: '检测误检更少，并修复了章节名正则自首版起从未匹配成功、片头片尾标记静默失效的问题。',
            },
            {
                tag: '重分析',
                title: '换源自动重分析',
                desc: '分析记录会保存媒体文件的修改时间，同路径下的文件被替换后自动作废旧指纹并重新分析，无需手动清理。',
            },
            {
                tag: '缓存',
                title: '指纹缓存更聪明',
                desc: '指纹缓存按分析窗口失效，只有影响窗口的配置变化才会重建指纹；探测不到时长的剧集不再进入分析器白跑。',
            },
            {
                tag: '吸附',
                title: '关键帧吸附更可靠',
                desc: 'VP9 片源补上关键帧过滤，扫描日志不再吞掉关键帧输出，吸附点被裁剪回搜索窗口内。',
            },
            {
                tag: '设置',
                title: '一键设点',
                desc: '播放中可把当前时刻一键设为片头结束点或片尾开始点；跳过开关逐项即时生效，扫描超时也可配置。',
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
            { title: '音频流直出', desc: '开启 HDMI / S-PDIF 直通后，AC3、DTS、TrueHD 等压缩音轨以原始码流交给功放解码，环绕声场原样保留；仅作用于直连播放的原片音轨，转码音频自动回退本地解码。' },
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
        h2: '从 470 MB 到 120 MB，功能一个不少',
        lede: '负责弹幕、片头片尾分析与授权的服务端经过了两轮深度瘦身：内存占用降到原来的约四分之一，启动只要约 0.2 秒，功能没有任何削减。',
        milestones: [
            { label: '优化前', value: '≈ 472 MB', note: '尚未瘦身时的基线' },
            { label: '原生二进制改造', value: '≈ 165 MB', note: '改造为原生程序，启动约 0.2 秒' },
            { label: '内存调优后 · 当前', value: '≈ 120 MB', note: '含程序本身与系统组件共约 120 MB；其中数据区设了 64 MB 上限，实测只用约 24 MB' },
        ],
        measures: [
            { title: '按需启动', desc: '各个功能模块改为用到时才加载，启动时不再一次性全部初始化。' },
            { title: '数据库缓存瘦身', desc: '把数据库自带的缓存上限从 64 MB 压到 8 MB，不再吃掉大半内存。' },
            { title: '精简后台线程', desc: '同时处理请求的线程从 200 降到 24，对 NAS 足够用，空闲线程占的内存随之释放。' },
            { title: '内存上限管控', desc: '给程序划定 64 MB 的内存上限，空闲内存及时还给系统，而不是越占越多。' },
            { title: '查询更轻', desc: '查询分析结果时不再拖带约 50 KB 的无用数据；48 路并发下内存峰值从 35 MB 降到 30.5 MB，内存整理次数减半。' },
            { title: '免 Java 环境', desc: '发布物改为原生可执行程序，NAS 上不再需要安装 Java；两种处理器架构各自独立构建发布。' },
        ],
        footnote: '实测数据：同一台 NAS、768 集的媒体库；24/48 路并发压测下内存峰值 35 MB，未触及 64 MB 上限，空闲时不再触发内存整理。',
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
            guide: '使用说明',
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
            liquidGlass: 'Liquid Glass 液态玻璃材质控件',
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
            danmuApi: '多平台弹幕聚合服务，服务端弹幕拉取参考',
            vue: '网页端 UI 框架',
            vite: '前端构建工具与开发服务器',
            vitePluginVue: 'Vite 的 Vue 单文件组件支持',
        },
    },

    guide: {
        crumb: '飞鲸影视 · 使用说明',
        eyebrow: '使用说明',
        title: '配置一次，智能观影。',
        lede: '从连接飞鲸服务端，到片头片尾智能分析与跳过，再到弹幕显示与弹幕源——这份说明带你走一遍飞鲸影视客户端里的全部相关设置。',
        backHome: '返回首页',
        backToHome: '返回飞鲸影视首页',
        tocAria: '目录',
        toc: {
            server: '配置服务端',
            smartSkip: '智能跳过片头片尾',
            danmaku: '弹幕配置',
        },
        server: {
            title: '启用并配置飞鲸服务端',
            lead: '飞鲸服务端运行在你的飞牛 NAS 上，负责片头片尾分析与弹幕等能力。在客户端里完成连接后，智能跳过与弹幕功能才会可用。',
            prereq: '前提：已安装最新版飞鲸影视客户端。',
            steps: [
                {
                    title: '开启服务端开关',
                    desc: '打开客户端「设置」→「服务器」，开启「启用飞鲸服务端」开关。开启后才会展开后续的配置卡片。',
                },
                {
                    title: '填写服务端地址',
                    desc: '在「飞鲸服务端地址」中填写完整的服务端 URL，例如 http://192.168.1.1:5365。回车或点击输入框以外区域即可保存。',
                },
                {
                    title: '填写授权码',
                    desc: '点击「填写授权码」。打开飞牛 OS 中的飞鲸影视，点击右上角「获取授权码」，复制授权码后粘贴到弹窗中，点击「确定」。',
                },
                {
                    title: '测试连接',
                    desc: '点击地址旁的「测试」按钮。成功时会提示「飞鲸服务端连接成功，当前服务端版本号：x.y.z」；失败时会提示地址无效或无法访问等原因。',
                },
            ],
            tips: [
                '启用开关、服务端地址与授权码三者齐备，智能跳过、弹幕等功能才会生效。',
                '服务端配置与客户端的「登录」页（IP:Port、用户名密码、访问码）是两回事，互不影响。',
            ],
        },
        smartSkip: {
            title: '智能分析与跳过片头片尾',
            lead: '服务端分析剧集画面与声音特征，识别出片头片尾区间；播放器据此自动跳过，追剧不再手动拖进度条。',
            sections: [
                {
                    title: '第一步 · 发起智能分析',
                    points: [
                        '在剧集详情页点击「⋯ 更多操作」→「智能分析片头/片尾」，为整部剧提交分析任务；也可以在剧季海报的「⋯」菜单中只分析单季。',
                        '分季详情页、我的收藏与媒体库的「⋯」菜单里同样提供该入口。',
                        '提交后会提示「片头/片尾分析任务已提交」；分季详情页显示「智能分析：状态」（未检测、准备中、等待中、分析中、部分成功、已完成、失败），页面自动轮询获取分析状态。',
                        '仅电视剧类支持，电影没有该入口；网盘或 STRM 视频无法使用智能分析。',
                    ],
                },
                {
                    title: '第二步 · 开启智能跳过',
                    points: [
                        '播放任意一集，在播放器控制栏打开「设置」→「跳过片头/片尾」，开启「智能跳过片头/片尾」。',
                        '播放到片头等片段起点时立即自动跳过，左下角提示「已自动跳过片头」，并附「撤销 5」倒计时，5 秒内可撤销跳回。',
                        '播放到片尾起点时显示倒计时「N 秒后跳过片尾」，可点「取消」；若开启了自动连播，则显示「N 秒后播放下一集」（下一集未就绪时为「N 秒后结束播放」）。',
                        '还可以进入「智能跳过配置」，勾选要跳过的片段类型：片头、片尾、前情提要、下集预告、广告。',
                    ],
                },
                {
                    title: '调整分析参数（可选）',
                    points: [
                        '在「设置」→「服务器」→「智能跳过配置」中点击「配置」（需客户端版本 ≥ 2.4.0）。',
                        '检测模式：检测片头、检测片尾、检测前情提要、检测下集预告、检测广告，五个独立开关。',
                        '时长限制：片头、片尾的最短与最长时长（秒）；边界偏移：片头开始/结束偏移、片尾结束偏移（±60 秒）。',
                        '高级选项：优先指纹匹配、备用黑帧分析器与动漫模式；随时可「恢复默认」。',
                    ],
                },
                {
                    title: '手动模式（不依赖服务端）',
                    points: [
                        '在同一个「跳过片头/片尾」页面里，可手动设置「片头时长」与「片尾时长」（0–600 秒）。',
                        '播放中还能一键定位：「将当前时间设为片头」「将当前剩余时长设为片尾」。',
                        '右上角「重置」可将两项时长清零；智能跳过开启时，手动滑杆暂时禁用。',
                    ],
                },
            ],
            tips: [
                '分析结果按剧季保存，一次分析即可反复使用；智能跳过开关按用户记忆。',
            ],
        },
        danmaku: {
            title: '弹幕功能配置',
            lead: '弹幕由飞鲸服务端统一聚合解析。连接服务端后，即可在播放器中开启弹幕，并按喜好调整显示效果与弹幕源。',
            sections: [
                {
                    title: '在播放器中开启弹幕',
                    points: [
                        '需先完成第一章的服务端配置，否则播放器中不会出现弹幕相关按钮。',
                        '播放器控制栏点击「开启弹幕」按钮即可加载当前剧集的弹幕，再次点击为「关闭弹幕」。',
                        '旁边的「弹幕设置」按钮打开显示设置面板。',
                    ],
                },
                {
                    title: '显示效果设置',
                    points: [
                        '显示区域：10% / 25% / 50% / 75% / 100% 五档；不透明度：0–100% 连续调节。',
                        '字号：50%–170%；滚动速度：极慢、较慢、适中、较快、极快五档。',
                        '「高级设置」中可开启「弹幕速度同步播放倍速」与「显示弹幕调试信息」。',
                    ],
                },
                {
                    title: '弹幕源配置（可选）',
                    points: [
                        '在「设置」→「服务器」中（需客户端版本 ≥ 2.4.0），可以补充两类额外弹幕源。',
                        '「弹弹play 弹幕源」：配置弹弹play 中转服务器，补充番剧弹幕；填入中转地址并保存，清空后保存即停用。',
                        '「兜底弹幕服务器」：所有直连弹幕源为空时，按顺序尝试已启用的服务器，支持兼容弹弹 play 协议的第三方弹幕服务器。可添加服务器（名称可选，地址需以 http:// 或 https:// 开头），并逐个开启/关闭、编辑或删除。',
                    ],
                },
            ],
            tips: [
                'bilibili、爱奇艺、咪咕等直连弹幕源由服务端聚合解析，客户端不提供逐平台开关。',
                '弹幕加载失败时会提示「请求弹幕接口失败，请检查飞鲸服务端配置」，请先回到第一章检查服务端配置。',
            ],
        },
    },

    authCode: {
        title: '授权码',
        exists: '授权码已存在。如需重新生成，请到文件管理 -> 应用文件 -> App.Native.flyNarwhalServer -> data 目录中，把「auth_code」文件删除后再次获取。',
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
