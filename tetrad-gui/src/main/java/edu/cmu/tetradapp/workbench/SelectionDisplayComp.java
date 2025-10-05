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
import java.awt.geom.Ellipse2D;

public class SelectionDisplayComp extends JComponent implements DisplayComp {

    private boolean selected;

    public SelectionDisplayComp(String name) {
        setBackground(DisplayNodeUtils.getNodeFillColor());
        setFont(DisplayNodeUtils.getFont());
        setName(name);
        this.setSize(getPreferredSize());
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        setSize(getPreferredSize());
    }

    @Override
    public boolean contains(int x, int y) {
        return getShape().contains(x, y);
    }

    private Shape getShape() {
        return new Ellipse2D.Double(0, 0, getPreferredSize().width - 1,
                getPreferredSize().height - 1);
    }

    private Shape getInnerShape() {
        return new Ellipse2D.Double(3, 3, getPreferredSize().width - 7,
                getPreferredSize().height - 7);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        FontMetrics fm = getFontMetrics(DisplayNodeUtils.getFont());
        int width = getPreferredSize().width;
        int stringWidth = fm.stringWidth(getName());
        int stringX = (width - stringWidth) / 2;
        int stringY = fm.getAscent() + DisplayNodeUtils.getPixelGap();

        // Fill outer shape
        g2.setColor(isSelected() ? DisplayNodeUtils.getNodeSelectedFillColor() : DisplayNodeUtils.getNodeFillColor());
        g2.fill(getShape());

        g2.setColor(isSelected() ? DisplayNodeUtils.getNodeSelectedEdgeColor() :
                DisplayNodeUtils.getNodeEdgeColor());
        g2.setStroke(new BasicStroke(5)); // Adjust thickness here
        g2.draw(getInnerShape());

        // Draw the text inside the shape
        g2.setColor(DisplayNodeUtils.getNodeTextColor());
        g2.setFont(DisplayNodeUtils.getFont());
        g2.drawString(getName(), stringX, stringY);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(DisplayNodeUtils.getFont());
        String name1 = getName();
        int textWidth = fm.stringWidth(name1);
        int textHeight = fm.getAscent();
        int width = textWidth + fm.getMaxAdvance() + 5;
        int height = 2 * DisplayNodeUtils.getPixelGap() + textHeight + 5;

        width = width;// (width < 60) ? 60 : width;

        return new Dimension(width, height);
    }

    private boolean isSelected() {
        return this.selected;
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}






