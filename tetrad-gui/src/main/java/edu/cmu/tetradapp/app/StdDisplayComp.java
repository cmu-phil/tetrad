package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.workbench.DisplayNodeUtils;
import edu.cmu.tetradapp.workbench.WorkbenchStyle;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;

/**
 * Appearance of standard session nodes, drawn as a compact card.
 * <p>
 * The card has a thin header band, tinted by node type, that names the type in small capitals, and a body that shows
 * the session name with the model acronym beneath it. A node with no model is drawn hollow with a dashed border, so
 * the "not yet filled in" state reads as an outline rather than a gray slab. Everything is vector-drawn from the
 * active Swing Look &amp; Feel colors, so the node scales correctly on HiDPI screens and follows light and dark mode
 * without any image assets.
 * <p>
 * The constructor still accepts the image path from the session configuration for compatibility, but the image is no
 * longer displayed.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StdDisplayComp extends JComponent implements SessionDisplayComp {

    /**
     * Corner radius of the card.
     */
    private static final int ARC = 12;

    /**
     * Height of the type band at the top of the card.
     */
    private static final int BAND_HEIGHT = 18;

    /**
     * Horizontal padding inside the card.
     */
    private static final int PAD_X = 14;

    /**
     * Vertical gap between the name and the acronym.
     */
    private static final int GAP_Y = 3;

    /**
     * Bottom padding inside the card.
     */
    private static final int PAD_BOTTOM = 10;

    /**
     * Minimum card width so short names still produce a sensible box.
     */
    private static final int MIN_WIDTH = 96;

    private static final Font FALLBACK_FONT = new Font("Dialog", Font.PLAIN, 12);

    private String name = " ";
    private String acronym = "No model";
    private String nodeType = "";

    private boolean hasModel;
    private boolean selected;

    /**
     * Constructs a session node card. The image path is accepted for configuration compatibility and ignored.
     *
     * @param imagePath the (unused) icon path from the session configuration.
     */
    public StdDisplayComp(String imagePath) {
        setOpaque(false);
        setFont(uiFont("Label.font", FALLBACK_FONT));
    }

    // ---------------------------------------------------------------- LAF helpers

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Font uiFont(String key, Font fallback) {
        Font f = UIManager.getFont(key);
        return f != null ? f : fallback;
    }

    private static boolean isDarkMode() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
    }

    private static Color blend(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round((1.0 - t) * a.getRed() + t * b.getRed());
        int g = (int) Math.round((1.0 - t) * a.getGreen() + t * b.getGreen());
        int bl = (int) Math.round((1.0 - t) * a.getBlue() + t * b.getBlue());
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    // ---------------------------------------------------------------- type hues

    /**
     * A saturated hue for the node type. This is never painted directly; it is blended toward the panel background
     * (dark mode) or toward white (light mode) for the band, and toward the label color for the band text.
     */
    private static Color typeHue(String type) {
        if (type == null) return WorkbenchStyle.KHAKI;
        switch (type) {
            case "Graph":
                return WorkbenchStyle.DUSTY_BLUE;
            case "PM":
            case "IM":
                return WorkbenchStyle.PLUM;
            case "Data":
            case "Simulation":
                return WorkbenchStyle.SAGE;
            case "Estimator":
                return WorkbenchStyle.TEAL;
            case "Search":
            case "Latent_Clusters":
            case "Latent_Structure":
            case "Regression":
            case "Updater":
                return WorkbenchStyle.ROSE;
            case "Knowledge":
                return blend(WorkbenchStyle.KHAKI, WorkbenchStyle.BROWN, 0.35);
            case "Compare":
            case "GridSearch":
                return WorkbenchStyle.TERRACOTTA;
            case "Note":
                return WorkbenchStyle.MUSTARD;
            default:
                return WorkbenchStyle.KHAKI;
        }
    }

    private static String typeLabel(String type) {
        if (type == null || type.isEmpty()) return "";
        return type.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- colors
    //
    // These are static and public so other parts of the session editor (the toolbar, for instance) can use the
    // same colors for the same node types. All of them read the current Look & Feel at call time, so they follow
    // light and dark mode automatically.

    /**
     * The panel background color of the current Look &amp; Feel.
     *
     * @return the panel background color.
     */
    public static Color panelBackground() {
        return WorkbenchStyle.panelBackground();
    }

    /**
     * The fill color of a session node card body.
     *
     * @return the card fill color.
     */
    public static Color cardFill() {
        // Session cards sit a step lighter than graph nodes so the type band and text stand out.
        Color base = WorkbenchStyle.cardFill();
        return isDarkMode() ? blend(base, Color.WHITE, 0.08) : blend(base, Color.WHITE, 0.65);
    }

    /**
     * The fill color of the type band for the given node type.
     *
     * @param type the node's button type, e.g. "Search".
     * @return the band fill color.
     */
    public static Color bandFill(String type) {
        Color hue = typeHue(type);
        if (isDarkMode()) {
            return blend(cardFill(), hue, 0.45);
        }
        return blend(cardFill(), hue, 0.26);
    }

    /**
     * The text color used on the type band for the given node type.
     *
     * @param type the node's button type, e.g. "Search".
     * @return the band text color.
     */
    public static Color bandText(String type) {
        Color hue = typeHue(type);
        if (isDarkMode()) {
            return blend(hue, Color.WHITE, 0.55);
        }
        return WorkbenchStyle.fade(blend(hue, Color.BLACK, 0.30));
    }

    /**
     * The border color of an unselected session node card.
     *
     * @return the border color.
     */
    public static Color cardBorder() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) c = UIManager.getColor("Separator.foreground");
        if (c == null) c = isDarkMode() ? new Color(100, 104, 110) : new Color(190, 194, 200);
        return c;
    }

    private Color getSelectedBorderColor() {
        return WorkbenchStyle.accent();
    }

    private Color getPrimaryText() {
        return WorkbenchStyle.nodeText();
    }

    private Color getSecondaryText() {
        Color fg = getPrimaryText();
        return isDarkMode() ? blend(fg, Color.GRAY, 0.35) : blend(fg, Color.WHITE, 0.40);
    }

    // ---------------------------------------------------------------- fonts

    private Font nameFont() {
        return getFont().deriveFont(Font.PLAIN);
    }

    private Font acronymFont() {
        Font f = getFont();
        return f.deriveFont(Font.BOLD, Math.max(9f, f.getSize2D() - 1f));
    }

    private Font bandFont() {
        Font f = getFont();
        return f.deriveFont(Font.BOLD, Math.max(8f, f.getSize2D() - 3f));
    }

    // ---------------------------------------------------------------- SessionDisplayComp

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        super.setName(name);
        this.name = name == null ? " " : name;
        resize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAcronym(String acronym) {
        this.acronym = acronym == null ? "" : acronym;
        resize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHasModel(boolean hasModel) {
        this.hasModel = hasModel;
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType == null ? "" : nodeType;
        resize();
    }

    // ---------------------------------------------------------------- geometry

    private Shape getShape() {
        return new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, ARC, ARC);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(int x, int y) {
        return getShape().contains(x, y);
    }

    private FontMetrics fm(Font f) {
        return getFontMetrics(f);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics nameFm = fm(nameFont());
        FontMetrics acrFm = fm(acronymFont());
        FontMetrics bandFm = fm(bandFont());

        int textW = Math.max(nameFm.stringWidth(name), acrFm.stringWidth(acronym));
        textW = Math.max(textW, bandFm.stringWidth(typeLabel(nodeType)));
        int w = Math.max(MIN_WIDTH, textW + 2 * PAD_X);

        int h = BAND_HEIGHT + 8 + nameFm.getHeight() + GAP_Y + acrFm.getHeight() + PAD_BOTTOM;
        return new Dimension(w, h);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    private void resize() {
        setSize(getPreferredSize());
        revalidate();
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateUI() {
        super.updateUI();
        setFont(uiFont("Label.font", FALLBACK_FONT));
        resize();
    }

    // ---------------------------------------------------------------- painting

    /**
     * {@inheritDoc}
     */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();
            Shape card = getShape();

            // Soft shadow, light mode only. In dark mode a shadow is invisible and just muddies the edge.
            if (!isDarkMode() && hasModel) {
                g2.setColor(new Color(0, 0, 0, 22));
                g2.fill(new RoundRectangle2D.Double(1.5, 2.5, w - 1, h - 1, ARC, ARC));
            }

            // Card body.
            g2.setColor(hasModel ? cardFill() : withAlpha(cardFill(), 140));
            g2.fill(card);

            // Type band: the top of the card, clipped to the rounded outline.
            Area band = new Area(card);
            band.intersect(new Area(new Rectangle2D.Double(0, 0, w, BAND_HEIGHT)));
            g2.setColor(hasModel ? bandFill(nodeType) : withAlpha(bandFill(nodeType), 160));
            g2.fill(band);

            // Band text.
            String label = typeLabel(nodeType);
            if (!label.isEmpty()) {
                g2.setFont(bandFont());
                FontMetrics bfm = g2.getFontMetrics();
                int tx = (w - bfm.stringWidth(label)) / 2;
                int ty = (BAND_HEIGHT - bfm.getHeight()) / 2 + bfm.getAscent();
                g2.setColor(bandText(nodeType));
                g2.drawString(label, tx, ty);
            }

            // Name.
            g2.setFont(nameFont());
            FontMetrics nfm = g2.getFontMetrics();
            int nameY = BAND_HEIGHT + 8 + nfm.getAscent();
            g2.setColor(getPrimaryText());
            g2.drawString(name, (w - nfm.stringWidth(name)) / 2, nameY);

            // Acronym.
            g2.setFont(acronymFont());
            FontMetrics afm = g2.getFontMetrics();
            int acrY = nameY + nfm.getDescent() + GAP_Y + afm.getAscent();
            g2.setColor(hasModel ? getSecondaryText() : blend(getSecondaryText(), panelBackground(), 0.35));
            g2.drawString(acronym, (w - afm.stringWidth(acronym)) / 2, acrY);

            // Border. Dashed when there is no model; heavier and accented when selected.
            if (selected) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(getSelectedBorderColor());
            } else if (hasModel) {
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(cardBorder());
            } else {
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
                        new float[]{4f, 3f}, 0f));
                g2.setColor(blend(cardBorder(), getPrimaryText(), 0.25));
            }
            g2.draw(card);
        } finally {
            g2.dispose();
        }
    }
}
