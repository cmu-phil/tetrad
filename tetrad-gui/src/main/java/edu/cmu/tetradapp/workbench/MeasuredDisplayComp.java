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
 * The display component for a measured-variable node in a graph workbench: a rounded rectangle in a light blue tint
 * with the variable name centered in it. Colors and font come from {@link WorkbenchStyle}, so the node follows the
 * active Look &amp; Feel.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MeasuredDisplayComp extends JComponent implements DisplayComp {

    private static final int ARC = 8;

    private boolean selected;

    /**
     * Constructs a measured node display with the given name.
     *
     * @param name the node name.
     */
    public MeasuredDisplayComp(String name) {
        setOpaque(false);
        setFont(WorkbenchStyle.nodeFont());
        setName(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        super.setName(name);
        setSize(getPreferredSize());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(int x, int y) {
        return getShape().contains(x, y);
    }

    private Shape getShape() {
        Dimension d = getPreferredSize();
        return new RoundRectangle2D.Double(0.5, 0.5, d.width - 1, d.height - 1, ARC, ARC);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            WorkbenchStyle.applyHints(g2);
            DisplayNodeUtils.paintNode(g2, getShape(), getName(), getPreferredSize(),
                    WorkbenchStyle.measuredFill(), isSelected());
        } finally {
            g2.dispose();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(WorkbenchStyle.nodeFont());
        int width = fm.stringWidth(getName()) + fm.getMaxAdvance();
        int height = 2 * DisplayNodeUtils.getPixelGap() + fm.getAscent() + 3;
        width = Math.max(width, 60);
        return new Dimension(width, height);
    }

    private boolean isSelected() {
        return this.selected;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
