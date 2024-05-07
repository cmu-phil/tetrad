///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * This component has three modes: <ul> <li> UNANCHORED <li> NORMAL <li> SELECTED </ul> In the unanchored mode, it
 * displays an edge in the the workbench, one end of which is anchored to a workbench node and the other end of which
 * tracks a mouse point.  The edge in this mode is useful for constructing new edges in the workbench.  In the normal
 * and selected modes, both ends are anchored to workbench nodes, and the edge will track these workbench nodes if they
 * are moved on the workbench.  The difference between the normal and selected modes is that they display the edge in
 * different colors and when queried they respond differently as to whether the edge is selected.  <p> The intended use
 * for this workbench edge is as follows.  When an edge on the screen is first being created, an instance of this
 * workbench edge is created anchored on one end to a workbench node.  As the mouse is dragged, updates to its position
 * are fed to the updateTrackPoint() method. When the mouse is released, the tracking edge is removed from the workbench
 * and replaced with a new workbench edge which is anchored to two nodes--(1) the original node from the tracking edge
 * and (2) the node which is nearest to the mouse release position.
 *
 * @author josephramsey
 * @author Willie Wheeler
 * @version $Id: $Id
 */
public class DisplayEdge extends JComponent implements IDisplayEdge {

    /**
     * Represents the fact that this is a directed edge, A--&gt;B.
     */
    public static final int DIRECTED = 0;
    /**
     * Represents the fact that this is an nondirected edge, Ao-oB
     */
    public static final int NONDIRECTED = 1;
    /**
     * Represents the fact that this is an undirected edge, A---B
     */
    public static final int UNDIRECTED = 2;
    /**
     * Represents the fact that this is a partially directed edge, Ao-&gt;B.
     */
    public static final int PARTIALLY_ORIENTED = 3;
    /**
     * Represents the fact that this is a bidirected edge, A&lt;-&gt;B.
     */
    public static final int BIDIRECTED = 4;
    /**
     * Represents a session edge
     */
    public static final int SESSION = 5;
    /**
     * Indicates that one end of the edge is anchored to one component, but the other half is tracking the mouse point.
     */
    protected static final int HALF_ANCHORED = 0;
    /**
     * Indicates that both ends of the edge are anchored to components.  The edge will move when those components move.
     */
    protected static final int ANCHORED_UNSELECTED = 1;
    /**
     * Indicates that the edge is under construction, which is similar to the normal mode except that it's displayed as
     * selected and will identify itself as selected upon request (for possible deletion, e.g.).
     */
    protected static final int ANCHORED_SELECTED = 2;
    /**
     * The node that this edge is linked "from."
     */
    private final DisplayNode node1;
    /**
     * Handler for <code>ComponentEvent</code>s.
     */
    private final ComponentHandler compHandler = new ComponentHandler();
    /**
     * Handler for <code>PropertyChange</code>s.
     */
    private final PropertyChangeHandler propertyChangeHandler =
            new PropertyChangeHandler();
    /**
     * The model edge that this display is is portraying.
     */
    private Edge modelEdge;
    /**
     * The getModel mode of the edge--HALF_ANCHORED, ANCHORED_UNSELECTED, or ANCHORED_SELECTED.
     */
    private int mode;
    /**
     * The type of the edge, one of DIRECTED, NONDIRECTED, UNDIRECTED, PARTIALLY_ORIENTED, BIDIRECTED.
     */
    private int type;
    /**
     * The node that this edge is linked "to."
     */
    private DisplayNode node2;
    /**
     * For HALF_ANCHORed edges, this is the mouse point they connect to.
     */
    private Point mouseTrackPoint = new Point();
    /**
     * This is the same mouse point, but relative to *this* edge component.
     */
    private Point relativeMouseTrackPoint = new Point();
    /**
     * If the user clicks in this region, the edge will select.
     */
    private Polygon clickRegion;
    /**
     * True iff only adacencies (and no endpoints) should be shown.
     */
    private boolean showAdjacenciesOnly;
    /**
     * The offset of this edge for multiple edges between node pairs.
     */
    private double offset;
    /**
     * The pair of points that this edge connects, from the edge of one component to the edge of the other.
     */
    private PointPair connectedPoints;

    /**
     * The color of the line.
     */
    private Color lineColor = new Color(26, 113, 169, 255);// DisplayNodeUtils.getNodeFillColor().darker().darker();

    /**
     * The color of the line when selected.
     */
    private Color selectedColor = new Color(244, 0, 20);//DisplayNodeUtils.getNodeSelectedFillColor();   // this one

    /**
     * The color of the line when highlighted.
     */
    private Color highlightedColor = new Color(238, 180, 34);

    /**
     * The width of the stroke.
     */
    private float strokeWidth = 1.2f;

    /**
     * True iff this edge is highlighted.
     */
    private boolean highlighted;

    /**
     * True iff this edge is solid.
     */
    private boolean solid = true;

    /**
     * True iff this edge is thick.
     */
    private boolean thick = false;

    //==========================CONSTRUCTORS============================//

    /**
     * Constructs a new DisplayEdge connecting two components, 'node1' and 'node2', assuming that a reference to the
     * model edge will not be needed.
     *
     * @param node1 the 'from' component.
     * @param node2 the 'to' component.
     * @param type  the type of the edge, either UNRANDOMIZED or RANDOMIZED.
     */
    protected DisplayEdge(DisplayNode node1, DisplayNode node2, int type) {
        this(node1, node2, type, null);
    }

    /**
     * <p>Constructor for DisplayEdge.</p>
     *
     * @param node1 a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param node2 a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param type  a int
     * @param color a {@link java.awt.Color} object
     */
    protected DisplayEdge(DisplayNode node1, DisplayNode node2, int type, Color color) {
        if (node1 == null) {
            throw new NullPointerException("Node1 must not be null.");
        }

        if (node2 == null) {
            throw new NullPointerException("Node2 must not be null.");
        }

        if (type < 0 || type > 5) {
            throw new IllegalArgumentException("Type must be one of DIRECTED, NONDIRECTED, UNDIRECTED, PARTIALLY_ORIENTED, or BIDIRECTED.");
        }

        this.node1 = node1;
        this.node2 = node2;
        this.type = type;

        if (color != null) {
            this.lineColor = color;
        }

        this.mode = DisplayEdge.ANCHORED_UNSELECTED;

        node1.addComponentListener(this.compHandler);
        node2.addComponentListener(this.compHandler);

        node1.addPropertyChangeListener(this.propertyChangeHandler);
        node2.addPropertyChangeListener(this.propertyChangeHandler);

        resetBounds();
    }

    /**
     * Constructs a new DisplayEdge connecting two components, 'node1' and 'node2', assuming that a reference to the
     * model edge will be needed.
     *
     * @param node1     the 'from' component.
     * @param node2     the 'to' component.
     * @param modelEdge a {@link edu.cmu.tetrad.graph.Edge} object
     */
    public DisplayEdge(Edge modelEdge, DisplayNode node1, DisplayNode node2) {
        this(modelEdge, node1, node2, null);
    }

    /**
     * <p>Constructor for DisplayEdge.</p>
     *
     * @param modelEdge a {@link edu.cmu.tetrad.graph.Edge} object
     * @param node1     a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param node2     a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param color     a {@link java.awt.Color} object
     */
    public DisplayEdge(Edge modelEdge, DisplayNode node1, DisplayNode node2, Color color) {

        if (modelEdge == null) {
            throw new NullPointerException("Model edge must not be null.");
        }

        if (node1 == null) {
            throw new NullPointerException("Node1 must not be null.");
        }

        if (node2 == null) {
            throw new NullPointerException("Node2 must not be null.");
        }

        this.modelEdge = modelEdge;
        this.node1 = node1;
        this.node2 = node2;

        if (color != null) {
            this.lineColor = color;
        }
        this.mode = DisplayEdge.ANCHORED_UNSELECTED;

        node1.addComponentListener(this.compHandler);
        node2.addComponentListener(this.compHandler);

        node1.addPropertyChangeListener(this.propertyChangeHandler);
        node2.addPropertyChangeListener(this.propertyChangeHandler);

        resetBounds();
    }

    /**
     * Constructs a new unanchored session edge.  The end of the edge at 'node1' isO anchored, but the other end tracks
     * a mouse point. The mouse point should be updated by the parent component using repeated calls to
     * 'updateTrackPoint'; this process is finished by finally anchoring the second end of the of edge using
     * 'anchorSecondEnd'.  Once this is done, the edge is considered anchored and will not be able to track a mouse
     * point any longer.
     *
     * @param node1           the 'from' component.
     * @param mouseTrackPoint the initial value of the mouse track point.
     * @param type            a int
     * @see #updateTrackPoint
     */
    public DisplayEdge(DisplayNode node1, Point mouseTrackPoint, int type) {
        this(node1, mouseTrackPoint, type, null);
    }

    /**
     * <p>Constructor for DisplayEdge.</p>
     *
     * @param node1           a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param mouseTrackPoint a {@link java.awt.Point} object
     * @param type            a int
     * @param color           a {@link java.awt.Color} object
     */
    public DisplayEdge(DisplayNode node1, Point mouseTrackPoint, int type, Color color) {

        if (node1 == null) {
            throw new NullPointerException("Node1 must not be null.");
        }

        if (mouseTrackPoint == null) {
            throw new NullPointerException(
                    "Mouse track point must not " + "be null.");
        }

        if (type < 0 || type > 5) {
            throw new IllegalArgumentException("Type must be one of DIRECTED, NONDIRECTED, UNDIRECTED, PARTIALLY_ORIENTED, or BIDIRECTED.");
        }

        this.node1 = node1;
        this.mouseTrackPoint = mouseTrackPoint;
        this.type = type;

        if (color != null) {
            this.lineColor = color;
        }

        this.mode = DisplayEdge.HALF_ANCHORED;

        resetBounds();
    }

    /**
     * Calculates the distance between a pair of points.
     *
     * @param p1 a {@link java.awt.Point} object
     * @param p2 a {@link java.awt.Point} object
     * @return a double
     */
    protected static double distance(Point p1, Point p2) {
        double d;

        d = (p1.x - p2.x) * (p1.x - p2.x);
        d += (p1.y - p2.y) * (p1.y - p2.y);
        d = FastMath.sqrt(d);

        return d;
    }

    /**
     * Calculates the degenerate horizontal sleeve in the case where the point pair is near horizontal.
     *
     * @param pp        the given point pair.
     * @param halfWidth the half-width of the sleeve.
     * @return the sleeve as a polygon.
     */
    private static Polygon getHorizSleeve(PointPair pp, int halfWidth) {
        int[] xpoints = new int[4];
        int[] ypoints = new int[4];

        xpoints[0] = pp.getFrom().x;
        xpoints[1] = pp.getFrom().x;
        xpoints[2] = pp.getTo().x;
        xpoints[3] = pp.getTo().x;
        ypoints[0] = pp.getFrom().y + halfWidth;
        ypoints[1] = pp.getFrom().y - halfWidth;
        ypoints[2] = pp.getTo().y - halfWidth;
        ypoints[3] = pp.getTo().y + halfWidth;

        return new Polygon(xpoints, ypoints, 4);
    }

    //============================PUBLIC METHODS========================//

    /**
     * {@inheritDoc}
     * <p>
     * Paints the component.
     */
    public void paint(Graphics g) {

        // NOTE:  For this component, the resetBounds() methods should ALWAYS
        // be called before repaint().
        switch (this.mode) {
            case DisplayEdge.HALF_ANCHORED:
                g.setColor(getLineColor());
                Point point = this.getRelativeMouseTrackPoint();
                setConnectedPoints(calculateEdge(getNode1(), point));

                if (getConnectedPoints() != null) {
                    drawEdge(g);
                }

                break;

            case DisplayEdge.ANCHORED_UNSELECTED:
                g.setColor(getLineColor());
                setConnectedPoints(calculateEdge(getNode1(), getNode2()));

                if (getConnectedPoints() != null) {
                    drawEdge(g);
                }

                break;

            case DisplayEdge.ANCHORED_SELECTED:
                g.setColor(getSelectedColor());
                setConnectedPoints(calculateEdge(getNode1(), getNode2()));

                if (getConnectedPoints() != null) {
                    drawEdge(g);
                }

                break;

            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Draws the actual edge.
     *
     * @param g the graphics context.
     */
    private void drawEdge(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        getConnectedPoints().getFrom().translate(-getLocation().x,
                -getLocation().y);
        getConnectedPoints().getTo().translate(-getLocation().x,
                -getLocation().y);

        setClickRegion(null);

        int x1 = getConnectedPoints().getFrom().x;
        int y1 = getConnectedPoints().getFrom().y;
        int x2 = getConnectedPoints().getTo().x;
        int y2 = getConnectedPoints().getTo().y;

//        Endpoint endpointA = this.getModelEdge().getEndpoint1();
//        Endpoint endpointB = this.getModelEdge().getEndpoint2();
//
//        if (endpointA == Endpoint.ARROW) {
//            x1 += FastMath.signum(x2 - x1) * getStrokeWidth();
//            y1 += FastMath.signum(x2 - x1) * getStrokeWidth();
//        }
//
//        if (endpointB == Endpoint.ARROW) {
//            x2 += FastMath.signum(x1 - x2) * getStrokeWidth();
//            y2 += FastMath.signum(x1 - x2) * getStrokeWidth();
//        }

        // This silly-looking next line is required to get around an annoying
        // bug that appears in Windows (but not Linux) in JDK
        // 1.4.2_08 and 1.5.0_02 at least. If you display an editor, then
        // select the "Save Screenshot" menu item, then do it again (or
        // select the "Save Graph Image" menu item), all of the lines drawn
        // by Graphics2D.drawLine in this class get atomized and distributed
        // across the entire dialog, and this atomization doesn't go away
        // even if you close the editor and reopen it. Must be some
        // undocumented interaction between the two graphics contexts
        // (the one drawing the image on screen and the other drawing it
        // in the Graphics2D from BufferedImage). In any case, any stroke
        // width <= 1.0 seems to cause the problem, so we pick a stroke
        // width slightly greater than 1.0. jdramsey 4/16/2005
//        g2d.setStroke(new BasicStroke(1.000001f));
        Stroke s;
        float width = thick ? 3f : 1.1f;

        Stroke solid = new BasicStroke(width);
        Stroke dashed = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);

        if (this.solid) {
            s = solid;
        } else {
            s = dashed;
        }

        g2d.setStroke(s);

        if (!isSelected()) {
            g2d.setColor(this.lineColor);
        }

        g2d.drawLine(x1, y1, x2, y2);

        if (!isShowAdjacenciesOnly()) {
            drawEndpoints(getConnectedPoints(), g);
        }

        firePropertyChange("newPointPair", null, getConnectedPoints());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the parent's contains() method using the click region, so that points not in the click region are
     * passed through to components lying beneath this one in the z-order. (Equates the effective shape of this edge to
     * its click region.)
     */
    public boolean contains(int x, int y) {
        Polygon clickRegion = getClickRegion();

        if (clickRegion != null) {
            return clickRegion.contains(new Point(x, y));
        } else {
            return false;
        }
    }

    /**
     * Retrieves the getModel region where mouse clicks are responded to (as opposed to passed on).
     *
     * @return the click region.
     */
    private Polygon getClickRegion() {

        if ((this.clickRegion == null) && (getConnectedPoints() != null)) {
            this.clickRegion = getSleeve(getConnectedPoints());
        }

        return this.clickRegion;
    }

    /**
     * Allows subclasses to set the clickable region is for this component.
     *
     * @param clickRegion a {@link java.awt.Polygon} object
     */
    protected final void setClickRegion(Polygon clickRegion) {
        this.clickRegion = clickRegion;
    }

    /**
     * Retrieves the currents point pair which defines the line segment of the edge.
     *
     * @return the getModel point pair.
     */
    public final PointPair getPointPair() {
        switch (this.mode) {
            case DisplayEdge.HALF_ANCHORED:
                Point point = this.getRelativeMouseTrackPoint();
                setConnectedPoints(calculateEdge(getNode1(), point));
                break;

            case DisplayEdge.ANCHORED_UNSELECTED:

                // Falls through!
            case DisplayEdge.ANCHORED_SELECTED:
                setConnectedPoints(calculateEdge(getNode1(), getNode2()));
                break;

            default:
                throw new IllegalStateException();
        }

        return getConnectedPoints();
    }

    /**
     * <p>getComp1.</p>
     *
     * @return the 'from' AbstractGraphNode to which this session edge is anchored.
     */
    public final DisplayNode getComp1() {
        return this.getNode1();
    }

    /**
     * <p>getComp2.</p>
     *
     * @return the 'to' AbstractGraphNode to which this session edge is anchored.
     */
    public final DisplayNode getComp2() {
        return this.getNode2();
    }

    /**
     * <p>Getter for the field <code>mode</code>.</p>
     *
     * @return the getModel mode of the component.
     */
    protected final int getMode() {
        return this.mode;
    }

    /**
     * <p>getTrackPoint.</p>
     *
     * @return the getModel track point for the edge.  When a new edge is being created in the UI, one end is anchored
     * to a AbstractGraphNode while the other tracks the mouse point.  When the mouse is released, the latest mouse
     * track point is needed to determine which node it's closest to so that it can be anchored to that node.
     */
    public final Point getTrackPoint() {
        return this.mouseTrackPoint;
    }

    /**
     * <p>isSelected.</p>
     *
     * @return true iff the component is selected.
     */
    public final boolean isSelected() {
        return this.mode == DisplayEdge.ANCHORED_SELECTED;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets whether the component is selected.
     */
    public final void setSelected(boolean selected) {
        if (selected == isSelected()) {
            return;
        }

        boolean oldSelected = isSelected();

        if (this.mode != DisplayEdge.HALF_ANCHORED) {
            this.mode = (selected ? DisplayEdge.ANCHORED_SELECTED : DisplayEdge.ANCHORED_UNSELECTED);
            firePropertyChange("selected", oldSelected, selected);
            repaint();
        }
    }

    /**
     * Launches the editor for this edge, if there is one.
     */
    public void launchAssociatedEditor() {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Updates the position of the free end of the edge while it is in the HALF_ANCHORED mode.
     */
    public final void updateTrackPoint(Point p) {
        if (this.mode != DisplayEdge.HALF_ANCHORED) {
            throw new IllegalStateException(
                    "Cannot call the updateTrackPoint " +
                    "method when the edge is " +
                    "not in HALF_ANCHORED mode.");
        }

        this.mouseTrackPoint = new Point(p);
        resetBounds();
        repaint();
    }

    /**
     * <p>Getter for the field <code>node1</code>.</p>
     *
     * @return node 1.
     */
    public final DisplayNode getNode1() {
        return this.node1;
    }

    /**
     * <p>Getter for the field <code>node2</code>.</p>
     *
     * @return node 2.
     */
    public final DisplayNode getNode2() {
        return this.node2;
    }

    /**
     * <p>Getter for the field <code>connectedPoints</code>.</p>
     *
     * @return the two points this edge actually connects--that is, the intersections of the edge with node 1 and node
     * 2.
     */
    public final PointPair getConnectedPoints() {
        return this.connectedPoints;
    }

    //==========================PROTECTED METHODS========================//

    /**
     * {@inheritDoc}
     * <p>
     * Allows subclasses to set what the connected points are.
     */
    public final void setConnectedPoints(PointPair connectedPoints) {
        this.connectedPoints = connectedPoints;
    }

    /**
     * <p>Getter for the field <code>relativeMouseTrackPoint</code>.</p>
     *
     * @return the moure track point relative to this component. (It's usually given relative to the containing
     * component.)
     */
    public final Point getRelativeMouseTrackPoint() {
        return this.relativeMouseTrackPoint;
    }

    /**
     * Calculates the two endpoints of the line segment connecting two given non-overlapping rectangles.  (Should give
     * back null for overlapping rectangles but doesn't always...)
     *
     * @param comp1 a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param comp2 a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @return a point pair which represents the connecting line segment through the center of each rectangle touching
     * the edge of each.
     */
    protected final PointPair calculateEdge(DisplayNode comp1, DisplayNode comp2) {
        Rectangle r1 = comp1.getBounds();
        Rectangle r2 = comp2.getBounds();
        Point c1 = new Point((int) (r1.x + r1.width / 2.0),
                (int) (r1.y + r1.height / 2.0));
        Point c2 = new Point((int) (r2.x + r2.width / 2.0),
                (int) (r2.y + r2.height / 2.0));

        double angle = FastMath.atan2(c1.y - c2.y, c1.x - c2.x);
        angle += FastMath.PI / 2;
        Point d = new Point((int) (this.offset * FastMath.cos(angle)),
                (int) (this.offset * FastMath.sin(angle)));
        c1.translate(d.x, d.y);
        c2.translate(d.x, d.y);

        Point p1 = getBoundaryIntersection(comp1, c1, c2);
        Point p2 = getBoundaryIntersection(comp2, c2, c1);

        if ((p1 == null) || (p2 == null)) {
            c1 = new Point((int) (r1.x + r1.width / 2.0),
                    (int) (r1.y + r1.height / 2.0));
            c2 = new Point((int) (r2.x + r2.width / 2.0),
                    (int) (r2.y + r2.height / 2.0));

            p1 = getBoundaryIntersection(comp1, c1, c2);
            p2 = getBoundaryIntersection(comp2, c2, c1);
        }

        if ((p1 == null) || (p2 == null)) {
            return null;
        }

        return new PointPair(p1, p2);
    }

    /**
     * Calculates the point pair which defines the straight line segment edge from a given point p to a given component.
     * Assumes that the component contains the center point of its bounding rectangle.
     *
     * @param comp a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param p    a {@link java.awt.Point} object
     * @return a {@link edu.cmu.tetradapp.workbench.PointPair} object
     */
    protected final PointPair calculateEdge(DisplayNode comp, Point p) {
        Rectangle r = comp.getBounds();
        Point p1 = new Point((int) (r.x + r.width / 2.0),
                (int) (r.y + r.height / 2.0));
        Point p2 = new Point(p);

        p2.translate(getLocation().x, getLocation().y);
        Point p3 = getBoundaryIntersection(comp, p1, p2);

        return (p3 == null) ? null : new PointPair(p3, p2);
    }

    //============================PRIVATE METHODS========================//

    /**
     * Draws endpoints appropriate to the type of edge this is.
     *
     * @param pp the point pair which specifies where and at what angle the endpoints are to be drawn.  The 'from'
     *           endpoint is drawn at the 'from' point angled as it it were coming from the 'to' endpoint, and
     *           vice-versa.
     * @param g  the graphics context.
     */
    protected final void drawEndpoints(PointPair pp, Graphics g) {

        if (this.getModelEdge() != null) {
            Endpoint endpointA = this.getModelEdge().getEndpoint1();
            Endpoint endpointB = this.getModelEdge().getEndpoint2();

            if (endpointA == Endpoint.CIRCLE) {
                drawCircleEndpoint(pp.getTo(), pp.getFrom(), g);
            } else if (endpointA == Endpoint.ARROW) {
                drawArrowEndpoint(pp.getTo(), pp.getFrom(), g);
            }

            if (endpointB == Endpoint.CIRCLE) {
                drawCircleEndpoint(pp.getFrom(), pp.getTo(), g);
            } else if (endpointB == Endpoint.ARROW) {
                drawArrowEndpoint(pp.getFrom(), pp.getTo(), g);
            }
        } else {
            switch (this.type) {

                case DisplayEdge.SESSION:
                    drawSessionArrowEndpoint(pp.getFrom(), pp.getTo(), g);
                    break;

                case DisplayEdge.DIRECTED:
                    drawArrowEndpoint(pp.getFrom(), pp.getTo(), g);
                    break;

                case DisplayEdge.NONDIRECTED:
                    drawCircleEndpoint(pp.getTo(), pp.getFrom(), g);
                    drawCircleEndpoint(pp.getFrom(), pp.getTo(), g);
                    break;

                case DisplayEdge.UNDIRECTED:
                    break;

                case DisplayEdge.PARTIALLY_ORIENTED:
                    drawCircleEndpoint(pp.getTo(), pp.getFrom(), g);
                    drawArrowEndpoint(pp.getFrom(), pp.getTo(), g);
                    break;

                case DisplayEdge.BIDIRECTED:
                    drawArrowEndpoint(pp.getFrom(), pp.getTo(), g);
                    drawArrowEndpoint(pp.getTo(), pp.getFrom(), g);
                    break;

                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Draws an arrowhead at the 'to' end of the edge.
     */
    private void drawArrowEndpoint(Point from, Point to, Graphics g) {
        double a = to.x - from.x;
        double b = from.y - to.y;
        double theta = FastMath.atan2(b, a);
        int itheta = (int) ((theta * 360.0) / (2.0 * FastMath.PI) + 180);

//        g.fillArc(to.x - 18, to.y - 18, 36, 36, itheta - 15, 30);
        g.fillArc(to.x - 17, to.y - 17, 34, 34,
                itheta - 14 - 3 * (int) getStrokeWidth(), 29 + 6 * (int) getStrokeWidth());
    }

    /**
     * Draws an session arrowhead at the 'to' end of the edge.
     */
    private void drawSessionArrowEndpoint(Point from, Point to, Graphics g) {
        double a = to.x - from.x;
        double b = from.y - to.y;
        double theta = FastMath.atan2(b, a);
        int itheta = (int) ((theta * 360.0) / (2.0 * FastMath.PI) + 180);

//        g.fillArc(to.x - 18, to.y - 18, 36, 36, itheta - 15, 30);
        g.fillArc(to.x - 18, to.y - 18, 36, 36,
                itheta - 33 * (int) getStrokeWidth(), 66 * (int) getStrokeWidth());
    }

    /**
     * Draws a circle endpoint at the 'to' point angled as if coming from the 'from' point.
     */
    private void drawCircleEndpoint(Point from, Point to, Graphics g) {
//        final int diameter = 13;
        int diameter = 12 + (int) getStrokeWidth();
        double a = to.x - from.x;
        double b = from.y - to.y;
        double theta = FastMath.atan2(b, a);
        //        int itheta = (int) ((theta * 360.0) / (2.0 * FastMath.PI) + 180);
        int xminus = (int) (FastMath.cos(theta) * diameter / 2);
        int yplus = (int) (FastMath.sin(theta) * diameter / 2);

        g.fillOval(to.x - xminus - diameter / 2, to.y + yplus - diameter / 2,
                diameter, diameter);

        Color c = g.getColor();

        g.setColor(Color.white);
        g.fillOval(to.x - xminus - diameter / 4 - 1,
                to.y + yplus - diameter / 4 - 1, (int) (diameter / 1.4),
                (int) (diameter / 1.4));
        g.setColor(c);
    }

    /**
     * Calculates the intersection with the boundary of the given component along a line which connects one point which
     * lies inside the boundary of the component with another point which lies outside the boundary of the component. If
     * the first point does not lie inside the boundary, or if the second point does not lie outside the boundary, a
     * null is returned. If the connecting line intersects the boundary at more than one place, the outermost one is
     * returned.
     */
    private Point getBoundaryIntersection(DisplayNode comp, Point pIn,
                                          Point pOut) {
        Point loc = comp.getLocation();

        if (!comp.contains(pIn.x - loc.x, pIn.y - loc.y)) {
            return null;
        }

        if (comp.contains(pOut.x - loc.x, pOut.y - loc.y)) {
            return null;
        }

        // Set up from, to, mid for a binary search (result = boundary
        // intersection).  In testing, this binary search method was
        // comparable to analytic methods for finding boundary
        // intersections for rectangular nodes but has the advantage
        // of flexibility, since this same algorithm applies without
        // modification to any convexly shaped nodes.  (The 'contains'
        // method for that node will of course need to be different.)
        Point pFrom = new Point(pOut);
        Point pTo = new Point(pIn);
        Point pMid = null;

        while (DisplayEdge.distance(pFrom, pTo) > 2.0) {
            pMid = new Point((pFrom.x + pTo.x) / 2, (pFrom.y + pTo.y) / 2);

            if (comp.contains(pMid.x - loc.x, pMid.y - loc.y)) {
                pTo = pMid;
            } else {
                pFrom = pMid;
            }
        }

        return pMid;
    }

    /**
     * Calculates a sleeve around the edge which represents the region within which mouse clicks will be send to the
     * edge (as opposed to ignored).
     *
     * @param pp the point pair representing the line segment of the edge.
     * @return the Polygon representing the sleeve, or null if no such Polygon exists (because, e.g., one of the
     * endpoints is null).
     */
    private Polygon getSleeve(PointPair pp) {
        if ((pp == null) || (pp.getFrom() == null) || (pp.getTo() == null)) {
            return null;
        }

//        int d = 7;    // halfwidth of the sleeve.
        int d = (int) getStrokeWidth() + 6;    // halfwidth of the sleeve.

        if (FastMath.abs(pp.getFrom().y - pp.getTo().y) <= 3) {
            return DisplayEdge.getHorizSleeve(pp, d);
        }

        int[] xpoints = new int[4];
        int[] ypoints = new int[4];
        double qx;
        double qy;

        qx = pp.getTo().x - pp.getFrom().x;
        qy = pp.getTo().y - pp.getFrom().y;

        double sx, sy;

        sx = (double) (d * d) / (1.0 + (qx * qx) / (qy * qy));
        sx = FastMath.pow(sx, 0.5);
        sy = -(qx / qy) * sx;
        sx += (double) pp.getFrom().x + 1.0;
        sy += (double) pp.getFrom().y + 1.0;

        Point t = new Point((int) (sx) - pp.getFrom().x,
                (int) (sy) - pp.getFrom().y);

        xpoints[0] = pp.getFrom().x + t.x;
        xpoints[1] = pp.getTo().x + t.x;
        xpoints[2] = pp.getTo().x - t.x;
        xpoints[3] = pp.getFrom().x - t.x;
        ypoints[0] = pp.getFrom().y + t.y;
        ypoints[1] = pp.getTo().y + t.y;
        ypoints[2] = pp.getTo().y - t.y;
        ypoints[3] = pp.getFrom().y - t.y;

        return new Polygon(xpoints, ypoints, 4);
    }

    /**
     * This method resets the bounds of the edge component to the union of the bounds of the two components which the
     * edge connects.  It also calculates the bounds of these two components relative to this new union.
     */
    private void resetBounds() {
        switch (this.mode) {
            case DisplayEdge.HALF_ANCHORED:
                Rectangle temp = new Rectangle(this.mouseTrackPoint.x,
                        this.mouseTrackPoint.y, 0, 0);

                setBounds(getNode1().getBounds().union(temp.getBounds()));

                //                node1RelativeBounds = new Rectangle(getNode1().getBounds());
                this.relativeMouseTrackPoint = new Point(this.mouseTrackPoint);

                //                node1RelativeBounds.translate(-getLocation().x, -getLocation().y);
                getRelativeMouseTrackPoint().translate(-getLocation().x,
                        -getLocation().y);
                break;

            case DisplayEdge.ANCHORED_UNSELECTED:

                // Falls through!
            case DisplayEdge.ANCHORED_SELECTED:
                Rectangle r1 = this.node1.getBounds();
                Rectangle r2 = this.node2.getBounds();
                Point c1 = new Point((int) (r1.x + r1.width / 2.0),
                        (int) (r1.y + r1.height / 2.0));
                Point c2 = new Point((int) (r2.x + r2.width / 2.0),
                        (int) (r2.y + r2.height / 2.0));

                double angle = FastMath.atan2(c1.y - c2.y, c1.x - c2.x);
                angle += FastMath.PI / 2;
                Point d = new Point((int) (this.offset * FastMath.cos(angle)),
                        (int) (this.offset * FastMath.sin(angle)));

                r1.translate(d.x, d.y);
                r2.translate(d.x, d.y);

                setBounds(r1.getBounds().union(r2.getBounds()));

                break;

            default:
                throw new IllegalStateException();
        }
    }

    private boolean isShowAdjacenciesOnly() {
        return this.showAdjacenciesOnly;
    }

    /**
     * <p>Getter for the field <code>modelEdge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Edge} object
     */
    public final Edge getModelEdge() {
        return this.modelEdge;
    }

    /**
     * <p>Getter for the field <code>offset</code>.</p>
     *
     * @return a double
     */
    public double getOffset() {
        return this.offset;
    }

    /**
     * {@inheritDoc}
     */
    public void setOffset(double offset) {
        this.offset = offset;
    }

    /**
     * <p>Getter for the field <code>lineColor</code>.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public Color getLineColor() {
        return this.highlighted ? getHighlightedColor() : this.lineColor;
//        return lineColor;
    }

    /**
     * {@inheritDoc}
     */
    public void setLineColor(Color lineColor) {
        if (lineColor != null) {
            this.lineColor = lineColor;
        }
    }

    /**
     * <p>Getter for the field <code>solid</code>.</p>
     *
     * @return a boolean
     */
    public boolean getSolid() {
        return this.solid;
    }

    /**
     * {@inheritDoc}
     */
    public void setSolid(boolean solid) {
        this.solid = solid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setThick(boolean thick) {
        this.thick = thick;
    }

    /**
     * <p>Getter for the field <code>selectedColor</code>.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public Color getSelectedColor() {
        return this.selectedColor;
    }

    /**
     * {@inheritDoc}
     */
    public void setSelectedColor(Color selectedColor) {
        this.selectedColor = selectedColor;
    }

    /**
     * <p>Getter for the field <code>highlightedColor</code>.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public Color getHighlightedColor() {
        return this.highlightedColor;
    }

    /**
     * {@inheritDoc}
     */
    public void setHighlightedColor(Color highlightedColor) {
        this.highlightedColor = highlightedColor;
    }

    /**
     * <p>Getter for the field <code>strokeWidth</code>.</p>
     *
     * @return a float
     */
    public float getStrokeWidth() {
        return this.strokeWidth;
    }

    /**
     * {@inheritDoc}
     */
    public void setStrokeWidth(float strokeWidth) {
        if (strokeWidth < 0f) {
            throw new IllegalArgumentException("Stroke width must be at least 0.");
        }

        this.strokeWidth = strokeWidth;
    }

    /**
     * {@inheritDoc}
     */
    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    //======================= Event handler class========================//

    /**
     * Handles <code>ComponentEvent</code>s.
     */
    private final class ComponentHandler extends ComponentAdapter {
        public void componentMoved(ComponentEvent e) {
            resetBounds();
            repaint();
        }

        public void componentResized(ComponentEvent e) {
            resetBounds();
            repaint();
        }
    }

    /**
     * Handles <code>PropertyChangeEvent</code>s.
     */
    private final class PropertyChangeHandler implements PropertyChangeListener {

        /**
         * This method gets called when a bound property is changed.
         *
         * @param evt A PropertyChangeEvent object describing the event source and the property that has changed.
         */
        public void propertyChange(PropertyChangeEvent evt) {
            String name = evt.getPropertyName();

            if ("selected".equals(name)) {
                if (Boolean.FALSE.equals(evt.getNewValue())) {
                    setSelected(false);
                }
            }
        }
    }
}





