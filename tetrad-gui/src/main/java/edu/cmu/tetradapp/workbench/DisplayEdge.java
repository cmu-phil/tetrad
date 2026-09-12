package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.util.TMath;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
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

    public static final int DIRECTED = 0;
    public static final int NONDIRECTED = 1;
    public static final int UNDIRECTED = 2;
    public static final int PARTIALLY_ORIENTED = 3;
    public static final int BIDIRECTED = 4;
    public static final int SESSION = 5;

    protected static final int HALF_ANCHORED = 0;
    protected static final int ANCHORED_UNSELECTED = 1;
    protected static final int ANCHORED_SELECTED = 2;

    private final DisplayNode node1;
    private final ComponentHandler compHandler = new ComponentHandler();
    private final PropertyChangeHandler propertyChangeHandler = new PropertyChangeHandler();

    private Edge modelEdge;
    private int mode;
    private int type;
    private DisplayNode node2;
    private Point mouseTrackPoint = new Point();
    private Point relativeMouseTrackPoint = new Point();
    private Polygon clickRegion;
    private boolean showAdjacenciesOnly;
    private double offset;
    private PointPair connectedPoints;

    /**
     * User-specified override for the line color; null means use theme color.
     */
    private Color lineColor;

    /**
     * User-specified override for the selected color; null means use theme color.
     */
    private Color selectedColor;

    /**
     * User-specified override for the highlighted color; null means use theme color.
     */
    private Color highlightedColor;

    private float strokeWidth = 1.2f;
    private boolean highlighted;
    private boolean solid = true;
    private boolean thick = false;

    //==========================CONSTRUCTORS============================//

    protected DisplayEdge(DisplayNode node1, DisplayNode node2, int type) {
        this(node1, node2, type, null);
    }

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

        setOpaque(false);
        resetBounds();
    }

    public DisplayEdge(Edge modelEdge, DisplayNode node1, DisplayNode node2) {
        this(modelEdge, node1, node2, null);
    }

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

        setOpaque(false);
        resetBounds();
    }

    public DisplayEdge(DisplayNode node1, Point mouseTrackPoint, int type) {
        this(node1, mouseTrackPoint, type, null);
    }

    public DisplayEdge(DisplayNode node1, Point mouseTrackPoint, int type, Color color) {
        if (node1 == null) {
            throw new NullPointerException("Node1 must not be null.");
        }

        if (mouseTrackPoint == null) {
            throw new NullPointerException("Mouse track point must not be null.");
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

        setOpaque(false);
        resetBounds();
    }

    //============================THEME HELPERS========================//

    private static boolean isDarkMode() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
    }

    private static Color getDefaultLineColor() {
        return WorkbenchStyle.edge();
    }

    private static Color getDefaultSelectedColor() {
        return WorkbenchStyle.edgeSelected();
    }

    private static Color getDefaultHighlightedColor() {
        return WorkbenchStyle.edgeHighlighted();
    }

    private static Color getCircleInteriorColor() {
        return WorkbenchStyle.circleInterior();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        repaint();
    }

    //============================UTILITY========================//

    protected static double distance(Point p1, Point p2) {
        double d;
        d = (p1.x - p2.x) * (p1.x - p2.x);
        d += (p1.y - p2.y) * (p1.y - p2.y);
        d = TMath.sqrt(d);
        return d;
    }

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

    @Override
    public void paint(Graphics g) {
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

    private void drawEdge(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        getConnectedPoints().getFrom().translate(-getLocation().x, -getLocation().y);
        getConnectedPoints().getTo().translate(-getLocation().x, -getLocation().y);

        setClickRegion(null);

        int x1 = getConnectedPoints().getFrom().x;
        int y1 = getConnectedPoints().getFrom().y;
        int x2 = getConnectedPoints().getTo().x;
        int y2 = getConnectedPoints().getTo().y;

        WorkbenchStyle.applyHints(g2d);

        Stroke s;
        float width = thick ? 3f : 1.5f;

        Stroke solidStroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Stroke dashedStroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
                new float[]{7f, 5f}, 0f);

        s = this.solid ? solidStroke : dashedStroke;
        g2d.setStroke(s);

        if (!isSelected()) {
            g2d.setColor(this.getLineColor());
        } else {
            g2d.setColor(this.getSelectedColor());
        }

        g2d.drawLine(x1, y1, x2, y2);

        if (!isShowAdjacenciesOnly()) {
            drawEndpoints(getConnectedPoints(), g2d);
        }

        firePropertyChange("newPointPair", null, getConnectedPoints());
    }

    @Override
    public boolean contains(int x, int y) {
        Polygon clickRegion = getClickRegion();
        return clickRegion != null && clickRegion.contains(new Point(x, y));
    }

    private Polygon getClickRegion() {
        if ((this.clickRegion == null) && (getConnectedPoints() != null)) {
            this.clickRegion = getSleeve(getConnectedPoints());
        }
        return this.clickRegion;
    }

    protected final void setClickRegion(Polygon clickRegion) {
        this.clickRegion = clickRegion;
    }

    public final PointPair getPointPair() {
        switch (this.mode) {
            case DisplayEdge.HALF_ANCHORED:
                Point point = this.getRelativeMouseTrackPoint();
                setConnectedPoints(calculateEdge(getNode1(), point));
                break;

            case DisplayEdge.ANCHORED_UNSELECTED:
            case DisplayEdge.ANCHORED_SELECTED:
                setConnectedPoints(calculateEdge(getNode1(), getNode2()));
                break;

            default:
                throw new IllegalStateException();
        }

        return getConnectedPoints();
    }

    public final DisplayNode getComp1() {
        return this.getNode1();
    }

    public final DisplayNode getComp2() {
        return this.getNode2();
    }

    protected final int getMode() {
        return this.mode;
    }

    public final Point getTrackPoint() {
        return this.mouseTrackPoint;
    }

    public final boolean isSelected() {
        return this.mode == DisplayEdge.ANCHORED_SELECTED;
    }

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

    public void launchAssociatedEditor() {
    }

    public final void updateTrackPoint(Point p) {
        if (this.mode != DisplayEdge.HALF_ANCHORED) {
            throw new IllegalStateException(
                    "Cannot call the updateTrackPoint method when the edge is not in HALF_ANCHORED mode.");
        }

        this.mouseTrackPoint = new Point(p);
        resetBounds();
        repaint();
    }

    public final DisplayNode getNode1() {
        return this.node1;
    }

    public final DisplayNode getNode2() {
        return this.node2;
    }

    public final PointPair getConnectedPoints() {
        return this.connectedPoints;
    }

    //==========================PROTECTED METHODS========================//

    public final void setConnectedPoints(PointPair connectedPoints) {
        this.connectedPoints = connectedPoints;
    }

    public final Point getRelativeMouseTrackPoint() {
        return this.relativeMouseTrackPoint;
    }

    protected final PointPair calculateEdge(DisplayNode comp1, DisplayNode comp2) {
        Rectangle r1 = comp1.getBounds();
        Rectangle r2 = comp2.getBounds();
        Point c1 = new Point((int) (r1.x + r1.width / 2.0), (int) (r1.y + r1.height / 2.0));
        Point c2 = new Point((int) (r2.x + r2.width / 2.0), (int) (r2.y + r2.height / 2.0));

        double angle = TMath.atan2(c1.y - c2.y, c1.x - c2.x);
        angle += TMath.PI / 2;
        Point d = new Point((int) (this.offset * TMath.cos(angle)), (int) (this.offset * TMath.sin(angle)));
        c1.translate(d.x, d.y);
        c2.translate(d.x, d.y);

        Point p1 = getBoundaryIntersection(comp1, c1, c2);
        Point p2 = getBoundaryIntersection(comp2, c2, c1);

        if ((p1 == null) || (p2 == null)) {
            c1 = new Point((int) (r1.x + r1.width / 2.0), (int) (r1.y + r1.height / 2.0));
            c2 = new Point((int) (r2.x + r2.width / 2.0), (int) (r2.y + r2.height / 2.0));

            p1 = getBoundaryIntersection(comp1, c1, c2);
            p2 = getBoundaryIntersection(comp2, c2, c1);
        }

        if ((p1 == null) || (p2 == null)) {
            return null;
        }

        return new PointPair(p1, p2);
    }

    protected final PointPair calculateEdge(DisplayNode comp, Point p) {
        Rectangle r = comp.getBounds();
        Point p1 = new Point((int) (r.x + r.width / 2.0), (int) (r.y + r.height / 2.0));
        Point p2 = new Point(p);

        p2.translate(getLocation().x, getLocation().y);
        Point p3 = getBoundaryIntersection(comp, p1, p2);

        return (p3 == null) ? null : new PointPair(p3, p2);
    }

    //============================PRIVATE METHODS========================//

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

    private float endpointScale() {
        return Math.max(1f, getStrokeWidth()) * (thick ? 1.35f : 1f);
    }

    private void drawArrowEndpoint(Point from, Point to, Graphics g) {
        float scale = endpointScale();
        fillArrowhead(from, to, 11f * scale, 4.5f * scale, g);
    }

    private void drawSessionArrowEndpoint(Point from, Point to, Graphics g) {
        float scale = endpointScale();
        fillArrowhead(from, to, 13f * scale, 6f * scale, g);
    }

    /**
     * Fills a triangular arrowhead with its tip at {@code to}, pointing away from {@code from}.
     *
     * @param from      the point the edge comes from.
     * @param to        the tip of the arrowhead.
     * @param length    the length of the arrowhead along the edge.
     * @param halfWidth half the width of the arrowhead base.
     * @param g         the graphics to draw on.
     */
    private void fillArrowhead(Point from, Point to, float length, float halfWidth, Graphics g) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double len = TMath.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return;

        double ux = dx / len;
        double uy = dy / len;
        double bx = to.x - ux * length;
        double by = to.y - uy * length;

        Path2D.Double head = new Path2D.Double();
        head.moveTo(to.x, to.y);
        head.lineTo(bx - uy * halfWidth, by + ux * halfWidth);
        head.lineTo(bx + uy * halfWidth, by - ux * halfWidth);
        head.closePath();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            WorkbenchStyle.applyHints(g2);
            g2.fill(head);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(head);
        } finally {
            g2.dispose();
        }
    }

    private void drawCircleEndpoint(Point from, Point to, Graphics g) {
        double diameter = 11.0 * endpointScale();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double len = TMath.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return;

        // Center the circle so it is tangent to the node boundary at 'to'.
        double cx = to.x - dx / len * diameter / 2.0;
        double cy = to.y - dy / len * diameter / 2.0;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            WorkbenchStyle.applyHints(g2);
            Ellipse2D.Double circle = new Ellipse2D.Double(cx - diameter / 2.0, cy - diameter / 2.0,
                    diameter, diameter);
            Color ring = g2.getColor();
            g2.setColor(getCircleInteriorColor());
            g2.fill(circle);
            g2.setColor(ring);
            g2.setStroke(new BasicStroke(thick ? 2.5f : 1.6f));
            g2.draw(circle);
        } finally {
            g2.dispose();
        }
    }

    private Point getBoundaryIntersection(DisplayNode comp, Point pIn, Point pOut) {
        Point loc = comp.getLocation();

        if (!comp.contains(pIn.x - loc.x, pIn.y - loc.y)) {
            return null;
        }

        if (comp.contains(pOut.x - loc.x, pOut.y - loc.y)) {
            return null;
        }

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

    private Polygon getSleeve(PointPair pp) {
        if ((pp == null) || (pp.getFrom() == null) || (pp.getTo() == null)) {
            return null;
        }

        int d = (int) getStrokeWidth() + 6;

        if (TMath.abs(pp.getFrom().y - pp.getTo().y) <= 3) {
            return DisplayEdge.getHorizSleeve(pp, d);
        }

        int[] xpoints = new int[4];
        int[] ypoints = new int[4];
        double qx = pp.getTo().x - pp.getFrom().x;
        double qy = pp.getTo().y - pp.getFrom().y;

        double sx = (double) (d * d) / (1.0 + (qx * qx) / (qy * qy));
        sx = TMath.pow(sx, 0.5);
        double sy = -(qx / qy) * sx;
        sx += (double) pp.getFrom().x + 1.0;
        sy += (double) pp.getFrom().y + 1.0;

        Point t = new Point((int) (sx) - pp.getFrom().x, (int) (sy) - pp.getFrom().y);

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

    private void resetBounds() {
        switch (this.mode) {
            case DisplayEdge.HALF_ANCHORED:
                Rectangle temp = new Rectangle(this.mouseTrackPoint.x, this.mouseTrackPoint.y, 0, 0);
                setBounds(getNode1().getBounds().union(temp.getBounds()));
                this.relativeMouseTrackPoint = new Point(this.mouseTrackPoint);
                getRelativeMouseTrackPoint().translate(-getLocation().x, -getLocation().y);
                break;

            case DisplayEdge.ANCHORED_UNSELECTED:
            case DisplayEdge.ANCHORED_SELECTED:
                Rectangle r1 = this.node1.getBounds();
                Rectangle r2 = this.node2.getBounds();
                Point c1 = new Point((int) (r1.x + r1.width / 2.0), (int) (r1.y + r1.height / 2.0));
                Point c2 = new Point((int) (r2.x + r2.width / 2.0), (int) (r2.y + r2.height / 2.0));

                double angle = TMath.atan2(c1.y - c2.y, c1.x - c2.x);
                angle += TMath.PI / 2;
                Point d = new Point((int) (this.offset * TMath.cos(angle)), (int) (this.offset * TMath.sin(angle)));

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

    public final Edge getModelEdge() {
        return this.modelEdge;
    }

    public double getOffset() {
        return this.offset;
    }

    @Override
    public void setOffset(double offset) {
        this.offset = offset;
    }

    public Color getLineColor() {
        if (this.highlighted) {
            return getHighlightedColor();
        }

        if (this.lineColor == null) {
            return getDefaultLineColor();
        }

        // A color set explicitly on the edge is usually chosen for light backgrounds; lift it a little in dark mode.
        return isDarkMode() ? this.lineColor.brighter() : this.lineColor;
    }

    @Override
    public void setLineColor(Color lineColor) {
        this.lineColor = lineColor;
    }

    public boolean getSolid() {
        return this.solid;
    }

    @Override
    public void setSolid(boolean solid) {
        this.solid = solid;
    }

    @Override
    public void setThick(boolean thick) {
        this.thick = thick;
    }

    public Color getSelectedColor() {
        return this.selectedColor != null ? this.selectedColor : getDefaultSelectedColor();
    }

    @Override
    public void setSelectedColor(Color selectedColor) {
        this.selectedColor = selectedColor;
    }

    public Color getHighlightedColor() {
        return this.highlightedColor != null ? this.highlightedColor : getDefaultHighlightedColor();
    }

    @Override
    public void setHighlightedColor(Color highlightedColor) {
        this.highlightedColor = highlightedColor;
    }

    public float getStrokeWidth() {
        return this.strokeWidth;
    }

    @Override
    public void setStrokeWidth(float strokeWidth) {
        if (strokeWidth < 0f) {
            throw new IllegalArgumentException("Stroke width must be at least 0.");
        }

        this.strokeWidth = strokeWidth;
    }

    @Override
    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    //======================= Event handler class========================//

    private final class ComponentHandler extends ComponentAdapter {
        @Override
        public void componentMoved(ComponentEvent e) {
            resetBounds();
            repaint();
        }

        @Override
        public void componentResized(ComponentEvent e) {
            resetBounds();
            repaint();
        }
    }

    private final class PropertyChangeHandler implements PropertyChangeListener {
        @Override
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