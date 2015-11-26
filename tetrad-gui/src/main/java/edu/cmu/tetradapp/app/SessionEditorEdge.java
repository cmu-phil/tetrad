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

package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.PointPair;

import java.awt.*;

/**
 * Presents an edge in the Tetrad SessionWorkbench.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class SessionEditorEdge extends DisplayEdge {

    /* Colors */
    private static final Color DIE_BACKGROUND = Color.red;
    private static final Color DIE_DOT = Color.black;

    /* Modes */
    public static final int UNRANDOMIZED = 0;
    private static final int RANDOMIZED = 1;

    /* States */
    private final Color curr_color = DIE_BACKGROUND;
    private int sessionEdgeMode = 0;

    /**
     * Constructs a new SessionEditorEdge connecting two components, 'node1' and
     * 'node2'.  The anchor component will be node1.
     *
     * @param node1           the 'from' component.
     * @param node2           the 'to' component.
     * @param sessionEdgeMode the sessionEdgeMode of the edge, either
     *                        UNRANDOMIZED or RANDOMIZED.
     */
    public SessionEditorEdge(SessionEditorNode node1, SessionEditorNode node2,
            int sessionEdgeMode) {

        super(node1, node2, DisplayEdge.DIRECTED);

        if ((sessionEdgeMode >= 0) && (sessionEdgeMode <= 1)) {
            this.sessionEdgeMode = sessionEdgeMode;
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Constructs a new unanchored session edge.  The end of the edge at 'node1'
     * is anchored, but the other end tracks a mouse point. The mouse point
     * should be updated by the parent component using repeated calls to
     * 'updateTrackPoint'; this process is finished by finally anchoring the
     * second end of the of edge using 'anchorSecondEnd'.  Once this is done,
     * the edge is considered anchored and will not be able to track a mouse
     * point any longer.
     *
     * @param node1           the 'from' component.
     * @param mouseTrackPoint the initial value of the mouse track point.
     * @see #updateTrackPoint
     */
    public SessionEditorEdge(SessionEditorNode node1, Point mouseTrackPoint) {
        super(node1, mouseTrackPoint, DisplayEdge.DIRECTED);
    }

    /**
     * Constructs a new unanchored session edge.  The end of the edge a 'node1'
     * is anchored, but the other end tracks a mouse point. The mouse point
     * should be updated by the parent component using repeated calls to
     * 'updateTrackPoint'; this process is finished by finally anchoring the
     * second end of the of edge using 'anchorSecondEnd'.  Once this is done,
     * the edge is considered anchored and will not be able to track a mouse
     * point any longer.
     *
     * @param node1           the 'from' component.
     * @param mouseTrackPoint the initial value of the mouse track point.
     * @param mode            ??
     * @see #updateTrackPoint
     */
    public SessionEditorEdge(SessionEditorNode node1, Point mouseTrackPoint,
            int mode) {
        super(node1, mouseTrackPoint, DisplayEdge.DIRECTED);
        this.sessionEdgeMode = mode;
    }

    /**
     * Calculates the sleeve of the die.
     *
     * @param dice the four points defining the die.
     * @return the sleeve
     */
    private static Polygon calcDiceSleeve(Point[] dice) {

        int[] xpoint = new int[4];
        int[] ypoint = new int[4];

        xpoint[0] = dice[0].x;
        xpoint[1] = dice[1].x;
        xpoint[2] = dice[2].x;
        xpoint[3] = dice[3].x;
        ypoint[0] = dice[0].y;
        ypoint[1] = dice[1].y;
        ypoint[2] = dice[2].y;
        ypoint[3] = dice[3].y;

        return new Polygon(xpoint, ypoint, 4);
    }

    private void drawDice(Graphics g, boolean erase, Color c) {

        Polygon dice = getDiceSleeve();

        if (erase) {
            g.setColor(Color.white);
            g.fillPolygon(dice);
        }
        else {
            Circle[] dicedot = getDiceDot();

            g.setColor(c);
            g.fillPolygon(dice);
            g.setColor(DIE_DOT);
            g.drawPolygon(dice);

            int height = dicedot[0].radius * 2;

            for (Circle aDicedot : dicedot) {
                g.fillOval(aDicedot.center.x, aDicedot.center.y, height,
                        height);
            }
        }
    }

    /**
     * Calculates the four corners of the die
     *
     * @return this array of points.
     */
    private Point[] getDiceArea() {

        int[] xpoint = new int[4];
        int[] ypoint = new int[4];
        PointPair pp = getConnectedPoints();
        Point midPoint = new Point((pp.getFrom().x + pp.getTo().x) / 2,
                (pp.getFrom().y + pp.getTo().y) / 2);
        double d = distance(pp.getFrom(), pp.getTo());

        if (d < 1) {
            d = 1;
        }

        double sin = (pp.getFrom().y - pp.getTo().y) / d;
        double cos = (pp.getFrom().x - pp.getTo().x) / d;

        xpoint[0] = (int) (midPoint.x - 10 * cos);
        xpoint[1] = (int) (midPoint.x - 10 * sin);
        xpoint[2] = (int) (midPoint.x + 10 * cos);
        xpoint[3] = (int) (midPoint.x + 10 * sin);
        ypoint[0] = (int) (midPoint.y + 10 * sin);
        ypoint[1] = (int) (midPoint.y - 10 * cos);
        ypoint[2] = (int) (midPoint.y - 10 * sin);
        ypoint[3] = (int) (midPoint.y + 10 * cos);

        Point[] dice = new Point[4];

        dice[0] = new Point(xpoint[0], ypoint[0]);
        dice[1] = new Point(xpoint[1], ypoint[1]);
        dice[2] = new Point(xpoint[2], ypoint[2]);
        dice[3] = new Point(xpoint[3], ypoint[3]);

        return dice;
    }

    private Circle[] getDiceDot() {
        PointPair pp = getConnectedPoints();
        Point midPoint = new Point((pp.getFrom().x + pp.getTo().x) / 2,
                (pp.getFrom().y + pp.getTo().y) / 2);
        Point[] dice = getDiceArea();
        Circle[] dot = new Circle[5];

        dot[0] = new Circle(new Point(midPoint.x - 1, midPoint.y - 1), 2);
        dot[1] = new Circle(new Point((dice[0].x + midPoint.x) / 2 - 1,
                (dice[0].y + midPoint.y) / 2 - 1), 2);
        dot[2] = new Circle(new Point((dice[1].x + midPoint.x) / 2 - 1,
                (dice[1].y + midPoint.y) / 2 - 1), 2);
        dot[3] = new Circle(new Point((dice[2].x + midPoint.x) / 2 - 1,
                (dice[2].y + midPoint.y) / 2 - 1), 2);
        dot[4] = new Circle(new Point((dice[3].x + midPoint.x) / 2 - 1,
                (dice[3].y + midPoint.y) / 2 - 1), 2);

        return dot;
    }

    private Polygon getDiceSleeve() {
        return calcDiceSleeve(getDiceArea());
    }

    /**
     * @return the mode of this edge, RANDOMIZED or UNRANDOMIZED.
     */
    public int getSessionEdgeMode() {
        return sessionEdgeMode;
    }

    /**
     * Determines whether this display edge represents a randomized edge or an
     * unrandomized edge.  (Randomized edges are displayed with a little die on
     * them.)
     *
     * @return true if this edge represents a randomized edge, false if not.
     */
    public boolean isRandomized() {

        if (sessionEdgeMode == RANDOMIZED) {
            return true;
        }
        else if (sessionEdgeMode == UNRANDOMIZED) {
            return false;
        }
        else {
            throw new IllegalStateException();
        }
    }

    /**
     * This method paints the component.
     *
     * @param g the graphics context.
     */
    public void paint(Graphics g) {

        // NOTE:  For this component, the resetBounds() methods should ALWAYS
        // be called before repaint().
        PointPair pp;

        switch (getMode()) {
            case HALF_ANCHORED:
                g.setColor(getLineColor());
                pp = calculateEdge(getNode1(), getRelativeMouseTrackPoint());

                if (pp != null) {
                    pp.getFrom().translate(-getLocation().x, -getLocation().y);
                    pp.getTo().translate(-getLocation().x, -getLocation().y);

                    setClickRegion(null);

                    g.drawLine(pp.getFrom().x, pp.getFrom().y, pp.getTo().x,
                            pp.getTo().y);
                    drawEndpoints(pp, g);
                    firePropertyChange("newPointPair", null, pp);
                }
                break;

            case ANCHORED_UNSELECTED:
                g.setColor(getLineColor());

                pp = calculateEdge(getNode1(), getNode2());

                if (pp != null) {
                    pp.getFrom().translate(-getLocation().x, -getLocation().y);
                    pp.getTo().translate(-getLocation().x, -getLocation().y);

                    setClickRegion(null);

                    g.drawLine(pp.getFrom().x, pp.getFrom().y, pp.getTo().x,
                            pp.getTo().y);
                    drawEndpoints(pp, g);
                    firePropertyChange("newPointPair", null, pp);
                }
                break;

            case ANCHORED_SELECTED:
                g.setColor(getSelectedColor());

                pp = calculateEdge(getNode1(), getNode2());

                if (pp != null) {
                    pp.getFrom().translate(-getLocation().x, -getLocation().y);
                    pp.getTo().translate(-getLocation().x, -getLocation().y);

                    setClickRegion(null);

                    g.drawLine(pp.getFrom().x, pp.getFrom().y, pp.getTo().x,
                            pp.getTo().y);
                    drawEndpoints(pp, g);
                    firePropertyChange("newPointPair", null, pp);
                }
                break;

            default :
                throw new IllegalStateException();
        }

        setConnectedPoints(pp);

        if (sessionEdgeMode == RANDOMIZED) {
            drawDice(g, false, curr_color);
        }
    }

    /**
     * Holds the radius and diameter of a circle.
     *
     * @author Pucktada
     */
    private static final class Circle {
        public final int radius;
        public final Point center;

        /**
         * @param c the center of the circle.
         * @param r the radius of the circle.
         */
        public Circle(Point c, int r) {
            radius = r;
            center = c;
        }
    }
}





