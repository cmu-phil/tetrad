package edu.cmu.tetradapp.workbench;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * The display component for error nodes, which is a transparent label-like component.
 *
 * @author Joseph Ramsmey
 * @version $Id: $Id
 */
public class ErrorDisplayComp extends JComponent implements DisplayComp {

    /**
     * True iff this display node is selected.
     */
    private boolean selected;

    /**
     * Constructor.
     *
     * @param name the node name
     */
    public ErrorDisplayComp(String name) {
        setOpaque(false);
        refreshTheme();
        setName(name);
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Font uiFont(String key, Font fallback) {
        Font f = UIManager.getFont(key);
        return f != null ? f : fallback;
    }

    private static Color getTextColor() {
        return uiColor("Label.foreground", DisplayNodeUtils.getNodeTextColor());
    }

    private static Color getSelectedTextColor() {
        Color c = UIManager.getColor("Table.selectionForeground");
        if (c != null) return c;

        c = UIManager.getColor("Component.focusColor");
        if (c != null) return c;

        return getTextColor();
    }

    private void refreshTheme() {
        setFont(uiFont("Label.font", DisplayNodeUtils.getFont()));
        setForeground(isSelected() ? getSelectedTextColor() : getTextColor());
    }

    @Override
    public void updateUI() {
        super.updateUI();
        refreshTheme();
        revalidate();
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        super.setName(name);
        setSize(getPreferredSize());
        revalidate();
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(int x, int y) {
        return getShape().contains(x, y);
    }

    /**
     * @return the shape of the component.
     */
    private Shape getShape() {
        Dimension d = getPreferredSize();
        return new Rectangle2D.Double(0, 0, d.width - 1, d.height - 1);
    }

    /**
     * {@inheritDoc}
     * Paints the component.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        refreshTheme();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Font font = getFont();
            FontMetrics fm = g2.getFontMetrics(font);
            Dimension size = getPreferredSize();

            String name = getName();
            if (name == null) name = "";

            int stringWidth = fm.stringWidth(name);
            int stringX = (size.width - stringWidth) / 2;
            int stringY = fm.getAscent() + (size.height - fm.getHeight()) / 2;

            g2.setFont(font);
            g2.setColor(getForeground());
            g2.drawString(name, stringX, stringY);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Calculates the size of the component based on its name.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        Font font = getFont() != null ? getFont() : DisplayNodeUtils.getFont();
        FontMetrics fm = getFontMetrics(font);

        String name = getName();
        if (name == null) name = "";

        int width = fm.stringWidth(name) + fm.getMaxAdvance();
        int height = 2 * DisplayNodeUtils.getPixelGap() + fm.getAscent();

        return new Dimension(width, height);
    }

    /**
     * @return true iff selected
     */
    public boolean isSelected() {
        return this.selected;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        refreshTheme();
        repaint();
    }
}