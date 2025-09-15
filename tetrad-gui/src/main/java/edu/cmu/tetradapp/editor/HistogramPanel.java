/// ////////////////////////////////////////////////////////////////////////////
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
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View for the Histogram class. Shows a histogram and gives controls for conditioning, etc.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HistogramPanel extends JPanel {
    /**
     * Constant <code>tiles</code>
     */
    public static final String[] tiles = {"1-tile", "2-tile", "tertile", "quartile", "quintile", "sextile",
            "septile", "octile", "nontile", "decile"};
    private static final Color LINE_COLOR = Color.GRAY.darker();

    /**
     * The histogram to display.
     */
    private final Histogram histogram;

    /**
     * The rectangles in the histogram.
     */
    private final Map<Rectangle, Integer> rectMap = new LinkedHashMap<>();

    /**
     * Whether to draw the axes.
     */
    private final boolean drawAxes;

    /**
     * The padding for the x-axis.
     */
    private final int paddingX;

    /**
     * The color of the bars.
     */
    private Color barColor = Color.RED.darker();

    /**
     * Constructs the histogram display panel given the initial histogram to display.
     *
     * @param histogram The histogram to display.
     * @param drawAxes  a boolean
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

    /**
     * {@inheritDoc}
     */
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
     * This method is responsible for painting the histogram display on a graphical component.
     *
     * @param graphics the Graphics object to be used for drawing
     */
    public void paintComponent(Graphics graphics) {
        int paddingY = drawAxes ? 15 : 5;
        int height = getHeight() - 2;
        int width = getWidth() - (drawAxes ? 4 : 2);
        int displayedHeight = height - paddingY;
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

        // Draw axes.
        if (drawAxes) {
            // draw the side line
            g2d.setColor(HistogramPanel.LINE_COLOR);
            int topY = height - (int) FastMath.ceil(scale * topFreq) + 1;
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
     *
     * @return a {@link edu.cmu.tetrad.data.Histogram} object
     */
    public Histogram getHistogram() {
        return this.histogram;
    }

    /**
     * <p>Setter for the field <code>barColor</code>.</p>
     *
     * @param barColor a {@link java.awt.Color} object
     */
    public void setBarColor(Color barColor) {
        this.barColor = barColor;
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





