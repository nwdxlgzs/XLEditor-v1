package com.nwdxlgzs.xleditor;

import android.content.res.Resources;
import android.graphics.Paint;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Util {
    // 修复的字符位置查找
    public static int findVisibleCharIndex(float[] positions, float targetX) {
        if (targetX <= 0) return 0;
        if (positions.length == 0) return 0;

        int left = 0;
        int right = positions.length - 1;
        int result = 0;

        while (left <= right) {
            int mid = (left + right) / 2;

            if (positions[mid] < targetX) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return result;
    }

    // 二分查找精确换行点
    public static int binarySearchWrapPoint(TextPaint textPaint, String text, int start, int left, int right, float maxWidth) {
        int result = left;

        while (left <= right) {
            int mid = (left + right) / 2;
            String subText = text.substring(start, mid);
            float width = textPaint.measureText(subText);

            if (width <= maxWidth) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return result;
    }

    public static List<HighlightSpan> mergeAndOptimizeSpans(List<HighlightSpan> spans, int lineLength, int textColor) {
        if (spans.isEmpty()) {
            return Collections.singletonList(new HighlightSpan(0, lineLength, textColor));
        }

        // 排序
        spans.sort((a, b) -> Integer.compare(a.start, b.start));

        List<HighlightSpan> merged = new ArrayList<>();
        int lastEnd = 0;

        for (HighlightSpan span : spans) {
            // 填充空隙
            if (span.start > lastEnd) {
                merged.add(new HighlightSpan(lastEnd, span.start, textColor));
            }

            // 添加当前span
            merged.add(span);
            lastEnd = Math.max(lastEnd, span.end);
        }

        // 填充末尾
        if (lastEnd < lineLength) {
            merged.add(new HighlightSpan(lastEnd, lineLength, textColor));
        }

        // 合并相邻的相同颜色span
        List<HighlightSpan> optimized = new ArrayList<>();
        HighlightSpan current = null;

        for (HighlightSpan span : merged) {
            if (current == null) {
                current = span;
            } else if (current.color == span.color && current.end == span.start) {
                // 合并
                current = new HighlightSpan(current.start, span.end, current.color);
            } else {
                optimized.add(current);
                current = span;
            }
        }

        if (current != null) {
            optimized.add(current);
        }

        return optimized;
    }

    public static boolean isPunctuation(char c) {
        return ".,;:!?()[]{}\"'".indexOf(c) >= 0;
    }

    // 新增：估算平均字符宽度
    public static float estimateAverageCharWidth(TextPaint textPaint, String text, float charWidth) {
        // 采样策略：对于超长文本，只测量部分字符
        int sampleSize = Math.min(100, text.length());
        float totalWidth = 0;
        int count = 0;

        // 均匀采样
        int step = Math.max(1, text.length() / sampleSize);

        for (int i = 0; i < text.length() && count < sampleSize; i += step) {
            char c = text.charAt(i);
            totalWidth += textPaint.measureText(String.valueOf(c));
            count++;
        }

        // 返回平均宽度，留一些余量
        return count > 0 ? (totalWidth / count) * 1.1f : charWidth;
    }

    // 查找单词边界
    public static int findWordBreakPoint(String text, int start, int preferredBreak) {
        // 向前查找空格或标点
        for (int i = preferredBreak; i > start; i--) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                return i;
            }
        }
        // 如果找不到合适的断点，使用原始位置
        return preferredBreak;
    }

    // 查找换行点
    public static int findWrapPoint(TextPaint textPaint, String text, int start, float maxWidth) {
        if (start >= text.length()) {
            return text.length();
        }

        // 二分查找最大可显示字符数
        int left = start;
        int right = text.length();
        int result = start + 1; // 至少显示一个字符

        while (left <= right) {
            int mid = (left + right) / 2;
            String subText = text.substring(start, mid);
            float width = textPaint.measureText(subText);

            if (width <= maxWidth) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        // 尝试在单词边界处换行
        if (result < text.length()) {
            int wordBreak = findWordBreakPoint(text, start, result);
            if (wordBreak > start) {
                result = wordBreak;
            }
        }

        return result;
    }

    // 优化的单词边界查找
    public static int findWordBreakPointFast(String text, int start, int preferredBreak) {
        // 限制搜索范围，避免过度查找
        int searchStart = Math.max(start, preferredBreak - 50);

        // 向前查找空格或标点
        for (int i = preferredBreak; i > searchStart; i--) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                return i;
            }
        }

        // 如果找不到合适的断点，使用原始位置
        return preferredBreak;
    }

    // 向后收缩查找
    public static int shrinkBackward(TextPaint textPaint, String text, int start, int current, float maxWidth) {
        int step = Math.max(1, (current - start) / 4); // 动态步长

        while (current > start + 1) {
            int next = Math.max(start + 1, current - step);
            String subText = text.substring(start, next);
            float width = textPaint.measureText(subText);

            if (width <= maxWidth) {
                // 可以容纳，使用二分查找精确定位
                return binarySearchWrapPoint(textPaint, text, start, next, current, maxWidth);
            }

            current = next;

            // 逐渐减小步长
            if (step > 1) {
                step = Math.max(1, step / 2);
            }
        }

        return start + 1;
    }

    // 向前扩展查找
    public static int expandForward(TextPaint textPaint, String text, int start, int current, float maxWidth) {
        int step = Math.max(1, (current - start) / 4); // 动态步长

        while (current < text.length()) {
            int next = Math.min(current + step, text.length());
            String subText = text.substring(start, next);
            float width = textPaint.measureText(subText);

            if (width > maxWidth) {
                // 超过了，使用二分查找精确定位
                return binarySearchWrapPoint(textPaint, text, start, current, next, maxWidth);
            }

            current = next;

            // 逐渐减小步长
            if (step > 1) {
                step = Math.max(1, step / 2);
            }
        }

        return text.length();
    }

    // 新增：快速查找换行点（使用估算值优化）
    public static int findWrapPointFast(TextPaint textPaint, String text, int start, int estimatedEnd, float maxWidth) {
        if (start >= text.length()) {
            return text.length();
        }

        // 测量估算位置的宽度
        String subText = text.substring(start, Math.min(estimatedEnd, text.length()));
        float width = textPaint.measureText(subText);

        int result;

        if (width <= maxWidth) {
            // 估算值偏小，向后扩展
            result = expandForward(textPaint, text, start, estimatedEnd, maxWidth);
        } else {
            // 估算值偏大，向前收缩
            result = shrinkBackward(textPaint, text, start, estimatedEnd, maxWidth);
        }

        // 尝试在单词边界处换行
        if (result < text.length() && result - start > 10) { // 只在行长度足够时才找单词边界
            int wordBreak = findWordBreakPointFast(text, start, result);
            if (wordBreak > start) {
                result = wordBreak;
            }
        }

        return Math.max(start + 1, result); // 至少返回一个字符
    }

    public static boolean isWordChar(char c) {
        return Character.isJavaIdentifierPart(c) || c == '.';
    }

    public static int calculateAutocompleteWidth(Resources resources, List<AutocompleteItem> autocompleteSuggestions) {
        int maxWidth = 0;
        Paint measurePaint = new Paint();
        measurePaint.setTextSize(14 * resources.getDisplayMetrics().density);

        for (AutocompleteItem item : autocompleteSuggestions) {
            int displayWidth = (int) measurePaint.measureText(item.displayText);
            int detailWidth = (int) measurePaint.measureText(item.detail);
            maxWidth = Math.max(maxWidth, Math.max(displayWidth, detailWidth));
        }

        // Add padding and ensure minimum width
        return Math.max(300, Math.min(500, maxWidth + 60));
    }

    // Improved method to get current word info with position
    public static WordInfo getCurrentWordInfo(int cursorPosition, StringBuilder content) {
        int start = cursorPosition;
        int end = cursorPosition;

        // Find word start
        while (start > 0 && isWordChar(content.charAt(start - 1))) {
            start--;
        }

        // Find word end (for replacement)
        while (end < content.length() && isWordChar(content.charAt(end))) {
            end++;
        }

        String prefix = content.substring(start, cursorPosition);
        return new WordInfo(prefix, start, end);
    }

    // 新增：将换行结果添加到列表（便于异步处理）
    public static void wrapLineToList(TextPaint textPaint, float charWidth, int originalLine, String lineText, float maxWidth,
                                      List<LineInfo> results, boolean useFastMode) {
        if (lineText.isEmpty()) {
            results.add(new LineInfo(originalLine, 0, 0, 0, ""));
            return;
        }

        if (useFastMode) {
            // 使用快速算法
            float avgCharWidth = estimateAverageCharWidth(textPaint, lineText, charWidth);
            int estimatedCharsPerLine = Math.max(1, (int) (maxWidth / avgCharWidth));

            int subLine = 0;
            int startOffset = 0;

            while (startOffset < lineText.length()) {
                int estimatedEnd = Math.min(startOffset + estimatedCharsPerLine, lineText.length());
                int endOffset = findWrapPointFast(textPaint, lineText, startOffset, estimatedEnd, maxWidth);

                String subText = lineText.substring(startOffset, endOffset);
                results.add(new LineInfo(originalLine, subLine, startOffset, endOffset, subText));

                startOffset = endOffset;
                subLine++;
            }
        } else {
            // 使用标准算法
            int subLine = 0;
            int startOffset = 0;

            while (startOffset < lineText.length()) {
                int endOffset = findWrapPoint(textPaint, lineText, startOffset, maxWidth);
                String subText = lineText.substring(startOffset, endOffset);

                results.add(new LineInfo(originalLine, subLine, startOffset, endOffset, subText));

                startOffset = endOffset;
                subLine++;
            }
        }
    }


    // 修复的 findVisibleStartChar 方法
    public static int findVisibleStartChar(TextPaint textPaint, String text, float targetWidth) {
        if (targetWidth <= 0) return 0;
        if (text.isEmpty()) return 0;

        int left = 0;
        int right = text.length();
        int result = 0;

        while (left <= right) {
            int mid = (left + right) / 2;
            if (mid > text.length()) break;

            String prefixText = text.substring(0, mid);
            float width = textPaint.measureText(prefixText);

            if (width <= targetWidth) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        // 确保从完整字符开始（处理emoji等多字节字符）
        while (result > 0 && result < text.length() &&
                Character.isLowSurrogate(text.charAt(result))) {
            result--;
        }

        return result;
    }

    // 修复的 findVisibleEndChar 方法 - 主要问题在这里
    public static int findVisibleEndChar(TextPaint textPaint, String text, int start, float maxWidth) {
        if (start >= text.length()) return text.length();
        if (maxWidth <= 0) return start;

        int left = start;
        int right = text.length();
        int result = start;

        while (left <= right) {
            int mid = (left + right) / 2;
            if (mid > text.length()) {
                right = text.length();
                continue;
            }

            String subText = text.substring(start, mid);
            float width = textPaint.measureText(subText);

            if (width <= maxWidth) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        // 确保不截断字符
        while (result > start && result < text.length() &&
                Character.isLowSurrogate(text.charAt(result))) {
            result--;
        }

        return result;
    }

    // 新增：调整高亮spans为子行相对坐标
    public static List<HighlightSpan> adjustSpansForSubLine(List<HighlightSpan> originalSpans,
                                                      int subLineStart, int subLineEnd) {
        List<HighlightSpan> adjustedSpans = new ArrayList<>();

        for (HighlightSpan span : originalSpans) {
            // 检查span是否与当前子行有交集
            if (span.end <= subLineStart || span.start >= subLineEnd) {
                continue; // 不在当前子行范围内
            }

            // 计算在子行中的相对位置
            int adjustedStart = Math.max(0, span.start - subLineStart);
            int adjustedEnd = Math.min(subLineEnd - subLineStart, span.end - subLineStart);

            if (adjustedStart < adjustedEnd) {
                adjustedSpans.add(new HighlightSpan(adjustedStart, adjustedEnd, span.color));
            }
        }

        return adjustedSpans;
    }

    // 新增：获取行的缩进
    public static String getLineIndentation(String lineText) {
        StringBuilder indentation = new StringBuilder();

        for (int i = 0; i < lineText.length(); i++) {
            char c = lineText.charAt(i);
            if (c == ' ' || c == '\t') {
                indentation.append(c);
            } else {
                break;
            }
        }

        // Special case: if the line has opening brackets that should increase indentation for the next line
        boolean addExtraIndent = lineText.matches(".*[{\\$$]\\s*$");

        if (addExtraIndent) {
            // Add four spaces for extra indentation level
            indentation.append("    ");
        }

        return indentation.toString();
    }
}
