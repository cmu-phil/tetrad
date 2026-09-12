///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.workbench;

import javax.swing.*;
import java.awt.*;

/**
 * The shared visual style of graph workbenches: colors, fonts, and rendering hints for graph nodes and edges.
 * <p>
 * Everything here is read from the active Swing Look &amp; Feel at call time, so nodes and edges follow light and dark
 * mode automatically and match the session editor cards. Callers should not cache the results across a Look &amp;
 * Feel change.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class WorkbenchStyle {

    private static final Font FALLBACK_FONT = new Font("Dialog", Font.BOLD, 12);

    // ---------------------------------------------------------------- colors
    //
    // Tetrad has no palette of its own. Canvas, card, border, text, and selection come from the active Look and
    // Feel (FlatLaf Light: panel #F2F2F2, component white, border a 20% shade, accent #2675BF; FlatLaf Dark:
    // panel #3C3F41, component a 5% tint, border a 19% tint, accent #4B6EAF), so graph and session views look like
    // the rest of the application in either mode. The only colors added are categorical and published: Paul Tol's
    // "muted" set for the nine session-node types, chosen for deuteranopia and protanopia, and Okabe-Ito blue, sky
    // blue, vermilion, and orange for data marks and the highlight. Light and dark differ only in how far a hue is
    // tinted onto the card and in which direction a label is pushed to read on its band.

    /** Tol muted indigo 332288. */
    public static final Color TOL_INDIGO = new Color(0x33, 0x22, 0x88);
    /** Tol muted cyan 88CCEE. */
    public static final Color TOL_CYAN = new Color(0x88, 0xCC, 0xEE);
    /** Tol muted teal 44AA99. */
    public static final Color TOL_TEAL = new Color(0x44, 0xAA, 0x99);
    /** Tol muted green 117733. */
    public static final Color TOL_GREEN = new Color(0x11, 0x77, 0x33);
    /** Tol muted olive 999933. */
    public static final Color TOL_OLIVE = new Color(0x99, 0x99, 0x33);
    /** Tol muted sand DDCC77. */
    public static final Color TOL_SAND = new Color(0xDD, 0xCC, 0x77);
    /** Tol muted rose CC6677. */
    public static final Color TOL_ROSE = new Color(0xCC, 0x66, 0x77);
    /** Tol muted wine 882255. */
    public static final Color TOL_WINE = new Color(0x88, 0x22, 0x55);
    /** Tol muted purple AA4499. */
    public static final Color TOL_PURPLE = new Color(0xAA, 0x44, 0x99);

    /** Okabe-Ito blue 0072B2: light-mode plot points and histogram bars. */
    public static final Color OI_BLUE = new Color(0x00, 0x72, 0xB2);
    /** Okabe-Ito sky blue 56B4E9: dark-mode plot points and histogram bars. */
    public static final Color OI_SKY = new Color(0x56, 0xB4, 0xE9);
    /** Okabe-Ito vermilion D55E00: light-mode regression and reference lines. */
    public static final Color OI_VERMILION = new Color(0xD5, 0x5E, 0x00);
    /** Okabe-Ito orange E69F00: the highlight in both modes, and dark-mode regression and reference lines. */
    public static final Color OI_ORANGE = new Color(0xE6, 0x9F, 0x00);

    private WorkbenchStyle() {
    }

    // ---------------------------------------------------------------- helpers

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    /**
     * Whether the active Look &amp; Feel is dark.
     *
     * @return true if dark.
     */
    public static boolean isDarkMode() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
    }

    /**
     * Linear blend of two colors.
     *
     * @param a the first color.
     * @param b the second color.
     * @param t the weight on the second color, clamped to [0, 1].
     * @return the blend.
     */
    public static Color blend(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round((1.0 - t) * a.getRed() + t * b.getRed());
        int g = (int) Math.round((1.0 - t) * a.getGreen() + t * b.getGreen());
        int bl = (int) Math.round((1.0 - t) * a.getBlue() + t * b.getBlue());
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * WCAG 2 contrast ratio between two colors, from 1 (identical) to 21 (black on white).
     *
     * @param a one color.
     * @param b the other.
     * @return the ratio.
     */
    public static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double u = v / 255.0;
        return u <= 0.03928 ? u / 12.92 : Math.pow((u + 0.055) / 1.055, 2.4);
    }

    /**
     * A text color for the given hue that reads on the given background: the hue pushed toward white or toward
     * black, whichever first clears a 4.5:1 contrast ratio (WCAG AA for small text). In dark mode the push toward
     * white is tried first, in light mode the push toward black, so labels follow the mode's idiom where both would
     * work.
     *
     * @param hue        the hue to derive the text color from.
     * @param background the color the text will sit on.
     * @return the text color.
     */
    public static Color readableOn(Color hue, Color background) {
        Color first = isDarkMode() ? Color.WHITE : Color.BLACK;
        Color second = isDarkMode() ? Color.BLACK : Color.WHITE;
        for (double t = 0.45; t <= 0.90; t += 0.05) {
            Color c = blend(hue, first, t);
            if (contrast(c, background) >= 4.5) return c;
            c = blend(hue, second, t);
            if (contrast(c, background) >= 4.5) return c;
        }
        return isDarkMode() ? Color.WHITE : Color.BLACK;
    }

    /**
     * Turns on antialiasing and quality rendering hints.
     *
     * @param g2 the graphics.
     */
    public static void applyHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    // ---------------------------------------------------------------- Look and Feel colors

    /**
     * The Look and Feel's panel background: the canvas.
     *
     * @return the panel background.
     */
    public static Color panelBackground() {
        return uiColor("Panel.background", isDarkMode() ? new Color(60, 63, 65) : new Color(242, 242, 242));
    }

    /**
     * The Look and Feel's label foreground: the text color.
     *
     * @return the label foreground.
     */
    public static Color labelForeground() {
        return uiColor("Label.foreground", isDarkMode() ? new Color(187, 187, 187) : Color.BLACK);
    }

    /**
     * The Look and Feel's component background (white in FlatLaf Light, a 5% tint of the panel in FlatLaf Dark):
     * the card fill.
     *
     * @return the component background.
     */
    public static Color componentBackground() {
        Color c = UIManager.getColor("TextField.background");
        if (c != null) return c;
        return isDarkMode() ? new Color(70, 73, 74) : Color.WHITE;
    }

    /**
     * The Look and Feel's component border color.
     *
     * @return the border color.
     */
    public static Color lafBorder() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) c = UIManager.getColor("Separator.foreground");
        if (c != null) return c;
        return isDarkMode() ? new Color(97, 99, 101) : new Color(194, 194, 194);
    }

    /**
     * The Look and Feel's accent color, which follows the OS accent when FlatLaf is configured to.
     *
     * @return the accent color.
     */
    public static Color lafAccent() {
        Color c = UIManager.getColor("Component.accentColor");
        if (c == null) c = UIManager.getColor("Component.focusColor");
        if (c != null) return c;
        return isDarkMode() ? new Color(0x4B, 0x6E, 0xAF) : new Color(0x26, 0x75, 0xBF);
    }

    /**
     * The selection color: the Look and Feel's accent, lightened in dark mode where FlatLaf Dark's accent is only
     * about 1.8:1 against the card and too faint for a border.
     *
     * @return the accent.
     */
    public static Color accent() {
        return isDarkMode() ? blend(lafAccent(), Color.WHITE, 0.35) : lafAccent();
    }

    /**
     * The highlight color, for edges carrying the model highlight flag: Okabe-Ito orange, far from the blue accent
     * for every kind of vision, in both modes.
     *
     * @return the highlight.
     */
    public static Color highlight() {
        return OI_ORANGE;
    }

    /**
     * The fill of a node or card body: the Look and Feel's component background.
     *
     * @return the card fill.
     */
    public static Color cardFill() {
        return componentBackground();
    }

    // ---------------------------------------------------------------- nodes

    /**
     * The node label font: the Look and Feel's label font, bold.
     *
     * @return the font.
     */
    public static Font nodeFont() {
        Font f = UIManager.getFont("Label.font");
        if (f == null) f = FALLBACK_FONT;
        return f.deriveFont(Font.BOLD);
    }

    /**
     * The node text color: the Look and Feel's label foreground.
     *
     * @return the node text color.
     */
    public static Color nodeText() {
        return labelForeground();
    }

    /**
     * Fill of a measured-variable node: the plain card fill; the border and shape carry the node.
     *
     * @return the measured node fill.
     */
    public static Color measuredFill() {
        return cardFill();
    }

    /**
     * Fill of a latent-variable node: a faint indigo on the card fill, at least 13 Lab units from a measured node
     * for normal, deuteranopic, and protanopic vision, with the label foreground still above 5:1 on it; the ellipse
     * shape does the rest.
     *
     * @return the latent node fill.
     */
    public static Color latentFill() {
        return blend(cardFill(), TOL_INDIGO, isDarkMode() ? 0.25 : 0.14);
    }

    /**
     * Fill of a selected node, given its unselected fill.
     *
     * @param base the unselected fill.
     * @return the selected fill.
     */
    public static Color selectedFill(Color base) {
        return blend(base, accent(), isDarkMode() ? 0.35 : 0.12);
    }

    /**
     * Border of an unselected node: the Look and Feel's component border.
     *
     * @return the node border color.
     */
    public static Color nodeBorder() {
        return lafBorder();
    }

    /**
     * Border of a selected node.
     *
     * @return the selected border color.
     */
    public static Color selectedBorder() {
        return accent();
    }

    // ---------------------------------------------------------------- edges

    /**
     * Color of an ordinary edge: a neutral grey between the label foreground and the panel, about 6:1 on the panel
     * in light mode and 4.5:1 in dark, so edges are clear but the cards lead.
     *
     * @return the edge color.
     */
    public static Color edge() {
        return isDarkMode()
                ? blend(labelForeground(), panelBackground(), 0.15)
                : blend(labelForeground(), panelBackground(), 0.38);
    }

    /**
     * Color of a selected edge.
     *
     * @return the selected edge color.
     */
    public static Color edgeSelected() {
        return accent();
    }

    /**
     * Color of a highlighted edge.
     *
     * @return the highlighted edge color.
     */
    public static Color edgeHighlighted() {
        return highlight();
    }

    /**
     * Interior fill of a circle endpoint, matching the workbench background so the circle reads as hollow.
     *
     * @return the circle interior color.
     */
    public static Color circleInterior() {
        return panelBackground();
    }

    // ---------------------------------------------------------------- plots

    /**
     * Color of plot points and histogram bars: Okabe-Ito blue on a light plot, sky blue on a dark one.
     *
     * @return the mark color.
     */
    public static Color plotMark() {
        return isDarkMode() ? OI_SKY : OI_BLUE;
    }

    /**
     * Color of regression and reference lines: Okabe-Ito vermilion on a light plot, orange on a dark one. Either
     * pair with the mark color is distinguishable for every kind of vision.
     *
     * @return the line color.
     */
    public static Color plotLine() {
        return isDarkMode() ? OI_ORANGE : OI_VERMILION;
    }
}
