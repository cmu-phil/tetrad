package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.TMath;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.Point2D;
import java.text.NumberFormat;
import java.util.Vector;

/**
 * This view draws the ScatterPlot using the information from the ScatterPlot class. It draws the ScatterPlot line,
 * axes, labels and the statistical values.
 *
 * @author Adrian Tang
 * @author josephramsey
 */
class ScatterplotPanel extends JPanel {
    private final NumberFormat nf;
    private final boolean removeZeroPointsPerPlot;
    private final ScatterPlot scatterPlot;
    private boolean drawAxes = false;
    private int pointSize = 5;

    /**
     * <p>Constructor for ScatterplotPanel.</p>
     *
     * @param ScatterPlot a {@link edu.cmu.tetradapp.editor.ScatterPlot} object
     */
    public ScatterplotPanel(ScatterPlot ScatterPlot) {
        this(ScatterPlot, false);
    }

    /**
     * Constructor.
     *
     * @param ScatterPlot             a {@link edu.cmu.tetradapp.editor.ScatterPlot} object
     * @param removeZeroPointsPerPlot a boolean
     */
    public ScatterplotPanel(ScatterPlot ScatterPlot, boolean removeZeroPointsPerPlot) {
        this.scatterPlot = ScatterPlot;
        this.removeZeroPointsPerPlot = removeZeroPointsPerPlot;

        this.nf = NumberFormat.getNumberInstance();
        this.nf.setMinimumFractionDigits(2);
        this.nf.setMaximumFractionDigits(2);

        setOpaque(true);
        refreshTheme();
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Color blend(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round((1.0 - t) * a.getRed() + t * b.getRed());
        int g = (int) Math.round((1.0 - t) * a.getGreen() + t * b.getGreen());
        int bb = (int) Math.round((1.0 - t) * a.getBlue() + t * b.getBlue());
        return new Color(r, g, bb);
    }

    private static Color getPlotBackground() {
        Color c = UIManager.getColor("TextArea.background");
        if (c != null) return c;

        c = UIManager.getColor("Panel.background");
        if (c != null) return c;

        return Color.WHITE;
    }

    private static Color getAxisColor() {
        Color c = UIManager.getColor("Label.foreground");
        if (c != null) return c;

        return Color.BLACK;
    }

    private static Color getBorderColor() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c != null) return c;

        c = UIManager.getColor("Separator.foreground");
        if (c != null) return c;

        c = UIManager.getColor("Label.foreground");
        if (c != null) return blend(c, getPlotBackground(), 0.35);

        return Color.DARK_GRAY;
    }

    private static Color getPointColor() {
        Color c = UIManager.getColor("Table.selectionBackground");
        if (c != null) return c;

        c = UIManager.getColor("Component.focusColor");
        if (c != null) return c;

        return Color.RED.darker();
    }

    private static Color getFitLineColor() {
        Color c = UIManager.getColor("Label.foreground");
        if (c != null) return blend(c, getPlotBackground(), 0.25);

        return Color.DARK_GRAY;
    }

    private void refreshTheme() {
        Color bg = getPlotBackground();
        setBackground(bg);
        setForeground(getAxisColor());
        setBorder(new LineBorder(getBorderColor()));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        refreshTheme();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Renders the view.
     */
    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        double xmin = this.scatterPlot.getXmin();
        double xmax = this.scatterPlot.getXmax();
        double ymin = this.scatterPlot.getYmin();
        double ymax = this.scatterPlot.getYmax();

        Graphics2D g = (Graphics2D) graphics.create();

        try {
            g.setColor(getBackground());
            g.setFont(uiFont("Label.font", new Font("Dialog", Font.PLAIN, 11)));
            g.fillRect(0, 0, getWidth(), getHeight());

            int chartWidth = getWidth();
            int chartHeight = getHeight();

            final int xStringMin = 10;
            int xMin = drawAxes ? 50 : 4;
            int xMax = drawAxes ? chartWidth - 20 : chartWidth - 4;
            int xRange = xMax - xMin;
            int yMin = drawAxes ? 30 : 4;
            int yMax = drawAxes ? chartHeight - 20 : chartHeight - 4;
            int yRange = yMax - yMin;

            // Draw axis lines.
            if (drawAxes) {
                g.setPaint(getAxisColor());
                g.drawLine(xMin, yMax, xMax, yMax);
                g.drawLine(xMin, yMin, xMin, yMax);

                g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));

                g.drawString(this.nf.format(ymax), 2 + xStringMin, yMin + 7);
                g.drawString(this.nf.format(ymin), 2 + xStringMin, yMax);
                g.drawString(this.nf.format(xmax), xMax - 20, yMax + 14);
                g.drawString(this.nf.format(xmin), 20 + 30, yMax + 14);
                g.drawString(this.scatterPlot.getXvar(), xMin + (xRange / 2) - 10, yMax + 14);

                g.translate(xMin - 7, yMin + (yRange / 2) + 10);
                g.rotate(-TMath.PI / 2.0);
                g.drawString(this.scatterPlot.getYvar(), xStringMin, 0);
                g.rotate(TMath.PI / 2.0);
                g.translate(-(xMin - 7), -(yMin + (yRange / 2) + 10));
            }

            // Draw ScatterPlot of the values.
            Vector<Point2D.Double> pts = this.scatterPlot.getSievedValues();
            double _xRange = xmax - xmin;
            double _yRange = ymax - ymin;
            int x, y;

            g.setColor(getPointColor());
            for (Point2D.Double _pt : pts) {
                if (Double.isNaN(_pt.getX()) || Double.isNaN(_pt.getY())) continue;

                if (removeZeroPointsPerPlot) {
                    if (_pt.getX() == 0 || _pt.getY() == 0) continue;
                }

                x = (int) (((_pt.getX() - xmin) / _xRange) * xRange + xMin);
                y = (int) (((ymax - _pt.getY()) / _yRange) * yRange + yMin);
                g.fillOval(x - pointSize / 2, y - pointSize / 2, pointSize, pointSize);
            }

            // draws best-fit line
            if (this.scatterPlot.isIncludeLine()) {
                double a = this.scatterPlot.getRegressionCoeff();
                double b = this.scatterPlot.getRegressionIntercept();

                double x1, y1 = 0;

                for (x1 = xmin; x1 <= xmax; x1 += 0.01) {
                    y1 = a * x1 + b;
                    if (y1 >= ymin && y1 <= ymax) {
                        break;
                    }
                }

                double x2, y2 = 0;

                for (x2 = xmax; x2 >= xmin; x2 -= 0.01) {
                    y2 = a * x2 + b;
                    if (y2 >= ymin && y2 <= ymax) {
                        break;
                    }
                }

                int xa = (int) (((x1 - xmin) / _xRange) * xRange + xMin);
                int ya = (int) (((ymax - y1) / _yRange) * yRange + yMin);

                int xb = (int) (((x2 - xmin) / _xRange) * xRange + xMin);
                int yb = (int) (((ymax - y2) / _yRange) * yRange + yMin);

                g.setColor(getFitLineColor());
                g.setStroke(new BasicStroke(2));
                g.drawLine(xa, ya, xb, yb);
            }

            // draws statistical values
            if (this.scatterPlot.isIncludeLine()) {
                g.setColor(getAxisColor());
                this.nf.setMinimumFractionDigits(3);
                this.nf.setMaximumFractionDigits(3);
                double r = this.scatterPlot.getCorrelationCoeff();
                double p = this.scatterPlot.getCorrelationPValue();

                if (drawAxes) {
                    g.setStroke(new BasicStroke(3));
                    g.drawString(
                            "Correlation coef = " + this.nf.format(r) + "  (p=" + this.nf.format(p) + ")",
                            70, 21
                    );
                }
            }
        } finally {
            g.dispose();
        }
    }

    private static Font uiFont(String key, Font fallback) {
        Font f = UIManager.getFont(key);
        return f != null ? f : fallback;
    }

    /**
     * <p>Setter for the field <code>drawAxes</code>.</p>
     *
     * @param drawAxes a boolean
     */
    public void setDrawAxes(boolean drawAxes) {
        this.drawAxes = drawAxes;
        repaint();
    }

    /**
     * <p>Setter for the field <code>pointSize</code>.</p>
     *
     * @param pointSize a int
     */
    public void setPointSize(int pointSize) {
        this.pointSize = pointSize;
        repaint();
    }
}