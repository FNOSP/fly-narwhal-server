package com.jankinwu.flynarwhal.core.danmu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the per-stage post-processing pipeline: offset rules with
 * specificity, minute-bucket dedupe, blocked words (literal + regex),
 * equal-interval sampling, and mode/color conversion — on both payload
 * shapes (bare array and episode-keyed object).
 */
class DanmuPostProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DanmuPostProcessor processor(String offsetRules, int dedupeMinutes, String blockedWords,
                                         int limitK, boolean topBottom, String colorMode, String colorPool) {
        return new DanmuPostProcessor(MAPPER, offsetRules, dedupeMinutes, blockedWords,
                limitK, topBottom, colorMode, colorPool);
    }

    private DanmuPostProcessor plain() {
        return processor("", 1, "", 0, false, "default", "");
    }

    private static String entry(int time, String text) {
        return "{\"text\":\"" + text + "\",\"time\":" + time + ",\"mode\":1,\"color\":\"#FFFFFF\",\"border\":false,\"style\":{}}";
    }

    private static String arrayPayload(String... entries) {
        return "[" + String.join(",", entries) + "]";
    }

    // ---- offset rules ----

    @Test
    void parsesOffsetRulesAndIgnoresMalformed() {
        var rules = DanmuPostProcessor.parseOffsetRules("overlord/S01:90, re-zero/S02/E03:-10.5, broken, :5, x:abc");
        assertEquals(2, rules.size());
        assertEquals(90.0, rules.get("overlord/S01"));
        assertEquals(-10.5, rules.get("re-zero/S02/E03"));
    }

    @Test
    void mostSpecificOffsetRuleWins() {
        DanmuPostProcessor p = processor("show:5,show/S02:30,show/S02/E03:90", 0, "", 0, false, "default", "");
        assertEquals(5.0, p.resolveOffsetSeconds("show", "1", 1));
        assertEquals(30.0, p.resolveOffsetSeconds("show", "2", 1));
        assertEquals(90.0, p.resolveOffsetSeconds("show", "2", 3));
        assertEquals(0.0, p.resolveOffsetSeconds("other", "2", 3));
        assertEquals(0.0, p.resolveOffsetSeconds(null, null, null));
    }

    @Test
    void offsetShiftsTimeAndDropsNegativeEntries() throws Exception {
        DanmuPostProcessor p = processor("show:-10", 0, "", 0, false, "default", "");
        String out = p.process(arrayPayload(entry(5, "early"), entry(30, "late")), "show", null, null);
        JsonNode arr = MAPPER.readTree(out);
        assertEquals(1, arr.size(), "entry pushed below 0 must be dropped");
        assertEquals(20, arr.get(0).path("time").asInt());
    }

    // ---- dedupe ----

    @Test
    void dedupesIdenticalTextWithinMinuteBucket() throws Exception {
        String out = plain().process(arrayPayload(
                entry(10, "哈哈"), entry(20, "哈哈"), entry(70, "哈哈"), entry(15, " 哈哈 ")), null, null, null);
        JsonNode arr = MAPPER.readTree(out);
        assertEquals(2, arr.size(), "same bucket collapses (incl. trimmed), next minute kept");
        assertEquals(10, arr.get(0).path("time").asInt());
        assertEquals(70, arr.get(1).path("time").asInt());
    }

    @Test
    void dedupeDisabledKeepsDuplicates() throws Exception {
        DanmuPostProcessor p = processor("", 0, "", 0, false, "default", "");
        String out = p.process(arrayPayload(entry(10, "哈"), entry(20, "哈")), null, null, null);
        assertEquals(2, MAPPER.readTree(out).size());
        assertFalse(p.isActive());
    }

    // ---- blocked words ----

    @Test
    void blockedWordsSupportLiteralAndRegex() throws Exception {
        DanmuPostProcessor p = processor("", 0, "打卡|/^\\d{5,}$/|/qwerty/i", 0, false, "default", "");
        String out = p.process(arrayPayload(
                entry(1, "2025-10-01打卡"), entry(2, "123456"), entry(3, "QWERTY"),
                entry(4, "正常弹幕"), entry(5, "前方高能")), null, null, null);
        JsonNode arr = MAPPER.readTree(out);
        assertEquals(2, arr.size());
        assertEquals("正常弹幕", arr.get(0).path("text").asText());
        assertEquals("前方高能", arr.get(1).path("text").asText());
    }

    // ---- limit sampling ----

    @Test
    void limitSamplesAtEqualIntervals() throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 2500; i++) {
            if (i > 0) sb.append(',');
            sb.append(entry(i, "t" + i));
        }
        sb.append(']');
        DanmuPostProcessor p = processor("", 0, "", 1, false, "default", ""); // cap = 1000
        JsonNode arr = MAPPER.readTree(p.process(sb.toString(), null, null, null));
        assertEquals(1000, arr.size());
        assertEquals("t0", arr.get(0).path("text").asText());
        assertTrue(arr.get(999).path("time").asInt() <= 2499);
    }

    @Test
    void limitBelowCapKeepsEverything() throws Exception {
        DanmuPostProcessor p = processor("", 0, "", 1, false, "default", "");
        JsonNode arr = MAPPER.readTree(p.process(arrayPayload(entry(1, "a"), entry(2, "b")), null, null, null));
        assertEquals(2, arr.size());
    }

    // ---- conversions ----

    @Test
    void topBottomConvertedToScroll() throws Exception {
        DanmuPostProcessor p = processor("", 0, "", 0, true, "default", "");
        String payload = "[" +
                "{\"text\":\"a\",\"time\":1,\"mode\":4,\"color\":\"#FFFFFF\",\"border\":false,\"style\":{}}," +
                "{\"text\":\"b\",\"time\":2,\"mode\":5,\"color\":\"#FFFFFF\",\"border\":false,\"style\":{}}," +
                "{\"text\":\"c\",\"time\":3,\"mode\":1,\"color\":\"#FFFFFF\",\"border\":false,\"style\":{}}]";
        JsonNode arr = MAPPER.readTree(p.process(payload, null, null, null));
        assertEquals(1, arr.get(0).path("mode").asInt());
        assertEquals(1, arr.get(1).path("mode").asInt());
        assertEquals(1, arr.get(2).path("mode").asInt());
    }

    @Test
    void whiteModeForcesAllColorsWhite() throws Exception {
        DanmuPostProcessor p = processor("", 0, "", 0, false, "white", "");
        String payload = "[{\"text\":\"a\",\"time\":1,\"mode\":1,\"color\":\"#FF0000\",\"border\":false,\"style\":{}}]";
        JsonNode arr = MAPPER.readTree(p.process(payload, null, null, null));
        assertEquals("#FFFFFF", arr.get(0).path("color").asText());
    }

    @Test
    void colorModeRecolorsWhiteEntriesDeterministically() throws Exception {
        DanmuPostProcessor p = processor("", 0, "", 0, false, "color", "16711680,255");
        String payload = "[" +
                "{\"text\":\"same\",\"time\":1,\"mode\":1,\"color\":\"#FFFFFF\",\"border\":false,\"style\":{}}," +
                "{\"text\":\"colored\",\"time\":2,\"mode\":1,\"color\":\"#FF0000\",\"border\":false,\"style\":{}}]";
        JsonNode first = MAPPER.readTree(p.process(payload, null, null, null));
        JsonNode second = MAPPER.readTree(p.process(payload, null, null, null));
        assertEquals(first.get(0).path("color").asText(), second.get(0).path("color").asText(),
                "same text must map to the same pool color across requests");
        assertTrue(first.get(0).path("color").asText().matches("#(FF0000|0000FF)"));
        assertEquals("#FF0000", first.get(1).path("color").asText(), "non-white entries are untouched");
    }

    // ---- shape handling & degradation ----

    @Test
    void episodeKeyedObjectShapeIsPreserved() throws Exception {
        String payload = "{\"1\":[" + entry(10, "哈") + "," + entry(11, "哈") + "],\"2\":[" + entry(5, "x") + "]}";
        JsonNode root = MAPPER.readTree(plain().process(payload, null, null, null));
        assertTrue(root.isObject());
        assertEquals(1, root.get("1").size(), "dedupe applies per episode list");
        assertEquals(1, root.get("2").size());
    }

    @Test
    void inactiveProcessorReturnsInputUntouched() {
        DanmuPostProcessor p = processor("", 0, "", 0, false, "default", "");
        String payload = arrayPayload(entry(1, "a"));
        assertEquals(payload, p.process(payload, "any", "1", 1));
    }

    @Test
    void malformedPayloadDegradesToRawInput() {
        assertEquals("not json at all", plain().process("not json at all", null, null, null));
    }
}
