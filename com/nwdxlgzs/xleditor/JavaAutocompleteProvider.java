package com.nwdxlgzs.xleditor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Sample autocomplete provider for Java
public class JavaAutocompleteProvider implements AutocompleteProvider {
    private static final String[] KEYWORDS = {
            "public", "private", "protected", "class", "interface", "extends",
            "implements", "void", "int", "String", "boolean", "float", "double"
    };

    private static final String[][] METHODS = {
            {"System.out.println", "System.out.println()", "Print to console"},
            {"toString", "toString()", "Convert to string"},
            {"equals", "equals(Object obj)", "Compare objects"},
            {"hashCode", "hashCode()", "Get hash code"},
            {"hashCode", "hashCode(555)", "Get hash code555"},
    };

    @Override
    public List<AutocompleteItem> getSuggestions(String prefix, int cursorPosition, String fullText) {
        List<AutocompleteItem> suggestions = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase();

        // Add matching keywords
        for (String keyword : KEYWORDS) {
            if (keyword.toLowerCase().startsWith(lowerPrefix)) {
                suggestions.add(new AutocompleteItem(
                        keyword + " ",
                        keyword,
                        "Keyword",
                        AutocompleteType.KEYWORD
                ));
            }
        }

        // Add matching methods
        for (String[] method : METHODS) {
            if (method[0].toLowerCase().contains(lowerPrefix)) {
                suggestions.add(new AutocompleteItem(
                        method[0],
                        method[1],
                        method[2],
                        AutocompleteType.METHOD
                ));
            }
        }

        // Add context-specific suggestions
        if (prefix.startsWith("@")) {
            suggestions.add(new AutocompleteItem(
                    "@Override",
                    "@Override",
                    "Override parent method",
                    AutocompleteType.KEYWORD
            ));
        }

        // Limit suggestions to improve performance
        return suggestions.stream()
//                    .limit(10)
                .collect(Collectors.toList());
    }
}
