package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.PointPair;

import javax.swing.*;
import java.awt.*;

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
        return laf != null && laf.getName().toLowerCase().contains("dark");
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
    private Point[] getDiceArea() {
        int[] xpoint = new int[4];
        int[] ypoint = new int[4];
        PointPair pp = getConnectedPoints();

        Point midPoint = new Point((pp.getFrom().x + pp.getTo().x) / 2,
                (pp.getFrom().y + pp.getTo().y) / 2);
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
        return SessionEditorEdge.calcDiceSleeve(getDiceArea());
    }

    /**
     * Paints the component.
     */
    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        try {
            Stroke solid = new BasicStroke(2.5f);
            g2d.setStroke(solid);

            PointPair pp;

            switch (getMode()) {
                case DisplayEdge.HALF_ANCHORED:
                    g2d.setColor(getLineColor());
                    pp = calculateEdge(getNode1(), getRelativeMouseTrackPoint());

                    if (pp != null) {
                        pp.getFrom().translate(-getLocation().x, -getLocation().y);
                        pp.getTo().translate(-getLocation().x, -getLocation().y);

                        setClickRegion(null);

                        g2d.drawLine(pp.getFrom().x, pp.getFrom().y, pp.getTo().x, pp.getTo().y);
                        drawEndpoints(pp, g2d);
                        firePropertyChange("newPointPair", null, pp);
                    }
                    break;

                case DisplayEdge.ANCHORED_UNSELECTED:
                    g2d.setColor(getLineColor());
                    pp = calculateEdge(getNode1(), getNode2());

                    if (pp != null) {
                        pp.getFrom().translate(-getLocation().x, -getLocation().y);
                        pp.getTo().translate(-getLocation().x, -getLocation().y);

                        setClickRegion(null);

                        g2d.drawLine(pp.getFrom().x, pp.getFrom().y, pp.getTo().x, pp.getTo().y);
                        drawEndpoints(pp, g2d);
                        firePropertyChange("newPointPair", null, pp);
                    }
                    break;

                case DisplayEdge.ANCHORED_SELECTED:
                    g2d.setColor(getSelectedColor());
                    pp = calculateEdge(getNode1(), getNode2());

                    if (pp != null) {
                        pp.getFrom().translate(-getLocation().x, -getLocation().y);
                        pp.getTo().translate(-getLocation().x, -getLocation().y);

                        setClickRegion(null);

                        g2d.drawLine(pp.getFrom().x, pp.getFrom().y, pp.getTo().x, pp.getTo().y);
                        drawEndpoints(pp, g2d);
                        firePropertyChange("newPointPair", null, pp);
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

    /**
     * Holds the radius and center of a circle.
     */
    private static final class Circle {
        public final int radius;
        public final Point center;

        public Circle(Point c, int r) {
            this.radius = r;
            this.center = c;
        }
    }
}