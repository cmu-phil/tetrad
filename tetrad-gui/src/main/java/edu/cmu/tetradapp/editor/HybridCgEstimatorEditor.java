package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.HybridCgEstimatorWrapper;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetrad.util.Parameters;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Locale;

public final class HybridCgEstimatorEditor extends JPanel {

    private final Parameters params;

    private final JSpinner alpha = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1e6, 0.1));
    private final JCheckBox shareVar = new JCheckBox("Share variance across strata", false);
    private final JComboBox<String> binPolicy =
            new JComboBox<>(new String[]{"equal_frequency", "equal_interval", "none"});
    private final JSpinner bins = new JSpinner(new SpinnerNumberModel(3, 2, 50, 1));

    private final JSpinner defBins = new JSpinner(new SpinnerNumberModel(3, 2, 50, 1));
    private final JSpinner defLo = new JSpinner(new SpinnerNumberModel(-1.0, -1e6, 1e6, 0.1));
    private final JSpinner defHi = new JSpinner(new SpinnerNumberModel(1.0, -1e6, 1e6, 0.1));

    // --- New convenience ctor: accept the wrapper directly
    public HybridCgEstimatorEditor(HybridCgEstimatorWrapper wrapper) {
        this(
                wrapper.getDataWrapper(),
                // Rebuild a PM wrapper from the same graph + params; category typing comes from node classes
                new HybridCgPmWrapper(wrapper.getGraph(), wrapper.getParameters()),
                wrapper.getParameters()
        );
    }

    public HybridCgEstimatorEditor(DataWrapper dataWrapper, HybridCgPmWrapper pmWrapper, Parameters params) {
        this.params = (params == null) ? new Parameters() : params;
        setLayout(new BorderLayout(10,10));
        add(buildPanel(), BorderLayout.CENTER);
        add(buildButtons(dataWrapper, pmWrapper), BorderLayout.SOUTH);
        loadFromParams();
        wireBindings();
    }

    private JPanel buildPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("Hybrid CG Estimation Settings"));
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

        return p;
    }

    private JPanel buildButtons(DataWrapper dataWrapper, HybridCgPmWrapper pmWrapper) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton estimate = new JButton("Estimate");
        estimate.addActionListener(ev -> {
            try {
                // Run the same pipeline your wrapper ctor uses
                HybridCgEstimatorWrapper out =
                        new HybridCgEstimatorWrapper(dataWrapper, pmWrapper, params);
                JOptionPane.showMessageDialog(
                        this,
                        "Estimated Hybrid CG IM for " + out.getNumModels() + " dataset(s).",
                        "Done",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Estimation failed:\n" + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
        p.add(estimate);
        return p;
    }

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
        cc.gridx = x; cc.gridy = y; cc.gridwidth = w; cc.fill = GridBagConstraints.HORIZONTAL; cc.weightx = 1;
        return cc;
    }
}