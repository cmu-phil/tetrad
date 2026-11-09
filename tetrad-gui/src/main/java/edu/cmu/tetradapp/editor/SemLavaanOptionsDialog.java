package edu.cmu.tetradapp.editor;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * Modal dialog to choose options for exporting a SemIm to lavaan (.lav).
 */
public class SemLavaanOptionsDialog extends JDialog {

    /**
     * Check box for including intercepts.
     */
    private final JCheckBox includeInterceptsBox;
    /**
     * Check box for including residual variances.
     */
    private final JCheckBox includeVariancesBox;
    /**
     * Check box for including residual covariances.
     */
    private final JCheckBox includeCovariancesBox;
    /**
     * Check box for fixing parameters instead of using start() values.
     */
    private final JCheckBox fixParametersBox;

    /**
     * True if the user pressed OK.
     */
    private boolean approved = false;

    /**
     * Constructs the dialog, initializing the check boxes from saved preferences.
     *
     * @param parent the parent window
     */
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
     * Returns whether the user approved the dialog.
     *
     * @return true if the user pressed OK, false if Cancel/close.
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * Returns whether intercepts should be included.
     *
     * @return true if intercepts should be included
     */
    public boolean isIncludeIntercepts() {
        return includeInterceptsBox.isSelected();
    }

    /**
     * Returns whether residual variances should be included.
     *
     * @return true if residual variances should be included
     */
    public boolean isIncludeVariances() {
        return includeVariancesBox.isSelected();
    }

    /**
     * Returns whether residual covariances should be included.
     *
     * @return true if residual covariances should be included
     */
    public boolean isIncludeCovariances() {
        return includeCovariancesBox.isSelected();
    }

    /**
     * Returns whether parameters should be fixed rather than given as start() values.
     *
     * @return true if parameters should be fixed
     */
    public boolean isFixParameters() {
        return fixParametersBox.isSelected();
    }
}