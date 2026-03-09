package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.util.TMath;

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
 * Theme-aware version:
 *  - uses Look &amp; Feel colors where possible
 *
 * @author josephramsey
 */
public class HistogramPanel extends JPanel {

    public static final String[] tiles = {"1-tile", "2-tile", "tertile", "quartile", "quintile", "sextile",
            "septile", "octile", "nontile", "decile"};

    private final Histogram histogram;
    private final Map<Rectangle, Integer> rectMap = new LinkedHashMap<>();
    private final boolean drawAxes;
    private final int paddingX;

    /**
     * Optional override for bar color. If null, theme color is used.
     */
    private Color barColor;

    // Popup bin choices
    private int[] binChoices = new int[]{5, 10, 15, 20, 30, 40};

    public HistogramPanel(Histogram histogram, boolean drawAxes) {
        this.drawAxes = drawAxes;
        this.paddingX = drawAxes ? 40 : 5;

        if (histogram == null) {
            throw new NullPointerException("Given histogram must not be null");
        }

        this.histogram = histogram;

        setOpaque(true);
        refreshTheme();
        installBinsPopup();
        setToolTipText(" ");
    }

    // ============================================================
    // Theme helpers
    // ============================================================

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static boolean isDarkMode() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
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

    private static Color brighten(Color c, double amount) {
        return blend(c, Color.WHITE, amount);
    }

    private static Color darken(Color c, double amount) {
        return blend(c, Color.BLACK, amount);
    }

    /**
     * Background outside the plot rectangle.
     */
    private static Color getPanelBg() {
        return uiColor("Panel.background", new Color(238, 238, 238));
    }

    /**
     * Background inside the plot rectangle.
     */
    private static Color getPlotBg() {
        if (isDarkMode()) {
            Color panel = getPanelBg();
            Color textField = uiColor("TextField.background", panel);
            return brighten(blend(panel, textField, 0.5), 0.03);
        }

        Color c = UIManager.getColor("TextField.background");
        if (c != null) return c;

        return Color.WHITE;
    }

    /**
     * Border/axis/tick/label color.
     */
    private static Color getLineColor() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c != null) return c;

        c = UIManager.getColor("Separator.foreground");
        if (c != null) return c;

        c = UIManager.getColor("Label.foreground");
        if (c != null) {
            return isDarkMode() ? blend(c, Color.GRAY, 0.35) : darken(c, 0.20);
        }

        return Color.GRAY.darker();
    }

    /**
     * Text color for labels.
     */
    private static Color getTextColor() {
        return uiColor("Label.foreground", isDarkMode() ? new Color(230, 230, 230) : Color.BLACK);
    }

    /**
     * Default bar color when no override is set.
     */
    private static Color getDefaultBarColor() {
        Color c = UIManager.getColor("Table.selectionBackground");
        if (c != null) return c.brighter();

        c = UIManager.getColor("Focus.color");
        if (c != null) return c.brighter();

        return isDarkMode() ? new Color(110, 170, 255) : Color.RED.darker();
    }

    private Color getBarColor() {
        return this.barColor != null ? this.barColor : getDefaultBarColor();
    }

    private void refreshTheme() {
        setBackground(getPanelBg());
        setForeground(getTextColor());
    }

    @Override
    public void updateUI() {
        super.updateUI();
        refreshTheme();
        repaint();
    }

    // ============================================================
    // Public API
    // ============================================================

    public void setBinChoices(int[] choices) {
        if (choices == null || choices.length == 0) return;
        this.binChoices = choices.clone();
        installBinsPopup();
    }

    public void setXAxisBounds(double min, double max, boolean ignoreOutside) {
        try {
            this.histogram.setContinuousBounds(min, max, ignoreOutside);
        } catch (RuntimeException ignored) {
            // If discrete target etc., ignore.
        }
        repaint();
    }

    public void clearXAxisBounds() {
        try {
            this.histogram.clearContinuousBounds();
        } catch (RuntimeException ignored) {
        }
        repaint();
    }

    private void installBinsPopup() {
        if (!(histogram.getTargetNode() instanceof ContinuousVariable)) {
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

        MouseAdapter ma = new MouseAdapter() {
            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                menu.show(HistogramPanel.this, e.getX(), e.getY());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShow(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShow(e);
            }
        };

        addMouseListener(ma);
    }

    private static int getMax(int[] freqs) {
        int max = freqs.length == 0 ? 0 : freqs[0];
        for (int i = 1; i < freqs.length; i++) {
            max = TMath.max(max, freqs[i]);
        }
        return max;
    }

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

    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        int paddingY = drawAxes ? 20 : 5;
        int height = getHeight() - 2;
        int width = getWidth() - (drawAxes ? 4 : 2);
        int displayedHeight = height - paddingY;

        int space = drawAxes ? 2 : 1;
        int dash = 10;

        rectMap.clear();

        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Histogram histogram = getHistogram();
            int[] freqs = histogram.getFrequencies();
            int categories = freqs.length;

            Color panelBg = getBackground();
            Color plotBg = getPlotBg();
            Color lineColor = getLineColor();
            Color textColor = getTextColor();
            Color bar = getBarColor();

            // Background outside plot
            g2d.setColor(panelBg);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            // Plot rectangle background
            g2d.setColor(plotBg);
            g2d.fillRect(0, 0, width, height);

            if (categories <= 0) {
                g2d.setColor(lineColor);
                g2d.drawRect(paddingX, 0, width - paddingX, height - paddingY);
                return;
            }

            int barWidth = TMath.max((width - paddingX) / categories, 2) - space;
            int topFreq = getMax(freqs);
            double scale = (topFreq == 0) ? 0.0 : (displayedHeight / (double) topFreq);

            FontMetrics fm = g2d.getFontMetrics();

            // Bars
            for (int i = 0; i < categories; i++) {
                int freq = freqs[i];
                int y = (int) TMath.ceil(scale * freq);
                int x = space * (i + 1) + barWidth * i + paddingX;

                g2d.setColor(bar);
                Rectangle rect = new Rectangle(x, (height - paddingY - y - space), barWidth, y);
                g2d.fill(rect);
                rectMap.put(rect, freq);
            }

            // Border around plot area
            g2d.setColor(lineColor);
            g2d.drawRect(paddingX, 0, width - paddingX, height - paddingY);

            if (drawAxes) {
                g2d.setColor(lineColor);

                int topY = height - paddingY - (int) TMath.ceil(scale * topFreq) + 1;
                String top = String.valueOf(topFreq);
                g2d.drawString(top, paddingX - fm.stringWidth(top), TMath.max(10, topY - 2));
                g2d.drawLine(paddingX - dash, topY, paddingX, topY);

                g2d.drawString("0", paddingX - fm.stringWidth("0"), height - paddingY + fm.getAscent() / 2);
                g2d.drawLine(paddingX - dash, height - paddingY, paddingX, height - paddingY);

                int hSize = (height - paddingY - topY) / 4;
                for (int i = 1; i < 4; i++) {
                    int yTick = height - paddingY - hSize * i;
                    g2d.drawLine(paddingX - dash, yTick, paddingX, yTick);
                }

                if (histogram.getTargetNode() instanceof ContinuousVariable) {
                    double xmin = histogram.getDisplayMin();
                    double xmax = histogram.getDisplayMax();

                    String sMin = formatAxis(xmin);
                    String sMax = formatAxis(xmax);

                    int yLabel = height - 2;
                    g2d.setColor(textColor);
                    g2d.drawString(sMin, paddingX, yLabel);
                    int maxX = width - fm.stringWidth(sMax);
                    g2d.drawString(sMax, TMath.max(paddingX + 5, maxX), yLabel);
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private String formatAxis(double v) {
        if (Double.isNaN(v)) return "";
        if (TMath.abs(v) < 1e-9) return "0";
        if (TMath.abs(v - 1.0) < 1e-9) return "1";
        return String.format("%.3g", v);
    }

    public Histogram getHistogram() {
        return this.histogram;
    }

    public void setBarColor(Color barColor) {
        this.barColor = barColor;
        repaint();
    }
}