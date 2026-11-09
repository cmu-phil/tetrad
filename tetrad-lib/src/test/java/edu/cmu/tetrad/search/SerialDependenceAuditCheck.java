package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.graph.Node;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Hand-run harness for the SERIAL_DEPENDENCE check in {@link DataAudit}.
 * <p>
 * With no arguments, runs a deterministic selftest: (1) an AR(1) series with rho = 0.9 must be flagged and its
 * estimated lag-1 autocorrelation must be near 0.9; (2) the same values randomly shuffled must not be flagged; (3)
 * two AR(1) blocks with strongly different means, stacked, must show the boundary/mean-shift artifact when pooled
 * and a clean within-block estimate when the block variable is passed as the serial grouping variable; (4) i.i.d.
 * noise must not be flagged; (5) naming a missing or continuous grouping variable must throw.
 * <p>
 * With arguments: args[0] is the path to a tab-delimited dataset with a header row; args[1] (optional) is the name
 * of a grouping column, which is treated as discrete (all other columns are treated as continuous). Prints the audit
 * report, the per-variable lag-1 autocorrelations and Ljung-Box p-values, and, if a grouping variable is given, the
 * pooled-vs-grouped comparison.
 */
public final class SerialDependenceAuditCheck {

    private SerialDependenceAuditCheck() {
    }

    /**
     * Entry point; see the class Javadoc.
     *
     * @param args see the class Javadoc.
     * @throws Exception if the dataset cannot be read.
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            selftest();
        } else {
            runOnFile(args[0], args.length > 1 ? args[1] : null);
        }
    }

    private static void runOnFile(String path, String groupVar) throws Exception {
        DataSet data = load(path, groupVar);
        System.out.println("Loaded " + data.getNumRows() + " x " + data.getNumColumns() + " from " + path);

        System.out.println("\n================ POOLED (no grouping) ================\n");
        DataAudit pooled = new DataAudit(data);
        System.out.println(pooled.report());
        printSerialStats(pooled);

        if (groupVar != null) {
            System.out.println("\n================ WITHIN GROUPS OF " + groupVar + " ================\n");
            DataAudit grouped = new DataAudit(data, new DataAudit.Config().withSerialGroupVariable(groupVar));
            System.out.println(grouped.report());
            printSerialStats(grouped);

            System.out.println("\nPooled vs. within-group lag-1 autocorrelations:");
            Map<String, Double> p1 = pooled.getLag1Autocorrelations();
            Map<String, Double> g1 = grouped.getLag1Autocorrelations();

            for (String name : p1.keySet()) {
                System.out.printf("  %-15s pooled r1 = %8.4f   within-group r1 = %8.4f%n",
                        name, p1.get(name), g1.getOrDefault(name, Double.NaN));
            }
        }
    }

    private static void printSerialStats(DataAudit audit) {
        System.out.println("Lag-1 autocorrelations and Ljung-Box p-values:");
        Map<String, Double> r1 = audit.getLag1Autocorrelations();
        Map<String, Double> lb = audit.getSerialDependencePValues();

        for (String name : r1.keySet()) {
            System.out.printf("  %-15s r1 = %8.4f   Ljung-Box p = %.4g%n", name, r1.get(name), lb.get(name));
        }
    }

    //==================================== SELFTEST ====================================//

    private static void selftest() {
        Random rng = new Random(38);
        int n = 500;
        int failures = 0;

        // (1) AR(1), rho = 0.9: must flag, r1 near 0.9.
        double[] ar = ar1(n, 0.9, rng);
        DataAudit a1 = auditOf(ar, null, null);
        double r1 = a1.getLag1Autocorrelations().get("X");
        boolean flagged1 = a1.hasFinding(FindingCode.SERIAL_DEPENDENCE);
        failures += check("AR(1) rho=0.9 flagged", flagged1);
        failures += check("AR(1) rho=0.9 r1 in (0.8, 0.97), got " + fmt(r1), r1 > 0.8 && r1 < 0.97);
        AuditFinding f1 = a1.getFindings(FindingCode.SERIAL_DEPENDENCE).get(0);
        Double nEff = f1.getValues().get("effectiveSampleSize");
        failures += check("AR(1) reports effectiveSampleSize < n/5, got " + (nEff == null ? "null" : fmt(nEff)),
                nEff != null && nEff < n / 5.0);

        // (2) Same values shuffled: must not flag.
        List<Double> shuffled = new ArrayList<>();
        for (double x : ar) shuffled.add(x);
        Collections.shuffle(shuffled, rng);
        double[] sh = shuffled.stream().mapToDouble(Double::doubleValue).toArray();
        DataAudit a2 = auditOf(sh, null, null);
        failures += check("Shuffled AR(1) not flagged", !a2.hasFinding(FindingCode.SERIAL_DEPENDENCE));

        // (3) Two AR(1) blocks with a large mean shift: pooled r1 inflated by the shift; grouped r1 near 0.9 and
        // the grouped audit must still (correctly) flag, since the within-block series really are dependent.
        double[] blockA = ar1(n, 0.9, rng);
        double[] blockB = ar1(n, 0.9, rng);
        double[] stacked = new double[2 * n];
        int[] block = new int[2 * n];

        for (int i = 0; i < n; i++) {
            stacked[i] = blockA[i];               // mean 0
            stacked[n + i] = blockB[i] + 20.0;    // mean 20
            block[i] = 0;
            block[n + i] = 1;
        }

        DataAudit a3p = auditOf(stacked, block, null);
        DataAudit a3g = auditOf(stacked, block, "Block");
        double r1p = a3p.getLag1Autocorrelations().get("X");
        double r1g = a3g.getLag1Autocorrelations().get("X");
        failures += check("Stacked-blocks pooled r1 inflated above 0.97 by mean shift, got " + fmt(r1p), r1p > 0.97);
        failures += check("Stacked-blocks within-group r1 in (0.8, 0.97), got " + fmt(r1g), r1g > 0.8 && r1g < 0.97);
        failures += check("Stacked-blocks within-group still flagged", a3g.hasFinding(FindingCode.SERIAL_DEPENDENCE));

        // (4) i.i.d. noise: must not flag.
        double[] iid = new double[n];
        for (int i = 0; i < n; i++) iid[i] = rng.nextGaussian();
        DataAudit a4 = auditOf(iid, null, null);
        failures += check("i.i.d. noise not flagged", !a4.hasFinding(FindingCode.SERIAL_DEPENDENCE));

        // (5) A variable constant within each group at non-representable decimal values (e.g., a town-level
        // attribute grouped by town): per-group centering leaves only rounding residue, from which autocorrelations
        // would be floating-point noise. The relative variance guard must skip it entirely rather than flag it.
        double[] townLevel = new double[2 * n];

        for (int i = 0; i < n; i++) {
            townLevel[i] = 15.3;      // block 0
            townLevel[n + i] = 17.8;  // block 1
        }

        DataAudit a5 = auditOf(townLevel, block, "Block");
        failures += check("Constant-within-group decimal variable not flagged",
                !a5.hasFinding(FindingCode.SERIAL_DEPENDENCE));
        failures += check("Constant-within-group decimal variable skipped (no r1 entry)",
                !a5.getLag1Autocorrelations().containsKey("X"));

        // (6) Bad grouping variables must throw.
        failures += check("Absent grouping variable throws", throwsIae(() -> auditOf(iid, null, "NoSuchVar")));
        failures += check("Continuous grouping variable throws", throwsIae(() -> auditOf(iid, null, "X")));

        System.out.println(failures == 0 ? "\nSELFTEST PASSED" : "\nSELFTEST FAILED: " + failures + " failure(s)");
        if (failures > 0) System.exit(1);
    }

    private static double[] ar1(int n, double rho, Random rng) {
        double[] x = new double[n];
        x[0] = rng.nextGaussian();

        for (int i = 1; i < n; i++) {
            x[i] = rho * x[i - 1] + Math.sqrt(1 - rho * rho) * rng.nextGaussian();
        }

        return x;
    }

    /**
     * Builds a dataset with continuous column X (and discrete column Block if block indices are given) and audits it
     * with the given serial grouping variable (or defaults if null).
     */
    private static DataAudit auditOf(double[] x, int[] block, String groupVar) {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        if (block != null) vars.add(new DiscreteVariable("Block", 2));

        MixedDataBox box = new MixedDataBox(vars, x.length);

        for (int i = 0; i < x.length; i++) {
            box.set(i, 0, x[i]);
            if (block != null) box.set(i, 1, block[i]);
        }

        DataSet data = new BoxDataSet(box, vars);
        return groupVar == null ? new DataAudit(data)
                : new DataAudit(data, new DataAudit.Config().withSerialGroupVariable(groupVar));
    }

    private static int check(String what, boolean ok) {
        System.out.println((ok ? "  ok:   " : "  FAIL: ") + what);
        return ok ? 0 : 1;
    }

    private static boolean throwsIae(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static String fmt(double x) {
        return String.format("%.4f", x);
    }

    //==================================== LOADING ====================================//

    /**
     * Loads a tab-delimited dataset with a header row. The column named groupVar (if any) is treated as discrete;
     * all other columns are treated as continuous. Empty fields and "*" are treated as missing.
     */
    private static DataSet load(String path, String groupVar) throws Exception {
        List<String[]> rows = new ArrayList<>();
        String[] header;

        try (BufferedReader in = new BufferedReader(new FileReader(path))) {
            header = in.readLine().split("\t", -1);
            String line;

            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(line.split("\t", -1));
            }
        }

        int groupCol = groupVar == null ? -1 : Arrays.asList(header).indexOf(groupVar);

        if (groupVar != null && groupCol == -1) {
            throw new IllegalArgumentException("Grouping column '" + groupVar + "' not found in header.");
        }

        List<Node> vars = new ArrayList<>();
        List<String> levels = new ArrayList<>();

        if (groupCol >= 0) {
            for (String[] row : rows) {
                if (!levels.contains(row[groupCol])) levels.add(row[groupCol]);
            }
        }

        for (int j = 0; j < header.length; j++) {
            vars.add(j == groupCol ? new DiscreteVariable(header[j], levels)
                    : new ContinuousVariable(header[j]));
        }

        MixedDataBox box = new MixedDataBox(vars, rows.size());

        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < header.length; j++) {
                String s = rows.get(i)[j].trim();

                if (j == groupCol) {
                    box.set(i, j, levels.indexOf(rows.get(i)[j]));
                } else if (s.isEmpty() || s.equals("*")) {
                    box.set(i, j, Double.NaN);
                } else {
                    box.set(i, j, Double.parseDouble(s));
                }
            }
        }

        return new BoxDataSet(box, vars);
    }
}
