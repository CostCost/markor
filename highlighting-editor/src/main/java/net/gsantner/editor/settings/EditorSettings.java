/*#######################################################
 *
 * SPDX-FileCopyrightText: 2026 Gregor Santner <gsantner AT mailbox DOT org>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Settings contract required by the highlighting editor kernel.
 * Host applications implement this interface to supply their own
 * preferences; a SharedPreferences-backed default is provided by
 * {@link DefaultEditorSettings}.
#########################################################*/
package net.gsantner.editor.settings;

import androidx.annotation.ColorInt;

/**
 * The subset of editor settings used by the {@code HighlightingEditor} kernel
 * and the built-in Markdown highlighter.
 */
public interface EditorSettings {

    // ---- General editor settings ----

    /**
     * @return true if the system spelling-checker red underline should be suppressed.
     */
    boolean isDisableSpellingRedUnderline();

    /**
     * @return Android font family name used by the editor (e.g. "sans-serif-regular").
     */
    String getFontFamily();

    /**
     * @return Foreground text color of the editor.
     */
    @ColorInt
    int getEditorForegroundColor();

    /**
     * @return Number of character spaces a tab character is rendered with.
     */
    int getTabWidth();

    // ---- Markdown highlighter settings ----

    /**
     * @return Debounce delay in milliseconds before highlighting is recomputed after an edit.
     */
    int getMarkdownHighlightingDelay();

    /**
     * @return true if two or more trailing spaces (hard line break) are highlighted.
     */
    boolean isMarkdownHighlightLineEnding();

    /**
     * @return true if inline code is rendered with a monospace font.
     */
    boolean isHighlightCodeMonospaceFont();

    /**
     * @return true if headings are rendered with a size proportional to their level.
     */
    boolean isHighlightBiggerHeadings();

    /**
     * @return true if code blocks are highlighted with a background color.
     */
    boolean isHighlightCodeBlock();
}
