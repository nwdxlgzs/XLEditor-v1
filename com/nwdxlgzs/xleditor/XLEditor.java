package com.nwdxlgzs.xleditor;

import static com.nwdxlgzs.xleditor.Util.*;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.widget.ListView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

public class XLEditor extends View {
    // 文本相关
    private StringBuilder content = new StringBuilder();
    private List<Integer> lineBreaks = new ArrayList<>();
    private TextPaint textPaint;
    private TextPaint lineNumberPaint;
    private TextPaint selectionPaint;
    private TextPaint cursorPaint;
    private float textSize = 48f;
    private float lineHeight;
    private float charWidth;

    // 光标和选择
    private int cursorPosition = 0;
    private boolean showCursor = true;
    public int selectionStart = -1;
    public static final int LONG_LINE_LENGTH = 1000;
    public int selectionEnd = -1;
    private boolean isSelecting = false;

    // 选择手柄
    private SelectionHandle startHandle;
    private SelectionHandle endHandle;
    private SelectionHandle activeHandle = null;
    private float handleTouchOffsetY = 0;

    // 放大镜
    private MagnifierView magnifier;
    private boolean showMagnifier = false;
    private float magnifierX = 0;
    private float magnifierY = 0;
    private float magnifierTargetX = 0;  // 添加：放大镜要观察的目标位置
    private float magnifierTargetY = 0;

    // 滚动相关
    private int scrollX = 0;
    private int scrollY = 0;
    private OverScroller scroller;
    private int maxScrollX = 0;
    private int maxScrollY = 0;

    // 配置项
    private boolean showLineNumbers = true;
    private boolean showInvisibleChars = false;
    private boolean autoIndent = true; // 新增：自动缩进开关
    private int lineNumberWidth = 0;
    private int padding = 20;
    private int lineNumberPadding = 10;

    // 手势检测
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private float yLineScale = 0.18f;
    private long lastClickTime = 0;
    private float lastClickX = 0;
    private float lastClickY = 0;

    // 语法高亮接口
    private SyntaxHighlighter syntaxHighlighter;

    // 颜色配置
    private int backgroundColor = Color.parseColor("#1e1e1e");
    private int textColor = Color.parseColor("#d4d4d4");
    private int lineNumberColor = Color.parseColor("#858585");
    private int lineNumberBgColor = Color.parseColor("#252526");
    private int selectionColor = Color.parseColor("#264f78");
    private int cursorColor = Color.parseColor("#aeafad");
    private int handleColor = Color.parseColor("#007acc");

    // 输入法相关
    private InputMethodManager imm;
    private boolean isEditable = true;

    // 文本菜单
    private PopupWindow textActionMenu;

    // 剪贴板
    private ClipboardManager clipboardManager;

    private final CharWidthCache charWidthCache = new CharWidthCache();
    private boolean isScrolling = false;
    private long lastScrollTime = 0;
    private final Map<Integer, LineRenderInfo> lineRenderCache = new ConcurrentHashMap<>();
    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2);
    private final Map<Integer, List<HighlightSpan>> highlightCache = new HashMap<>();
    private final Set<Integer> highlightingLines = new HashSet<>();
    private ExecutorService highlightExecutor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 添加缓存版本控制
    private int textVersion = 0;
    private final Map<Integer, Integer> lineVersions = new HashMap<>();
    // 在类成员变量中添加
    private boolean wordWrap = false; // 自动换行开关
    private List<LineInfo> lineInfos = new ArrayList<>(); // 存储每行的信息

    private Stack<TextState> undoStack = new Stack<>();
    private Stack<TextState> redoStack = new Stack<>();
    private boolean isUndoRedoAction = false;
    private static final int MAX_UNDO_STACK_SIZE = 100;

    // Search
    public String searchQuery = "";
    public List<SearchResult> searchResults = new ArrayList<>();
    public int currentSearchIndex = -1;
    private Paint searchHighlightPaint;
    private Paint currentSearchHighlightPaint;

    // Autocomplete
    private AutocompleteProvider autocompleteProvider;
    private PopupWindow autocompletePopup;
    private ListView autocompleteListView;
    private List<AutocompleteItem> autocompleteSuggestions = new ArrayList<>();
    private boolean isAutocompleteShowing = false;

    private Runnable cursorBlinker = new Runnable() {
        @Override
        public void run() {
            if (hasFocus() && selectionStart == selectionEnd) {
                showCursor = !showCursor;
                invalidate();
            }
            postDelayed(this, 500);
        }
    };

    public XLEditor(Context context) {
        this(context, null);
    }

    public XLEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // Getter and setter for auto-indent
    public boolean isAutoIndent() {
        return autoIndent;
    }

    public void setAutoIndent(boolean autoIndent) {
        this.autoIndent = autoIndent;
    }

    // Getter for showLineNumbers
    public boolean isShowLineNumbers() {
        return this.showLineNumbers;
    }

    // Getter for showInvisibleChars
    public boolean isShowInvisibleChars() {
        return showInvisibleChars;
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);

        // 初始化画笔
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(textSize);
        textPaint.setColor(textColor);
        textPaint.setTypeface(Typeface.MONOSPACE);

        lineNumberPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        lineNumberPaint.setTextSize(textSize * 0.9f);
        lineNumberPaint.setColor(lineNumberColor);
        lineNumberPaint.setTypeface(Typeface.MONOSPACE);

        selectionPaint = new TextPaint();
        selectionPaint.setColor(selectionColor);
        selectionPaint.setAlpha(80);

        cursorPaint = new TextPaint();
        cursorPaint.setColor(cursorColor);
        cursorPaint.setStrokeWidth(4f);

        // 计算字符尺寸
        updateTextMetrics();

        // 初始化滚动器
        scroller = new OverScroller(getContext());

        // 初始化手势检测
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

        // 输入法管理器
        imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        // 剪贴板管理器
        clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);

        // 初始化选择手柄
        startHandle = new SelectionHandle(true);
        endHandle = new SelectionHandle(false);

        // 初始化放大镜
        magnifier = new MagnifierView();

        // 启动光标闪烁
        postDelayed(cursorBlinker, 500);
        // Initialize search paints
        searchHighlightPaint = new Paint();
        searchHighlightPaint.setColor(Color.parseColor("#3e4451"));
        searchHighlightPaint.setAlpha(100);

        currentSearchHighlightPaint = new Paint();
        currentSearchHighlightPaint.setColor(Color.parseColor("#ff6b6b"));
        currentSearchHighlightPaint.setAlpha(120);

        // Save initial state for undo
        saveUndoState();

        // Initialize autocomplete
        autocompleteListView = new ListView(getContext());
        autocompleteListView.setBackgroundColor(Color.parseColor("#252526"));
        autocompleteListView.setDivider(null);

        autocompletePopup = new PopupWindow(autocompleteListView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        autocompletePopup.setOutsideTouchable(true);
        autocompletePopup.setFocusable(false);

        autocompleteListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < autocompleteSuggestions.size()) {
                insertAutocomplete(autocompleteSuggestions.get(position));
            }
        });
    }

    private void updateTextMetrics() {
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        lineHeight = metrics.descent - metrics.ascent;
        charWidth = textPaint.measureText("M");

        // 初始化字符宽度缓存
        charWidthCache.init(textPaint);

        updateLineNumberWidth();
    }

    private void updateLineNumberWidth() {
        if (showLineNumbers && getLineCount() > 0) {
            String maxLineNumber = String.valueOf(getLineCount());
            lineNumberWidth = (int) (lineNumberPaint.measureText(maxLineNumber) + lineNumberPadding * 2);
        } else {
            lineNumberWidth = 0;
        }
    }

    private void updateLineBreaks() {
        lineBreaks.clear();
        lineBreaks.add(0);

        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineBreaks.add(i + 1);
            }
        }
    }

    public void setText(String text) {
        content.setLength(0);
        content.append(text);
        cursorPosition = 0;
        selectionStart = selectionEnd = 0;
        onTextChanged();
        invalidate();
    }

    public String getText() {
        return content.toString();
    }

    public int getLineCount() {
        return lineBreaks.size();
    }

    private int getLineForPosition(int position) {
        for (int i = lineBreaks.size() - 1; i >= 0; i--) {
            if (position >= lineBreaks.get(i)) {
                return i;
            }
        }
        return 0;
    }

    private int getLineStart(int line) {
        if (line < 0 || line >= lineBreaks.size()) return 0;
        return lineBreaks.get(line);
    }

    private int getLineEnd(int line) {
        if (line < 0 || line >= lineBreaks.size() - 1) {
            return content.length();
        }
        int end = lineBreaks.get(line + 1) - 1;
        return Math.max(getLineStart(line), end);
    }

    private String getLineText(int line) {
        int start = getLineStart(line);
        int end = getLineEnd(line);
        if (end > start && content.charAt(end - 1) == '\n') {
            end--;
        }
        return content.substring(start, Math.min(end, content.length()));
    }



    // 新增：带LineInfo的绘制方法
    private void drawLineWithInfo(Canvas canvas, LineInfo lineInfo, float y) {
        String line = lineInfo.text;
        int lineIndex = lineInfo.originalLine;

        if (line.isEmpty()) return;

        float lineStartX = lineNumberWidth + padding;
        float drawX = lineStartX - scrollX;

        // 使用缓存的高亮信息
        if (syntaxHighlighter != null) {
            List<HighlightSpan> spans = highlightCache.get(lineIndex);

            if (spans == null) {
                requestHighlightAsync(lineIndex, getLineText(lineIndex));
                canvas.drawText(line, drawX, y - (lineHeight - textSize) / 2, textPaint);
            } else {
                if (wordWrap) {
                    // 调整spans为子行相对坐标
                    List<HighlightSpan> adjustedSpans = adjustSpansForSubLine(
                            spans, lineInfo.startOffset, lineInfo.endOffset);
                    drawHighlightedText(canvas, line, adjustedSpans, drawX, y, 0, line.length());
                } else {
                    drawHighlightedText(canvas, line, spans, drawX, y, 0, line.length());
                }
            }
        } else {
            canvas.drawText(line, drawX, y - (lineHeight - textSize) / 2, textPaint);
        }

        if (showInvisibleChars) {
            drawVisibleInvisibleChars(canvas, line, lineIndex, drawX, y, 0, line.length());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background
        canvas.drawColor(backgroundColor);

        // Calculate visible line range
        int firstVisibleLine = Math.max(0, (int) Math.floor(scrollY / lineHeight));
        int lastVisibleLine = Math.min(getDisplayLineCount() - 1,
                (int) Math.ceil((scrollY + getHeight()) / lineHeight));

        // 限制预加载范围，避免过度预加载
        int preloadMargin = Math.min(5, (lastVisibleLine - firstVisibleLine) / 2);
        int preloadFirst = Math.max(0, firstVisibleLine - preloadMargin);
        int preloadLast = Math.min(getDisplayLineCount() - 1, lastVisibleLine + preloadMargin);

        // 预加载高亮（不阻塞绘制）- 只对可见范围内的行
        // Draw text (only visible lines)
        for (int i = firstVisibleLine; i <= lastVisibleLine && i < lineInfos.size(); i++) {
            float y = (i + 1) * lineHeight - scrollY;

            // Check if line is actually visible
            if (y - lineHeight > getHeight() || y < 0) {
                continue;
            }

            LineInfo lineInfo = lineInfos.get(i);

            // 传递LineInfo以便在drawLine中使用
            drawLineWithInfo(canvas, lineInfo, y);
        }


        // Draw line number background
        if (showLineNumbers) {
            canvas.drawRect(0, 0, lineNumberWidth, getHeight(), lineNumberPaint);
            @SuppressLint("DrawAllocation") Paint bgPaint = new Paint();
            bgPaint.setColor(lineNumberBgColor);
            canvas.drawRect(0, 0, lineNumberWidth, getHeight(), bgPaint);
        }

        // Save canvas state and clip text area
        canvas.save();
        canvas.clipRect(lineNumberWidth, 0, getWidth(), getHeight());

        // Draw selection area (only visible part)
        if (selectionStart != selectionEnd) {
            drawSelection(canvas, firstVisibleLine, lastVisibleLine + 1);
        }

        // Draw text (only visible lines)
        for (int i = firstVisibleLine; i <= lastVisibleLine && i < lineInfos.size(); i++) {
            float y = (i + 1) * lineHeight - scrollY;

            // Check if line is actually visible
            if (y - lineHeight > getHeight() || y < 0) {
                continue;
            }

            LineInfo lineInfo = lineInfos.get(i);
            drawLine(canvas, lineInfo.text, lineInfo.originalLine, y);
        }

        if (!searchResults.isEmpty()) {
            drawSearchHighlights(canvas);
        }

        // Draw cursor
        if (showCursor && hasFocus() && selectionStart == selectionEnd) {
            drawCursor(canvas);
        }
        canvas.restore();
        // Draw selection handles
        if (isSelecting) {
            drawSelectionHandles(canvas);
        }
        // Draw line numbers (only visible part)
        if (showLineNumbers) {
            drawLineNumbers(canvas, firstVisibleLine, lastVisibleLine + 1);
        }

        // Draw magnifier (moved out of regular drawing path)
        if (showMagnifier) {
            magnifier.draw(canvas);
        }

    }

    private void drawSearchHighlights(Canvas canvas) {
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            Paint paint = (i == currentSearchIndex) ?
                    currentSearchHighlightPaint : searchHighlightPaint;

            if (!wordWrap) {
                // 原有逻辑
                PointF startPos = getPositionCoordinates(result.start);
                PointF endPos = getPositionCoordinates(result.end);

                if (getLineForPosition(result.start) == getLineForPosition(result.end)) {
                    // Single line
                    canvas.drawRect(startPos.x, startPos.y, endPos.x, startPos.y + lineHeight, paint);
                } else {
                    // Multi-line
                    drawMultiLineSearchHighlight(canvas, result, paint);
                }
            } else {
                // 自动换行模式
                drawWordWrapSearchHighlight(canvas, result, paint);
            }
        }
    }

    // 新增：绘制自动换行模式下的搜索高亮
    private void drawWordWrapSearchHighlight(Canvas canvas, SearchResult result, Paint paint) {
        int start = result.start;
        int end = result.end;

        int startDisplayLine = getDisplayLineForPosition(start);
        int endDisplayLine = getDisplayLineForPosition(end);

        for (int i = startDisplayLine; i <= endDisplayLine && i < lineInfos.size(); i++) {
            LineInfo lineInfo = lineInfos.get(i);
            float y = (i + yLineScale) * lineHeight - scrollY;

            int lineStart = getLineStart(lineInfo.originalLine);
            int absoluteStart = lineStart + lineInfo.startOffset;
            int absoluteEnd = lineStart + lineInfo.endOffset;

            int drawStart = Math.max(absoluteStart, start);
            int drawEnd = Math.min(absoluteEnd, end);

            if (drawStart < drawEnd) {
                // 计算相对于子行的位置
                int relativeStart = drawStart - absoluteStart;
                int relativeEnd = drawEnd - absoluteStart;

                String textBefore = lineInfo.text.substring(0, Math.min(relativeStart, lineInfo.text.length()));
                String textHighlight = lineInfo.text.substring(
                        Math.min(relativeStart, lineInfo.text.length()),
                        Math.min(relativeEnd, lineInfo.text.length())
                );

                float startX = lineNumberWidth + padding + textPaint.measureText(textBefore) - scrollX;
                float width = textPaint.measureText(textHighlight);

                canvas.drawRect(startX, y, startX + width, y + lineHeight, paint);
            }
        }
    }

    // 新增：绘制多行搜索高亮（非自动换行模式）
    private void drawMultiLineSearchHighlight(Canvas canvas, SearchResult result, Paint paint) {
        int startLine = getLineForPosition(result.start);
        int endLine = getLineForPosition(result.end);

        for (int line = startLine; line <= endLine; line++) {
            float y = (line + yLineScale) * lineHeight - scrollY;

            int lineStart = getLineStart(line);
            int lineEnd = getLineEnd(line);

            int drawStart = (line == startLine) ? result.start : lineStart;
            int drawEnd = (line == endLine) ? result.end : lineEnd;

            float startX = getXForPosition(drawStart);
            float endX = getXForPosition(drawEnd);

            canvas.drawRect(startX, y, endX, y + lineHeight, paint);
        }
    }


    public void clearSearchHighlight() {
        searchResults.clear();
        searchQuery = "";
        currentSearchIndex = -1;
        invalidate();
    }

    private void drawSelection(Canvas canvas, int startLine, int endLine) {
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);

        if (!wordWrap) {
            // 原有逻辑保持不变
            int startLineIndex = getLineForPosition(start);
            int endLineIndex = getLineForPosition(end);

            for (int i = Math.max(startLineIndex, startLine); i <= Math.min(endLineIndex, endLine - 1); i++) {
                float y = (i + yLineScale) * lineHeight - scrollY;

                int lineStart = getLineStart(i);
                int lineEnd = getLineEnd(i);

                int drawStart = Math.max(lineStart, start);
                int drawEnd = Math.min(lineEnd, end);

                if (drawStart < drawEnd) {
                    float startX = getXForPosition(drawStart);
                    float endX = getXForPosition(drawEnd);

                    canvas.drawRect(startX, y, endX, y + lineHeight, selectionPaint);
                }
            }
        } else {
            // 自动换行模式
            int startDisplayLine = getDisplayLineForPosition(start);
            int endDisplayLine = getDisplayLineForPosition(end);

            for (int i = Math.max(startDisplayLine, startLine); i <= Math.min(endDisplayLine, endLine - 1) && i < lineInfos.size(); i++) {
                LineInfo lineInfo = lineInfos.get(i);
                float y = (i + yLineScale) * lineHeight - scrollY;

                int lineStart = getLineStart(lineInfo.originalLine);
                int absoluteStart = lineStart + lineInfo.startOffset;
                int absoluteEnd = lineStart + lineInfo.endOffset;

                int drawStart = Math.max(absoluteStart, start);
                int drawEnd = Math.min(absoluteEnd, end);

                if (drawStart < drawEnd) {
                    // 计算相对于子行的位置
                    int relativeStart = drawStart - absoluteStart;
                    int relativeEnd = drawEnd - absoluteStart;

                    String textBefore = lineInfo.text.substring(0, Math.min(relativeStart, lineInfo.text.length()));
                    String textSelection = lineInfo.text.substring(Math.min(relativeStart, lineInfo.text.length()), Math.min(relativeEnd, lineInfo.text.length()));

                    float startX = lineNumberWidth + padding + textPaint.measureText(textBefore) - scrollX;
                    float width = textPaint.measureText(textSelection);

                    canvas.drawRect(startX, y, startX + width, y + lineHeight, selectionPaint);
                }
            }
        }
    }


    private void drawLineNumbers(Canvas canvas, int startLine, int endLine) {
        Paint linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#2e2e2e"));
        linePaint.setStrokeWidth(2);
        canvas.drawLine(lineNumberWidth - 1, 0, lineNumberWidth - 1, getHeight(), linePaint);

        int lastDrawnLineNumber = -1;

        for (int i = startLine; i < endLine && i < lineInfos.size(); i++) {
            LineInfo lineInfo = lineInfos.get(i);

            // 只在每个原始行的第一个子行绘制行号
            if (lineInfo.subLine == 0) {
                float y = (i + 1) * lineHeight - scrollY - (lineHeight - textSize) / 2;
                String lineNumber = String.valueOf(lineInfo.originalLine + 1);
                float x = lineNumberWidth - lineNumberPaint.measureText(lineNumber) - lineNumberPadding;
                canvas.drawText(lineNumber, x, y, lineNumberPaint);
            }
        }
    }


    // 修改 drawLine 方法中调用高亮绘制的部分
    private void drawLine(Canvas canvas, String line, int lineIndex, float y) {
        if (line.isEmpty()) return;

        float lineStartX = lineNumberWidth + padding;
        float visibleLeft = scrollX;
        float visibleRight = scrollX + getWidth() + lineNumberWidth + padding * 2;
        int line_length = line.length();
        // 对超长行使用特殊处理
        if (line_length > LONG_LINE_LENGTH) {
            drawLongLine(canvas, line, lineIndex, y, visibleLeft, visibleRight, lineStartX);
            return;
        }

        // 计算整行宽度以快速判断是否在可视范围内
        float totalLineWidth = textPaint.measureText(line);

        // 修复：更准确的可视范围判断
        if (lineStartX + totalLineWidth < visibleLeft || lineStartX > visibleRight) {
            return;
        }

        // 计算需要绘制的字符范围
        int drawStart = 0;
        int drawEnd = line.length();

        // 修复：更准确的可视范围计算
        if (lineStartX < visibleLeft) {
            // 只有当行的起始位置确实在可视区域左侧时才需要裁剪
            float offsetNeeded = visibleLeft - lineStartX;
            drawStart = findVisibleStartChar(textPaint, line, offsetNeeded);
        }

        // 修复：计算可视范围的结束字符时考虑实际的绘制起始位置
        if (drawStart < line.length()) {
            float remainingWidth = visibleRight - lineStartX;
            if (drawStart > 0) {
                // 减去已跳过部分的宽度
                String skippedText = line.substring(0, drawStart);
                remainingWidth -= textPaint.measureText(skippedText);
            }

            // 只有当剩余宽度不足以显示所有字符时才裁剪
            if (remainingWidth < totalLineWidth - textPaint.measureText(line.substring(0, drawStart))) {
                drawEnd = findVisibleEndChar(textPaint, line, drawStart, remainingWidth);
            }
        }

        if (drawStart >= drawEnd) return;

        String visibleText = line.substring(drawStart, drawEnd);

        // 计算绘制起始位置
        float startX = lineStartX;
        if (drawStart > 0) {
            // 准确计算前面字符的宽度
            String prefixText = line.substring(0, drawStart);
            startX += textPaint.measureText(prefixText);
        }

        float drawX = startX - scrollX;

        // 使用缓存的高亮信息
        if (syntaxHighlighter != null) {
            List<HighlightSpan> spans = highlightCache.get(lineIndex);

            if (spans == null) {
                requestHighlightAsync(lineIndex, line);
                canvas.drawText(visibleText, drawX, y - (lineHeight - textSize) / 2, textPaint);
            } else {
                // 自动换行模式下需要调整spans的坐标
                if (wordWrap) {
                    // 获取当前显示行的信息
                    LineInfo currentLineInfo = null;
                    for (LineInfo info : lineInfos) {
                        if (info.originalLine == lineIndex &&
                                visibleText.equals(info.text)) {
                            currentLineInfo = info;
                            break;
                        }
                    }

                    if (currentLineInfo != null) {
                        // 调整spans坐标为相对于子行的坐标
                        List<HighlightSpan> adjustedSpans = adjustSpansForSubLine(
                                spans, currentLineInfo.startOffset, currentLineInfo.endOffset);
                        drawHighlightedText(canvas, line, adjustedSpans,
                                lineStartX - scrollX, y, drawStart, drawEnd);
                    } else {
                        // 降级处理
                        canvas.drawText(visibleText, drawX, y - (lineHeight - textSize) / 2, textPaint);
                    }
                } else {
                    // 非自动换行模式，正常绘制
                    drawHighlightedText(canvas, line, spans, lineStartX - scrollX, y, drawStart, drawEnd);
                }
            }
        } else {
            canvas.drawText(visibleText, drawX, y - (lineHeight - textSize) / 2, textPaint);
        }

        if (showInvisibleChars) {
            drawVisibleInvisibleChars(canvas, line, lineIndex, lineStartX - scrollX, y, drawStart, drawEnd);
        }
    }




    // 4. 超长行绘制优化
    // 修复的超长行绘制方法
    private void drawLongLine(Canvas canvas, String line, int lineIndex, float y,
                              float visibleLeft, float visibleRight, float lineStartX) {
        LineRenderInfo renderInfo = lineRenderCache.get(lineIndex);

        if (renderInfo == null || !renderInfo.text.equals(line)) {
            renderInfo = new LineRenderInfo(line);
            lineRenderCache.put(lineIndex, renderInfo);
            preprocessLongLine(lineIndex, renderInfo);
        }

        if (renderInfo.isComplete && renderInfo.charPositions != null) {
            drawLongLineOptimized(canvas, renderInfo, y, visibleLeft, visibleRight, lineStartX);
        } else {
            drawLongLineFallback(canvas, line, y, visibleLeft, visibleRight, lineStartX);
        }
    }

    // 5. 异步预处理长行
    private void preprocessLongLine(int lineIndex, LineRenderInfo renderInfo) {
        if (renderInfo.isProcessing) return;

        renderInfo.isProcessing = true;

        renderExecutor.execute(() -> {
            try {
                // 分批计算字符位置
                int batchSize = 10000;
                float[] positions = new float[renderInfo.length + 1];
                float currentX = 0;

                for (int i = 0; i < renderInfo.length; i += batchSize) {
                    int end = Math.min(i + batchSize, renderInfo.length);

                    // 批量测量
                    float[] widths = new float[end - i];
                    textPaint.getTextWidths(renderInfo.text, i, end, widths);

                    // 累积位置
                    for (int j = 0; j < widths.length; j++) {
                        positions[i + j] = currentX;
                        currentX += widths[j];
                    }

                    // 避免长时间占用CPU
                    if (end < renderInfo.length) {
                        Thread.yield();
                    }
                }

                positions[renderInfo.length] = currentX;
                renderInfo.charPositions = positions;
                renderInfo.totalWidth = currentX;
                renderInfo.isComplete = true;

                // 通知更新
                mainHandler.post(() -> {
                    invalidateLineArea(lineIndex);
                });

            } catch (Exception e) {
                renderInfo.isProcessing = false;
            }
        });
    }


    // 6. 优化的长行绘制
    // 修复的优化长行绘制
    private void drawLongLineOptimized(Canvas canvas, LineRenderInfo renderInfo, float y,
                                       float visibleLeft, float visibleRight, float lineStartX) {

        // 找到可视范围内的字符
        int startChar = findVisibleCharIndex(renderInfo.charPositions, visibleLeft - lineStartX);
        int endChar = findVisibleCharIndex(renderInfo.charPositions, visibleRight - lineStartX);

        // 确保范围有效，并稍微扩展一点以避免边界问题
        startChar = Math.max(0, startChar - 1);
        endChar = Math.min(renderInfo.length, endChar + 2);

        if (startChar >= endChar || startChar >= renderInfo.length) return;

        // 计算绘制位置
        float startX = lineStartX + renderInfo.charPositions[startChar] - scrollX;

        // 分段绘制可见部分
        String visibleText = renderInfo.text.substring(startChar, endChar);
        canvas.drawText(visibleText, startX, y - (lineHeight - textSize) / 2, textPaint);
    }

    // 9. 降级绘制（当预处理未完成时）
    // 修复的降级绘制方法
    private void drawLongLineFallback(Canvas canvas, String line, float y,
                                      float visibleLeft, float visibleRight, float lineStartX) {

        // 使用更精确的估算
        float averageCharWidth = textPaint.measureText("M"); // 使用单字符作为基准

        // 计算估算的可视字符范围
        int estimatedStart = Math.max(0, (int) ((visibleLeft - lineStartX) / averageCharWidth));
        int estimatedEnd = Math.min(line.length(),
                (int) ((visibleRight - lineStartX) / averageCharWidth) + 50);

        // 进一步精确化起始位置
        if (estimatedStart > 0) {
            // 从估算位置向前微调
            String prefix = line.substring(0, Math.min(estimatedStart, line.length()));
            float actualPrefixWidth = textPaint.measureText(prefix);
            float targetWidth = visibleLeft - lineStartX;

            // 如果误差较大，进行调整
            if (Math.abs(actualPrefixWidth - targetWidth) > averageCharWidth * 10) {
                // 重新计算更精确的起始位置
                estimatedStart = findVisibleStartChar(textPaint, line, targetWidth);
            }
        }

        // 确保范围有效
        estimatedStart = Math.max(0, Math.min(estimatedStart, line.length()));
        estimatedEnd = Math.max(estimatedStart, Math.min(estimatedEnd, line.length()));

        if (estimatedStart >= estimatedEnd) return;

        // 计算准确的绘制位置
        float startX = lineStartX;
        if (estimatedStart > 0) {
            String prefixText = line.substring(0, estimatedStart);
            startX += textPaint.measureText(prefixText);
        }

        String visibleText = line.substring(estimatedStart, estimatedEnd);
        canvas.drawText(visibleText, startX - scrollX, y - (lineHeight - textSize) / 2, textPaint);
    }

    // 修复的高亮文本绘制方法
    private void drawHighlightedText(Canvas canvas, String line, List<HighlightSpan> spans,
                                     float baseX, float y, int drawStart, int drawEnd) {
        int lastColor = textColor;
        float currentX = baseX;

        // 如果drawStart > 0，需要计算前面文本的宽度偏移
        if (drawStart > 0) {
            String prefixText = line.substring(0, drawStart);
            currentX += textPaint.measureText(prefixText);
        }

        // 按位置排序spans
        List<HighlightSpan> sortedSpans = new ArrayList<>(spans);
        sortedSpans.sort((a, b) -> Integer.compare(a.start, b.start));

        int drawPos = drawStart;

        for (HighlightSpan span : sortedSpans) {
            // 跳过不在可视范围内的span
            if (span.end <= drawStart || span.start >= drawEnd) {
                continue;
            }

            // 计算span在可视范围内的部分
            int visibleSpanStart = Math.max(span.start, drawStart);
            int visibleSpanEnd = Math.min(span.end, drawEnd);

            if (visibleSpanStart >= visibleSpanEnd) continue;

            // 绘制span之前的默认颜色文本（如果有）
            if (drawPos < visibleSpanStart) {
                if (lastColor != textColor) {
                    textPaint.setColor(textColor);
                    lastColor = textColor;
                }
                String beforeText = line.substring(drawPos, visibleSpanStart);
                canvas.drawText(beforeText, currentX, y - (lineHeight - textSize) / 2, textPaint);
                currentX += textPaint.measureText(beforeText);
                drawPos = visibleSpanStart;
            }

            // 绘制span文本
            if (span.color != lastColor) {
                textPaint.setColor(span.color);
                lastColor = span.color;
            }

            String spanText = line.substring(visibleSpanStart, visibleSpanEnd);
            canvas.drawText(spanText, currentX, y - (lineHeight - textSize) / 2, textPaint);
            currentX += textPaint.measureText(spanText);
            drawPos = visibleSpanEnd;
        }

        // 绘制剩余的默认颜色文本
        if (drawPos < drawEnd) {
            if (lastColor != textColor) {
                textPaint.setColor(textColor);
            }
            String remainingText = line.substring(drawPos, drawEnd);
            canvas.drawText(remainingText, currentX, y - (lineHeight - textSize) / 2, textPaint);
        }

        // 恢复默认颜色
        if (lastColor != textColor) {
            textPaint.setColor(textColor);
        }
    }


    // 优化的不可见字符绘制
    private void drawVisibleInvisibleChars(Canvas canvas, String line, int lineIndex,
                                           float baseX, float y, int drawStart, int drawEnd) {
        Paint paint = new Paint(textPaint);
        paint.setColor(Color.parseColor("#3e3e3e"));
        paint.setTextSize(textSize * 0.6f);

        float x = baseX;

        // 只处理可见范围内的字符
        for (int i = drawStart; i < drawEnd; i++) {
            char c = line.charAt(i);
            String charStr = String.valueOf(c);
            float charWidth = textPaint.measureText(charStr);

            if (c == ' ') {
                float cx = x + charWidth / 2;
                float cy = y - lineHeight / 2;
                canvas.drawCircle(cx, cy, 2, paint);
            } else if (c == '\t') {
                float cx = x + charWidth / 2;
                float cy = y - lineHeight / 2;
                canvas.drawText("→", cx - charWidth / 2, cy + textSize * 0.3f, paint);
            }

            x += charWidth;
        }

        // 绘制换行符（如果在可见范围内）
        if (drawEnd >= line.length() &&
                (lineIndex < getLineCount() - 1 ||
                        (getLineEnd(lineIndex) < content.length() &&
                                content.charAt(getLineEnd(lineIndex)) == '\n'))) {
            paint.setTextSize(textSize * 0.5f);
            canvas.drawText("↵", x, y - lineHeight / 2 + textSize * 0.3f, paint);
        }
    }

    // 优化 getXForPosition 方法，添加缓存机制
    private final Map<Integer, Float> positionXCache = new HashMap<>();

    // 修复 getXForPosition 方法，确保缓存机制正确工作
    private float getXForPosition(int position) {
        // **修复：缓存键应该包含缩放因子，确保缩放后缓存失效**
        String cacheKey = position + "_" + scaleFactor;

        // 简化缓存机制，避免复杂的版本控制
        if (!wordWrap) {
            int line = getLineForPosition(position);
            int lineStart = getLineStart(line);
            String lineText = getLineText(line);
            int column = position - lineStart;

            if (column <= 0) {
                return lineNumberWidth + padding - scrollX;
            }

            if (column > lineText.length()) {
                column = lineText.length();
            }

            String textBeforePosition = lineText.substring(0, column);
            float width = textPaint.measureText(textBeforePosition);
            return lineNumberWidth + padding + width - scrollX;
        } else {
            // 自动换行模式
            int displayLine = getDisplayLineForPosition(position);
            if (displayLine < 0 || displayLine >= lineInfos.size()) {
                return lineNumberWidth + padding - scrollX;
            }

            LineInfo lineInfo = lineInfos.get(displayLine);
            int lineStart = getLineStart(lineInfo.originalLine);
            int positionInSubLine = position - lineStart - lineInfo.startOffset;

            if (positionInSubLine <= 0) {
                return lineNumberWidth + padding - scrollX;
            }

            if (positionInSubLine > lineInfo.text.length()) {
                positionInSubLine = lineInfo.text.length();
            }

            String textBeforePosition = lineInfo.text.substring(0, positionInSubLine);
            float width = textPaint.measureText(textBeforePosition);
            return lineNumberWidth + padding + width - scrollX;
        }
    }

    // 修复 drawCursor 方法
    private void drawCursor(Canvas canvas) {
        float x = getXForPosition(cursorPosition);

        // 使用正确的行计算方式
        int line = wordWrap ? getDisplayLineForPosition(cursorPosition) : getLineForPosition(cursorPosition);

        // **修复：使用与 getPositionCoordinates 一致的Y坐标计算**
        float baselineY = (line + 1) * lineHeight - scrollY - (lineHeight - textSize) / 2;

        // **修复：光标高度应该随缩放调整**
        float cursorHeight = textSize;
        float y1 = baselineY - cursorHeight * 0.8f;
        float y2 = baselineY + cursorHeight * 0.2f;

        // **修复：光标宽度也应该随缩放调整**
        Paint cursorPaintScaled = new Paint(cursorPaint);
        cursorPaintScaled.setStrokeWidth(4f * scaleFactor);

        canvas.drawLine(x, y1, x, y2, cursorPaintScaled);
    }


    // 修复 drawSelectionHandles 方法
    private void drawSelectionHandles(Canvas canvas) {
        if (isSelecting) {
            if (selectionStart == selectionEnd) {
                // 零宽度选择，在光标位置绘制两个手柄
                PointF pos = getPositionCoordinates(cursorPosition);

                // **修复：手柄位置应该在文本行的下方**
                float handleY = pos.y + lineHeight;

                // 在光标左右两侧绘制手柄
                startHandle.draw(canvas, pos.x - 2, handleY);
                endHandle.draw(canvas, pos.x + 2, handleY);
            } else {
                // 正常选择，分别绘制开始和结束手柄
                int startPos = Math.min(selectionStart, selectionEnd);
                int endPos = Math.max(selectionStart, selectionEnd);

                PointF startPos_coord = getPositionCoordinates(startPos);
                PointF endPos_coord = getPositionCoordinates(endPos);

                // **修复：确保手柄位置在正确的Y坐标**
                startHandle.draw(canvas, startPos_coord.x, startPos_coord.y + lineHeight);
                endHandle.draw(canvas, endPos_coord.x, endPos_coord.y + lineHeight);
            }
        }
    }

    // 修复 getPositionCoordinates 方法
    private PointF getPositionCoordinates(int position) {
        if (!wordWrap) {
            int line = getLineForPosition(position);
            float x = getXForPosition(position);
            // **修复：使用正确的Y坐标计算，包含缩放因子**
            float y = (line + yLineScale) * lineHeight - scrollY;
            return new PointF(x, y);
        } else {
            int displayLine = getDisplayLineForPosition(position);
            if (displayLine < 0 || displayLine >= lineInfos.size()) {
                float defaultX = lineNumberWidth + padding - scrollX;
                float defaultY = -scrollY;
                return new PointF(defaultX, defaultY);
            }

            float x = getXForPosition(position);
            // **修复：确保Y坐标计算考虑当前的行高（已包含缩放）**
            float y = (displayLine + yLineScale) * lineHeight - scrollY;
            return new PointF(x, y);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 处理选择手柄拖动
        if (handleSelectionHandlesTouch(event)) {
            return true;
        }

        boolean handled = scaleGestureDetector.onTouchEvent(event);
        handled |= gestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isScrolling = false;
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                requestFocus();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isScrolling = false;
                // 滚动结束后提升渲染质量
                postDelayed(() -> {
                    if (!isScrolling) {
                        if (activeHandle != null) {
                            activeHandle = null;
                            showMagnifier = false;
                        }
                    }
                }, 100);

                break;
            case MotionEvent.ACTION_MOVE:
                long now = System.currentTimeMillis();
                if (now - lastScrollTime > 16) { // 60fps
                    isScrolling = true;
                }
                lastScrollTime = now;
                break;
        }

        return true;
    }

    private boolean handleSelectionHandlesTouch(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isSelecting) {
                    if (selectionStart == selectionEnd) {
                        // Zero-width selection - check both handles at cursor position
                        PointF pos = getPositionCoordinates(cursorPosition);

                        // Check if touching either handle area
                        if (startHandle.contains(x, y, pos.x - 2, pos.y + lineHeight) ||
                                startHandle.contains(x, y, pos.x + 2, pos.y + lineHeight)) {

                            // Determine which handle based on touch position
                            if (x < pos.x) {
                                activeHandle = startHandle;
                            } else {
                                activeHandle = endHandle;
                            }

                            handleTouchOffsetY = y - (pos.y + lineHeight);
                            showMagnifier = true;
                            updateMagnifier(x, y);
                            return true;
                        }
                    } else {
                        // Normal selection handling
                        PointF startPos = getPositionCoordinates(Math.min(selectionStart, selectionEnd));
                        PointF endPos = getPositionCoordinates(Math.max(selectionStart, selectionEnd));

                        // First check if we're touching the start handle
                        if (startHandle.contains(x, y, startPos.x, startPos.y + lineHeight)) {
                            activeHandle = startHandle;
                            handleTouchOffsetY = y - (startPos.y + lineHeight);
                            showMagnifier = true;
                            updateMagnifier(x, y);
                            return true;
                        }
                        // Then check if we're touching the end handle
                        else if (endHandle.contains(x, y, endPos.x, endPos.y + lineHeight)) {
                            activeHandle = endHandle;
                            handleTouchOffsetY = y - (endPos.y + lineHeight);
                            showMagnifier = true;
                            updateMagnifier(x, y);
                            return true;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (activeHandle != null) {
                    float adjustedY = y - handleTouchOffsetY;

                    if (activeHandle == startHandle || activeHandle == endHandle) {
                        adjustedY = activeHandle.fixTextY(adjustedY);
                    }

                    int position = getPositionFromCoordinates(x, adjustedY);

                    // Handle zero-width selection expansion
                    if (selectionStart == selectionEnd && position != cursorPosition) {
                        if (activeHandle == startHandle) {
                            if (position < cursorPosition) {
                                selectionStart = position;
                                selectionEnd = cursorPosition;
                            } else {
                                selectionStart = cursorPosition;
                                selectionEnd = position;
                                activeHandle = endHandle;
                            }
                        } else {
                            if (position > cursorPosition) {
                                selectionStart = cursorPosition;
                                selectionEnd = position;
                            } else {
                                selectionStart = position;
                                selectionEnd = cursorPosition;
                                activeHandle = startHandle;
                            }
                        }
                    } else {
                        // Normal selection adjustment
                        if (activeHandle == startHandle) {
                            int currentStart = Math.min(selectionStart, selectionEnd);
                            int currentEnd = Math.max(selectionStart, selectionEnd);

                            if (position != currentStart) {
                                if (position < currentEnd) {
                                    selectionStart = position;
                                    selectionEnd = currentEnd;
                                } else {
                                    selectionStart = currentEnd;
                                    selectionEnd = position;
                                    activeHandle = endHandle;
                                }
                            }
                        } else {
                            int currentStart = Math.min(selectionStart, selectionEnd);
                            int currentEnd = Math.max(selectionStart, selectionEnd);

                            if (position != currentEnd) {
                                if (position > currentStart) {
                                    selectionStart = currentStart;
                                    selectionEnd = position;
                                } else {
                                    selectionStart = position;
                                    selectionEnd = currentStart;
                                    activeHandle = startHandle;
                                }
                            }
                        }
                    }

                    // Update magnifier
                    updateMagnifier(x, y);

                    // Clear position cache to ensure accurate coordinates after movement
                    positionXCache.clear();

                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeHandle != null) {
                    activeHandle = null;
                    showMagnifier = false;
                    invalidate();
                    showTextActionMenu();
                    return true;
                }
                break;
        }

        return false;
    }

    private void updateMagnifier(float touchX, float touchY) {
        // Record the actual touch position (the position to be observed by the magnifier)
        magnifierTargetX = touchX;
        magnifierTargetY = touchY - handleTouchOffsetY;

        // Calculate magnifier display position
        int magnifierWidth = magnifier.WIDTH;  // Use the existing WIDTH value from your MagnifierView class

        // Start by positioning the magnifier centered above the touch point
        magnifierX = touchX;
        magnifierY = touchY - 200; // Default position above

        // Check if magnifier would go off-screen to the left
        if (magnifierX - (float) magnifierWidth / 2 < 0) {
            magnifierX = (float) magnifierWidth / 2;  // Keep half the magnifier on screen
        }

        // Check if magnifier would go off-screen to the right
        if (magnifierX + (float) magnifierWidth / 2 > getWidth()) {
            magnifierX = getWidth() - (float) magnifierWidth / 2;  // Keep half the magnifier on screen
        }

        // Check if there's enough space above, if not show below
        if (magnifierY < 150) {
            magnifierY = touchY + 240; // If not enough space above, show below
        }
    }


    // 从坐标获取文本位置（处理中文等可变宽度字符）
// 从坐标获取文本位置（支持换行）
    // 修改 getPositionFromCoordinates 方法
    private int getPositionFromCoordinates(float x, float y) {
        float actualY = y + scrollY;
        int displayLine = (int) (actualY / lineHeight);

        if (displayLine < 0) displayLine = 0;

        if (!wordWrap) {
            // 原有逻辑
            if (displayLine >= getLineCount()) displayLine = getLineCount() - 1;

            int lineStart = getLineStart(displayLine);
            String lineText = getLineText(displayLine);

            float currentX = lineNumberWidth + padding - scrollX;
            int position = 0;

            for (int i = 0; i < lineText.length(); i++) {
                float charWidth = textPaint.measureText(String.valueOf(lineText.charAt(i)));
                if (x < currentX + charWidth / 2) {
                    break;
                }
                currentX += charWidth;
                position++;
            }

            return lineStart + position;
        } else {
            // 自动换行模式
            if (displayLine >= lineInfos.size()) displayLine = lineInfos.size() - 1;

            LineInfo lineInfo = lineInfos.get(displayLine);
            int lineStart = getLineStart(lineInfo.originalLine);

            String subText = lineInfo.text;
            float currentX = lineNumberWidth + padding - scrollX;
            int positionInSubLine = 0;

            for (int i = 0; i < subText.length(); i++) {
                float charWidth = textPaint.measureText(String.valueOf(subText.charAt(i)));
                if (x < currentX + charWidth / 2) {
                    break;
                }
                currentX += charWidth;
                positionInSubLine++;
            }

            return lineStart + lineInfo.startOffset + positionInSubLine;
        }
    }


    private void updateScrollBounds() {
        if (wordWrap) {
            maxScrollY = Math.max(0, getDisplayLineCount() * (int) lineHeight - getHeight() + padding);
            maxScrollX = 0; // 自动换行时不需要水平滚动
        } else {
            maxScrollY = Math.max(0, getLineCount() * (int) lineHeight - getHeight() + padding);

            float maxLineWidth = 0;
            for (int i = 0; i < getLineCount(); i++) {
                float lineWidth = textPaint.measureText(getLineText(i));
                maxLineWidth = Math.max(maxLineWidth, lineWidth);
            }
            maxScrollX = Math.max(0, (int) (maxLineWidth + lineNumberWidth + padding * 2) - getWidth());
        }
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (wordWrap) {
            // 对于超长文本，考虑异步更新
            if (content.length() > 10000) {
                // 先显示基本内容
                updateLineInfosAsync();
            } else {
                updateLineInfos();
            }
        }

        updateScrollBounds();
    }

    // 新增：异步更新行信息
    private void updateLineInfosAsync() {
        // 先创建临时的行信息，确保UI能立即响应
        lineInfos.clear();
        lineInfos.add(new LineInfo(0, 0, 0, Math.min(100, content.length()),
                content.substring(0, Math.min(100, content.length()))));

        // 然后在后台线程更新完整信息
        new Thread(() -> {
            List<LineInfo> newLineInfos = new ArrayList<>();
            float maxWidth = getWidth() - lineNumberWidth - padding * 2;

            if (maxWidth > 0) {
                for (int i = 0; i < getLineCount(); i++) {
                    String lineText = getLineText(i);
                    List<LineInfo> lineResults = new ArrayList<>();
                    wrapLineToList(textPaint, charWidth, i, lineText, maxWidth, lineResults, lineText.length() > 1000);
                    newLineInfos.addAll(lineResults);
                }
            }

            // 更新UI
            post(() -> {
                lineInfos = newLineInfos;
                updateScrollBounds();
                invalidate();
            });
        }).start();
    }


    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.getCurrX();
            scrollY = scroller.getCurrY();
            invalidate();
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN |
                EditorInfo.IME_FLAG_NO_EXTRACT_UI;

        return new BaseInputConnection(this, true) { // Changed to true for full connection
            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (selectionStart != selectionEnd) {
                    deleteSelection();
                } else if (beforeLength > 0 && cursorPosition > 0) {
                    content.delete(cursorPosition - 1, cursorPosition);
                    cursorPosition--;
                    selectionStart = selectionEnd = cursorPosition;
                    onTextChanged();
                }
                return true;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                // Special handling for newline characters to ensure auto-indent works
                if (text.toString().equals("\n") && autoIndent) {
                    int currentLine = getLineForPosition(cursorPosition);
                    String indentation = getLineIndentation(getLineText(currentLine));

                    // Delete selection if there is one
                    if (selectionStart != selectionEnd) {
                        deleteSelection();
                    }

                    // Insert newline with proper indentation
                    content.insert(cursorPosition, "\n" + indentation);
                    cursorPosition += 1 + indentation.length();
                    selectionStart = selectionEnd = cursorPosition;
                    onTextChanged();
                    return true;
                }

                // Default handling for other text
                if (selectionStart != selectionEnd) {
                    deleteSelection();
                }

                content.insert(cursorPosition, text);
                cursorPosition += text.length();
                selectionStart = selectionEnd = cursorPosition;
                onTextChanged();
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_DEL:
                            return deleteSurroundingText(1, 0);
                        case KeyEvent.KEYCODE_ENTER:
                            // Process auto-indent directly here
                            int currentLine = getLineForPosition(cursorPosition);
                            String indentation = getLineIndentation(getLineText(currentLine));

                            // Insert newline with proper indentation
                            if (selectionStart != selectionEnd) {
                                deleteSelection();
                            }

                            content.insert(cursorPosition, "\n" + indentation);
                            cursorPosition += 1 + indentation.length();
                            selectionStart = selectionEnd = cursorPosition;
                            onTextChanged();
                            return true;
                    }
                }
                return super.sendKeyEvent(event);
            }
        };
    }

    private void deleteSelection() {
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        content.delete(start, end);
        cursorPosition = start;
        selectionStart = selectionEnd = cursorPosition;
        onTextChanged();
    }

    public void showSoftInput() {
        if (imm != null && isEditable) {
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideSoftInput() {
        if (imm != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    // 手势监听器
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            // 检查是否双击
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 300 &&
                    Math.abs(e.getX() - lastClickX) < 50 &&
                    Math.abs(e.getY() - lastClickY) < 50) {
                // 双击选择单词
                onDoubleTap(e);
            } else {
                // 单击定位光标
                int position = getPositionFromCoordinates(e.getX(), e.getY());
                cursorPosition = position;
                selectionStart = selectionEnd = position;
                isSelecting = false;
                showSoftInput();
                invalidate();
            }

            lastClickTime = currentTime;
            lastClickX = e.getX();
            lastClickY = e.getY();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            scrollX = Math.max(0, Math.min(maxScrollX, scrollX + (int) distanceX));
            scrollY = Math.max(0, Math.min(maxScrollY, scrollY + (int) distanceY));
            invalidate();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            scroller.fling(scrollX, scrollY, -(int) velocityX, -(int) velocityY,
                    0, maxScrollX, 0, maxScrollY);
            invalidate();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            int position = getPositionFromCoordinates(e.getX(), e.getY());
            if (isSelecting && position >= selectionStart && position <= selectionEnd) {
                invalidate();
                postDelayed(() -> showTextActionMenu(), 100);
                return;
            }

            // Try to select current word
            int start = position;
            int end = position;

            // Check if we're at a word boundary
            boolean foundWord = false;

            // Check if position is valid and within content bounds
            if (position >= 0 && position <= content.length()) {
                // Check character at current position
                if (position < content.length() && isWordChar(content.charAt(position))) {
                    // Cursor is at the beginning of a word
                    foundWord = true;
                    // Expand forward
                    while (end < content.length() && isWordChar(content.charAt(end))) {
                        end++;
                    }
                    // Expand backward
                    while (start > 0 && isWordChar(content.charAt(start - 1))) {
                        start--;
                    }
                } else if (position > 0 && isWordChar(content.charAt(position - 1))) {
                    // Cursor is at the end of a word
                    foundWord = true;
                    start = position - 1;
                    end = position;
                    // Expand the selection to include the whole word
                    while (start > 0 && isWordChar(content.charAt(start - 1))) {
                        start--;
                    }
                    while (end < content.length() && isWordChar(content.charAt(end))) {
                        end++;
                    }
                }
            }

            // Always show selection handles, even for empty selection
            if (!foundWord || start == end) {
                // Set zero-width selection at cursor position
                selectionStart = position;
                selectionEnd = position;
                cursorPosition = position;
            } else {
                // Word found, select it
                selectionStart = start;
                selectionEnd = end;
                cursorPosition = end;
            }

            isSelecting = true;
            showCursor = false; // Hide cursor when showing handles
            invalidate();

            // Show text action menu after a short delay to ensure UI is updated
            postDelayed(() -> showTextActionMenu(), 100);
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            onLongPress(e);
            return true;
        }

        private boolean isWordChar(char c) {
            return Character.isLetterOrDigit(c) || c >= 0x4E00 && c <= 0x9FA5; // 包含中文字符
        }
    }

    // 缩放监听器
    // Improved ScaleListener that updates selection handle positions
    // 在 ScaleListener 的 onScale 方法中添加更完整的缓存清理
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private int originalSelectionStart;
        private int originalSelectionEnd;

        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            originalSelectionStart = selectionStart;
            originalSelectionEnd = selectionEnd;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.2f, Math.min(scaleFactor, 3.0f));

            // 更新文本尺寸
            textSize = 48f * scaleFactor;
            textPaint.setTextSize(textSize);
            lineNumberPaint.setTextSize(textSize * 0.9f);

            // 更新文本度量信息
            updateTextMetrics();

            // 如果在自动换行模式，重新计算行信息
            if (wordWrap) {
                updateLineInfos();
            }

            // 更新滚动边界
            updateScrollBounds();

            // **关键修复：清理所有位置相关的缓存**
            clearAllPositionCaches();

            // 保持选择状态
            selectionStart = originalSelectionStart;
            selectionEnd = originalSelectionEnd;

            // 强制重绘
            invalidate();
            return true;
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            // 缩放结束后确保选择手柄位置正确
            if (isSelecting) {
                updateHandlePositions();
            }

            // 确保光标位置可见
            ensurePositionVisible(cursorPosition);
        }
    }

    // 新增：清理所有位置相关的缓存
    private void clearAllPositionCaches() {
        positionXCache.clear();
        lineRenderCache.clear();
        charWidthCache.clear();

        // 清理字符宽度缓存并重新初始化
        charWidthCache.init(textPaint);
    }

    // Add this method to your XLEditor class
    // 修复 updateHandlePositions 方法
    private void updateHandlePositions() {
        if (selectionStart == -1 || selectionEnd == -1) return;

        // 强制清理位置缓存
        clearAllPositionCaches();

        // 重新计算手柄位置
        if (isSelecting) {
            // 确保选择区域可见
            int startPos = Math.min(selectionStart, selectionEnd);
            int endPos = Math.max(selectionStart, selectionEnd);

            // 计算新的位置坐标
            PointF startCoords = getPositionCoordinates(startPos);
            PointF endCoords = getPositionCoordinates(endPos);

            // 如果选择区域不在可视范围内，调整滚动位置
            if (startCoords.y < 0 || startCoords.y > getHeight()) {
                ensurePositionVisible(startPos);
            }
        }

        invalidate();
    }


    // 选择手柄类
// 修复 SelectionHandle 类的 contains 方法
    private class SelectionHandle {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean isStart;
        private final float BASE_HANDLE_SIZE = 36;

        public SelectionHandle(boolean isStart) {
            this.isStart = isStart;
            paint.setColor(handleColor);
        }

        public void draw(Canvas canvas, float x, float y) {
            float handleSize = BASE_HANDLE_SIZE * scaleFactor;
            float strokeWidth = 4 * scaleFactor;
            paint.setStrokeWidth(strokeWidth);

            // 绘制垂直线
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x, y - lineHeight, x, y, paint);

            // 绘制手柄圆圈
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y + handleSize / 2, handleSize / 2, paint);

            // 绘制内部圆圈
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y + handleSize / 2, handleSize / 3, paint);
            paint.setColor(handleColor);
        }

        public boolean contains(float touchX, float touchY, float handleX, float handleY) {
            float handleSize = BASE_HANDLE_SIZE * scaleFactor;
            float touchRadius = handleSize * 1.5f;

            // **修复：扩大触摸区域，考虑缩放因子**
            return Math.abs(touchX - handleX) < touchRadius &&
                    touchY > handleY - lineHeight &&
                    touchY < handleY + handleSize * 2;
        }

        public float fixTextY(float handleY) {
            float handleSize = BASE_HANDLE_SIZE * scaleFactor;
            return handleY - handleSize;
        }
    }

    // Replace your current MagnifierView class with this implementation
    private class MagnifierView {
        private final int WIDTH = 480;
        private final int HEIGHT = 240;
        private final float ZOOM = 2.0f;
        private final float CORNER_RADIUS = 20f;
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint = new Paint();

        MagnifierView() {
            borderPaint.setColor(Color.parseColor("#808080"));
            borderPaint.setStrokeWidth(2);
            borderPaint.setStyle(Paint.Style.STROKE);
            backgroundPaint.setColor(backgroundColor);
        }

        void draw(Canvas canvas) {
            // Create a snapshot of the current view state
            Bitmap viewSnapshot = createViewSnapshot();

            if (viewSnapshot == null) {
                return; // Unable to create snapshot
            }

            // Calculate the region to display in the magnifier
            float centerX = magnifierTargetX;
            float centerY = magnifierTargetY;

            // Calculate source rectangle (the area to magnify)
            int sourceWidth = (int) (WIDTH / ZOOM);
            int sourceHeight = (int) (HEIGHT / ZOOM);
            int sourceLeft = Math.max(0, (int) (centerX - (float) sourceWidth / 2));
            int sourceTop = Math.max(0, (int) (centerY - (float) sourceHeight / 2));

            // Ensure source rectangle doesn't go beyond bitmap bounds
            sourceLeft = Math.min(sourceLeft, viewSnapshot.getWidth() - sourceWidth);
            sourceTop = Math.min(sourceTop, viewSnapshot.getHeight() - sourceHeight);

            if (sourceLeft < 0) sourceLeft = 0;
            if (sourceTop < 0) sourceTop = 0;

            // Adjust source rectangle if it would go beyond bitmap edges
            int actualSourceWidth = Math.min(sourceWidth, viewSnapshot.getWidth() - sourceLeft);
            int actualSourceHeight = Math.min(sourceHeight, viewSnapshot.getHeight() - sourceTop);

            // Create destination rectangle (where to draw on magnifier)
            RectF destRect = new RectF(magnifierX - (float) WIDTH / 2, magnifierY - (float) HEIGHT / 2,
                    magnifierX + (float) WIDTH / 2, magnifierY + (float) HEIGHT / 2);

            // Create the source rectangle
            Rect sourceRect = new Rect(sourceLeft, sourceTop,
                    sourceLeft + actualSourceWidth, sourceTop + actualSourceHeight);

            // Create rounded rectangle path for clipping
            Path clipPath = new Path();
            clipPath.addRoundRect(destRect, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW);

            // Draw magnifier background
            canvas.drawPath(clipPath, backgroundPaint);

            // Save canvas state and apply clipping
            canvas.save();
            canvas.clipPath(clipPath);

            // Draw the magnified content
            canvas.drawBitmap(viewSnapshot, sourceRect, destRect, null);

            // Draw crosshair
            Paint crosshairPaint = new Paint();
            crosshairPaint.setColor(Color.RED);
            crosshairPaint.setStrokeWidth(2);
            crosshairPaint.setAlpha(180);

            canvas.drawLine(magnifierX - 15, magnifierY, magnifierX + 15, magnifierY, crosshairPaint);
            canvas.drawLine(magnifierX, magnifierY - 15, magnifierX, magnifierY + 15, crosshairPaint);

            // Restore canvas state
            canvas.restore();

            // Draw border
            canvas.drawPath(clipPath, borderPaint);

            // Recycle the snapshot to avoid memory leaks
            viewSnapshot.recycle();
        }

        // Method to create a bitmap snapshot of the current view
        private Bitmap createViewSnapshot() {
            // Check if view has dimensions
            if (getWidth() <= 0 || getHeight() <= 0) {
                return null;
            }

            // Create a bitmap of the entire view
            Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas snapshotCanvas = new Canvas(bitmap);

            // Save the magnifier state to avoid infinite recursion
            boolean originalMagnifierState = showMagnifier;
            showMagnifier = false;

            // Draw the view onto the canvas
            XLEditor.super.draw(snapshotCanvas);

            // Restore magnifier state
            showMagnifier = originalMagnifierState;

            return bitmap;
        }
    }


    private void showTextActionMenu() {
        if (textActionMenu != null && textActionMenu.isShowing()) {
            textActionMenu.dismiss();
        }

        // Create menu view
        LinearLayout menuLayout = new LinearLayout(getContext());
        menuLayout.setOrientation(LinearLayout.HORIZONTAL);
        menuLayout.setBackgroundColor(Color.parseColor("#424242"));
        menuLayout.setPadding(10, 10, 10, 10);

        // Add menu items
        String[] menuItems = {"复制", "剪切", "粘贴", "全选"};

        // Disable copy/cut for zero-width selection
        boolean hasSelection = (selectionStart != selectionEnd);

        for (String item : menuItems) {
            TextView menuItem = new TextView(getContext());
            menuItem.setText(item);
            menuItem.setPadding(20, 10, 20, 10);

            // Set appropriate colors and enable/disable based on selection
            if ((item.equals("复制") || item.equals("剪切")) && !hasSelection) {
                menuItem.setTextColor(Color.GRAY);
                menuItem.setEnabled(false);
            } else {
                menuItem.setTextColor(Color.WHITE);
                menuItem.setOnClickListener(v -> {
                    handleMenuAction(item);
                    textActionMenu.dismiss();
                });
            }

            menuLayout.addView(menuItem);

            // Add divider
            if (!item.equals("全选")) {
                View divider = new View(getContext());
                divider.setBackgroundColor(Color.parseColor("#606060"));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT);
                params.setMargins(0, 5, 0, 5);
                divider.setLayoutParams(params);
                menuLayout.addView(divider);
            }
        }

        textActionMenu = new PopupWindow(menuLayout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textActionMenu.setOutsideTouchable(true);
        textActionMenu.setFocusable(false);

        // Calculate popup position
        int[] location = new int[2];
        getLocationOnScreen(location);

        // Use cursor position for zero-width selection
        PointF pos;
        if (selectionStart == selectionEnd) {
            pos = getPositionCoordinates(cursorPosition);
        } else {
            pos = getPositionCoordinates(Math.min(selectionStart, selectionEnd));
        }

        int x = location[0] + (int) pos.x;
        int y = location[1] + (int) pos.y - 100;

        // Ensure menu doesn't go off screen
        if (x < 0) x = 0;
        if (x + 300 > getWidth()) x = getWidth() - 300; // Approximate menu width
        if (y < 0) y = location[1] + (int) pos.y + (int) lineHeight + 50;

        textActionMenu.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
    }

    private void handleMenuAction(String action) {
        switch (action) {
            case "复制":
                if (selectionStart != selectionEnd) {
                    String selectedText = getSelectedText();
                    ClipData clip = ClipData.newPlainText("text", selectedText);
                    clipboardManager.setPrimaryClip(clip);
                }
                // Clear selection after copy
                clearSelection();
                break;

            case "剪切":
                if (selectionStart != selectionEnd) {
                    String selectedText = getSelectedText();
                    ClipData clip = ClipData.newPlainText("text", selectedText);
                    clipboardManager.setPrimaryClip(clip);
                    deleteSelection();
                }
                break;

            case "粘贴":
                ClipData clip = clipboardManager.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) {
                        if (selectionStart != selectionEnd) {
                            deleteSelection();
                        }
                        content.insert(cursorPosition, text);
                        cursorPosition += text.length();
                        selectionStart = selectionEnd = cursorPosition;
                        onTextChanged();
                    }
                }
                // Clear selection after paste
                clearSelection();
                break;

            case "全选":
                selectAll();
                // Show menu again after select all
                postDelayed(() -> showTextActionMenu(), 100);
                return; // Don't clear selection
        }

        // Clear selection for other actions (except select all)
        if (!action.equals("全选")) {
            isSelecting = false;
            invalidate();
        }
    }

    // 公共方法
    public void setShowLineNumbers(boolean show) {
        this.showLineNumbers = show;
        updateTextMetrics();
        invalidate();
    }

    public void setShowInvisibleChars(boolean show) {
        this.showInvisibleChars = show;
        invalidate();
    }

    public void setSyntaxHighlighter(SyntaxHighlighter highlighter) {
        this.syntaxHighlighter = highlighter;
        invalidate();
    }

    public String getSelectedText() {
        if (selectionStart == selectionEnd) {
            return "";
        }

        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        return content.substring(start, end);
    }

    public void setSelection(int start, int end) {
        selectionStart = Math.max(0, Math.min(start, content.length()));
        selectionEnd = Math.max(0, Math.min(end, content.length()));
        cursorPosition = selectionEnd;
        isSelecting = true;
        invalidate();
    }

    public void selectAll() {
        selectionStart = 0;
        selectionEnd = content.length();
        cursorPosition = selectionEnd;
        isSelecting = true;
        // Remove automatic menu show here since it's handled in handleMenuAction
        invalidate();
    }

    public void clearSelection() {
        selectionStart = selectionEnd = cursorPosition;
        isSelecting = false;
        invalidate();
    }

    public void setEditable(boolean editable) {
        this.isEditable = editable;
        setFocusable(editable);
        setFocusableInTouchMode(editable);
    }

    private void saveUndoState() {
        if (isUndoRedoAction) return;

        TextState state = new TextState(
                content.toString(),
                cursorPosition,
                selectionStart,
                selectionEnd
        );

        undoStack.push(state);
        redoStack.clear();

        // Limit stack size
        while (undoStack.size() > MAX_UNDO_STACK_SIZE) {
            undoStack.remove(0);
        }
    }

    public void undo() {
        if (undoStack.size() <= 1) return;

        isUndoRedoAction = true;

        // Save current state to redo stack
        TextState currentState = new TextState(
                content.toString(),
                cursorPosition,
                selectionStart,
                selectionEnd
        );
        redoStack.push(currentState);

        // Remove current state from undo stack
        undoStack.pop();

        // Restore previous state
        TextState prevState = undoStack.peek();
        content.setLength(0);
        content.append(prevState.text);
        cursorPosition = prevState.cursorPosition;
        selectionStart = prevState.selectionStart;
        selectionEnd = prevState.selectionEnd;

        // **修复：添加完整的状态更新**
        updateAfterTextChange();

        isUndoRedoAction = false;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;

        isUndoRedoAction = true;

        TextState redoState = redoStack.pop();

        // Save current state to undo stack
        TextState currentState = new TextState(
                content.toString(),
                cursorPosition,
                selectionStart,
                selectionEnd
        );
        undoStack.push(currentState);

        // Restore redo state
        content.setLength(0);
        content.append(redoState.text);
        cursorPosition = redoState.cursorPosition;
        selectionStart = redoState.selectionStart;
        selectionEnd = redoState.selectionEnd;

        // **修复：添加完整的状态更新**
        updateAfterTextChange();

        isUndoRedoAction = false;
    }

    // **新增：统一的文本变更后更新方法**
    private void updateAfterTextChange() {
        // 清理所有位置相关的缓存
        clearAllPositionCaches();
        // 更新文本版本（用于高亮缓存失效）
        textVersion++;
        invalidateHighlightCache();
        // 更新行信息
        updateLineBreaks();
        updateLineInfos();
        // 更新文本度量和滚动边界
        updateTextMetrics();
        updateScrollBounds();
        // 确保光标位置可见
        ensurePositionVisible(cursorPosition);
        // 重置光标闪烁
        showCursor = true;
        // 强制重绘
        invalidate();
    }

    // Enhanced search implementation with whole word matching
    public void search(String query, boolean caseSensitive, boolean regex, boolean searchWholeWord) {
        this.searchQuery = query;
        searchResults.clear();
        currentSearchIndex = -1;

        if (query.isEmpty()) {
            invalidate();
            return;
        }

        String searchText = content.toString();
        if (!caseSensitive && !regex) {
            searchText = searchText.toLowerCase();
            query = query.toLowerCase();
        }

        if (regex) {
            try {
                Pattern pattern = caseSensitive ?
                        Pattern.compile(query) :
                        Pattern.compile(query, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(content.toString());

                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    int line = getLineForPosition(start);
                    searchResults.add(new SearchResult(start, end, line, getLineText(line)));
                }
            } catch (Exception e) {
                // Invalid regex
            }
        } else {
            // Regular text search with optional whole word matching
            int index = 0;
            while ((index = searchText.indexOf(query, index)) != -1) {
                boolean addResult = true;

                // Check for whole word matching if enabled
                if (searchWholeWord) {
                    // Check character before
                    if (index > 0) {
                        char charBefore = content.charAt(index - 1);
                        if (Character.isLetterOrDigit(charBefore) || charBefore == '_') {
                            addResult = false;
                        }
                    }

                    // Check character after
                    if (addResult && index + query.length() < content.length()) {
                        char charAfter = content.charAt(index + query.length());
                        if (Character.isLetterOrDigit(charAfter) || charAfter == '_') {
                            addResult = false;
                        }
                    }
                }

                if (addResult) {
                    int line = getLineForPosition(index);
                    searchResults.add(new SearchResult(
                            index,
                            index + query.length(),
                            line,
                            getLineText(line)
                    ));
                }

                index += 1; // Move to next character to continue searching
            }
        }

        if (!searchResults.isEmpty()) {
            currentSearchIndex = 0;
            jumpToSearchResult(0);
        }

        invalidate();
    }

    public void findNext() {
        if (searchResults.isEmpty()) return;

        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size();
        jumpToSearchResult(currentSearchIndex);
    }

    public void findPrevious() {
        if (searchResults.isEmpty()) return;

        currentSearchIndex--;
        if (currentSearchIndex < 0) {
            currentSearchIndex = searchResults.size() - 1;
        }
        jumpToSearchResult(currentSearchIndex);
    }

    private void jumpToSearchResult(int index) {
        if (index < 0 || index >= searchResults.size()) return;

        SearchResult result = searchResults.get(index);
        cursorPosition = result.end;
        selectionStart = result.start;
        selectionEnd = result.end;

        // 确保搜索结果可见
        ensurePositionVisible(result.start);

        // 如果是自动换行模式，可能需要额外的滚动调整
        if (wordWrap) {
            int displayLine = getDisplayLineForPosition(result.start);
            float targetY = displayLine * lineHeight;

            // 确保整个高亮区域可见
            if (targetY < scrollY) {
                scrollY = (int) Math.max(0, targetY - padding);
            } else if (targetY + lineHeight > scrollY + getHeight()) {
                scrollY = (int) Math.min(maxScrollY, targetY + lineHeight - getHeight() + padding);
            }
        }

        invalidate();
    }

    public void replace(String replacement) {
        if (currentSearchIndex < 0 || currentSearchIndex >= searchResults.size()) return;

        SearchResult result = searchResults.get(currentSearchIndex);
        content.replace(result.start, result.end, replacement);

        // Update search results after replacement
        search(searchQuery, false, false, false);
    }

    public void replaceAll(String replacement) {
        if (searchResults.isEmpty()) return;

        // Replace from end to beginning to maintain indices
        for (int i = searchResults.size() - 1; i >= 0; i--) {
            SearchResult result = searchResults.get(i);
            content.replace(result.start, result.end, replacement);
        }

        onTextChanged();
        searchResults.clear();
        currentSearchIndex = -1;
    }

    public int getSearchResultCount() {
        return searchResults.size();
    }

    public int getCurrentSearchIndex() {
        return currentSearchIndex;
    }

    // Line jump implementation
    public void gotoLine(int lineNumber) {
        if (lineNumber < 1 || lineNumber > getLineCount()) {
            return;
        }

        int position = getLineStart(lineNumber - 1);
        cursorPosition = position;
        selectionStart = selectionEnd = position;

        ensurePositionVisible(position);
        invalidate();
    }

    private void ensurePositionVisible(int position) {
        PointF pos = getPositionCoordinates(position);

        // 使用正确的滚动计算
        int targetScrollY = (int) (pos.y + scrollY - (float) getHeight() / 2);
        int targetScrollX = (int) (pos.x + scrollX - (float) getWidth() / 2);

        targetScrollY = Math.max(0, Math.min(maxScrollY, targetScrollY));
        targetScrollX = Math.max(0, Math.min(maxScrollX, targetScrollX));

        scroller.startScroll(scrollX, scrollY,
                targetScrollX - scrollX, targetScrollY - scrollY, 300);
        invalidate();
    }


    // **修复：同时更新 onTextChanged 方法使用统一的更新逻辑**
    private void onTextChanged() {
        // **修复：使用统一的更新方法**
        updateAfterTextChange();

        // 只有在非撤销/重做操作时才保存状态
        if (!isUndoRedoAction) {
            saveUndoState();
        }

        // 自动完成功能
        if (autocompleteProvider != null && isEditable) {
            postDelayed(() -> showAutocomplete(), 100);
        }
    }

    private void invalidateHighlightCache() {
        // 可以选择性地清除缓存，而不是全部清除
        highlightCache.clear();
        lineVersions.clear();
    }

    public void setAutocompleteProvider(AutocompleteProvider provider) {
        this.autocompleteProvider = provider;
    }

    private void showAutocomplete() {
        if (autocompleteProvider == null || !isEditable) return;

        // Get current word prefix and position
        WordInfo wordInfo = getCurrentWordInfo(cursorPosition, content);
        if (wordInfo.prefix.isEmpty()) {
            hideAutocomplete();
            return;
        }

        // Get suggestions with position info
        autocompleteSuggestions = autocompleteProvider.getSuggestions(
                wordInfo.prefix, wordInfo.startPosition, content.toString());

        if (autocompleteSuggestions.isEmpty()) {
            hideAutocomplete();
            return;
        }

        // Create custom adapter with better layout
        ArrayAdapter<AutocompleteItem> adapter = new ArrayAdapter<AutocompleteItem>(
                getContext(),
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                autocompleteSuggestions
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);

                AutocompleteItem item = getItem(position);
                text1.setText(item.displayText);
                text1.setTextColor(Color.WHITE);
                text1.setTextSize(14);

                text2.setText(item.detail);
                text2.setTextColor(Color.GRAY);
                text2.setTextSize(12);

                view.setBackgroundColor(Color.parseColor("#252526"));
                view.setPadding(15, 10, 15, 10);

                return view;
            }
        };

        autocompleteListView.setAdapter(adapter);

        // Calculate popup dimensions based on content
        int itemHeight = 150; // Approximate height per item
        int maxHeight = 800;
        int width = calculateAutocompleteWidth(getResources(), autocompleteSuggestions);
        int height = Math.min(maxHeight, autocompleteSuggestions.size() * itemHeight);

        // Calculate position
        int[] location = new int[2];
        getLocationOnScreen(location);

        PointF pos = getPositionCoordinates(wordInfo.startPosition);
        int x = location[0] + (int) pos.x;
        int y = location[1] + (int) pos.y + (int) lineHeight;

        // Adjust position if it goes off screen
        if (x + width > getWidth()) {
            x = getWidth() - width - 20;
        }

        // Check if there's enough space below
        if (y + height > location[1] + getHeight()) {
            // Show above cursor
            y = location[1] + (int) pos.y - height;
        }

        autocompletePopup.setWidth(width);
        autocompletePopup.setHeight(height);
        autocompletePopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
        isAutocompleteShowing = true;
    }


    private void hideAutocomplete() {
        if (autocompletePopup != null && autocompletePopup.isShowing()) {
            autocompletePopup.dismiss();
        }
        isAutocompleteShowing = false;
    }


    // Improved insert autocomplete with better replacement logic
    private void insertAutocomplete(AutocompleteItem item) {
        WordInfo wordInfo = getCurrentWordInfo(cursorPosition, content);

        // Replace the entire word, not just the prefix
        content.replace(wordInfo.startPosition, wordInfo.endPosition, item.displayText);
        cursorPosition = wordInfo.startPosition + item.displayText.length();
        selectionStart = selectionEnd = cursorPosition;

        onTextChanged();
        hideAutocomplete();
    }


    // Getter/Setter
    public boolean isWordWrap() {
        return wordWrap;
    }

    public void setWordWrap(boolean wordWrap) {
        this.wordWrap = wordWrap;
        updateLineInfos();
        updateScrollBounds();
        invalidate();
    }

    // 优化后的 updateLineInfos 方法
    private void updateLineInfos() {
        lineInfos.clear();

        if (!wordWrap) {
            // 不换行时，每个原始行对应一个显示行
            for (int i = 0; i < getLineCount(); i++) {
                String lineText = getLineText(i);
                lineInfos.add(new LineInfo(i, 0, 0, lineText.length(), lineText));
            }
        } else {
            // 换行时，需要计算每行的换行情况
            float maxWidth = getWidth() - lineNumberWidth - padding * 2;
            if (maxWidth <= 0) {
                // 宽度还未确定，暂时不处理
                return;
            }

            // 异步处理超长行
            for (int i = 0; i < getLineCount(); i++) {
                String lineText = getLineText(i);

                // 对于超长行，使用优化的换行算法
                if (lineText.length() > 1000) {
                    wrapLineFast(i, lineText, maxWidth);
                } else {
                    wrapLine(i, lineText, maxWidth);
                }
            }
        }
    }

    // 新增：快速换行算法（针对超长行）
    private void wrapLineFast(int originalLine, String lineText, float maxWidth) {
        if (lineText.isEmpty()) {
            lineInfos.add(new LineInfo(originalLine, 0, 0, 0, ""));
            return;
        }

        // 估算每个字符的平均宽度
        float avgCharWidth = estimateAverageCharWidth(textPaint, lineText, charWidth);

        // 估算每行大约能容纳的字符数
        int estimatedCharsPerLine = Math.max(1, (int) (maxWidth / avgCharWidth));

        int subLine = 0;
        int startOffset = 0;

        while (startOffset < lineText.length()) {
            int endOffset;

            // 先用估算值快速定位
            int estimatedEnd = Math.min(startOffset + estimatedCharsPerLine, lineText.length());

            // 然后微调到准确位置
            endOffset = findWrapPointFast(textPaint, lineText, startOffset, estimatedEnd, maxWidth);

            String subText = lineText.substring(startOffset, endOffset);
            lineInfos.add(new LineInfo(originalLine, subLine, startOffset, endOffset, subText));

            startOffset = endOffset;
            subLine++;

            // 如果子行数过多，考虑延迟渲染或虚拟化
            if (subLine > 100 && (subLine % 100 == 0)) {
                // 让UI线程有机会响应
                Thread.yield();
            }
        }
    }

    private void wrapLine(int originalLine, String lineText, float maxWidth) {
        if (lineText.isEmpty()) {
            lineInfos.add(new LineInfo(originalLine, 0, 0, 0, ""));
            return;
        }

        int subLine = 0;
        int startOffset = 0;

        while (startOffset < lineText.length()) {
            int endOffset = findWrapPoint(textPaint, lineText, startOffset, maxWidth);
            String subText = lineText.substring(startOffset, endOffset);

            lineInfos.add(new LineInfo(originalLine, subLine, startOffset, endOffset, subText));

            startOffset = endOffset;
            subLine++;
        }
    }


    // 获取显示行数（考虑换行）
    private int getDisplayLineCount() {
        return wordWrap ? lineInfos.size() : getLineCount();
    }

    // 新增：获取位置对应的显示行
    private int getDisplayLineForPosition(int position) {
        if (!wordWrap) {
            return getLineForPosition(position);
        }

        for (int i = 0; i < lineInfos.size(); i++) {
            LineInfo lineInfo = lineInfos.get(i);
            int lineStart = getLineStart(lineInfo.originalLine);
            int absoluteStart = lineStart + lineInfo.startOffset;
            int absoluteEnd = lineStart + lineInfo.endOffset;

            if (position >= absoluteStart && position <= absoluteEnd) {
                return i;
            }
        }

        // 如果位置在最后，返回最后一个显示行
        return lineInfos.isEmpty() ? 0 : lineInfos.size() - 1;
    }

    private void requestHighlightAsync(final int lineIndex, final String lineText) {
        // 检查是否已经在高亮中
        if (highlightingLines.contains(lineIndex)) {
            return;
        }

        // 检查缓存版本
        Integer cachedVersion = lineVersions.get(lineIndex);
        if (cachedVersion != null && cachedVersion == textVersion) {
            return; // 缓存仍然有效
        }

        highlightingLines.add(lineIndex);

        highlightExecutor.execute(() -> {
            try {
                // 在后台线程计算高亮
                if (syntaxHighlighter != null) {
                    List<HighlightSpan> spans = syntaxHighlighter.highlight(this, lineText, lineIndex);

                    // 合并和优化 spans
                    List<HighlightSpan> optimizedSpans = mergeAndOptimizeSpans(spans, lineText.length(), textColor);

                    // 回到主线程更新缓存
                    mainHandler.post(() -> {
                        highlightCache.put(lineIndex, optimizedSpans);
                        lineVersions.put(lineIndex, textVersion);
                        highlightingLines.remove(lineIndex);

                        // 只重绘该行区域
                        invalidateLineArea(lineIndex);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> highlightingLines.remove(lineIndex));
            }
        });
    }


    private void invalidateLineArea(int lineIndex) {
        float y = lineIndex * lineHeight - scrollY;
        invalidate(0, (int) y, getWidth(), (int) (y + lineHeight));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // 关闭高亮线程池
        if (highlightExecutor != null) {
            highlightExecutor.shutdown();
            try {
                if (!highlightExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    highlightExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                highlightExecutor.shutdownNow();
            }
        }
        if (renderExecutor != null) {
            renderExecutor.shutdown();
            try {
                if (!renderExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    renderExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                renderExecutor.shutdownNow();
            }
        }

        lineRenderCache.clear();
        // 清理缓存
        highlightCache.clear();
        highlightingLines.clear();
        lineVersions.clear();
    }


}