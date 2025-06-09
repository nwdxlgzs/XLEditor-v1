package com.nwdxlgzs.xleditor;

public class LineRenderInfo {
    public final String text;
    public final int length;
    public float[] charPositions; // 每个字符的x坐标
    public float totalWidth;
    public boolean isProcessing = false;
    public boolean isComplete = false;

    LineRenderInfo(String text) {
        this.text = text;
        this.length = text.length();
    }
}