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
 *
 * Borrows heavily from HistogramDisplayPanel
 *
 * @author Michael Freenor
 */
public class QQPlotDisplayPanel extends JPanel {


    /**
     * The line color around the histogram.
     */
    private static Color LINE_COLOR = Color.GRAY.darker();


    /**
     * Variables that control the size of the drawing area.
     */
    private final static int PADDING = 50;
    private final static int HEIGHT = 600 + PADDING;
    private final static int DISPLAYED_HEIGHT = HEIGHT;
    private final static int WIDTH = 600 + PADDING;
    private final static int SPACE = 2;
    private final static int DASH = 10;


    /**
     * The default size of the component.
     */
    private Dimension size = new Dimension(WIDTH + 2 * SPACE, HEIGHT);

    /**
     * Format for continuous data.
     */
    private NumberFormat format = NumberFormatUtil.getInstance().getNumberFormat();


    /**
     * The q-q plot we are displaying.
     */

    private QQPlot qqPlot;

    /**
     * A cached string displaying what is being viewed in the histogram.
     */
    private String displayString;


    /**
     * A cache value that stores the top frequency.
     */
    private int topFreq = -1;

    /**
     * A map from the rectangles that define the bars, to the number of units in the bar.
     */
    private Map<Rectangle, Integer> rectMap = new ConcurrentHashMap<Rectangle, Integer>();


    /**
     * Constructs the histogram dipslay panel given the initial histogram to display.
     */
    public QQPlotDisplayPanel(QQPlot qqPlot) {
        this.qqPlot = qqPlot;
        
        if (qqPlot == null) {
            throw new NullPointerException("Given q-q plot must be null");
        }

        this.addMouseMotionListener(new MouseMovementListener());
        this.setToolTipText(" ");
    }

    //============================ PUblic Methods =============================//

    /**
     * Updates the histogram that is dispalyed to the given one.
     */
    public synchronized void updateQQPlot(QQPlot qqPlot) {
        if (qqPlot == null) {
            throw new NullPointerException("The given q-q plot must not be null");
        }
        this.displayString = null;
        this.qqPlot = qqPlot;
        this.topFreq = -1;
        this.repaint();
    }


    public String getToolTipText(MouseEvent evt) {
        return null;
    }


    /**
     * Paints the histogram and related items.
     */
    public void paintComponent(Graphics graphics) {
        // set up variables.
        this.rectMap.clear();
        Graphics2D g2d = (Graphics2D) graphics;
        int height = HEIGHT - PADDING;
        FontMetrics fontMetrics = g2d.getFontMetrics();
        // draw background/surrounding box.
        g2d.setColor(this.getBackground());
        g2d.fillRect(0, 0, WIDTH + 2 * SPACE, HEIGHT);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(PADDING, 0, (WIDTH + SPACE) - PADDING, height);

        //border
        g2d.setColor(LINE_COLOR);
        g2d.drawRect(PADDING, 0, (WIDTH + SPACE) - PADDING, height);
        // graw the buttom line
        g2d.setColor(LINE_COLOR);
        g2d.drawString(format.format(Math.floor(this.qqPlot.getMinSample())), PADDING + 5, height + 15);
        g2d.drawLine(PADDING, height + DASH, PADDING, height);
        String maxStr = format.format((int)Math.ceil(this.qqPlot.getMaxSample()));
        g2d.drawString(maxStr, WIDTH - fontMetrics.stringWidth(maxStr), height + 15);
        g2d.drawLine(WIDTH + SPACE, height + DASH, WIDTH + SPACE, height);
        int size = (WIDTH - PADDING) / 4;

        // draw the side line
        g2d.setColor(LINE_COLOR);
        int topY = 0;
        String top = "" + Math.ceil(this.qqPlot.getMaxSample());
        g2d.drawString(top, PADDING - fontMetrics.stringWidth(top), topY + 10);
        g2d.drawLine(PADDING - DASH, topY, PADDING, topY);
        g2d.drawString(Math.floor(this.qqPlot.getMinSample()) + "", PADDING - fontMetrics.stringWidth(Math.floor(this.qqPlot.getMinIdeal()) + ""), height - 2);
        g2d.drawLine(PADDING - DASH, height, PADDING, height);

        //draw the y=x line
        double idealRange = this.qqPlot.getMaxIdeal() - this.qqPlot.getMinIdeal();
        double sampleRange = this.qqPlot.getMaxSample() - this.qqPlot.getMinSample();

        double idealPerSample = idealRange / sampleRange;

        double intersectPoint = this.qqPlot.getMaxSample() * idealPerSample;

//        g2d.drawLine(PADDING, height, WIDTH + SPACE, height - ((height - topY)));

        //draw the data points
        int dataColumn = this.qqPlot.getDataSet().getColumn(this.qqPlot.getSelectedVariable());
        //set selected variable if there is none
        if (dataColumn == -1)
        {
            for (int i = 0; i < this.qqPlot.getDataSet().getNumColumns(); i++)
            {
                if (this.qqPlot.getDataSet().getVariable(i) instanceof ContinuousVariable)
                {
                    this.qqPlot.setSelectedVariable((ContinuousVariable)this.qqPlot.getDataSet().getVariable(i));
                    dataColumn = i;
                    break;
                }
            }
        }
        //int compColumn = this.qqPlot.getSelectedDataModel().getColumn(this.qqPlot.getComparisonVariable());
        //System.out.println(compColumn);
        g2d.setColor(new Color(255, 0, 0));
        //g2d.fill(new Ellipse2D.Double(0-2 + PADDING, 0-2 + height, 4, 4));

        double originX = PADDING - 2;
        double originY = height - 2;

        for (int i = 0; i < this.qqPlot.getDataSet().getNumRows(); i++)
        {
            double x = (this.qqPlot.getDataSet().getDouble(i, dataColumn));
            double y = (this.qqPlot.getComparisonVariable()[i]);

            if (x >= this.qqPlot.getMinSample() && x <= this.qqPlot.getMaxSample()
                    && y >= this.qqPlot.getMinSample() && y <= this.qqPlot.getMaxSample())
            {
                double result[] = plotPoint(x, y, Math.floor(this.qqPlot.getMinSample()), Math.ceil(this.qqPlot.getMaxSample()));

                //System.out.println("(" + x + "," + y +  ")");

                g2d.fill(new Ellipse2D.Double(result[0], result[1], 4, 4));
            }
        }

        // draw the display string.
        g2d.setColor(LINE_COLOR);
        g2d.drawString(getDisplayString(), PADDING, HEIGHT - 5);
    }

    private String getDisplayString() {
        if (this.displayString == null) {
            this.displayString = "Showing: " + qqPlot.getSelectedVariable().getName();
        }
        return this.displayString;
    }

    public double[] plotPoint(double x, double y, double minRange, double maxRange) {
        double[] result = new double[2];
        double range = maxRange - minRange;
        result[0] = (WIDTH - 50) * ((y - minRange) / range) - 2 + 50;
        result[1] = HEIGHT - (HEIGHT - 50) * ((x - minRange) / range) - 2 - 50;
        //System.out.println("<" + result[0] +  ", " + result[1] + ">");
        return result;
    }


    public Dimension getPreferredSize() {
        return this.size;
    }


    public Dimension getMaximumSize() {
        return this.size;
    }


    public Dimension getMinimumSize() {
        return this.size;
    }

    //========================== private methods ==========================//

    private static int getMax(int[] freqs) {
        int max = freqs[0];
        for (int i = 1; i < freqs.length; i++) {
            int current = freqs[i];
            if (max < current) {
                max = current;
            }
        }
        return max;
    }

    //============================ Inner class =====================================//

    private class MouseMovementListener implements MouseMotionListener {

        public void mouseDragged(MouseEvent e) {

        }

        public void mouseMoved(MouseEvent e) {
            Point point = e.getPoint();
            for (Rectangle rect : rectMap.keySet()) {
                if (rect.contains(point)) {
                  //  System.out.println(rectMap.get(rect));
                    break;
                }
            }
        }
    }

}



