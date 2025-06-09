package com.nwdxlgzs.xleditor;

public class HighlightSpan {
    public int start;
    public int end;
    public int color;

    public HighlightSpan(int start, int end, int color) {
        this.start = start;
        this.end = end;
        this.color = color;
    }
}