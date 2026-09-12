///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.workbench;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Vector icons for the graph editor toolbars, drawn in the same style and colors as the workbench itself: tiny
 * measured and latent nodes, and edges with the same arrowheads and circle endpoints the workbench draws. Colors
 * come from {@link WorkbenchStyle} at paint time, so the icons follow light and dark mode without being rebuilt.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class WorkbenchIcons {

    /**
     * Icon width, matching the old GIF assets closely enough that button layout is unchanged.
     */
    private static final int W = 64;

    /**
     * Icon height.
     */
    private static final int H = 26;

    private WorkbenchIcons() {
    }

    /**
     * Returns the icon for a toolbar tool by name. Names are the ones the toolbars already use: "move", "variable",
     * "latent", "directed", "undirected", "bidirected", "nondirected", "partiallyoriented".
     *
     * @param name the tool name.
     * @return the icon.
     * @throws IllegalArgumentException if the name is not recognized.
     */
    public static Icon forTool(String name) {
        switch (name) {
            case "move":
                return new MoveIcon();
            case "variable":
                return new NodeIcon(false);
            case "latent":
                return new NodeIcon(true);
            case "directed":
                return new EdgeIcon(End.TAIL, End.ARROW);
            case "undirected":
                return new EdgeIcon(End.TAIL, End.TAIL);
            case "bidirected":
                return new EdgeIcon(End.ARROW, End.ARROW);
            case "nondirected":
                return new EdgeIcon(End.CIRCLE, End.CIRCLE);
            case "partiallyoriented":
                return new EdgeIcon(End.CIRCLE, End.ARROW);
            default:
                throw new IllegalArgumentException("Unknown toolbar icon: " + name);
        }
    }

    private enum End {TAIL, ARROW, CIRCLE}

    private static Graphics2D prepare(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        WorkbenchStyle.applyHints(g2);
        return g2;
    }

    private static Font labelFont() {
        return WorkbenchStyle.nodeFont().deriveFont(Font.BOLD, 10f);
    }

    /**
     * Draws a miniature node with a one-letter label centered in it.
     */
    private static void miniNode(Graphics2D g2, Shape shape, Color fill, String label) {
        g2.setColor(fill);
        g2.fill(shape);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(WorkbenchStyle.nodeBorder());
        g2.draw(shape);

        g2.setFont(labelFont());
        FontMetrics fm = g2.getFontMetrics();
        Rectangle b = shape.getBounds();
        int x = b.x + (b.width - fm.stringWidth(label)) / 2;
        int y = b.y + (b.height - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(WorkbenchStyle.nodeText());
        g2.drawString(label, x, y);
    }

    /**
     * Draws a small "add" plus sign in the selection accent.
     */
    private static void plus(Graphics2D g2, int cx, int cy) {
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(WorkbenchStyle.accent());
        g2.draw(new Line2D.Double(cx - 4, cy, cx + 4, cy));
        g2.draw(new Line2D.Double(cx, cy - 4, cx, cy + 4));
    }

    // ---------------------------------------------------------------- move

    /**
     * Four short arrows radiating from a center: select and move.
     */
    private static final class MoveIcon implements Icon {
        @Override
        public int getIconWidth() {
            return W;
        }

        @Override
        public int getIconHeight() {
            return H;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = prepare(g);
            try {
                double cx = x + W / 2.0;
                double cy = y + H / 2.0;
                double r = 10;
                g2.setColor(WorkbenchStyle.edge());
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(cx - r, cy, cx + r, cy));
                g2.draw(new Line2D.Double(cx, cy - r, cx, cy + r));
                for (int k = 0; k < 4; k++) {
                    double ang = k * Math.PI / 2;
                    double tx = cx + r * Math.cos(ang);
                    double ty = cy + r * Math.sin(ang);
                    arrowhead(g2, tx - 6 * Math.cos(ang), ty - 6 * Math.sin(ang), tx, ty, 6, 3);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // ---------------------------------------------------------------- nodes

    /**
     * A plus sign and a miniature node, measured (rounded rectangle) or latent (ellipse).
     */
    private static final class NodeIcon implements Icon {
        private final boolean latent;

        private NodeIcon(boolean latent) {
            this.latent = latent;
        }

        @Override
        public int getIconWidth() {
            return W;
        }

        @Override
        public int getIconHeight() {
            return H;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = prepare(g);
            try {
                plus(g2, x + 10, y + H / 2);
                double nw = 30, nh = 18;
                double nx = x + 22, ny = y + (H - nh) / 2.0;
                if (latent) {
                    miniNode(g2, new Ellipse2D.Double(nx, ny, nw, nh), WorkbenchStyle.latentFill(), "L");
                } else {
                    miniNode(g2, new RoundRectangle2D.Double(nx, ny, nw, nh, 6, 6), WorkbenchStyle.measuredFill(), "X");
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // ---------------------------------------------------------------- edges

    /**
     * Two miniature measured nodes joined by an edge with the given endpoints.
     */
    private static final class EdgeIcon implements Icon {
        private final End left;
        private final End right;

        private EdgeIcon(End left, End right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public int getIconWidth() {
            return W;
        }

        @Override
        public int getIconHeight() {
            return H;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = prepare(g);
            try {
                double nw = 16, nh = 16;
                double ny = y + (H - nh) / 2.0;
                double lx = x + 2, rx = x + W - 2 - nw;
                double ey = y + H / 2.0;
                double x1 = lx + nw, x2 = rx;

                miniNode(g2, new RoundRectangle2D.Double(lx, ny, nw, nh, 5, 5), WorkbenchStyle.measuredFill(), "X");
                miniNode(g2, new RoundRectangle2D.Double(rx, ny, nw, nh, 5, 5), WorkbenchStyle.measuredFill(), "Y");

                g2.setColor(WorkbenchStyle.edge());
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(x1, ey, x2, ey));

                endpoint(g2, left, x1, ey, -1);
                endpoint(g2, right, x2, ey, +1);
            } finally {
                g2.dispose();
            }
        }

        /**
         * Draws an endpoint at (tx, ey), with dir +1 meaning the edge arrives from the left.
         */
        private void endpoint(Graphics2D g2, End end, double tx, double ey, int dir) {
            switch (end) {
                case ARROW:
                    arrowhead(g2, tx - dir * 8, ey, tx, ey, 8, 3.5);
                    break;
                case CIRCLE:
                    double d = 7;
                    double cx = tx - dir * d / 2;
                    Ellipse2D.Double circle = new Ellipse2D.Double(cx - d / 2, ey - d / 2, d, d);
                    Color ring = g2.getColor();
                    g2.setColor(WorkbenchStyle.circleInterior());
                    g2.fill(circle);
                    g2.setColor(ring);
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.draw(circle);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Fills a triangular arrowhead with its tip at (tx, ty), pointing from (fx, fy). Uses the current color.
     */
    private static void arrowhead(Graphics2D g2, double fx, double fy, double tx, double ty,
                                  double length, double halfWidth) {
        double dx = tx - fx, dy = ty - fy;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return;
        double ux = dx / len, uy = dy / len;
        double bx = tx - ux * length, by = ty - uy * length;
        Path2D.Double head = new Path2D.Double();
        head.moveTo(tx, ty);
        head.lineTo(bx - uy * halfWidth, by + ux * halfWidth);
        head.lineTo(bx + uy * halfWidth, by - ux * halfWidth);
        head.closePath();
        g2.fill(head);
    }
}
