package edu.cmu.tetradapp.editor;

import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.Point2D;
import java.text.NumberFormat;
import java.util.Vector;

/**
 * This view draws the ScatterPlot using the information from the ScatterPlot
 * class. It draws the ScatterPlot line, axes, labels and the statistical values.
 *
 * @author Adrian Tang
 * @author josephramsey
 */
class ScatterplotPanel extends JPanel {
    private ScatterPlot scatterPlot;

    private final NumberFormat nf;
    private boolean drawAxes = false;

    /**
     * Constructor.
     */
    public ScatterplotPanel(ScatterPlot ScatterPlot) {
        this.scatterPlot = ScatterPlot;

        setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        this.nf = NumberFormat.getNumberInstance();
        this.nf.setMinimumFractionDigits(2);
        this.nf.setMaximumFractionDigits(2);

        setBorder(new LineBorder(Color.BLACK));
    }

    /**
     * Renders the view.
     */
    public void paintComponent(Graphics graphics) {
        double xmin = this.scatterPlot.getXmin();
        double xmax = this.scatterPlot.getXmax();
        double ymin = this.scatterPlot.getYmin();
        double ymax = this.scatterPlot.getYmax();

        Graphics2D g = (Graphics2D) graphics;

        g.setColor(Color.white);
        g.setFont(new Font("Dialog", Font.PLAIN, 11));
        g.fillRect(0, 0, getSize().width, getSize().height);

        int chartWidth = getSize().width;
        int chartHeight = getSize().height;

        final int xStringMin = 10;
        int xMin = drawAxes ? 50 : 0;
        int xMax = drawAxes ? chartWidth - 20 : chartWidth;
        int xRange = xMax - xMin;
        int yMin = drawAxes ? 30 : 0;
        int yMax = drawAxes ? chartHeight - 20 : chartHeight;
        int yRange = yMax - yMin;

        /* draws axis lines */
        if (drawAxes) {
            g.setStroke(new BasicStroke());
            g.setPaint(Color.black);
            g.drawLine(xMin, yMax, xMax, yMax);
            g.drawLine(xMin, yMin, xMin, yMax);

            /* draws the labels for the corresponding experiment and sample names */
            g.setFont(g.getFont().deriveFont(11f));

            /* draws axis labels and scale */
            g.drawString(this.nf.format(ymax), 2 + xStringMin, yMin + 7);
            g.drawString(this.nf.format(ymin), 2 + xStringMin, yMax);
            g.drawString(this.nf.format(xmax), xMax - 20, yMax + 14);
            g.drawString(this.nf.format(xmin), 20 + 30, yMax + 14);
            g.drawString(this.scatterPlot.getXvar(), xMin + (xRange / 2) - 10, yMax + 14);
            g.translate(xMin - 7, yMin + (yRange / 2) + 10);
            g.rotate(-FastMath.PI / 2.0);
            g.drawString(this.scatterPlot.getYvar(), xStringMin, 0);
            g.rotate(FastMath.PI / 2.0);
            g.translate(-(xMin - 7), -(yMin + (yRange / 2) + 10));
        }

        /* draws ScatterPlot of the values */
        Vector<Point2D.Double> pts = this.scatterPlot.getSievedValues();
        double _xRange = xmax - xmin;
        double _yRange = ymax - ymin;
        int x, y;

        g.setColor(Color.RED.darker());
        for (Point2D.Double _pt : pts) {
            x = (int) (((_pt.getX() - xmin) / _xRange) * xRange + xMin);
            y = (int) (((ymax - _pt.getY()) / _yRange) * yRange + yMin);
            g.fillOval(x - 2, y - 2, 5, 5);
        }

        /* draws best-fit line */
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

            g.setColor(Color.DARK_GRAY);
            g.setStroke(new BasicStroke(2));
            g.drawLine(xa, ya, xb, yb);
        }

        /* draws statistical values */
        if (this.scatterPlot.isIncludeLine()) {
            g.setColor(Color.black);
            this.nf.setMinimumFractionDigits(3);
            this.nf.setMaximumFractionDigits(3);
            double r = this.scatterPlot.getCorrelationCoeff();
            double p = this.scatterPlot.getCorrelationPValue();

            if (drawAxes) {
                g.drawString("correlation coef = " + this.nf.format(r)
                        + "  (p=" + this.nf.format(p) + ")", 100, 21);
            }
        }
    }

    public void setDrawAxes(boolean drawAxes) {
        this.drawAxes = drawAxes;
    }
}