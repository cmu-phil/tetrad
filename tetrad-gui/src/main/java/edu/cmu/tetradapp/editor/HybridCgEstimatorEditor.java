package edu.cmu.tetradapp.editor;

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
 * Editor for Hybrid CG estimator results.
 * Left: graph workbench. Right: tabs with (1) Estimator panel and (2) Estimated Model (appears after estimation).
 */
public class HybridCgEstimatorEditor extends JPanel {

    @Serial private static final long serialVersionUID = 1L;

    /** Container holding the workbench + tabs. */
    private final JPanel targetPanel;

    /** Estimator wrapper (parallel to BayesEstimatorWrapper). */
    private final HybridCgEstimatorWrapper wrapper;

    /** Right-side estimator panel. */
    private HybridCgEstimatorEditorWizard wizard;

    /** Right-side tabs so we can add the “Estimated Model” tab after estimation. */
    private JTabbedPane rightTabs;

    /** The “Estimated Model” tab contents (created lazily). */
    private Component estimatedTabContent;

    public HybridCgEstimatorEditor(HybridCgModel.HybridCgIm im, DataSet dataSet, Parameters parameters) {
        this(new HybridCgEstimatorWrapper(
                new DataWrapper(dataSet),
                new HybridCgPmWrapper(im.getPm()),
                parameters
        ));
    }

    public HybridCgEstimatorEditor(HybridCgEstimatorWrapper estWrapper) {
        this.wrapper = estWrapper;

        setLayout(new BorderLayout());
        this.targetPanel = new JPanel(new BorderLayout());
        resetHybridImEditor();
        add(this.targetPanel, BorderLayout.CENTER);
        validate();

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

    /** Rebuild the UI for the currently selected model. */
    private void resetHybridImEditor() {
        JPanel panel = new JPanel(new BorderLayout());

        // Left: graph workbench
        HybridCgModel.HybridCgIm hybridIm = this.wrapper.getEstimatedHybridCgIm();
        HybridCgModel.HybridCgPm hybridPm = hybridIm.getPm();
        Graph graph = hybridPm.getGraph();
        GraphWorkbench workbench = new GraphWorkbench(graph);

        // Right: tabs
        rightTabs = new JTabbedPane();

        // Estimator tab (wizard)
        DataSet dataSet = this.wrapper.getDataSet();
        HybridCgPmWrapper pmWrapperForWizard = new HybridCgPmWrapper(hybridPm);
        this.wizard = new HybridCgEstimatorEditorWizard(pmWrapperForWizard, dataSet);

        // When estimation finishes, the wizard fires "editorValueChanged" with the new HybridCgImWrapper.
        this.wizard.addPropertyChangeListener(evt -> {
            if ("editorValueChanged".equals(evt.getPropertyName())) {
                Object nv = evt.getNewValue();
                if (nv instanceof HybridCgImWrapper imw) {
                    // Install (or replace) the “Estimated Model” tab and switch to it
                    installEstimatedModelTab(imw);
                    rightTabs.setSelectedIndex(1); // 0 = Estimator, 1 = Estimated Model
                    // Bubble up to the app that the model changed
                    firePropertyChange("modelChanged", null, imw);
                } else {
                    // Still tell upstream something changed
                    firePropertyChange("modelChanged", null, nv);
                }
            }
        });

        JScrollPane wizardScroll = new JScrollPane(getWizard());

        // Stats tab content as text (optional: keep inside “Estimator” tab or move to a separate tab)
        String stats = buildModelStatsText(hybridIm, dataSet);
        JTextArea modelStats = new JTextArea(stats);
        modelStats.setEditable(false);
        JScrollPane statsScroll = new JScrollPane(modelStats);

        // Put estimator + stats into a small right-side vertical split (optional). To keep parity with Bayes,
        // we’ll just make a two-tab right panel: Estimator | Estimated Model. Stats are embedded at the bottom of Estimator.
        JPanel estimatorHost = new JPanel(new BorderLayout());
        estimatorHost.add(wizardScroll, BorderLayout.CENTER);
        estimatorHost.add(statsScroll, BorderLayout.SOUTH);

        rightTabs.addTab("Estimator", estimatorHost);
        // “Estimated Model” tab is added dynamically after user runs an estimation.

        // Split: left graph | right tabs
        JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, workbenchScroll, rightTabs);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);

        setLayout(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);

        setName("Hybrid CG Estimator");

        // Menu bar with Save Graph Image
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

    /** Create/update the “Estimated Model” tab with a live HybridCgImEditor bound to the given IM wrapper. */
    private void installEstimatedModelTab(HybridCgImWrapper imw) {
        HybridCgImEditor imEditor = new HybridCgImEditor(imw);

        JScrollPane estimatedScroll = new JScrollPane(imEditor);

        if (estimatedTabContent == null) {
            rightTabs.addTab("Estimated Model", estimatedScroll);
            estimatedTabContent = estimatedScroll;
        } else {
            int idx = rightTabs.indexOfComponent(estimatedTabContent);
            if (idx >= 0) {
                rightTabs.setComponentAt(idx, estimatedScroll);
                rightTabs.setTitleAt(idx, "Estimated Model");
                estimatedTabContent = estimatedScroll;
            } else {
                rightTabs.addTab("Estimated Model", estimatedScroll);
                estimatedTabContent = estimatedScroll;
            }
        }
    }

    /** Lightweight stats. */
    private static String buildModelStatsText(HybridCgModel.HybridCgIm im, DataSet dataSet) {
        StringBuilder buf = new StringBuilder();
        NumberFormat nf = new DecimalFormat("0.000");

        buf.append("Hybrid CG Estimation Summary\n");
        buf.append("----------------------------\n");
        buf.append("Rows (N): ").append(dataSet != null ? dataSet.getNumRows() : "—").append('\n');
        buf.append("Variables: ").append(dataSet != null ? dataSet.getNumColumns() : "—").append('\n');
        buf.append('\n');

        HybridCgModel.HybridCgPm pm = im.getPm();
        edu.cmu.tetrad.graph.Node[] nodes = pm.getNodes();
        int discrete = 0, continuous = 0;
        for (int i = 0; i < nodes.length; i++) {
            if (pm.isDiscrete(i)) discrete++; else continuous++;
        }
        buf.append("Discrete vars: ").append(discrete).append('\n');
        buf.append("Continuous vars: ").append(continuous).append('\n');

        // Placeholders for future metrics:
        // buf.append("Log-likelihood: ").append(nf.format(...)).append('\n');
        // buf.append("BIC: ").append(nf.format(...)).append('\n');

        return buf.toString();
    }
}