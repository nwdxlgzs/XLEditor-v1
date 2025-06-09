package com.nwdxlgzs.xleditor;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

// JavaSyntaxHighlighter 实现
public class JavaSyntaxHighlighter implements SyntaxHighlighter {
    private static final String[] KEYWORDS = {
            "public", "private", "protected", "class", "interface", "extends", "implements",
            "void", "int", "String", "boolean", "float", "double", "long", "short", "byte",
            "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
            "return", "new", "this", "super", "static", "final", "abstract", "synchronized",
            "try", "catch", "finally", "throw", "throws", "package", "import"
    };

    private static final int COLOR_KEYWORD = Color.parseColor("#569cd6");
    private static final int COLOR_STRING = Color.parseColor("#ce9178");
    private static final int COLOR_COMMENT = Color.parseColor("#6a9955");
    private static final int COLOR_NUMBER = Color.parseColor("#b5cea8");
    private static final int COLOR_ANNOTATION = Color.parseColor("#dcdcaa");
    private static final int COLOR_DEFAULT = Color.parseColor("#d4d4d4");

    @Override
    public List<HighlightSpan> highlight(XLEditor editor,String line, int lineIndex) {
        List<HighlightSpan> spans = new ArrayList<>();

        if (line.trim().startsWith("//")) {
            spans.add(new HighlightSpan(0, line.length(), COLOR_COMMENT));
            return spans;
        }

        boolean[] highlighted = new boolean[line.length()];

        // 处理字符串
        int stringStart = -1;
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!inString) {
                    stringStart = i;
                    inString = true;
                } else {
                    spans.add(new HighlightSpan(stringStart, i + 1, COLOR_STRING));
                    for (int j = stringStart; j <= i; j++) {
                        highlighted[j] = true;
                    }
                    inString = false;
                }
            }
        }

        // 处理关键字和数字
        int pos = 0;
        while (pos < line.length()) {
            if (highlighted[pos]) {
                pos++;
                continue;
            }

            if (Character.isJavaIdentifierStart(line.charAt(pos)) || Character.isDigit(line.charAt(pos))) {
                int start = pos;
                while (pos < line.length() &&
                        (Character.isJavaIdentifierPart(line.charAt(pos)) ||
                                Character.isDigit(line.charAt(pos)) ||
                                line.charAt(pos) == '.')) {
                    pos++;
                }

                String word = line.substring(start, pos);

                boolean isKeyword = false;
                for (String keyword : KEYWORDS) {
                    if (keyword.equals(word)) {
                        spans.add(new HighlightSpan(start, pos, COLOR_KEYWORD));
                        isKeyword = true;
                        break;
                    }
                }

                if (!isKeyword && isNumber(word)) {
                    spans.add(new HighlightSpan(start, pos, COLOR_NUMBER));
                }
            } else {
                pos++;
            }
        }

        return mergeSpans(spans, line.length());
    }

    private boolean isNumber(String word) {
        try {
            if (word.startsWith("0x") || word.startsWith("0X")) {
                Long.parseLong(word.substring(2), 16);
                return true;
            } else if (word.contains(".")) {
                Double.parseDouble(word);
                return true;
            } else {
                Long.parseLong(word);
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<HighlightSpan> mergeSpans(List<HighlightSpan> spans, int lineLength) {
        if (lineLength == 0) {
            return spans;
        }

        spans.sort((a, b) -> Integer.compare(a.start, b.start));

        List<HighlightSpan> merged = new ArrayList<>();
        int lastEnd = 0;

        for (HighlightSpan span : spans) {
            if (span.start > lastEnd) {
                merged.add(new HighlightSpan(lastEnd, span.start, COLOR_DEFAULT));
            }
            merged.add(span);
            lastEnd = Math.max(lastEnd, span.end);
        }

        if (lastEnd < lineLength) {
            merged.add(new HighlightSpan(lastEnd, lineLength, COLOR_DEFAULT));
        }

        return merged;
    }
}