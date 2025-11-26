package edu.cmu.tetradapp.editor;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * Modal dialog to choose options for exporting a SemIm to lavaan (.lav).
 */
public class SemLavaanOptionsDialog extends JDialog {

    private final JCheckBox includeInterceptsBox;
    private final JCheckBox includeVariancesBox;
    private final JCheckBox includeCovariancesBox;
    private final JCheckBox fixParametersBox;

    private boolean approved = false;

    public SemLavaanOptionsDialog(Window parent) {
        super(parent, "Export SEM to lavaan", ModalityType.APPLICATION_MODAL);

        includeInterceptsBox = new JCheckBox("Include intercepts (y ~ c*1)",
                Preferences.userRoot().getBoolean("edu.cmu.tetradapp.editor.SemLavaanOptionsDialog.include.intercepts", true));
        includeVariancesBox = new JCheckBox("Include residual variances (y ~~ v*y)",
                Preferences.userRoot().getBoolean("edu.cmu.tetradapp.editor.SemLavaanOptionsDialog.include.variances", true));
        includeCovariancesBox = new JCheckBox("Include residual covariances (y ~~ c*z)",
                Preferences.userRoot().getBoolean("edu.cmu.tetradapp.editor.SemLavaanOptionsDialog.include.covariances", true));
        fixParametersBox = new JCheckBox("Fix parameters (instead of using start() values)",
                Preferences.userRoot().getBoolean("edu.cmu.tetradapp.editor.SemLavaanOptionsDialog.fix.parameters", true));

        includeCovariancesBox.addActionListener(e -> {
            Preferences.userRoot().putBoolean("edu.cmu.tetradapp.editor.SemLavaanOptionsDialog.include.covariances", includeCovariancesBox.isSelected());
        });

        includeVariancesBox.addActionListener(e -> {
            Preferences.userRoot().putBoolean("edu.cmu.tetradapp.editor.SemLavaanOptionsDialog.include.variances", includeVariancesBox.isSelected());
        });

        includeInterceptsBox.addActionListener(e -> {
            Preferences.userRoot().putBoolean("edu.cmu.tetradapp.editor.SemLavaanOptionsDialog.include.intercepts", includeInterceptsBox.isSelected());
        });

        fixParametersBox.addActionListener(e -> {
            Preferences.userRoot().putBoolean("edu.cmu.tetradapp.editor.SemLavaanOptionsDialog.fix.parameters", fixParametersBox.isSelected());
        });

        initLayout();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Center: checkboxes
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.add(includeInterceptsBox);
        optionsPanel.add(includeVariancesBox);
        optionsPanel.add(includeCovariancesBox);
        optionsPanel.add(Box.createVerticalStrut(8));
        optionsPanel.add(fixParametersBox);

        mainPanel.add(optionsPanel, BorderLayout.CENTER);

        // South: buttons
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(e -> {
            approved = true;
            setVisible(false);
        });

        cancelButton.addActionListener(e -> {
            approved = false;
            setVisible(false);
        });

        buttonsPanel.add(okButton);
        buttonsPanel.add(cancelButton);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(okButton);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Show the dialog modally.
     */
    public void showDialog() {
        approved = false; // reset
        setVisible(true); // blocks until closed
    }

    /**
     * @return true if the user pressed OK, false if Cancel/close.
     */
    public boolean isApproved() {
        return approved;
    }

    public boolean isIncludeIntercepts() {
        return includeInterceptsBox.isSelected();
    }

    public boolean isIncludeVariances() {
        return includeVariancesBox.isSelected();
    }

    public boolean isIncludeCovariances() {
        return includeCovariancesBox.isSelected();
    }

    public boolean isFixParameters() {
        return fixParametersBox.isSelected();
    }
}