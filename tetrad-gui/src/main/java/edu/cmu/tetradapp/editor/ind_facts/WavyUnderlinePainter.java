package edu.cmu.tetradapp.editor.ind_facts;

import org.apache.commons.math3.util.FastMath;

import javax.swing.text.*;
import java.awt.*;

/** Paints a red wavy underline (squiggle) under a text range. */
public final class WavyUnderlinePainter implements Highlighter.HighlightPainter {
    private final Color color;

    public WavyUnderlinePainter(Color color) {
        this.color = color;
    }

    @Override
    public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
        try {
            Rectangle r0 = c.modelToView(p0);
            Rectangle r1 = c.modelToView(p1);
            if (r0 == null || r1 == null) return;

            g.setColor(color);

            // Handle multi-line highlights: draw per line segment.
            int start = p0;
            while (start < p1) {
                Rectangle rs = c.modelToView(start);
                if (rs == null) break;

                int lineEnd = Utilities.getRowEnd(c, start);
                int end = FastMath.min(p1, lineEnd);

                Rectangle re = c.modelToView(end);
                if (re == null) break;

                int y = rs.y + rs.height - 2;
                int x1 = rs.x;
                int x2 = re.x;

                drawWavyLine(g, x1, y, x2, y);

                start = end + 1;
            }
        } catch (BadLocationException ignored) {
        }
    }

    private void drawWavyLine(Graphics g, int x1, int y1, int x2, int y2) {
        int waveHeight = 2;
        int waveLength = 4;

        int x = x1;
        boolean up = true;
        while (x < x2) {
            int nx = FastMath.min(x + waveLength, x2);
            int ny = up ? y1 - waveHeight : y1 + waveHeight;
            g.drawLine(x, y1, nx, ny);
            y1 = ny;
            x = nx;
            up = !up;
        }
    }
}