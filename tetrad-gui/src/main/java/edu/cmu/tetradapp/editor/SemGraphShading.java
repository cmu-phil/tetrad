package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeShading;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.ISemIm;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.IDisplayEdge;

import java.awt.Color;
import java.awt.Component;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/**
 * Edge shading and tooltip logic shared by the SEM IM editor and the SEM estimator editor, which each carry their own
 * copy of the graphical SEM editor. Everything here is a pure function of the model; the editors call it from their
 * {@code resetLabels()} so the display is re-derived after every change.
 */
final class SemGraphShading {

    private SemGraphShading() {
    }

    /**
     * Applies or clears per-edge line colors on the workbench's display edges. Coefficient edges are shaded by sign,
     * with intensity the square root of |coef| relative to the largest |coef| in the model; error covariance edges are
     * shaded by the sign of the covariance with intensity the square root of the implied |correlation|. Edges without
     * a parameter (e.g. edges to error nodes) get the default color. Display edges are recreated whenever the
     * workbench graph changes, so call this from resetLabels rather than once.
     *
     * @param workbench     the workbench displaying (a copy of) the graph
     * @param graph         the model graph
     * @param semIm         the model
     * @param implCovar     the implied covariance matrix, for correlations
     * @param shade         false clears all shading
     * @param edgeParameter maps an edge to its parameter, or null
     */
    static void applyEdgeShading(GraphWorkbench workbench, Graph graph, ISemIm semIm, Matrix implCovar,
                                 boolean shade, Function<Edge, Parameter> edgeParameter) {
        Map<Edge, Object> display = workbench.getModelEdgesToDisplay();

        double maxAbsCoef = 0.0;
        if (shade) {
            for (Edge edge : graph.getEdges()) {
                Parameter p = edgeParameter.apply(edge);
                if (p != null && p.getType() == ParamType.COEF) {
                    double v = semIm.getParamValue(p);
                    if (Double.isFinite(v)) maxAbsCoef = Math.max(maxAbsCoef, Math.abs(v));
                }
            }
        }

        for (Edge edge : graph.getEdges()) {
            Object o = display.get(edge);
            if (!(o instanceof IDisplayEdge displayEdge)) continue;

            Color color = null;

            if (shade) {
                Parameter p = edgeParameter.apply(edge);
                if (p != null) {
                    double val = semIm.getParamValue(p);
                    double intensity = 0.0;

                    if (p.getType() == ParamType.COEF) {
                        intensity = maxAbsCoef > 0 ? Math.sqrt(Math.abs(val) / maxAbsCoef) : 0.0;
                    } else if (p.getType() == ParamType.COVAR) {
                        double varA = semIm.getVariance(edge.getNode1(), implCovar);
                        double varB = semIm.getVariance(edge.getNode2(), implCovar);
                        double corr = val / TMath.sqrt(varA * varB);
                        intensity = Double.isFinite(corr) ? Math.sqrt(Math.min(1.0, Math.abs(corr))) : 0.0;
                    }

                    if (Double.isFinite(val) && Double.isFinite(intensity)) {
                        color = EdgeShading.signed(val, intensity);
                    }
                }
            }

            displayEdge.setLineColor(color);
            if (displayEdge instanceof Component c) c.repaint();
        }
    }

    /**
     * Sets or clears the edge tooltip. The workbench only builds edge tooltips on mouse-enter when editing is enabled,
     * and the SEM workbenches have editing disabled, so the tooltip is set directly. The annotation is also set on the
     * workbench's copy of the model edge so the standard tooltip path, if it ever runs, shows the same information.
     *
     * @param workbench the workbench
     * @param edge      an edge of the model graph
     * @param text      tooltip body, or null to clear
     */
    static void setEdgeAnnotation(GraphWorkbench workbench, Edge edge, String text) {
        Object o = workbench.getModelEdgesToDisplay().get(edge);
        if (!(o instanceof IDisplayEdge displayEdge)) return;

        if (displayEdge.getModelEdge() != null) {
            displayEdge.getModelEdge().setAnnotation(text);
        }

        if (text == null) {
            workbench.setEdgeToolTip(edge, null);
        } else {
            workbench.setEdgeToolTip(edge, "<html>" + edge.getNode1().getName() + " "
                    + (Edges.isBidirectedEdge(edge) ? "&lt;-&gt;" : "--&gt;") + " " + edge.getNode2().getName()
                    + "<br>" + text.replace("<", "&lt;").replace(">", "&gt;") + "</html>");
        }
    }

    /**
     * The name of the node's error term. When error terms are hidden the SemGraph drops error nodes from its map, so
     * fall back to the naming convention the graph uses when it creates them.
     *
     * @param semGraph the model graph
     * @param node     a non-error node
     * @return the error term's name
     */
    static String errorTermName(SemGraph semGraph, Node node) {
        Node exo = semGraph.getExogenous(node);
        if (exo != null && exo != node) return exo.getName();
        return "E_" + node.getName();
    }

    /**
     * Formats "  (SE=..., T=..., P=...)" for a parameter.
     *
     * @param semIm     the model
     * @param parameter the parameter
     * @param maxFree   max free parameters for statistics
     * @param fmt       number formatter (the editor's asString)
     * @return the formatted statistics
     */
    static String stats(ISemIm semIm, Parameter parameter, int maxFree, DoubleFunction<String> fmt) {
        return "  (SE=" + fmt.apply(semIm.getStandardError(parameter, maxFree))
                + ", T=" + fmt.apply(semIm.getTValue(parameter, maxFree))
                + ", P=" + fmt.apply(semIm.getPValue(parameter, maxFree)) + ")";
    }

    /**
     * Tooltip line describing a node's error variance: "E_X ~ N(0, sd), Var = v" (or the unstandardized SD when the
     * display is in correlation mode), followed by the parameter's statistics.
     *
     * @param semIm          the model
     * @param node           a non-error node
     * @param varianceParam  the node's (error) variance parameter
     * @param asCorrelations whether the display is showing correlations
     * @param maxFree        max free parameters for statistics
     * @param fmt            number formatter (the editor's asString)
     * @return the line
     */
    static String errorVarianceLine(ISemIm semIm, Node node, Parameter varianceParam, boolean asCorrelations,
                                    int maxFree, DoubleFunction<String> fmt) {
        double errVar = semIm.getParamValue(varianceParam);
        String name = errorTermName(semIm.getSemPm().getGraph(), node);
        String line = asCorrelations
                ? "SD(" + name + ") = " + fmt.apply(TMath.sqrt(errVar)) + " (unstandardized)"
                : name + " ~ N(0, " + fmt.apply(TMath.sqrt(errVar)) + "), Var = " + fmt.apply(errVar);
        return line + stats(semIm, varianceParam, maxFree, fmt);
    }
}
