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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.BasisFunctionBicScore;
import edu.cmu.tetrad.search.score.PenaltyDiscountCalibration;
import edu.cmu.tetrad.search.score.PenaltyDiscountReport;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ErrorDialogs;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A calculator for the BIC penalty discount, over
 * {@link edu.cmu.tetrad.search.score.PenaltyDiscountCalibration} and
 * {@link edu.cmu.tetrad.search.score.PenaltyDiscountReport}. It answers, for the selected data, the question the
 * {@code semBicAutoPenalty} parameter answers inside a search, but before a search is run and with the working
 * shown.
 *
 * <p>Two directions are offered. Forward: given an expected degree, a target ratio of spurious to true edges, and
 * optionally a smallest partial correlation worth an edge, what penalty discount should be used? Inverse: given a
 * penalty discount already in use, how many spurious edges does it budget for, at what per-pair level, and what is
 * the smallest effect it can see? The Sweep tab tabulates the inverse direction over a range around the
 * recommended value, which is where the crossover between the false-discovery and effect-size criteria is
 * visible.</p>
 *
 * <p><b>Why this belongs on the data.</b> The calibration is not a function of p and N alone. A pair of variables
 * costs {@code size[x] * size[y]} degrees of freedom, where the parameter block size is one for SEM BIC,
 * categories minus one for Degenerate Gaussian and discrete BIC, and the embedding block size for Basis Function
 * BIC. With the data in hand those sizes are known exactly rather than assumed, so the degrees-of-freedom
 * histogram the calibration inverts is the real one, including the mixed-type classes. For Basis Function BIC the
 * block sizes are read off a score actually constructed on the data, so adaptive basis selection is reflected.</p>
 *
 * <p>Selecting a covariance matrix restricts the calculator to the SEM BIC family, which is the only one defined
 * on second moments; p and N are read from the matrix.</p>
 *
 * <p>Computation runs off the event thread under a {@link WatchedProcess}. For the SEM BIC and Degenerate Gaussian
 * families it is instantaneous, but constructing a Basis Function score embeds the data, and the optional
 * permutation fit draws hundreds of null statistics per degrees-of-freedom class, so both can take a while on a
 * large data set and both are interruptible from the usual "Processing (click to stop)" dialog.</p>
 *
 * @author josephramsey
 * @see PenaltyDiscountReport
 * @see PenaltyDiscountCalibration
 */
class PenaltyDiscountCalculatorAction extends AbstractAction {

    /**
     * Parameter block sizes are all one; the score charges one parameter per parent.
     */
    private static final String FAMILY_SEM_BIC = "SEM BIC (block size 1)";

    /**
     * Continuous variables cost one parameter, discrete ones categories minus one.
     */
    private static final String FAMILY_DG_BIC = "DG-BIC / Discrete BIC (block size = categories - 1)";

    /**
     * Block sizes are read from a Basis Function score constructed on the data.
     */
    private static final String FAMILY_BF_BIC = "BF-BIC (block size = basis expansion)";

    /**
     * The editor this action is attached to.
     */
    private final ISelectedModel dataEditor;

    /**
     * Constructs the action.
     *
     * @param editor The editor holding the selected data model.
     */
    public PenaltyDiscountCalculatorAction(ISelectedModel editor) {
        super("Penalty Discount Calculator...");
        this.dataEditor = editor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        DataModel model = this.dataEditor.getSelectedDataModel();

        int p;
        int rows;
        int completeCases;

        if (model instanceof DataSet dataSet) {
            p = dataSet.getNumColumns();
            rows = dataSet.getNumRows();
            completeCases = countCompleteCases(dataSet);
        } else if (model instanceof ICovarianceMatrix cov) {
            p = cov.getDimension();
            rows = cov.getSampleSize();
            completeCases = rows;
        } else {
            JOptionPane.showMessageDialog(findOwner(),
                    "Need a tabular data set or a covariance matrix to calibrate a penalty discount.");
            return;
        }

        if (p < 2) {
            JOptionPane.showMessageDialog(findOwner(),
                    "Need at least two variables to calibrate a penalty discount.");
            return;
        }

        if (rows < 2) {
            JOptionPane.showMessageDialog(findOwner(),
                    "Need a sample size of at least two to calibrate a penalty discount.");
            return;
        }

        JComponent panel = createPanel(model, p, rows, completeCases);

        EditorWindow window = new EditorWindow(panel, "Penalty Discount Calculator", null, false,
                (JComponent) this.dataEditor);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);
    }

    //============================== Private methods ============================//

    /**
     * The number of rows with no missing value in any column.
     */
    private static int countCompleteCases(DataSet dataSet) {
        int count = 0;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            boolean complete = true;

            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                if (MissingDataAudit.isMissing(dataSet, i, j)) {
                    complete = false;
                    break;
                }
            }

            if (complete) count++;
        }

        return count;
    }

    /**
     * Parameter block sizes for the Degenerate Gaussian and discrete BIC family: one per continuous variable,
     * categories minus one per discrete variable.
     */
    private static int[] degenerateGaussianBlockSizes(DataSet dataSet) {
        List<Node> variables = dataSet.getVariables();
        int[] sizes = new int[variables.size()];

        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = variables.get(i) instanceof DiscreteVariable d
                    ? Math.max(1, d.getNumCategories() - 1) : 1;
        }

        return sizes;
    }

    /**
     * Builds the calculator panel: inputs on top, a Compute button, and Result and Sweep tabs below.
     */
    private JComponent createPanel(DataModel model, int p, int rows, int completeCases) {
        boolean tabular = model instanceof DataSet;

        JComboBox<String> family = new JComboBox<>(tabular
                ? new String[]{FAMILY_SEM_BIC, FAMILY_DG_BIC, FAMILY_BF_BIC}
                : new String[]{FAMILY_SEM_BIC});
        family.setEnabled(tabular);

        JSpinner truncation = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        JCheckBox rankTransform = new JCheckBox("Rank transform", false);
        JCheckBox adaptive = new JCheckBox("Adaptive basis selection", false);
        JCheckBox permutation = new JCheckBox("Permutation-fitted nulls", false);
        JTextField nullDraws = new JTextField("500", 6);

        JTextField sampleSize = new JTextField(String.valueOf(rows), 8);
        JTextField expectedDegree = new JTextField("5.0", 6);
        JTextField targetFdr = new JTextField("0.01", 6);
        JTextField minEffect = new JTextField("0.0", 6);
        JTextField evaluateAt = new JTextField("2.0", 6);

        JTextArea result = new JTextArea(18, 72);
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
        tabs.setPreferredSize(new Dimension(720, 380));

        // The Basis Function controls apply only to that family; keep them from implying otherwise.
        Runnable syncEnabled = () -> {
            boolean bf = FAMILY_BF_BIC.equals(family.getSelectedItem());
            truncation.setEnabled(bf);
            rankTransform.setEnabled(bf);
            adaptive.setEnabled(bf);
            permutation.setEnabled(bf);
            nullDraws.setEnabled(bf && permutation.isSelected());
        };
        family.addActionListener(e -> syncEnabled.run());
        permutation.addActionListener(e -> syncEnabled.run());
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
            double atC;
            int draws;

            try {
                n = Integer.parseInt(sampleSize.getText().trim());
                degree = Double.parseDouble(expectedDegree.getText().trim());
                fdr = Double.parseDouble(targetFdr.getText().trim());
                effect = Double.parseDouble(minEffect.getText().trim());
                atC = Double.parseDouble(evaluateAt.getText().trim());
                draws = Integer.parseInt(nullDraws.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(findOwner(), "Could not read a number: " + ex.getMessage(),
                        "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean bf = FAMILY_BF_BIC.equals(family.getSelectedItem());
            boolean dg = FAMILY_DG_BIC.equals(family.getSelectedItem());
            int truncationLimit = (Integer) truncation.getValue();
            boolean rank = rankTransform.isSelected();
            boolean adapt = adaptive.isSelected();
            boolean fitNulls = bf && permutation.isSelected();

            // Off the event thread: embedding the data for a Basis Function score, and the permutation fit in
            // particular, are not instantaneous on a large data set.
            new WatchedProcess() {
                @Override
                public void watch() throws InterruptedException {
                    int[] sizes;
                    Map<Integer, PenaltyDiscountCalibration.NullFit> fits = null;
                    String note = "";

                    try {
                        if (bf) {
                            DataSet dataSet = (DataSet) model;
                            BasisFunctionBicScore score = new BasisFunctionBicScore(
                                    dataSet, truncationLimit, 0.0, adapt, rank);
                            sizes = score.embeddingBlockSizes();

                            if (fitNulls) {
                                fits = score.fitNullsByPermutation(dataSet, draws, 0L);
                            } else if (!rank) {
                                note = "\nWARNING: the min-max basis embedding's null is not chi-square -- it has a"
                                       + "\npower-law tail -- so the exact calibration below sets the penalty"
                                       + "\ndiscount too low, in past measurements by enough to produce about a"
                                       + "\nhundred times the budgeted false edges. Either check"
                                       + "\nPermutation-fitted nulls or use the rank-transformed embedding, whose"
                                       + "\nnull is chi-square.\n";
                            }
                        } else if (dg) {
                            sizes = degenerateGaussianBlockSizes((DataSet) model);
                        } else {
                            sizes = new int[p];
                            java.util.Arrays.fill(sizes, 1);
                        }
                    } catch (RuntimeException ex) {
                        if (ErrorDialogs.isInterruption(ex)) {
                            throw new InterruptedException("Penalty discount calculation stopped.");
                        }

                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(findOwner(),
                                "Could not build the parameter block sizes: " + ex.getMessage(), "Error",
                                JOptionPane.WARNING_MESSAGE));
                        return;
                    }

                    PenaltyDiscountReport report;

                    try {
                        report = new PenaltyDiscountReport(sizes, n, degree, fdr, effect, fits);
                    } catch (RuntimeException ex) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(findOwner(),
                                ex.getMessage(), "Error", JOptionPane.WARNING_MESSAGE));
                        return;
                    }

                    String text = report.report() + inverseText(report, atC) + note;
                    List<PenaltyDiscountReport.Row> sweep = sweepAround(report, atC);

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

        inputs.add(new JLabel(String.format(
                "Variables (p) = %d; rows = %d; complete cases = %d.  Choose the N the score will use.",
                p, rows, completeCases)), gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.gridx = 0;
        inputs.add(new JLabel("Score family:"), gbc);
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
        inputs.add(permutation, gbc);
        gbc.gridx = 1;
        inputs.add(new JLabel("draws per df class:"), gbc);
        gbc.gridx = 2;
        inputs.add(nullDraws, gbc);

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
        inputs.add(new JLabel("Evaluate at c:"), gbc);
        gbc.gridx = 1;
        inputs.add(evaluateAt, gbc);
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
     * The inverse direction: what a penalty discount already in use is buying. Package visible so that it can be
     * exercised headlessly in tests.
     *
     * @param report The report.
     * @param c      The penalty discount to evaluate.
     * @return The text block.
     */
    static String inverseText(PenaltyDiscountReport report, double c) {
        if (!(c > 0)) return "\nEvaluate at c: skipped (c must be positive).\n";

        PenaltyDiscountReport.Row row = report.rowAt(c);

        return String.format("%nAt the entered c = %.4f: expected spurious edges = %.3f (%.1f%% of the expected "
                             + "true edges);%nsmallest per-parameter partial correlation accepted = %.4f; per-pair "
                             + "level ranges from%n%.3e in the smallest df class to %.3e in the largest.%n",
                c, row.expectedFalseEdges(), 100.0 * row.falseToTrueRatio(), row.minDetectableR(),
                row.alphaSmallestDf(), row.alphaLargestDf());
    }

    /**
     * A sweep bracketing both the recommended discount and the one the user entered, so that the crossover between
     * the two criteria is on screen wherever it falls. Package visible for tests.
     *
     * @param report The report.
     * @param atC    The user's discount, ignored when not positive.
     * @return Sixteen rows.
     */
    static List<PenaltyDiscountReport.Row> sweepAround(PenaltyDiscountReport report, double atC) {
        double center = report.getPenaltyDiscount();
        double lo = center / 4.0;
        double hi = center * 4.0;

        if (atC > 0) {
            lo = Math.min(lo, atC / 2.0);
            hi = Math.max(hi, atC * 2.0);
        }

        lo = Math.max(lo, 1e-4);
        if (!(hi > lo)) hi = lo * 2.0;

        List<PenaltyDiscountReport.Row> rows = new ArrayList<>();
        int steps = 16;

        for (int i = 0; i < steps; i++) {
            rows.add(report.rowAt(lo + i * (hi - lo) / (steps - 1)));
        }

        return rows;
    }

    /**
     * The component to center message dialogs on.
     */
    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, (JComponent) this.dataEditor);
    }

    /**
     * The Sweep tab's table model: one row per penalty discount, with what it implies.
     */
    private static class SweepTableModel extends AbstractTableModel {

        /**
         * Column headings.
         */
        private static final String[] COLUMNS = {"c", "E(false edges)", "false/true", "min r", "alpha (min df)",
                "alpha (max df)"};

        /**
         * The rows.
         */
        private List<PenaltyDiscountReport.Row> rows = new ArrayList<>();

        /**
         * Replaces the rows and fires a change.
         */
        void setRows(List<PenaltyDiscountReport.Row> rows) {
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
            PenaltyDiscountReport.Row row = this.rows.get(rowIndex);

            return switch (columnIndex) {
                case 0 -> String.format("%.4f", row.penaltyDiscount());
                case 1 -> String.format("%.4f", row.expectedFalseEdges());
                case 2 -> String.format("%.4f", row.falseToTrueRatio());
                case 3 -> String.format("%.4f", row.minDetectableR());
                case 4 -> String.format("%.3e", row.alphaSmallestDf());
                case 5 -> String.format("%.3e", row.alphaLargestDf());
                default -> "";
            };
        }
    }
}
