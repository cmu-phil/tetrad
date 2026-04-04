package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.InterventionalHistogramModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Objects;

public final class InterventionalHistogramEditor extends JPanel {

    private final InterventionalHistogramModel model;

    private final JComboBox<String> yCombo = new JComboBox<>();
    private final JTextField doField = new JTextField();           // e.g. "X10=0, X3=1"
    private final JTextField nField = new JTextField("5000");
    private final JTextField binsField = new JTextField("9");
    private final JCheckBox removeZero = new JCheckBox("Remove zeros (display)", false);

    private final JButton runButton = new JButton("Compute Y | do(X)");
    private final JLabel statusLabel = new JLabel(" ");

    // query strip
    private final JTextField loField = new JTextField("-1.0");
    private final JTextField hiField = new JTextField("1.0");
    private final JButton probButton = new JButton("P(lo ≤ Y ≤ hi)");
    private final JLabel probLabel = new JLabel(" ");

    private final JPanel center = new JPanel(new BorderLayout());

    public InterventionalHistogramEditor(InterventionalHistogramModel model) {
        this.model = Objects.requireNonNull(model, "model");
        setLayout(new BorderLayout(5, 5));

        initYCombo();
        initUI();
        initListeners();

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

    private void initUI() {
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

        // Row: do field
        r++;
        c.gridx = 0; c.gridy = r; c.weightx = 0;
        top.add(new JLabel("do(X=...):"), c);
        c.gridx = 1; c.gridy = r; c.weightx = 1;
        top.add(doField, c);

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

        add(top, BorderLayout.NORTH);

        center.setPreferredSize(new Dimension(650, 450));
        add(center, BorderLayout.CENTER);

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

        add(bottom, BorderLayout.SOUTH);
    }

    private void initListeners() {
        runButton.addActionListener(this::onRun);
        probButton.addActionListener(this::onProb);
    }

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
        probLabel.setText(String.format("≈ %.4f   (cnt=%d / n=%d)", p, cnt, sample.length));
    }

    private void syncToModel() {
        String y = (String) yCombo.getSelectedItem();
        if (y == null || y.isBlank()) throw new IllegalArgumentException("Select Y.");

        model.setYName(y);
        model.setDoSpecText(doField.getText());

        int n = Integer.parseInt(nField.getText().trim());
        int bins = Integer.parseInt(binsField.getText().trim());

        model.setSampleSize(n);
        model.setNumBins(bins);
        model.setRemoveZeroPoints(removeZero.isSelected());
    }

    private void refreshView() {
        statusLabel.setText(model.getStatusMessage());

        center.removeAll();

        DataSet yDs = model.getYSampleDataSet();
        if (yDs == null) {
            center.add(new JLabel("No histogram yet. Click Compute."), BorderLayout.CENTER);
        } else {
            // Use the SAME Histogram class + HistogramPanel approach as PlotMatrix.
            Histogram h = new Histogram(yDs, "Y*", model.isRemoveZeroPoints());
            h.setNumBins(model.getNumBins());

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