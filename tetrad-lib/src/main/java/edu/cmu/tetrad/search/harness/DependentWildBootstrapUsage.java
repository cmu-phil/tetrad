package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.WildBootstrapMarkovCheck;
import edu.cmu.tetrad.search.WildBootstrapMarkovCheck.Multiplier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-run harness comparing the i.i.d. wild bootstrap Markov check against the two
 * dependence-aware (bootstrap-t) variants on a data file and a saved graph.
 *
 * <pre>
 *   java edu.cmu.tetrad.search.harness.DependentWildBootstrapUsage \
 *        data.txt graph.txt \
 *        [--facts olmp|olmp-sink|local|mb]   fact family (default olmp)
 *        [--coords LON,LAT]                  coordinate columns for blocks/kernel
 *        [--cell auto|SIZE]                  spatial grid cell size for block scheme
 *        [--bandwidth auto|H]                kernel bandwidth(s); comma list for a grid
 *        [--blocksCol NAME]                  a column giving block ids directly (e.g. TOWN)
 *        [--B 2000] [--seed 13]
 * </pre>
 *
 * <p>Example, Boston Housing aggregated to towns, GMAS PAG saved from the interface, OLMP
 * facts, spatial blocks from town centroids plus a kernel bandwidth grid:
 *
 * <pre>
 *   ... DependentWildBootstrapUsage boston-town.txt gmas-pag.txt \
 *        --facts olmp --coords LON,LAT --cell auto --bandwidth auto --B 2000
 * </pre>
 *
 * <p>The data file is tab-delimited with a header. Columns that parse as numbers in every row
 * become continuous variables; other columns (e.g. TOWN) are kept aside and usable only via
 * --blocksCol. Coordinate columns may be columns excluded from the search; facts are filtered
 * to variables present in both graph and data.
 *
 * <p>Reading the output: the i.i.d. check assumes exchangeable rows and is anticonservative
 * under spatial/serial dependence (biased toward FAILING a true model); the block and kernel
 * bootstrap-t checks calibrate against dependence up to the chosen cell size / bandwidth. If
 * the i.i.d. check fails and the dependence-aware checks pass across a sensible grid of
 * scales, the failure was plausibly a row-dependence artifact. Blocks give the cleaner level;
 * the kernel has more power but is more bandwidth-sensitive.
 */
public final class DependentWildBootstrapUsage {

    private DependentWildBootstrapUsage() {
    }

    /**
     * Entry point; see the class Javadoc for arguments.
     *
     * @param args see above.
     * @throws Exception on I/O or interruption.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: DependentWildBootstrapUsage data.txt graph.txt [options]  (see Javadoc)");
            return;
        }
        String dataPath = args[0];
        String graphPath = args[1];
        String factsKind = "olmp";
        String coordsSpec = null;
        String cellSpec = null;
        String bandwidthSpec = null;
        String blocksCol = null;
        int B = 2000;
        long seed = 13L;

        for (int a = 2; a < args.length; a++) {
            switch (args[a]) {
                case "--facts" -> factsKind = args[++a];
                case "--coords" -> coordsSpec = args[++a];
                case "--cell" -> cellSpec = args[++a];
                case "--bandwidth" -> bandwidthSpec = args[++a];
                case "--blocksCol" -> blocksCol = args[++a];
                case "--B" -> B = Integer.parseInt(args[++a]);
                case "--seed" -> seed = Long.parseLong(args[++a]);
                default -> throw new IllegalArgumentException("Unknown option: " + args[a]);
            }
        }

        // ---- Load the data: numeric columns -> continuous DataSet; string columns kept aside.
        List<String> header = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(dataPath))) {
            String line = r.readLine();
            for (String h : line.split("\t")) header.add(h.trim());
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(line.split("\t", -1));
            }
        }
        int n = rows.size();
        int p = header.size();

        boolean[] numeric = new boolean[p];
        for (int c = 0; c < p; c++) {
            numeric[c] = true;
            for (String[] row : rows) {
                try {
                    Double.parseDouble(row[c].trim());
                } catch (NumberFormatException e) {
                    numeric[c] = false;
                    break;
                }
            }
        }

        List<Node> vars = new ArrayList<>();
        List<Integer> numCols = new ArrayList<>();
        for (int c = 0; c < p; c++) {
            if (numeric[c]) {
                vars.add(new ContinuousVariable(header.get(c)));
                numCols.add(c);
            }
        }
        double[][] box = new double[n][numCols.size()];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < numCols.size(); k++) {
                box[i][k] = Double.parseDouble(rows.get(i)[numCols.get(k)].trim());
            }
        }
        DataSet data = new BoxDataSet(new DoubleDataBox(box), vars);
        System.out.println("data: n = " + n + ", numeric columns = " + numCols.size() + " of " + p);

        // ---- Load the graph and generate the implied facts.
        Graph graph = GraphSaveLoadUtils.loadGraphTxt(new File(graphPath));
        ConditioningSetType cst = switch (factsKind) {
            case "olmp" -> ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY;
            case "olmp-sink" -> ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY_SINK_ELIMINATION;
            case "local" -> ConditioningSetType.LOCAL_MARKOV;
            case "mb" -> ConditioningSetType.MARKOV_BLANKET;
            default -> throw new IllegalArgumentException("--facts must be olmp|olmp-sink|local|mb: " + factsKind);
        };
        Set<IndependenceFact> factSet = MarkovCheck.computeAllImpliedFacts(graph, cst);

        List<IndependenceFact> facts = new ArrayList<>();
        int skipped = 0;
        outer:
        for (IndependenceFact f : factSet) {
            if (data.getVariable(f.getX().getName()) == null
                || data.getVariable(f.getY().getName()) == null) {
                skipped++;
                continue;
            }
            for (Node z : f.getZ()) {
                if (data.getVariable(z.getName()) == null) {
                    skipped++;
                    continue outer;
                }
            }
            facts.add(f);
        }
        facts.sort(Comparator.comparing(IndependenceFact::toString));   // deterministic order
        System.out.println("facts (" + factsKind + "): " + facts.size() + " usable"
                           + (skipped > 0 ? ", " + skipped + " skipped (variables not in data)" : ""));
        if (facts.isEmpty()) {
            System.out.println("Nothing to test.");
            return;
        }

        // ---- Coordinates / blocks.
        double[][] coords = null;
        if (coordsSpec != null) {
            String[] names = coordsSpec.split(",");
            int[] idx = new int[names.length];
            for (int k = 0; k < names.length; k++) {
                idx[k] = header.indexOf(names[k].trim());
                if (idx[k] < 0 || !numeric[idx[k]]) {
                    throw new IllegalArgumentException("--coords column not found or not numeric: " + names[k]);
                }
            }
            coords = new double[n][names.length];
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < names.length; k++) {
                    coords[i][k] = Double.parseDouble(rows.get(i)[idx[k]].trim());
                }
            }
            double mnn = WildBootstrapMarkovCheck.medianNearestNeighborDistance(coords);
            System.out.printf("coords: %s; median nearest-neighbor distance = %.6g%n", coordsSpec, mnn);
        }

        int[] colBlocks = null;
        if (blocksCol != null) {
            int c = header.indexOf(blocksCol);
            if (c < 0) throw new IllegalArgumentException("--blocksCol column not found: " + blocksCol);
            Map<String, Integer> ids = new LinkedHashMap<>();
            colBlocks = new int[n];
            for (int i = 0; i < n; i++) {
                colBlocks[i] = ids.computeIfAbsent(rows.get(i)[c].trim(), s -> ids.size());
            }
            System.out.println("blocks from column " + blocksCol + ": " + ids.size() + " blocks");
        }

        // ---- Run the configurations.
        System.out.println();
        System.out.println("== i.i.d. wild bootstrap (exchangeable rows) ==");
        WildBootstrapMarkovCheck mc = new WildBootstrapMarkovCheck(data)
                .setNumBootstraps(B).setSeed(seed).setMultiplier(Multiplier.RADEMACHER);
        System.out.println(mc.checkFacts(facts));

        if (colBlocks != null) {
            System.out.println("== wild cluster bootstrap-t, blocks = " + blocksCol + " ==");
            mc.setBlocks(colBlocks);
            System.out.println(mc.checkFacts(facts));
        }

        if (coords != null) {
            double mnn = WildBootstrapMarkovCheck.medianNearestNeighborDistance(coords);
            if (cellSpec != null) {
                double cell = cellSpec.equals("auto") ? 3.0 * mnn : Double.parseDouble(cellSpec);
                int[] gb = WildBootstrapMarkovCheck.gridBlocks(coords, cell);
                long distinct = java.util.Arrays.stream(gb).distinct().count();
                System.out.printf("== wild cluster bootstrap-t, grid blocks, cell = %.6g (%d blocks) ==%n",
                        cell, distinct);
                mc.setBlocks(gb);
                System.out.println(mc.checkFacts(facts));
            }
            if (bandwidthSpec != null) {
                String[] hs = bandwidthSpec.equals("auto")
                        ? new String[]{Double.toString(2.0 * mnn), Double.toString(3.0 * mnn),
                                       Double.toString(4.0 * mnn)}
                        : bandwidthSpec.split(",");
                for (String hstr : hs) {
                    double h = Double.parseDouble(hstr.trim());
                    System.out.printf("== dependent wild bootstrap-t, kernel bandwidth h = %.6g ==%n", h);
                    mc.setKernel(coords, h);
                    System.out.println(mc.checkFacts(facts));
                }
            }
        }

        if (colBlocks == null && coords == null) {
            System.out.println("(no --coords / --blocksCol given: only the i.i.d. check was run)");
        }
    }
}
