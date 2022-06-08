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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Ellipse2D;
import java.text.NumberFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A panel that is responsible for drawing a scatter plot.
 * <p>
 * Borrows heavily from HistogramDisplayPanel
 *
 * @author Michael Freenor
 */
class ScatterPlotDisplayPanel extends JPanel {


    /**
     * The line color around the histogram.
     */
    private static final Color LINE_COLOR = Color.GRAY.darker();

    /**
     * Variables that control the size of the drawing area.
     */
    private int PADDINGLEFT = 75;
    private int PADDINGOTHER = 50;
    private int HEIGHT = 600 + this.PADDINGOTHER;
    private int WIDTH = 600 + this.PADDINGLEFT;
    private int SPACE = 2;


    /**
     * The default size of the component.
     */
    private final Dimension size = new Dimension(this.WIDTH + 2 * this.SPACE, this.HEIGHT);

    /**
     * Format for continuous data.
     */
    private final NumberFormat format = NumberFormatUtil.getInstance().getNumberFormat();


    /**
     * The q-q plot we are displaying.
     */

    private ScatterPlotOld scatterPlot;

    /**
     * A map from the rectangles that define the bars, to the number of units in the bar.
     */
    private final Map<Rectangle, Integer> rectMap = new ConcurrentHashMap<>();


    /**
     * Constructs the scatterplot dipslay panel given the initial scatterplot to display.
     */
    public ScatterPlotDisplayPanel(ScatterPlotOld scatterPlot) {
        this.scatterPlot = scatterPlot;

        if (scatterPlot == null) {
            throw new NullPointerException("Given scatter plot must be null");
        }

        this.addMouseMotionListener(new MouseMovementListener());
        this.setToolTipText(" ");
    }

    //============================ PUblic Methods =============================//

    /**
     * Updates the histogram that is dispalyed to the given one.
     */
    public synchronized void updateScatterPlot(ScatterPlotOld scatterPlot) {
        if (scatterPlot == null) {
            throw new NullPointerException("The given scatter plot must not be null");
        }

        this.scatterPlot = scatterPlot;
        this.repaint();
    }


    public String getToolTipText(MouseEvent evt) {
        return null;
    }


    /**
     * Paints the histogram and related items.
     */
    public void paintComponent(Graphics graphics) {

        double least = this.scatterPlot.getMinSample();
        double greatest = this.scatterPlot.getMaxSample();

        String minStr = this.format.format(least);
        String maxStr = this.format.format(greatest);

        Graphics2D g2d = (Graphics2D) graphics;
        FontMetrics fontMetrics = g2d.getFontMetrics();

        int widthMinStr = fontMetrics.stringWidth(minStr);
        int widthMaxStr = fontMetrics.stringWidth(maxStr);

        this.PADDINGLEFT = Math.max(widthMinStr, widthMaxStr);
        this.PADDINGOTHER = 50;
        this.HEIGHT = 600 + this.PADDINGOTHER;
        this.WIDTH = 600 + this.PADDINGLEFT;
        this.SPACE = 2;
        int DASH = 10;

        setSize(new Dimension(this.WIDTH + 2 * this.SPACE, this.HEIGHT));

        // set up variables.
        this.rectMap.clear();
        int height = this.HEIGHT - this.PADDINGOTHER;

        // draw background/surrounding box.
        g2d.setColor(this.getBackground());
        g2d.fillRect(0, 0, this.WIDTH + 2 * this.SPACE, this.HEIGHT);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(this.PADDINGLEFT, 0, (this.WIDTH + this.SPACE) - this.PADDINGLEFT, height);

        //border
        g2d.setColor(ScatterPlotDisplayPanel.LINE_COLOR);
        g2d.drawRect(this.PADDINGLEFT, 0, (this.WIDTH + this.SPACE) - this.PADDINGLEFT, height - 2 * this.SPACE);

        // draw the buttom line
        g2d.setColor(ScatterPlotDisplayPanel.LINE_COLOR);

        g2d.drawString(minStr, this.PADDINGLEFT + 5, height + 15);
        g2d.drawLine(this.PADDINGLEFT, height + DASH, this.PADDINGOTHER, height);
        g2d.drawString(maxStr, this.WIDTH - widthMaxStr, height + 15);
        g2d.drawLine(this.WIDTH + this.SPACE, height + DASH, this.WIDTH + this.SPACE, height);

        // draw the side line
        g2d.setColor(ScatterPlotDisplayPanel.LINE_COLOR);
        final int topY = 0;
        g2d.drawString(maxStr, this.PADDINGLEFT - fontMetrics.stringWidth(maxStr), topY + 10);
        g2d.drawLine(this.PADDINGLEFT - DASH, topY, this.PADDINGOTHER, topY);
        g2d.drawString(minStr, this.PADDINGLEFT - fontMetrics.stringWidth(minStr), height - 2);
        g2d.drawLine(this.PADDINGLEFT - DASH, height, this.PADDINGOTHER, height);

        //draw the origin lines if they should go on the screen -- first find out where they exist
        if (this.scatterPlot.getMinSample() < 0 && this.scatterPlot.getMaxSample() > 0) {
            double[] originLeft = plotPoint(least, 0, least, greatest);
            double[] originRight = plotPoint(greatest, 0, least, greatest);
            double[] originTop = plotPoint(0, least, least, greatest);
            double[] originBottom = plotPoint(0, greatest, least, greatest);

            g2d.drawLine((int) originLeft[0] + 2, (int) originLeft[1] + 2, (int) originRight[0] + 2, (int) originRight[1] + 2);
            g2d.drawLine((int) originTop[0] + 2, (int) originTop[1] + 2, (int) originBottom[0] + 2, (int) originBottom[1] + 2);
        }

        g2d.setColor(new Color(255, 0, 0));

        //draw each point in the indexSet from our ScatterPlot
        for (Object o : this.scatterPlot.getIndexSet()) {
            int i = (Integer) o;
            double x = this.scatterPlot.getxData()[i];
            double y = this.scatterPlot.getyData()[i];

            double[] result = plotPoint(x, y, least, greatest);
            g2d.fill(new Ellipse2D.Double(result[0], result[1], 4, 4));
        }

        //draw the regression line
        if (this.scatterPlot.isDrawRegLine()) {
            //RegressionRunner regRunner;
            RegressionDataset regData;

            /*
             * In the following code, the complement of the indexSet (for all integers <= (n - 1)) is
             * calculated.  This set of indices is removed from a copy of our original dataSet, such
             * that the ScatterPlot only contains the appropriate points for rendering.
             */

            Parameters params = new Parameters();
            params.set("targetName", this.scatterPlot.getYVariable().getName());

            if (this.scatterPlot.getIndexSet().size() != this.scatterPlot.getDataSet().getNumRows()) {
                DataSet newDataSet = this.scatterPlot.getDataSet().copy();

                int[] throwAway = new int[this.scatterPlot.getComplementIndexSet().size()];
                for (int j = 0; j < throwAway.length; j++) {
                    throwAway[j] = (Integer) this.scatterPlot.getComplementIndexSet().get(j);
                }

                newDataSet.removeRows(throwAway);
                regData = new RegressionDataset(newDataSet);
                //regRunner = new RegressionRunner(new DataWrapper(newDataSet), params);
            } else {
                regData = new RegressionDataset(this.scatterPlot.getDataSet());
                //regRunner = new RegressionRunner(new DataWrapper(scatterPlot.dataSet), params);
            }

            //regRunner.execute();
            RegressionResult regResult = regData.regress(this.scatterPlot.getYVariable(), this.scatterPlot.getXVariable());
            double[] coef = regResult.getCoef();

            double[] regLeft = plotPoint(least, coef[0] + coef[1] * least, least, greatest);
            double[] regRight = plotPoint(greatest, coef[0] + coef[1] * greatest, least, greatest);
            g2d.setColor(ScatterPlotDisplayPanel.LINE_COLOR);
            g2d.drawLine((int) regLeft[0] + 2, (int) regLeft[1] + 2, (int) regRight[0] + 2, (int) regRight[1] + 2);
        }

        // draw the display string.
        g2d.setColor(ScatterPlotDisplayPanel.LINE_COLOR);
    }

    /**
     * @param x        Location along the X axis.
     * @param y        Location along the Y axis (Cartesian coordinates, not Java2D).
     * @param minRange The value at the origin.
     * @param maxRange The value at the extremity (determined by the largest value encountered in either variable).
     * @return An ordered pair determining the proper location on the screen in Java2D coordinates.
     */
    private double[] plotPoint(double x, double y, double minRange, double maxRange) {
        double[] result = new double[2];
        double range = maxRange - minRange;
        result[0] = (this.WIDTH - this.PADDINGLEFT) * ((x - minRange) / range) + 4 + this.PADDINGLEFT;
        result[1] = this.HEIGHT - (this.HEIGHT - this.PADDINGOTHER) * ((y - minRange) / range) - 8 - this.PADDINGOTHER;

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

    //============================ Inner class =====================================//

    private class MouseMovementListener implements MouseMotionListener {

        public void mouseDragged(MouseEvent e) {

        }

        public void mouseMoved(MouseEvent e) {
            Point point = e.getPoint();
            for (Rectangle rect : ScatterPlotDisplayPanel.this.rectMap.keySet()) {
                if (rect.contains(point)) {
                    break;
                }
            }
        }
    }
}




