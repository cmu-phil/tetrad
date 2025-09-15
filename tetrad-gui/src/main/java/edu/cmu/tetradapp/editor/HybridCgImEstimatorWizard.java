// tetrad-gui/src/main/java/edu/cmu/tetradapp/editor/HybridCgImEstimatorWizard.java
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.hybridcg.HybridCgModel;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.HybridCgImWrapper;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Objects;

/**
 * Wizard dialog to estimate a Hybrid CG IM from a Hybrid CG PM + DataSet.
 * Parameters:
 *   - Bin policy: equal_frequency | equal_interval | none
 *   - # bins    : >= 2 (when policy != none)
 *   - alpha     : Dirichlet pseudo-count (double)
 *   - shareVar  : share one variance across rows for each continuous child
 *
 * Usage:
 *   HybridCgImEstimatorWizard dlg = HybridCgImEstimatorWizard.create(owner, pmWrapper, dataSet);
 *   dlg.setVisible(true);
 *   HybridCgImWrapper result = dlg.getResult(); // null if canceled/failed
 */
public final class HybridCgImEstimatorWizard extends JDialog {

    private final HybridCgPmWrapper pmWrapper;
    private final DataSet data;

    private JComboBox<String> binPolicy;
    private JSpinner binsSpinner;
    private JCheckBox shareVar;
    private JFormattedTextField alphaField;

    private HybridCgImWrapper result;

    private HybridCgImEstimatorWizard(Window owner, HybridCgPmWrapper pmWrapper, DataSet data) {
        super(owner, "Estimate Hybrid CG IM", ModalityType.APPLICATION_MODAL);
        this.pmWrapper = Objects.requireNonNull(pmWrapper, "pmWrapper");
        this.data = Objects.requireNonNull(data, "data");

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(520, getHeight()));
        setLocationRelativeTo(owner);
    }

    public static HybridCgImEstimatorWizard create(Window owner, HybridCgPmWrapper pmWrapper, DataSet data) {
        return new HybridCgImEstimatorWizard(owner, pmWrapper, data);
    }

    public HybridCgImWrapper getResult() {
        return result;
    }

    // ---------------- UI ----------------

    private JComponent buildHeader() {
        JLabel hdr = new JLabel(
                "<html><body style='padding:8px'>" +
                "<b>Estimate Hybrid CG Parameters</b><br>" +
                "Choose a binning policy for continuous parents of discrete children, then run MLE.<br>" +
                "Dirichlet α applies to CPT rows; sharing variance can help in sparse strata." +
                "</body></html>"
        );
        hdr.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xDDDDDD)));
        return hdr;
    }

    private JComponent buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 10, 4, 10);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 0.0;

        // Number formatter for alpha
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        NumberFormatter alphaFmt = new NumberFormatter(nf);
        alphaFmt.setValueClass(Double.class);
        alphaFmt.setAllowsInvalid(false);
        alphaFmt.setCommitsOnValidEdit(true);
        DefaultFormatterFactory alphaFactory = new DefaultFormatterFactory(alphaFmt, alphaFmt, alphaFmt);

        binPolicy = new JComboBox<>(new String[]{"equal_frequency", "equal_interval", "none"});
        binsSpinner = new JSpinner(new SpinnerNumberModel(3, 2, 1000, 1));
        shareVar = new JCheckBox("Share variance across strata", false);
        alphaField = new JFormattedTextField(alphaFactory);
        alphaField.setValue(1.0);
        alphaField.setColumns(8);

        // Enable/disable bins spinner based on policy
        binPolicy.addActionListener(e -> {
            String p = (String) binPolicy.getSelectedItem();
            boolean enable = !"none".equalsIgnoreCase(p);
            binsSpinner.setEnabled(enable);
            binsSpinner.setForeground(enable ? Color.BLACK : Color.GRAY);
        });

        int row = 0;

        g.gridx = 0; g.gridy = row; g.weightx = 0.0;
        form.add(new JLabel("Binning policy:"), g);
        g.gridx = 1; g.weightx = 1.0;
        form.add(binPolicy, g);

        row++;
        g.gridx = 0; g.gridy = row; g.weightx = 0.0;
        form.add(new JLabel("# bins (per continuous parent):"), g);
        g.gridx = 1; g.weightx = 1.0;
        form.add(binsSpinner, g);

        row++;
        g.gridx = 0; g.gridy = row; g.weightx = 0.0;
        form.add(new JLabel("Dirichlet α:"), g);
        g.gridx = 1; g.weightx = 1.0;
        form.add(alphaField, g);

        row++;
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        form.add(shareVar, g);

        form.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        return form;
    }

    private JComponent buildButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton run = new JButton("Estimate");

        cancel.addActionListener(e -> dispose());

        run.addActionListener(e -> {
            try {
                Parameters params = new Parameters();
                String policy = (String) binPolicy.getSelectedItem();
                int bins = ((Number) binsSpinner.getValue()).intValue();
                double alpha = ((Number) alphaField.getValue()).doubleValue();
                boolean share = shareVar.isSelected();

                params.set("hybridcg.binPolicy", policy);
                params.set("hybridcg.bins", bins);
                params.set("hybridcg.alpha", alpha);
                params.set("hybridcg.shareVariance", share);

                // Build a new IM via the convenience ctor (calls the estimator glue)
                HybridCgImWrapper imw = new HybridCgImWrapper(pmWrapper, data, params);
                this.result = imw;

                // Optional: nudge listeners that the model downstream changed
                firePropertyChange("modelChanged", false, true);

                // Optional: quick summary
                HybridCgModel.HybridCgIm im = imw.getHybridCgIm();
                if (im != null && im.getPm() != null) {
                    // no-op; you could show a tiny success message if you like
                }

                dispose();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Estimation failed:\n" + ex.getMessage(),
                        "Estimator", JOptionPane.ERROR_MESSAGE);
            }
        });

        p.add(cancel);
        p.add(run);
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return p;
    }
}