///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.score.BasisFunctionBicScore;
import edu.cmu.tetrad.search.test.AlphaCalibration;
import edu.cmu.tetrad.search.test.AlphaReport;
import edu.cmu.tetradapp.util.ErrorDialogs;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * A calculator for the significance level of a constraint-based search, the counterpart of
 * {@link PenaltyDiscountCalculatorPanel} and built the same way, over
 * {@link edu.cmu.tetrad.search.test.AlphaReport}.
 *
 * <p>The false-edge criterion is test-agnostic, so it is reported for any test. The power side is computed for the
 * likelihood-ratio family -- Fisher z, the conditional Gaussian and Degenerate Gaussian LRTs, and the Basis
 * Function LRT -- with degrees of freedom read from the data exactly as the penalty discount calculator reads
 * them: one per variable for Fisher z, categories minus one for the CG and DG tests, and the embedding block size
 * for the BF test, from a score constructed on the data so that adaptive basis selection is reflected. The
 * dialog says in so many words that the kernel and random-feature tests are not covered on the power side.</p>
 *
 * <p>The forward direction asks what level the budget allows and what each df class of pair can then detect; the
 * inverse direction, in the Sweep tab, asks what the conventional levels are actually buying on this data. Unlike
 * the penalty discount, the two criteria conflict rather than combining, so the dialog reports both and flags
 * incompatibility instead of silently choosing.</p>
 *
 * <p>Computation runs off the event thread under a {@link WatchedProcess}; the calibration itself is instantaneous,
 * but constructing a Basis Function score embeds the data, and that is not free on a large data set.</p>
 *
 * <p>This is the Alpha tab of {@link CalibrationCalculatorAction}; the Penalty Discount tab is
 * {@link PenaltyDiscountCalculatorPanel}.</p>
 *
 * @author josephramsey
 * @see AlphaReport
 * @see PenaltyDiscountCalculatorPanel
 * @see CalibrationCalculatorAction
 */
final class AlphaCalculatorPanel {

    /**
     * One parameter per pair.
     */
    private static final String FAMILY_FISHER_Z = "Fisher Z (df 1)";

    /**
     * Continuous variables cost one parameter, discrete ones categories minus one.
     */
    private static final String FAMILY_CG_DG = "CG-LRT / DG-LRT (df from categories - 1)";

    /**
     * Degrees of freedom are read from a Basis Function score constructed on the data.
     */
    private static final String FAMILY_BF = "BF-LRT (df from basis expansion)";

    private AlphaCalculatorPanel() {
    }

    /**
     * Builds the Alpha tab for a data model the caller has already checked is a tabular data set or a covariance
     * matrix with at least two variables and four rows.
     *
     * @param editor The editor the panel's message dialogs are centered on.
     * @param model  The selected data model.
     * @return The panel.
     */
    static JComponent create(ISelectedModel editor, DataModel model) {
        int p;
        int rows;

        if (model instanceof DataSet dataSet) {
            p = dataSet.getNumColumns();
            rows = dataSet.getNumRows();
        } else if (model instanceof ICovarianceMatrix cov) {
            p = cov.getDimension();
            rows = cov.getSampleSize();
        } else {
            throw new IllegalArgumentException("Need a tabular data set or a covariance matrix.");
        }

        return createPanel(editor, model, p, rows);
    }

    //============================== Private methods ============================//

    /**
     * Builds the calculator panel: inputs on top, Result and Sweep tabs below.
     */
    private static JComponent createPanel(ISelectedModel editor, DataModel model, int p, int rows) {
        boolean tabular = model instanceof DataSet;

        JComboBox<String> family = new JComboBox<>(tabular
                ? new String[]{FAMILY_FISHER_Z, FAMILY_CG_DG, FAMILY_BF}
                : new String[]{FAMILY_FISHER_Z});
        family.setEnabled(tabular);

        JSpinner truncation = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        JCheckBox rankTransform = new JCheckBox("Rank transform", false);
        JCheckBox adaptive = new JCheckBox("Adaptive basis selection", false);

        JTextField sampleSize = new JTextField(String.valueOf(rows), 8);
        JTextField expectedDegree = new JTextField("5.0", 6);
        JTextField targetFdr = new JTextField("0.01", 6);
        JTextField minEffect = new JTextField("0.0", 6);
        JTextField power = new JTextField("0.80", 6);
        JSpinner conditioningSetSize = new JSpinner(new SpinnerNumberModel(2, 0, 100, 1));
        JSpinner depth = new JSpinner(new SpinnerNumberModel(3, -1, 100, 1));
        JTextField evaluateAt = new JTextField("0.01", 8);

        JTextArea result = new JTextArea(18, 80);
        result.setEditable(false);
        result.setFont(new Font(Font.MONOSPACED, Font.PLAIN, result.getFont().getSize()));
        result.setMargin(new Insets(8, 8, 8, 8));
        result.setText("Set the inputs and press Compute.");

        SweepTableModel sweepModel = new SweepTableModel();
        JTable sweepTable = new JTable(sweepModel);
        sweepTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Result", new JScrollPane(result));
        tabs.addTab("Sweep", new JScrollPane(sweepTable));
        tabs.setPreferredSize(new Dimension(780, 400));

        Runnable syncEnabled = () -> {
            boolean bf = FAMILY_BF.equals(family.getSelectedItem());
            truncation.setEnabled(bf);
            rankTransform.setEnabled(bf);
            adaptive.setEnabled(bf);
        };
        family.addActionListener(e -> syncEnabled.run());
        syncEnabled.run();

        JButton compute = new JButton("Compute");
        JButton copy = new JButton("Copy Result");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(result.getText()), null));

        compute.addActionListener(e -> {
            int n;
            double degree;
            double fdr;
            double effect;
            double pow;
            double atAlpha;

            try {
                n = Integer.parseInt(sampleSize.getText().trim());
                degree = Double.parseDouble(expectedDegree.getText().trim());
                fdr = Double.parseDouble(targetFdr.getText().trim());
                effect = Double.parseDouble(minEffect.getText().trim());
                pow = Double.parseDouble(power.getText().trim());
                atAlpha = Double.parseDouble(evaluateAt.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(findOwner(editor), "Could not read a number: " + ex.getMessage(),
                        "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean bf = FAMILY_BF.equals(family.getSelectedItem());
            boolean cgDg = FAMILY_CG_DG.equals(family.getSelectedItem());
            int truncationLimit = (Integer) truncation.getValue();
            boolean rank = rankTransform.isSelected();
            boolean adapt = adaptive.isSelected();
            int condSize = (Integer) conditioningSetSize.getValue();
            int depthCap = (Integer) depth.getValue();

            new WatchedProcess() {
                @Override
                public void watch() throws InterruptedException {
                    int[] sizes;

                    try {
                        if (bf) {
                            sizes = new BasisFunctionBicScore((DataSet) model, truncationLimit, 0.0, adapt, rank)
                                    .embeddingBlockSizes();
                        } else if (cgDg) {
                            sizes = CalibrationBlockSizes.categoriesMinusOne((DataSet) model);
                        } else {
                            sizes = CalibrationBlockSizes.allOnes(p);
                        }
                    } catch (RuntimeException ex) {
                        if (ErrorDialogs.isInterruption(ex)) {
                            throw new InterruptedException("Alpha calculation stopped.");
                        }

                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(findOwner(editor),
                                "Could not build the degrees of freedom: " + ex.getMessage(), "Error",
                                JOptionPane.WARNING_MESSAGE));
                        return;
                    }

                    AlphaReport report;

                    try {
                        report = new AlphaReport(sizes, n, degree, fdr, condSize, pow, effect, depthCap);
                    } catch (RuntimeException ex) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(findOwner(editor),
                                ex.getMessage(), "Error", JOptionPane.WARNING_MESSAGE));
                        return;
                    }

                    String text = report.report() + inverseText(report, atAlpha) + scopeNote();
                    List<AlphaReport.Row> sweep = report.sweep();

                    SwingUtilities.invokeLater(() -> {
                        result.setText(text);
                        result.setCaretPosition(0);
                        sweepModel.setRows(sweep);
                    });
                }
            };
        });

        JPanel inputs = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4;

        inputs.add(new JLabel(String.format("Variables (p) = %d; rows = %d.", p, rows)), gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        inputs.add(new JLabel("Test family:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        inputs.add(family, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        inputs.add(new JLabel("Truncation limit:"), gbc);
        gbc.gridx = 1;
        inputs.add(truncation, gbc);
        gbc.gridx = 2;
        inputs.add(rankTransform, gbc);
        gbc.gridx = 3;
        inputs.add(adaptive, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        inputs.add(new JLabel("Sample size N:"), gbc);
        gbc.gridx = 1;
        inputs.add(sampleSize, gbc);
        gbc.gridx = 2;
        inputs.add(new JLabel("Expected degree:"), gbc);
        gbc.gridx = 3;
        inputs.add(expectedDegree, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        inputs.add(new JLabel("Target FDR:"), gbc);
        gbc.gridx = 1;
        inputs.add(targetFdr, gbc);
        gbc.gridx = 2;
        inputs.add(new JLabel("Min partial correlation:"), gbc);
        gbc.gridx = 3;
        inputs.add(minEffect, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        inputs.add(new JLabel("Target power:"), gbc);
        gbc.gridx = 1;
        inputs.add(power, gbc);
        gbc.gridx = 2;
        inputs.add(new JLabel("Conditioning set size |S|:"), gbc);
        gbc.gridx = 3;
        inputs.add(conditioningSetSize, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        inputs.add(new JLabel("Search depth (-1 = unlimited):"), gbc);
        gbc.gridx = 1;
        inputs.add(depth, gbc);
        gbc.gridx = 2;
        inputs.add(new JLabel("Evaluate at alpha:"), gbc);
        gbc.gridx = 3;
        inputs.add(evaluateAt, gbc);

        gbc.gridy++;
        gbc.gridx = 2;
        inputs.add(compute, gbc);
        gbc.gridx = 3;
        inputs.add(copy, gbc);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(inputs, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    /**
     * The inverse direction: what a level already in use is buying. Package visible for tests.
     *
     * @param report The report.
     * @param alpha  The level to evaluate.
     * @return The text block.
     */
    static String inverseText(AlphaReport report, double alpha) {
        if (!(alpha > 0 && alpha < 1)) return "\nEvaluate at alpha: skipped (alpha must be in (0, 1)).\n";

        AlphaReport.Row row = report.rowAt(alpha);

        return String.format("%nAt the entered alpha = %.3e: at most %.2f spurious adjacencies (%.1f%% of the "
                             + "expected%ntrue edges); smallest partial correlation detected = %.4f (smallest df "
                             + "class) to %.4f%n(largest df class); equivalent SEM BIC penalty discount = %.3f.%n",
                alpha, row.expectedFalseEdges(), 100.0 * row.falseToTrueRatio(), row.minDetectableRMinDf(),
                row.minDetectableRMaxDf(), row.equivalentPenalty());
    }

    /**
     * The scope statement appended to every result.
     */
    private static String scopeNote() {
        return "\nScope: the false-edge level applies to any test run at its nominal level. The detection figures\n"
               + "are for tests whose null is chi-square on the degrees of freedom shown (Fisher Z, CG-LRT,\n"
               + "DG-LRT, BF-LRT) and do not apply to the kernel and random-feature tests (KCI, GCM, RFF).\n";
    }

    /**
     * The component to center message dialogs on.
     */
    private static JFrame findOwner(ISelectedModel editor) {
        return (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, (JComponent) editor);
    }

    /**
     * The Sweep tab's table model.
     */
    private static class SweepTableModel extends AbstractTableModel {

        /**
         * Column headings.
         */
        private static final String[] COLUMNS = {"alpha", "E(false edges)", "false/true", "min r (min df)",
                "min r (max df)", "equivalent c"};

        /**
         * The rows.
         */
        private List<AlphaReport.Row> rows = new ArrayList<>();

        /**
         * Replaces the rows and fires a change.
         */
        void setRows(List<AlphaReport.Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return this.rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AlphaReport.Row row = this.rows.get(rowIndex);

            return switch (columnIndex) {
                case 0 -> String.format("%.3e", row.alpha());
                case 1 -> String.format("%.3f", row.expectedFalseEdges());
                case 2 -> String.format("%.4f", row.falseToTrueRatio());
                case 3 -> String.format("%.4f", row.minDetectableRMinDf());
                case 4 -> String.format("%.4f", row.minDetectableRMaxDf());
                case 5 -> String.format("%.3f", row.equivalentPenalty());
                default -> "";
            };
        }
    }
}
