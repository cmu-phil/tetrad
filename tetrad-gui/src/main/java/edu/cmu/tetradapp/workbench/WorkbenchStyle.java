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

    // ---------------------------------------------------------------- palette
    //
    // The "dusty" palette: cream F1E0C5, khaki C9B79C, sage 71816D, dark brown 342A21, dusty rose DA667B, extended
    // with a few more muted tones in the same register: dusty blue, plum, teal, mustard, terracotta. Roles: cream
    // is the card base, sage and plum tint the nodes, brown draws the edges, rose marks selection, and mustard is
    // the highlight. The session cards use the wider set. This palette now drives dark mode only; see the light
    // mode section below.

    /** Cream F1E0C5. */
    public static final Color CREAM = new Color(0xF1, 0xE0, 0xC5);
    /** Khaki C9B79C. */
    public static final Color KHAKI = new Color(0xC9, 0xB7, 0x9C);
    /** Sage 71816D. */
    public static final Color SAGE = new Color(0x71, 0x81, 0x6D);
    /** Dark brown 342A21. */
    public static final Color BROWN = new Color(0x34, 0x2A, 0x21);
    /** Dusty rose DA667B. */
    public static final Color ROSE = new Color(0xDA, 0x66, 0x7B);
    /** Dusty blue. */
    public static final Color DUSTY_BLUE = new Color(0x74, 0x96, 0xB4);
    /** Dusty plum. */
    public static final Color PLUM = new Color(0x9A, 0x74, 0x9E);
    /** Dusty teal. */
    public static final Color TEAL = new Color(0x5E, 0x9E, 0x98);
    /** Mustard. */
    public static final Color MUSTARD = new Color(0xD6, 0xA8, 0x3C);
    /** Terracotta. */
    public static final Color TERRACOTTA = new Color(0xC8, 0x78, 0x58);
    /** Indigo. Cooler and bluer than plum, so PM and IM read as distinct types. */
    public static final Color INDIGO = new Color(0x5A, 0x5F, 0xB5);

    // ---------------------------------------------------------------- light mode
    //
    // Light mode is native to the Look and Feel: canvas, card, border, text, and selection all come from FlatLaf
    // Light's own defaults (panel #F2F2F2, component white, border shade 20%, accent #2675BF or the OS accent), so
    // Tetrad's graph and session views look like the rest of the application. The only colors added are
    // categorical: Paul Tol's "muted" set for the nine session-node types, chosen for deuteranopia and
    // protanopia, and Okabe-Ito blue, vermilion, and orange for data marks and the highlight. Dark mode keeps
    // the dusty palette above.

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

    /** Okabe-Ito blue 0072B2: plot points and histogram bars. */
    public static final Color OI_BLUE = new Color(0x00, 0x72, 0xB2);
    /** Okabe-Ito vermilion D55E00: regression and reference lines. */
    public static final Color OI_VERMILION = new Color(0xD5, 0x5E, 0x00);
    /** Okabe-Ito orange E69F00: the light-mode highlight, far from the blue accent for every kind of vision. */
    public static final Color OI_ORANGE = new Color(0xE6, 0x9F, 0x00);

    // Dark mode values, all from the dusty palette.

    /** Card base, dark: the brown, lifted a little. */
    private static final Color BASE_DARK = blend(BROWN, Color.WHITE, 0.18);
    /** Measured node hue, dark: sage. */
    private static final Color MEASURED_HUE = SAGE;
    /** Latent node hue, dark: plum. */
    private static final Color LATENT_HUE = PLUM;
    /** Edge color, dark: khaki. */
    private static final Color EDGE_DARK = KHAKI;
    /** Node border tint, dark: khaki. */
    private static final Color BORDER_TINT_DARK = KHAKI;
    /** Selection accent, dark: rose. */
    private static final Color ACCENT_DARK = blend(ROSE, Color.WHITE, 0.15);
    /** Highlight, dark: mustard. */
    private static final Color HIGHLIGHT_DARK = blend(MUSTARD, Color.WHITE, 0.15);

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
     * In light mode, softens a color by pulling it part way toward the panel background; in dark mode returns it
     * unchanged. Used so that light mode reads as faded rather than saturated.
     *
     * @param c the color to soften.
     * @return the softened color.
     */
    public static Color fade(Color c) {
        return isDarkMode() ? c : blend(c, panelBackground(), 0.05);
    }

    /**
     * Turns on antialiasing for shapes and text. Applied once at the top of a workbench paint, it is inherited by
     * every node and edge painted beneath it.
     *
     * @param g2 the graphics to configure.
     */
    public static void applyHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    // ---------------------------------------------------------------- base colors

    /**
     * The panel background of the current Look &amp; Feel.
     *
     * @return the panel background.
     */
    public static Color panelBackground() {
        return uiColor("Panel.background", isDarkMode() ? new Color(60, 63, 65) : new Color(242, 242, 242));
    }

    /**
     * The primary label color of the current Look &amp; Feel.
     *
     * @return the label color.
     */
    public static Color labelForeground() {
        return uiColor("Label.foreground", isDarkMode() ? new Color(230, 230, 230) : new Color(30, 30, 30));
    }

    /**
     * The accent color used for selection and focus.
     *
     * @return the accent color.
     */
    public static Color accent() {
        return isDarkMode() ? ACCENT_DARK : lafAccent();
    }

    /**
     * The Look and Feel's component background (white in FlatLaf Light): the light-mode card fill.
     *
     * @return the component background.
     */
    public static Color componentBackground() {
        Color c = UIManager.getColor("TextField.background");
        return c != null ? c : Color.WHITE;
    }

    /**
     * The Look and Feel's component border color (a 20% shade of the panel in FlatLaf Light).
     *
     * @return the border color.
     */
    public static Color lafBorder() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) c = UIManager.getColor("Separator.foreground");
        return c != null ? c : new Color(194, 194, 194);
    }

    /**
     * The Look and Feel's accent color, which follows the OS accent when FlatLaf is configured to.
     *
     * @return the accent color.
     */
    public static Color lafAccent() {
        Color c = UIManager.getColor("Component.accentColor");
        if (c == null) c = UIManager.getColor("Component.focusColor");
        return c != null ? c : new Color(0x26, 0x75, 0xBF);
    }

    /**
     * A highlight color, used for edges carrying the model highlight flag: mustard, distinct from the rose used for
     * selection.
     *
     * @return the highlight color.
     */
    public static Color highlight() {
        return isDarkMode() ? HIGHLIGHT_DARK : OI_ORANGE;
    }

    /**
     * The fill of a node or card body: the panel background lifted toward the light base color in light mode, or
     * toward the dark base color in dark mode.
     *
     * @return the card fill.
     */
    public static Color cardFill() {
        Color panel = panelBackground();
        if (isDarkMode()) {
            return blend(panel, BASE_DARK, 0.30);
        }
        return componentBackground();
    }

    // ---------------------------------------------------------------- nodes

    /**
     * The font for graph node names: the Look &amp; Feel label font in bold.
     *
     * @return the node font.
     */
    public static Font nodeFont() {
        Font f = UIManager.getFont("Label.font");
        if (f == null) f = FALLBACK_FONT;
        return f.deriveFont(Font.BOLD);
    }

    /**
     * The color of graph node names.
     *
     * @return the node text color.
     */
    public static Color nodeText() {
        Color fg = labelForeground();
        return fg;
    }

    private static Color tintedFill(Color hue, double lightAmount, double darkAmount) {
        return blend(cardFill(), hue, isDarkMode() ? darkAmount : lightAmount);
    }

    /**
     * Fill color of a measured-variable node: a sage tint on the card fill.
     *
     * @return the measured node fill.
     */
    public static Color measuredFill() {
        // Light mode: plain card white, like any other component; the border and shape carry the node.
        return isDarkMode() ? tintedFill(MEASURED_HUE, 0.30, 0.45) : cardFill();
    }

    /**
     * Fill color of a latent-variable node: a plum tint on the card fill.
     *
     * @return the latent node fill.
     */
    public static Color latentFill() {
        // Light mode: a faint indigo on white, about 13 Lab units from a measured node for every kind of vision;
        // the ellipse shape does the rest.
        return isDarkMode() ? tintedFill(LATENT_HUE, 0.28, 0.45) : blend(cardFill(), TOL_INDIGO, 0.14);
    }

    /**
     * Fill color of a selected node, given its unselected fill.
     *
     * @param base the unselected fill.
     * @return the selected fill.
     */
    public static Color selectedFill(Color base) {
        return blend(base, accent(), isDarkMode() ? 0.35 : 0.12);
    }

    /**
     * Border color of an unselected node.
     *
     * @return the node border color.
     */
    public static Color nodeBorder() {
        if (!isDarkMode()) return lafBorder();
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) c = UIManager.getColor("Separator.foreground");
        if (c == null) c = new Color(100, 104, 110);
        return blend(blend(c, Color.WHITE, 0.10), BORDER_TINT_DARK, 0.45);
    }

    /**
     * Border color of a selected node.
     *
     * @return the selected node border color.
     */
    public static Color selectedBorder() {
        return accent();
    }

    // ---------------------------------------------------------------- edges

    /**
     * Default color of an edge that has no color of its own: dark brown, which sits back behind the tinted nodes.
     *
     * @return the default edge color.
     */
    public static Color edge() {
        // Light mode: a neutral grey, about 6:1 on the panel, so edges are clear but the white cards lead.
        return isDarkMode() ? EDGE_DARK : blend(Color.BLACK, panelBackground(), 0.38);
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
}
