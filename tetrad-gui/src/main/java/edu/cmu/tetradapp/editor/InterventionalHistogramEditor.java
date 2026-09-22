package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.InterventionalHistogramModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Editor for the Interventional Histogram prototype. Shows a histogram of Y under an intervention
 * do(X1 = x1, ..., Xm = xm), where each intervened variable must be discrete in the data. Y may be
 * discrete or continuous.
 *
 * <p>The intervention is specified with structured rows -- a variable dropdown listing the discrete
 * variables and a value dropdown listing that variable's category names -- rather than typed text,
 * so category indices never need to be known. The rows are serialized to the model's spec string
 * ("name=index, ...") so the model contract and session serialization are unchanged.</p>
 *
 * <p>An Explanation tab states what the sampler computes, when the parents-of-X adjustment is a
 * valid identification of P(Y | do(X)), and when it is not.</p>
 *
 * @author josephramsey
 * @see InterventionalHistogramModel
 * @see InterventionalHistogramExplanationPanel
 */
public final class InterventionalHistogramEditor extends JPanel {

    private final InterventionalHistogramModel model;

    private final JComboBox<String> yCombo = new JComboBox<>();
    private final JTextField nField = new JTextField("5000");
    private final JTextField binsField = new JTextField("9");
    private final JCheckBox removeZero = new JCheckBox("Remove zeros (display)", false);

    private final JButton runButton = new JButton("Compute Y | do(X)");
    private final JLabel statusLabel = new JLabel(" ");

    // do() specification rows
    private final JPanel doRowsPanel = new JPanel();
    private final JButton addDoButton = new JButton("Add intervention");
    private final List<DoRow> doRows = new ArrayList<>();

    /**
     * Variables eligible for intervention: discrete in the data and present in the graph.
     */
    private final List<DiscreteVariable> doCandidates = new ArrayList<>();

    // query strip
    private final JTextField loField = new JTextField("-1.0");
    private final JTextField hiField = new JTextField("1.0");
    private final JButton probButton = new JButton("P(lo \u2264 Y \u2264 hi)");
    private final JLabel probLabel = new JLabel(" ");

    private final JPanel center = new JPanel(new BorderLayout());

    public InterventionalHistogramEditor(InterventionalHistogramModel model) {
        this.model = Objects.requireNonNull(model, "model");
        setLayout(new BorderLayout(5, 5));

        initYCombo();
        initDoCandidates();

        JPanel histogramTab = buildHistogramTab();
        restoreDoRows();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Histogram", histogramTab);
        tabs.addTab("Explanation", InterventionalHistogramExplanationPanel.create());
        add(tabs, BorderLayout.CENTER);

        initListeners();
        updateBinsEnabled();

        refreshView();
    }

    private void initYCombo() {
        DataSet data = model.getData();
        List<Node> vars = data.getVariables();
        for (Node v : vars) {
            yCombo.addItem(v.getName());
        }
        if (yCombo.getItemCount() > 0) yCombo.setSelectedIndex(0);
    }

    private void initDoCandidates() {
        DataSet data = model.getData();
        for (Node v : data.getVariables()) {
            if (v instanceof DiscreteVariable dv && model.getGraph().getNode(v.getName()) != null) {
                doCandidates.add(dv);
            }
        }
    }

    private JPanel buildHistogramTab() {
        JPanel tab = new JPanel(new BorderLayout(5, 5));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;

        // Row: Y selector
        c.gridx = 0; c.gridy = r; c.weightx = 0;
        top.add(new JLabel("Y:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1;
        top.add(yCombo, c);

        // Row: do rows
        r++;
        doRowsPanel.setLayout(new BoxLayout(doRowsPanel, BoxLayout.Y_AXIS));

        JPanel doBlock = new JPanel(new BorderLayout(2, 2));
        doBlock.add(doRowsPanel, BorderLayout.CENTER);

        JPanel addLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        addLine.add(addDoButton);
        if (doCandidates.isEmpty()) {
            addDoButton.setEnabled(false);
            addLine.add(Box.createHorizontalStrut(8));
            addLine.add(new JLabel("(no discrete variables available to intervene on)"));
        }
        doBlock.add(addLine, BorderLayout.SOUTH);

        c.gridx = 0; c.gridy = r; c.weightx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        top.add(new JLabel("do(X=...):"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1;
        top.add(doBlock, c);

        // Row: params
        r++;
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.add(new JLabel("Sample n:"));
        nField.setColumns(6);
        p.add(nField);
        p.add(new JLabel("Bins:"));
        binsField.setColumns(4);
        p.add(binsField);
        p.add(removeZero);
        p.add(runButton);

        c.gridx = 0; c.gridy = r; c.weightx = 0;
        top.add(new JLabel(" "), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1;
        top.add(p, c);

        // Row: status
        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0;
        top.add(new JLabel("Status:"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1;
        top.add(statusLabel, c);

        tab.add(top, BorderLayout.NORTH);

        center.setPreferredSize(new Dimension(650, 450));
        tab.add(center, BorderLayout.CENTER);

        // bottom query strip
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        loField.setColumns(8);
        hiField.setColumns(8);
        bottom.add(probButton);
        bottom.add(new JLabel("lo:"));
        bottom.add(loField);
        bottom.add(new JLabel("hi:"));
        bottom.add(hiField);
        bottom.add(probLabel);

        tab.add(bottom, BorderLayout.SOUTH);

        return tab;
    }

    private void initListeners() {
        runButton.addActionListener(this::onRun);
        probButton.addActionListener(this::onProb);
        addDoButton.addActionListener(e -> {
            addDoRow(null, -1);
            revalidateDoRows();
        });
        yCombo.addActionListener(e -> updateBinsEnabled());
    }

    /**
     * The bins control applies only to a continuous Y; a discrete Y gets one bar per category.
     */
    private void updateBinsEnabled() {
        String y = (String) yCombo.getSelectedItem();
        boolean discreteY = y != null && model.getData().getVariable(y) instanceof DiscreteVariable;
        binsField.setEnabled(!discreteY);
        binsField.setToolTipText(discreteY
                ? "Not used for a discrete Y; each category gets its own bar."
                : "Number of histogram bins for a continuous Y.");
    }

    // ----------------------------
    // do() rows
    // ----------------------------

    /**
     * One intervention row: a variable dropdown over the discrete candidates and a value dropdown over
     * that variable's category names. The value combo's selected index is the category index.
     */
    private final class DoRow {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        final JComboBox<String> varCombo = new JComboBox<>();
        final JComboBox<String> valCombo = new JComboBox<>();
        final JButton removeButton = new JButton("\u2212");

        DoRow(String initialVar, int initialVal) {
            for (DiscreteVariable dv : doCandidates) {
                varCombo.addItem(dv.getName());
            }

            if (initialVar != null) varCombo.setSelectedItem(initialVar);
            repopulateValues();
            if (initialVal >= 0 && initialVal < valCombo.getItemCount()) {
                valCombo.setSelectedIndex(initialVal);
            }

            varCombo.addActionListener(e -> repopulateValues());

            removeButton.setMargin(new Insets(0, 6, 0, 6));
            removeButton.setToolTipText("Remove this intervention");
            removeButton.addActionListener(e -> {
                doRows.remove(this);
                doRowsPanel.remove(panel);
                revalidateDoRows();
            });

            panel.add(new JLabel("do("));
            panel.add(varCombo);
            panel.add(new JLabel("="));
            panel.add(valCombo);
            panel.add(new JLabel(")"));
            panel.add(removeButton);
        }

        void repopulateValues() {
            valCombo.removeAllItems();
            DiscreteVariable dv = selectedVariable();
            if (dv != null) {
                for (String cat : dv.getCategories()) {
                    valCombo.addItem(cat);
                }
                if (valCombo.getItemCount() > 0) valCombo.setSelectedIndex(0);
            }
        }

        DiscreteVariable selectedVariable() {
            String name = (String) varCombo.getSelectedItem();
            if (name == null) return null;
            for (DiscreteVariable dv : doCandidates) {
                if (dv.getName().equals(name)) return dv;
            }
            return null;
        }

        /** Category index of the selected value, or -1 if none. */
        int selectedValueIndex() {
            return valCombo.getSelectedIndex();
        }
    }

    private void addDoRow(String initialVar, int initialVal) {
        if (doCandidates.isEmpty()) return;
        DoRow row = new DoRow(initialVar, initialVal);
        doRows.add(row);
        doRowsPanel.add(row.panel);
    }

    private void revalidateDoRows() {
        doRowsPanel.revalidate();
        doRowsPanel.repaint();
        revalidate();
        repaint();
    }

    /**
     * Repopulates the rows from the model's saved spec text (session reload). Tokens that no longer
     * resolve to a discrete variable or a valid category index are dropped silently.
     */
    private void restoreDoRows() {
        String spec = model.getDoSpecText();
        if (spec == null || spec.isBlank()) return;

        for (String tok : spec.split("[,\\s]+")) {
            String t = tok.trim();
            int eq = t.indexOf('=');
            if (eq <= 0 || eq >= t.length() - 1) continue;

            String name = t.substring(0, eq).trim();
            int val;
            try {
                val = Integer.parseInt(t.substring(eq + 1).trim());
            } catch (NumberFormatException nfe) {
                continue;
            }

            DiscreteVariable dv = null;
            for (DiscreteVariable cand : doCandidates) {
                if (cand.getName().equals(name)) { dv = cand; break; }
            }
            if (dv == null || val < 0 || val >= dv.getNumCategories()) continue;

            addDoRow(name, val);
        }
    }

    /** Composes the "name=index, ..." spec string the model parses. */
    private String composeDoSpecText() {
        StringBuilder sb = new StringBuilder();
        for (DoRow row : doRows) {
            DiscreteVariable dv = row.selectedVariable();
            int idx = row.selectedValueIndex();
            if (dv == null || idx < 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(dv.getName()).append('=').append(idx);
        }
        return sb.toString();
    }

    // ----------------------------
    // actions
    // ----------------------------

    private void onRun(ActionEvent e) {
        try {
            syncToModel();
            setBusy(true);

            model.recomputeAsync(() -> {
                setBusy(false);
                refreshView();
            });

        } catch (Exception ex) {
            setBusy(false);
            statusLabel.setText(ex.getMessage());
        }
    }

    private void onProb(ActionEvent e) {
        double[] sample = model.getYSample();
        if (sample == null || sample.length == 0) {
            probLabel.setText("No sample.");
            return;
        }

        double lo, hi;
        try {
            lo = Double.parseDouble(loField.getText().trim());
            hi = Double.parseDouble(hiField.getText().trim());
        } catch (NumberFormatException nfe) {
            probLabel.setText("Bad lo/hi.");
            return;
        }

        if (hi < lo) {
            double t = lo; lo = hi; hi = t;
        }

        int cnt = 0;
        for (double v : sample) {
            if (Double.isFinite(v) && v >= lo && v <= hi) cnt++;
        }

        double p = ((double) cnt) / sample.length;
        probLabel.setText(String.format("\u2248 %.4f   (cnt=%d / n=%d)", p, cnt, sample.length));
    }

    private void syncToModel() {
        String y = (String) yCombo.getSelectedItem();
        if (y == null || y.isBlank()) throw new IllegalArgumentException("Select Y.");

        model.setYName(y);
        model.setDoSpecText(composeDoSpecText());

        int n = Integer.parseInt(nField.getText().trim());
        int bins = Integer.parseInt(binsField.getText().trim());

        model.setSampleSize(n);
        model.setNumBins(bins);
        model.setRemoveZeroPoints(removeZero.isSelected());
    }

    private void refreshView() {
        statusLabel.setText(model.getStatusMessage());
        statusLabel.setToolTipText(model.getStatusMessage());

        center.removeAll();

        DataSet yDs = model.getYSampleDataSet();
        if (yDs == null) {
            center.add(new JLabel("No histogram yet. Click Compute."), BorderLayout.CENTER);
        } else {
            // Use the SAME Histogram class + HistogramPanel approach as PlotMatrix.
            Histogram h = new Histogram(yDs, "Y*", model.isRemoveZeroPoints());

            // setNumBins throws for a discrete target; a discrete Y* gets one bar per category.
            if (!(yDs.getVariable("Y*") instanceof DiscreteVariable)) {
                h.setNumBins(model.getNumBins());
            }

            HistogramPanel hp = new HistogramPanel(h, true);
            center.add(hp, BorderLayout.CENTER);
        }

        center.revalidate();
        center.repaint();
    }

    private void setBusy(boolean busy) {
        runButton.setEnabled(!busy);
        probButton.setEnabled(!busy);
    }
}
