package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Appearance of session nodes for standard nodes.
 * <p>
 * Uses colors from the active Swing Look &amp; Feel when available,
 * with Tetrad defaults as fallbacks.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StdDisplayComp extends JComponent implements SessionDisplayComp {

    private static final Font SMALL_FONT = new Font("Dialog", Font.BOLD, 10);

    private final JLabel nameLabel;
    private final JLabel acronymLabel;
    private final String imagePath;
    private final JLabel iconLabel;

    private boolean hasModel;
    private boolean selected;

    public StdDisplayComp(String imagePath) {
        this.nameLabel = new JLabel(" ");
        this.acronymLabel = new JLabel("No model");
        this.iconLabel = new JLabel();
        this.imagePath = imagePath;

        setOpaque(false);
        nameLabel.setOpaque(false);
        acronymLabel.setOpaque(false);
        iconLabel.setOpaque(false);

        layoutComponents();
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Font uiFont(String key, Font fallback) {
        Font f = UIManager.getFont(key);
        return f != null ? f : fallback;
    }

    private static boolean isDarkMode() {
        LookAndFeel laf = UIManager.getLookAndFeel();
        return laf != null && laf.getName().toLowerCase().contains("dar");
    }

    private static Color blend(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round((1.0 - t) * a.getRed() + t * b.getRed());
        int g = (int) Math.round((1.0 - t) * a.getGreen() + t * b.getGreen());
        int b2 = (int) Math.round((1.0 - t) * a.getBlue() + t * b.getBlue());
        return new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b2))
        );
    }

    private static Color brighten(Color c, double amount) {
        return blend(c, Color.WHITE, amount);
    }

    private static Color darken(Color c, double amount) {
        return blend(c, Color.BLACK, amount);
    }

    /**
     * Unselected node fill when there is a model.
     */
    private static Color getHasModelFillColor() {
        if (isDarkMode()) {
            Color panel = uiColor("Panel.background", new Color(60, 63, 65));
            Color button = uiColor("Button.background", panel);
            return brighten(blend(panel, button, 0.5), 0.01);
        }

        //        Color button = UIManager.getColor("Button.background");
        //        if (button != null) {
        //            return blend(button, new Color(26, 113, 169, 255), 0.10);
        //            // In light mode, move AWAY from the background so nodes stand out more.
        ////            return brighten(button, 0.05);
        //        }

        //        Color panel = UIManager.getColor("Panel.background");
        //        if (panel != null) {
        //            return blend(panel, DisplayNodeUtils.getNodeFillColor(), 0.10);// new Color(26, 113, 169, 255), 0.10);
        ////            return brighten(panel, 0.10);
        //        }

        return DisplayNodeUtils.getNodeFillColor();
    }

    /**
     * Unselected node fill when there is no model.
     */
    private static Color getNoModelFillColor() {
        Color base = getHasModelFillColor();

        if (isDarkMode()) {
            // Make "no model" clearly dimmer and slightly grayer in dark mode.
            return darken(blend(base, Color.BLACK, 0.25), 0.18);
        }

        // In light mode, keep it muted but still clearly visible.
        return Color.LIGHT_GRAY;// darken(Color.LIGHT_GRAY, 0.20);
    }

    /**
     * Selected node fill.
     */
    private static Color getSelectedFillColor() {
        if (isDarkMode()) {
            return uiColor("Table.selectionBackground", DisplayNodeUtils.getNodeSelectedFillColor());
        }

        return DisplayNodeUtils.getNodeSelectedFillColor();
    }

    private static Color getEdgeColor() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c != null) {
            return isDarkMode() ? c : darken(c, 0.10);
        }

        c = UIManager.getColor("Separator.foreground");
        if (c != null) {
            return isDarkMode() ? c : darken(c, 0.10);
        }

        c = UIManager.getColor("Label.foreground");
        if (c != null) {
            return isDarkMode() ? blend(c, Color.GRAY, 0.35) : blend(c, Color.BLACK, 0.35);
        }

        return DisplayNodeUtils.getNodeEdgeColor();
    }

    private static Color getSelectedEdgeColor() {
        Color c = UIManager.getColor("Component.focusColor");
        if (c != null) return c;

        c = UIManager.getColor("Focus.color");
        if (c != null) return c;

        c = UIManager.getColor("Table.selectionBackground");
        if (c != null) return c;

        return DisplayNodeUtils.getNodeSelectedEdgeColor();
    }

    private static Color getPrimaryTextColor() {
        return uiColor("Label.foreground", isDarkMode() ? new Color(230, 230, 230) : Color.BLACK);
    }

    private static Color getSecondaryTextColor() {
        Color fg = getPrimaryTextColor();
        return isDarkMode() ? blend(fg, Color.GRAY, 0.30) : blend(fg, Color.WHITE, 0.20);
    }

    private static Color darker(Color c, double factor) {
        int r = (int) (c.getRed() * (1 - factor));
        int g = (int) (c.getGreen() * (1 - factor));
        int b = (int) (c.getBlue() * (1 - factor));
        return new Color(Math.max(r, 0), Math.max(g, 0), Math.max(b, 0));
    }

    private static Color lighter(Color c, double factor) {
        int r = (int) (c.getRed() + (255 - c.getRed()) * factor);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * factor);
        int b = (int) (c.getBlue() + (255 - c.getBlue()) * factor);
        return new Color(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    private Color getUnselectedFillColor() {
        return hasModel ? getHasModelFillColor() : getNoModelFillColor();
    }

    private boolean isSelected() {
        return this.selected;
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        this.nameLabel.setText(name);
    }

    @Override
    public void setAcronym(String acronym) {
        this.acronymLabel.setText(acronym);
        layoutComponents();
    }

    @Override
    public void setHasModel(boolean hasModel) {
        this.hasModel = hasModel;
        refreshTheme();
        repaint();
    }

    private Shape getShape() {
        return new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);
        //        return new Rectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1);
    }

    @Override
    public boolean contains(int x, int y) {
        return getShape().contains(x, y);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        refreshTheme();
        refreshIcon();
        layoutComponents();
    }

    private void refreshTheme() {
        Font baseFont = uiFont("Label.font", DisplayNodeUtils.getFont());
        setFont(baseFont);

        nameLabel.setForeground(getPrimaryTextColor());
        nameLabel.setFont(baseFont);

        acronymLabel.setForeground(getSecondaryTextColor());
        acronymLabel.setFont(SMALL_FONT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Shape shape = getShape();

            g2.setColor(isSelected() ? getSelectedFillColor() : getUnselectedFillColor());
            g2.fill(shape);

            if (isDarkMode()) {
                g2.setColor(lighter(isSelected() ? getSelectedEdgeColor() : getEdgeColor(), 0.25));
            } else {
                g2.setColor(darker(isSelected() ? getSelectedEdgeColor() : getEdgeColor(), .25));
            }
            g2.draw(shape);
        } finally {
            g2.dispose();
        }
    }

    private void layoutComponents() {
        removeAll();
        setLayout(new BorderLayout());

        refreshTheme();

        Box b = Box.createVerticalBox();
        b.setOpaque(false);

        //        if (isDarkMode()) {
        //            b.add(Box.createRigidArea(new Dimension(50, 10)));f
        //        }

        //        if (!isDarkMode()) {
        refreshIcon();

        Box b1 = Box.createHorizontalBox();
        b1.setOpaque(false);
        b1.add(Box.createHorizontalGlue());
        b1.add(this.iconLabel);
        b1.add(Box.createHorizontalGlue());
        b.add(b1);

        //        } else {
        //            b.add(Box.createRigidArea(new Dimension(60, 6)));
        //        }

        Box b2 = Box.createHorizontalBox();
        b2.setOpaque(false);
        b2.add(Box.createHorizontalGlue());
        b2.add(Box.createHorizontalStrut(6));
        b2.add(this.nameLabel);
        b2.add(Box.createHorizontalStrut(6));
        b2.add(Box.createHorizontalGlue());
        b.add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.setOpaque(false);
        b3.add(Box.createHorizontalGlue());
        b3.add(Box.createHorizontalStrut(6));
        b3.add(this.acronymLabel);
        b3.add(Box.createHorizontalStrut(6));
        b3.add(Box.createHorizontalGlue());
        b.add(b3);

        //        if (isDarkMode()) {
        //            b.add(Box.createRigidArea(new Dimension(60, 10)));
        //        } else {
        b.add(Box.createRigidArea(new Dimension(60, 4)));
        //        }

        add(b, BorderLayout.CENTER);

        setSize(getPreferredSize());

        revalidate();
        repaint();
    }

    private void refreshIcon() {
        Image image = ImageUtils.getImage(this, imagePath);
        iconLabel.setIcon(new ImageIcon(image));
    }
}