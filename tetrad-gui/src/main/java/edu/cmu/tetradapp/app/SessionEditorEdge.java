package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.PointPair;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;

/**
 * Presents an edge in the Tetrad SessionWorkbench.
 *
 * @author josephramsey
 */
final class SessionEditorEdge extends DisplayEdge {

    /* Modes */
    public static final int UNRANDOMIZED = 0;
    private static final int RANDOMIZED = 1;

    private int sessionEdgeMode;

    /**
     * Constructs a new SessionEditorEdge connecting two components, 'node1' and 'node2'.
     *
     * @param node1 the 'from' component
     * @param node2 the 'to' component
     * @param sessionEdgeMode the mode of the edge, either UNRANDOMIZED or RANDOMIZED
     */
    public SessionEditorEdge(SessionEditorNode node1, SessionEditorNode node2,
                             int sessionEdgeMode) {
        super(node1, node2, DisplayEdge.SESSION);

        if ((sessionEdgeMode >= 0) && (sessionEdgeMode <= 1)) {
            this.sessionEdgeMode = sessionEdgeMode;
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Constructs a new unanchored session edge.
     *
     * @param node1 the 'from' component
     * @param mouseTrackPoint the initial mouse track point
     */
    public SessionEditorEdge(SessionEditorNode node1, Point mouseTrackPoint) {
        super(node1, mouseTrackPoint, DisplayEdge.SESSION);
    }

    /**
     * Constructs a new unanchored session edge.
     *
     * @param node1 the 'from' component
     * @param mouseTrackPoint the initial mouse track point
     * @param mode edge mode
     */
    public SessionEditorEdge(SessionEditorNode node1, Point mouseTrackPoint,
                             int mode) {
        super(node1, mouseTrackPoint, DisplayEdge.SESSION);
        this.sessionEdgeMode = mode;
    }

    // ============================================================
    // Theme helpers
    // ============================================================

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static boolean isDarkMode() {
        LookAndFeel laf = UIManager.getLookAndFeel();
        return laf != null && laf.getName().toLowerCase().contains("dar");
    }

    private static Color blend(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round((1.0 - t) * a.getRed() + t * b.getRed());
        int g = (int) Math.round((1.0 - t) * a.getGreen() + t * b.getGreen());
        int b2 = (int) Math.round((1.0 - t) * a.getBlue() + t * b.getBlue());
        return new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b2))
        );
    }

    private static Color getDieFillColor() {
        Color c = UIManager.getColor("Table.selectionBackground");
        if (c != null) {
            // Slightly soften it so it reads as a marker, not a selected component.
            return isDarkMode() ? blend(c, Color.GRAY, 0.15) : blend(c, Color.WHITE, 0.10);
        }

        c = UIManager.getColor("Component.focusColor");
        if (c != null) return c;

        return isDarkMode() ? new Color(200, 90, 90) : Color.RED;
    }

    private static Color getDieStrokeColor() {
        Color c = UIManager.getColor("Label.foreground");
        if (c != null) return c;

        c = UIManager.getColor("Component.borderColor");
        if (c != null) return c;

        return isDarkMode() ? new Color(220, 220, 220) : Color.BLACK;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        repaint();
    }

    // ============================================================
    // Dice geometry
    // ============================================================

    /**
     * Calculates the sleeve of the die.
     *
     * @param dice the four points defining the die
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

    private void drawDice(Graphics2D g2, Color fillColor) {
        Polygon dice = getDiceSleeve();
        Circle[] dicedot = getDiceDot();

        g2.setColor(fillColor);
        g2.fillPolygon(dice);

        g2.setColor(getDieStrokeColor());
        g2.drawPolygon(dice);

        int height = dicedot[0].radius * 2;

        for (Circle aDicedot : dicedot) {
            g2.fillOval(aDicedot.center.x, aDicedot.center.y, height, height);
        }
    }

    /**
     * Calculates the four corners of the die.
     *
     * @return the array of points
     */
    /**
     * @return where the die goes: the middle of the curve if this edge was last drawn as one, and otherwise the
     * middle of the straight line between its connected points. (At its middle a curve runs parallel to that line,
     * so the die is turned the same way in both cases.)
     */
    private Point getDiceCenter() {
        if (this.curveMiddle != null) {
            return new Point(this.curveMiddle);
        }

        PointPair pp = getConnectedPoints();
        return new Point((pp.getFrom().x + pp.getTo().x) / 2, (pp.getFrom().y + pp.getTo().y) / 2);
    }

    private Point[] getDiceArea() {
        int[] xpoint = new int[4];
        int[] ypoint = new int[4];
        PointPair pp = getConnectedPoints();

        Point midPoint = getDiceCenter();
        double d = DisplayEdge.distance(pp.getFrom(), pp.getTo());

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
        Point midPoint = getDiceCenter();
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
        return SessionEditorEdge.calcDiceSleeve(getDiceArea());
    }

    /**
     * Paints the component.
     */
    @Override
    public void paint(Graphics g) {
        if (checkCurveBounds()) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            PointPair pp;
            this.curveMiddle = null;

            // As for the edges of a graph, an edge between two boxes is drawn straight unless it has to bend around
            // a third box, in which case it is drawn as a curve; see DisplayEdge.bezierCurve. An edge still being
            // dragged out stays straight.
            switch (getMode()) {
                case DisplayEdge.HALF_ANCHORED:
                    pp = calculateEdge(getNode1(), getRelativeMouseTrackPoint());
                    drawSessionEdge(g2d, pp, getLineColor());
                    break;

                case DisplayEdge.ANCHORED_UNSELECTED:
                    pp = drawSessionCurve(g2d, getLineColor());

                    if (pp == null) {
                        pp = calculateEdge(getNode1(), getNode2());
                        drawSessionEdge(g2d, pp, getLineColor());
                    }

                    break;

                case DisplayEdge.ANCHORED_SELECTED:
                    pp = drawSessionCurve(g2d, getSelectedColor());

                    if (pp == null) {
                        pp = calculateEdge(getNode1(), getNode2());
                        drawSessionEdge(g2d, pp, getSelectedColor());
                    }

                    break;

                default:
                    throw new IllegalStateException();
            }

            setConnectedPoints(pp);

            if (this.sessionEdgeMode == SessionEditorEdge.RANDOMIZED && pp != null) {
                drawDice(g2d, getDieFillColor());
            }
        } finally {
            g2d.dispose();
        }
    }

    // ============================================================
    // Session-edge rendering
    // ============================================================

    /**
     * Stroke width of the session edge shaft, in pixels. Deliberately much
     * heavier than workbench edges so session edges (dataflow) cannot be
     * mistaken for causal or Markov edges.
     */
    /**
     * The middle of the curve this edge was last drawn as, in this component's coordinates, or null if it was last
     * drawn straight.
     */
    private transient Point curveMiddle;

    private static final float SHAFT_WIDTH = 5f;

    /**
     * Length of the arrowhead along the edge, in pixels.
     */
    private static final float HEAD_LENGTH = 16f;

    /**
     * Half the width of the arrowhead base, in pixels.
     */
    private static final float HEAD_HALF_WIDTH = 8f;

    /**
     * Draws a session edge as a single thick solid line with a filled
     * arrowhead scaled to match.
     *
     * @param g2d   the graphics to draw on
     * @param pp    the connected points, in parent coordinates; may be null
     * @param color the color for shaft and head
     */
    private void drawSessionEdge(Graphics2D g2d, PointPair pp, Color color) {
        if (pp == null) {
            return;
        }

        pp.getFrom().translate(-getLocation().x, -getLocation().y);
        pp.getTo().translate(-getLocation().x, -getLocation().y);

        setClickRegion(null);

        double dx = pp.getTo().x - pp.getFrom().x;
        double dy = pp.getTo().y - pp.getFrom().y;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) {
            firePropertyChange("newPointPair", null, pp);
            return;
        }

        double ux = dx / len;
        double uy = dy / len;

        // The shaft stops at the base of the arrowhead so the round cap
        // does not bulge past the head.
        double baseX = pp.getTo().x - ux * HEAD_LENGTH;
        double baseY = pp.getTo().y - uy * HEAD_LENGTH;

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(SHAFT_WIDTH, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g2d.draw(new Line2D.Double(pp.getFrom().x, pp.getFrom().y,
                baseX, baseY));

        // Filled arrowhead, base corners offset along the perpendicular
        // unit vector (-uy, ux).
        Path2D.Double head = new Path2D.Double();
        head.moveTo(pp.getTo().x, pp.getTo().y);
        head.lineTo(baseX - uy * HEAD_HALF_WIDTH, baseY + ux * HEAD_HALF_WIDTH);
        head.lineTo(baseX + uy * HEAD_HALF_WIDTH, baseY - ux * HEAD_HALF_WIDTH);
        head.closePath();
        g2d.fill(head);

        firePropertyChange("newPointPair", null, pp);
    }

    /**
     * Holds the radius and center of a circle.
     */
    /**
     * Draws this edge as a curve, if it has to bend around a box: a thick shaft along the curve, stopping at the base
     * of a filled arrowhead that points along the curve's direction where it arrives.
     *
     * @return the ends of the curve, in this component's coordinates; or null if the edge is to be drawn straight
     * instead, in which case nothing has been drawn or changed.
     */
    private PointPair drawSessionCurve(Graphics2D g2d, Color color) {
        QuadCurve2D.Double curve = bezierCurve();

        if (curve == null) {
            return null;
        }

        // The point of the curve at parameter t, and its distance from the tip.
        double tipX = curve.x2, tipY = curve.y2;

        if (Math.hypot(tipX - curve.x1, tipY - curve.y1) <= HEAD_LENGTH) {
            return null;
        }

        // Where the arrowhead's base sits: the last point of the curve that is the head's length from the tip.
        double lo = 0.0, hi = 1.0;

        for (int i = 0; i < 24; i++) {
            double t = (lo + hi) / 2.0, s = 1.0 - t;
            double x = s * s * curve.x1 + 2.0 * s * t * curve.ctrlx + t * t * curve.x2;
            double y = s * s * curve.y1 + 2.0 * s * t * curve.ctrly + t * t * curve.y2;

            if (Math.hypot(tipX - x, tipY - y) > HEAD_LENGTH) {
                lo = t;
            } else {
                hi = t;
            }
        }

        double tb = lo, sb = 1.0 - tb;
        double baseX = sb * sb * curve.x1 + 2.0 * sb * tb * curve.ctrlx + tb * tb * curve.x2;
        double baseY = sb * sb * curve.y1 + 2.0 * sb * tb * curve.ctrly + tb * tb * curve.y2;

        double len = Math.hypot(tipX - baseX, tipY - baseY);

        if (len < 1e-6) {
            return null;
        }

        double ux = (tipX - baseX) / len;
        double uy = (tipY - baseY) / len;

        // The curve from its start to the base of the head; its control point lies that fraction of the way from
        // the start to the whole curve's control point.
        QuadCurve2D.Double shaft = new QuadCurve2D.Double(curve.x1, curve.y1,
                sb * curve.x1 + tb * curve.ctrlx, sb * curve.y1 + tb * curve.ctrly, baseX, baseY);

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(SHAFT_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(shaft);

        Path2D.Double head = new Path2D.Double();
        head.moveTo(tipX, tipY);
        head.lineTo(baseX - uy * HEAD_HALF_WIDTH, baseY + ux * HEAD_HALF_WIDTH);
        head.lineTo(baseX + uy * HEAD_HALF_WIDTH, baseY - ux * HEAD_HALF_WIDTH);
        head.closePath();
        g2d.fill(head);

        setClickRegion(new BasicStroke(SHAFT_WIDTH + 12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(curve));

        this.curveMiddle = new Point((int) Math.round(0.25 * curve.x1 + 0.5 * curve.ctrlx + 0.25 * curve.x2),
                (int) Math.round(0.25 * curve.y1 + 0.5 * curve.ctrly + 0.25 * curve.y2));

        PointPair pp = new PointPair(new Point((int) Math.round(curve.x1), (int) Math.round(curve.y1)),
                new Point((int) Math.round(tipX), (int) Math.round(tipY)));

        firePropertyChange("newPointPair", null, pp);
        return pp;
    }

    private static final class Circle {
        public final int radius;
        public final Point center;

        public Circle(Point c, int r) {
            this.radius = r;
            this.center = c;
        }
    }
}