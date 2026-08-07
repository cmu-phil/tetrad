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
        check(varsModel.getRowCount() == 7 && varsModel.getColumnCount() == 8, "variables table is 7 x 8");

        for (int r = 0; r < varsModel.getRowCount(); r++) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < varsModel.getColumnCount(); c++)
                line.append(varsModel.getValueAt(r, c)).append("\t");
            System.out.println(line);
        }

        check("3".equals(varsModel.getValueAt(3, 5)), "x3 distinct observed == 3, got " + varsModel.getValueAt(3, 5));
        check("-".equals(varsModel.getValueAt(6, 6)), "discrete variable AD p shown as '-'");
        check(!"-".equals(varsModel.getValueAt(2, 6)), "continuous variable AD p shown");
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

        System.out.println("\nALL CHECKS PASSED");
    }

    private static void check(boolean b, String msg) {
        if (!b) throw new AssertionError("FAILED: " + msg);
        if (!msg.contains("renders")) System.out.println("ok: " + msg);
    }
}
