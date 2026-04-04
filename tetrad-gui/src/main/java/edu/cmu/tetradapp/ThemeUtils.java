package edu.cmu.tetradapp;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

public final class ThemeUtils {

    private ThemeUtils() {
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