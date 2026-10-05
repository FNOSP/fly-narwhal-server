package com.jankinwu.flynarwhal.core.danmu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.config.DanmuMatchProperties;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers parsing of the dandanplay-style third-party fallback payloads
 * (bare array and "danmuku" wrapper, 8-field p attribute).
 */
class DanmuAppServiceFallbackTest {

    private DanmuAppService service() {
        return new DanmuAppService(null, null, new ObjectMapper(), new DanmuMatchProperties(), null, null);
    }

    @Test
    void parsesBareArrayPayload() throws Exception {
        String json = "[{\"cid\":\"1\",\"p\":\"12.5,1,25,16711680,1690000000,0,uid,did\",\"m\":\"哈喽\"}," +
                "{\"cid\":\"2\",\"p\":\"30,5,25,255,1690000000,0,uid,did\",\"m\":\"顶部蓝色\"}]";
        List<DanmuModel> list = service().parseFallbackDanmu(json);
        assertEquals(2, list.size());

        DanmuModel first = list.get(0);
        assertEquals(12.5, first.getTime());
        assertEquals("哈喽", first.getText());
        assertEquals(1, first.getMode());
        assertEquals("#FF0000", first.getColor());

        DanmuModel second = list.get(1);
        assertEquals(5, second.getMode());
        assertEquals("#0000FF", second.getColor());
    }

    @Test
    void parsesDanmukuWrapperPayload() throws Exception {
        String json = "{\"count\":1,\"danmuku\":[{\"cid\":\"9\",\"p\":\"3,4,18,65280,0,0,u,i\",\"m\":\"底部绿色\"}]}";
        List<DanmuModel> list = service().parseFallbackDanmu(json);
        assertEquals(1, list.size());
        assertEquals("底部绿色", list.get(0).getText());
        assertEquals("#00FF00", list.get(0).getColor());
    }

    @Test
    void skipsMalformedEntries() throws Exception {
        String json = "[{\"p\":\"\",\"m\":\"no p\"},{\"p\":\"abc,1\",\"m\":\"bad time\"},{\"p\":\"5\",\"m\":\"ok\"}]";
        List<DanmuModel> list = service().parseFallbackDanmu(json);
        assertEquals(1, list.size());
        assertEquals("ok", list.get(0).getText());
        assertTrue(list.get(0).getColor().equals("#FFFFFF"), "color stays default when p has no 4th field");
    }

    @Test
    void unexpectedShapeYieldsEmpty() throws Exception {
        assertTrue(service().parseFallbackDanmu("{\"foo\":1}").isEmpty());
    }

    @Test
    void parsesDmkuTuplePayload() throws Exception {
        // Shape served by dmku.hls.one: [time, position, color, size, text].
        String json = "{\"code\":23,\"danum\":3,\"danmuku\":[" +
                "[2,\"right\",\"#fff\",\"32\",\"列队来袭\"]," +
                "[10,\"top\",\"#ffffff\",\"32px\",\"顶部白\"]," +
                "[20,\"bottom\",\"#FF0000\",\"32\",\"底部红\"]," +
                "[\"bad\",\"right\",\"#fff\",\"32\",\"时间非数字\"]]}";
        List<DanmuModel> list = service().parseFallbackDanmu(json);
        assertEquals(3, list.size());

        DanmuModel first = list.get(0);
        assertEquals(2.0, first.getTime());
        assertEquals(1, first.getMode());
        assertEquals("#FFFFFF", first.getColor(), "3-digit hex must expand");
        assertEquals("列队来袭", first.getText());

        assertEquals(5, list.get(1).getMode(), "top maps to bilibili mode 5");
        assertEquals(4, list.get(2).getMode(), "bottom maps to bilibili mode 4");
        assertEquals("#FF0000", list.get(2).getColor());
    }

    @Test
    void expandsHexColors() {
        assertEquals("#FFFFFF", DanmuAppService.expandHexColor("#fff"));
        assertEquals("#FF8800", DanmuAppService.expandHexColor("#f80"));
        assertEquals("#AABBCC", DanmuAppService.expandHexColor("#aabbcc"));
        assertEquals("#FFFFFF", DanmuAppService.expandHexColor(""));
        assertEquals("#FFFFFF", DanmuAppService.expandHexColor("xyz"));
    }
}
