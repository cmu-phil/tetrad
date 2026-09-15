package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.EdgeShading;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.CVReport;
import edu.cmu.tetrad.sem.EdgeStrengthResult;
import edu.cmu.tetrad.sem.NodeCVSummary;
import edu.cmu.tetrad.sem.PartialEdgeStrengthResult;
import edu.cmu.tetradapp.model.NNEstimatorModel;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.WorkbenchStyle;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Graph tab for {@link NNEstimatorComparePanel}: shows the working DAG with
 * edges shaded by a statistic chosen from a dropdown. Statistics are read
 * from what the other tabs have already computed and persisted on the
 * {@link NNEstimatorModel}; this tab computes nothing itself.
 *
 * <p>The four statistics:
 * <ol>
 *   <li><b>Edge strength (MMD²)</b> — the interventional strength from the
 *       Edge Strength tab; unsigned, shaded relative to the largest computed
 *       value.</li>
 *   <li><b>Partial strength</b> — partial R² for continuous children (shaded
 *       on its absolute [0, 1] scale) or partial cross-entropy improvement
 *       for discrete children (shaded relative to the largest computed
 *       value); unsigned.</li>
 *   <li><b>Marginal effect (signed)</b> — the standardized average marginal
 *       effect; blue for positive, vermillion for negative, reddish purple
 *       when the derivative's sign varies across the data. Defined only for
 *       continuous parent and child.</li>
 *   <li><b>CV fit of child</b> — the child node's cross-validation statistic
 *       (OOS R² or cross-entropy improvement) from the Cross-Validation tab.
 *       This is a <i>node</i> statistic, so every edge into the child gets
 *       the same shade; the tooltip says so.</li>
 * </ol>
 *
 * <p>Edges whose selected statistic has not been computed are drawn in the
 * default line color with a tooltip saying which tab to run. Intensities use
 * a square-root spread, as in {@code HybridCgEdgeStrengths}, so one large
 * value does not push everything else into the palest shade.
 */
final class NNEstimatorGraphPanel extends JPanel {

    // ── statistic choices ─────────────────────────────────────────────────────

    private static final String STAT_MMD2     = "Edge strength (interventional MMD\u00B2)";
    private static final String STAT_PARTIAL  = "Partial strength (partial R\u00B2 / xent improvement)";
    private static final String STAT_MARGINAL = "Marginal effect (signed, standardized)";
    private static final String STAT_CV       = "Cross-validation fit of child (R\u00B2 / xent improvement)";

    // ── components ────────────────────────────────────────────────────────────

    private final NNEstimatorModel model;
    private final GraphWorkbench workbench;
    private final JComboBox<String> statCombo = new JComboBox<>(
            new String[]{STAT_MMD2, STAT_PARTIAL, STAT_MARGINAL, STAT_CV});
    private final JLabel coverageLabel = new JLabel(" ");
    private final JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));

    NNEstimatorGraphPanel(NNEstimatorModel model) {
        super(new BorderLayout(8, 8));
        this.model = model;

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        this.workbench = new GraphWorkbench(new EdgeListGraph());
        this.workbench.setEnableEditing(false);
        this.workbench.setAllowDoubleClickActions(false);
        this.workbench.setAllowEdgeReorientations(false);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());
        statCombo.addActionListener(e -> refresh());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Shade edges by:"));
        controls.add(statCombo);
        controls.add(refreshButton);
        controls.add(coverageLabel);

        JPanel south = new JPanel(new BorderLayout());
        legendPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        south.add(legendPanel, BorderLayout.CENTER);

        add(controls, BorderLayout.NORTH);
        add(new JScrollPane(workbench), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        refresh();
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    /**
     * Rebuilds the colored graph from the model's current working graph and
     * persisted results for the selected statistic. Cheap; safe to call on
     * every tab selection.
     */
    void refresh() {
        String stat = (String) statCombo.getSelectedItem();
        boolean dark = WorkbenchStyle.isDarkMode();

        Graph src = model.getWorkingGraph();
        Graph colored = new EdgeListGraph(src.getNodes());

        // Newest result per (parent, child): stored oldest-first, so later
        // entries overwrite earlier ones.
        Map<String, NNEstimatorModel.EdgeStrengthPair> byEdge = new HashMap<>();
        for (NNEstimatorModel.EdgeStrengthPair p : model.getEdgeStrengthResults()) {
            byEdge.put(key(p.edge().parentName, p.edge().childName), p);
        }

        Map<String, NodeCVSummary> byNode = new HashMap<>();
        CVReport cv = model.getCvReport();
        if (cv != null) {
            for (NodeCVSummary s : cv.nodeSummaries) byNode.put(s.node, s);
        }

        int computed = 0;
        int total = 0;

        // Normalizers over the computed values.
        double maxMmd2 = 0.0, maxXent = 0.0, maxAbsEffect = 0.0, maxCvXentImp = 0.0;
        for (NNEstimatorModel.EdgeStrengthPair p : byEdge.values()) {
            EdgeStrengthResult e = p.edge();
            if (Double.isFinite(e.mmd2)) maxMmd2 = Math.max(maxMmd2, e.mmd2);
            PartialEdgeStrengthResult q = p.partial();
            if (q != null) {
                if (q.discreteChild && Double.isFinite(q.partialXentImprovement)) {
                    maxXent = Math.max(maxXent, q.partialXentImprovement);
                }
                double eff = effectValue(q);
                if (Double.isFinite(eff)) maxAbsEffect = Math.max(maxAbsEffect, Math.abs(eff));
            }
        }
        for (NodeCVSummary s : byNode.values()) {
            if (s.discreteChild && Double.isFinite(s.oosXent) && Double.isFinite(s.baselineXent)) {
                maxCvXentImp = Math.max(maxCvXentImp, s.baselineXent - s.oosXent);
            }
        }

        for (Edge e : src.getEdges()) {
            Edge copy = new Edge(e);
            total++;

            // The working graph is a DAG, so every edge is directed.
            Node parent = Edges.isDirectedEdge(e) ? Edges.getDirectedEdgeTail(e) : e.getNode1();
            Node child  = Edges.isDirectedEdge(e) ? Edges.getDirectedEdgeHead(e) : e.getNode2();

            String annotation;
            Color color = null;

            switch (stat == null ? STAT_MMD2 : stat) {
                case STAT_PARTIAL -> {
                    NNEstimatorModel.EdgeStrengthPair p =
                            byEdge.get(key(parent.getName(), child.getName()));
                    PartialEdgeStrengthResult q = p == null ? null : p.partial();
                    if (q == null) {
                        annotation = notComputedEdge();
                    } else if (!q.discreteChild && Double.isFinite(q.partialR2)) {
                        double t = Math.sqrt(clamp01(q.partialR2));
                        color = EdgeShading.color(EdgeShading.Hue.UNSIGNED, t, dark);
                        annotation = String.format(
                                "Partial R\u00B2 = %.4f over %d folds. "
                                + "Shaded on the absolute [0, 1] scale (square-root spread).",
                                q.partialR2, q.numFolds);
                    } else if (q.discreteChild && Double.isFinite(q.partialXentImprovement)) {
                        double t = maxXent > 0
                                ? Math.sqrt(clamp01(q.partialXentImprovement / maxXent)) : 0.0;
                        color = EdgeShading.color(EdgeShading.Hue.UNSIGNED, t, dark);
                        annotation = String.format(
                                "Partial cross-entropy improvement = %.4f nats over %d folds. "
                                + "Shaded relative to the largest computed improvement.",
                                q.partialXentImprovement, q.numFolds);
                    } else {
                        annotation = "Partial strength was computed but is not finite for this edge.";
                    }
                }
                case STAT_MARGINAL -> {
                    NNEstimatorModel.EdgeStrengthPair p =
                            byEdge.get(key(parent.getName(), child.getName()));
                    PartialEdgeStrengthResult q = p == null ? null : p.partial();
                    double eff = q == null ? Double.NaN : effectValue(q);
                    if (q == null) {
                        annotation = notComputedEdge();
                    } else if (!Double.isFinite(eff)) {
                        annotation = "Marginal effect is defined only when both parent and child "
                                     + "are continuous.";
                    } else {
                        double t = maxAbsEffect > 0
                                ? Math.sqrt(clamp01(Math.abs(eff) / maxAbsEffect)) : 0.0;
                        if (q.isNonMonotone()) {
                            color = EdgeShading.color(EdgeShading.Hue.MIXED, t, dark);
                            annotation = String.format(
                                    "Standardized average marginal effect = %.4f, but the "
                                    + "derivative's sign varies across the data (%.0f%% positive), "
                                    + "so the average understates the relationship.",
                                    eff, 100 * q.fracPositiveDeriv);
                        } else {
                            color = EdgeShading.signed(eff, t, dark);
                            annotation = String.format(
                                    "Standardized average marginal effect = %.4f "
                                    + "(SD units of child per SD of parent). "
                                    + "Shaded relative to the largest computed |effect|.", eff);
                        }
                    }
                }
                case STAT_CV -> {
                    NodeCVSummary s = byNode.get(child.getName());
                    if (s == null) {
                        annotation = cv == null
                                ? "Not computed. Run the Cross-Validation tab first."
                                : "No cross-validation summary for child " + child.getName() + ".";
                    } else if (!s.discreteChild && Double.isFinite(s.oosR2)) {
                        double t = Math.sqrt(clamp01(s.oosR2));
                        color = EdgeShading.color(EdgeShading.Hue.UNSIGNED, t, dark);
                        annotation = String.format(
                                "OOS R\u00B2 = %.4f for child %s over %d folds. "
                                + "This is a statistic of the child node; every edge into %s "
                                + "carries the same shade. Negative R\u00B2 is shown as the "
                                + "palest shade.",
                                s.oosR2, s.node, s.numFolds, s.node);
                    } else if (s.discreteChild && Double.isFinite(s.oosXent)
                               && Double.isFinite(s.baselineXent)) {
                        double imp = s.baselineXent - s.oosXent;
                        double t = maxCvXentImp > 0
                                ? Math.sqrt(clamp01(imp / maxCvXentImp)) : 0.0;
                        color = EdgeShading.color(EdgeShading.Hue.UNSIGNED, t, dark);
                        annotation = String.format(
                                "OOS cross-entropy improvement = %.4f nats for child %s over "
                                + "%d folds. This is a statistic of the child node; every edge "
                                + "into %s carries the same shade.",
                                imp, s.node, s.numFolds, s.node);
                    } else {
                        annotation = "Cross-validation ran, but the statistic is not finite "
                                     + "for child " + child.getName() + ".";
                    }
                }
                default -> {  // STAT_MMD2
                    NNEstimatorModel.EdgeStrengthPair p =
                            byEdge.get(key(parent.getName(), child.getName()));
                    EdgeStrengthResult r = p == null ? null : p.edge();
                    if (r == null || !Double.isFinite(r.mmd2)) {
                        annotation = r == null ? notComputedEdge()
                                : "Edge strength was computed but is not finite for this edge.";
                    } else {
                        double t = maxMmd2 > 0 ? Math.sqrt(clamp01(r.mmd2 / maxMmd2)) : 0.0;
                        color = EdgeShading.color(EdgeShading.Hue.UNSIGNED, t, dark);
                        String noise = r.isAboveNoise()
                                ? "Above the null refit noise band."
                                : "NOT distinguishable from null refit noise.";
                        annotation = String.format(
                                "Interventional MMD\u00B2 = %.5f \u00B1 %.5f. %s "
                                + "Shaded relative to the largest computed MMD\u00B2.",
                                r.mmd2, r.mmd2Sd, noise);
                    }
                }
            }

            if (color != null) {
                computed++;
                copy.setLineColor(color);
            }
            copy.setAnnotation(annotation);
            colored.addEdge(copy);
        }

        workbench.setGraph(colored);
        setTooltips();
        updateLegend(stat, dark);

        coverageLabel.setText(total == 0 ? "The working graph has no edges."
                : String.format("%d of %d edges have a computed value for this statistic.",
                computed, total));
        revalidate();
        repaint();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String key(String parent, String child) {
        return parent + "\u2192" + child;
    }

    private static String notComputedEdge() {
        return "Not computed. Use the Edge Strength tab (Compute Parent Strengths or "
               + "Compute All) first.";
    }

    /** Standardized effect when finite, else the raw effect, else NaN. */
    private static double effectValue(PartialEdgeStrengthResult q) {
        if (Double.isFinite(q.avgMarginalEffectStd)) return q.avgMarginalEffectStd;
        return q.avgMarginalEffect;
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Editing is off, so the workbench will not build edge tooltips on hover;
     * set them directly from the annotations, as in {@code HybridCgGraphViewer}.
     */
    private void setTooltips() {
        for (Edge edge : workbench.getGraph().getEdges()) {
            String a = edge.getAnnotation();
            if (a == null) continue;
            a = a.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            workbench.setEdgeToolTip(edge, "<html>" + edge.getNode1().getName() + " \u2192 "
                    + edge.getNode2().getName()
                    + "<br><div style='width:320px'>" + a + "</div></html>");
        }
    }

    private void updateLegend(String stat, boolean dark) {
        legendPanel.removeAll();
        String note;
        if (STAT_MARGINAL.equals(stat)) {
            legendPanel.add(ramp(EdgeShading.Hue.POSITIVE, dark, "Positive"));
            legendPanel.add(ramp(EdgeShading.Hue.NEGATIVE, dark, "Negative"));
            legendPanel.add(ramp(EdgeShading.Hue.MIXED, dark, "Sign varies"));
            note = "Darker = larger |standardized effect|, relative to the largest computed. "
                   + "Hover an edge for values.";
        } else {
            legendPanel.add(ramp(EdgeShading.Hue.UNSIGNED, dark, "Strength"));
            if (STAT_CV.equals(stat)) {
                note = "Node statistic: every edge into a child carries the child's shade. "
                       + "Darker = better fit. Hover an edge for values.";
            } else if (STAT_PARTIAL.equals(stat)) {
                note = "Darker = stronger. Partial R\u00B2 is on its absolute scale; "
                       + "cross-entropy improvements are relative to the largest computed. "
                       + "Hover an edge for values.";
            } else {
                note = "Darker = stronger, relative to the largest computed MMD\u00B2. "
                       + "Hover an edge for values.";
            }
        }
        note += " Unshaded edges have no computed value.";
        JLabel l = new JLabel(note);
        l.setFont(l.getFont().deriveFont(Font.ITALIC, l.getFont().getSize2D() - 1f));
        legendPanel.add(l);
        legendPanel.revalidate();
        legendPanel.repaint();
    }

    private static JComponent ramp(EdgeShading.Hue hue, boolean dark, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        row.add(swatch(EdgeShading.color(hue, 0.15, dark)));
        row.add(swatch(EdgeShading.color(hue, 0.6, dark)));
        row.add(swatch(EdgeShading.color(hue, 1.0, dark)));
        row.add(new JLabel(text));
        return row;
    }

    private static JComponent swatch(Color c) {
        JLabel l = new JLabel();
        l.setOpaque(true);
        l.setBackground(c);
        l.setPreferredSize(new Dimension(12, 12));
        return l;
    }
}
