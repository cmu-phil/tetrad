package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.HybridCgEstimatorWrapper;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TMath;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hybrid CG Estimator Editor
 *
 * <ul>
 *   <li><b>Left:</b> Estimation settings and “Estimate” button.
 *   <li><b>Right:</b> The wrapper's current estimated IM, shown in a {@link HybridCgImEditor}.
 * </ul>
 *
 * <p>On open, the IM already held by the wrapper is shown. Pressing <i>Estimate</i> re-runs the
 * estimator with the current settings, stores the result back into the wrapper via
 * {@link HybridCgEstimatorWrapper#setHybridCgIm}, and replaces the display on the right, so what
 * is shown is what downstream boxes receive. The IM editor fires <code>modelChanged</code> events,
 * which this editor re-fires so upstream listeners only need to listen to this container.</p>
 */
public final class HybridCgEstimatorEditor extends JPanel {

    // ---------- Estimation parameters UI ----------
    private final Parameters params;

    private final JSpinner alpha      = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1e6, 0.1));
    private final JCheckBox shareVar  = new JCheckBox("Share variance across strata", false);
    private final JComboBox<String> binPolicy =
            new JComboBox<>(new String[]{"equal_frequency", "equal_interval", "none"});
    private final JSpinner bins       = new JSpinner(new SpinnerNumberModel(3, 2, 50, 1));

    private final JSpinner defBins    = new JSpinner(new SpinnerNumberModel(3, 2, 50, 1));
    private final JSpinner defLo      = new JSpinner(new SpinnerNumberModel(-1.0, -1e6, 1e6, 0.1));
    private final JSpinner defHi      = new JSpinner(new SpinnerNumberModel( 1.0, -1e6, 1e6, 0.1));

    // ---------- The session wrapper whose IM we show and update ----------
    private final HybridCgEstimatorWrapper wrapper;
    private final DataWrapper dataWrapper;
    private final HybridCgPmWrapper pmWrapper;

    // ---------- IM display host on the right ----------
    private final JPanel imHost = new JPanel(new BorderLayout());
    private final JPanel graphHost = new JPanel(new BorderLayout());
    private final JLabel bicLabel = new JLabel("BIC: n/a");
    private final JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

    // ---------- Constructors ----------

    public HybridCgEstimatorEditor(HybridCgEstimatorWrapper wrapper) {
        this.wrapper     = wrapper;
        this.dataWrapper = wrapper.getDataWrapper();
        this.pmWrapper   = wrapper.getPmWrapper();
        Parameters params = wrapper.getParameters();
        this.params      = (params == null) ? new Parameters() : params;

        setLayout(new BorderLayout());

        // Left: settings panel
        JPanel settings = buildSettingsPanel();

        // Right: tabs for the estimated IM and its shaded graph, with the BIC line below.
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Estimated IM", imHost);
        tabs.addTab("Graph", graphHost);
        tabs.setToolTipTextAt(1, "Model graph with edges shaded by estimated strength");

        statusBar.add(bicLabel);

        JPanel right = new JPanel(new BorderLayout());
        right.add(tabs, BorderLayout.CENTER);
        right.add(statusBar, BorderLayout.SOUTH);

        settings.setPreferredSize(new Dimension(320, 400));
        right.setPreferredSize(new Dimension(600, 400));

        // Split
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, settings, right);
        split.setResizeWeight(0.30);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        loadFromParams();
        wireBindings();

        // Show the IM the wrapper already holds.
        HybridCgIm im = wrapper.getEstimatedHybridCgIm();
        if (im != null) {
            showIm(im);
            updateBic();
        } else {
            imHost.add(makeEmptyImPanel(), BorderLayout.CENTER);
            graphHost.add(makeEmptyImPanel(), BorderLayout.CENTER);
        }
    }

    // ---------- UI building ----------

    private JPanel buildSettingsPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new TitledBorder("Hybrid CG Estimation Settings"));

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.anchor = GridBagConstraints.WEST;

        int r = 0;
        c.gridx=0; c.gridy=r; p.add(new JLabel("Dirichlet alpha:"), c);
        c.gridx=1; p.add(alpha, c); r++;

        c.gridx=0; c.gridy=r; c.gridwidth=2; p.add(shareVar, c); r++; c.gridwidth=1;

        c.gridx=0; c.gridy=r; p.add(new JLabel("Bin policy:"), c);
        c.gridx=1; p.add(binPolicy, c); r++;

        c.gridx=0; c.gridy=r; p.add(new JLabel("Bins:"), c);
        c.gridx=1; p.add(bins, c); r++;

        p.add(new JSeparator(), grid(c,0,++r,2)); r++;

        c.gridx=0; c.gridy=r; p.add(new JLabel("Fallback default bins:"), c);
        c.gridx=1; p.add(defBins, c); r++;

        c.gridx=0; c.gridy=r; p.add(new JLabel("Default range low:"), c);
        c.gridx=1; p.add(defLo, c); r++;

        c.gridx=0; c.gridy=r; p.add(new JLabel("Default range high:"), c);
        c.gridx=1; p.add(defHi, c); r++;

        root.add(p, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton estimate = new JButton("Estimate");
        estimate.addActionListener(ev -> runEstimate());
        buttons.add(estimate);
        root.add(buttons, BorderLayout.SOUTH);

        return root;
    }

    private JComponent makeEmptyImPanel() {
        JPanel empty = new JPanel(new GridBagLayout());
        JLabel hint = new JLabel("Press “Estimate” to estimate the IM.");
        hint.setForeground(new Color(0x555555));
        empty.add(hint);
        return empty;
    }

    // ---------- Estimation ----------

    private void runEstimate() {
        bicLabel.setText("BIC: …");

        try {
            HybridCgEstimatorWrapper fresh =
                    new HybridCgEstimatorWrapper(dataWrapper, pmWrapper, params);
            HybridCgIm im = fresh.getEstimatedHybridCgIm();

            // Store the new estimate in the session wrapper so downstream boxes see it.
            wrapper.setHybridCgIm(im);

            showIm(im);
            updateBic();
            firePropertyChange("modelChanged", null, null);
        } catch (Exception ex) {
            bicLabel.setText("BIC: n/a");
            JOptionPane.showMessageDialog(
                    this,
                    "Estimation failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void showIm(HybridCgIm im) {
        HybridCgImEditor editor = new HybridCgImEditor(im);
        editor.addPropertyChangeListener("modelChanged",
                evt -> firePropertyChange("modelChanged", null, null));

        imHost.removeAll();
        imHost.add(editor, BorderLayout.CENTER);
        imHost.revalidate();
        imHost.repaint();

        graphHost.removeAll();
        graphHost.add(HybridCgGraphViewer.panel(im), BorderLayout.CENTER);
        graphHost.revalidate();
        graphHost.repaint();
    }

    private void updateBic() {
        try {
            double bic = computeCgBicScore(
                    dataWrapper.getSelectedDataModel(),
                    pmWrapper.getGraph(),
                    params
            );
            bicLabel.setText(String.format("BIC: %.3f (higher is better)", bic));
        } catch (Exception ex) {
            bicLabel.setText("BIC: n/a");
        }
    }

    // ---------- Parameter IO ----------

    private void loadFromParams() {
        alpha.setValue(params.getDouble("hybridcg.alpha", 1.0));
        shareVar.setSelected(params.getBoolean("hybridcg.shareVariance", false));
        binPolicy.setSelectedItem(params.getString("hybridcg.binPolicy", "equal_frequency"));
        bins.setValue(TMath.max(2, params.getInt("hybridcg.bins", 3)));
        defBins.setValue(TMath.max(2, params.getInt("hybridcg.defaultBins", 3)));
        defLo.setValue(params.getDouble("hybridcg.defaultRangeLow", -1.0));
        defHi.setValue(params.getDouble("hybridcg.defaultRangeHigh", 1.0));
    }

    private void wireBindings() {
        alpha.addChangeListener(e -> params.set("hybridcg.alpha", ((Number)alpha.getValue()).doubleValue()));
        shareVar.addActionListener(e -> params.set("hybridcg.shareVariance", shareVar.isSelected()));
        binPolicy.addActionListener(e -> params.set("hybridcg.binPolicy",
                String.valueOf(binPolicy.getSelectedItem()).toLowerCase(Locale.ROOT)));
        bins.addChangeListener(e -> params.set("hybridcg.bins", ((Number)bins.getValue()).intValue()));
        defBins.addChangeListener(e -> params.set("hybridcg.defaultBins", ((Number)defBins.getValue()).intValue()));
        defLo.addChangeListener(e -> params.set("hybridcg.defaultRangeLow", ((Number)defLo.getValue()).doubleValue()));
        defHi.addChangeListener(e -> params.set("hybridcg.defaultRangeHigh", ((Number)defHi.getValue()).doubleValue()));
    }

    private static GridBagConstraints grid(GridBagConstraints c, int x, int y, int w) {
        GridBagConstraints cc = (GridBagConstraints) c.clone();
        cc.gridx = x; cc.gridy = y; cc.gridwidth = w;
        cc.fill = GridBagConstraints.HORIZONTAL; cc.weightx = 1;
        return cc;
    }

    // --- Compute CG-BIC for the current graph on the given data ---
    private static double computeCgBicScore(edu.cmu.tetrad.data.DataModel dataModel,
                                            edu.cmu.tetrad.graph.Graph graph,
                                            edu.cmu.tetrad.util.Parameters params) {
        // penaltyDiscount = 1.0 → standard BIC
        double penalty = params.getDouble("penaltyDiscount", 1.0);
        boolean discretize = params.getBoolean("discretize", false);

        DataSet mixedDataSet = SimpleDataLoader.getMixedDataSet(dataModel);

        edu.cmu.tetrad.search.score.ConditionalGaussianScore score =
                new edu.cmu.tetrad.search.score.ConditionalGaussianScore(
                        mixedDataSet,
                        penalty,
                        discretize
                );

        // If you expose these params in your UI, keep wiring them:
        score.setNumCategoriesToDiscretize(
                params.getInt(edu.cmu.tetrad.util.Params.NUM_CATEGORIES_TO_DISCRETIZE, 4));
        score.setStructurePrior(
                params.getDouble(edu.cmu.tetrad.util.Params.STRUCTURE_PRIOR, 0.0));
        score.setMinSampleSizePerCell(
                params.getInt(edu.cmu.tetrad.util.Params.MIN_SAMPLE_SIZE_PER_CELL, 5));

        graph = GraphUtils.replaceNodes(graph, dataModel.getVariables());

        java.util.List<Node> nodes = mixedDataSet.getVariables();
        Map<Node, Integer> varIndices = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            varIndices.put(node, i);
        }

        double _score = 0.0;

        for (Node node : graph.getNodes()) {
            java.util.List<Node> parents = graph.getParents(node);

            int i = varIndices.get(node);

            int[] parentIndices = new int[parents.size()];
            for (int j = 0; j < parents.size(); j++) {
                parentIndices[j] = varIndices.get(parents.get(j));
            }

            _score += score.localScore(i, parentIndices);
        }

        return _score;
    }

}