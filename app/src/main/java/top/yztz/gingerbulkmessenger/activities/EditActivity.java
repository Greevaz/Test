/*
 * Copyright (C) 2026 yztz
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package top.yztz.gingerbulkmessenger.activities;

import static top.yztz.gingerbulkmessenger.util.TextParser.VARIABLE_PATTERN;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.view.KeyEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.yztz.gingerbulkmessenger.R;
import top.yztz.gingerbulkmessenger.data.DataModel;
import top.yztz.gingerbulkmessenger.data.TemplateManager;
import top.yztz.gingerbulkmessenger.util.ToastUtil;

public class EditActivity extends AppCompatActivity {
    private static final String TAG = "EditActivity";
    private EditText mEt;
//    private DrawerLayout mDrawerLayout;
    private BottomAppBar mBottomAppBar;
    private FloatingActionButton mBtnSave;
    private boolean edited;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        mEt = findViewById(R.id.et_editor);
//        mEt.setLineSpacing(0, 1.4f); // Fixed line height for stability
        mBtnSave = findViewById(R.id.btn_save);
        mBtnSave.setOnClickListener(v->{
            DataModel.setTemplate(mEt.getText().toString().trim());
            DataModel.saveAsHistory(EditActivity.this);
            ToastUtil.show(EditActivity.this, getString(R.string.save_success));
            finish();
        });

        mBottomAppBar = findViewById(R.id.bottomAppBar);
        mBottomAppBar.setOnMenuItemClickListener(v->{
            MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(EditActivity.this);
            int itemId = v.getItemId();
            if (itemId == R.id.btn_var) {
                dialogBuilder.setTitle(getString(R.string.variable_selection_title))
                        .setItems(DataModel.getTitles(), (dialog, which) -> {
                            int loc = mEt.getSelectionStart();
                            String pat = "${" + DataModel.getTitles()[which] + "}";
                            if (loc == -1) mEt.getText().append(pat);
                            else mEt.getText().insert(loc, pat);
                            dialog.dismiss();
                        }).setCancelable(true).show();
                return true;
            } else if (itemId == R.id.btn_clear) {
                dialogBuilder.setTitle(getString(R.string.confirm_clear_title))
                        .setCancelable(true).setMessage(getString(R.string.confirm_clear_msg))
                        .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                            mEt.getText().clear();
                            dialog.dismiss();
                        }).setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                            dialog.dismiss();
                        }).show();
                // Respond to positive button press
                return true;
            } else if (itemId == R.id.btn_save_template) {
                showSaveTemplateDialog();
                return true;
            } else if (itemId == R.id.btn_restore_template) {
                showRestoreTemplateDialog();
                return true;
            }
                return false;
        });


        //获取已经保存的内容并显示
        mEt.setText(DataModel.getTemplate());
        highlight(mEt.getText());


        mEt.addTextChangedListener(new TextWatcher() {
            private int pendingDeleteStart = -1;
            private int pendingDeleteLength = -1;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                pendingDeleteStart = -1;
                pendingDeleteLength = -1;
                
                // Detect deletion: count > after means characters are being removed
                if (count > after && count > 0) {
                    Editable editable = mEt.getText();
                    if (editable != null) {
                        // Check if any part of a variable is being deleted
                        VariableChipSpan[] spans = editable.getSpans(start, start + count, VariableChipSpan.class);
                        for (VariableChipSpan span : spans) {
                            int spanStart = editable.getSpanStart(span);
                            int spanEnd = editable.getSpanEnd(span);
                            // If only part of the variable is deleted, we need to delete the whole thing
                            if (!(start <= spanStart && start + count >= spanEnd)) {
                                // Partial deletion detected - record the whole span for deletion
                                pendingDeleteStart = spanStart;
                                pendingDeleteLength = spanEnd - spanStart;
                                break;
                            }
                        }
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingDeleteStart >= 0 && pendingDeleteLength > 0) {
                    pendingDeleteStart = -1;
                    pendingDeleteLength = -1;
                    
                    mEt.removeTextChangedListener(this);
                    // Calculate remaining part to delete
                    // The deletion already happened, so we need to find and remove any leftover
                    VariableChipSpan[] leftoverSpans = s.getSpans(0, s.length(), VariableChipSpan.class);
                    for (VariableChipSpan span : leftoverSpans) {
                        int spanStart = s.getSpanStart(span);
                        int spanEnd = s.getSpanEnd(span);
                        // Check if this is a broken span (text doesn't match pattern)
                        String spanText = s.subSequence(spanStart, spanEnd).toString();
                        if (!VARIABLE_PATTERN.matcher(spanText).matches()) {
                            s.delete(spanStart, spanEnd);
                            break;
                        }
                    }
                    highlight(s);
                    mEt.addTextChangedListener(this);
                    edited = true;
                    return;
                }
                
                mEt.removeTextChangedListener(this);
                highlight(s);
                mEt.addTextChangedListener(this);
                edited = true;
            }
        });
        //线性布局
//        RecyclerView.LayoutManager manager = new LinearLayoutManager(this);
//        mRv.setLayoutManager(manager);
//        //分割线
//        mRv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
//        mRv.setAdapter(adapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (edited) {
                    new MaterialAlertDialogBuilder(EditActivity.this)
                            .setTitle(getString(R.string.edit_unsaved_title))
                            .setMessage(getString(R.string.edit_unsaved_msg))
                            .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                                setEnabled(false); // 禁用此回调
                                getOnBackPressedDispatcher().onBackPressed(); // 触发系统默认退出
                            })
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Ensure cursor doesn't land inside a chip
        mEt.setAccessibilityDelegate(null); // Optional: some systems might need this for better control
        mEt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                checkSelection(mEt.getSelectionStart(), mEt.getSelectionEnd());
            }
        });

        mEt.setOnClickListener(v -> checkSelection(mEt.getSelectionStart(), mEt.getSelectionEnd()));

        mEt.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                int selectionStart = mEt.getSelectionStart();
                int selectionEnd = mEt.getSelectionEnd();
                if (selectionStart == selectionEnd && selectionStart > 0) {
                    Editable text = mEt.getText();
                    if (text != null) {
                        VariableChipSpan[] spans = text.getSpans(selectionStart - 1, selectionStart, VariableChipSpan.class);
                        for (VariableChipSpan span : spans) {
                            int spanEnd = text.getSpanEnd(span);
                            if (selectionStart == spanEnd) {
                                int spanStart = text.getSpanStart(span);
                                text.delete(spanStart, spanEnd);
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        });
    }

    /**
     * Ask for a name and store the text currently in the editor as a reusable template.
     */
    private void showSaveTemplateDialog() {
        String content = mEt.getText().toString().trim();
        if (content.isEmpty()) {
            ToastUtil.show(this, getString(R.string.template_empty));
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        EditText nameEt = dialogView.findViewById(R.id.edit_text);
        nameEt.setHint(R.string.template_name_hint);
        nameEt.setSingleLine(true);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.save_template))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    String name = nameEt.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        ToastUtil.show(this, getString(R.string.template_name_empty));
                        return;
                    }
                    if (TemplateManager.exists(this, name)) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.template_overwrite_title))
                                .setMessage(getString(R.string.template_overwrite_msg, name))
                                .setPositiveButton(getString(R.string.ok), (d, w) -> storeTemplate(name, content))
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    } else {
                        storeTemplate(name, content);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void storeTemplate(String name, String content) {
        TemplateManager.saveTemplate(this, name, content);
        ToastUtil.show(this, getString(R.string.template_saved));
    }

    /**
     * Show the saved templates; tap to restore one into the editor, long-press to delete it.
     */
    private void showRestoreTemplateDialog() {
        List<TemplateManager.SavedTemplate> templates = TemplateManager.getTemplates(this);
        if (templates.isEmpty()) {
            ToastUtil.show(this, getString(R.string.template_none));
            return;
        }

        String[] names = new String[templates.size()];
        for (int i = 0; i < names.length; i++) names[i] = templates.get(i).name;

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.template_pick_title))
                .setItems(names, (d, which) -> {
                    TemplateManager.SavedTemplate chosen = templates.get(which);
                    String current = mEt.getText().toString().trim();
                    if (!current.isEmpty() && !current.equals(chosen.content)) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.template_replace_title))
                                .setMessage(getString(R.string.template_replace_msg))
                                .setPositiveButton(getString(R.string.ok), (d2, w) -> applyTemplate(chosen))
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    } else {
                        applyTemplate(chosen);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();

        // Long-press an entry to delete it
        dialog.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            TemplateManager.SavedTemplate target = templates.get(position);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.template_delete_title))
                    .setMessage(getString(R.string.template_delete_msg, target.name))
                    .setPositiveButton(getString(R.string.delete), (d, w) -> {
                        TemplateManager.deleteTemplate(this, target.name);
                        ToastUtil.show(this, getString(R.string.template_deleted));
                        dialog.dismiss();
                    })
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
            return true;
        });
    }

    private void applyTemplate(TemplateManager.SavedTemplate template) {
        mEt.setText(template.content); // TextWatcher re-applies the variable chips
        mEt.setSelection(mEt.getText().length());

        // Variables in the template that are not columns of the currently imported table
        Set<String> available = new LinkedHashSet<>(java.util.Arrays.asList(DataModel.getTitles()));
        Set<String> missing = new LinkedHashSet<>();
        Matcher m = VARIABLE_PATTERN.matcher(template.content);
        while (m.find()) {
            if (!available.contains(m.group(1))) missing.add(m.group(1));
        }
        if (missing.isEmpty()) {
            ToastUtil.show(this, getString(R.string.template_restored));
        } else {
            ToastUtil.show(this, getString(R.string.template_missing_vars, TextUtils.join(", ", missing)));
        }
    }

    private void checkSelection(int start, int end) {
        Editable text = mEt.getText();
        if (text == null) return;
        VariableChipSpan[] spans = text.getSpans(Math.max(0, start - 1), Math.min(text.length(), end + 1), VariableChipSpan.class);
        for (VariableChipSpan span : spans) {
            int spanStart = text.getSpanStart(span);
            int spanEnd = text.getSpanEnd(span);
            if (start > spanStart && start < spanEnd) {
                // Inside! Move to nearest edge
                if (start - spanStart < spanEnd - start) {
                    mEt.setSelection(spanStart);
                } else {
                    mEt.setSelection(spanEnd);
                }
                break;
            }
        }
    }

    private void highlight(Editable s) {
        String temp = s.toString();
        // Remove old spans
        VariableChipSpan[] oldSpans = s.getSpans(0, s.length(), VariableChipSpan.class);
        for (VariableChipSpan oldSpan : oldSpans) {
            s.removeSpan(oldSpan);
        }

        Matcher m = VARIABLE_PATTERN.matcher(temp);
        int bgColor = ContextCompat.getColor(this, R.color.md_theme_primaryContainer);
        int textColor = ContextCompat.getColor(this, R.color.md_theme_onPrimaryContainer);
        int strokeColor = ContextCompat.getColor(this, R.color.md_theme_primary);
        int padding = (int) (10 * getResources().getDisplayMetrics().density);

        while (m.find()) {
            String varName = m.group(1);
            Log.d(TAG, "find var: " + varName);
            VariableChipSpan span = new VariableChipSpan(varName, bgColor, textColor, strokeColor, padding);
            s.setSpan(span, m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Variable Chip Span
     */
    private static class VariableChipSpan extends ReplacementSpan {
        private final String displayText;
        private final int backgroundColor;
        private final int textColor;
        private final int strokeColor;
        private final int horizontalPadding;

        public VariableChipSpan(String displayText, int backgroundColor, int textColor, int strokeColor, int horizontalPadding) {
            this.displayText = displayText;
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
            this.strokeColor = strokeColor;
            this.horizontalPadding = horizontalPadding;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            float originalSize = paint.getTextSize();
            paint.setTextSize(originalSize * 0.85f); // Shrink font size
            paint.setFakeBoldText(true);
            
            float textWidth = paint.measureText(this.displayText);
            int margin = (int) (4 * horizontalPadding / 8f);
            
            // Restore size to not affect other spans
            paint.setTextSize(originalSize);
            paint.setFakeBoldText(false);
            return (int) (textWidth + 2 * horizontalPadding + 2 * margin);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            float originalSize = paint.getTextSize();
            paint.setTextSize(originalSize * 0.85f); // Shrink font size
            paint.setFakeBoldText(true);
            
            float textWidth = paint.measureText(this.displayText);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float margin = 4 * horizontalPadding / 8f;
            
            // Calculate chip height using the shrunk font metrics
            float chipHeight = (fm.descent - fm.ascent) * 1.2f;
            float verticalCenter = y + (fm.ascent + fm.descent) / 2;
            float chipTop = verticalCenter - chipHeight / 2;
            float chipBottom = verticalCenter + chipHeight / 2;
            
            RectF rect = new RectF(x + margin, chipTop, x + textWidth + 2 * horizontalPadding + margin, chipBottom);
            float radius = rect.height() / 4;
            
            // 1. Draw Background
            paint.setColor(backgroundColor);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, radius, radius, paint);
            
            // 2. Draw Stroke
            paint.setColor(strokeColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawRoundRect(rect, radius, radius, paint);
            
            // 3. Draw Text (centered vertically in the rect)
            paint.setColor(textColor);
            paint.setStyle(Paint.Style.FILL);
            // Re-calculate baseline for the shrunk text
            float textBaseline = verticalCenter - (fm.ascent + fm.descent) / 2;
            canvas.drawText(this.displayText, x + horizontalPadding + margin, textBaseline, paint);
            
            // Restore paint state
            paint.setTextSize(originalSize);
            paint.setFakeBoldText(false);
        }
    }


    /**
     * 打开编辑器
     */
    public static void openEditor(Context context) {
        if (!DataModel.loaded()) {
            ToastUtil.show(context, context.getString(R.string.error_import_data_first));
        } else {
            Intent intent = new Intent(context, EditActivity.class);
            context.startActivity(intent);
        }
    }
}
