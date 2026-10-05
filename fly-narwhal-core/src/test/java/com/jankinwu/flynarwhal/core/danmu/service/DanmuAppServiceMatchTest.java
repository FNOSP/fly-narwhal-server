package com.jankinwu.flynarwhal.core.danmu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.config.DanmuMatchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the configurable match enhancements: title mapping, noise stripping,
 * strict prefix matching, and the platform-order override.
 */
class DanmuAppServiceMatchTest {

    private DanmuAppService serviceWith(DanmuMatchProperties props) {
        return new DanmuAppService(null, null, new ObjectMapper(), props, null, null);
    }

    private DanmuAppService defaultService() {
        return serviceWith(new DanmuMatchProperties());
    }

    // ---- prepareSearchTitle ----

    @Test
    void titleMappingReplacesExactTitles() {
        DanmuMatchProperties props = new DanmuMatchProperties();
        props.setTitleMapping(List.of("唐朝诡事录->唐朝诡事录之西行", "国色芳华 -> 锦绣芳华"));
        DanmuAppService svc = serviceWith(props);
        assertEquals("唐朝诡事录之西行", svc.prepareSearchTitle("唐朝诡事录"));
        assertEquals("锦绣芳华", svc.prepareSearchTitle("国色芳华"));
        // Only whole-title exact matches are replaced.
        assertEquals("唐朝诡事录第二季", svc.prepareSearchTitle("唐朝诡事录第二季"));
    }

    @Test
    void noisePatternsAreStripped() {
        DanmuAppService svc = defaultService();
        assertEquals("百花杀", svc.prepareSearchTitle("百花杀（真彩）"));
        assertEquals("某剧", svc.prepareSearchTitle("【4K】某剧"));
        assertEquals("某剧", svc.prepareSearchTitle("某剧(完整版)"));
    }

    @Test
    void strippingToEmptyFallsBackToMappedTitle() {
        DanmuMatchProperties props = new DanmuMatchProperties();
        props.setNoisePatterns(List.of("全部"));
        DanmuAppService svc = serviceWith(props);
        assertEquals("全部", svc.prepareSearchTitle("全部"));
    }

    @Test
    void invalidNoiseRegexIsSkippedWithoutCrash() {
        DanmuMatchProperties props = new DanmuMatchProperties();
        props.setNoisePatterns(List.of("[unclosed", "真彩"));
        DanmuAppService svc = serviceWith(props);
        assertEquals("百花杀", svc.prepareSearchTitle("百花杀真彩"));
    }

    // ---- titleMatches ----

    @Test
    void looseModeKeepsContainsBehavior() {
        DanmuAppService svc = defaultService();
        assertTrue(svc.titleMatches("古惑仔3之只手遮天", "遮天"));
        assertTrue(svc.titleMatches("遮天 第一季", "遮天"));
        assertFalse(svc.titleMatches("某其他剧", "遮天"));
    }

    @Test
    void strictModeRequiresPrefix() {
        DanmuMatchProperties props = new DanmuMatchProperties();
        props.setStrictTitle(true);
        DanmuAppService svc = serviceWith(props);
        assertFalse(svc.titleMatches("古惑仔3之只手遮天", "遮天"));
        assertTrue(svc.titleMatches("遮天", "遮天"));
        assertTrue(svc.titleMatches("遮天 第一季", "遮天"));
        assertTrue(svc.titleMatches("遮天之少年歌行", "遮天"));
    }

    @Test
    void multiwordQueryUsesFirstToken() {
        DanmuAppService svc = defaultService();
        assertTrue(svc.titleMatches("庆余年 第一季", "庆余年 2019"));
    }

    // ---- platformPriority ----

    @Test
    void builtinOrderAppliesWhenUnconfigured() {
        DanmuAppService svc = defaultService();
        assertTrue(svc.platformPriority("https://v.qq.com/x/cover/a/b.html")
                < svc.platformPriority("https://www.iqiyi.com/v_x.html"));
        assertTrue(svc.platformPriority("https://www.iqiyi.com/v_x.html")
                < svc.platformPriority("https://www.bilibili.com/bangumi/play/ss1"));
        assertEquals(10, svc.platformPriority("https://www.migu.cn/x"));
    }

    @Test
    void configuredOrderOverridesBuiltin() {
        DanmuMatchProperties props = new DanmuMatchProperties();
        props.setPlatformOrder(List.of("bilibili", "qq"));
        DanmuAppService svc = serviceWith(props);
        assertEquals(0, svc.platformPriority("https://www.bilibili.com/bangumi/play/ss1"));
        assertEquals(1, svc.platformPriority("https://v.qq.com/x/cover/a/b.html"));
        // Unlisted platforms rank behind every listed one.
        assertEquals(2, svc.platformPriority("https://www.iqiyi.com/v_x.html"));
    }

    @Test
    void unknownKeysWorkAsDomainFragments() {
        DanmuMatchProperties props = new DanmuMatchProperties();
        props.setPlatformOrder(List.of("migu", "qq"));
        DanmuAppService svc = serviceWith(props);
        assertEquals(0, svc.platformPriority("https://www.migu.cn/video/x"));
        assertEquals(1, svc.platformPriority("https://v.qq.com/x/cover/a/b.html"));
    }

    // ---- config string parsing ----

    @Test
    void propertiesParseDelimitedConfigStrings() {
        DanmuMatchProperties props = new DanmuMatchProperties(
                "bilibili, qq", "唐朝诡事录->唐朝诡事录之西行;国色芳华->锦绣芳华",
                "广告;;预告", true);
        assertEquals(List.of("bilibili", "qq"), props.getPlatformOrder());
        assertEquals(2, props.getTitleMapping().size());
        assertEquals(List.of("广告", "预告"), props.getNoisePatterns());
        assertTrue(props.isStrictTitle());
    }

    @Test
    void emptyNoiseConfigKeepsBuiltinDefault() {
        DanmuMatchProperties props = new DanmuMatchProperties("", "", "", false);
        assertEquals(DanmuMatchProperties.DEFAULT_NOISE_PATTERNS, props.getNoisePatterns());
        DanmuAppService svc = serviceWith(props);
        assertEquals("百花杀", svc.prepareSearchTitle("百花杀（真彩）"));
    }
}
