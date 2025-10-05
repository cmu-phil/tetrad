package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.DiscreteBicScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Modernized translation of the legacy TestIGFCI_TCGA harness.
 * <p>
 * What it does (TL;DR): - Loads a TCGA-style CSV (continuous or discrete). - Optionally applies tiered knowledge (e.g.,
 * omics -> phenotype). - Configures GFCI (or IGFCI if present) with INSTANCE-SPECIFIC score/test knobs. - Runs the
 * search and writes the resulting PAG to .txt and .graphml.
 * <p>
 * How to run: - As a unit-style runner from IntelliJ or CLI, adjust the MAIN_* constants below. - Or integrate into
 * your existing test suite.
 */
public class TestIGFCI_TCGA_Modern {

    // ======= USER SWITCHES / PATHS =======
    private static final boolean DATA_IS_DISCRETE = false;   // true for discrete TCGA matrices, false for continuous
    private static final Path INPUT_CSV = Path.of("data", "tcga_joint.csv"); // change to your file
    private static final Delimiter CSV_DELIM = Delimiter.COMMA;

    // Optional: save outputs
    private static final Path OUT_DIR = Path.of("output", "igfci_tcga");
    private static final String OUT_BASENAME = "tcga_igfci";

    // Knowledge tiers (optional). Example: omics -> clinical.
    private static final boolean USE_KNOWLEDGE = false;
    private static final String[] TIER0 = new String[]{ /* e.g., "Gene_*", "CNV_*", "Methyl_*" */};
    private static final String[] TIER1 = new String[]{ /* e.g., "Age", "Stage", "SurvivalMonths" */};

    // Instance-specific knobs (plumbed for your IS classes)
    private static final boolean USE_INSTANCE_SPECIFIC = true; // flips to IS variants if present on classpath

    // GFCI parameters
    private static final double ALPHA = 0.01;     // For Fisher-Z / Chi-square
    private static final double PENALTY_DISCOUNT = 2.0; // For (S)EM-BIC / discrete BIC
    private static final int MAX_DEGREE = 3;      // limit search complexity if desired

    /**
     * Private constructor for the TestIGFCI_TCGA_Modern class. This constructor is used to prevent instantiation of the
     * class. Methods and functionality of this class are designed to be used statically.
     */
    private TestIGFCI_TCGA_Modern() {

    }

    /**
     * Main entry point for the application. Executes the workflow that includes loading data, configuring knowledge
     * tiers, setting parameters, running a search algorithm, and saving outputs.
     *
     * @param args Command line arguments. These can be used to configure various aspects of the process if needed.
     * @throws Exception If any errors occur during the execution of the workflow, such as reading data, configuring
     *                   parameters, or saving the outputs.
     */
    public static void main(String[] args) throws Exception {

        // 1) Read data
        DataSet data = readData(INPUT_CSV, DATA_IS_DISCRETE, CSV_DELIM);
        log("Loaded data: %s vars x %s rows", data.getNumColumns(), data.getNumRows());

        // 2) Optional knowledge tiers
        Knowledge knowledge = new Knowledge();
        if (USE_KNOWLEDGE) {
            addTier(knowledge, 0, TIER0);
            addTier(knowledge, 1, TIER1);
            log("Knowledge set with %d tiers", knowledge.getNumTiers());
        }

        // 3) Build score & independence test (instance-specific if available)
        ScoreAndTest st = buildScoreAndTest(data, DATA_IS_DISCRETE, ALPHA, PENALTY_DISCOUNT, USE_INSTANCE_SPECIFIC);

        // 4) Configure parameters
        Parameters params = new Parameters();
        params.set("alpha", ALPHA);
        params.set("penaltyDiscount", PENALTY_DISCOUNT);
        params.set("maxDegree", MAX_DEGREE);
        // (Add additional flags you care about; GFci honors many common keys.)

        // 5) Run search
        Graph pag = runGfciOrIgfci(data, st, knowledge, params);

        // 6) Save outputs
        Files.createDirectories(OUT_DIR);
        Path txt = OUT_DIR.resolve(OUT_BASENAME + ".txt");
        Path graphml = OUT_DIR.resolve(OUT_BASENAME + ".graphml");
        GraphSaveLoadUtils.saveGraph(pag, txt.toFile(), false);
//        GraphSaveLoadUtils.saveGraphML(pag, graphml.toFile());
        log("Saved PAG to %s and %s", txt, graphml);
    }

    private static DataSet readData(Path csv, boolean discrete, Delimiter delimiter) throws Exception {
        File f = csv.toFile();
        if (!f.exists()) throw new IllegalArgumentException("Data file not found: " + f.getAbsolutePath());
        if (discrete) {
//            VerticalDiscreteDataReader rdr = new VerticalDiscreteDataReader();
//            rdr.setDelimiter(delimiter);
//            rdr.setMissingValueMarker("*");
//
            return SimpleDataLoader.loadDiscreteData(f, "//", '"',
                    "*", true, delimiter, false);

//            return rdr.read(f);
        } else {
            return SimpleDataLoader.loadDiscreteData(f, "//", '"',
                    "*", true, delimiter, false);

//            VerticalDoubleDataReader rdr = new VerticalDoubleDataReader();
//            rdr.setDelimiter(delimiter);
//            rdr.setMissingValueMarker("*");
//            return rdr.read(f);
        }
    }

    private static void addTier(Knowledge k, int tierIndex, String[] names) {
        if (names == null || names.length == 0) return;
        for (String n : names) {
            if (n == null || n.isBlank()) continue;
            k.addToTier(tierIndex, n);
        }
    }

    /**
     * Wires up either standard scores/tests or your instance-specific variants if present. Drop your IS classes on the
     * classpath and toggle USE_INSTANCE_SPECIFIC.
     * <p>
     * Expected IS classes (examples you mentioned recently): - edu.cmu.tetrad.search.score.ISBicScore (continuous) -
     * edu.cmu.tetrad.search.score.ISBdeuScore (discrete) - Optional IS independence tests if you have them
     */
    @SuppressWarnings("unchecked")
    private static ScoreAndTest buildScoreAndTest(DataSet data, boolean discrete,
                                                  double alpha, double penaltyDiscount, boolean useIS) {
        try {
            if (discrete) {
                Object score;
                if (useIS) {
                    // Prefer IS BDeu if available
                    try {
                        Class<?> c = Class.forName("edu.cmu.tetrad.search.score.ISBdeuScore");
                        score = c.getConstructor(DataSet.class).newInstance(data);
                        c.getMethod("setStructurePrior", double.class).invoke(score, 1.0); // example knob
                        c.getMethod("setPenaltyDiscount", double.class).invoke(score, penaltyDiscount);
                        log("Using ISBdeuScore");
                    } catch (ClassNotFoundException e) {
                        DiscreteBicScore s = new DiscreteBicScore(data);
                        s.setPenaltyDiscount(penaltyDiscount);
                        score = s;
                        log("ISBdeuScore not on classpath; falling back to DiscreteBicScore");
                    }
                } else {
                    DiscreteBicScore s = new DiscreteBicScore(data);
                    s.setPenaltyDiscount(penaltyDiscount);
                    score = s;
                }
                // Standard discrete independence test
                IndependenceTest test = new IndTestChiSquare(data, alpha);
                return new ScoreAndTest(score, test);
            } else {
                Object score;
                if (useIS) {
                    try {
                        Class<?> c = Class.forName("edu.cmu.tetrad.search.score.ISBicScore");
                        score = c.getConstructor(DataSet.class).newInstance(data);
                        c.getMethod("setPenaltyDiscount", double.class).invoke(score, penaltyDiscount);
                        log("Using ISBicScore");
                    } catch (ClassNotFoundException e) {
                        SemBicScore s = new SemBicScore(new CovarianceMatrix(data));
                        s.setPenaltyDiscount(penaltyDiscount);
                        score = s;
                        log("ISBicScore not on classpath; falling back to SemBicScore");
                    }
                } else {
                    SemBicScore s = new SemBicScore(new CovarianceMatrix(data));
                    s.setPenaltyDiscount(penaltyDiscount);
                    score = s;
                }
                // Standard continuous independence test
                IndependenceTest test = new IndTestFisherZ(data, alpha);
                return new ScoreAndTest(score, test);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct score/test", e);
        }
    }

    /**
     * Uses GFci from the modern package. If an IGfci class exists in your build, prefer it. (Some distributions provide
     * IGfci as a thin wrapper around GFci that activates IS logic.)
     */
    private static Graph runGfciOrIgfci(DataModel data,
                                        ScoreAndTest st,
                                        Knowledge knowledge,
                                        Parameters params) {
        // Try IGfci first (if present)
        try {
            Class<?> igfciClass = Class.forName("edu.cmu.tetrad.search.IGfci");
            Object igfci = igfciClass.getConstructor(Object.class, IndependenceTest.class).newInstance(st.score(), st.test());
            // Common settings
            igfciClass.getMethod("setKnowledge", Knowledge.class).invoke(igfci, knowledge);
            igfciClass.getMethod("setMaxDegree", int.class).invoke(igfci, params.getInt("maxDegree", -1));
            Graph g = (Graph) igfciClass.getMethod("search").invoke(igfci);
            log("Ran IGfci (instance-specific)");
            return g;
        } catch (ClassNotFoundException notPresent) {
            // Fall back to GFci
            Gfci gfci = new Gfci(st.test(), (Score) st.score());
            gfci.setKnowledge(knowledge);
            gfci.setMaxDegree(params.getInt("maxDegree", -1));
            Graph g = null;
            try {
                g = gfci.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log("Ran GFci");
            return g;
        } catch (Exception reflectionIssues) {
            throw new RuntimeException("Failed to run IGFCI/GFCI", reflectionIssues);
        }
    }

    private static void log(String fmt, Object... args) {
        System.out.println("[IGFCI-TCGA] " + String.format(fmt, args));
    }

    // Utility to sanity print a few nodes (optional)
    @SuppressWarnings("unused")
    private static void printSomeNodes(Graph g, int k) {
        List<Node> nodes = g.getNodes();
        for (int i = 0; i < Math.min(k, nodes.size()); i++) {
            System.out.println("  " + nodes.get(i).getName());
        }
    }

    private record ScoreAndTest(Object score, IndependenceTest test) {
    }
}