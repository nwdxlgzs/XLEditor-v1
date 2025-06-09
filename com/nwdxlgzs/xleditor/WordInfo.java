package com.nwdxlgzs.xleditor;

public class WordInfo {
    public String prefix;
    public int startPosition;
    public int endPosition;

    public WordInfo(String prefix, int startPosition, int endPosition) {
        this.prefix = prefix;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }
}