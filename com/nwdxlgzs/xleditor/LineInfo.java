package com.nwdxlgzs.xleditor;

public class LineInfo {
    public int originalLine;      // 原始行号
    public int subLine;          // 子行号（用于换行）
    public int startOffset;      // 在原始行中的起始位置
    public int endOffset;        // 在原始行中的结束位置
    public String text;          // 实际显示的文本

    public LineInfo(int originalLine, int subLine, int startOffset, int endOffset, String text) {
        this.originalLine = originalLine;
        this.subLine = subLine;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.text = text;
    }
}