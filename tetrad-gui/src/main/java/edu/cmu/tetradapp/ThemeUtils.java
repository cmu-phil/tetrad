package edu.cmu.tetradapp;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

public final class ThemeUtils {

    /**
     * The percent values offered for the UI font scale, smallest to largest. 100 is the Look and Feel default.
     */
    public static final int[] FONT_SCALE_PERCENTS = {85, 100, 110, 125, 150, 175, 200};

    /**
     * The unscaled size of the Look and Feel's default font, captured the first time the scale is applied so that
     * repeated scaling always derives from the same base rather than compounding.
     */
    private static int baseFontSize = -1;

    private ThemeUtils() {
    }

    /**
     * The stored UI font scale, in percent. 100, the default, means the Look and Feel's own font size.
     *
     * @return the scale in percent.
     */
    public static int getFontScalePercent() {
        return Preferences.userRoot().getInt("uiFontScalePercent", 100);
    }

    /**
     * Stores the UI font scale and applies it to the running interface.
     *
     * @param percent the scale in percent; 100 restores the Look and Feel default.
     */
    public static void setFontScalePercent(int percent) {
        Preferences.userRoot().putInt("uiFontScalePercent", percent);
        applyStoredFontScale();
    }

    /**
     * Applies the stored UI font scale to the running Look and Feel. FlatLaf treats the "defaultFont" UI default as
     * the master font, so putting a resized font there rescales every component that follows the Look and Feel;
     * components that set an explicit font (notably the Monospaced text displays) instead pick the scale up through
     * {@link #monospaced(int)} when they are next built. Called at startup after the Look and Feel is set, and again
     * whenever the setting changes.
     */
    public static void applyStoredFontScale() {
        Font base = UIManager.getLookAndFeelDefaults().getFont("defaultFont");
        if (base == null) base = UIManager.getLookAndFeelDefaults().getFont("Label.font");
        if (base == null) base = new Font("Dialog", Font.PLAIN, 12);

        if (ThemeUtils.baseFontSize <= 0) {
            ThemeUtils.baseFontSize = base.getSize();
        }

        int percent = getFontScalePercent();

        if (percent == 100) {
            UIManager.put("defaultFont", null);
        } else {
            float size = Math.max(8f, ThemeUtils.baseFontSize * percent / 100f);
            UIManager.put("defaultFont", base.deriveFont(size));
        }

        com.formdev.flatlaf.FlatLaf.updateUI();
    }

    /**
     * A plain Monospaced font whose size follows the UI font scale. Text displays that used to construct a fixed
     * Monospaced font directly should ask for it here instead, passing the size they used at 100 percent.
     *
     * @param baseSize the size of the font at a scale of 100 percent.
     * @return the scaled font.
     */
    public static Font monospaced(int baseSize) {
        return monospaced(baseSize, Font.PLAIN);
    }

    /**
     * A Monospaced font in the given style whose size follows the UI font scale.
     *
     * @param baseSize the size of the font at a scale of 100 percent.
     * @param style    the font style, as in {@link Font#Font(String, int, int)}.
     * @return the scaled font.
     */
    public static Font monospaced(int baseSize, int style) {
        int size = Math.max(8, Math.round(baseSize * getFontScalePercent() / 100f));
        return new Font(Font.MONOSPACED, style, size);
    }

    private static boolean isDarkMode() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
    }

    public static void applyTheme(boolean dark) {
        try {
            if (dark) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }

            Preferences.userRoot().putBoolean("darkMode", dark);

            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
                window.invalidate();
                window.validate();
                window.repaint();
            }
        } catch (UnsupportedLookAndFeelException e) {
            throw new RuntimeException("Could not switch theme.", e);
        }
    }
}