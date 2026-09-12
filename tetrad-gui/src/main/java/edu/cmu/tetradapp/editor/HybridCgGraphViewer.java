package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeStrengths;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeStrengths.Kind;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.WorkbenchStyle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * A persistent, read-only view of the graph of a {@link HybridCgIm} with edges colored by strength (see
 * {@link HybridCgEdgeStrengths}), shown in a scrollable workbench with a legend. Hovering an edge shows the
 * underlying coefficient or effect size in the tooltip.
 *
 * <p>The workbench is created once and updated in place via {@link #update(HybridCgIm)}, so menus and actions bound
 * to it (Find Variable, Graph Properties, Paths, Layout) remain valid across re-estimates and tab switches.</p>
 */
public final class HybridCgGraphViewer {

    private final GraphWorkbench workbench;
    private final JPanel component;

    /**
     * Builds the view. If the model is null, an empty graph is shown until {@link #update(HybridCgIm)} is called.
     *
     * @param imOrNull the instantiated model, or null for an initially empty view
     */
    public HybridCgGraphViewer(HybridCgIm imOrNull) {
        Graph initial = imOrNull == null
                ? new EdgeListGraph()
                : HybridCgEdgeStrengths.coloredGraph(imOrNull, WorkbenchStyle.isDarkMode());

        this.workbench = new GraphWorkbench(initial);
        this.workbench.setEnableEditing(false);
        this.workbench.setAllowDoubleClickActions(false);
        this.workbench.setAllowEdgeReorientations(false);

        setTooltips();

        this.component = new JPanel(new BorderLayout());
        this.component.add(new JScrollPane(this.workbench), BorderLayout.CENTER);
        this.component.add(legend(), BorderLayout.SOUTH);
    }

    /**
     * Recolors the view for the given model, keeping the same workbench instance.
     *
     * @param im the instantiated model
     */
    public void update(HybridCgIm im) {
        Graph colored = HybridCgEdgeStrengths.coloredGraph(im, WorkbenchStyle.isDarkMode());
        this.workbench.setGraph(colored);
        setTooltips();
        this.component.revalidate();
        this.component.repaint();
    }

    /**
     * @return the panel holding the workbench and legend
     */
    public JComponent getComponent() {
        return this.component;
    }

    /**
     * @return the workbench, for binding menus and actions
     */
    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    /**
     * Builds a panel showing the colored graph for the given IM in a scroll pane, with a legend below.
     *
     * @param im the instantiated model
     * @return the panel
     */
    public static JComponent panel(HybridCgIm im) {
        return new HybridCgGraphViewer(im).getComponent();
    }

    /**
     * Builds the standard Graph menu (Graph Properties, Paths, Find Variable) bound to this view's workbench. Each
     * item first runs the given callback — typically one that selects the Graph tab — so the action always operates
     * on a visible graph.
     *
     * @param beforeShow run before each action; may be null
     * @param parameters parameters for the Paths dialog
     * @return the menu
     */
    public JMenu graphMenu(Runnable beforeShow, Parameters parameters) {
        JMenu graph = new JMenu("Graph");

        Action props = new GraphPropertiesAction(this.workbench);
        Action paths = new PathsAction(this.workbench, parameters);
        Action find = new FindVariableAction(this.workbench);

        graph.add(wrap(props, beforeShow,
                KeyStroke.getKeyStroke(KeyEvent.VK_G, java.awt.event.InputEvent.ALT_DOWN_MASK)));
        graph.add(wrap(paths, beforeShow,
                KeyStroke.getKeyStroke(KeyEvent.VK_T, java.awt.event.InputEvent.ALT_DOWN_MASK)));
        graph.add(wrap(find, beforeShow,
                KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx())));

        return graph;
    }

    private static JMenuItem wrap(Action delegate, Runnable beforeShow, KeyStroke accelerator) {
        JMenuItem item = new JMenuItem(new AbstractAction((String) delegate.getValue(Action.NAME)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (beforeShow != null) beforeShow.run();
                delegate.actionPerformed(e);
            }
        });
        if (accelerator != null) item.setAccelerator(accelerator);
        return item;
    }

    /**
     * Editing is off, so the workbench will not build edge tooltips on hover; set them directly from the annotations
     * that coloredGraph put on the edges (they survive the workbench's copy of the graph).
     */
    private void setTooltips() {
        for (Edge edge : this.workbench.getGraph().getEdges()) {
            String a = edge.getAnnotation();
            if (a == null) continue;
            a = a.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            this.workbench.setEdgeToolTip(edge, "<html>" + edge.getNode1().getName() + " → "
                                                + edge.getNode2().getName()
                                                + "<br><div style='width:320px'>" + a + "</div></html>");
        }
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
