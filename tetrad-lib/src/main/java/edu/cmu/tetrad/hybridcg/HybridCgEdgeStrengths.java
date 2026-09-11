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

package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes a per-edge strength summary for a {@link HybridCgIm} and produces a display copy of its graph with edges
 * colored by that strength.
 * <p>
 * Four kinds of edge occur in a hybrid CG model, handled in two families:
 * <ul>
 *   <li><b>Continuous child, continuous parent.</b> The edge carries one linear coefficient per discrete-parent
 *   stratum. Strength is the largest absolute coefficient across strata. The kind is {@code LINEAR_POSITIVE} if every
 *   stratum's coefficient is non-negative, {@code LINEAR_NEGATIVE} if every one is non-positive, and
 *   {@code LINEAR_MIXED} if the sign differs across strata.</li>
 *   <li><b>Everything else</b> ({@code TABULAR}): discrete child with discrete parent, discrete child with binned
 *   continuous parent, continuous child with discrete parent. Strength is a scale-free effect size in [0, 1]: for a
 *   discrete child, the maximum total-variation distance between the child's conditional distributions across the
 *   parent's values, holding the other parents fixed; for a continuous child, the largest shift in conditional mean
 *   across the parent's values in units of the residual standard deviation, d, squashed to d / (1 + d).</li>
 * </ul>
 * Linear strengths are raw coefficients and are only comparable after scaling; {@link #coloredGraph} scales them by
 * the largest absolute coefficient in the model. Tabular strengths are already in [0, 1].
 */
public final class HybridCgEdgeStrengths {

    private HybridCgEdgeStrengths() {
    }

    /**
     * The family an edge's strength belongs to.
     */
    public enum Kind {
        /** Linear coefficient, non-negative in every stratum. */
        LINEAR_POSITIVE,
        /** Linear coefficient, non-positive in every stratum. */
        LINEAR_NEGATIVE,
        /** Linear coefficient whose sign differs across discrete-parent strata. */
        LINEAR_MIXED,
        /** No linear coefficient; strength is a distribution-shift effect size in [0, 1]. */
        TABULAR
    }

    /**
     * Strength summary for one edge.
     *
     * @param kind        the family
     * @param value       for linear kinds, the largest absolute coefficient across strata (raw scale); for TABULAR, an
     *                    effect size in [0, 1]
     * @param description a short human-readable account of how the value was obtained
     */
    public record Strength(Kind kind, double value, String description) {
        /**
         * True if this is one of the linear kinds.
         */
        public boolean isLinear() {
            return kind != Kind.TABULAR;
        }
    }

    /**
     * Computes a strength for every edge of the IM's graph.
     *
     * @param im the instantiated model
     * @return map from each edge of {@code im.getPm().getGraph()} to its strength, in graph order
     */
    public static Map<Edge, Strength> compute(HybridCgIm im) {
        HybridCgPm pm = im.getPm();
        Graph g = pm.getGraph();
        Node[] nodes = pm.getNodes();
        Map<Edge, Strength> out = new LinkedHashMap<>();

        for (int y = 0; y < nodes.length; y++) {
            int[] dps = pm.getDiscreteParents(y);
            int[] cps = pm.getContinuousParents(y);
            int rows;
            try {
                rows = pm.getNumRows(y);
            } catch (IllegalStateException ex) {
                continue; // discrete child with continuous parents but no cutpoints yet; nothing to summarize
            }
            int[] dims = pm.getRowDims(y);
            if (rows == 0) continue;

            if (!pm.isDiscrete(y)) {
                // Continuous parents: linear coefficients, one per stratum.
                for (int t = 0; t < cps.length; t++) {
                    Edge e = g.getEdge(nodes[cps[t]], nodes[y]);
                    if (e == null) continue;
                    out.put(e, linearStrength(im, y, t, rows, nodes[cps[t]].getName()));
                }
                // Discrete parents: mean shift in residual-SD units.
                for (int i = 0; i < dps.length; i++) {
                    Edge e = g.getEdge(nodes[dps[i]], nodes[y]);
                    if (e == null) continue;
                    out.put(e, meanShiftStrength(im, y, dims, i, rows, nodes[dps[i]].getName()));
                }
            } else {
                // Row dims are [discrete parents..., continuous-parent bins...]; position k selects one parent.
                for (int k = 0; k < dims.length; k++) {
                    Node parent = k < dps.length ? nodes[dps[k]] : nodes[cps[k - dps.length]];
                    Edge e = g.getEdge(parent, nodes[y]);
                    if (e == null) continue;
                    boolean binned = k >= dps.length;
                    out.put(e, tvShiftStrength(im, pm, y, dims, k, rows, parent.getName(), binned));
                }
            }
        }
        return out;
    }

    /**
     * Returns a copy of the IM's graph (sharing node objects, with new edge objects) whose edges carry a line color
     * and a tooltip annotation reflecting their strength. Linear intensities are scaled by the largest absolute
     * coefficient in the model; tabular intensities are used as is.
     *
     * @param im the instantiated model
     * @return a colored display copy of the graph
     */
    public static Graph coloredGraph(HybridCgIm im) {
        Map<Edge, Strength> strengths = compute(im);

        double maxAbsCoef = 0.0;
        for (Strength s : strengths.values()) {
            if (s.isLinear() && Double.isFinite(s.value())) maxAbsCoef = Math.max(maxAbsCoef, s.value());
        }

        Graph src = im.getPm().getGraph();
        Graph g = new EdgeListGraph(src.getNodes());
        for (Edge e : src.getEdges()) {
            Edge copy = new Edge(e);
            Strength s = strengths.get(e);
            if (s != null) {
                double intensity;
                if (s.isLinear()) {
                    intensity = maxAbsCoef > 0 ? Math.min(1.0, s.value() / maxAbsCoef) : 0.0;
                } else {
                    intensity = Math.max(0.0, Math.min(1.0, s.value()));
                }
                if (!Double.isFinite(intensity)) intensity = 0.0;
                copy.setLineColor(colorFor(s.kind(), intensity));
                copy.setAnnotation(s.description() + String.format(" Color intensity %.2f.", intensity));
            }
            g.addEdge(copy);
        }
        return g;
    }

    /**
     * Maps a kind and an intensity in [0, 1] to a color. Blue for positive linear, red for negative linear, purple for
     * mixed sign, orange for tabular. Higher intensity gives a darker, more saturated shade.
     *
     * @param kind      the family
     * @param intensity a value in [0, 1]
     * @return the color
     */
    public static Color colorFor(Kind kind, double intensity) {
        float hue = switch (kind) {
            case LINEAR_POSITIVE -> 0.62f;   // blue
            case LINEAR_NEGATIVE -> 0.00f;   // red
            case LINEAR_MIXED -> 0.80f;      // purple
            case TABULAR -> 0.08f;           // orange
        };
        float s = (float) (0.3 + 0.7 * Math.max(0.0, Math.min(1.0, intensity)));
        float saturation = s;
        float brightness = 0.95f - 0.35f * s;
        return Color.getHSBColor(hue, saturation, brightness);
    }

    // ---------------------------------------------------------------- strengths

    private static Strength linearStrength(HybridCgIm im, int y, int t, int rows, String parentName) {
        double maxAbs = 0.0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int count = 0;
        for (int r = 0; r < rows; r++) {
            double c = im.getCoefficient(y, r, t);
            if (!Double.isFinite(c)) continue;
            count++;
            maxAbs = Math.max(maxAbs, Math.abs(c));
            min = Math.min(min, c);
            max = Math.max(max, c);
        }
        if (count == 0) {
            return new Strength(Kind.LINEAR_POSITIVE, 0.0, "Linear coefficient on " + parentName + ": not set.");
        }
        Kind kind;
        if (min >= 0) kind = Kind.LINEAR_POSITIVE;
        else if (max <= 0) kind = Kind.LINEAR_NEGATIVE;
        else kind = Kind.LINEAR_MIXED;

        String desc;
        if (count == 1) {
            desc = String.format("Linear coefficient on %s = %.4g.", parentName, min);
        } else {
            desc = String.format("Linear coefficient on %s ranges over [%.4g, %.4g] across %d strata; max |coef| = %.4g.",
                    parentName, min, max, count, maxAbs);
            if (kind == Kind.LINEAR_MIXED) desc += " Sign differs across strata.";
        }
        return new Strength(kind, maxAbs, desc);
    }

    private static Strength meanShiftStrength(HybridCgIm im, int y, int[] dims, int k, int rows, String parentName) {
        double bestD = 0.0;
        for (int[] group : groupsVarying(dims, k, rows)) {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, varSum = 0.0;
            int n = 0, nVar = 0;
            for (int r : group) {
                double mu = im.getMean(y, r);
                if (!Double.isFinite(mu)) continue;
                lo = Math.min(lo, mu);
                hi = Math.max(hi, mu);
                n++;
                double v = im.getVariance(y, r);
                if (Double.isFinite(v) && v > 0) {
                    varSum += v;
                    nVar++;
                }
            }
            if (n < 2) continue;
            double shift = hi - lo;
            double d;
            if (nVar > 0) d = shift / Math.sqrt(varSum / nVar);
            else d = shift > 0 ? Double.POSITIVE_INFINITY : 0.0;
            bestD = Math.max(bestD, d);
        }
        double value = Double.isInfinite(bestD) ? 1.0 : bestD / (1.0 + bestD);
        String desc = String.format("Largest mean shift across values of %s = %.3g residual SDs (effect size %.2f).",
                parentName, bestD, value);
        return new Strength(Kind.TABULAR, value, desc);
    }

    private static Strength tvShiftStrength(HybridCgIm im, HybridCgPm pm, int y, int[] dims, int k, int rows,
                                            String parentName, boolean binned) {
        int card = pm.getCardinality(y);
        double maxTv = 0.0;
        for (int[] group : groupsVarying(dims, k, rows)) {
            for (int a = 0; a < group.length; a++) {
                for (int b = a + 1; b < group.length; b++) {
                    double tv = 0.0;
                    boolean ok = true;
                    for (int c = 0; c < card; c++) {
                        double pa = im.getProbability(y, group[a], c);
                        double pb = im.getProbability(y, group[b], c);
                        if (!Double.isFinite(pa) || !Double.isFinite(pb)) {
                            ok = false;
                            break;
                        }
                        tv += Math.abs(pa - pb);
                    }
                    if (ok) maxTv = Math.max(maxTv, 0.5 * tv);
                }
            }
        }
        String desc = String.format("Largest total-variation shift in P(child) across %s of %s = %.2f.",
                binned ? "bins" : "values", parentName, maxTv);
        return new Strength(Kind.TABULAR, maxTv, desc);
    }

    /**
     * Rows are indexed in mixed radix over {@code dims} with the first dimension most significant (matching
     * {@link HybridCgPm#getRowIndex}). Returns, for each configuration of the other dimensions, the rows obtained by
     * letting dimension {@code k} range over its values, in value order.
     */
    private static List<int[]> groupsVarying(int[] dims, int k, int rows) {
        List<int[]> groups = new ArrayList<>();
        if (dims.length == 0 || dims[k] <= 0) return groups;
        int stride = 1;
        for (int j = k + 1; j < dims.length; j++) stride *= dims[j];
        for (int base = 0; base < rows; base++) {
            if ((base / stride) % dims[k] != 0) continue;
            int[] group = new int[dims[k]];
            for (int v = 0; v < dims[k]; v++) group[v] = base + v * stride;
            groups.add(group);
        }
        return groups;
    }
}
