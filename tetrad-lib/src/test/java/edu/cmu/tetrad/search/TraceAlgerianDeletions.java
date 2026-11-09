package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * Traces the deletion mechanism on the Algerian data: runs Bfci verbose to capture the separators
 * that licensed each removal, then directly evaluates FisherZ p-values for the FWI-chart pairs
 * against (a) the empty set, (b) the separator Bfci used, and (c) a few structured alternatives,
 * to show how the exhaustive adjacency-subset search finds "independence" under near-determinism.
 */
public final class TraceAlgerianDeletions {
    private TraceAlgerianDeletions() {
    }

    private static final String[] VARS = {"Temperature", "RH", "Ws", "Rain",
            "FFMC", "DMC", "DC", "ISI", "BUI", "FWI"};

    public static void main(String[] args) throws Exception {
        DataSet data = load(args[0]);

        // Capture Bfci's verbose removal log.
        RandomUtil.getInstance().setSeed(42L);
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        Bfci s = new Bfci(test, score);
        s.setNumStarts(1);
        s.setVerbose(true);

        StringBuilder log = new StringBuilder();
        TetradLogger.getInstance().addOutputStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
                log.append((char) b);
            }
        });

        Graph out = s.search();
        System.out.println("Bfci final edges: " + out.getNumEdges());
        System.out.println("\n-- Bfci removal log (chart pairs) --");
        for (String line : log.toString().split("\n")) {
            if (line.contains("Removed edge") || line.contains("Tried removing")) {
                System.out.println(line.trim());
            }
        }

        // Direct p-value evaluations for the chart pairs Bfci deleted.
        System.out.println("\n-- Direct FisherZ p-values (alpha = 0.01; p > alpha reads 'independent') --");
        IndTestFisherZ t2 = new IndTestFisherZ(data, 0.01);
        pv(t2, data, "ISI", "BUI", List.of());
        pv(t2, data, "ISI", "BUI", List.of("FFMC"));
        pv(t2, data, "ISI", "BUI", List.of("DMC", "DC"));
        pv(t2, data, "ISI", "BUI", List.of("FWI"));
        pv(t2, data, "BUI", "FWI", List.of());
        pv(t2, data, "BUI", "FWI", List.of("ISI"));
        pv(t2, data, "BUI", "FWI", List.of("DMC", "DC"));
        pv(t2, data, "Ws", "ISI", List.of());
        pv(t2, data, "Ws", "ISI", List.of("FFMC"));
        pv(t2, data, "Ws", "ISI", List.of("FFMC", "FWI"));
        pv(t2, data, "RH", "ISI", List.of());
        pv(t2, data, "RH", "ISI", List.of("FFMC"));
        pv(t2, data, "ISI", "DC", List.of());
        pv(t2, data, "ISI", "DC", List.of("BUI"));
        pv(t2, data, "FFMC", "FWI", List.of());
        pv(t2, data, "FFMC", "FWI", List.of("ISI"));
        pv(t2, data, "FFMC", "FWI", List.of("ISI", "BUI"));
    }

    private static void pv(IndTestFisherZ t, DataSet data, String x, String y, List<String> cond)
            throws InterruptedException {
        Node nx = data.getVariable(x), ny = data.getVariable(y);
        Set<Node> s = new LinkedHashSet<>();
        for (String c : cond) s.add(data.getVariable(c));
        double p = t.checkIndependence(nx, ny, s).getPValue();
        System.out.printf("p(%s, %s | %s) = %.5f %s%n", x, y, cond, p, p > 0.01 ? "  <-- 'independent'" : "");
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
        for (int i = 0; i < rows.size(); i++)
            for (int j = 0; j < VARS.length; j++)
                box.set(i, j, Double.parseDouble(rows.get(i)[idx.get(VARS[j])].trim()));
        return new BoxDataSet(box, vars);
    }
}
