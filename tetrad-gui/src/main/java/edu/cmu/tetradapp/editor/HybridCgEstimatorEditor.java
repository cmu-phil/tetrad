package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.hybridcg.HybridCgModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.HybridCgEstimatorWrapper;
import edu.cmu.tetradapp.model.HybridCgImWrapper;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Editor for Hybrid CG estimator results. Mirrors BayesEstimatorEditor:
 * - Shows the graph on the left and an estimation panel on the right.
 * - Supports multiple datasets via a model selector.
 * - Fires "modelChanged" when a new IM is estimated in the right panel.
 *
 * This editor expects HybridCgEstimatorEditorWizard to be a JPanel taking:
 *   new HybridCgEstimatorEditorWizard(HybridCgPmWrapper pmWrapper, DataSet data)
 * and to fire a PropertyChange event "editorValueChanged" with the new HybridCgImWrapper as newValue.
 */
public class HybridCgEstimatorEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Container holding the workbench + tabs. */
    private final JPanel targetPanel;

    /** Estimator wrapper (parallel to BayesEstimatorWrapper). */
    private final HybridCgEstimatorWrapper wrapper;

    /** Right-side panel that runs estimation. */
    private HybridCgEstimatorEditorWizard wizard;

    // ---------------------- Constructors ----------------------

    /**
     * Convenience ctor: take an existing IM + dataset + params and build an estimator wrapper.
     * Note this will (re-)estimate using the graph from the IM's PM over the provided dataset.
     */
    public HybridCgEstimatorEditor(HybridCgModel.HybridCgIm im, DataSet dataSet, Parameters parameters) {
        this(new HybridCgEstimatorWrapper(
                new DataWrapper(dataSet),
                // Build a PM wrapper from the IM's PM directly (uses the PM's graph/types)
                new HybridCgPmWrapper(im.getPm()),
                parameters
        ));
    }

    /**
     * Primary ctor: from an estimator wrapper (one or more datasets).
     */
    public HybridCgEstimatorEditor(HybridCgEstimatorWrapper estWrapper) {
        this.wrapper = estWrapper;

        setLayout(new BorderLayout());

        this.targetPanel = new JPanel(new BorderLayout());
        resetHybridImEditor();

        add(this.targetPanel, BorderLayout.CENTER);
        validate();

        // If there are multiple datasets/models, add a selector like the Bayes editor
        if (this.wrapper.getNumModels() > 1) {
            JComboBox<Integer> comp = new JComboBox<>();
            for (int i = 0; i < this.wrapper.getNumModels(); i++) comp.addItem(i + 1);

            comp.addActionListener(e -> {
                Object sel = comp.getSelectedItem();
                if (sel instanceof Integer idx1) {
                    HybridCgEstimatorEditor.this.wrapper.setModelIndex(idx1 - 1);
                    resetHybridImEditor();
                    validate();
                }
            });

            comp.setMaximumSize(comp.getPreferredSize());

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model "));
            b.add(comp);
            b.add(new JLabel(" from "));
            b.add(new JLabel(this.wrapper.getName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.NORTH);
        }
    }

    @Override
    public void setName(String name) {
        String old = getName();
        super.setName(name);
        this.firePropertyChange("name", old, getName());
    }

    // ---------------------- Internals ----------------------

    private HybridCgEstimatorEditorWizard getWizard() {
        return this.wizard;
    }

    /** Rebuild the right-hand tabs and left-hand graph for the currently selected model. */
    private void resetHybridImEditor() {
        JPanel panel = new JPanel(new BorderLayout());

        // Pull estimated IM and its graph
        HybridCgModel.HybridCgIm hybridIm = this.wrapper.getEstimatedHybridCgIm();
        HybridCgModel.HybridCgPm hybridPm = hybridIm.getPm();
        Graph graph = hybridPm.getGraph();

        // Left: graph workbench
        GraphWorkbench workbench = new GraphWorkbench(graph);

        // Dataset currently selected inside the wrapper
        DataSet dataSet = this.wrapper.getDataSet();

        // Build a PM wrapper to feed the right-side estimation panel
        // Prefer to use the actual HybridCgPm object if your wrapper supports it
        HybridCgPmWrapper pmWrapperForWizard = new HybridCgPmWrapper(hybridPm);

        // Right: the estimator panel (runs MLE with user options)
        this.wizard = new HybridCgEstimatorEditorWizard(pmWrapperForWizard, dataSet);

        // When estimation finishes, the wizard fires "editorValueChanged" with the new HybridCgImWrapper
        this.wizard.addPropertyChangeListener(evt -> {
            if ("editorValueChanged".equals(evt.getPropertyName())) {
                // evt.getNewValue() should be a HybridCgImWrapper; you can read it if you want
                // and/or just propagate that a model changed.
                firePropertyChange("modelChanged", null, evt.getNewValue());
            }
        });

        JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));
        JScrollPane wizardScroll = new JScrollPane(getWizard());

        // --- Model statistics tab (simple summary for now) ---
        String stats = buildModelStatsText(hybridIm, dataSet);
        JTextArea modelStats = new JTextArea(stats);
        modelStats.setEditable(false);
        JScrollPane statsScroll = new JScrollPane(modelStats);

        // Tabs on right
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Estimator", wizardScroll);
        tabbedPane.add("Model Statistics", statsScroll);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, workbenchScroll, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);

        setLayout(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);

        setName("Hybrid CG Estimator");

        // Menu bar with Save Graph Image, like Bayes editor
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        panel.add(menuBar, BorderLayout.NORTH);

        // Replace content
        this.targetPanel.removeAll();
        this.targetPanel.add(panel, BorderLayout.CENTER);
        this.targetPanel.revalidate();
        this.targetPanel.repaint();
    }

    /** Light-weight stats text (safe defaults if your model doesn’t expose likelihood/BIC yet). */
    private static String buildModelStatsText(HybridCgModel.HybridCgIm im, DataSet dataSet) {
        StringBuilder buf = new StringBuilder();
        NumberFormat nf = new DecimalFormat("0.000");

        buf.append("Hybrid CG Estimation Summary\n");
        buf.append("----------------------------\n");
        buf.append("Rows (N): ").append(dataSet != null ? dataSet.getNumRows() : "—").append('\n');
        buf.append("Variables: ").append(dataSet != null ? dataSet.getNumColumns() : "—").append('\n');
        buf.append('\n');

        // Structural summary
        HybridCgModel.HybridCgPm pm = im.getPm();
        edu.cmu.tetrad.graph.Node[] nodes = pm.getNodes();
        int discrete = 0, continuous = 0;
        for (int i = 0; i < nodes.length; i++) {
            if (pm.isDiscrete(i)) discrete++; else continuous++;
        }
        buf.append("Discrete vars: ").append(discrete).append('\n');
        buf.append("Continuous vars: ").append(continuous).append('\n');

        // Placeholders for future estimator metrics:
        // buf.append("Log-likelihood: ").append(nf.format(result.logLik)).append('\n');
        // buf.append("Parameters (k): ").append(result.numParams).append('\n');
        // buf.append("BIC: ").append(nf.format(result.bic)).append('\n');

        return buf.toString();
    }
}