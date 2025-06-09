package com.nwdxlgzs.xleditor;

import java.util.List;

// 语法高亮接口
public interface SyntaxHighlighter {
    List<HighlightSpan> highlight(XLEditor editor, String line, int lineIndex);
}