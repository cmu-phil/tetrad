package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.search.Gfci;
import edu.cmu.tetrad.search.score.BDeuScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * CLI runner used to reproduce the original IGFCI-on-TCGA workflow.
 *
 * <p>Refactor goals:
 * <ul>
 *   <li>Separate concerns (arg parsing, IO, population run, LOO run).</li>
 *   <li>Safer IO and error handling, no broad catch named as an IOException variable.</li>
 *   <li>Use Paths/Files and create directories robustly.</li>
 *   <li>Preserve original defaults/behavior.</li>
 * </ul>
 *
 * <p>Usage examples:
 * <pre>
 *   java ... TestIGFCI_TCGA \
 *     -data gsva_dis_25 -knowledge forbid_pairs_nodes2 -dir /path/to/dir \
 *     -th true -cutoff 0.5 -kappa 0.5 -bs 1
 * </pre>
 */
public class TestIGFCI_TCGA {

    /**
     * Constructs a new instance of the TestIGFCI_TCGA class. This constructor serves as
     * the initialization method for the class. Currently, it performs no additional
     * operations beyond creating an instance of TestIGFCI_TCGA.
     */
    public TestIGFCI_TCGA() {

    }

    // --- Defaults preserved from the legacy version ---
    private static final String DEFAULT_DATA_NAME = "gsva_dis_25";
    private static final String DEFAULT_KNOWLEDGE_NAME = "forbid_pairs_nodes2";
    private static final String DEFAULT_NAME_COLUMN = "Name"; // column removed from the data
    private static final char DEFAULT_DELIM = ',';
    private static final double DEFAULT_CUTOFF = 0.5;
    private static final double DEFAULT_KAPPA = 0.5;
    private static final boolean DEFAULT_THRESHOLD = true;
    private static final int DEFAULT_BOOTSTRAP_MULTIPLIER = 1; // nbs

    // 90% bootstrap as in the legacy code
    private static final double BOOTSTRAP_FRACTION = 0.9;

    /**
     * The main entry point of the application. It initializes the execution by parsing command-line arguments,
     * configuring the options, and calling the execution logic.
     *
     * @param args the command-line arguments. Supported arguments include:
     *             - `-h` or `--help`: displays help information.
     *             - `-th`: specifies whether thresholding is enabled (expected boolean value).
     *             - `-alpha`: a legacy parameter kept for compatibility, though unused (expected double value).
     *             - `-cutoff`: sets the cutoff value (expected double value).
     *             - `-kappa`: sets the kappa value (expected double value).
     *             - `-data`: specifies the data name (expected string).
     *             - `-knowledge`: specifies the knowledge file name (expected string).
     *             - `-dir`: specifies the directory containing data and knowledge files (expected string).
     *             - `-bs`: specifies the bootstrap multiplier or index (expected integer).
     */
    public static void main(String[] args) {
        // Print raw args for reproducibility (legacy behavior)
        System.out.println(Arrays.asList(args));

        // Parse CLI
        CliOptions opt = parseArgs(args);

        // Kick off
        new TestIGFCI_TCGA().run(opt);
    }

    private void run(CliOptions opt) {
        // Seed matches the legacy formula: 1454147771L + 100 * nbs
        RandomUtil.getInstance().setSeed(1454147771L + 100L * opt.bootstrapIndex);

        // Inputs
        Path dataCsv = Paths.get(opt.dataDir).resolve(opt.dataName + ".csv");
        Path knowledgeCsv = Paths.get(opt.dataDir).resolve(opt.knowledgeName + ".csv");

        // Output dir
        Path outDir = Paths.get(opt.dataDir).resolve(Paths.get("outputs_PAGs_BS", opt.dataName));
        ensureDir(outDir);

        // Read data
        DataSet trainWithNames = readDiscreteCsv(dataCsv, DEFAULT_DELIM);
        DataSet trainData = stripNameColumn(trainWithNames, DEFAULT_NAME_COLUMN);

        // Bootstrap sample (population run uses bs in the legacy code)
        DataSet bs = bootstrap(trainData, BOOTSTRAP_FRACTION);

        // Knowledge
        Knowledge knowledge = loadKnowledgePairs(knowledgeCsv);

        // --- Population search ---
        System.out.println("begin population search");
        Graph populationPag = runPopulation(bs, knowledge, opt);
        saveGraph(populationPag, outDir.resolve(opt.dataName + "_populationWide_" + opt.bootstrapIndex + ".txt"));

        // --- Leave-one-out instance-specific runs ---
        leaveOneOut(trainWithNames, trainData, bs, knowledge, opt, outDir);
    }

    // ================== Pipeline pieces ==================

    private Graph runPopulation(DataSet bs, Knowledge knowledge, CliOptions opt) {
        IndTestProbabilisticBDeu indTest = new IndTestProbabilisticBDeu(bs, /*alpha*/ 0.5); // original used 0.5; CLI -alpha was unused
        indTest.setThreshold(opt.threshold);
        indTest.setCutoff(opt.cutoff);

        BDeuScore score = new BDeuScore(bs);
        Gfci gfci = new Gfci(indTest, score);
        gfci.setKnowledge(knowledge);
        try {
            return gfci.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Population search interrupted", e);
        }
    }

    private void leaveOneOut(
            DataSet trainWithNames,
            DataSet trainData,
            DataSet bs,
            Knowledge knowledge,
            CliOptions opt,
            Path outDir
    ) {
        final int n = trainData.getNumRows();
        final int nameColIndex = trainWithNames.getColumn(trainWithNames.getVariable(DEFAULT_NAME_COLUMN));

        BDeuScore populationScore = new BDeuScore(bs); // for structure prior only

        for (int i = 0; i < n; i++) {
            System.out.println("case i: " + i);

            // Train/test split for LOO
            DataSet train = trainData.copy();
            DataSet test = train.subsetRows(new int[]{i});
            train.removeRows(new int[]{i});

            // Instance-specific independence test
            IndTestProbabilisticISBDeu indTestIS = new IndTestProbabilisticISBDeu(bs, test, populationScore.getStructurePrior());
            indTestIS.setThreshold(opt.threshold);
            indTestIS.setCutoff(opt.cutoff);

            // Instance-specific score (kappa for add/delete/reorient)
            IsBDeuScore2 scoreIS = new IsBDeuScore2(bs, test);
            scoreIS.setKAddition(opt.kappa);
            scoreIS.setKDeletion(opt.kappa);
            scoreIS.setKReorientation(opt.kappa);

            // Background score on train for mixed objective
            Score scoreTrain = new BDeuScore(train);

            // Search
            IsGFci isGfci = new IsGFci(indTestIS, scoreIS, scoreTrain);
            isGfci.setKnowledge(knowledge);
            Graph graph;
            try {
                graph = isGfci.search();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Instance-specific search interrupted (i=" + i + ")", e);
            }

            String caseName = (String) trainWithNames.getObject(i, nameColIndex);
            Path out = outDir.resolve(opt.dataName + "_" + caseName + "_" + opt.bootstrapIndex + ".txt");
            saveGraph(graph, out);
        }
    }

    // ================== IO helpers ==================

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create output directory: " + dir, e);
        }
    }

    private static DataSet readDiscreteCsv(Path csv, char delimiter) {
        VerticalDiscreteTabularDatasetReader reader =
                new VerticalDiscreteTabularDatasetFileReader(csv, DelimiterUtils.toDelimiter(delimiter));
        try {
            return (DataSet) DataConvertUtils.toDataModel(reader.readInData());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read dataset: " + csv, e);
        }
    }

    private static DataSet stripNameColumn(DataSet withNames, String nameCol) {
        DataSet copy = withNames.copy();
        int idx = copy.getColumn(copy.getVariable(nameCol));
        copy.removeColumn(idx);
        return copy;
    }

    private static DataSet bootstrap(DataSet data, double fraction) {
        int numRows = (int) Math.round(fraction * data.getNumRows());
        return new BootstrapSampler().sample(data, numRows);
        // NOTE: BootstrapSampler().sample() samples with replacement in Tetrad.
    }

    private static Knowledge loadKnowledgePairs(Path csv) {
        DataSet knowledgeData = readDiscreteCsv(csv, DEFAULT_DELIM);
        Knowledge k = new Knowledge();
        for (int i = 0; i < knowledgeData.getNumRows(); i++) {
            String a = Objects.toString(knowledgeData.getObject(i, 0));
            String b = Objects.toString(knowledgeData.getObject(i, 1));
            // The legacy code sets both directions forbidden
            k.setForbidden(a, b);
            k.setForbidden(b, a);
        }
        return k;
    }

    private static void saveGraph(Graph g, Path out) {
        File f = out.toFile();
        GraphSaveLoadUtils.saveGraph(g, f, false);
        System.out.println("Wrote: " + out);
    }

    // ================== CLI ==================

    private static CliOptions parseArgs(String[] args) {
        // Defaults
        CliOptions opt = new CliOptions();
        opt.dataDir = System.getProperty("user.dir");
        opt.threshold = DEFAULT_THRESHOLD;
        opt.cutoff = DEFAULT_CUTOFF;
        opt.kappa = DEFAULT_KAPPA;
        opt.dataName = DEFAULT_DATA_NAME;
        opt.knowledgeName = DEFAULT_KNOWLEDGE_NAME;
        opt.bootstrapIndex = DEFAULT_BOOTSTRAP_MULTIPLIER;

        if (args == null || args.length == 0) {
            return opt; // use defaults
        }

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h":
                case "--help":
                    printHelpAndExit();
                    break;
                case "-th":
                    opt.threshold = Boolean.parseBoolean(required(args, ++i, "-th"));
                    break;
                case "-alpha":
                    // NOTE: alpha was parsed in legacy but never used; we keep it for compatibility
                    opt.alpha = Double.parseDouble(required(args, ++i, "-alpha"));
                    break;
                case "-cutoff":
                    opt.cutoff = Double.parseDouble(required(args, ++i, "-cutoff"));
                    break;
                case "-kappa":
                    opt.kappa = Double.parseDouble(required(args, ++i, "-kappa"));
                    break;
                case "-data":
                    opt.dataName = required(args, ++i, "-data");
                    break;
                case "-knowledge":
                    opt.knowledgeName = required(args, ++i, "-knowledge");
                    break;
                case "-dir":
                    opt.dataDir = required(args, ++i, "-dir");
                    break;
                case "-bs":
                    opt.bootstrapIndex = Integer.parseInt(required(args, ++i, "-bs"));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + a + ". Use -h for help.");
            }
        }

        return opt;
    }

    private static String required(String[] args, int i, String opt) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for " + opt);
        }
        return args[i];
    }

    private static void printHelpAndExit() {
        System.out.println("Usage: TestIGFCI_TCGA [options]\n" +
                           "  -data <name>         Data CSV base name (default: " + DEFAULT_DATA_NAME + ")\n" +
                           "  -knowledge <name>    Knowledge CSV base name (default: " + DEFAULT_KNOWLEDGE_NAME + ")\n" +
                           "  -dir <path>          Directory containing CSVs (default: user.dir)\n" +
                           "  -th <true|false>     Use thresholding in tests (default: " + DEFAULT_THRESHOLD + ")\n" +
                           "  -cutoff <double>     Test cutoff (default: " + DEFAULT_CUTOFF + ")\n" +
                           "  -kappa <double>      kappa for add/delete/reorient (default: " + DEFAULT_KAPPA + ")\n" +
                           "  -alpha <double>      (parsed but not used; kept for compatibility)\n" +
                           "  -bs <int>            Bootstrap index multiplier (default: " + DEFAULT_BOOTSTRAP_MULTIPLIER + ")\n" +
                           "  -h, --help           Show this help");
        System.exit(0);
    }

    // Simple POJO for parsed options
    private static class CliOptions {
        String dataDir;
        String dataName;
        String knowledgeName;
        boolean threshold;
        double cutoff;
        double kappa;
        double alpha; // kept for compatibility though unused
        int bootstrapIndex;
    }
}
