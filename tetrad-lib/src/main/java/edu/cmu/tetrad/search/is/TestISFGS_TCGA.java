package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.BDeuScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetReader;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

/**
 * CLI runner for instance-specific FGES on TCGA-like discrete data.
 *
 * <p>Refactor highlights:
 * <ul>
 *   <li>Correct argument handling (compute paths after parsing).</li>
 *   <li>Extracted helpers for IO, population run, IS run, bootstrap.</li>
 *   <li>try-with-resources for CSV log; robust directory creation.</li>
 *   <li>Preserves original defaults/behavior (kappa sweep fixed at p=5).</li>
 * </ul>
 */
public class TestISFGS_TCGA {

    private static final String DEFAULT_DATA_NAME = "gsva_dis_25";
    private static final String DEFAULT_NAME_COLUMN = "Name";
    private static final char DEFAULT_DELIM = ',';

    public static void main(String[] args) {
        // Print raw args for reproducibility
        System.out.println(Arrays.asList(args));

        // Defaults
        String dataName = DEFAULT_DATA_NAME;
        String dataDir = System.getProperty("user.dir");
        int numBootstraps = 1;

        // Parse args
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-data":
                    dataName = required(args, ++i, "-data");
                    break;
                case "-dir":
                    dataDir = required(args, ++i, "-dir");
                    break;
                case "-bs":
                    numBootstraps = Integer.parseInt(required(args, ++i, "-bs"));
                    break;
                case "-h":
                case "--help":
                    printHelpAndExit();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + args[i] + ". Use -h for help.");
            }
        }

        new TestISFGS_TCGA().run(dataDir, dataName, numBootstraps);
    }

    private void run(String dataDir, String dataName, int numBootstraps) {
        Path dataCsv = Paths.get(dataDir).resolve(dataName + ".csv");
        Path outDir = Paths.get(dataDir).resolve(Paths.get("outputs", dataName, "kappa_0.5")); // matches p=5 → 0.5
        ensureDir(outDir);

        // Read data
        DataSet dataWithNames = readDiscreteCsv(dataCsv, DEFAULT_DELIM);
        System.out.println(dataWithNames.getNumRows() + ", " + dataWithNames.getNumColumns());
        DataSet data = stripNameColumn(dataWithNames, DEFAULT_NAME_COLUMN);

        // Population model
        double samplePrior = 1.0;
        double structurePrior = 1.0;
        Graph graphP = learnPopulationFges(data, samplePrior, structurePrior);
        System.out.println("Pop graph:" + graphP.getEdges());

        // Persist population graph (legacy path pattern)
        saveGraph(graphP, outDir.resolve(dataName + "_population_wide.txt"));

        // Legacy code sweeps p=5..5 giving kappa = 0.5; preserve behavior
        double kappa = 0.5;
        Path csv = outDir.resolve(dataName + "_kappa_" + kappa + ".csv");

        try (PrintStream out = new PrintStream(Files.newOutputStream(csv))) {
            out.println("case, m-likelihood pop, m-likelihood IS");

            // LOO × bootstrap
            final int n = data.getNumRows();
            final int nameIdx = dataWithNames.getColumn(dataWithNames.getVariable(DEFAULT_NAME_COLUMN));

            for (int i = 0; i < n; i++) {
                // LOO split
                DataSet train = data.copy();
                DataSet test = data.subsetRows(new int[]{i});
                train.removeRows(new int[]{i});

                for (int b = 0; b < numBootstraps; b++) {
                    DataSet bs = bootstrap(train, train.getNumRows());

                    BDeuScore popScoreOnBs = new BDeuScore(bs);

                    // Score population graph under IS objective for comparison
                    IsBDeuScore2 scoreIS_forPop = new IsBDeuScore2(bs, test);
                    scoreIS_forPop.setSamplePrior(samplePrior);
                    scoreIS_forPop.setKAddition(0.5);
                    scoreIS_forPop.setKDeletion(0.5);
                    scoreIS_forPop.setKReorientation(0.5);
                    IsFges tmp = new IsFges(scoreIS_forPop, popScoreOnBs);
                    tmp.setPopulationGraph(graphP);
                    double popMl = tmp.scoreDag(graphP);

                    String caseName = Objects.toString(dataWithNames.getObject(i, nameIdx));
                    Path outGraph = outDir.resolve(dataName + "_" + caseName + "_kappa_" + kappa + "_" + b + ".txt");

                    // Learn IS graph
                    Graph graphIS = learnInstanceSpecific(bs, test, kappa, graphP, samplePrior);

                    // Score learned IS graph under the same objective
                    IsFges tmp2 = new IsFges(scoreIS_forPop, popScoreOnBs);
                    tmp2.setPopulationGraph(graphP);
                    double isMl = tmp2.scoreDag(graphIS);
                    out.println(caseName + "," + popMl + "," + isMl);

                    saveGraph(graphIS, outGraph);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to write CSV log: " + csv, e);
        }
    }

    // ================== Learning helpers ==================

    private static Graph learnPopulationFges(DataSet data, double samplePrior, double structurePrior) {
        BDeuScore score = new BDeuScore(data);
        score.setPriorEquivalentSampleSize(samplePrior);
        score.setStructurePrior(structurePrior);
        Fges fges = new Fges(score);
        fges.setSymmetricFirstStep(true);
        try {
            Graph g = fges.search();
            return GraphUtils.replaceNodes(g, data.getVariables());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Population FGES interrupted", e);
        }
    }

    private static Graph learnInstanceSpecific(DataSet train, DataSet test, double kappa, Graph populationGraph, double samplePrior) {
        BDeuScore popScore = new BDeuScore(train);

        IsBDeuScore2 scoreIS = new IsBDeuScore2(train, test);
        scoreIS.setSamplePrior(samplePrior);
        scoreIS.setKAddition(kappa);
        scoreIS.setKDeletion(kappa);
        scoreIS.setKReorientation(kappa);

        IsFges fges = new IsFges(scoreIS, popScore);
        fges.setPopulationGraph(populationGraph);
        fges.setInitialGraph(populationGraph);

        try {
            Graph g = fges.search();
            return GraphUtils.replaceNodes(g, train.getVariables());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("IS-FGES interrupted", e);
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

    private static void saveGraph(Graph g, Path out) {
        File f = out.toFile();
        GraphSaveLoadUtils.saveGraph(g, f, false);
        System.out.println("Wrote: " + out);
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

    private static DataSet bootstrap(DataSet data, int size) {
        return new BootstrapSampler().sample(data, size);
    }

    private static String required(String[] args, int i, String flag) {
        if (i >= args.length) throw new IllegalArgumentException("Missing value for " + flag);
        return args[i];
    }

    private static void printHelpAndExit() {
        System.out.println("Usage: TestISFGS_TCGA [options]\n" +
                           "  -data <name>   Data CSV base name (default: " + DEFAULT_DATA_NAME + ")\n" +
                           "  -dir <path>    Directory containing CSVs (default: user.dir)\n" +
                           "  -bs <int>      Number of bootstrap replicates per LOO case (default: 1)\n" +
                           "  -h, --help     Show this help");
        System.exit(0);
    }
}
