package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.UnmixSpec;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class UnmixParamsEditor extends JPanel implements FinalizingParameterEditor {

    private final UnmixSpec spec = new UnmixSpec();
    private Parameters parameters;
    // Basic
    private JCheckBox autoKCheck;
    private JSpinner kSpinner, kminSpinner, kmaxSpinner;

    // Parent-superset
    private JCheckBox supersetCheck;
    private JSpinner topMSpinner;
    private JComboBox<UnmixSpec.SupersetScore> supersetScoreBox;

    // Residual scaling
    private JCheckBox robustScaleCheck;

    // Covariance
    private JRadioButton covAuto, covFull, covDiag;
    private JSpinner safetyMarginSpinner;

    // EM stability
    private JSpinner restartsSpinner, itersSpinner, ridgeSpinner, shrinkageSpinner,
            annealStepsSpinner, annealTSpinner, seedSpinner;

    // Diagnostics
    private JCheckBox diagCheck;

    public UnmixParamsEditor() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header
        add(buildHeader(), BorderLayout.NORTH);

        // Center: stacked sections
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(buildBasicPanel());
        center.add(Box.createVerticalStrut(8));
        center.add(buildParentSupersetPanel());
        center.add(Box.createVerticalStrut(8));
        center.add(buildCovariancePanel());
        center.add(Box.createVerticalStrut(8));
        center.add(buildEmPanel());
        center.add(Box.createVerticalStrut(8));
        center.add(buildDiagnosticsPanel());

        add(new JScrollPane(center), BorderLayout.CENTER);

        // Wire initial enable/disable
        syncEnableStates();
    }

    private JComponent buildHeader() {
        Box box = Box.createVerticalBox();
        box.add(new JLabel("Unmix a dataset into latent components (clusters) based on residual signatures."));
        box.add(new JLabel("Choose K explicitly or let the algorithm pick K ∈ [Kmin, Kmax] via BIC."));
        return box;
    }

    private JPanel buildBasicPanel() {
        JPanel p = titled("Basic");

        autoKCheck = new JCheckBox("Auto-select K by BIC", spec.isAutoSelectK());
        autoKCheck.addActionListener(e -> {
            spec.setAutoSelectK(autoKCheck.isSelected());
            syncEnableStates();
            pushToParams();
        });

        kSpinner = intSpinner(spec.getK(), 1, 100, 1, v -> {
            spec.setK(v);
            pushToParams();
        });
        kminSpinner = intSpinner(spec.getKmin(), 1, 100, 1, v -> {
            spec.setKmin(v);
            pushToParams();
        });
        kmaxSpinner = intSpinner(spec.getKmax(), 1, 100, 1, v -> {
            spec.setKmax(v);
            pushToParams();
        });

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row1.add(autoKCheck);
        row1.add(new JLabel("K:"));
        row1.add(kSpinner);
        row1.add(new JLabel("Kmin:"));
        row1.add(kminSpinner);
        row1.add(new JLabel("Kmax:"));
        row1.add(kmaxSpinner);

        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(row1);
        return p;
    }

    private JPanel buildParentSupersetPanel() {
        JPanel p = titled("Parent superset (residualization)");

        supersetCheck = new JCheckBox("Use parent superset", spec.isUseParentSuperset());
        supersetCheck.addActionListener(e -> {
            spec.setUseParentSuperset(supersetCheck.isSelected());
            syncEnableStates();
            pushToParams();
        });

        topMSpinner = intSpinner(spec.getSupersetTopM(), 1, 1000, 1, v -> {
            spec.setSupersetTopM(v);
            pushToParams();
        });

        supersetScoreBox = new JComboBox<>(UnmixSpec.SupersetScore.values());
        supersetScoreBox.setSelectedItem(spec.getSupersetScore());
        supersetScoreBox.addActionListener(e -> {
            spec.setSupersetScore((UnmixSpec.SupersetScore) supersetScoreBox.getSelectedItem());
            pushToParams();
        });

        robustScaleCheck = new JCheckBox("Robustly scale residuals (median/IQR)", spec.isRobustScaleResiduals());
        robustScaleCheck.addActionListener(e -> {
            spec.setRobustScaleResiduals(robustScaleCheck.isSelected());
            pushToParams();
        });

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row1.add(supersetCheck);
        row1.add(new JLabel("Top-M:"));
        row1.add(topMSpinner);
        row1.add(new JLabel("Score:"));
        row1.add(supersetScoreBox);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row2.add(robustScaleCheck);

        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(row1);
        p.add(Box.createVerticalStrut(6));
        p.add(row2);
        return p;
    }

    private JPanel buildCovariancePanel() {
        JPanel p = titled("Covariance policy");

        ButtonGroup g = new ButtonGroup();
        covAuto = new JRadioButton("Auto", spec.getCovarianceMode() == UnmixSpec.CovarianceMode.AUTO);
        covFull = new JRadioButton("Full", spec.getCovarianceMode() == UnmixSpec.CovarianceMode.FULL);
        covDiag = new JRadioButton("Diagonal", spec.getCovarianceMode() == UnmixSpec.CovarianceMode.DIAGONAL);
        g.add(covAuto);
        g.add(covFull);
        g.add(covDiag);

        safetyMarginSpinner = intSpinner(spec.getFullSigmaSafetyMargin(), 0, 1000, 1, v -> {
            spec.setFullSigmaSafetyMargin(v);
            pushToParams();
        });

        covAuto.addActionListener(e -> {
            spec.setCovarianceMode(UnmixSpec.CovarianceMode.AUTO);
            syncEnableStates();
            pushToParams();
        });
        covFull.addActionListener(e -> {
            spec.setCovarianceMode(UnmixSpec.CovarianceMode.FULL);
            syncEnableStates();
            pushToParams();
        });
        covDiag.addActionListener(e -> {
            spec.setCovarianceMode(UnmixSpec.CovarianceMode.DIAGONAL);
            syncEnableStates();
            pushToParams();
        });

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row1.add(covAuto);
        row1.add(covFull);
        row1.add(covDiag);
        row1.add(new JLabel("Safety margin (Auto):"));
        row1.add(safetyMarginSpinner);

        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(row1);
        return p;
    }

    private JPanel buildEmPanel() {
        JPanel p = titled("EM stability");

        restartsSpinner = intSpinner(spec.getKmeansRestarts(), 1, 10_000, 1, v -> {
            spec.setKmeansRestarts(v);
            pushToParams();
        });
        itersSpinner = intSpinner(spec.getEmMaxIters(), 1, 1_000_000, 10, v -> {
            spec.setEmMaxIters(v);
            pushToParams();
        });

        ridgeSpinner = dblSpinner(spec.getRidge(), 0.0, 1.0, 1e-3, v -> {
            spec.setRidge(v);
            pushToParams();
        });
        shrinkageSpinner = dblSpinner(spec.getShrinkage(), 0.0, 1.0, 0.01, v -> {
            spec.setShrinkage(v);
            pushToParams();
        });

        annealStepsSpinner = intSpinner(spec.getAnnealSteps(), 0, 10_000, 1, v -> {
            spec.setAnnealSteps(v);
            pushToParams();
        });
        annealTSpinner = dblSpinner(spec.getAnnealStartT(), 0.0, 10.0, 0.05, v -> {
            spec.setAnnealStartT(v);
            pushToParams();
        });

        seedSpinner = intSpinner((int) spec.getRandomSeed(), Integer.MIN_VALUE, Integer.MAX_VALUE, 1, v -> {
            spec.setRandomSeed(v);
            pushToParams();
        });

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row1.add(new JLabel("K-means restarts:"));
        row1.add(restartsSpinner);
        row1.add(new JLabel("EM iters:"));
        row1.add(itersSpinner);
        row1.add(new JLabel("Seed:"));
        row1.add(seedSpinner);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row2.add(new JLabel("Ridge:"));
        row2.add(ridgeSpinner);
        row2.add(new JLabel("Shrinkage:"));
        row2.add(shrinkageSpinner);
        row2.add(new JLabel("Anneal steps:"));
        row2.add(annealStepsSpinner);
        row2.add(new JLabel("Start T:"));
        row2.add(annealTSpinner);

        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(row1);
        p.add(Box.createVerticalStrut(6));
        p.add(row2);
        return p;
    }

    private JPanel buildDiagnosticsPanel() {
        JPanel p = titled("Diagnostics");
        diagCheck = new JCheckBox("Log responsibilities, per-K BIC, and cluster sizes", spec.isSaveDiagnostics());
        diagCheck.addActionListener(e -> {
            spec.setSaveDiagnostics(diagCheck.isSelected());
            spec.setLogIntermediate(diagCheck.isSelected());
            pushToParams();
        });
        p.add(diagCheck);
        return p;
    }

    // ---------------- plumbing ----------------

    private void syncEnableStates() {
        boolean autoK = autoKCheck.isSelected();
        kSpinner.setEnabled(!autoK);
        kminSpinner.setEnabled(autoK);
        kmaxSpinner.setEnabled(autoK);

        boolean superset = supersetCheck.isSelected();
        topMSpinner.setEnabled(superset);
        supersetScoreBox.setEnabled(superset);

        boolean autoCov = covAuto.isSelected();
        safetyMarginSpinner.setEnabled(autoCov);

        revalidate();
        repaint();
    }

    private JPanel titled(String title) {
        JPanel p = new JPanel();
        p.setBorder(new TitledBorder(title));
        return p;
    }

    private JSpinner intSpinner(int value, int min, int max, int step, IntConsumer c) {
        SpinnerNumberModel m = new SpinnerNumberModel(value, min, max, step);
        JSpinner s = new JSpinner(m);
        s.addChangeListener(e -> c.apply((Integer) s.getValue()));
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(4);
        return s;
    }

    private JSpinner dblSpinner(double value, double min, double max, double step, DblConsumer c) {
        SpinnerNumberModel m = new SpinnerNumberModel(value, min, max, step);
        JSpinner s = new JSpinner(m);
        s.addChangeListener(e -> c.apply(((Number) s.getValue()).doubleValue()));
        JSpinner.NumberEditor ed = new JSpinner.NumberEditor(s, "0.####");
        s.setEditor(ed);
        ed.getTextField().setColumns(6);
        return s;
    }

    @Override
    public void setup() { /* no-op */ }

    @Override
    public boolean mustBeShown() {
        return false;
    }

    // ---------------- FinalizingParameterEditor ----------------

    @Override
    public void setParams(Parameters params) {
        this.parameters = params;
        // seed defaults into Parameters so downstream can fetch
        pushToParams();
    }

    @Override
    public void setParentModels(Object[] parentModels) {
        if (parentModels == null || parentModels.length == 0)
            throw new IllegalArgumentException("There must be a parent model");
        DataWrapper data = null;
        for (Object parent : parentModels) if (parent instanceof DataWrapper) data = (DataWrapper) parent;
        if (data == null) throw new IllegalArgumentException("Should have a DataWrapper parent");
        DataModel model = data.getSelectedDataModel();
        if (!(model instanceof DataSet)) throw new IllegalArgumentException("The dataset must be rectangular");
    }

    @Override
    public boolean finalizeEdit() {
        pushToParams();
        return true;
    }

    private void pushToParams() {
        if (parameters != null) {
            parameters.set("unmixSpec", spec);
        }
    }

    // tiny functional helpers
    private interface IntConsumer {
        void apply(int v);
    }

    private interface DblConsumer {
        void apply(double v);
    }
}