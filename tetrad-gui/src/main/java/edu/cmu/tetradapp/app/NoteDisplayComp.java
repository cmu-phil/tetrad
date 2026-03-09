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

package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Provides a modified appearance for session nodes to be used for notes.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NoteDisplayComp extends JComponent implements SessionDisplayComp {

    /**
     * The label that contains the name.
     */
    private final JLabel name = new JLabel("Note");

    /**
     * States whether the component is selected or not.
     */
    private boolean selected;

    /**
     * Constructs the Node display.
     */
    public NoteDisplayComp() {
        setOpaque(false);
        buildComponents();
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
        int bl = (int) Math.round((1.0 - t) * a.getBlue() + t * b.getBlue());
        return new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, bl))
        );
    }

    private static Color brighten(Color c, double amount) {
        return blend(c, Color.WHITE, amount);
    }

    private static Color darken(Color c, double amount) {
        return blend(c, Color.BLACK, amount);
    }

    private static Color getNoteFillColor() {
        if (isDarkMode()) {
            Color panel = uiColor("Panel.background", new Color(43, 43, 43));
            Color base = new Color(92, 86, 58); // muted warm note tone for dark mode
            return blend(panel, base, 0.70);
        }

        return new Color(255, 252, 210);
    }

    private static Color getRuledLineColor() {
        if (isDarkMode()) {
            return blend(getNoteFillColor(), Color.WHITE, 0.18);
        }

        return new Color(210, 214, 235);
    }

    private static Color getBorderColor() {
        Color c = uiColor("Component.borderColor", null);
        if (c != null) {
            return c;
        }

        c = uiColor("Separator.foreground", null);
        if (c != null) {
            return c;
        }

        if (isDarkMode()) {
            return new Color(140, 146, 168);
        }

        return new Color(148, 152, 177);
    }

    private static Color getSelectedBorderColor() {
        Color c = uiColor("Component.focusColor", null);
        if (c != null) {
            return c;
        }

        c = uiColor("Focus.color", null);
        if (c != null) {
            return c;
        }

        c = uiColor("Table.selectionBackground", null);
        if (c != null) {
            return c;
        }

        return isDarkMode() ? new Color(110, 160, 220) : DisplayNodeUtils.getNodeSelectedEdgeColor();
    }

    private static Color getTextColor() {
        return uiColor("Label.foreground", isDarkMode() ? new Color(235, 235, 235) : Color.BLACK);
    }

    private static Color getShadowColor() {
        return isDarkMode()
                ? new Color(0, 0, 0, 80)
                : new Color(0, 0, 0, 28);
    }

    private Shape getShape() {
        return new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
    }

    /**
     * Paints the component with the given Graphics context.
     *
     * @param g the Graphics context in which to paint
     */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            Shape shape = getShape();

            // subtle shadow
            g2.setColor(getShadowColor());
            g2.fillRoundRect(2, 3, Math.max(0, width - 4), Math.max(0, height - 4), 12, 12);

            // note body
            g2.setColor(getNoteFillColor());
            g2.fill(shape);

            // ruled lines, starting below the title area
            int topInset = Math.max(26, this.name.getPreferredSize().height + 10);
            int spacing = 8;
            g2.setColor(getRuledLineColor());

            for (int y = topInset; y < height - 8; y += spacing) {
                g2.drawLine(10, y, width - 10, y);
            }

            // top band, slightly differentiated
            Color fill = getNoteFillColor();
            Color header = isDarkMode() ? brighten(fill, 0.05) : darken(fill, 0.03);
            g2.setColor(header);
            g2.fillRoundRect(0, 0, width - 1, topInset, 12, 12);
            g2.fillRect(0, topInset - 8, width - 1, 8);

            // border
            g2.setColor(isSelected() ? getSelectedBorderColor() : getBorderColor());
            g2.draw(shape);
        } finally {
            g2.dispose();
        }

        super.paintComponent(g);
    }

    /**
     * Unused.
     *
     * @param acronym the acronym (e.g. "PC") for the node.
     */
    @Override
    public void setAcronym(String acronym) {
        // Ignore.
    }

    /**
     * States whether this comp is selected.
     *
     * @return true iff the display is selected.
     */
    public boolean isSelected() {
        return this.selected;
    }

    /**
     * @param selected a boolean
     */
    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    /**
     * Unused.
     *
     * @param b a boolean
     */
    @Override
    public void setHasModel(boolean b) {
        // Ignore.
    }

    /**
     * Sets the name of the node.
     *
     * @param name the name of the node.
     */
    @Override
    public void setName(String name) {
        super.setName(name);
        this.name.setText(name);
        revalidate();
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (this.name != null) {
            refreshTheme();
            buildComponents();
        }
    }

    private void refreshTheme() {
        Font baseFont = uiFont("Label.font", DisplayNodeUtils.getFont());
        setFont(baseFont);
        this.name.setFont(baseFont.deriveFont(Font.BOLD));
        this.name.setForeground(getTextColor());
        this.name.setOpaque(false);
    }

    private void buildComponents() {
        removeAll();
        setLayout(new BorderLayout());

        refreshTheme();

        Box b = Box.createVerticalBox();
        b.setOpaque(false);

        b.add(Box.createVerticalStrut(6));

        Box b2 = Box.createHorizontalBox();
        b2.setOpaque(false);
        b2.add(Box.createHorizontalStrut(10));
        b2.add(this.name);
        b2.add(Box.createHorizontalGlue());
        b2.add(Box.createHorizontalStrut(10));
        b.add(b2);

        b.add(Box.createVerticalStrut(68));

        add(b, BorderLayout.CENTER);

        revalidate();
        repaint();
    }
}