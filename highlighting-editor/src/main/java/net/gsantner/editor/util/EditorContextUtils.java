/*#######################################################
 *
 * SPDX-FileCopyrightText: 2026 Gregor Santner <gsantner AT mailbox DOT org>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Minimal subset extracted from opoc GsContextUtils.
 * Only the routines required by the highlighting editor kernel are included.
#########################################################*/
package net.gsantner.editor.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import net.gsantner.opoc.format.GsTextUtils;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Stateless helper utilities. Equivalent to the small subset of
 * {@code GsContextUtils} used by the original implementation.
 */
public final class EditorContextUtils {

    private EditorContextUtils() {
        throw new AssertionError();
    }

    /**
     * @return true if a dark theme/mode is currently enabled.
     */
    public static boolean isDarkModeEnabled(final Context context) {
        final int state = AppCompatDelegate.getDefaultNightMode();
        if (state == AppCompatDelegate.MODE_NIGHT_YES) {
            return true;
        } else if (state == AppCompatDelegate.MODE_NIGHT_NO) {
            return false;
        } else if (context != null) {
            switch (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) {
                case Configuration.UI_MODE_NIGHT_YES:
                    return true;
                case Configuration.UI_MODE_NIGHT_NO:
                    return false;
            }
        }
        return false;
    }

    /**
     * Parse a color hex string using RGBA order (RRGGBB / RRGGBBAA),
     * unlike {@link Color#parseColor(String)} which uses ARGB.
     *
     * @param hexColorString Hex color string in RRGGBB or RRGGBBAA format.
     * @return The color, or null if the input cannot be parsed.
     */
    public static @ColorInt
    @Nullable
    Integer parseHexColorString(final String hexColorString) {
        String h = TextUtils.isEmpty(hexColorString) ? "" : hexColorString;
        h = h.replaceAll("[^A-Fa-f0-9]", "").trim();
        if (h.isEmpty() || h.length() > 8) {
            return null;
        }
        try {
            if (h.length() > 6) {
                h = h.substring(6) + (h.length() == 8 ? "" : "0") + h.substring(0, 6);
            }
            return Color.parseColor("#" + h);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Format a timestamp using the given pattern.
     *
     * @param locale   Optional locale; defaults to system locale.
     * @param format   {@link SimpleDateFormat} pattern.
     * @param datetime Timestamp in milliseconds; current time if null.
     * @return Formatted string, or the pattern itself if it is invalid.
     */
    public static String formatDateTime(@Nullable final Locale locale, @Nullable final String format, @Nullable final Long datetime) {
        try {
            final Locale l = locale != null ? locale : Locale.getDefault();
            final long t = datetime != null ? datetime : System.currentTimeMillis();
            return new SimpleDateFormat(GsTextUtils.unescapeString(format), l).format(t);
        } catch (Exception err) {
            return format;
        }
    }
}
