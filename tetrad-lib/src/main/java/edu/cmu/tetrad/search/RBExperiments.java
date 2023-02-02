package edu.cmu.tetrad.search;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.bayes.DirichletEstimator;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.performance.Comparison;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TextTable;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.ParsingException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

import static java.lang.Math.exp;
import static java.lang.Math.log;

public class RBExperiments {

    private final int depth = 5;
    private String directory;

    private static class MapUtil {
        public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
            List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
                public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                    return (o2.getValue()).compareTo(o1.getValue());
                }
            });

            Map<K, V> result = new LinkedHashMap<>();
            for (Map.Entry<K, V> entry : list) {
                result.put(entry.getKey(), entry.getValue());
            }
            return result;
        }
    }

    private List<Node> getLatents(Graph dag) {
        List<Node> latents = new ArrayList<>();
        for (Node n : dag.getNodes()) {
            if (n.getNodeType() == NodeType.LATENT) {
                latents.add(n);
            }
        }
        return latents;
    }

    public Graph makeSimpleDAG(int numLatentConfounders) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nodes.add(new DiscreteVariable(Integer.toString(i + 1)));
        }

        Graph dag = new EdgeListGraph(nodes);
        dag.addDirectedEdge(nodes.get(0), nodes.get(1));
        dag.addDirectedEdge(nodes.get(0), nodes.get(2));
        dag.addDirectedEdge(nodes.get(1), nodes.get(3));
        dag.addDirectedEdge(nodes.get(2), nodes.get(3));
        dag.addDirectedEdge(nodes.get(2), nodes.get(4));
        return dag;
    }

    private BayesIm initializeIM(BayesIm im) {
        int node = 0;
        im.setProbability(node, 0, 0, 0.8);
        im.setProbability(node, 0, 1, 0.2);


        node = 1;
        im.setProbability(node, 0, 0, 0.9);
        im.setProbability(node, 0, 1, 0.1);
        im.setProbability(node, 1, 0, 0.3);
        im.setProbability(node, 1, 1, 0.7);

        node = 2;
        im.setProbability(node, 0, 0, 0.8);
        im.setProbability(node, 0, 1, 0.2);
        im.setProbability(node, 1, 0, 0.4);
        im.setProbability(node, 1, 1, 0.6);

        node = 3;
        im.setProbability(node, 0, 0, 0.9);
        im.setProbability(node, 0, 1, 0.1);
        im.setProbability(node, 1, 0, 0.7);
        im.setProbability(node, 1, 1, 0.3);
        im.setProbability(node, 2, 0, 0.6);
        im.setProbability(node, 2, 1, 0.4);
        im.setProbability(node, 3, 0, 0.2);
        im.setProbability(node, 3, 1, 0.8);

        node = 4;
        im.setProbability(node, 0, 0, 0.9);
        im.setProbability(node, 0, 1, 0.1);
        im.setProbability(node, 1, 0, 0.6);
        im.setProbability(node, 1, 1, 0.4);

        return im;
    }

    public static void main(String[] args) throws IOException {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        // read and process input arguments
        double alpha = 0.05, lower = 0.3, upper = 0.7;
        int numCases = 100, numModels = 5, numBootstrapSamples = 10, round = 0;
        String modelName = "Alarm", filePath = "/Users/chw20/Documents/DBMI/bsc-results",
                dataPath = System.getProperty("user.dir");
        boolean threshold1 = false, threshold2 = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-c":
                    numCases = Integer.parseInt(args[i + 1]);
                    break;
                case "-lv":
                    Double.parseDouble(args[i + 1]);
                    break;
                case "-bs":
                    numBootstrapSamples = Integer.parseInt(args[i + 1]);
                    break;
                case "-alpha":
                    alpha = Double.parseDouble(args[i + 1]);
                    break;
                case "-m":
                    numModels = Integer.parseInt(args[i + 1]);
                    break;
                case "-net":
                    modelName = args[i + 1];
                    break;
                case "-t1":
                    threshold1 = Boolean.parseBoolean(args[i + 1]);
                    break;
                case "-t2":
                    threshold2 = Boolean.parseBoolean(args[i + 1]);
                    break;
                case "-low":
                    lower = Double.parseDouble(args[i + 1]);
                    break;
                case "-up":
                    upper = Double.parseDouble(args[i + 1]);
                    break;
                case "-out":
                    filePath = args[i + 1];
                    break;
                case "-data":
                    dataPath = args[i + 1];
                    break;
                case "-i":
                    round = Integer.parseInt(args[i + 1]);
                    break;
            }
        }

        // create an instance of class and run an experiment on it
        RBExperiments DFC = new RBExperiments();
        DFC.directory = dataPath;
        double[] lv = {0.0};//, 0.1, 0.2};
        int[] cases = {200};//, 2000};
        for (int numCase : cases) {
            for (double numLatentConfounder : lv) {
                for (int i = 0; i < 10; i++) {
                    DFC.experiment(modelName, numCase, numModels, numBootstrapSamples, alpha, numLatentConfounder, threshold1,
                            threshold2, lower, upper, filePath, i);
                }
            }
        }
    }

    public void experiment(String modelName, int numCases, int numModels, int numBootstrapSamples, double alpha,
                           double numLatentConfounders, boolean threshold1, boolean threshold2, double lower, double upper,
                           String filePath, int round) {
        // 32827167123L
        final Long seed = 878376L;
        RandomUtil.getInstance().setSeed(seed);
        PrintStream out;

        // get the Bayesian network (graph and parameters) of the given model
        BayesIm im = getBayesIM(modelName);
        BayesPm pm = im.getBayesPm();
        Graph dag = pm.getDag();

        // set the "numLatentConfounders" percentage of variables to be latent
        int numVars = im.getNumNodes();
        int LV = (int) Math.floor(numLatentConfounders * numVars);
        RandomGraph.fixLatents4(LV, dag);
        System.out.println("Variables set to be latent:" + getLatents(dag));

        // create output directory and files
        filePath = filePath + "/" + modelName + "-Vars" + dag.getNumNodes() + "-Edges" + dag.getNumEdges() + "-H"
                + numLatentConfounders + "-Cases" + numCases + "-numModels" + numModels + "-BS" + numBootstrapSamples;
        try {
            File dir = new File(filePath);
            dir.mkdirs();
            File file = new File(dir,
                    "Results-" + modelName + "-Vars" + dag.getNumNodes() + "-Edges" + dag.getNumEdges() + "-H"
                            + numLatentConfounders + "-Cases" + numCases + "-numModels" + numModels + "-BS"
                            + numBootstrapSamples + "-" + round + ".txt");
            if (!file.exists() || file.length() == 0) {
                out = new PrintStream(new FileOutputStream(file));
            } else {
                return;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // simulate data from instantiated model
        DataSet fullData = im.simulateData(numCases, round * 1000000 + 71512, true);
        fullData = refineData(fullData);
        DataSet data = DataUtils.restrictToMeasured(fullData);

        // get the true underlying PAG
//        DagToPag dagToPag = new DagToPag(dag);
//        dagToPag.setCompleteRuleSetUsed(false);
//        Graph PAG_True = dagToPag.convert();

        Graph PAG_True = SearchGraphUtils.dagToPag(dag);

        PAG_True = GraphUtils.replaceNodes(PAG_True, data.getVariables());

        // run RFCI to get a PAG using chi-squared test
        long start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        Graph rfciPag = runPagCs(data, alpha);
        long RfciTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - start;
        System.out.println("RFCI done!");

        // run RFCI-BSC (RB) search using BSC test and obtain constraints that
        // are queried during the search
        List<Graph> bscPags = new ArrayList<>();
        start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        IndTestProbabilistic testBSC = runRB(data, bscPags, numModels, threshold1);
        long BscRfciTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - start;
        Map<IndependenceFact, Double> H = testBSC.getH();
        //		out.println("H Size:" + H.size());
        System.out.println("RB (RFCI-BSC) done!");
        //
        // create empirical data for constraints
        start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        DataSet depData = createDepDataFiltering(H, data, numBootstrapSamples, threshold2, lower, upper);
        out.println("DepData(row,col):" + depData.getNumRows() + "," + depData.getNumColumns());
        System.out.println("Dep data creation done!");

        // learn structure of constraints using empirical data
        Graph depCPDAG = runFGS(depData);
        Graph estDepBN = SearchGraphUtils.dagFromCPDAG(depCPDAG);
        System.out.println("estDepBN: " + estDepBN.getEdges());
        out.println("DepGraph(nodes,edges):" + estDepBN.getNumNodes() + "," + estDepBN.getNumEdges());
        System.out.println("Dependency graph done!");

        // estimate parameters of the graph learned for constraints
        BayesPm pmHat = new BayesPm(estDepBN, 2, 2);
        DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(pmHat, 0.5);
        BayesIm imHat = DirichletEstimator.estimate(prior, depData);
        Long BscdTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - start;
        System.out.println("Dependency BN_Param done");

        // compute scores of graphs that are output by RB search using BSC-I and
        // BSC-D methods
        start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        allScores lnProbs = getLnProbsAll(bscPags, H, data, imHat, estDepBN);
        Long mutualTime = (ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - start) / 2;

        // normalize the scores
        start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        Map<Graph, Double> normalizedDep = normalProbs(lnProbs.LnBSCD);
        Long dTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - start;

        start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        Map<Graph, Double> normalizedInd = normalProbs(lnProbs.LnBSCI);
        Long iTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - start;

        // get the most probable PAG using each scoring method
        normalizedDep = MapUtil.sortByValue(normalizedDep);
        Graph maxBND = normalizedDep.keySet().iterator().next();
//		Graph maxBND = bscPags.get(0);

        normalizedInd = MapUtil.sortByValue(normalizedInd);
        Graph maxBNI = normalizedInd.keySet().iterator().next();
//		Graph maxBNI = bscPags.get(0);

        // summarize and write the results into output files
        out.println("*** RFCI time (sec):" + (RfciTime / 1000));
        summarize(rfciPag, PAG_True, out);

        out.println("\n*** RB-I time (sec):" + BscRfciTime);// + mutualTime + iTime) / 1000));
        summarize(maxBNI, PAG_True, out);
        //
        out.println("\n*** RB-D time (sec):" + BscRfciTime);// + BscdTime + mutualTime + dTime) / 1000));
        summarize(maxBND, PAG_True, out);
        //
        out.println("P(maxBNI): \n" + 1.0);//normalizedInd.get(maxBNI));
        out.println("P(maxBND): \n" + 1.0);// normalizedDep.get(maxBND));
        out.println("------------------------------------------");
        out.println("PAG_True: \n" + PAG_True);
        out.println("------------------------------------------");
        out.println("Rfci: \n" + rfciPag);
        out.println("------------------------------------------");
        out.println("RB-I: \n" + maxBNI);
        out.println("------------------------------------------");
        out.println("RB-D: \n" + maxBND);

        out.close();

    }

    private DataSet refineData(DataSet fullData) {
        for (int c = 0; c < fullData.getNumColumns(); c++) {
            for (int r = 0; r < fullData.getNumRows(); r++) {
                if (fullData.getInt(r, c) < 0) {
                    fullData.setInt(r, c, 0);
                }
            }
        }

        return fullData;
    }

    private BayesIm getBayesIM(String type) {
        if ("Alarm".equals(type)) {
            return loadBayesIm("Alarm.xdsl", true);
        } else if ("Hailfinder".equals(type)) {
            return loadBayesIm("Hailfinder.xdsl", false);
        } else if ("Hepar".equals(type)) {
            return loadBayesIm("Hepar2.xdsl", true);
        } else if ("Win95".equals(type)) {
            return loadBayesIm("win95pts.xdsl", false);
        } else if ("Barley".equals(type)) {
            return loadBayesIm("barley.xdsl", false);
        }

        throw new IllegalArgumentException("Not a recogized Bayes IM type.");
    }

    private void summarize(Graph graph, Graph trueGraph, PrintStream out) {
        out.flush();
        ArrayList<Comparison.TableColumn> tableColumns = new ArrayList<>();

        tableColumns.add(Comparison.TableColumn.AhdCor);
        tableColumns.add(Comparison.TableColumn.AhdFp);
        tableColumns.add(Comparison.TableColumn.AhdFn);
        tableColumns.add(Comparison.TableColumn.AhdPrec);
        tableColumns.add(Comparison.TableColumn.AhdRec);

        tableColumns.add(Comparison.TableColumn.AdjCor);
        tableColumns.add(Comparison.TableColumn.AdjFp);
        tableColumns.add(Comparison.TableColumn.AdjFn);
        tableColumns.add(Comparison.TableColumn.AdjPrec);
        tableColumns.add(Comparison.TableColumn.AdjRec);

        tableColumns.add(Comparison.TableColumn.SHD);

        GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison(trueGraph, graph);

        List<Node> variables = new ArrayList<>();
        for (Comparison.TableColumn column : tableColumns) {
            variables.add(new ContinuousVariable(column.toString()));
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(0, variables.size()), variables);
        dataSet.setNumberFormat(new DecimalFormat("0"));

        int newRow = dataSet.getNumRows();

        if (tableColumns.contains(Comparison.TableColumn.AdjCor)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AdjCor), comparison.getAdjCor());
        }

        if (tableColumns.contains(Comparison.TableColumn.AdjFn)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AdjFn), comparison.getAdjFn());
        }

        if (tableColumns.contains(Comparison.TableColumn.AdjFp)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AdjFp), comparison.getAdjFp());
        }

        if (tableColumns.contains(Comparison.TableColumn.AdjPrec)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AdjPrec), comparison.getAdjPrec());
        }

        if (tableColumns.contains(Comparison.TableColumn.AdjRec)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AdjRec), comparison.getAdjRec());
        }

        if (tableColumns.contains(Comparison.TableColumn.AhdCor)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AhdCor), comparison.getAhdCor());
        }

        if (tableColumns.contains(Comparison.TableColumn.AhdFn)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AhdFn), comparison.getAhdFn());
        }

        if (tableColumns.contains(Comparison.TableColumn.AhdFp)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AhdFp), comparison.getAhdFp());
        }

        if (tableColumns.contains(Comparison.TableColumn.AhdPrec)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AhdPrec), comparison.getAhdPrec());
        }

        if (tableColumns.contains(Comparison.TableColumn.AhdRec)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.AhdRec), comparison.getAhdRec());
        }

        if (tableColumns.contains(Comparison.TableColumn.SHD)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(Comparison.TableColumn.SHD), comparison.getShd());
        }

        int[] cols = new int[tableColumns.size()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        //		out.println(getTextTable(dataSet, cols, new DecimalFormat("0.00")));
        out.println(MisclassificationUtils.edgeMisclassifications(graph, trueGraph));
        int SHDAdj = comparison.getEdgesAdded().size() + comparison.getEdgesRemoved().size();
        int diffEdgePoint = comparison.getShd() - SHDAdj * 2;
        out.println("# missing/extra edges: " + SHDAdj);
        out.println("# different edge points: " + diffEdgePoint);

        out.println("-------------------------------");

    }

    private double[] printCorrectArrows(Graph outGraph, Graph truePag, PrintStream out) {
        int correctArrows = 0;
        int totalEstimatedArrows = 0;
        int totalTrueArrows = 0;

        double[] stats = new double[5];

        for (Edge edge : outGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            Edge edge1 = truePag.getEdge(x, y);

            if (ex == Endpoint.ARROW) {
                if (edge1 != null && edge1.getProximalEndpoint(x) == Endpoint.ARROW) {
                    correctArrows++;
                }
                totalEstimatedArrows++;
            }

            if (ey == Endpoint.ARROW) {
                if (edge1 != null && edge1.getProximalEndpoint(y) == Endpoint.ARROW) {
                    correctArrows++;
                }

                totalEstimatedArrows++;
            }
        }

        for (Edge edge : truePag.getEdges()) {
            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            if (ex == Endpoint.ARROW) {
                totalTrueArrows++;
            }

            if (ey == Endpoint.ARROW) {
                totalTrueArrows++;
            }
        }

        out.println();
        out.println("# correct arrows: " + correctArrows);
        out.println("# total estimated arrows: " + totalEstimatedArrows);
        out.println("# total true arrows: " + totalTrueArrows);

        out.println();
        NumberFormat nf = new DecimalFormat("0.00");
        double precision = correctArrows / (double) totalEstimatedArrows;
        out.println("Arrow precision: " + nf.format(precision));
        double recall = correctArrows / (double) totalTrueArrows;
        out.println("Arrow recall: " + nf.format(recall));

        stats[0] = correctArrows;
        stats[1] = totalEstimatedArrows;
        stats[2] = totalTrueArrows;
        stats[3] = precision;
        stats[4] = recall;

        return stats;
    }

    private double[] printCorrectTails(Graph outGraph, Graph truePag, PrintStream out) {
        int correctTails = 0;
        int totalEstimatedTails = 0;
        int totalTrueTails = 0;

        double[] stats = new double[5];

        for (Edge edge : outGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            Edge edge1 = truePag.getEdge(x, y);

            if (ex == Endpoint.TAIL) {
                if (edge1 != null && edge1.getProximalEndpoint(x) == Endpoint.TAIL) {
                    correctTails++;
                }

                totalEstimatedTails++;
            }

            if (ey == Endpoint.TAIL) {
                if (edge1 != null && edge1.getProximalEndpoint(y) == Endpoint.TAIL) {
                    correctTails++;
                }

                totalEstimatedTails++;
            }
        }

        for (Edge edge : truePag.getEdges()) {
            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            if (ex == Endpoint.TAIL) {
                totalTrueTails++;
            }

            if (ey == Endpoint.TAIL) {
                totalTrueTails++;
            }
        }

        out.println();
        out.println("# correct tails: " + correctTails);
        out.println("# total estimated tails: " + totalEstimatedTails);
        out.println("# total true tails: " + totalTrueTails);

        out.println();
        NumberFormat nf = new DecimalFormat("0.00");
        double precision = correctTails / (double) totalEstimatedTails;
        out.println("Tail precision: " + nf.format(precision));
        double recall = correctTails / (double) totalTrueTails;
        out.println("Tail recall: " + nf.format(recall));

        stats[0] = correctTails;
        stats[1] = totalEstimatedTails;
        stats[2] = totalTrueTails;
        stats[3] = precision;
        stats[4] = recall;

        return stats;
    }

    private TextTable getTextTable(DataSet dataSet, int[] columns, NumberFormat nf) {
        TextTable table = new TextTable(dataSet.getNumRows() + 2, columns.length + 1);

        table.setToken(0, 0, "Run #");

        for (int j = 0; j < columns.length; j++) {
            table.setToken(0, j + 1, dataSet.getVariable(columns[j]).getName());
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            table.setToken(i + 1, 0, Integer.toString(i + 1));
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < columns.length; j++) {
                table.setToken(i + 1, j + 1, nf.format(dataSet.getDouble(i, columns[j])));
            }
        }

        NumberFormat nf2 = new DecimalFormat("0.00");

        for (int j = 0; j < columns.length; j++) {
            double sum = 0.0;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                sum += dataSet.getDouble(i, columns[j]);
            }

            double avg = sum / dataSet.getNumRows();

            table.setToken(dataSet.getNumRows() + 2 - 1, j + 1, nf2.format(avg));
        }

        table.setToken(dataSet.getNumRows() + 2 - 1, 0, "Avg");

        return table;
    }

    private DataSet createDepDataFiltering(Map<IndependenceFact, Double> H, DataSet data, int numBootstrapSamples,
                                           boolean threshold, double lower, double upper) {
        List<Node> vars = new ArrayList<>();
        Map<IndependenceFact, Double> HCopy = new HashMap<>();
        for (IndependenceFact f : H.keySet()) {
            if (H.get(f) > lower && H.get(f) < upper) {
                HCopy.put(f, H.get(f));
                DiscreteVariable var = new DiscreteVariable(f.toString());
                vars.add(var);
            }
        }

        DataSet depData = new BoxDataSet(new DoubleDataBox(numBootstrapSamples, vars.size()), vars);
        System.out.println("\nDep data rows: " + depData.getNumRows() + ", columns: " + depData.getNumColumns());
        System.out.println("HCopy size: " + HCopy.size());

        for (int b = 0; b < numBootstrapSamples; b++) {
            DataSet bsData = DataUtils.getBootstrapSample(data, data.getNumRows());
            IndTestProbabilistic bsTest = new IndTestProbabilistic(bsData);
            bsTest.setThreshold(threshold);
            for (IndependenceFact f : HCopy.keySet()) {
                boolean ind = bsTest.checkIndependence(f.getX(), f.getY(), f.getZ()).independent();
                int value = ind ? 1 : 0;
                depData.setInt(b, depData.getColumn(depData.getVariable(f.toString())), value);
            }
        }
        return depData;
    }

    private Graph runFGS(DataSet data) {
        BDeuScore sd = new BDeuScore(data);
        sd.setSamplePrior(1.0);
        sd.setStructurePrior(1.0);
        Fges fgs = new Fges(sd);
        fgs.setVerbose(false);
        fgs.setFaithfulnessAssumed(true);
        Graph fgsCPDAG = fgs.search();
        fgsCPDAG = GraphUtils.replaceNodes(fgsCPDAG, data.getVariables());
        return fgsCPDAG;
    }

    private allScores getLnProbsAll(List<Graph> pags, Map<IndependenceFact, Double> H, DataSet data, BayesIm im,
                                    Graph dep) {
        // Map<Graph, Double> pagLnBDeu = new HashMap<Graph, Double>();
        Map<Graph, Double> pagLnBSCD = new HashMap<>();
        Map<Graph, Double> pagLnBSCI = new HashMap<>();

        for (Graph pagOrig : pags) {
            if (!pagLnBSCD.containsKey(pagOrig)) {
                double lnInd = getLnProb(pagOrig, H);

                // Filtering
                double lnDep = getLnProbUsingDepFiltering(pagOrig, H, im, dep);
                pagLnBSCD.put(pagOrig, lnDep);
                pagLnBSCI.put(pagOrig, lnInd);
            }
        }

        System.out.println("pags size: " + pags.size());
        System.out.println("unique pags size: " + pagLnBSCD.size());

        return new allScores(pagLnBSCD, pagLnBSCI);
    }

    private static class allScores {
        Map<Graph, Double> LnBSCD;
        Map<Graph, Double> LnBSCI;

        allScores(Map<Graph, Double> LnBSCD, Map<Graph, Double> LnBSCI) {
            this.LnBSCD = LnBSCD;
            this.LnBSCI = LnBSCI;
        }

    }

    private IndTestProbabilistic runRB(DataSet data, List<Graph> pags, int numModels, boolean threshold) {
        IndTestProbabilistic BSCtest = new IndTestProbabilistic(data);

        BSCtest.setThreshold(threshold);
        Rfci BSCrfci = new Rfci(BSCtest);

        BSCrfci.setVerbose(false);
        BSCrfci.setCompleteRuleSetUsed(false);
        BSCrfci.setDepth(this.depth);

        for (int i = 0; i < numModels; i++) {
            Graph BSCPag = BSCrfci.search();
            BSCPag = GraphUtils.replaceNodes(BSCPag, data.getVariables());
            pags.add(BSCPag);

        }
        return BSCtest;
    }

    private Graph runPagCs(DataSet data, double alpha) {
        IndTestChiSquare test = new IndTestChiSquare(data, alpha);

        Rfci fci1 = new Rfci(test);
        fci1.setDepth(this.depth);
        fci1.setVerbose(false);
        fci1.setCompleteRuleSetUsed(false);
        Graph PAG_CS = fci1.search();
        PAG_CS = GraphUtils.replaceNodes(PAG_CS, data.getVariables());
        return PAG_CS;
    }

    private double getLnProbUsingDepFiltering(Graph pag, Map<IndependenceFact, Double> H, BayesIm im, Graph dep) {
        double lnQ = 0;

        for (IndependenceFact fact : H.keySet()) {
            BCInference.OP op;
            double p = 0.0;

            if (pag.paths().isDSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
                op = BCInference.OP.independent;
            } else {
                op = BCInference.OP.dependent;
            }

            if (im.getNode(fact.toString()) != null) {
                Node node = im.getNode(fact.toString());

                int[] parents = im.getParents(im.getNodeIndex(node));

                if (parents.length > 0) {

                    int[] parentValues = new int[parents.length];

                    for (int parentIndex = 0; parentIndex < parentValues.length; parentIndex++) {
                        String parentName = im.getNode(parents[parentIndex]).getName();
                        String[] splitParent = parentName.split(Pattern.quote("_||_"));
                        Node X = pag.getNode(splitParent[0].trim());

                        String[] splitParent2 = splitParent[1].trim().split(Pattern.quote("|"));
                        Node Y = pag.getNode(splitParent2[0].trim());

                        List<Node> Z = new ArrayList<>();
                        if (splitParent2.length > 1) {
                            String[] splitParent3 = splitParent2[1].trim().split(Pattern.quote(","));
                            for (String s : splitParent3) {
                                Z.add(pag.getNode(s.trim()));
                            }
                        }
                        IndependenceFact parentFact = new IndependenceFact(X, Y, Z);
                        if (pag.paths().isDSeparatedFrom(parentFact.getX(), parentFact.getY(), parentFact.getZ())) {
                            parentValues[parentIndex] = 1;
                        } else {
                            parentValues[parentIndex] = 0;
                        }
                    }

                    int rowIndex = im.getRowIndex(im.getNodeIndex(node), parentValues);
                    p = im.getProbability(im.getNodeIndex(node), rowIndex, 1);

                } else {
                    p = im.getProbability(im.getNodeIndex(node), 0, 1);
                }
                if (op == BCInference.OP.dependent) {
                    p = 1.0 - p;
                }

                if (p < -0.0001 || p > 1.0001 || Double.isNaN(p) || Double.isInfinite(p)) {
                    throw new IllegalArgumentException("p illegally equals " + p);
                }

                double v = lnQ + log(p);

                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    continue;
                }

                lnQ = v;
            } else {
                p = H.get(fact);

                if (p < -0.0001 || p > 1.0001 || Double.isNaN(p) || Double.isInfinite(p)) {
                    throw new IllegalArgumentException("p illegally equals " + p);
                }

                if (op == BCInference.OP.dependent) {
                    p = 1.0 - p;
                }

                double v = lnQ + log(p);

                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    continue;
                }

                lnQ = v;
            }
        }

        return lnQ;
    }

    private double getLnProb(Graph pag, Map<IndependenceFact, Double> H) {
        double lnQ = 0;
        for (IndependenceFact fact : H.keySet()) {
            BCInference.OP op;

            if (pag.paths().isDSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
                op = BCInference.OP.independent;
            } else {
                op = BCInference.OP.dependent;
            }

            double p = H.get(fact);

            if (p < -0.0001 || p > 1.0001 || Double.isNaN(p) || Double.isInfinite(p)) {
                throw new IllegalArgumentException("p illegally equals " + p);
            }

            if (op == BCInference.OP.dependent) {
                p = 1.0 - p;
            }

            double v = lnQ + log(p);

            if (Double.isNaN(v) || Double.isInfinite(v)) {
                continue;
            }

            lnQ = v;
        }
        return lnQ;
    }

    private Map<Graph, Double> normalProbs(Map<Graph, Double> pagLnProbs) {
        double lnQTotal = lnQTotal(pagLnProbs);
        Map<Graph, Double> normalized = new HashMap<>();
        for (Graph pag : pagLnProbs.keySet()) {
            double lnQ = pagLnProbs.get(pag);
            double normalizedlnQ = lnQ - lnQTotal;
            normalized.put(pag, exp(normalizedlnQ));
        }
        return normalized;
    }

    private BayesIm loadBayesIm(String filename, boolean useDisplayNames) {
        try {
            Builder builder = new Builder();
            File dir = new File(this.directory + "/xdsl");
            File file = new File(dir, filename);
            Document document = builder.build(file);
            XdslXmlParser parser = new XdslXmlParser();
            parser.setUseDisplayNames(useDisplayNames);
            return parser.getBayesIm(document.getRootElement());
        } catch (ParsingException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected double lnXplusY(double lnX, double lnY) {
        double lnYminusLnX;
        double temp;

        if (lnY > lnX) {
            temp = lnX;
            lnX = lnY;
            lnY = temp;
        }

        lnYminusLnX = lnY - lnX;

        if (lnYminusLnX < RBExperiments.MININUM_EXPONENT) {
            return lnX;
        } else {
            double w = Math.log1p(exp(lnYminusLnX));
            return w + lnX;
        }
    }

    private double lnQTotal(Map<Graph, Double> pagLnProb) {
        Set<Graph> pags = pagLnProb.keySet();
        Iterator<Graph> iter = pags.iterator();
        double lnQTotal = pagLnProb.get(iter.next());

        while (iter.hasNext()) {
            Graph pag = iter.next();
            double lnQ = pagLnProb.get(pag);
            lnQTotal = lnXplusY(lnQTotal, lnQ);
        }

        return lnQTotal;
    }

    private static final int MININUM_EXPONENT = -1022;

    public DataSet bootStrapSampling(DataSet data, int numBootstrapSamples, int bootsrapSampleSize) {

        return DataUtils.getBootstrapSample(data, bootsrapSampleSize);
    }

}
