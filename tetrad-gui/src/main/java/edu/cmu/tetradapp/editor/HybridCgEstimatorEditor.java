package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.HybridCgEstimatorWrapper;
import edu.cmu.tetradapp.model.HybridCgImWrapper;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.util.Parameters;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hybrid CG Estimator Editor
 *
 * <ul>
 *   <li><b>Left:</b> Estimation settings and “Estimate & Preview” button.</li>
 *   <li><b>Right:</b> Live preview of the estimated IM using {@link HybridCgImEditor}.</li>
 * </ul>
 *
 * <p>Pressing <i>Estimate & Preview</i> re-runs the estimator with the current settings
 * and replaces the preview on the right. The preview fires <code>modelChanged</code>
 * events, which this editor re-fires so upstream listeners only need to listen
 * to this container.</p>
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

    // ---------- Dependencies we need to (re)run estimation ----------
    private final DataWrapper dataWrapper;
    private final HybridCgPmWrapper pmWrapper;

    // ---------- Preview host on the right ----------
    private final JPanel previewHost = new JPanel(new BorderLayout());
    private HybridCgImEditor currentPreview;   // recreated after each estimate
    private final JLabel bicLabel = new JLabel("BIC: n/a");
    private final JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

    // ---------- Constructors ----------

    /** Convenience: build from an existing estimator wrapper. */
    public HybridCgEstimatorEditor(HybridCgEstimatorWrapper wrapper) {
        this(
                wrapper.getDataWrapper(),
                wrapper.getPmWrapper(),
                wrapper.getParameters()
        );
    }

    public HybridCgEstimatorEditor(DataWrapper dataWrapper,
                                   HybridCgPmWrapper pmWrapper,
                                   Parameters params) {
        this.dataWrapper = dataWrapper;
        this.pmWrapper   = pmWrapper;
        this.params      = (params == null) ? new Parameters() : params;

        setLayout(new BorderLayout());

        // Left: settings panel
        JPanel settings = buildSettingsPanel();

        // Right: preview host (placeholder)
        previewHost.setBorder(new TitledBorder("Estimated IM Preview"));
        previewHost.add(makeEmptyPreview(), BorderLayout.CENTER);

        // status bar (left-aligned)
        statusBar.add(bicLabel);
        previewHost.add(statusBar, BorderLayout.SOUTH);

        settings.setPreferredSize(new Dimension(320, 400));
        previewHost.setPreferredSize(new Dimension(600, 400));

        settings.setPreferredSize(new Dimension(320, 400));
        previewHost.setPreferredSize(new Dimension(600, 400));

        // Split
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, settings, previewHost);
        split.setResizeWeight(0.30);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        loadFromParams();
        wireBindings();
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
        JButton estimate = new JButton("Estimate & Preview");
        estimate.addActionListener(ev -> runEstimateAndShowPreview());
        buttons.add(estimate);
        root.add(buttons, BorderLayout.SOUTH);

        return root;
    }

    private JComponent makeEmptyPreview() {
        JPanel empty = new JPanel(new GridBagLayout());
        JLabel hint = new JLabel("Press “Estimate & Preview” to view the estimated IM.");
        hint.setForeground(new Color(0x555555));
        empty.add(hint);
        return empty;
    }

    // ---------- Estimation + preview ----------

    private void runEstimateAndShowPreview() {
        bicLabel.setText("BIC: …"); // or "BIC: n/a"

        try {
            HybridCgEstimatorWrapper out =
                    new HybridCgEstimatorWrapper(dataWrapper, pmWrapper, params);

            HybridCgIm im = resolveImFromWrapper(out);
            if (im == null) {
                HybridCgImWrapper imw = tryExtractImWrapper(out);
                if (imw != null) im = imw.getHybridCgIm();
            }

            if (im == null) {
                JOptionPane.showMessageDialog(this,
                        "Estimation completed, but no IM was available for preview.",
                        "Note", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            showPreview(im);

            JOptionPane.showMessageDialog(
                    this,
                    "Estimated Hybrid CG IM" +
                    (out.getNumModels() > 1 ? "s" : "") +
                    " for " + out.getNumModels() + " dataset(s).",
                    "Done",
                    JOptionPane.INFORMATION_MESSAGE
            );

            // --- BIC score after preview ---
            try {
                double bic = computeCgBicScore(
                        out.getDataWrapper().getSelectedDataModel(),
                        pmWrapper.getGraph(),
                        params
                );
                bicLabel.setText(String.format("BIC: %.3f (higher is better)", bic));
            } catch (Exception ex) {
                bicLabel.setText("BIC: n/a");
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Estimation failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE
            );
        }
    }

//    private void showPreview(HybridCgIm im) {
//        // Replace the right-hand preview with a fresh HybridCgImEditor
//        HybridCgImEditor editor = new HybridCgImEditor(im);
//        // Bridge modelChanged events upward so callers only need to listen here.
//        editor.addPropertyChangeListener("modelChanged",
//                evt -> firePropertyChange("modelChanged", null, null));
//
//        currentPreview = editor;
//        previewHost.removeAll();
//        previewHost.add(editor, BorderLayout.CENTER);
//        previewHost.revalidate();
//        previewHost.repaint();
//    }

    private void showPreview(HybridCgIm im) {
        HybridCgImEditor editor = new HybridCgImEditor(im);
        editor.addPropertyChangeListener("modelChanged",
                evt -> firePropertyChange("modelChanged", null, null));

        currentPreview = editor;
        previewHost.removeAll();
        previewHost.add(editor, BorderLayout.CENTER);
        previewHost.add(statusBar, BorderLayout.SOUTH); // keep the BIC line
        previewHost.revalidate();
        previewHost.repaint();
    }

    // ---------- Parameter IO ----------

    private void loadFromParams() {
        alpha.setValue(params.getDouble("hybridcg.alpha", 1.0));
        shareVar.setSelected(params.getBoolean("hybridcg.shareVariance", false));
        binPolicy.setSelectedItem(params.getString("hybridcg.binPolicy", "equal_frequency"));
        bins.setValue(Math.max(2, params.getInt("hybridcg.bins", 3)));
        defBins.setValue(Math.max(2, params.getInt("hybridcg.defaultBins", 3)));
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

    // ---------- Helper: tolerant extraction of an IM from the estimator wrapper ----------

//    private static HybridCgIm tryExtractIm(HybridCgEstimatorWrapper w) {
//        try { return (HybridCgIm) w.getClass().getMethod("getHybridCgIm").invoke(w); }
//        catch (Throwable ignore) { }
//        try { return (HybridCgIm) w.getClass().getMethod("getIm").invoke(w); }
//        catch (Throwable ignore) { }
//        return null;
//    }

    // --- Call this instead of the old tryExtract methods ---
    private HybridCgIm resolveImFromWrapper(HybridCgEstimatorWrapper w) {
        // 1) If the wrapper needs an explicit kick, try to run it.
        for (String m : new String[]{"estimate", "run", "execute"}) {
            try {
                Method mm = w.getClass().getMethod(m);
                if (mm.getReturnType() == Void.TYPE) { mm.invoke(w); }
                else { mm.invoke(w); } // ignore return; we're just ensuring it ran
                break;
            } catch (Throwable ignore) {}
        }

        // 2) Direct getters returning HybridCgIm
        for (String m : new String[]{
                "getHybridCgIm", "getIm", "getEstimatedIm", "getResultIm", "getOutputIm"}) {
            try {
                Method mm = w.getClass().getMethod(m);
                Object res = mm.invoke(w);
                if (res instanceof HybridCgIm im) return im;
            } catch (Throwable ignore) {}
        }

        // 3) Getters returning HybridCgImWrapper
        for (String m : new String[]{
                "getHybridCgImWrapper", "getImWrapper", "getResult", "getOutput"}) {
            try {
                Method mm = w.getClass().getMethod(m);
                Object res = mm.invoke(w);
                if (res instanceof HybridCgImWrapper iw) return iw.getHybridCgIm();
            } catch (Throwable ignore) {}
        }

        // 4) Lists of IMs or wrappers
        for (String m : new String[]{
                "getIms", "getEstimatedIms", "getImWrappers", "getHybridCgImWrappers", "getResults"}) {
            try {
                Method mm = w.getClass().getMethod(m);
                Object res = mm.invoke(w);
                if (res instanceof java.util.List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof HybridCgIm im) return im;
                    if (first instanceof HybridCgImWrapper iw) return iw.getHybridCgIm();
                }
            } catch (Throwable ignore) {}
        }

        // 5) Last resort: scan all zero-arg methods for anything that *is* an IM/IM wrapper.
        for (Method m : w.getClass().getMethods()) {
            if (m.getParameterCount() == 0) {
                try {
                    Object res = m.invoke(w);
                    if (res instanceof HybridCgIm im) return im;
                    if (res instanceof HybridCgImWrapper iw) return iw.getHybridCgIm();
                    if (res instanceof java.util.List<?> list && !list.isEmpty()) {
                        Object first = list.get(0);
                        if (first instanceof HybridCgIm im2) return im2;
                        if (first instanceof HybridCgImWrapper iw2) return iw2.getHybridCgIm();
                    }
                } catch (Throwable ignore) {}
            }
        }
        return null; // not found
    }

    private static HybridCgImWrapper tryExtractImWrapper(HybridCgEstimatorWrapper w) {
        try { return (HybridCgImWrapper) w.getClass().getMethod("getHybridCgImWrapper").invoke(w); }
        catch (Throwable ignore) { }
        try { return (HybridCgImWrapper) w.getClass().getMethod("getImWrapper").invoke(w); }
        catch (Throwable ignore) { }
        try {
            Object list = w.getClass().getMethod("getImWrappers").invoke(w);
            if (list instanceof java.util.List<?> l && !l.isEmpty() && l.get(0) instanceof HybridCgImWrapper iw) {
                return iw;
            }
        } catch (Throwable ignore) { }
        return null;
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