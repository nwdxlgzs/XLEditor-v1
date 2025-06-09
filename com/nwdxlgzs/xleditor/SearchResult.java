package com.nwdxlgzs.xleditor;

public class SearchResult {
    public final int start;
    public final int end;
    public final int line;
    public final String lineText;

    public SearchResult(int start, int end, int line, String lineText) {
        this.start = start;
        this.end = end;
        this.line = line;
        this.lineText = lineText;
    }
}