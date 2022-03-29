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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Ellipse2D;
import java.text.NumberFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A panel that is responsible for drawing a q-q plot.
 * <p>
 * Borrows heavily from HistogramDisplayPanel
 *
 * @author Michael Freenor
 */
class QQPlotDisplayPanel extends JPanel {


    /**
     * The line color around the histogram.
     */
    private static final Color LINE_COLOR = Color.GRAY.darker();


    /**
     * Variables that control the size of the drawing area.
     */
    private static final int PADDING = 50;
    private static final int HEIGHT = 400 + PADDING;
    private static final int WIDTH = 400 + PADDING;
    private static final int SPACE = 2;
    private static final int DASH = 10;


    /**
     * The default size of the component.
     */
    private final Dimension size = new Dimension(WIDTH + 2 * SPACE, HEIGHT);

    /**
     * Format for continuous data.
     */
    private final NumberFormat format = NumberFormatUtil.getInstance().getNumberFormat();


    /**
     * The q-q plot we are displaying.
     */

    private QQPlot qqPlot;

    /**
     * A cached string displaying what is being viewed in the histogram.
     */
    private String displayString;


    /**
     * A map from the rectangles that define the bars, to the number of units in the bar.
     */
    private final Map<Rectangle, Integer> rectMap = new ConcurrentHashMap<>();


    /**
     * Constructs the histogram dipslay panel given the initial histogram to display.
     */
    public QQPlotDisplayPanel(QQPlot qqPlot) {
        this.qqPlot = qqPlot;

        if (qqPlot == null) {
            throw new NullPointerException("Given q-q plot must be null");
        }

        addMouseMotionListener(new MouseMovementListener());
        setToolTipText(" ");
    }

    //============================ PUblic Methods =============================//

    /**
     * Updates the histogram that is dispalyed to the given one.
     */
    public synchronized void updateQQPlot(QQPlot qqPlot) {
        if (qqPlot == null) {
            throw new NullPointerException("The given q-q plot must not be null");
        }
        displayString = null;
        this.qqPlot = qqPlot;
        repaint();
    }


    public String getToolTipText(MouseEvent evt) {
        return null;
    }


    /**
     * Paints the histogram and related items.
     */
    public void paintComponent(Graphics graphics) {
        // set up variables.
        rectMap.clear();
        Graphics2D g2d = (Graphics2D) graphics;
        final int height = HEIGHT - PADDING;
        FontMetrics fontMetrics = g2d.getFontMetrics();
        // draw background/surrounding box.
        g2d.setColor(getBackground());
        g2d.fillRect(0, 0, WIDTH + 2 * SPACE, HEIGHT);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(PADDING, 0, (WIDTH + SPACE) - PADDING, height);

        //border
        g2d.setColor(LINE_COLOR);
        g2d.drawRect(PADDING, 0, (WIDTH + SPACE) - PADDING, height);
        // graw the buttom line
        g2d.setColor(LINE_COLOR);
        g2d.drawString(format.format(Math.floor(qqPlot.getMinSample())), PADDING + 5, height + 15);
        g2d.drawLine(PADDING, height + DASH, PADDING, height);
        String maxStr = format.format((int) Math.ceil(qqPlot.getMaxSample()));
        g2d.drawString(maxStr, WIDTH - fontMetrics.stringWidth(maxStr), height + 15);
        g2d.drawLine(WIDTH + SPACE, height + DASH, WIDTH + SPACE, height);

        // draw the side line
        g2d.setColor(LINE_COLOR);
        final int topY = 0;
        String top = "" + Math.ceil(qqPlot.getMaxSample());
        g2d.drawString(top, PADDING - fontMetrics.stringWidth(top), topY + 10);
        g2d.drawLine(PADDING - DASH, topY, PADDING, topY);
        g2d.drawString(Math.floor(qqPlot.getMinSample()) + "", PADDING - fontMetrics.stringWidth(Math.floor(qqPlot.getMinIdeal()) + ""), height - 2);
        g2d.drawLine(PADDING - DASH, height, PADDING, height);

        //draw the data points
        int dataColumn = qqPlot.getDataSet().getColumn(qqPlot.getSelectedVariable());

        //set selected variable if there is none
        if (dataColumn == -1) {
            for (int i = 0; i < qqPlot.getDataSet().getNumColumns(); i++) {
                if (qqPlot.getDataSet().getVariable(i) instanceof ContinuousVariable) {
                    qqPlot.setSelectedVariable((ContinuousVariable) qqPlot.getDataSet().getVariable(i));
                    dataColumn = i;
                    break;
                }
            }
        }

        g2d.setColor(new Color(255, 0, 0));

        for (int i = 0; i < qqPlot.getDataSet().getNumRows(); i++) {
            double x = (qqPlot.getDataSet().getDouble(i, dataColumn));
            double y = (qqPlot.getComparisonVariable()[i]);

            if (x >= qqPlot.getMinSample() && x <= qqPlot.getMaxSample()
                    && y >= qqPlot.getMinSample() && y <= qqPlot.getMaxSample()) {
                double[] result = this.plotPoint(x, y, Math.floor(qqPlot.getMinSample()), Math.ceil(qqPlot.getMaxSample()));

                g2d.fill(new Ellipse2D.Double(result[0], result[1], 4, 4));
            }
        }

        // draw the display string.
        g2d.setColor(LINE_COLOR);
        g2d.drawString(this.getDisplayString(), PADDING, HEIGHT - 5);
    }

    private String getDisplayString() {
        if (displayString == null) {
            displayString = "Showing: " + qqPlot.getSelectedVariable().getName();
        }
        return displayString;
    }

    private double[] plotPoint(double x, double y, double minRange, double maxRange) {
        double[] result = new double[2];
        double range = maxRange - minRange;
        result[0] = (WIDTH - 50) * ((y - minRange) / range) - 2 + 50;
        result[1] = HEIGHT - (HEIGHT - 50) * ((x - minRange) / range) - 2 - 50;
        //System.out.println("<" + result[0] +  ", " + result[1] + ">");
        return result;
    }


    public Dimension getPreferredSize() {
        return size;
    }


    public Dimension getMaximumSize() {
        return size;
    }


    public Dimension getMinimumSize() {
        return size;
    }

    //============================ Inner class =====================================//

    private class MouseMovementListener implements MouseMotionListener {

        public void mouseDragged(MouseEvent e) {

        }

        public void mouseMoved(MouseEvent e) {
            Point point = e.getPoint();
            for (Rectangle rect : rectMap.keySet()) {
                if (rect.contains(point)) {
                    break;
                }
            }
        }
    }

}



