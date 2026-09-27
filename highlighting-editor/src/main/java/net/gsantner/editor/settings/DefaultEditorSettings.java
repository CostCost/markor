/*#######################################################
 *
 * SPDX-FileCopyrightText: 2026 Gregor Santner <gsantner AT mailbox DOT org>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Default EditorSettings implementation backed by SharedPreferences.
#########################################################*/
package net.gsantner.editor.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import net.gsantner.editor.util.EditorContextUtils;

/**
 * Default {@link EditorSettings} implementation.
 * Values are persisted in a private SharedPreferences file so that they
 * survive across sessions. Hosts may subclass this to change defaults,
 * or implement {@link EditorSettings} directly for a fully custom backend.
 */
@SuppressWarnings("unused")
public class DefaultEditorSettings implements EditorSettings {

    private static final String PREFS_NAME = "highlighting_editor_settings";

    // Preference keys
    private static final String KEY_DISABLE_SPELLING_RED_UNDERLINE = "disable_spelling_red_underline";
    private static final String KEY_FONT_FAMILY = "font_family";
    private static final String KEY_EDITOR_FOREGROUND_COLOR = "editor_foreground_color";
    private static final String KEY_TAB_WIDTH = "tab_width";
    private static final String KEY_MARKDOWN_HL_DELAY = "markdown_hl_delay";
    private static final String KEY_MARKDOWN_HL_LINE_ENDING = "markdown_hl_line_ending";
    private static final String KEY_HL_CODE_MONOSPACE = "highlight_code_monospace_font";
    private static final String KEY_HL_BIGGER_HEADINGS = "highlight_bigger_headings";
    private static final String KEY_HL_CODE_BLOCK = "highlight_code_block";

    // Defaults (match the original Markor defaults)
    public static final boolean DEFAULT_DISABLE_SPELLING_RED_UNDERLINE = true;
    public static final String DEFAULT_FONT_FAMILY = "sans-serif-regular";
    public static final @ColorInt int DEFAULT_EDITOR_FOREGROUND_LIGHT = Color.BLACK;
    public static final @ColorInt int DEFAULT_EDITOR_FOREGROUND_DARK = 0xFFE6E6E6;
    public static final int DEFAULT_TAB_WIDTH = 1;
    public static final int DEFAULT_MARKDOWN_HL_DELAY = 650;
    public static final boolean DEFAULT_MARKDOWN_HL_LINE_ENDING = false;
    public static final boolean DEFAULT_HL_CODE_MONOSPACE = false;
    public static final boolean DEFAULT_HL_BIGGER_HEADINGS = false;
    public static final boolean DEFAULT_HL_CODE_BLOCK = true;

    protected final Context _context;
    protected final SharedPreferences _prefs;

    public DefaultEditorSettings(@NonNull final Context context) {
        _context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        _prefs = _context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public boolean isDisableSpellingRedUnderline() {
        return _prefs.getBoolean(KEY_DISABLE_SPELLING_RED_UNDERLINE, DEFAULT_DISABLE_SPELLING_RED_UNDERLINE);
    }

    public void setDisableSpellingRedUnderline(final boolean value) {
        _prefs.edit().putBoolean(KEY_DISABLE_SPELLING_RED_UNDERLINE, value).apply();
    }

    @Override
    public String getFontFamily() {
        return _prefs.getString(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY);
    }

    public void setFontFamily(final String fontFamily) {
        _prefs.edit().putString(KEY_FONT_FAMILY, fontFamily).apply();
    }

    @Override
    public @ColorInt int getEditorForegroundColor() {
        final int fallback = EditorContextUtils.isDarkModeEnabled(_context)
                ? DEFAULT_EDITOR_FOREGROUND_DARK
                : DEFAULT_EDITOR_FOREGROUND_LIGHT;
        return _prefs.getInt(KEY_EDITOR_FOREGROUND_COLOR, fallback);
    }

    public void setEditorForegroundColor(final @ColorInt int color) {
        _prefs.edit().putInt(KEY_EDITOR_FOREGROUND_COLOR, color).apply();
    }

    @Override
    public int getTabWidth() {
        return _prefs.getInt(KEY_TAB_WIDTH, DEFAULT_TAB_WIDTH);
    }

    public void setTabWidth(final int width) {
        _prefs.edit().putInt(KEY_TAB_WIDTH, width).apply();
    }

    @Override
    public int getMarkdownHighlightingDelay() {
        return _prefs.getInt(KEY_MARKDOWN_HL_DELAY, DEFAULT_MARKDOWN_HL_DELAY);
    }

    public void setMarkdownHighlightingDelay(final int delayMs) {
        _prefs.edit().putInt(KEY_MARKDOWN_HL_DELAY, delayMs).apply();
    }

    @Override
    public boolean isMarkdownHighlightLineEnding() {
        return _prefs.getBoolean(KEY_MARKDOWN_HL_LINE_ENDING, DEFAULT_MARKDOWN_HL_LINE_ENDING);
    }

    public void setMarkdownHighlightLineEnding(final boolean value) {
        _prefs.edit().putBoolean(KEY_MARKDOWN_HL_LINE_ENDING, value).apply();
    }

    @Override
    public boolean isHighlightCodeMonospaceFont() {
        return _prefs.getBoolean(KEY_HL_CODE_MONOSPACE, DEFAULT_HL_CODE_MONOSPACE);
    }

    public void setHighlightCodeMonospaceFont(final boolean value) {
        _prefs.edit().putBoolean(KEY_HL_CODE_MONOSPACE, value).apply();
    }

    @Override
    public boolean isHighlightBiggerHeadings() {
        return _prefs.getBoolean(KEY_HL_BIGGER_HEADINGS, DEFAULT_HL_BIGGER_HEADINGS);
    }

    public void setHighlightBiggerHeadings(final boolean value) {
        _prefs.edit().putBoolean(KEY_HL_BIGGER_HEADINGS, value).apply();
    }

    @Override
    public boolean isHighlightCodeBlock() {
        return _prefs.getBoolean(KEY_HL_CODE_BLOCK, DEFAULT_HL_CODE_BLOCK);
    }

    public void setHighlightCodeBlock(final boolean value) {
        _prefs.edit().putBoolean(KEY_HL_CODE_BLOCK, value).apply();
    }
}
