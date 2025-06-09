package com.nwdxlgzs.xleditor;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ShowDialog {
    private final XLEditor editor;
    public ShowDialog(XLEditor editor){
        this.editor = editor;
    }
    public void showGotoLineDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(editor.getContext());
        builder.setTitle("跳转到行");

        final EditText input = new EditText(editor.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("输入行号 (1-" + editor.getLineCount() + ")");
        builder.setView(input);

        builder.setPositiveButton("跳转", (dialog, which) -> {
            try {
                int lineNumber = Integer.parseInt(input.getText().toString());
                editor.gotoLine(lineNumber);
            } catch (NumberFormatException e) {
                // Invalid input
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @SuppressLint("DefaultLocale")
    public void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(editor.getContext());
        builder.setTitle("查找和替换");

        // Create custom layout for search dialog
        LinearLayout layout = new LinearLayout(editor.getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        // Search input
        EditText searchInput = new EditText(editor.getContext());
        searchInput.setHint("查找内容");
        searchInput.setText(editor.searchQuery);
        layout.addView(searchInput);

        // Replace input
        EditText replaceInput = new EditText(editor.getContext());
        replaceInput.setHint("替换为");
        layout.addView(replaceInput);

        // Options
        CheckBox caseSensitiveCheck = new CheckBox(editor.getContext());
        caseSensitiveCheck.setText("区分大小写");
        layout.addView(caseSensitiveCheck);

        CheckBox regexCheck = new CheckBox(editor.getContext());
        regexCheck.setText("使用正则表达式");
        layout.addView(regexCheck);

        // 新增：完整词匹配选项
        CheckBox wholeWordCheck = new CheckBox(editor.getContext());
        wholeWordCheck.setText("完整词匹配");
        layout.addView(wholeWordCheck);

        // Search results info
        TextView resultsInfo = new TextView(editor.getContext());
        resultsInfo.setPadding(0, 10, 0, 10);
        resultsInfo.setText("准备查找...");
        layout.addView(resultsInfo);

        // Button container
        LinearLayout buttonLayout = new LinearLayout(editor.getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 10, 0, 0);

        Button findButton = new Button(editor.getContext());
        findButton.setText("查找");
        findButton.setOnClickListener(v -> {
            String query = searchInput.getText().toString();
            if (!query.isEmpty()) {
                editor.search(query, caseSensitiveCheck.isChecked(), regexCheck.isChecked(), wholeWordCheck.isChecked());
                if (editor.searchResults.isEmpty()) {
                    resultsInfo.setText("未找到匹配项");
                } else {
                    resultsInfo.setText(String.format("找到 %d 个匹配项 (当前: %d)",
                            editor.searchResults.size(), editor.currentSearchIndex + 1));
                }
            }
        });
        buttonLayout.addView(findButton);

        Button findNextButton = new Button(editor.getContext());
        findNextButton.setText("下一个");
        findNextButton.setOnClickListener(v -> {
            editor.findNext();
            if (!editor.searchResults.isEmpty()) {
                resultsInfo.setText(String.format("找到 %d 个匹配项 (当前: %d)",
                        editor.searchResults.size(), editor.currentSearchIndex + 1));
            }
        });
        buttonLayout.addView(findNextButton);

        Button findPrevButton = new Button(editor.getContext());
        findPrevButton.setText("上一个");
        findPrevButton.setOnClickListener(v -> {
            editor.findPrevious();
            if (!editor.searchResults.isEmpty()) {
                resultsInfo.setText(String.format("找到 %d 个匹配项 (当前: %d)",
                        editor.searchResults.size(), editor.currentSearchIndex + 1));
            }
        });
        buttonLayout.addView(findPrevButton);

        layout.addView(buttonLayout);

        // Replace buttons
        LinearLayout replaceButtonLayout = new LinearLayout(editor.getContext());
        replaceButtonLayout.setOrientation(LinearLayout.HORIZONTAL);
        replaceButtonLayout.setPadding(0, 5, 0, 0);

        Button replaceButton = new Button(editor.getContext());
        replaceButton.setText("替换");
        replaceButton.setOnClickListener(v -> {
            String replacement = replaceInput.getText().toString();
            editor.replace(replacement);
            // Re-search to update results
            editor.search(searchInput.getText().toString(),
                    caseSensitiveCheck.isChecked(), regexCheck.isChecked(), wholeWordCheck.isChecked());
            if (!editor.searchResults.isEmpty()) {
                resultsInfo.setText(String.format("已替换，剩余 %d 个匹配项", editor.searchResults.size()));
            }
        });
        replaceButtonLayout.addView(replaceButton);

        Button replaceAllButton = new Button(editor.getContext());
        replaceAllButton.setText("全部替换");
        replaceAllButton.setOnClickListener(v -> {
            String replacement = replaceInput.getText().toString();
            int count = editor.searchResults.size();
            editor.replaceAll(replacement);
            resultsInfo.setText(String.format("已替换 %d 个匹配项", count));
        });
        replaceButtonLayout.addView(replaceAllButton);

        layout.addView(replaceButtonLayout);

        builder.setView(layout);
        builder.setNegativeButton("关闭", (dialog, which) -> {
            editor.clearSearchHighlight();
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Auto-search if there's selected text
        if (editor.selectionStart != editor.selectionEnd) {
            searchInput.setText(editor.getSelectedText());
            searchInput.selectAll();
        }
    }
}
