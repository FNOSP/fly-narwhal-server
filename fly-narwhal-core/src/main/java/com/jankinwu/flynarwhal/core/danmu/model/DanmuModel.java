package com.jankinwu.flynarwhal.core.danmu.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class DanmuModel {
    private String text = "";
    private double time = 0;
    private int mode = 0;
    private String color = "#FFFFFF";
    private boolean border = false;
    private Map<String, Object> style = new HashMap<>();
    private Map<String, Object> other = new HashMap<>();

    public Map<String, Object> toDict() {
        if (color != null && !color.startsWith("#")) {
            try {
                int colorInt = Integer.parseInt(color);
                color = String.format("#%06X", colorInt);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        Map<String, Object> dict = new HashMap<>();
        dict.put("text", text.replace("&#", ""));
        dict.put("time", (int) time);
        dict.put("mode", mode);
        dict.put("color", color);
        dict.put("border", border);
        dict.put("style", style);
        return dict;
    }
}
