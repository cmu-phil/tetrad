package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.DerivedTreatmentSpecV2;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BinarizeTreatmentDialogV2 extends JDialog {

    private DerivedTreatmentSpecV2 result;

    public static DerivedTreatmentSpecV2 showDialog(Component parent, DataSet data, Graph graph) {
        Window w = SwingUtilities.getWindowAncestor(parent);
        BinarizeTreatmentDialogV2 d = new BinarizeTreatmentDialogV2(w, data);
        d.setVisible(true);
        return d.result;
    }

    private BinarizeTreatmentDialogV2(Window owner, DataSet data) {
        super(owner, "Binarize Treatment (v2.1)", ModalityType.APPLICATION_MODAL);

        JComboBox<String> sourceVar = new JComboBox<>(
                data.getVariables().stream().map(Node::getName).sorted().toArray(String[]::new)
        );

        JComboBox<DerivedTreatmentSpecV2.RuleType> ruleBox = new JComboBox<>();
        JTextField derivedName = new JTextField(24);

        JTextField thresholdField = new JTextField("0.0", 10);
        JTextField qLowField = new JTextField("0.30", 10);
        JTextField qHighField = new JTextField("0.70", 10);

        DefaultListModel<String> catModel = new DefaultListModel<>();
        JList<String> catList = new JList<>(catModel);
        catList.setVisibleRowCount(6);
        catList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JLabel preview = new JLabel(" ");

        Runnable refreshForSource = () -> {
            String vName = (String) sourceVar.getSelectedItem();
            Node v = data.getVariable(vName);

            ruleBox.removeAllItems();
            catModel.clear();

            if (v instanceof DiscreteVariable dv) {
                ruleBox.addItem(DerivedTreatmentSpecV2.RuleType.DISC_SUBSET_VS_REST);
                for (int i = 0; i < dv.getNumCategories(); i++) {
                    catModel.addElement(i + ": " + dv.getCategory(i));
                }
                if (dv.getNumCategories() > 0) catList.setSelectedIndex(0);
            } else {
                ruleBox.addItem(DerivedTreatmentSpecV2.RuleType.CONT_MEDIAN_SPLIT);
                ruleBox.addItem(DerivedTreatmentSpecV2.RuleType.CONT_THRESHOLD);
                ruleBox.addItem(DerivedTreatmentSpecV2.RuleType.CONT_QUANTILE_BANDS);
            }

            derivedName.setText(vName + "_bin");
        };

        sourceVar.addActionListener(e -> {
            refreshForSource.run();
            updatePreview(data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList, preview);
        });
        ruleBox.addActionListener(e ->
                updatePreview(data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList, preview)
        );

        refreshForSource.run();

        derivedName.getDocument().addDocumentListener(SimpleDocListener.of(() ->
                updatePreview(data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList, preview)
        ));
        thresholdField.getDocument().addDocumentListener(SimpleDocListener.of(() ->
                updatePreview(data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList, preview)
        ));
        qLowField.getDocument().addDocumentListener(SimpleDocListener.of(() ->
                updatePreview(data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList, preview)
        ));
        qHighField.getDocument().addDocumentListener(SimpleDocListener.of(() ->
                updatePreview(data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList, preview)
        ));
        catList.addListSelectionListener(e ->
                updatePreview(data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList, preview)
        );

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        int r = 0;

        c.gridx = 0; c.gridy = r; center.add(new JLabel("Source variable:"), c);
        c.gridx = 1; c.gridy = r++; center.add(sourceVar, c);

        c.gridx = 0; c.gridy = r; center.add(new JLabel("Rule:"), c);
        c.gridx = 1; c.gridy = r++; center.add(ruleBox, c);

        c.gridx = 0; c.gridy = r; center.add(new JLabel("Derived name:"), c);
        c.gridx = 1; c.gridy = r++; center.add(derivedName, c);

        JPanel cont = new JPanel(new GridLayout(0, 2, 6, 6));
        cont.setBorder(BorderFactory.createTitledBorder("Continuous rules"));
        cont.add(new JLabel("Threshold t (X > t):"));
        cont.add(thresholdField);
        cont.add(new JLabel("qLow (bands):"));
        cont.add(qLowField);
        cont.add(new JLabel("qHigh (bands):"));
        cont.add(qHighField);

        JPanel disc = new JPanel(new BorderLayout());
        disc.setBorder(BorderFactory.createTitledBorder("Discrete subset S (S vs rest)"));
        disc.add(new JScrollPane(catList), BorderLayout.CENTER);

        c.gridx = 0; c.gridy = r; c.gridwidth = 2; center.add(cont, c); r++;
        c.gridx = 0; c.gridy = r; c.gridwidth = 2; center.add(disc, c); r++;

        c.gridx = 0; c.gridy = r; c.gridwidth = 2; center.add(preview, c); r++;

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");

        ok.addActionListener(e -> {
            try {
                DerivedTreatmentSpecV2 spec = buildSpecFromControls(
                        data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList
                );

                int[] x01 = spec.computeX01Full(data);
                var pr = spec.previewCounts(x01);
                int usable = pr.n0 + pr.n1;

                if (usable > 0) {
                    double minFrac = Math.min(pr.n0, pr.n1) / (double) usable;
                    if (minFrac < 0.05) {
                        int ans = JOptionPane.showConfirmDialog(
                                this,
                                "Group imbalance warning:\n" +
                                        "n0=" + pr.n0 + ", n1=" + pr.n1 + ", missing/excluded=" + pr.nMissing + "\n" +
                                        "Min group fraction = " + minFrac + " (< 0.05).\n\nUse anyway?",
                                "Imbalance warning",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                        );
                        if (ans != JOptionPane.YES_OPTION) return;
                    }
                }

                this.result = spec;
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Binarize error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancel.addActionListener(e -> {
            this.result = null;
            dispose();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(ok);
        south.add(cancel);

        setLayout(new BorderLayout());
        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        updatePreview(data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList, preview);

        pack();
        setLocationRelativeTo(owner);
    }

    private static void updatePreview(DataSet data,
                                      JComboBox<String> sourceVar,
                                      JComboBox<DerivedTreatmentSpecV2.RuleType> ruleBox,
                                      JTextField derivedName,
                                      JTextField thresholdField,
                                      JTextField qLowField,
                                      JTextField qHighField,
                                      JList<String> catList,
                                      JLabel preview) {
        try {
            DerivedTreatmentSpecV2 spec = buildSpecFromControls(
                    data, sourceVar, ruleBox, derivedName, thresholdField, qLowField, qHighField, catList
            );
            int[] x01 = spec.computeX01Full(data);
            var pr = spec.previewCounts(x01);
            preview.setText("Preview: n0=" + pr.n0 + ", n1=" + pr.n1 + ", missing/excluded=" + pr.nMissing +
                    "   (" + spec.describeRule() + ")");
        } catch (Exception ex) {
            preview.setText("Preview: " + ex.getMessage());
        }
    }

    private static DerivedTreatmentSpecV2 buildSpecFromControls(
            DataSet data,
            JComboBox<String> sourceVar,
            JComboBox<DerivedTreatmentSpecV2.RuleType> ruleBox,
            JTextField derivedName,
            JTextField thresholdField,
            JTextField qLowField,
            JTextField qHighField,
            JList<String> catList
    ) {
        String src = (String) sourceVar.getSelectedItem();
        DerivedTreatmentSpecV2.RuleType rule = (DerivedTreatmentSpecV2.RuleType) ruleBox.getSelectedItem();
        String dName = derivedName.getText().trim();
        if (dName.isEmpty()) throw new IllegalArgumentException("v2.1: derived name is empty.");

        if (rule == DerivedTreatmentSpecV2.RuleType.DISC_SUBSET_VS_REST) {
            Set<Integer> subset = new LinkedHashSet<>();
            for (int idx : catList.getSelectedIndices()) subset.add(idx);
            if (subset.isEmpty()) throw new IllegalArgumentException("v2.1: select at least one category.");
            return new DerivedTreatmentSpecV2(dName, src, rule, 0.0, 0.0, 0.0, subset);
        }

        if (rule == DerivedTreatmentSpecV2.RuleType.CONT_THRESHOLD) {
            double t = Double.parseDouble(thresholdField.getText().trim());
            return new DerivedTreatmentSpecV2(dName, src, rule, t, 0.0, 0.0, null);
        }

        if (rule == DerivedTreatmentSpecV2.RuleType.CONT_QUANTILE_BANDS) {
            double ql = Double.parseDouble(qLowField.getText().trim());
            double qh = Double.parseDouble(qHighField.getText().trim());
            return new DerivedTreatmentSpecV2(dName, src, rule, 0.0, ql, qh, null);
        }

        // median split
        return new DerivedTreatmentSpecV2(dName, src, rule, 0.0, 0.0, 0.0, null);
    }

    private interface SimpleDocListener extends javax.swing.event.DocumentListener {
        static SimpleDocListener of(Runnable r) {
            return new SimpleDocListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            };
        }
    }
}