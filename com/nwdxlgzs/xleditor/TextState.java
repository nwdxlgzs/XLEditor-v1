package com.nwdxlgzs.xleditor;

public class TextState {
    public final String text;
    public final int cursorPosition;
    public final int selectionStart;
    public final int selectionEnd;

    public TextState(String text, int cursorPosition, int selectionStart, int selectionEnd) {
        this.text = text;
        this.cursorPosition = cursorPosition;
        this.selectionStart = selectionStart;
        this.selectionEnd = selectionEnd;
    }
}