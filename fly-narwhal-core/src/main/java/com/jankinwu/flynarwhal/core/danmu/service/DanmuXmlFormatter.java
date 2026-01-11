package com.jankinwu.flynarwhal.core.danmu.service;

import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;

import java.util.List;
import java.util.Map;

public final class DanmuXmlFormatter {
    private DanmuXmlFormatter() {
    }

    public static String toXml(List<DanmuModel> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<i>\n");
        for (DanmuModel d : list) {
            sb.append(list2Xml(d)).append("\n");
        }
        sb.append("</i>");
        return sb.toString();
    }

    private static String list2Xml(DanmuModel data) {
        String color = data.getColor();
        int colorInt;
        if (color != null && color.startsWith("#")) {
            colorInt = Integer.parseInt(color.substring(1), 16);
        } else if (color != null) {
            try {
                colorInt = Integer.parseInt(color);
            } catch (NumberFormatException e) {
                colorInt = 0xFFFFFF;
            }
        } else {
            colorInt = 0xFFFFFF;
        }

        Map<String, Object> style = data.getStyle();
        int size = 25;
        if (style != null && style.get("size") != null) {
            Object v = style.get("size");
            if (v instanceof Number) {
                size = ((Number) v).intValue();
            } else {
                try {
                    size = Integer.parseInt(v.toString());
                } catch (Exception ignored) {
                }
            }
        }

        String text = escapeXml(data.getText());
        int time = (int) data.getTime();
        int mode = data.getMode();
        return "    <d p=\"" + time + "," + mode + "," + size + "," + colorInt + ",0,0,0,0\">" + text + "</d>";
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

