package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeStrengths;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeStrengths.Kind;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.WorkbenchStyle;

import javax.swing.*;
import java.awt.*;

/**
 * Builds a read-only view of the graph of a {@link HybridCgIm} with edges colored by strength (see
 * {@link HybridCgEdgeStrengths}), shown in a scrollable workbench with a legend. Hovering an edge shows the underlying
 * coefficient or effect size in the tooltip.
 */
public final class HybridCgGraphViewer {

    private HybridCgGraphViewer() {
    }

    /**
     * Builds a panel showing the colored graph for the given IM in a scroll pane, with a legend below.
     *
     * @param im the instantiated model
     * @return the panel
     */
    public static JComponent panel(HybridCgIm im) {
        Graph colored = HybridCgEdgeStrengths.coloredGraph(im, WorkbenchStyle.isDarkMode());

        GraphWorkbench workbench = new GraphWorkbench(colored);
        workbench.setEnableEditing(false);
        workbench.setAllowDoubleClickActions(false);
        workbench.setAllowEdgeReorientations(false);

        // Editing is off, so the workbench will not build edge tooltips on hover; set them directly from the
        // annotations that coloredGraph put on the edges (they survive the workbench's copy of the graph).
        for (Edge edge : workbench.getGraph().getEdges()) {
            String a = edge.getAnnotation();
            if (a == null) continue;
            a = a.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            workbench.setEdgeToolTip(edge, "<html>" + edge.getNode1().getName() + " → " + edge.getNode2().getName()
                    + "<br><div style='width:320px'>" + a + "</div></html>");
        }

        JPanel content = new JPanel(new BorderLayout());
        content.add(new JScrollPane(workbench), BorderLayout.CENTER);
        content.add(legend(), BorderLayout.SOUTH);
        return content;
    }

    private static JComponent legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 4));
        p.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        p.add(item(Kind.LINEAR_POSITIVE, "Positive linear coefficient"));
        p.add(item(Kind.LINEAR_NEGATIVE, "Negative linear coefficient"));
        p.add(item(Kind.LINEAR_MIXED, "Coefficient sign varies across strata"));
        p.add(item(Kind.TABULAR, "Table effect (distribution shift)"));
        JLabel note = new JLabel("Darker = stronger. Linear shades are relative to the largest |coefficient| in the model; hover an edge for values.");
        note.setFont(note.getFont().deriveFont(Font.ITALIC, note.getFont().getSize2D() - 1f));
        p.add(note);
        return p;
    }

    private static JComponent item(Kind kind, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        boolean dark = WorkbenchStyle.isDarkMode();
        row.add(swatch(HybridCgEdgeStrengths.colorFor(kind, 0.15, dark)));
        row.add(swatch(HybridCgEdgeStrengths.colorFor(kind, 0.6, dark)));
        row.add(swatch(HybridCgEdgeStrengths.colorFor(kind, 1.0, dark)));
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
