package com.nwdxlgzs.xleditor;

import android.text.TextPaint;

import java.util.HashMap;
import java.util.Map;

public class CharWidthCache {
    public CharWidthCache(){}
    private final float[] asciiWidths = new float[128];
    private final Map<Character, Float> unicodeWidths = new HashMap<>();
    private boolean initialized = false;

    void init(TextPaint paint) {
        if (initialized) return;

        // 预计算ASCII字符宽度
        for (int i = 0; i < 128; i++) {
            asciiWidths[i] = paint.measureText(String.valueOf((char) i));
        }

        initialized = true;
    }

    float getCharWidth(char c, TextPaint paint) {
        if (c < 128) {
            return asciiWidths[c];
        }

        Float width = unicodeWidths.get(c);
        if (width == null) {
            width = paint.measureText(String.valueOf(c));
            // 限制缓存大小
            if (unicodeWidths.size() < 1000) {
                unicodeWidths.put(c, width);
            }
        }

        return width;
    }

    void clear() {
        unicodeWidths.clear();
    }
}