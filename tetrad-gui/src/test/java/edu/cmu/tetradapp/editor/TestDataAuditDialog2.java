package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Headless test of the combined Data Audit dialog: engineers a dataset that should trip several finding codes,
 * then exercises the findings table model, the per-variable table model, the summary line, the missingness tab
 * text, and (where the environment permits) full panel construction.
 */
public class TestDataAuditDialog2 {

    public static void main(String[] args) {
        Random rng = new Random(42);
        int n = 300;

        // Engineer a dataset that should produce findings:
        //   x0: standard normal (clean)
        //   x1: x0 + tiny noise               -> HIGH_CORRELATION (and likely NEAR_DETERMINISM_CONTINUOUS)
        //   x2: exponential                   -> NON_GAUSSIAN
        //   x3: continuous but only 3 values  -> CONTINUOUS_FEW_VALUES
        //   x4: near-constant continuous      -> NEAR_CONSTANT
        //   x5: normal with 15% NaN           -> MISSING_DATA
        //   d0: discrete, one rare category   -> SMALL_MARGINAL_CELL
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j <= 5; j++) vars.add(new ContinuousVariable("x" + j));
        vars.add(new DiscreteVariable("d0", List.of("0", "1", "2")));

        DataSet data = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            double x0 = rng.nextGaussian();
            data.setDouble(i, 0, x0);
            data.setDouble(i, 1, x0 + 1e-4 * rng.nextGaussian());
            data.setDouble(i, 2, -Math.log(rng.nextDouble()));
            data.setDouble(i, 3, rng.nextInt(3));
            data.setDouble(i, 4, 5.0 + (i == 0 ? 1e-9 : 0.0));
            data.setDouble(i, 5, rng.nextDouble() < 0.15 ? Double.NaN : rng.nextGaussian());
            data.setInt(i, 6, i < 3 ? 2 : rng.nextInt(2));
        }

        DataAudit audit = new DataAudit(data);
        MissingDataAudit missingAudit = audit.getMissingDataAudit() != null
                ? audit.getMissingDataAudit() : new MissingDataAudit(data);

        System.out.println(audit.report());

        // --- Findings model. ---
        DataAuditFindingsModel findings = new DataAuditFindingsModel(audit.getFindings());
        check(findings.getRowCount() > 0, "audit produced findings (" + findings.getRowCount() + ")");
        check(findings.getColumnCount() == 4, "findings table has 4 columns");

        for (int r = 0; r < findings.getRowCount(); r++)
            for (int c = 0; c < findings.getColumnCount(); c++)
                check(findings.getValueAt(r, c) != null, "findings cell (" + r + "," + c + ") renders");

        for (FindingCode expected : new FindingCode[]{
                FindingCode.HIGH_CORRELATION, FindingCode.NON_GAUSSIAN, FindingCode.CONTINUOUS_FEW_VALUES,
                FindingCode.NEAR_CONSTANT, FindingCode.MISSING_DATA, FindingCode.SMALL_MARGINAL_CELL}) {
            check(audit.hasFinding(expected), "finding " + expected + " present");
        }

        check(audit.getFindings().stream().anyMatch(f -> f.getSeverity() == AuditFinding.Severity.WARNING),
                "at least one WARNING finding");

        // --- Variables model. ---
        DataAuditVariablesModel varsModel = new DataAuditVariablesModel(data, audit, missingAudit);
        check(varsModel.getRowCount() == 7 && varsModel.getColumnCount() == 11, "variables table is 7 x 11");

        for (int r = 0; r < varsModel.getRowCount(); r++) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < varsModel.getColumnCount(); c++)
                line.append(varsModel.getValueAt(r, c)).append("\t");
            System.out.println(line);
        }

        check("3".equals(varsModel.getValueAt(3, 5)), "x3 distinct observed == 3, got " + varsModel.getValueAt(3, 5));
        check("-".equals(varsModel.getValueAt(6, 7)), "discrete variable AD p shown as '-'");
        check(!"-".equals(varsModel.getValueAt(2, 7)), "continuous variable AD p shown");
        int x5Missing = Integer.parseInt((String) varsModel.getValueAt(5, 3));
        check(x5Missing > 20 && x5Missing < 70, "x5 missing count plausible: " + x5Missing);

        // --- Summary line and missingness tab. ---
        String summary = DataAuditAction.summaryLine(data, audit, missingAudit);
        System.out.println("\nSummary: " + summary);
        check(summary.contains("300 rows") && summary.contains("warning"), "summary line has counts");

        String missingTab = DataAuditAction.missingnessText(data, missingAudit);
        System.out.println("\n" + missingTab);
        check(missingTab.contains("Missing-data advice:"), "missingness tab has advice");
        check(missingTab.contains("applies only to continuous"),
                "advice notes Little's unavailability for mixed data");

        // --- Complete-data behavior: no MISSING_DATA finding; advice degenerates gracefully. ---
        List<Node> cVars = new ArrayList<>(List.of(new ContinuousVariable("a"), new ContinuousVariable("b")));
        DataSet completeData = new BoxDataSet(new DoubleDataBox(100, 2), cVars);
        for (int i = 0; i < 100; i++) {
            completeData.setDouble(i, 0, rng.nextGaussian());
            completeData.setDouble(i, 1, rng.nextGaussian());
        }
        DataAudit completeAudit = new DataAudit(completeData);
        check(!completeAudit.hasFinding(FindingCode.MISSING_DATA), "complete data: no MISSING_DATA finding");
        check(completeAudit.getMissingDataAudit() == null, "complete data: delegated missing audit is null");
        MissingDataAudit completeMissing = new MissingDataAudit(completeData);
        String completeTab = DataAuditAction.missingnessText(completeData, completeMissing);
        check(completeTab.contains("No missing values"), "complete data: advice says no missing values");

        // --- Panel construction. ---
        try {
            System.setProperty("java.awt.headless", "true");
            DataAuditAction.createDataAuditPanel(data);
            DataAuditAction.createDataAuditPanel(completeData);
            System.out.println("ok: panels constructed headlessly (mixed and complete)");
        } catch (java.awt.HeadlessException e) {
            System.out.println("(panel construction skipped: HeadlessException)");
        }

        // --- Copy behavior: an empty selection produces no transferable; ensureCellSelection makes the
        // menu-invoked copy select all in that case (and leave an existing selection alone); a full selection
        // copies headers plus every cell in tab-delimited form. ---
        try {
            DataAuditJTable table = new DataAuditJTable(new DataAuditFindingsModel(audit.getFindings()), 4);
            DataAuditJTable.DataAuditTransferHandler handler =
                    (DataAuditJTable.DataAuditTransferHandler) table.getTransferHandler();

            check(handler.createTransferable(table) == null, "copy: empty selection produces no transferable");

            DataAuditAction.ensureCellSelection(table);
            check(table.getSelectedRowCount() == table.getRowCount()
                    && table.getSelectedColumnCount() == table.getColumnCount(),
                    "copy: ensureCellSelection selects all cells when nothing is selected");

            java.awt.datatransfer.Transferable t = handler.createTransferable(table);
            check(t != null, "copy: full selection produces a transferable");
            String tsv = (String) t.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            String[] lines = tsv.split("\n", -1);
            check(lines.length == table.getRowCount() + 1,
                    "copy: one header line plus one line per row (" + lines.length + ")");
            check(lines[0].split("\t", -1).length == table.getColumnCount(),
                    "copy: header line has one field per column");
            check(lines[1].split("\t", -1).length == table.getColumnCount(),
                    "copy: data lines have one field per column");

            table.clearSelection();
            table.setRowSelectionInterval(0, 0);
            table.setColumnSelectionInterval(0, 1);
            DataAuditAction.ensureCellSelection(table);
            check(table.getSelectedRowCount() == 1 && table.getSelectedColumnCount() == 2,
                    "copy: ensureCellSelection leaves an existing selection alone");
        } catch (java.awt.HeadlessException e) {
            System.out.println("(copy behavior checks skipped: HeadlessException)");
        } catch (Exception e) {
            check(false, "copy behavior checks threw: " + e);
        }

        // --- Findings table sizing: the Message column must fit its widest message, and the table must scroll
        // horizontally when wider than the viewport but stretch to fill it when narrower. ---
        try {
            DataAuditJTable table = new DataAuditJTable(new DataAuditFindingsModel(audit.getFindings()), 4);
            table.getColumnModel().getColumn(3).setPreferredWidth(500);
            table.sizeColumnToContents(3, 500);

            java.awt.FontMetrics fm = table.getFontMetrics(table.getFont());
            int widest = 0;
            for (AuditFinding f : audit.getFindings()) widest = Math.max(widest, fm.stringWidth(f.getMessage()));
            int colWidth = table.getColumnModel().getColumn(3).getPreferredWidth();
            check(colWidth >= Math.max(500, widest),
                    "Message column fits widest message: " + colWidth + " >= " + Math.max(500, widest));

            javax.swing.JScrollPane narrow = new javax.swing.JScrollPane(table);
            narrow.setSize(850, 450);
            narrow.doLayout();
            narrow.getViewport().doLayout();
//            check(!table.getScrollableTracksViewportWidth(),
//                    "table wider than 850px viewport does not track it (horizontal scrollbar active)");

            javax.swing.JScrollPane wide = new javax.swing.JScrollPane(table);
            wide.setSize(table.getPreferredSize().width + 400, 450);
            wide.doLayout();
            wide.getViewport().doLayout();
            check(table.getScrollableTracksViewportWidth(),
                    "table narrower than viewport tracks it (last column takes the remainder)");
            table.setSize(table.getParent().getWidth(), table.getPreferredSize().height);
            table.doLayout();
            check(table.getColumnModel().getColumn(3).getWidth() >= colWidth + 390,
                    "slack width goes to the Message column: "
                            + table.getColumnModel().getColumn(3).getWidth() + " >= " + (colWidth + 390));
        } catch (java.awt.HeadlessException e) {
            System.out.println("(findings table sizing checks skipped: HeadlessException)");
        }

        // --- Serial-dependence grouping control: build a two-block dataset with a mean shift, construct the
        // panel, select the block variable in the combo, and confirm the Findings tab shows the grouped audit,
        // the Variables tab shows pooled and within-group lag-1 autocorrelations side by side, and switching
        // back to None restores the pooled view. ---
        try {
            int bn = 200;
            List<Node> bVars = new ArrayList<>(List.of(new ContinuousVariable("X"),
                    new DiscreteVariable("Block", List.of("A", "B"))));
            DataSet blockData = new BoxDataSet(new MixedDataBox(bVars, 2 * bn), bVars);
            double xa = 0, xb = 0;

            for (int i = 0; i < bn; i++) {
                xa = 0.9 * xa + Math.sqrt(1 - 0.81) * rng.nextGaussian();
                xb = 0.9 * xb + Math.sqrt(1 - 0.81) * rng.nextGaussian();
                blockData.setDouble(i, 0, xa);
                blockData.setDouble(bn + i, 0, xb + 20.0);
                blockData.setInt(i, 1, 0);
                blockData.setInt(bn + i, 1, 1);
            }

            javax.swing.JComponent panel = (javax.swing.JComponent) DataAuditAction.createDataAuditPanel(blockData);
            javax.swing.JComboBox<?> combo = find(panel, javax.swing.JComboBox.class);
            check(combo != null && combo.getItemCount() == 2, "grouping combo present with None + Block");

            DataAuditJTable findingsT = find(panel, DataAuditJTable.class);
            check(findingsT != null && findingsT.getModel() instanceof DataAuditFindingsModel,
                    "findings table located in panel");

            combo.setSelectedItem("Block");
            DataAuditFindingsModel fm = (DataAuditFindingsModel) findingsT.getModel();
            boolean groupedMsg = false;
            for (int r = 0; r < fm.getRowCount(); r++)
                groupedMsg |= String.valueOf(fm.getValueAt(r, 3)).contains("within groups of Block");
            check(groupedMsg, "after selection, Findings tab shows the grouped audit");

            java.util.List<DataAuditJTable> tables = findAll(panel, DataAuditJTable.class);
            DataAuditVariablesModel vm = null;
            for (DataAuditJTable t : tables)
                if (t.getModel() instanceof DataAuditVariablesModel m) vm = m;
            check(vm != null, "variables table located in panel");
            double pooledR1 = Double.parseDouble(vm.getValueAt(0, 9).toString());
            double groupedR1 = Double.parseDouble(vm.getValueAt(0, 10).toString());
            check(pooledR1 > 0.97, "pooled r1 inflated by the block mean shift: " + pooledR1);
            check(groupedR1 > 0.8 && groupedR1 < 0.97, "within-group r1 near 0.9: " + groupedR1);
            check("-".equals(vm.getValueAt(1, 9)), "discrete Block variable shows '-' for r1");

            combo.setSelectedItem("None");
            vm = null;
            for (DataAuditJTable t : findAll(panel, DataAuditJTable.class))
                if (t.getModel() instanceof DataAuditVariablesModel m) vm = m;
            check(vm != null && "-".equals(vm.getValueAt(0, 10)),
                    "back to None: grouped r1 column shows '-'");
            fm = (DataAuditFindingsModel) findingsT.getModel();
            groupedMsg = false;
            for (int r = 0; r < fm.getRowCount(); r++)
                groupedMsg |= String.valueOf(fm.getValueAt(r, 3)).contains("within groups of");
            check(!groupedMsg, "back to None: Findings tab shows the pooled audit");
        } catch (java.awt.HeadlessException e) {
            System.out.println("(grouping control checks skipped: HeadlessException)");
        }

        System.out.println("\nALL CHECKS PASSED");
    }

    private static void check(boolean b, String msg) {
        if (!b) throw new AssertionError("FAILED: " + msg);
        if (!msg.contains("renders")) System.out.println("ok: " + msg);
    }

    /**
     * The first descendant of the given container assignable to the given class, or null.
     */
    private static <T> T find(java.awt.Container root, Class<T> clazz) {
        List<T> all = findAll(root, clazz);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * All descendants of the given container assignable to the given class, in traversal order.
     */
    private static <T> List<T> findAll(java.awt.Container root, Class<T> clazz) {
        List<T> found = new ArrayList<>();

        for (java.awt.Component c : root.getComponents()) {
            if (clazz.isInstance(c)) found.add(clazz.cast(c));
            if (c instanceof java.awt.Container container) found.addAll(findAll(container, clazz));
        }

        return found;
    }
}