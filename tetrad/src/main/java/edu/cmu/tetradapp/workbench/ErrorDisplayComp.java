///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.workbench;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * The display component for error nodes, which is a transparent ellipse.
 *
 * @author  Joseph Ramsmey
 */
public class ErrorDisplayComp extends JComponent
        implements DisplayComp {
    private boolean selected = false;

    public ErrorDisplayComp(String name) {
        setBackground(DisplayNodeUtils.getNodeFillColor());
        setFont(DisplayNodeUtils.getFont());
        setName(name);
    }

    public void setName(String name) {
        super.setName(name);
        setSize(getPreferredSize());
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean contains(int x, int y) {
        return getShape().contains(x, y);
    }

    /**
     * @return the shape of the component.
     */
    public Shape getShape() {
        return new Rectangle2D.Double(0, 0, getPreferredSize().width - 1,
                getPreferredSize().height - 1);
    }

    /**
     * Paints the component.
     */
    public void paint(Graphics g) {
        FontMetrics fm = getFontMetrics(DisplayNodeUtils.getFont());
        Dimension size = getPreferredSize();
        int stringWidth = fm.stringWidth(getName());
        int stringX = (size.width - stringWidth) / 2;
        int stringY = fm.getAscent() + (size.height - fm.getHeight()) / 2;

        g.setColor(DisplayNodeUtils.getNodeTextColor());
        g.drawString(getName(), stringX, stringY);
    }


    /**
     * Calculates the size of the component based on its name.
     */
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(DisplayNodeUtils.getFont());
        int width = fm.stringWidth(getName()) + fm.getMaxAdvance();
        int height = 2 * DisplayNodeUtils.getPixelGap() + fm.getAscent();

        return new Dimension(width, height);
    }

    public boolean isSelected() {
        return selected;
    }
}



