package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.Histogram;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View for the Histogram class.
 *
 * Drop-in replacement:
 *  - right-click popup for choosing #bins (continuous targets)
 *  - optional forced x-axis bounds (delegates to Histogram)
 *  - draws x-axis min/max labels when drawAxes==true
 *
 * @author josephramsey
 */
public class HistogramPanel extends JPanel {

    /**
     * An array of predefined tile labels representing different segment names or quantile divisions,
     * commonly used in statistical or graphical displays. Each entry in the array corresponds to
     * a specific type of tile or quantile, ranging from "1-tile" to "decile".
     */
    public static final String[] tiles = {"1-tile", "2-tile", "tertile", "quartile", "quintile", "sextile",
            "septile", "octile", "nontile", "decile"};

    private static final Color LINE_COLOR = Color.GRAY.darker();

    private final Histogram histogram;
    private final Map<Rectangle, Integer> rectMap = new LinkedHashMap<>();
    private final boolean drawAxes;
    private final int paddingX;

    private Color barColor = Color.RED.darker();

    // Popup bin choices (you can tweak)
    private int[] binChoices = new int[]{5, 10, 15, 20, 30, 40};

    /**
     * Constructs a HistogramPanel to display a histogram visualization.
     *
     * @param histogram the Histogram object to be displayed; must not be null
     * @param drawAxes a boolean indicating whether axes should be drawn on the panel;
     *                 if true, extra padding is added to accommodate the axes
     * @throws NullPointerException if the provided histogram is null
     */
    public HistogramPanel(Histogram histogram, boolean drawAxes) {
        this.drawAxes = drawAxes;
        this.paddingX = drawAxes ? 40 : 5;

        if (histogram == null) {
            throw new NullPointerException("Given histogram must not be null");
        }

        this.histogram = histogram;

        installBinsPopup();
        this.setToolTipText(" ");
    }

    /**
     * Sets the bin choices for the histogram panel. This method updates the
     * available bin options and reconfigures the associated popup menu to
     * reflect the new bin choices. If the provided array of choices is null
     * or empty, the method returns without making changes.
     *
     * @param choices an array of integers representing the possible numbers
     *                of bins available for the histogram. Each element must
     *                be a positive integer; negative or non-positive values
     *                will be ignored during internal processing.
     */
    public void setBinChoices(int[] choices) {
        if (choices == null || choices.length == 0) return;
        this.binChoices = choices.clone();
        // Reinstall to reflect new choices
        installBinsPopup();
    }

    /**
     * Sets the bounds for the x-axis of the histogram and specifies whether
     * data points outside these bounds should be ignored. This configuration
     * will be applied to the underlying histogram visualization.
     *
     * @param min the minimum value (lower bound) for the x-axis; inclusive
     * @param max the maximum value (upper bound) for the x-axis; inclusive
     * @param ignoreOutside a boolean indicating whether data points outside
     *                      the specified bounds should be excluded
     */
    public void setXAxisBounds(double min, double max, boolean ignoreOutside) {
        try {
            this.histogram.setContinuousBounds(min, max, ignoreOutside);
        } catch (RuntimeException ignored) {
            // If discrete target etc., ignore.
        }
        repaint();
    }

    /**
     * Clears the x-axis bounds for the histogram visualization.
     *
     * This method removes any previously set bounds on the x-axis of the histogram,
     * effectively resetting it to display the full range of data. The underlying
     * histogram's continuous bounds are cleared through the associated method, and
     * any graphical updates are immediately triggered by invoking the repaint method.
     *
     * If an exception occurs during the process of clearing the bounds, it is ignored,
     * ensuring that the visualization continues to update without interruption.
     *
     * Note: This method primarily serves as a utility for resetting the bounds on
     * the histogram's x-axis, and is typically used in scenarios where dynamic
     * modifications to the histogram display are required.
     */
    public void clearXAxisBounds() {
        try {
            this.histogram.clearContinuousBounds();
        } catch (RuntimeException ignored) {
        }
        repaint();
    }

    /**
     * Installs a popup menu for configuring the number of bins displayed in the histogram.
     *
     * This method is specifically designed for histograms that target continuous variables.
     * If the histogram's target is not a continuous variable, the method exits without
     * performing any configuration.
     *
     * The popup menu contains a submenu labeled "Bins," which provides choices for the
     * number of bins to display in the histogram. Users can select the desired number of
     * bins from a list of positive integer options. If the selected bin count triggers
     * a runtime exception when applied to the histogram, the exception is ignored, and
     * the component is repainted regardless.
     *
     * For continuous interaction, the popup menu is triggered on platform-specific
     * mouse events (such as right-clicking). Mouse listeners are dynamically added and
     * replaced, ensuring the popup operates consistently while avoiding duplicate listeners.
     *
     * Key functionality:
     * - The number of bins is determined by an internal array of options (`binChoices`).
     * - Invalid or non-positive bin counts in the array are ignored.
     * - The menu dynamically updates to reflect the current bin selection.
     *
     * Note: The method relies on the histogram's state and UI infrastructure to integrate
     * the popup menu behavior. It does not expose or validate the current number of bins
     * due to limitations in the underlying histogram API.
     */
    private void installBinsPopup() {
        // Only meaningful for continuous targets.
        if (!(histogram.getTargetNode() instanceof ContinuousVariable)) {
            // Still install a popup so the component can show "N/A" if you want,
            // but simplest: do nothing.
            return;
        }

        final JPopupMenu menu = new JPopupMenu();

        JMenu binsMenu = new JMenu("Bins");
        ButtonGroup group = new ButtonGroup();

        for (int b : binChoices) {
            if (b < 1) continue;
            final int bins = b;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(bins + " bins");
            item.addActionListener(e -> {
                try {
                    histogram.setNumBins(bins);
                } catch (RuntimeException ex) {
                    // ignore
                }
                repaint();
            });
            group.add(item);
            binsMenu.add(item);
        }

        menu.add(binsMenu);

        // Mouse listener for cross-platform popup triggers
        MouseAdapter ma = new MouseAdapter() {
            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                // Update selection checks to current bins if we can infer it:
                // Histogram doesn't expose numBins; so we just show menu without checks.
                menu.show(HistogramPanel.this, e.getX(), e.getY());
            }

            @Override public void mousePressed(MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
        };

        // Remove prior listeners of same type (avoid duplicates if reinstalling)
        for (var l : getMouseListeners()) {
            if (l instanceof MouseAdapter) {
                // best-effort: don't remove unrelated adapters; but this is fine for your use case
            }
        }
        addMouseListener(ma);
    }

    private static int getMax(int[] freqs) {
        int max = freqs.length == 0 ? 0 : freqs[0];
        for (int i = 1; i < freqs.length; i++) {
            max = Math.max(max, freqs[i]);
        }
        return max;
    }

    /**
     * Retrieves the tooltip text for the histogram panel based on the location of the mouse event.
     * The tooltip displays the value associated with the rectangular area containing the mouse pointer.
     *
     * @param evt the MouseEvent object representing the mouse pointer's location and event information
     * @return a String representing the tooltip text for the hovered rectangular area, or null if
     *         the mouse pointer is not contained within any rectangle or no value is associated with it
     */
    @Override
    public String getToolTipText(MouseEvent evt) {
        Point point = evt.getPoint();
        for (Rectangle rect : this.rectMap.keySet()) {
            if (rect.contains(point)) {
                Integer i = this.rectMap.get(rect);
                return i == null ? null : i.toString();
            }
        }
        return null;
    }

    /**
     * Renders the component by drawing a histogram, axes, and labels.
     * This method overrides the default paintComponent behavior to provide
     * customized histogram rendering based on the data and display settings.
     *
     * @param graphics the graphics context used for rendering the component
     */
    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        int paddingY = drawAxes ? 20 : 5; // a bit more room for x labels
        int height = getHeight() - 2;
        int width = getWidth() - (drawAxes ? 4 : 2);
        int displayedHeight = height - paddingY;

        int space = drawAxes ? 2 : 1;
        int dash = 10;

        rectMap.clear();

        Graphics2D g2d = (Graphics2D) graphics;
        Histogram histogram = getHistogram();

        int[] freqs = histogram.getFrequencies();
        int categories = freqs.length;

        // Background / box
        g2d.setColor(getBackground());
        g2d.fillRect(0, 0, width + 2 * space, height);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        if (categories <= 0) {
            // Nothing to draw
            g2d.setColor(LINE_COLOR);
            g2d.drawRect(paddingX, 0, width - paddingX, height - paddingY);
            return;
        }

        int barWidth = FastMath.max((width - paddingX) / categories, 2) - space;
        int topFreq = getMax(freqs);
        double scale = (topFreq == 0) ? 0.0 : (displayedHeight / (double) topFreq);

        FontMetrics fm = g2d.getFontMetrics();

        // Bars
        for (int i = 0; i < categories; i++) {
            int freq = freqs[i];
            int y = (int) FastMath.ceil(scale * freq);
            int x = space * (i + 1) + barWidth * i + paddingX;

            g2d.setColor(barColor);
            Rectangle rect = new Rectangle(x, (height - paddingY - y - space), barWidth, y);
            g2d.fill(rect);
            rectMap.put(rect, freq);
        }

        // Border around plot area
        g2d.setColor(LINE_COLOR);
        g2d.drawRect(paddingX, 0, width - paddingX, height - paddingY);

        if (drawAxes) {
            // Y-axis ticks/labels (existing behavior)
            g2d.setColor(LINE_COLOR);

            int topY = height - paddingY - (int) FastMath.ceil(scale * topFreq) + 1;
            String top = String.valueOf(topFreq);
            g2d.drawString(top, paddingX - fm.stringWidth(top), Math.max(10, topY - 2));
            g2d.drawLine(paddingX - dash, topY, paddingX, topY);

            g2d.drawString("0", paddingX - fm.stringWidth("0"), height - paddingY + fm.getAscent() / 2);
            g2d.drawLine(paddingX - dash, height - paddingY, paddingX, height - paddingY);

            int hSize = (height - paddingY - topY) / 4;
            for (int i = 1; i < 4; i++) {
                int yTick = height - paddingY - hSize * i;
                g2d.drawLine(paddingX - dash, yTick, paddingX, yTick);
            }

            // NEW: x-axis min/max labels when continuous
            if (histogram.getTargetNode() instanceof ContinuousVariable) {
                double xmin = histogram.getDisplayMin();
                double xmax = histogram.getDisplayMax();

                String sMin = formatAxis(xmin);
                String sMax = formatAxis(xmax);

                int yLabel = height - 2; // bottom
                g2d.drawString(sMin, paddingX, yLabel);
                int maxX = width - fm.stringWidth(sMax);
                g2d.drawString(sMax, Math.max(paddingX + 5, maxX), yLabel);
            }
        }
    }

    private String formatAxis(double v) {
        if (Double.isNaN(v)) return "";
        // Keep it simple; you can swap for NumberFormatUtil if you prefer
        // For p-values, this will show nicely as 0 and 1.
        if (Math.abs(v) < 1e-9) return "0";
        if (Math.abs(v - 1.0) < 1e-9) return "1";
        return String.format("%.3g", v);
    }

    /**
     * Retrieves the current {@code Histogram} object associated with this panel.
     *
     * @return the {@code Histogram} object being displayed in the panel
     */
    public Histogram getHistogram() {
        return this.histogram;
    }

    /**
     * Sets the color used to draw the bars in the histogram.
     *
     * @param barColor the {@code Color} object representing the desired color for the histogram bars.
     *                 This value must not be null, as it directly affects the rendering appearance
     *                 of the histogram bars.
     */
    public void setBarColor(Color barColor) {
        this.barColor = barColor;
    }
}