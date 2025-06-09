package com.nwdxlgzs.xleditor;

import java.util.List;

public interface AutocompleteProvider {
    List<AutocompleteItem> getSuggestions(String prefix, int cursorPosition, String fullText);
}