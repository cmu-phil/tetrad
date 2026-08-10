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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.List;

/**
 * Displays a data audit for the selected dataset, combining the general data-quality audit
 * ({@link edu.cmu.tetrad.data.audit.DataAudit}) with the missingness audit
 * ({@link edu.cmu.tetrad.data.missing.MissingDataAudit}). A summary line sits above three tabs:
 * <ul>
 * <li><b>Findings</b>: the audit's findings (severity, code, variables, message), warnings highlighted. Per the
 * audit's contract these describe properties of the data and carry no recommendations.</li>
 * <li><b>Variables</b>: per-variable facts (type, observed/missing counts, distinct observed values,
 * Anderson-Darling p, minimum pairwise complete count).</li>
 * <li><b>Missingness &amp; Advice</b>: dataset-level missingness facts, Little's MCAR test where applicable, and
 * the missing-data handling advice from {@link MissingDataAudit#advice()}.</li>
 * </ul>
 * All computation is done by the library classes, so this dialog reports exactly what causal-cmd and py-tetrad
 * report for the same dataset.
 *
 * @author josephramsey
 * @see DataAudit
 * @see MissingDataAudit
 */
class DataAuditAction extends AbstractAction {

    /**
     * The data editor that action is attached to.
     */
    private final ISelectedModel dataEditor;

    /**
     * Constructs the <code>DataAuditAction</code> given the editor that it's attached to.
     *
     * @param editor a {@link edu.cmu.tetradapp.editor.ISelectedModel} object
     */
    public DataAuditAction(ISelectedModel editor) {
        super("Data Audit...");
        this.dataEditor = editor;
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        if (!(this.dataEditor.getSelectedDataModel() instanceof DataSet dataSet)) {
            JOptionPane.showMessageDialog(findOwner(), "Need a tabular dataset to audit.");
            return;
        }

        if (dataSet.getNumColumns() == 0) {
            JOptionPane.showMessageDialog(findOwner(), "Cannot audit an empty data set.");
            return;
        }

        JComponent panel;

        try {
            panel = createDataAuditPanel(dataSet);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(findOwner(), "Could not compute the data audit: "
                    + ex.getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        EditorWindow window = new EditorWindow(panel,
                "Data Audit", null, false, (JComponent) this.dataEditor);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);
    }

    //============================== Private methods ============================//

    /**
     * Builds the audit panel: a summary line, then tabs for findings, per-variable facts, and missingness/advice.
     * Package visible so that it can be exercised headlessly in tests.
     */
    static JComponent createDataAuditPanel(DataSet dataSet) {
        DataAudit audit = new DataAudit(dataSet);

        // The DataAudit's delegated missingness audit is null for complete data; the dialog wants one either way.
        MissingDataAudit missingAudit = audit.getMissingDataAudit() != null
                ? audit.getMissingDataAudit() : new MissingDataAudit(dataSet);

        DataAuditJTable findingsTable =
                new DataAuditJTable(new DataAuditFindingsModel(audit.getFindings()), 4);
        setPreferredColumnWidths(findingsTable, new int[]{80, 220, 180, 500});

        // The Message column fits its longest message (reachable by scrolling right) rather than truncating at a
        // fixed width; when messages are short it stretches to fill the rest of the dialog instead (see
        // DataAuditJTable's sizing behavior).
        findingsTable.sizeColumnToContents(3, 500);

        DataAuditJTable variablesTable =
                new DataAuditJTable(new DataAuditVariablesModel(dataSet, audit, missingAudit), 2);

        JTextArea missingText = new JTextArea(missingnessText(dataSet, missingAudit));
        missingText.setEditable(false);
        missingText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, missingText.getFont().getSize()));
        missingText.setLineWrap(true);
        missingText.setWrapStyleWord(true);
        missingText.setMargin(new Insets(8, 8, 8, 8));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Findings", new JScrollPane(findingsTable));
        tabs.addTab("Variables", new JScrollPane(variablesTable));
        tabs.addTab("Missingness & Advice", new JScrollPane(missingText));
        tabs.setPreferredSize(new Dimension(850, 450));

        JMenuBar bar = new JMenuBar();
        JMenuItem copyCells = new JMenuItem("Copy Cells");
        copyCells.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyCells.addActionListener(e -> {
            JTable target = tabs.getSelectedIndex() == 1 ? variablesTable : findingsTable;
            Action copyAction = TransferHandler.getCopyAction();
            copyAction.actionPerformed(new ActionEvent(target, ActionEvent.ACTION_PERFORMED, "copy"));
        });

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(copyCells);
        bar.add(editMenu);

        JLabel summary = new JLabel(summaryLine(dataSet, audit, missingAudit));
        summary.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JPanel north = new JPanel(new BorderLayout());
        north.add(bar, BorderLayout.NORTH);
        north.add(summary, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(north, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);

        Box box = Box.createVerticalBox();
        box.add(panel);

        return box;
    }

    /**
     * The always-visible one-line summary above the tabs.
     */
    static String summaryLine(DataSet dataSet, DataAudit audit, MissingDataAudit missingAudit) {
        int numContinuous = audit.getContinuousNames().size();
        int numDiscrete = dataSet.getNumColumns() - numContinuous;

        long warnings = audit.getFindings().stream()
                .filter(f -> f.getSeverity() == AuditFinding.Severity.WARNING).count();
        long infos = audit.getFindings().size() - warnings;

        NumberFormat pct = NumberFormat.getPercentInstance();
        pct.setMaximumFractionDigits(1);

        String missingness = missingAudit.anyMissing()
                ? pct.format(missingAudit.getOverallMissingRate()) + " missing"
                : "no missing values";

        return dataSet.getNumRows() + " rows x " + dataSet.getNumColumns() + " variables ("
                + numContinuous + " continuous, " + numDiscrete + " discrete); "
                + warnings + " warning(s), " + infos + " informational; " + missingness + ".";
    }

    /**
     * The Missingness &amp; Advice tab: dataset-level missingness facts and the library's missing-data advice.
     * Per-variable rates are omitted here since the Variables tab shows them.
     */
    static String missingnessText(DataSet dataSet, MissingDataAudit audit) {
        NumberFormat pct = NumberFormat.getPercentInstance();
        pct.setMaximumFractionDigits(1);

        StringBuilder b = new StringBuilder();

        b.append("Overall missing rate: ").append(pct.format(audit.getOverallMissingRate())).append('\n');
        b.append("Complete rows (listwise n): ").append(audit.getNumCompleteRows()).append('\n');
        b.append("Distinct missingness patterns: ").append(audit.getNumPatterns()).append('\n');
        b.append("Min pairwise complete count: ").append(audit.getMinPairwiseCount()).append('\n');
        b.append("Mean pairwise complete count: ")
                .append(String.format("%.1f", audit.getMeanPairwiseCount())).append('\n');

        if (audit.anyMissing() && dataSet.isContinuous()) {
            try {
                MissingDataAudit.LittleResult r = audit.littlesMcarTest();
                b.append("Little's MCAR test: chi-square = ").append(String.format("%.2f", r.chiSquare))
                        .append(", df = ").append(r.df)
                        .append(", p = ").append(String.format("%.4f", r.pValue));

                if (r.numPatternsSkipped > 0) {
                    b.append(" (").append(r.numPatternsSkipped).append(" singular pattern(s) skipped)");
                }

                b.append('\n');
            } catch (Exception e) {
                b.append("Little's MCAR test: could not be computed (").append(e.getMessage()).append(")\n");
            }
        }

        b.append('\n').append("Missing-data advice:").append('\n');

        List<String> advice = audit.advice();

        for (int i = 0; i < advice.size(); i++) {
            b.append(i + 1).append(". ").append(advice.get(i)).append('\n');
        }

        return b.toString();
    }

    /**
     * Sets preferred widths for the given table's columns.
     */
    private static void setPreferredColumnWidths(JTable table, int[] widths) {
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(
                JFrame.class, (JComponent) this.dataEditor);
    }
}
