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

import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View for the Histogram class. Shows a histogram and gives controls for conditioning, etc.
 *
 * @author josephramsey
 */
public class HistogramPanel extends JPanel {
    public static final String[] tiles = {"1-tile", "2-tile", "tertile", "quartile", "quintile", "sextile",
            "septile", "octile", "nontile", "decile"};
    private static final Color LINE_COLOR = Color.GRAY.darker();
    private final Histogram histogram;
    private final NumberFormat format = new DecimalFormat("0.#");// NumberFormatUtil.getInstance().getNumberFormat();
    private final Map<Rectangle, Integer> rectMap = new LinkedHashMap<>();
    private boolean drawAxes = true;
    private int paddingX;
    private Color barColor = Color.RED.darker();

    /**
     * Constructs the histogram display panel given the initial histogram to display.
     *
     * @param histogram The histogram to display.
     */
    public HistogramPanel(Histogram histogram, boolean drawAxes) {
        this.drawAxes = drawAxes;

        paddingX = drawAxes ? 40 : 5;

        if (histogram == null) {
            throw new NullPointerException("Given histogram must be null");
        }

        this.histogram = histogram;

        this.setToolTipText(" ");
    }

    private static int getMax(int[] freqs) {
        int max = freqs[0];

        for (int i = 1; i < freqs.length; i++) {
            int current = freqs[i];
            if (current > max) {
                max = current;
            }
        }

        return max;
    }

    public String getToolTipText(MouseEvent evt) {

        Point point = evt.getPoint();
        for (Rectangle rect : this.rectMap.keySet()) {
            if (rect.contains(point)) {
                Integer i = this.rectMap.get(rect);
                if (i != null) {
                    return i.toString();
                }

                break;
            }
        }

        return null;
    }

    /**
     * Paints the histogram and related items.
     */
    public void paintComponent(Graphics graphics) {
        int paddingY = drawAxes ? 15 : 5;
        int height = drawAxes ? getHeight() - 10 : getHeight();
        int width = getWidth() - (drawAxes ? paddingX : 2);
        int displayedHeight = (int) (height - paddingY);
        int space = drawAxes ? 2 : 1;
        int dash = 10;

        // Set up variables.
        this.rectMap.clear();
        Graphics2D g2d = (Graphics2D) graphics;
        Histogram histogram = this.getHistogram();
        int[] freqs = histogram.getFrequencies();
        int categories = freqs.length;
        int barWidth = FastMath.max((width - paddingX) / categories, 2) - space;
        int topFreq = HistogramPanel.getMax(freqs);
        double scale = displayedHeight / (double) topFreq;
        FontMetrics fontMetrics = g2d.getFontMetrics();

        // Draw background/surrounding box.
        g2d.setColor(this.getBackground());
        g2d.fillRect(0, 0, width + 2 * space, height);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Draw the histogram
        for (int i = 0; i < categories; i++) {
            int freq = freqs[i];
            int y = (int) FastMath.ceil(scale * freq);
            int x = space * (i + 1) + barWidth * i + paddingX;
            g2d.setColor(barColor);
            Rectangle rect = new Rectangle(x, (height - y - space), barWidth, y);
            g2d.fill(rect);
            this.rectMap.put(rect, freq);
        }

        // Draw border.
        g2d.setColor(HistogramPanel.LINE_COLOR);
        g2d.drawRect(paddingX, 0, width - paddingX, height);
        // draw the buttom line
        g2d.setColor(HistogramPanel.LINE_COLOR);

        Node target = histogram.getTargetNode();

        // Draw axes.
        if (drawAxes) {
            // draw the side line
            g2d.setColor(HistogramPanel.LINE_COLOR);
            int topY = height - (int) FastMath.ceil(scale * topFreq);
            String top = String.valueOf(topFreq);
            g2d.drawString(top, paddingX - fontMetrics.stringWidth(top), topY - 2);
            g2d.drawLine(paddingX - dash, topY, paddingX, topY);
            g2d.drawString("0", paddingX - fontMetrics.stringWidth("0"), height - 2);
            g2d.drawLine(paddingX - dash, height, paddingX, height);
            int hSize = (height - topY) / 4;
            for (int i = 1; i < 4; i++) {
                int topHeight = height - hSize * i;
                g2d.drawLine(paddingX - dash, topHeight, paddingX, topHeight);
            }
        }
    }

    /**
     * The histogram we are displaying.
     */
    public Histogram getHistogram() {
        return this.histogram;
    }

    public void setBarColor(Color barColor) {
        this.barColor = barColor;
    }

    private Map<Integer, Double> pickGoodPointsAndValues(double minValue, double maxValue) {
        double range = maxValue - minValue;
        int powerOfTen = (int) FastMath.floor(FastMath.log(range) / FastMath.log(10));
        Map<Integer, Double> points = new HashMap<>();

        int low = (int) FastMath.floor(minValue / FastMath.pow(10, powerOfTen));
        int high = (int) FastMath.ceil(maxValue / FastMath.pow(10, powerOfTen));

        for (int i = low; i < high; i++) {
            double realValue = i * FastMath.pow(10, powerOfTen);
            Integer intValue = translateToInt(minValue, maxValue, realValue);

            if (intValue == null) {
                continue;
            }

            points.put(intValue, realValue);
        }

        return points;
    }

    private Integer translateToInt(double minValue, double maxValue, double value) {
        if (minValue >= maxValue) {
            throw new IllegalArgumentException();
        }
        if (paddingX >= 332) {
            throw new IllegalArgumentException();
        }

        double ratio = (value - minValue) / (maxValue - minValue);

        int intValue = (int) (FastMath.round(paddingX + ratio * (double) (332 - paddingX)));

        if (intValue < paddingX || intValue > 332) {
            return null;
        }

        return intValue;
    }
}





