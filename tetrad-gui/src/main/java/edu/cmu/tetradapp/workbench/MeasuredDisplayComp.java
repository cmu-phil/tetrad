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
import java.awt.geom.RoundRectangle2D;

/**
 * The display component for measured nodes--an opaque rounded rectangle.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MeasuredDisplayComp extends JComponent implements DisplayComp {

    /**
     * True iff this display node is selected.
     */
    private boolean selected;

    /**
     * <p>Constructor for MeasuredDisplayComp.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public MeasuredDisplayComp(String name) {
        setBackground(DisplayNodeUtils.getNodeFillColor());
        setFont(DisplayNodeUtils.getFont());
        setName(name);
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        super.setName(name);
        setSize(getPreferredSize());
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(int x, int y) {
        return getShape().contains(x, y);
    }

    /**
     * @return the shape of the component.
     */
    private Shape getShape() {
        return new RoundRectangle2D.Double(0, 0, getPreferredSize().width - 1,
                getPreferredSize().height - 1, 4, 3);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Paints the component.
     */
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        FontMetrics fm = getFontMetrics(DisplayNodeUtils.getFont());
        int width = getPreferredSize().width;
        int stringWidth = fm.stringWidth(getName());
        int stringX = (width - stringWidth) / 2;
        int stringY = fm.getAscent() + DisplayNodeUtils.getPixelGap();

        g2.setColor(isSelected() ? DisplayNodeUtils.getNodeSelectedFillColor() :
                DisplayNodeUtils.getNodeFillColor());
        g2.fill(getShape());
        g2.setColor(isSelected() ? DisplayNodeUtils.getNodeSelectedEdgeColor() :
                DisplayNodeUtils.getNodeEdgeColor());
        g2.draw(getShape());
        g2.setColor(DisplayNodeUtils.getNodeTextColor());
        g2.setFont(DisplayNodeUtils.getFont());
        g2.drawString(getName(), stringX, stringY);
    }

    /**
     * Calculates the size of the component based on its name.
     *
     * @return a {@link java.awt.Dimension} object
     */
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(DisplayNodeUtils.getFont());
        int width = fm.stringWidth(getName()) + fm.getMaxAdvance();
        int height = 2 * DisplayNodeUtils.getPixelGap() + fm.getAscent() + 3;
        width = (width < 60) ? 60 : width;
        return new Dimension(width, height);
    }

    private boolean isSelected() {
        return this.selected;
    }

    /**
     * {@inheritDoc}
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}





