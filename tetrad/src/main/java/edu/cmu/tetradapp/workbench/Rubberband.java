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
import java.awt.geom.RoundRectangle2D;

/**
 * Instantiates a rubberband suitable for selections.
 *
 * @author Joseph Ramsey
 */
public class Rubberband extends JComponent {

    /**
     * The shape itself, whose outline makes the rubberband.
     */
    private Shape shape = new RoundRectangle2D.Double();

    /**
     * The dashed stoke that forms the outline of the rubberband.
     */
    private BasicStroke stroke;

    /**
     * The anchor point.
     */
    private Point anchor;

    /**
     * Constructs a new rubberband at a specified location.
     *
     * @param anchor The anchor point of the rubberband; the rubber band will
     *               always go from this point to some other point.
     */
    public Rubberband(Point anchor) {

        if (anchor == null) {
            throw new NullPointerException("Anchor point must not be null.");
        }

        // Set the anchor.
        this.anchor = anchor;

        // set up stroke
        int width = 1;
        int cap = BasicStroke.CAP_ROUND;
        int join = BasicStroke.JOIN_BEVEL;
        int miterlimit = 0;
        float[] dash = new float[]{2, 2, 4, 2};
        float dashphase = 0.0f;

        stroke = new BasicStroke(width, cap, join, miterlimit, dash, dashphase);

        // initial location of rubberband
        setLocation(anchor);
        setSize(0, 0);
    }

    /**
     * Updates the track point for the rubber band so that it extends from the
     * initial location point to the point specified as the argument.
     */
    public void updateTrackPoint(Point p) {
        int newLocX = Math.min(p.x, this.anchor.x);
        int newLocY = Math.min(p.y, this.anchor.y);
        int deltaX = Math.abs(p.x - this.anchor.x);
        int deltaY = Math.abs(p.y - this.anchor.y);
        setLocation(newLocX, newLocY);
        setSize(deltaX, deltaY);
    }

    /**
     * Paints the rubberband.
     */
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        resetShapeBounds();
        g2.setColor(Color.black);
        g2.setStroke(stroke);
        g2.draw(shape);
    }

    /**
     * Rests the boundaries of the shape which is drawn as the rubberband. The
     * boundaries of the shape need to be set so that when drawn they lie just
     * inside the boundaries of the component on all four sides.
     */
    private void resetShapeBounds() {
        RoundRectangle2D.Double rrect = (RoundRectangle2D.Double) shape;
        rrect.setRoundRect(0, 0, getBounds().width - 1, getBounds().height - 1,
                10, 10);
    }

    /**
     * @return the shape of the rubberband, which can be used to determine which
     * components it intersects.
     */
    public Shape getShape() {
        return shape;
    }
}





