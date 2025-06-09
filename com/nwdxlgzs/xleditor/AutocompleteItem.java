package com.nwdxlgzs.xleditor;

public class AutocompleteItem {
    public final String text;
    public final String displayText;
    public final String detail;
    public final AutocompleteType type;

    public AutocompleteItem(String text, String displayText, String detail, AutocompleteType type) {
        this.text = text;
        this.displayText = displayText;
        this.detail = detail;
        this.type = type;
    }
}