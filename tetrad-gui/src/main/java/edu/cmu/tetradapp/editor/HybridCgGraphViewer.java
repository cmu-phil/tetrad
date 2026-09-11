package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeStrengths;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeStrengths.Kind;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetradapp.model.GraphWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * Pops up a window showing the graph of a {@link HybridCgIm} with edges colored by strength (see
 * {@link HybridCgEdgeStrengths}). The graph is shown in a full {@link GraphEditor}, so the usual Graph menu operations
 * are available. Hovering an edge shows the underlying coefficient or effect size in the tooltip.
 */
public final class HybridCgGraphViewer {

    private HybridCgGraphViewer() {
    }

    /**
     * Opens a non-modal window showing the colored graph for the given IM.
     *
     * @param im     the instantiated model
     * @param parent a component used to position the window; may be null
     * @param title  window title; may be null
     */
    public static void show(HybridCgIm im, Component parent, String title) {
        Graph colored = HybridCgEdgeStrengths.coloredGraph(im);
        GraphEditor editor = new GraphEditor(new GraphWrapper(colored));

        JPanel content = new JPanel(new BorderLayout());
        content.add(editor, BorderLayout.CENTER);
        content.add(legend(), BorderLayout.SOUTH);

        JFrame frame = new JFrame(title == null ? "Hybrid CG Graph" : title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(parent);
        frame.setVisible(true);
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
        row.add(swatch(HybridCgEdgeStrengths.colorFor(kind, 0.15)));
        row.add(swatch(HybridCgEdgeStrengths.colorFor(kind, 0.6)));
        row.add(swatch(HybridCgEdgeStrengths.colorFor(kind, 1.0)));
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
