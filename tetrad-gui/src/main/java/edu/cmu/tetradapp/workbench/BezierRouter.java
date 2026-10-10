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

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses, for all the curved edges of a workbench together, the side to which each one bows, so that the curves
 * cross each other as little as possible. It has no Swing state, so that it can be tested on its own;
 * {@link AbstractWorkbench} supplies the node rectangles and edges and hands the result to {@link DisplayEdge}.
 * <p>
 * An edge drawn as a quadratic Bezier curve is described by its signed apex: how far the curve is from the straight
 * chord between its two node centers at the chord's midpoint, positive toward the chord's left normal (-uy, ux) for
 * the unit chord direction (ux, uy) from the first node to the second. Each edge has two candidate apexes, one per
 * side, each just large enough to clear the nodes sitting in the chord's corridor on that side (and at least a
 * gentle base bow). An edge on its own takes the side that needs the smaller bow; that is the starting point here.
 * Then, edge by edge, the side with fewer crossings against the other curves as they currently stand is taken, and
 * this is repeated until no edge changes. Each change lowers the total cost, so the passes end; the result is a
 * local minimum, not a guaranteed global one.
 * <p>
 * An edge with a nonzero offset is one of several edges between the same two nodes; its side is fixed by the sign of
 * the offset, so that those edges stay on opposite sides, and it is not moved.
 *
 * @author josephramsey
 */
public final class BezierRouter {

    /**
     * The largest deflection, in pixels, of a curve's apex from its chord.
     */
    public static final double MAX_APEX = 70.0;

    /**
     * Clearance, in pixels, that a curve tries to keep from the rectangle of a node it passes.
     */
    public static final double NODE_MARGIN = 10.0;

    /**
     * The most edges for which sides are chosen together. The work grows with the square of the number of edges
     * and is redone whenever a node moves, so beyond this each edge chooses its side on its own.
     */
    public static final int MAX_EDGES = 250;

    /**
     * The number of straight pieces a curve is cut into for counting crossings.
     */
    private static final int PIECES = 8;

    /**
     * What a crossing costs, in pixels of bow: a side is taken for having fewer crossings whatever the bow.
     */
    private static final double CROSSING_COST = 1000.0;

    /**
     * What it costs for a curve to be unable to clear a node on the side taken: more than any number of crossings,
     * so that an edge is never run behind a node to save crossings. Bending around nodes is what the curves are
     * for.
     */
    private static final double BLOCKED_COST = 1.0e9;

    /**
     * What it costs for a curve to clear the nodes on the side taken by less than NODE_MARGIN: less than a
     * crossing, so that of two sides with the same crossings the roomier one is taken.
     */
    private static final double TIGHT_COST = 200.0;

    /**
     * The number of straight pieces a curve is cut into for testing it against the node rectangles.
     */
    private static final int CLEAR_PIECES = 16;

    /**
     * The step, in pixels, between the bows tried on a side.
     */
    private static final double APEX_STEP = 2.0;

    private BezierRouter() {
    }

    /**
     * The apex an edge takes on its own, without regard to the other edges: the side needing the smaller bow.
     *
     * @param nodes  the bounds of the nodes.
     * @param from   the index of the edge's first node.
     * @param to     the index of the edge's second node.
     * @param offset the edge's offset, nonzero if it is one of several edges between the same two nodes.
     * @return the signed apex, or 0 if the two nodes have the same center.
     */
    public static double soloApex(Rectangle[] nodes, int from, int to, double offset) {
        double[] sides = sideApexes(nodes, from, to, offset);
        return sides == null ? 0.0 : sides[sides[2] <= sides[3] ? 0 : 1];
    }

    /**
     * Chooses the apexes of all the edges together.
     *
     * @param nodes   the bounds of the nodes.
     * @param edges   the edges, each as the indices of its first and second node.
     * @param offsets the offsets of the edges; see the class comment.
     * @return the signed apex of each edge.
     */
    public static double[] route(Rectangle[] nodes, int[][] edges, double[] offsets) {
        int numEdges = edges.length;
        double[] apex = new double[numEdges];

        // Per edge: the apex for the plus side, the apex for the minus side, and the cost of each side on its own.
        double[][] sides = new double[numEdges][];
        double[][][] curves = new double[numEdges][][];
        double[][] boxes = new double[numEdges][];

        for (int e = 0; e < numEdges; e++) {
            sides[e] = sideApexes(nodes, edges[e][0], edges[e][1], offsets[e]);
            apex[e] = sides[e] == null ? 0.0 : sides[e][sides[e][2] <= sides[e][3] ? 0 : 1];
            curves[e] = polyline(nodes, edges[e], apex[e]);
            boxes[e] = box(curves[e]);
        }

        if (numEdges > MAX_EDGES) {
            return apex;
        }

        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;

            for (int e = 0; e < numEdges; e++) {
                if (sides[e] == null || offsets[e] != 0.0) {
                    continue;
                }

                int current = apex[e] == sides[e][0] ? 0 : 1;
                int other = 1 - current;
                double[][] otherCurve = polyline(nodes, edges[e], sides[e][other]);

                double currentCost = sides[e][2 + current]
                                     + CROSSING_COST * crossings(e, curves[e], edges, curves, boxes);
                double otherCost = sides[e][2 + other]
                                   + CROSSING_COST * crossings(e, otherCurve, edges, curves, boxes);

                if (otherCost < currentCost) {
                    apex[e] = sides[e][other];
                    curves[e] = otherCurve;
                    boxes[e] = box(otherCurve);
                    changed = true;
                }
            }

            if (!changed) {
                break;
            }
        }

        return apex;
    }

    /**
     * Counts the crossings among the curves with the given apexes, for testing and for comparing choices.
     *
     * @param nodes the bounds of the nodes.
     * @param edges the edges, each as the indices of its first and second node.
     * @param apex  the signed apex of each edge; all zero for straight edges.
     * @return the number of pairs of edges that cross.
     */
    public static int countCrossings(Rectangle[] nodes, int[][] edges, double[] apex) {
        double[][][] curves = new double[edges.length][][];
        double[][] boxes = new double[edges.length][];

        for (int e = 0; e < edges.length; e++) {
            curves[e] = polyline(nodes, edges[e], apex[e]);
            boxes[e] = box(curves[e]);
        }

        int count = 0;
        for (int e = 0; e < edges.length; e++) count += crossings(e, curves[e], edges, curves, boxes);
        return count / 2;
    }

    /**
     * @return for the edge between the given nodes: the apex for the plus side, the apex for the minus side, and
     * the cost of each of those sides on its own; or null if the two nodes have the same center. With a nonzero
     * offset the side the offset does not point to gets an infinite cost.
     * <p>
     * On each side the bow taken is the one nearest the gentle base bow whose curve stays clear of every other
     * node, tested against the node rectangles themselves: larger bows are tried first, up to the largest allowed,
     * and then smaller ones, down to a straight line. A curve may therefore pass between its chord and a node
     * beside it. (Previously each side had to go around the outside of every node within reach of it, each taken
     * as a circle, so that an edge whose chord ran cleanly through a gap between two nodes counted as blocked on
     * both sides and was bowed as far as allowed, often behind some other node; on Richard's Layout of random
     * graphs the curves ran behind nodes more often than straight edges did.) The cost of a side is the size of
     * its bow, plus TIGHT_COST if the curve clears the nodes but not by NODE_MARGIN, or plus BLOCKED_COST if no bow
     * allowed on that side clears them, in which case the bow overlapping the nodes least is taken.
     */
    private static double[] sideApexes(Rectangle[] nodes, int from, int to, double offset) {
        double x1 = nodes[from].getCenterX(), y1 = nodes[from].getCenterY();
        double x2 = nodes[to].getCenterX(), y2 = nodes[to].getCenterY();
        double len = Math.hypot(x2 - x1, y2 - y1);

        if (len < 1e-6) {
            return null;
        }

        double base = Math.min(16.0, Math.max(5.0, 0.06 * len));
        double maxApex = Math.min(MAX_APEX, 0.35 * len);

        // Only the nodes within reach of some curve allowed can matter.
        double reach = maxApex + NODE_MARGIN;
        double minX = Math.min(x1, x2) - reach, maxX = Math.max(x1, x2) + reach;
        double minY = Math.min(y1, y2) - reach, maxY = Math.max(y1, y2) + reach;
        List<Rectangle> near = new ArrayList<>();

        for (int k = 0; k < nodes.length; k++) {
            if (k == from || k == to) {
                continue;
            }

            Rectangle b = nodes[k];

            if (b.x <= maxX && b.x + b.width >= minX && b.y <= maxY && b.y + b.height >= minY) {
                near.add(b);
            }
        }

        if (offset != 0.0) {
            // One of several edges between the same pair: bow to the offset's side, at least far enough to
            // separate them.
            boolean plus = offset > 0.0;
            double[] side = sideApex(x1, y1, x2, y2, plus ? 1.0 : -1.0, Math.min(maxApex, Math.max(base,
                    Math.abs(offset))), maxApex, false, near);
            return new double[]{side[0], -side[0], plus ? 0.0 : Double.POSITIVE_INFINITY,
                    plus ? Double.POSITIVE_INFINITY : 0.0};
        }

        double[] plusSide = sideApex(x1, y1, x2, y2, 1.0, base, maxApex, true, near);
        double[] minusSide = sideApex(x1, y1, x2, y2, -1.0, base, maxApex, true, near);

        return new double[]{plusSide[0], -minusSide[0], plusSide[1], minusSide[1]};
    }

    /**
     * @param sign    1 for the plus side, -1 for the minus side.
     * @param start   the bow to try first.
     * @param smaller whether bows smaller than the starting one may be taken.
     * @return the size of the bow for the given side of the chord from (x1, y1) to (x2, y2), and its cost; see
     * sideApexes.
     */
    private static double[] sideApex(double x1, double y1, double x2, double y2, double sign, double start,
                                     double maxApex, boolean smaller, List<Rectangle> near) {
        if (near.isEmpty()) {
            return new double[]{start, start};
        }

        for (double margin : new double[]{NODE_MARGIN, 0.0}) {
            double tight = margin == 0.0 ? TIGHT_COST : 0.0;

            for (double a = start; a < maxApex; a += APEX_STEP) {
                if (overlap(x1, y1, x2, y2, sign * a, margin, near, true) == 0) return new double[]{a, a + tight};
            }

            if (overlap(x1, y1, x2, y2, sign * maxApex, margin, near, true) == 0) {
                return new double[]{maxApex, maxApex + tight};
            }

            if (smaller) {
                for (double a = start - APEX_STEP; a > -APEX_STEP; a -= APEX_STEP) {
                    double _a = Math.max(0.0, a);
                    if (overlap(x1, y1, x2, y2, sign * _a, margin, near, true) == 0) return new double[]{_a, _a + tight};
                }
            }
        }

        // No bow allowed on this side clears the nodes: take the one that overlaps them least, and of those the
        // one nearest the starting bow.
        double best = start;
        int bestOverlap = Integer.MAX_VALUE;

        for (double a = smaller ? 0.0 : start; a <= maxApex; a += APEX_STEP) {
            int overlap = overlap(x1, y1, x2, y2, sign * a, 0.0, near, false);

            if (overlap < bestOverlap || (overlap == bestOverlap && Math.abs(a - start) < Math.abs(best - start))) {
                bestOverlap = overlap;
                best = a;
            }
        }

        return new double[]{best, BLOCKED_COST + 10.0 * bestOverlap + best};
    }

    /**
     * @param firstOnly whether to stop at the first piece found to overlap a node.
     * @return the number of the CLEAR_PIECES straight pieces of the curve with the given apex, from (x1, y1) to
     * (x2, y2), that touch one of the given node rectangles grown by the given margin.
     */
    private static int overlap(double x1, double y1, double x2, double y2, double apex, double margin,
                               List<Rectangle> near, boolean firstOnly) {
        double len = Math.hypot(x2 - x1, y2 - y1);

        // The control point is displaced twice the apex from the chord's midpoint.
        double cx = (x1 + x2) / 2.0 - (y2 - y1) / len * 2.0 * apex;
        double cy = (y1 + y2) / 2.0 + (x2 - x1) / len * 2.0 * apex;

        int count = 0;
        double px = x1, py = y1;

        for (int i = 1; i <= CLEAR_PIECES; i++) {
            double t = i / (double) CLEAR_PIECES, s = 1.0 - t;
            double x = s * s * x1 + 2.0 * s * t * cx + t * t * x2;
            double y = s * s * y1 + 2.0 * s * t * cy + t * t * y2;

            for (Rectangle b : near) {
                if (new Rectangle2D.Double(b.x - margin, b.y - margin, b.width + 2.0 * margin,
                        b.height + 2.0 * margin).intersectsLine(px, py, x, y)) {
                    count++;
                    if (firstOnly) return count;
                    break;
                }
            }

            px = x;
            py = y;
        }

        return count;
    }

    /**
     * @return the curve of the given edge with the given apex, from node center to node center, as PIECES + 1
     * points.
     */
    private static double[][] polyline(Rectangle[] nodes, int[] edge, double apex) {
        double x1 = nodes[edge[0]].getCenterX(), y1 = nodes[edge[0]].getCenterY();
        double x2 = nodes[edge[1]].getCenterX(), y2 = nodes[edge[1]].getCenterY();
        double len = Math.hypot(x2 - x1, y2 - y1);

        // The control point is displaced twice the apex from the chord's midpoint.
        double cx = (x1 + x2) / 2.0, cy = (y1 + y2) / 2.0;

        if (len >= 1e-6) {
            cx += -(y2 - y1) / len * 2.0 * apex;
            cy += (x2 - x1) / len * 2.0 * apex;
        }

        double[][] points = new double[PIECES + 1][2];

        for (int i = 0; i <= PIECES; i++) {
            double t = i / (double) PIECES, s = 1.0 - t;
            points[i][0] = s * s * x1 + 2.0 * s * t * cx + t * t * x2;
            points[i][1] = s * s * y1 + 2.0 * s * t * cy + t * t * y2;
        }

        return points;
    }

    /**
     * @return the number of the other edges that the given curve of edge e crosses. Two edges at a common node are
     * compared without their pieces at that node, where they meet without crossing.
     */
    private static int crossings(int e, double[][] curve, int[][] edges, double[][][] curves,
                                 double[][] boxes) {
        double[] box = box(curve);
        int count = 0;

        for (int f = 0; f < edges.length; f++) {
            if (f == e) {
                continue;
            }

            double[] otherBox = boxes[f];

            if (box[0] > otherBox[2] || otherBox[0] > box[2] || box[1] > otherBox[3] || otherBox[1] > box[3]) {
                continue;
            }

            // The pieces to leave out at each end of each curve, where the two edges share a node.
            int eStart = 0, eEnd = PIECES, fStart = 0, fEnd = PIECES;

            if (edges[e][0] == edges[f][0]) {
                eStart = 1;
                fStart = 1;
            }
            if (edges[e][0] == edges[f][1]) {
                eStart = 1;
                fEnd = PIECES - 1;
            }
            if (edges[e][1] == edges[f][0]) {
                eEnd = PIECES - 1;
                fStart = 1;
            }
            if (edges[e][1] == edges[f][1]) {
                eEnd = PIECES - 1;
                fEnd = PIECES - 1;
            }

            if (cross(curve, eStart, eEnd, curves[f], fStart, fEnd)) {
                count++;
            }
        }

        return count;
    }

    private static boolean cross(double[][] a, int aStart, int aEnd, double[][] b, int bStart, int bEnd) {
        for (int i = aStart; i < aEnd; i++) {
            double ax1 = a[i][0], ay1 = a[i][1], ax2 = a[i + 1][0], ay2 = a[i + 1][1];
            double aMinX = Math.min(ax1, ax2), aMaxX = Math.max(ax1, ax2);
            double aMinY = Math.min(ay1, ay2), aMaxY = Math.max(ay1, ay2);

            for (int j = bStart; j < bEnd; j++) {
                double bx1 = b[j][0], by1 = b[j][1], bx2 = b[j + 1][0], by2 = b[j + 1][1];

                // Pieces whose boxes are apart cannot cross.
                if (Math.min(bx1, bx2) > aMaxX || Math.max(bx1, bx2) < aMinX
                    || Math.min(by1, by2) > aMaxY || Math.max(by1, by2) < aMinY) {
                    continue;
                }

                if (Line2D.linesIntersect(ax1, ay1, ax2, ay2, bx1, by1, bx2, by2)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static double[] box(double[][] curve) {
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (double[] p : curve) {
            box[0] = Math.min(box[0], p[0]);
            box[1] = Math.min(box[1], p[1]);
            box[2] = Math.max(box[2], p[0]);
            box[3] = Math.max(box[3], p[1]);
        }

        return box;
    }
}
