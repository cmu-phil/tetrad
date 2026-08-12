package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * Diagnostic: why do Fcit / FcitSl differ from Bfci / GraspFci on the Algerian Forest Fires data
 * when all are seeded by BOSS or GRaSP? Continuous subsystem only (Temperature .. FWI, 10 vars),
 * matched score/test/settings, fixed seed per run. Compares each output pairwise and against the
 * FWI-system reference chart documented in the dataset's readme
 * (https://www.nwcg.gov/publications/pms437/cffdrs/fire-weather-index-system):
 * FFMC &lt;- Temp,RH,Ws,Rain; DMC &lt;- Temp,RH,Rain; DC &lt;- Temp,Rain; ISI &lt;- FFMC,Ws;
 * BUI &lt;- DMC,DC; FWI &lt;- ISI,BUI. That chart is the repo's own ground-truth pointer, used here
 * only to DESCRIBE agreement, not as search input. No background knowledge is given to any run,
 * so the comparison isolates the algorithms' post-seed machinery.
 */
public final class DiagnoseAlgerianFires {
    private DiagnoseAlgerianFires() {
    }

    private static final String[] VARS = {"Temperature", "RH", "Ws", "Rain",
            "FFMC", "DMC", "DC", "ISI", "BUI", "FWI"};

    // Reference chart, child <- parents.
    private static final Map<String, List<String>> FWI_REF = new LinkedHashMap<>();

    static {
        FWI_REF.put("FFMC", List.of("Temperature", "RH", "Ws", "Rain"));
        FWI_REF.put("DMC", List.of("Temperature", "RH", "Rain"));
        FWI_REF.put("DC", List.of("Temperature", "Rain"));
        FWI_REF.put("ISI", List.of("FFMC", "Ws"));
        FWI_REF.put("BUI", List.of("DMC", "DC"));
        FWI_REF.put("FWI", List.of("ISI", "BUI"));
    }

    public static void main(String[] args) throws Exception {
        DataSet data = load(args[0]);
        System.out.println("Loaded " + data.getNumRows() + " x " + data.getNumColumns());

        // Near-determinism scan: R^2 of each FWI-chart child on its documented parents.
        System.out.println("\n-- Determinism scan (R^2 of each index on its documented parents) --");
        for (Map.Entry<String, List<String>> e : FWI_REF.entrySet()) {
            System.out.printf("%-5s <- %-28s R^2 = %.4f%n", e.getKey(), e.getValue(),
                    rSquared(data, e.getKey(), e.getValue()));
        }

        Map<String, Graph> results = new LinkedHashMap<>();

        RandomUtil.getInstance().setSeed(42L);
        results.put("Bfci(BOSS)", runBfci(data));

        RandomUtil.getInstance().setSeed(42L);
        results.put("GraspFci", runGraspFci(data));

        RandomUtil.getInstance().setSeed(42L);
        results.put("Fcit(BOSS)", runFcit(data, Fcit.START_WITH.BOSS));

        RandomUtil.getInstance().setSeed(42L);
        results.put("Fcit(GRASP)", runFcit(data, Fcit.START_WITH.GRASP));

        RandomUtil.getInstance().setSeed(42L);
        results.put("FcitSl", runFcitSl(data));

        System.out.println("\n-- Outputs --");
        for (Map.Entry<String, Graph> e : results.entrySet()) {
            System.out.println("\n### " + e.getKey() + " (" + e.getValue().getNumEdges() + " edges)");
            List<Edge> edges = new ArrayList<>(e.getValue().getEdges());
            edges.sort(Comparator.comparing(Edge::toString));
            for (Edge edge : edges) System.out.println("  " + edge);
        }

        System.out.println("\n-- Agreement with the documented FWI chart --");
        System.out.printf("%-12s %8s %8s %8s %8s%n", "algorithm", "adjTP", "adjFP", "adjFN", "orientOK");
        for (Map.Entry<String, Graph> e : results.entrySet()) {
            int[] a = agreement(e.getValue());
            System.out.printf("%-12s %8d %8d %8d %8d%n", e.getKey(), a[0], a[1], a[2], a[3]);
        }

        System.out.println("\n-- Pairwise skeleton differences (edges in A not in B / in B not in A) --");
        List<String> names = new ArrayList<>(results.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                Graph a = results.get(names.get(i)), b = results.get(names.get(j));
                List<String> onlyA = new ArrayList<>(), onlyB = new ArrayList<>();
                for (Edge e : a.getEdges())
                    if (!b.isAdjacentTo(b.getNode(e.getNode1().getName()), b.getNode(e.getNode2().getName())))
                        onlyA.add(e.getNode1() + "--" + e.getNode2());
                for (Edge e : b.getEdges())
                    if (!a.isAdjacentTo(a.getNode(e.getNode1().getName()), a.getNode(e.getNode2().getName())))
                        onlyB.add(e.getNode1() + "--" + e.getNode2());
                System.out.printf("%-12s vs %-12s : only-in-first=%s only-in-second=%s%n",
                        names.get(i), names.get(j), onlyA, onlyB);
            }
        }
    }

    /** adjTP, adjFP, adjFN over the FWI chart's adjacencies; orientOK = chart edges rendered with an arrowhead at the child and no arrowhead at the parent. */
    private static int[] agreement(Graph g) {
        Set<Set<String>> refAdj = new HashSet<>();
        for (Map.Entry<String, List<String>> e : FWI_REF.entrySet())
            for (String p : e.getValue()) refAdj.add(Set.of(e.getKey(), p));

        int tp = 0, fp = 0, orientOk = 0;
        for (Edge e : g.getEdges()) {
            Set<String> pair = Set.of(e.getNode1().getName(), e.getNode2().getName());
            if (refAdj.contains(pair)) tp++;
            else fp++;
        }
        int fn = refAdj.size() - tp;

        for (Map.Entry<String, List<String>> e : FWI_REF.entrySet()) {
            Node child = g.getNode(e.getKey());
            for (String p : e.getValue()) {
                Node parent = g.getNode(p);
                if (child == null || parent == null || !g.isAdjacentTo(parent, child)) continue;
                boolean headAtChild = g.getEndpoint(parent, child) == Endpoint.ARROW;
                boolean headAtParent = g.getEndpoint(child, parent) == Endpoint.ARROW;
                if (headAtChild && !headAtParent) orientOk++;
            }
        }
        return new int[]{tp, fp, fn, orientOk};
    }

    private static double rSquared(DataSet data, String yName, List<String> xNames) {
        int n = data.getNumRows();
        double[] y = col(data, yName);
        double[][] x = new double[n][xNames.size() + 1];
        for (int i = 0; i < n; i++) x[i][0] = 1.0;
        for (int j = 0; j < xNames.size(); j++) {
            double[] c = col(data, xNames.get(j));
            for (int i = 0; i < n; i++) x[i][j + 1] = c[i];
        }
        // Normal equations via simple Gaussian elimination.
        int p = xNames.size() + 1;
        double[][] xtx = new double[p][p];
        double[] xty = new double[p];
        for (int i = 0; i < n; i++)
            for (int a = 0; a < p; a++) {
                xty[a] += x[i][a] * y[i];
                for (int b = 0; b < p; b++) xtx[a][b] += x[i][a] * x[i][b];
            }
        double[] beta = solve(xtx, xty);
        double ybar = Arrays.stream(y).average().orElse(0);
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            double pred = 0;
            for (int a = 0; a < p; a++) pred += x[i][a] * beta[a];
            ssRes += (y[i] - pred) * (y[i] - pred);
            ssTot += (y[i] - ybar) * (y[i] - ybar);
        }
        return 1 - ssRes / ssTot;
    }

    private static double[] solve(double[][] a, double[] b) {
        int nn = b.length;
        double[][] m = new double[nn][nn + 1];
        for (int i = 0; i < nn; i++) {
            System.arraycopy(a[i], 0, m[i], 0, nn);
            m[i][nn] = b[i];
        }
        for (int c = 0; c < nn; c++) {
            int piv = c;
            for (int r = c + 1; r < nn; r++) if (Math.abs(m[r][c]) > Math.abs(m[piv][c])) piv = r;
            double[] t = m[c]; m[c] = m[piv]; m[piv] = t;
            if (Math.abs(m[c][c]) < 1e-12) continue;
            for (int r = 0; r < nn; r++) {
                if (r == c) continue;
                double f = m[r][c] / m[c][c];
                for (int k = c; k <= nn; k++) m[r][k] -= f * m[c][k];
            }
        }
        double[] out = new double[nn];
        for (int i = 0; i < nn; i++) out[i] = Math.abs(m[i][i]) < 1e-12 ? 0 : m[i][nn] / m[i][i];
        return out;
    }

    private static double[] col(DataSet data, String name) {
        int j = data.getColumnIndex(name);
        double[] out = new double[data.getNumRows()];
        for (int i = 0; i < data.getNumRows(); i++) out[i] = data.getDouble(i, j);
        return out;
    }

    private static DataSet load(String path) throws Exception {
        List<String[]> rows = new ArrayList<>();
        String[] header;
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            header = r.readLine().split("\t");
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) rows.add(line.split("\t"));
            }
        }
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) idx.put(header[i].trim(), i);

        List<Node> vars = new ArrayList<>();
        for (String v : VARS) vars.add(new ContinuousVariable(v));
        DoubleDataBox box = new DoubleDataBox(rows.size(), VARS.length);
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < VARS.length; j++) {
                box.set(i, j, Double.parseDouble(rows.get(i)[idx.get(VARS[j])].trim()));
            }
        }
        return new BoxDataSet(box, vars);
    }

    private static Graph runBfci(DataSet data) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        Bfci s = new Bfci(test, score);
        s.setNumStarts(1);
        return s.search();
    }

    private static Graph runGraspFci(DataSet data) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        GraspFci s = new GraspFci(test, score);
        s.setNumStarts(1);
        return s.search();
    }

    private static Graph runFcit(DataSet data, Fcit.START_WITH sw) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        Fcit s = new Fcit(test, score);
        s.setNumStarts(1);
        s.setStartWith(sw);
        return s.search();
    }

    private static Graph runFcitSl(DataSet data) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        FcitSl s = new FcitSl(test, score);
        return s.search();
    }
}
