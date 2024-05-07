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

package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.IDisplayEdge;
import edu.cmu.tetradapp.workbench.PointPair;
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
public class KnowledgeDisplayEdge extends JComponent implements IDisplayEdge {

    /**
     * Indicates an explicitly forbidden edge.
     */
    public static final int FORBIDDEN_EXPLICITLY = 3;
    /**
     * Indicates a required edges.
     */
    public static final int REQUIRED = 5;
    /**
     * Indicates that one end of the edge is anchored to one component, but the other half is tracking the mouse point.
     */
    private static final int HALF_ANCHORED = 0;
    /**
     * Indicates that both ends of the edge are anchored to components.  The edge will move when those components move.
     */
    private static final int ANCHORED_UNSELECTED = 1;
    /**
     * Indicates that the edge is under construction, which is similar to the normal mode except that it's displayed as
     * selected and will identify itself as selected upon request (for possible deletion, e.g.).
     */
    private static final int ANCHORED_SELECTED = 2;
    /**
     * Indicates an edges forbidden by tiers.
     */
    private static final int FORBIDDEN_BY_TIERS = 4;
    /**
     * Indicates a edge required by a knowledge group.
     */
    private static final int REQUIRED_BY_GROUPS = 6;

    /**
     * Indicates a edge forbidden by a knowledge group.
     */
    private static final int FORBIDDEN_BY_GROUPS = 7;

    /**
     * The color that forbidden edges are drawn in.
     */
    private final Color forbiddenExplicitlyColor = Color.MAGENTA.darker().darker();

    /**
     * The color for forbidden group edges.
     */
    private final Color forbiddenGroupsColor = Color.MAGENTA.brighter();

    /**
     * The color for required group edges.
     */
    private final Color requiredGroupsColor = Color.GREEN.brighter();


    /**
     * The color that forbidden edges are drawn in.
     */
    private final Color forbiddenByTiersColor = Color.LIGHT_GRAY;

    /**
     * The color that required edges are drawn in.
     */
    private final Color requiredColor = Color.GREEN.darker();

    /**
     * The color that selected edges will be drawn in.
     */
    private final Color selectedColor = Color.red;
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
     * The highlighted color.
     */
    private Color highlightedColor = Color.red.darker().darker();
    /**
     * The model edge that this display is is portraying (if known).
     */
    private Edge modelEdge;
    /**
     * The getModel mode of the edge--HALF_ANCHORED, ANCHORED_UNSELECTED, or ANCHORED_SELECTED.
     */
    private int mode;
    /**
     * One of FORBIDDEN or REQUIRED.
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
     * True if only the adjacencies of the edge should be shown.
     */
    private boolean showAdjacenciesOnly = false;
    /**
     * The offset of this edge for multiple edges between node pairs.
     */
    private double offset;
    /**
     * The pair of points that this edge connects, from the edge of one component to the edge of the other.
     */
    private PointPair connectedPoints;

    /**
     * True if the edge is solid, false if it's dashed.
     */
    private boolean solid = true;

    /**
     * True if the edge is thick, false if it's thin.
     */
    private boolean thick = false;

    //==========================CONSTRUCTORS============================//

    /**
     * Constructs a new DisplayEdge connecting two components, 'node1' and 'node2', assuming that a reference to the
     * model edge will not be needed.
     *
     * @param node1 the 'from' component.
     * @param node2 the 'to' component.
     * @param type  FORBIDDEN or REQUIRED.
     */
    public KnowledgeDisplayEdge(KnowledgeDisplayNode node1,
                                KnowledgeDisplayNode node2, int type) {
        if (node1 == null) {
            throw new NullPointerException("Node1 must not be null.");
        }

        if (node2 == null) {
            throw new NullPointerException("Node2 must not be null.");
        }

        if (!(type == KnowledgeDisplayEdge.FORBIDDEN_EXPLICITLY || type == KnowledgeDisplayEdge.FORBIDDEN_BY_TIERS ||
              type == KnowledgeDisplayEdge.REQUIRED || type == KnowledgeDisplayEdge.REQUIRED_BY_GROUPS || type == KnowledgeDisplayEdge.FORBIDDEN_BY_GROUPS)) {
            throw new IllegalArgumentException();
        }

        this.node1 = node1;
        this.node2 = node2;
        this.mode = KnowledgeDisplayEdge.ANCHORED_UNSELECTED;
        this.type = type;

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
    public KnowledgeDisplayEdge(Edge modelEdge, DisplayNode node1,
                                DisplayNode node2) {
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
        this.mode = KnowledgeDisplayEdge.ANCHORED_UNSELECTED;

        node1.addComponentListener(this.compHandler);
        node2.addComponentListener(this.compHandler);

        KnowledgeModelEdge _modelEdge = (KnowledgeModelEdge) modelEdge;

        int edgeType = _modelEdge.getType();
        if (edgeType == KnowledgeModelEdge.FORBIDDEN_EXPLICITLY) {
            this.type = KnowledgeDisplayEdge.FORBIDDEN_EXPLICITLY;
        } else if (edgeType == KnowledgeModelEdge.FORBIDDEN_BY_TIERS) {
            this.type = KnowledgeDisplayEdge.FORBIDDEN_BY_TIERS;
        } else if (edgeType == KnowledgeModelEdge.REQUIRED) {
            this.type = KnowledgeDisplayEdge.REQUIRED;
        } else if (edgeType == KnowledgeModelEdge.REQUIRED_BY_GROUPS) {
            this.type = KnowledgeDisplayEdge.REQUIRED_BY_GROUPS;
        } else if (edgeType == KnowledgeModelEdge.FORBIDDEN_BY_GROUPS) {
            this.type = KnowledgeDisplayEdge.FORBIDDEN_BY_GROUPS;
        }

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
    public KnowledgeDisplayEdge(DisplayNode node1, Point mouseTrackPoint,
                                int type) {
        if (node1 == null) {
            throw new NullPointerException("Node1 must not be null.");
        }

        if (mouseTrackPoint == null) {
            throw new NullPointerException(
                    "Mouse track point must not " + "be null.");
        }

        if (!(type == KnowledgeDisplayEdge.FORBIDDEN_EXPLICITLY || type == KnowledgeDisplayEdge.FORBIDDEN_BY_TIERS ||
              type == KnowledgeDisplayEdge.REQUIRED || type == KnowledgeDisplayEdge.REQUIRED_BY_GROUPS || type == KnowledgeDisplayEdge.FORBIDDEN_BY_GROUPS)) {
            throw new IllegalArgumentException();
        }

        this.node1 = node1;
        this.mouseTrackPoint = mouseTrackPoint;
        this.mode = KnowledgeDisplayEdge.HALF_ANCHORED;
        this.type = type;

        resetBounds();
    }

    /**
     * Calculates the distance between a pair of points.
     */
    private static double distance(Point p1, Point p2) {

        double d;

        d = (p1.x - p2.x) * (p1.x - p2.x);
        d += (p1.y - p2.y) * (p1.y - p2.y);
        d = FastMath.sqrt(d);

        return d;
    }

    /**
     * Draws an arrowhead at the 'to' end of the edge.
     */
    private static void drawArrowEndpoint(Point from, Point to, Graphics g) {
        double a = to.x - from.x;
        double b = from.y - to.y;
        double theta = FastMath.atan2(b, a);
        int itheta = (int) ((theta * 360.0) / (2.0 * FastMath.PI) + 180);

        g.fillArc(to.x - 18, to.y - 18, 36, 36, itheta - 15, 30);
    }

    //============================PUBLIC METHODS========================//

    /**
     * Draws a circle endpoint at the 'to' point angled as if coming from the 'from' point.
     */
    private static void drawCircleEndpoint(Point from, Point to, Graphics g) {
        final int diameter = 13;
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
     * Calculates a sleeve around the edge which represents the region within which mouse clicks will be send to the
     * edge (as opposed to ignored).
     *
     * @param pp the point pair representing the line segment of the edge.
     * @return the Polygon representing the sleeve, or null if no such Polygon exists (because, e.g., one of the
     * endpoints is null).
     */
    private static Polygon getSleeve(PointPair pp) {
        if ((pp == null) || (pp.getFrom() == null) || (pp.getTo() == null)) {
            return null;
        }

        final int d = 7;    // halfwidth of the sleeve.

        if (FastMath.abs(pp.getFrom().y - pp.getTo().y) <= 3) {
            return KnowledgeDisplayEdge.getHorizSleeve(pp);
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
     * Calculates the degenerate horizontal sleeve in the case where the point pair is near horizontal.
     *
     * @param pp the given point pair.
     * @return the sleeve as a polygon.
     */
    private static Polygon getHorizSleeve(PointPair pp) {
        int[] xpoints = new int[4];
        int[] ypoints = new int[4];

        xpoints[0] = pp.getFrom().x;
        xpoints[1] = pp.getFrom().x;
        xpoints[2] = pp.getTo().x;
        xpoints[3] = pp.getTo().x;
        ypoints[0] = pp.getFrom().y + 7;
        ypoints[1] = pp.getFrom().y - 7;
        ypoints[2] = pp.getTo().y - 7;
        ypoints[3] = pp.getTo().y + 7;

        return new Polygon(xpoints, ypoints, 4);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Paints the component.
     */
    public void paint(Graphics g) {

        // NOTE:  For this component, the resetBounds() methods should ALWAYS
        // be called before repaint().
        switch (this.mode) {

            case KnowledgeDisplayEdge.HALF_ANCHORED:
                g.setColor(getLineColor());

                Point point = getRelativeMouseTrackPoint();
                setConnectedPoints(calculateEdge(getNode1(), point));

                if (getConnectedPoints() != null) {
                    drawEdge(g);

                }
                break;

            case KnowledgeDisplayEdge.ANCHORED_UNSELECTED:
                g.setColor(getLineColor());

                setConnectedPoints(calculateEdge(getNode1(), getNode2()));

                if (getConnectedPoints() != null) {
                    drawEdge(g);
                }
                break;

            case KnowledgeDisplayEdge.ANCHORED_SELECTED:
                g.setColor(this.selectedColor);

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
     */
    private void drawEdge(Graphics g) {
        getConnectedPoints().getFrom().translate(-getLocation().x,
                -getLocation().y);
        getConnectedPoints().getTo().translate(-getLocation().x,
                -getLocation().y);

        setClickRegion();

        g.drawLine(getConnectedPoints().getFrom().x,
                getConnectedPoints().getFrom().y,
                getConnectedPoints().getTo().x, getConnectedPoints().getTo().y);

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
            this.clickRegion = KnowledgeDisplayEdge.getSleeve(getConnectedPoints());
        }

        return this.clickRegion;
    }

    /**
     * Retrieves the currents point pair which defines the line segment of the edge.
     *
     * @return the getModel point pair.
     */
    public final PointPair getPointPair() {
        switch (this.mode) {
            case KnowledgeDisplayEdge.HALF_ANCHORED:
                Point point = getRelativeMouseTrackPoint();
                setConnectedPoints(calculateEdge(getNode1(), point));
                break;

            case KnowledgeDisplayEdge.ANCHORED_UNSELECTED:

                // Falls through!
            case KnowledgeDisplayEdge.ANCHORED_SELECTED:
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
        return getNode1();
    }

    /**
     * <p>getComp2.</p>
     *
     * @return the 'to' AbstractGraphNode to which this session edge is anchored.
     */
    public final DisplayNode getComp2() {
        return getNode2();
    }

    /**
     * <p>Getter for the field <code>mode</code>.</p>
     *
     * @return the getModel mode of the component.
     */
    public final int getMode() {
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
        return this.mode == KnowledgeDisplayEdge.ANCHORED_SELECTED;
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

        if (this.mode != KnowledgeDisplayEdge.HALF_ANCHORED) {
            this.mode = (selected ? KnowledgeDisplayEdge.ANCHORED_SELECTED : KnowledgeDisplayEdge.ANCHORED_UNSELECTED);
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
        if (this.mode != KnowledgeDisplayEdge.HALF_ANCHORED) {
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

    //==========================PROTECTED METHODS========================//

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

    //============================PRIVATE METHODS========================//

    /**
     * Allows subclasses to set the clickable region is for this component.
     */
    private void setClickRegion() {
        this.clickRegion = null;
    }

    /**
     * Calculates the two endpoints of the line segment connecting two given non-overlapping rectangles.  (Should give
     * back null for overlapping rectangles but doesn't always...)
     *
     * @return a point pair which represents the connecting line segment through the center of each rectangle touching
     * the edge of each.
     */
    private PointPair calculateEdge(Component comp1, Component comp2) {
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
     */
    private PointPair calculateEdge(DisplayNode comp, Point p) {
        Rectangle r = comp.getBounds();
        Point p1 = new Point((int) (r.x + r.width / 2.0),
                (int) (r.y + r.height / 2.0));
        Point p2 = new Point(p);

        p2.translate(getLocation().x, getLocation().y);

        Point p3 = getBoundaryIntersection(comp, p1, p2);

        return (p3 == null) ? null : new PointPair(p3, p2);
    }

    /**
     * Draws endpoints appropriate to the type of edge this is.
     *
     * @param pp the point pair which specifies where and at what angle the endpoints are to be drawn.  The 'from'
     *           endpoint is drawn at the 'from' point angled as it it were coming from the 'to' endpoint, and
     *           vice-versa.
     * @param g  the graphics context.
     */
    private void drawEndpoints(PointPair pp, Graphics g) {

        if (getModelEdge() != null) {
            Endpoint endpointA = getModelEdge().getEndpoint1();
            Endpoint endpointB = getModelEdge().getEndpoint2();

            if (endpointA == Endpoint.CIRCLE) {
                KnowledgeDisplayEdge.drawCircleEndpoint(pp.getTo(), pp.getFrom(), g);
            } else if (endpointA == Endpoint.ARROW) {
                KnowledgeDisplayEdge.drawArrowEndpoint(pp.getTo(), pp.getFrom(), g);
            }

            if (endpointB == Endpoint.CIRCLE) {
                KnowledgeDisplayEdge.drawCircleEndpoint(pp.getFrom(), pp.getTo(), g);
            } else if (endpointB == Endpoint.ARROW) {
                KnowledgeDisplayEdge.drawArrowEndpoint(pp.getFrom(), pp.getTo(), g);
            }
        } else {
            KnowledgeDisplayEdge.drawArrowEndpoint(pp.getFrom(), pp.getTo(), g);
        }
    }

    /**
     * Calculates the intersection with the boundary of the given component along a line which connects one point which
     * lies inside the boundary of the component with another point which lies outside the boundary of the component. If
     * the first point does not lie inside the boundary, or if the second point does not lie outside the boundary, a
     * null is returned. If the connecting line intersects the boundary at more than one place, the outermost one is
     * returned.
     */
    private Point getBoundaryIntersection(Component comp, Point pIn,
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

        while (KnowledgeDisplayEdge.distance(pFrom, pTo) > 2.0) {
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
     * This method resets the bounds of the edge component to the union of the bounds of the two components which the
     * edge connects.  It also calculates the bounds of these two components relative to this new union.
     */
    private void resetBounds() {
        Rectangle node1RelativeBounds;
        Rectangle node2RelativeBounds;

        switch (this.mode) {
            case KnowledgeDisplayEdge.HALF_ANCHORED:
                Rectangle temp = new Rectangle(this.mouseTrackPoint.x,
                        this.mouseTrackPoint.y, 0, 0);

                Rectangle r = getNode1().getBounds().union(temp.getBounds());
                r = r.union(new Rectangle(r.x - 3, r.y - 3, r.width + 6,
                        r.height + 6));
                setBounds(r);

                node1RelativeBounds = new Rectangle(getNode1().getBounds());
                this.relativeMouseTrackPoint = new Point(this.mouseTrackPoint);

                node1RelativeBounds.translate(-getLocation().x,
                        -getLocation().y);
                getRelativeMouseTrackPoint().translate(-getLocation().x,
                        -getLocation().y);
                break;

            case KnowledgeDisplayEdge.ANCHORED_UNSELECTED:

                // Falls through!
            case KnowledgeDisplayEdge.ANCHORED_SELECTED:
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

                Rectangle r3 = r1.getBounds().union(r2.getBounds());
                r3 = r3.union(new Rectangle(r3.x - 3, r3.y - 3, r3.width + 6,
                        r3.height + 6));
                setBounds(r3);

                node1RelativeBounds = new Rectangle(getNode1().getBounds());
                node2RelativeBounds = new Rectangle(getNode2().getBounds());

                node1RelativeBounds.translate(-getLocation().x,
                        -getLocation().y);
                node2RelativeBounds.translate(-getLocation().x,
                        -getLocation().y);
                break;

            default:
                throw new IllegalStateException();
        }
    }

    private boolean isShowAdjacenciesOnly() {
        return this.showAdjacenciesOnly;
    }

    /**
     * <p>Setter for the field <code>showAdjacenciesOnly</code>.</p>
     *
     * @param showAdjacenciesOnly a boolean
     */
    public void setShowAdjacenciesOnly(boolean showAdjacenciesOnly) {
        this.showAdjacenciesOnly = showAdjacenciesOnly;
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
     * <p>Getter for the field <code>type</code>.</p>
     *
     * @return a int
     */
    public int getType() {
        return this.type;
    }

    /**
     * <p>getLineColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public Color getLineColor() {
        // Ignore highlighting, too evil. jdramsey 3/4/2010

        if (this.type == KnowledgeDisplayEdge.FORBIDDEN_EXPLICITLY) {
            return this.forbiddenExplicitlyColor;
        }
        if (this.type == KnowledgeDisplayEdge.FORBIDDEN_BY_TIERS) {
            return this.forbiddenByTiersColor;
        } else if (this.type == KnowledgeDisplayEdge.REQUIRED) {
            return this.requiredColor;
        } else if (this.type == KnowledgeDisplayEdge.REQUIRED_BY_GROUPS) {
            return this.requiredGroupsColor;
        } else if (this.type == KnowledgeDisplayEdge.FORBIDDEN_BY_GROUPS) {
            return this.forbiddenGroupsColor;
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Unimplemented.
     */
    public void setLineColor(Color lineColor) {
//        throw new UnsupportedOperationException();
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
     * Unimplemented.
     *
     * @return a {@link java.awt.Color} object
     */
    public Color getSelectedColor() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Unimplemented.
     */
    public void setSelectedColor(Color selectedColor) {
        throw new UnsupportedOperationException();
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
     * Unimplemented.
     *
     * @return a float
     */
    public float getStrokeWidth() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Unimplemented.
     */
    public void setStrokeWidth(float strokeWidth) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void setHighlighted(boolean highlighted) {
        /*
      True iff this edge is highlighted.
     */
    }

    //======================= Event handler class========================//

    /**
     * Handles <code>ComponentEvent</code>s.
     */
    private final class ComponentHandler extends ComponentAdapter {

        /**
         * This method captures motion events on the components to which the edge is anchored and repaints the edge
         * accordingly.
         */
        public void componentMoved(ComponentEvent e) {
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





