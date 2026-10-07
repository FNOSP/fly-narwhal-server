// 繁體中文 —— 手寫，採台灣／香港用語，非簡繁字元替換。
// 詞彙對照：客戶端→用戶端、服務端→伺服器、視頻→影片、網絡→網路、
// 軟件→軟體、默認→預設、支持→支援、信息→資訊、登錄→登入、保存→儲存、
// 文件→檔案、高清→高畫質、內核→內核。
// 鏡像 zh-CN.js 的 key 樹（開發期由 useI18n.js 斷言）。
export default {
    meta: {
        title: '飛鯨影視 2.0 · 煥新出發 | 第三方飛牛影視桌面用戶端',
        description:
            '飛鯨影視 2.0 煥新出發——面向飛牛影視服務的第三方桌面用戶端。Flutter 原生重寫，內建 mpv 播放內核，涵蓋 Windows、macOS、Linux，支援智慧跳過片頭片尾與彈幕。',
    },

    common: {
        brand: '飛鯨影視',
        backHome: '返回首頁',
        language: '語言',
    },

    nav: {
        homeAria: '飛鯨影視首頁',
        logoAria: '飛鯨影視',
        linksAria: '頁面導覽',
        links: {
            renew: '2.0 煥新',
            screenshots: '介面預覽',
            features: '核心特色',
            player: '播放內核',
            server: '伺服器',
            changelog: '更新日誌',
            faq: '常見問題',
            guide: '使用說明',
        },
        getAuthCode: '取得授權碼',
        download: '下載用戶端',
    },

    hero: {
        iconAlt: '飛鯨影視應用程式圖示',
        brand: '飛鯨影視 2.0 · 煥新出發',
        titleLine1: '把飛牛影視',
        titleLine2: '裝進你的桌面',
        lede: '面向飛牛影視服務的第三方桌面用戶端。2.0 煥新出發：Flutter 原生重寫，內建 mpv 播放內核，涵蓋 Windows、macOS 與 Linux，支援智慧跳過片頭片尾與彈幕。',
        download: '下載用戶端',
        getAuthCode: '取得授權碼',
        guide: '使用說明',
        scrollHint: '向下捲動',
    },

    renew: {
        eyebrow: '2.0 · 煥新出發',
        titleLine1: '不是小修小補，',
        titleLine2: '是一次徹底的重做。',
        lede: '從播放內核到視覺語言，飛鯨影視 2.0 把桌面觀影體驗從頭再做了一遍。',
        pillars: [
            {
                title: 'Flutter 原生重寫',
                desc: '告別 JVM 執行環境，算繪與視窗排程更貼近系統原生，安裝檔更輕、執行更高效。',
            },
            {
                title: 'mpv 播放內核',
                desc: 'GPU 硬解直出螢幕，HDR 動態範圍原樣保留，CPU 佔用大幅下降。',
            },
            {
                title: 'Liquid Glass 視覺',
                desc: '全新設計語言，Liquid Glass 液態玻璃質感搭配現代版面，整體更通透。',
            },
            {
                title: '全平台涵蓋',
                desc: '新增 Linux 支援，三平台七格式，開箱即用。',
            },
        ],
    },

    screenshots: {
        eyebrow: '介面預覽',
        h2: '看起來，就是原生應用程式',
        lede: '2.0 煥新出發：用 Flutter 重新建構桌面用戶端，不再依賴 JVM 執行環境，視窗互動與算繪都更貼近系統本身。',
        switchAria: '預覽圖切換',
        shots: [
            {
                alt: '飛鯨影視登入頁，Liquid Glass 液態玻璃質感',
                title: '登入',
                desc: '支援飛牛 ID 與 NAS 登入，連線位址、連接埠與 HTTPS 安全存取都可直接設定。',
            },
            {
                alt: '飛鯨影視媒體庫首頁',
                title: '媒體庫',
                desc: '首頁彙整繼續觀看與媒體庫分類，電影、影集、動畫依分類與標籤瀏覽。',
            },
            {
                alt: '飛鯨影視播放器，含中英雙語字幕與播放控制列',
                title: '播放器',
                desc: '內建 mpv 播放內核，支援 GPU 硬解、PGS/SUP 字幕與 HDR 動態範圍。',
            },
            {
                alt: '飛鯨影視播放 8K 高碼率片源',
                title: '8K 與高碼率',
                desc: '原生解碼高碼率片源，搭配顯示卡硬解在桌面端直接播放，無需伺服器轉碼。',
            },
        ],
    },

    features: {
        eyebrow: '核心特色',
        h2Line1: '為桌面觀影',
        h2Line2: '重新做一遍',
        lede: '播放內核、字幕、跳過與彈幕都不是外掛功能，而是從頭依桌面端的使用方式設計的。',
        items: [
            {
                title: 'Flutter 原生重寫',
                desc: '2.x 全面重寫桌面端，告別 JVM 執行環境的額外開銷。啟動流程、播放器初始化與首幀算繪都經過最佳化，安裝檔更輕、開啟更快。',
            },
            {
                title: '內建 mpv 播放內核',
                desc: '基於 media_kit 與完整 libmpv，支援 GPU 硬解與本機解碼，macOS / Linux 封裝完整解碼函式庫，可直接處理 HDR、HLG 與 Dolby Vision。',
            },
            {
                title: 'PGS / SUP 字幕',
                desc: '支援 HDMV PGS 藍光字幕解碼，相容外掛與內封字幕，可在播放中隨時切換字幕與音軌。',
            },
            {
                title: '智慧跳過片頭片尾',
                desc: '由搭配的伺服器分析影集並給出片頭片尾區間，播放時自動跳過，追劇不再手動拖曳進度列。',
            },
            {
                title: '彈幕與播放進度',
                desc: '擷取並快取彈幕，搭配播放進度與繼續觀看，多裝置之間接續播放。',
            },
            {
                title: '三平台雙架構',
                desc: 'Windows x64、macOS Intel / Apple Silicon、Linux x64 / arm64 全平台涵蓋，提供 exe、dmg、deb、rpm、pkg.tar.zst 與 AppImage。',
            },
        ],
        stats: [
            { label: '桌面平台' },
            { label: '安裝檔格式' },
            { label: 'CPU 架構組合' },
        ],
    },

    player: {
        eyebrow: '播放內核',
        titleLine1: '影院級播放內核，',
        titleLine2: '裝進你的桌面。',
        lede: '基於 mpv 同款播放內核，8K、HDR 等大片直接在你的電腦上流暢播放，不佔伺服器運算資源，不卡頓。',
        videoAria: '飛鯨影視播放 8K 60fps HDR 高碼率片源',
        tag: '8K · 60fps · HDR · 硬解直出',
        note: '硬解播放 8K HDR 影片的能力將在未來版本推出，敬請期待',
        chipsAria: '播放能力',
        chips: [
            'GPU 硬解',
            'HDR',
            'HLG',
            'Dolby Vision',
            'PGS / SUP 字幕',
            '8K 高碼率',
            '直鏈播放',
            'HLS 回退',
            '音軌切換',
        ],
        compare: [
            {
                title: 'RGBA 回讀算繪',
                desc: '受限於 Compose Desktop 算繪機制，硬解畫面需回讀 CPU 再算繪。CPU 佔用高，HDR 片源被迫由伺服器轉成 SDR。',
            },
            {
                title: 'GPU 硬解直出',
                desc: 'libmpv 硬解輸出直達螢幕，CPU 佔用顯著降低，原片動態範圍原樣保留，HDR 不再失真。',
            },
        ],
    },

    danmu: {
        eyebrow: '彈幕',
        h2Line1: '更多片源，',
        h2Line2: '更準的匹配與更穩的抓取',
        lede: '片源擴充到九個平台再加一個第三方兜底，標題匹配可以按需調整，抓取到的彈幕還會經過一條設定即時生效的後處理管線。',
        items: [
            {
                tag: '片源',
                title: '九個平台，外加第三方兜底',
                desc: '新增咪咕視訊、彈彈play（官方服務與公共中繼兩個通道，可各自開關並指定首選，中繼無需開放平台憑證），以及一個彈彈play 相容的第三方兜底彈幕伺服器。',
            },
            {
                tag: '匹配',
                title: '標題匹配可設定',
                desc: '支援自訂標題映射、搜尋前剔除雜訊詞、嚴格標題模式，並可指定各平台的搜尋優先順序。',
            },
            {
                tag: '後處理',
                title: '彈幕後處理管線',
                desc: '彈幕下發前經過時間軸偏移、同文字去重、屏蔽詞過濾、限量取樣、模式轉換與顏色重映射；設定改動即時生效，處理失敗自動降級為原始結果。',
            },
            {
                tag: '快取',
                title: '快取分級',
                desc: '空結果不再寫入快取，稀疏結果 1 小時過期、完整結果快取 30 天；一次失敗或過早的抓取不會再被長期釘死成「無彈幕」。',
            },
            {
                tag: '穩定',
                title: '抓取更穩',
                desc: '各平台源共享一個有界執行緒池，分段抓取 60 秒逾時，單一卡死的抓取不再拖垮整集；優酷令牌過期自動刷新重試，搜狐依實際時長補齊分段。',
            },
            {
                tag: '修復',
                title: '一批抓取與顯示修復',
                desc: '修復哔哩哔哩分段抓取、愛奇藝解析與影集定位、優酷影片 ID、豆瓣 ID 解析、整季共用單集彈幕與錯誤兜底到第一集等問題；彈幕也不再於開播前搶跑顯示。',
            },
        ],
        platforms: ['哔哩哔哩', '愛奇藝', '優酷', '騰訊視訊', '芒果TV', '搜狐視訊', '咪咕視訊', '彈彈play', '第三方兜底'],
    },

    skip: {
        eyebrow: '智慧跳過',
        h2Line1: '片頭、前情提要、下集預告，',
        h2Line2: '都可以跳過',
        lede: '跳過範圍從片頭片尾擴展到前情提要、下集預告與中插廣告；換過的片源會自動重新分析。',
        caption: '播放器內的「智慧跳過設定」：每一類都可單獨開關，自動跳過後仍可取消。',
        items: [
            {
                tag: '識別',
                title: '更多可跳過的片段',
                desc: '前情提要與下集預告加入智慧分析；單集還支援標記多個中插廣告段，片頭片尾跳過覆蓋更多片源。',
            },
            {
                tag: '精度',
                title: '偵測更準',
                desc: '偵測誤偵更少，並修復了章節名正規表示式自首版起從未匹配成功、片頭片尾標記靜默失效的問題。',
            },
            {
                tag: '重分析',
                title: '換源自動重分析',
                desc: '分析記錄會儲存媒體檔案的修改時間，同路徑下的檔案被替換後自動作廢舊指紋並重新分析，無需手動清理。',
            },
            {
                tag: '快取',
                title: '指紋快取更聰明',
                desc: '指紋快取依分析視窗失效，只有影響視窗的設定變化才會重建指紋；偵測不到時長的影集不再進入分析器白跑。',
            },
            {
                tag: '吸附',
                title: '關鍵影格吸附更可靠',
                desc: 'VP9 片源補上關鍵影格過濾，掃描日誌不再吞掉關鍵影格輸出，吸附點被裁剪回搜尋視窗內。',
            },
            {
                tag: '設定',
                title: '一鍵設點',
                desc: '播放中可把目前時刻一鍵設為片頭結束點或片尾開始點；跳過開關逐項即時生效，掃描逾時也可設定。',
            },
        ],
    },

    more: {
        eyebrow: '更多能力',
        h2Line1: '細節之處，',
        h2Line2: '處處都是為觀影設計的',
        items: [
            { title: '資料夾檢視', desc: '直接依目錄瀏覽 NAS 上的媒體檔案，不經刮削也能找到想看的片源。' },
            { title: 'Live TV', desc: '直播頻道即點即播，轉台與音量都在同一個播放介面內完成。' },
            { title: '雲端硬碟影片播放', desc: '雲端硬碟裡的影片無需轉存，線上直接起播。' },
            { title: '媒體資訊面板', desc: '視訊流、音訊流與字幕軌的編碼、位元率、語言一目瞭然。' },
            { title: 'STRM 直連播放', desc: '對齊飛牛影視 Web 端流程，解析 STRM 檔案後直連雲端位址播放。' },
            { title: '播放詳細資訊', desc: '解碼方式、掉幀與緩衝狀態即時可見。' },
            { title: '進階播放選項', desc: '強制 H.264、SDR 色調映射，為舊裝置與特殊片源兜底。' },
            { title: '音訊串流直出', desc: '開啟 HDMI / S-PDIF 直通後，AC3、DTS、TrueHD 等壓縮音軌以原始位元流交給環繞擴大機解碼，環繞音場原樣保留；僅作用於直連播放的原片音軌，轉碼音訊自動回退本機解碼。' },
        ],
        liveChannel: 'CCTV-8 電視劇 · 1080i',
    },

    security: {
        eyebrow: '安全',
        titleLine1: '你的密碼，',
        titleLine2: '不再明文落盤。',
        lede: '登入紀錄中的密碼經認證加密後才寫入磁碟。本機檔案即使被拷走，也拿不到任何一個明文密碼。',
        vaultLabel: '登入紀錄 · NAS-Home',
        encrypted: '已加密',
        saltMeta: 'salt · nonce 隨機',
        guards: ['認證標籤校驗', '密文完整性檢查', '敏感記憶體零化', '金鑰異常自動清除', '竄改即失效'],
        steps: [
            { title: 'HKDF 衍生金鑰', desc: '主金鑰經 HKDF 衍生出獨立加密金鑰，不與原始密碼直接接觸。' },
            { title: '隨機 Salt / Nonce', desc: '每次加密使用全新隨機值，同一密碼也得到完全不同的密文。' },
            { title: 'AES-256-GCM 認證加密', desc: '加密同時產生認證標籤，密文遭竄改即可被偵測。' },
            { title: '密文安全落盤', desc: '磁碟上只有密文；寫入完成後，敏感記憶體立即零化。' },
        ],
    },

    server: {
        eyebrow: '伺服器',
        h2: '從 470 MB 到 120 MB，功能一項不缺',
        lede: '負責彈幕、片頭片尾分析與授權的伺服器經過了兩輪深度瘦身：記憶體占用降到原來的約四分之一，啟動只要約 0.2 秒，功能沒有任何刪減。',
        milestones: [
            { label: '優化前', value: '≈ 472 MB', note: '尚未瘦身時的基線' },
            { label: '原生二進位改造', value: '≈ 165 MB', note: '改造為原生程式，啟動約 0.2 秒' },
            { label: '記憶體調校後 · 目前', value: '≈ 120 MB', note: '含程式本身與系統元件共約 120 MB；其中資料區設了 64 MB 上限，實測只用約 24 MB' },
        ],
        measures: [
            { title: '按需啟動', desc: '各個功能模組改為用到時才載入，啟動時不再一次性全部初始化。' },
            { title: '資料庫快取瘦身', desc: '把資料庫自帶的快取上限從 64 MB 壓到 8 MB，不再吃掉大半記憶體。' },
            { title: '精簡背景執行緒', desc: '同時處理請求的執行緒從 200 降到 24，對 NAS 足夠用，閒置執行緒佔的記憶體隨之釋放。' },
            { title: '記憶體上限管控', desc: '為程式劃定 64 MB 的記憶體上限，閒置記憶體即時還給系統，而不是越佔越多。' },
            { title: '查詢更輕', desc: '查詢分析結果時不再拖帶約 50 KB 的無用資料；48 路併發下記憶體峰值從 35 MB 降到 30.5 MB，記憶體整理次數減半。' },
            { title: '免 Java 環境', desc: '發佈物改為原生可執行程式，NAS 上不再需要安裝 Java；兩種處理器架構各自獨立構建發佈。' },
        ],
        footnote: '實測資料：同一台 NAS、768 集的媒體庫；24/48 路併發壓測下記憶體峰值 35 MB，未觸及 64 MB 上限，閒置時不再觸發記憶體整理。',
    },

    platforms: {
        title: '三平台 · 雙架構 · 七格式',
        lede: '無論你的桌面是什麼組合，都有一個開箱即用的安裝檔在等著。',
        notes: {
            windows: '兩種版本均支援應用程式內更新，可攜版解壓即用',
            macos: '通用 .dmg，涵蓋兩代晶片',
            linux: 'deb / rpm / Arch / AppImage 全涵蓋',
        },
        badges: {
            mirror: '鏡像加速',
            autoUpdate: '自動更新',
            portable: '可攜版',
        },
    },

    download: {
        eyebrow: '下載',
        h2: '下載用戶端',
        h2WithTag: '下載用戶端 {tag}',
        lede: '選擇與你系統相符的安裝檔。下載經由鏡像加速，頁面上的連結始終指向最新穩定版。',
        tabsAria: '選擇平台',
        archLabel: '架構',
        archAria: '選擇架構',
        recommended: '推薦',
        download: '下載',
        empty: '該組合暫無可用的安裝檔，可前往',
        emptyTail: '查看。',
        macNoteToggle: '首次開啟被系統阻擋？',
        notes: {
            windows: '安裝檔與可攜版均支援應用程式內自動更新；可攜版解壓即用，無需安裝。',
            macos: '發佈版為臨時簽章，首次開啟若提示無法驗證開發者，可移除隔離標記後啟動。',
            linux: '依發行版選擇 deb / rpm / pkg.tar.zst，或使用通用的 AppImage。',
        },
        macNoteBody1: '發佈版本會對應用程式做臨時簽章，多數情況下只會提示「無法驗證開發者」。若提示「已損毀」，把應用程式放入',
        macNoteBody2: '後在終端機執行：',
        macNoteTail: '透過應用程式內自動更新安裝的版本不帶隔離標記，通常無需執行。',
        publishedNote: '{published}全部安裝檔及 SHA256SUMS 校驗檔案見 ',
        publishedNoteTail: '。',
        releasesPage: 'GitHub Releases 頁面',
    },

    changelog: {
        eyebrow: '更新日誌',
        h2Line1: '持續進化，',
        h2Line2: '每個版本都有據可查。',
        lede: '最新版本的完整變更，以及發佈以來的全部更新紀錄。',
        viewAll: '{channel}全部版本更新日誌',
        viewAllDesc: '從 {oldest} 到今天共 {count} 個版本，每一次發佈的完整紀錄都在這裡。',
        viewAllDescNoOldest: '共 {count} 個版本，每一次發佈的完整紀錄都在這裡。',
        sourceAria: '更新日誌來源',
        channels: {
            client: '用戶端',
            server: '伺服器',
        },
        categories: {
            Added: '新增',
            Changed: '改進',
            Fixed: '修正',
        },
        latestBadge: '最新版',
        error: '更新日誌暫時無法載入，可能是網路原因。',
        loadingAria: '更新日誌載入中',
        gotoGithub: '前往 GitHub 查看完整更新日誌 →',
        backHome: '返回首頁',
    },

    timeline: {
        crumb: '{brand} · {channel}更新日誌',
        eyebrow: '全部版本',
        title: '每一次發佈，都有跡可循。',
        lede: '共 {count} 個版本，每一次新增、改進與修正，都完整記錄在這裡。',
        backHome: '返回首頁',
        backToHome: '返回飛鯨影視首頁',
        latestBadge: '最新版',
        gotoGithub: '前往 GitHub 查看 →',
    },

    faq: {
        eyebrow: '常見問題',
        h2: '還想多瞭解一點？',
        items: [
            {
                q: '播放影片支援硬體解碼嗎？',
                a: '播放器基於 media_kit / libmpv，具備 GPU 加速能力。最終效果取決於平台、驅動程式、影片格式與系統環境；HDR、字幕與音軌切換等體驗仍在持續最佳化，請以實際版本表現為準。',
            },
            {
                q: '支援用 FN ID 或透過 NAS 登入嗎？',
                a: '支援。目前登入流程同時涵蓋 FN ID 與 NAS 登入兩種情境，連線位址、連接埠與 HTTPS 都可以直接設定。',
            },
            {
                q: '可以用飛牛 OS 的自簽憑證走 HTTPS 嗎？',
                a: '支援。當伺服器憑證校驗不通過（自簽、已過期或網域不符）時，會彈出視窗提示，可選擇信任此憑證、僅本次信任或取消存取。信任後會記錄憑證指紋，伺服器更換憑證時會重新詢問；已信任的憑證可在「設定 → 隱私與安全」中管理。',
            },
            {
                q: '支援直鏈播放嗎？',
                a: '除 Dolby Vision Profile 5 之外，原畫質下預設直鏈播放；部分播放失敗情境會自動回退到 HLS。',
            },
            {
                q: 'macOS 首次開啟提示「無法驗證開發者」或「已損毀」？',
                a: '發佈版對應用程式做了臨時簽章，多數情況只會提示「無法驗證開發者」。若提示「已損毀」，把應用程式放入 /Applications 後在終端機執行 xattr -dr com.apple.quarantine /Applications/FlyNarwhal.app 即可。透過應用程式內自動更新安裝的版本不帶隔離標記。下載區也有同樣的說明。',
            },
            {
                q: '這個專案是飛牛官方出品的嗎？',
                a: '不是。本專案為飛牛 OS 愛好者開發的第三方影視用戶端，與飛牛影視官方無關。使用前請確保遵守相關服務條款。',
            },
        ],
    },

    cta: {
        kicker: '飛鯨影視 2.0 · 煥新出發',
        titleLine1: '把飛牛影視，',
        titleLine2: '裝進你的每一塊螢幕。',
        lede: '開源、免費、持續進化。現在下載，今晚的劇就用它看。',
        download: '下載用戶端',
        getAuthCode: '取得授權碼',
        linksAria: '專案連結',
        repoClient: '用戶端倉庫',
        repoServer: '伺服器倉庫',
        allReleases: '全部版本',
    },

    footer: {
        contributorsAria: '貢獻者',
        contributors: '貢獻者',
        commitsTip: '{login} · {commits} 次提交',
        moreContributors: '還有 {count} 位貢獻者',
        viewAllContributors: '查看全部貢獻者',
        contributorsCount: '共 {count} 位 · 貢獻圖譜 →',
        bannerAria: '飛鯨影視 × XIAOBO NETWORK × 飛牛開發者開放平台 FNOSP',
        tagline: '面向飛牛影視服務的第三方桌面用戶端',
        linksAria: '相關連結',
        links: {
            clientRepo: '用戶端倉庫',
            serverRepo: '伺服器倉庫',
            allReleases: '全部版本',
            changelog: '更新日誌',
            issues: '問題回報',
            credits: '開源致謝',
            guide: '使用說明',
        },
        disclaimer: '本專案為飛牛 OS 愛好者開發的第三方影視用戶端，與飛牛影視官方無關。使用前請確保遵守相關服務條款。',
        copyright: '© {year} FNOSP · 基於 AGPL-3.0 開源授權發佈',
    },

    credits: {
        backHome: '返回首頁',
        crumb: '飛鯨影視 · 開源致謝',
        eyebrow: '開源致謝',
        title: '站在開源的肩膀上。',
        lede: '飛鯨影視由 {count} 個開源專案托舉而成——從解碼內核到視窗透明效果。這份名單記錄每一個被參考或使用的函式庫，以及它在產品中承擔的角色。',
        licenseBefore: '飛鯨影視自身基於',
        licenseLink: 'AGPL-3.0',
        licenseMiddle: '授權開源。上述專案各自遵循其原始授權條款；如有遺漏或錯誤，歡迎在',
        licenseLinkTail: 'GitHub 提交指正',
        licenseAfter: '。',
        backToHome: '返回飛鯨影視首頁',
        groups: {
            clientFramework: '用戶端 · 應用程式框架',
            clientMedia: '用戶端 · 播放與網路',
            server: '伺服器',
            webFrontend: '伺服器 · 網頁前端',
        },
        notes: {
            flutter: '跨平台 UI 框架',
            fluentUi: 'Windows Fluent Design 風格控制項',
            liquidGlass: 'Liquid Glass 液態玻璃材質控制項',
            acrylic: '視窗壓克力 / 雲母透明效果',
            windowManager: '無邊框視窗與自繪標題列',
            canvasDanmaku: '彈幕算繪元件',
            lottie: '向量動畫播放',
            flutterSvg: 'SVG 圖示與插圖算繪',
            mediaKit: '跨平台影音播放方案',
            ffmpeg: '解碼內核',
            dio: 'HTTP 用戶端',
            riverpod: '狀態管理與相依注入',
            talker: '日誌與網路請求偵錯',
            sharedPreferences: '本機設定儲存',
            cryptography: '加解密與摘要演算法',
            inappwebview: '內嵌網頁登入',
            springBoot: '應用程式框架與內嵌 Web 容器',
            graalvm: '原生執行檔編譯',
            mybatisPlus: '資料存取層',
            h2: '內嵌資料庫',
            mapstruct: '物件映射程式碼產生',
            jackson: 'JSON 序列化',
            jsoup: 'HTML 解析',
            brotli: '回應解壓縮',
            introSkipper: '自動偵測並跳過片頭片尾的 Jellyfin 外掛',
            fnosTv: '基於飛牛影視介面開發的網頁端，彈幕功能參考',
            danmuApi: '多平台彈幕聚合服務，伺服器彈幕抓取參考',
            vue: '網頁端 UI 框架',
            vite: '前端建置工具與開發伺服器',
            vitePluginVue: 'Vite 的 Vue 單一檔案元件支援',
        },
    },

    guide: {
        crumb: '飛鯨影視 · 使用說明',
        eyebrow: '使用說明',
        title: '設定一次，智慧觀影。',
        lede: '從連接飛鯨伺服器，到片頭片尾智慧分析與跳過，再到彈幕顯示與彈幕來源——這份說明帶你走一遍飛鯨影視用戶端裡的全部相關設定。',
        backHome: '返回首頁',
        backToHome: '返回飛鯨影視首頁',
        tocAria: '目錄',
        toc: {
            server: '設定伺服器',
            smartSkip: '智慧跳過片頭片尾',
            danmaku: '彈幕設定',
        },
        server: {
            title: '啟用並設定飛鯨伺服器',
            lead: '飛鯨伺服器執行在你的飛牛 NAS 上，負責片頭片尾分析與彈幕等能力。在用戶端裡完成連接後，智慧跳過與彈幕功能才會可用。',
            prereq: '前提：已安裝最新版飛鯨影視用戶端。',
            steps: [
                {
                    title: '開啟伺服器開關',
                    desc: '打開用戶端「設定」→「伺服器」，開啟「啟用飛鯨服務端」開關。開啟後才會展開後續的設定卡片。',
                },
                {
                    title: '填寫伺服器位址',
                    desc: '在「飛鯨服務端位址」中填寫完整的伺服器 URL，例如 http://192.168.1.1:5365。按 Enter 或點選輸入框以外區域即可儲存。',
                },
                {
                    title: '填寫授權碼',
                    desc: '點選「填寫授權碼」。打開飛牛 OS 中的飛鯨影視，點選右上角「取得授權碼」，複製授權碼後貼到對話框中，點選「確定」。',
                },
                {
                    title: '測試連線',
                    desc: '點選位址旁的「測試」按鈕。成功時會提示「飛鯨服務端連線成功，目前服務端版本號：x.y.z」；失敗時會提示位址無效或無法存取等原因。',
                },
            ],
            tips: [
                '啟用開關、伺服器位址與授權碼三者齊備，智慧跳過、彈幕等功能才會生效。',
                '伺服器設定與用戶端的「登入」頁（IP:Port、使用者名稱密碼、存取碼）是兩回事，互不影響。',
            ],
        },
        smartSkip: {
            title: '智慧分析與跳過片頭片尾',
            lead: '伺服器分析劇集畫面與聲音特徵，識別出片頭片尾區間；播放器據此自動跳過，追劇不再手動拖進度條。',
            sections: [
                {
                    title: '第一步 · 發起智慧分析',
                    points: [
                        '在劇集詳情頁點選「⋯ 更多操作」→「智慧分析片頭/片尾」，為整部劇提交分析任務；也可以在劇季海報的「⋯」選單中只分析單季。',
                        '分季詳情頁、我的收藏與媒體庫的「⋯」選單裡同樣提供該入口。',
                        '提交後會提示「片頭/片尾分析任務已提交」；分季詳情頁顯示「智慧分析：狀態」（未偵測、準備中、等待中、分析中、部分成功、已完成、失敗），頁面自動輪詢取得分析狀態。',
                        '僅電視劇類支援，電影沒有該入口；網盤或 STRM 影片無法使用智慧分析。',
                    ],
                },
                {
                    title: '第二步 · 開啟智慧跳過',
                    points: [
                        '播放任意一集，在播放器控制列打開「設定」→「跳過片頭/片尾」，開啟「智慧跳過片頭/片尾」。',
                        '播放到片頭等片段起點時立即自動跳過，左下角提示「已自動跳過片頭」，並附「撤銷 5」倒數，5 秒內可撤銷跳回。',
                        '播放到片尾起點時顯示倒數「N 秒後跳過片尾」，可點「取消」；若開啟了自動連播，則顯示「N 秒後播放下一集」（下一集未就緒時為「N 秒後結束播放」）。',
                        '還可以進入「智慧跳過設定」，勾選要跳過的片段類型：片頭、片尾、前情提要、下集預告、廣告。',
                    ],
                },
                {
                    title: '調整分析參數（選填）',
                    points: [
                        '在「設定」→「伺服器」→「智慧跳過設定」中點選「設定」（需用戶端版本 ≥ 2.4.0）。',
                        '偵測模式：偵測片頭、偵測片尾、偵測前情提要、偵測下集預告、偵測廣告，五個獨立開關。',
                        '時長限制：片頭、片尾的最短與最長時長（秒）；邊界偏移：片頭開始/結束偏移、片尾結束偏移（±60 秒）。',
                        '進階選項：優先指紋比對、備用黑幀分析器與動漫模式；隨時可「恢復預設」。',
                    ],
                },
                {
                    title: '手動模式（不依賴伺服器）',
                    points: [
                        '在同一個「跳過片頭/片尾」頁面裡，可手動設定「片頭時長」與「片尾時長」（0–600 秒）。',
                        '播放中還能一鍵定位：「將目前時間設為片頭」「將目前剩餘時長設為片尾」。',
                        '右上角「重設」可將兩項時長歸零；智慧跳過開啟時，手動滑桿暫時停用。',
                    ],
                },
            ],
            tips: [
                '分析結果依劇季儲存，一次分析即可反覆使用；智慧跳過開關依使用者記憶。',
            ],
        },
        danmaku: {
            title: '彈幕功能設定',
            lead: '彈幕由飛鯨伺服器統一聚合解析。連接伺服器後，即可在播放器中開啟彈幕，並依喜好調整顯示效果與彈幕來源。',
            sections: [
                {
                    title: '在播放器中開啟彈幕',
                    points: [
                        '需先完成第一章的伺服器設定，否則播放器中不會出現彈幕相關按鈕。',
                        '播放器控制列點選「開啟彈幕」按鈕即可載入目前劇集的彈幕，再次點選為「關閉彈幕」。',
                        '旁邊的「彈幕設定」按鈕打開顯示設定面板。',
                    ],
                },
                {
                    title: '顯示效果設定',
                    points: [
                        '顯示區域：10% / 25% / 50% / 75% / 100% 五檔；不透明度：0–100% 連續調節。',
                        '字型大小：50%–170%；捲動速度：極慢、較慢、適中、較快、極快五檔。',
                        '「進階設定」中可開啟「彈幕速度同步播放倍速」與「顯示彈幕偵錯資訊」。',
                    ],
                },
                {
                    title: '彈幕來源設定（選填）',
                    points: [
                        '在「設定」→「伺服器」中（需用戶端版本 ≥ 2.4.0），可以補充兩類額外彈幕來源。',
                        '「彈彈play 彈幕源」：設定彈彈play 中繼伺服器，補充番劇彈幕；填入中繼位址並儲存，清空後儲存即停用。',
                        '「兜底彈幕伺服器」：所有直連彈幕來源為空時，依順序嘗試已啟用的伺服器，支援相容彈彈 play 協定的第三方彈幕伺服器。可新增伺服器（名稱選填，位址需以 http:// 或 https:// 開頭），並逐個開啟/關閉、編輯或刪除。',
                    ],
                },
            ],
            tips: [
                'bilibili、愛奇藝、咪咕等直連彈幕來源由伺服器聚合解析，用戶端不提供逐平台開關。',
                '彈幕載入失敗時會提示「請求彈幕介面失敗，請檢查飛鯨服務端設定」，請先回到第一章檢查伺服器設定。',
            ],
        },
    },

    authCode: {
        title: '授權碼',
        exists: '授權碼已存在。如需重新產生，請刪除飛鯨伺服器執行檔所在目錄下的 auth_code 檔案後再次取得。',
        once: '此授權碼只顯示一次，請立即複製並妥善保存！',
        fetchFailed: '取得失敗',
        unknownError: '未知錯誤',
        requestFailed: '請求失敗',
        connectFailed:
            '無法連線到飛鯨伺服器（/api/config/auth-code）。請確認伺服器正在執行後重試；若目前只是靜態頁面預覽，請在伺服器啟動後再取得授權碼。',
        close: '關閉',
        copy: '複製',
        copied: '已複製',
    },

    release: {
        viewChangelog: '查看 GitHub 更新日誌',
        loading: '正在取得最新版本…',
        versionLatest: '最新版本 {tag} · 查看更新日誌',
        published: '目前最新版本 {tag}，發佈於 {date}。',
        allArchs: '全部架構',
        gotoDownloadPage: '前往下載頁面',
        gotoDownloadPageDesc: '在 GitHub Releases 選擇安裝檔',
        formats: {
            exe: { name: 'Windows 安裝檔', desc: '一鍵安裝到本機' },
            zip: { name: '可攜壓縮檔', desc: '解壓即用，無需安裝' },
            dmg: { name: '磁碟映像', desc: '拖入「應用程式」即可使用' },
            deb: { name: 'Debian / Ubuntu', desc: 'apt / dpkg 安裝' },
            rpm: { name: 'RHEL / Fedora', desc: 'dnf / yum 安裝' },
            zst: { name: 'Arch Linux', desc: 'pacman 安裝' },
            appimage: { name: '通用格式', desc: '單一檔案，任意發行版可執行' },
        },
    },

    theme: {
        aria: '主題',
        system: '跟隨系統',
        light: '淺色',
        dark: '深色',
        labelSystem: '主題：跟隨系統（目前{current}）',
        label: '主題：{current}',
    },
}
