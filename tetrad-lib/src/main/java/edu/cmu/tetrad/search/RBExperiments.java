package edu.cmu.tetrad.search;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.bayes.DirichletEstimator;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.performance.Comparison;
import edu.cmu.tetrad.performance.Comparison.TableColumn;
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
        public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(final Map<K, V> map) {
            final List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
                public int compare(final Map.Entry<K, V> o1, final Map.Entry<K, V> o2) {
                    return (o2.getValue()).compareTo(o1.getValue());
                }
            });

            final Map<K, V> result = new LinkedHashMap<K, V>();
            for (final Map.Entry<K, V> entry : list) {
                result.put(entry.getKey(), entry.getValue());
            }
            return result;
        }
    }

    private List<Node> getLatents(final Graph dag) {
        final List<Node> latents = new ArrayList<>();
        for (final Node n : dag.getNodes()) {
            if (n.getNodeType() == NodeType.LATENT) {
                latents.add(n);
            }
        }
        return latents;
    }

    public Graph makeSimpleDAG(final int numLatentConfounders) {
        final List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nodes.add(new DiscreteVariable(Integer.toString(i + 1)));
        }

        final Graph dag = new EdgeListGraph(nodes);
        dag.addDirectedEdge(nodes.get(0), nodes.get(1));
        dag.addDirectedEdge(nodes.get(0), nodes.get(2));
        dag.addDirectedEdge(nodes.get(1), nodes.get(3));
        dag.addDirectedEdge(nodes.get(2), nodes.get(3));
        dag.addDirectedEdge(nodes.get(2), nodes.get(4));
        return dag;
    }

    private BayesIm initializeIM(final BayesIm im) {
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

//	private static DataSet readInDataSet(Path dataFile) {
//		DataSet dataSet = null;
//		DataReader dataReader = new VerticalTabularDiscreteDataReader(dataFile, ',');
//
//		try {
//			dataSet = dataReader.readInData();
//		} catch (IOException exception) {
//			String errMsg = String.format("Failed when reading data file '%s'.", dataFile.getFileName());
//			System.err.println(errMsg);
//			System.exit(-128);
//		}
//
//		return dataSet;
//	}


    public static void main(final String[] args) throws IOException {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        // read and process input arguments
        double alpha = 0.05, numLatentConfounders = 0, lower = 0.3, upper = 0.7;
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
                    numLatentConfounders = Double.parseDouble(args[i + 1]);
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
        final RBExperiments DFC = new RBExperiments();
        DFC.directory = dataPath;
        final double[] lv = new double[]{0.0};//, 0.1, 0.2};
        final int[] cases = new int[]{200};//, 2000};
        for (final int numCase : cases) {
            for (final double numLatentConfounder : lv) {
                for (int i = 0; i < 10; i++) {
                    DFC.experiment(modelName, numCase, numModels, numBootstrapSamples, alpha, numLatentConfounder, threshold1,
                            threshold2, lower, upper, filePath, i);
                }
            }
        }
    }

    public void experiment(final String modelName, final int numCases, final int numModels, final int numBootstrapSamples, final double alpha,
                           final double numLatentConfounders, final boolean threshold1, final boolean threshold2, final double lower, final double upper,
                           String filePath, final int round) {
        // 32827167123L
        final Long seed = 878376L;
        RandomUtil.getInstance().setSeed(seed);
        final PrintStream out;

        // get the Bayesian network (graph and parameters) of the given model
        final BayesIm im = getBayesIM(modelName);
        final BayesPm pm = im.getBayesPm();
        final Graph dag = pm.getDag();

        // set the "numLatentConfounders" percentage of variables to be latent
        final int numVars = im.getNumNodes();
        final int LV = (int) Math.floor(numLatentConfounders * numVars);
        GraphUtils.fixLatents4(LV, dag);
        System.out.println("Variables set to be latent:" + getLatents(dag));

        // create output directory and files
        filePath = filePath + "/" + modelName + "-Vars" + dag.getNumNodes() + "-Edges" + dag.getNumEdges() + "-H"
                + numLatentConfounders + "-Cases" + numCases + "-numModels" + numModels + "-BS" + numBootstrapSamples;
        try {
            final File dir = new File(filePath);
            dir.mkdirs();
            final File file = new File(dir,
                    "Results-" + modelName + "-Vars" + dag.getNumNodes() + "-Edges" + dag.getNumEdges() + "-H"
                            + numLatentConfounders + "-Cases" + numCases + "-numModels" + numModels + "-BS"
                            + numBootstrapSamples + "-" + round + ".txt");
            if (!file.exists() || file.length() == 0) {
                out = new PrintStream(new FileOutputStream(file));
            } else {
                return;
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        // simulate data from instantiated model
        DataSet fullData = im.simulateData(numCases, round * 1000000 + 71512, true);
        fullData = refineData(fullData);
        final DataSet data = DataUtils.restrictToMeasured(fullData);

        // get the true underlying PAG
        final DagToPag2 dagToPag = new DagToPag2(dag);
        dagToPag.setCompleteRuleSetUsed(false);
        Graph PAG_True = dagToPag.convert();
        PAG_True = GraphUtils.replaceNodes(PAG_True, data.getVariables());

        // run RFCI to get a PAG using chi-squared test
        long start = System.currentTimeMillis();
        final Graph rfciPag = runPagCs(data, alpha);
        final long RfciTime = System.currentTimeMillis() - start;
        System.out.println("RFCI done!");

        // run RFCI-BSC (RB) search using BSC test and obtain constraints that
        // are queried during the search
        final List<Graph> bscPags = new ArrayList<Graph>();
        start = System.currentTimeMillis();
        final IndTestProbabilistic testBSC = runRB(data, bscPags, numModels, threshold1);
        final long BscRfciTime = System.currentTimeMillis() - start;
        final Map<IndependenceFact, Double> H = testBSC.getH();
        //		out.println("H Size:" + H.size());
        System.out.println("RB (RFCI-BSC) done!");
        //
        // create empirical data for constraints
        start = System.currentTimeMillis();
        final DataSet depData = createDepDataFiltering(H, data, numBootstrapSamples, threshold2, lower, upper);
        out.println("DepData(row,col):" + depData.getNumRows() + "," + depData.getNumColumns());
        System.out.println("Dep data creation done!");

        // learn structure of constraints using empirical data
        final Graph depCPDAG = runFGS(depData);
        final Graph estDepBN = SearchGraphUtils.dagFromCPDAG(depCPDAG);
        System.out.println("estDepBN: " + estDepBN.getEdges());
        out.println("DepGraph(nodes,edges):" + estDepBN.getNumNodes() + "," + estDepBN.getNumEdges());
        System.out.println("Dependency graph done!");

        // estimate parameters of the graph learned for constraints
        final BayesPm pmHat = new BayesPm(estDepBN, 2, 2);
        final DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(pmHat, 0.5);
        final BayesIm imHat = DirichletEstimator.estimate(prior, depData);
        final Long BscdTime = System.currentTimeMillis() - start;
        System.out.println("Dependency BN_Param done");

        // compute scores of graphs that are output by RB search using BSC-I and
        // BSC-D methods
        start = System.currentTimeMillis();
        final allScores lnProbs = getLnProbsAll(bscPags, H, data, imHat, estDepBN);
        final Long mutualTime = (System.currentTimeMillis() - start) / 2;

        // normalize the scores
        start = System.currentTimeMillis();
        Map<Graph, Double> normalizedDep = normalProbs(lnProbs.LnBSCD);
        final Long dTime = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        Map<Graph, Double> normalizedInd = normalProbs(lnProbs.LnBSCI);
        final Long iTime = System.currentTimeMillis() - start;

        // get the most probable PAG using each scoring method
        normalizedDep = MapUtil.sortByValue(normalizedDep);
        final Graph maxBND = normalizedDep.keySet().iterator().next();
//		Graph maxBND = bscPags.get(0);

        normalizedInd = MapUtil.sortByValue(normalizedInd);
        final Graph maxBNI = normalizedInd.keySet().iterator().next();
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
        //		out.println("------------------------------------------");
        //		out.println(normalizedInd.values());
        //		out.println("------------------------------------------");
        //		out.println(normalizedDep.values());
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

	/*public void experiment2(String modelName, int numCases, int numModels, int numBootstrapSamples, double alpha,
			double numLatentConfounders, boolean threshold1, boolean threshold2, double lower, double upper,
			String filePath, int round) {
		// 32827167123L
		modelName = "MCBN1";
		Long seed = 32827167123L;
		RandomUtil.getInstance().setSeed(seed);
		PrintStream out;

		// Path to data file

		// create a Bayesian network (dag) and its parameters
		Graph dag = makeSimpleDAG(0);
		BayesPm pm = new BayesPm(dag, 2, 2);
		BayesIm im = new MlBayesIm(pm, MlBayesIm.MANUAL);
		im = initializeIM(im);
		System.out.println("MCBN1: " + dag);


		// set the "numLatentConfounders" percentage of variables to be latent
		int numVars = im.getNumNodes();
		int LV = (int) Math.floor(numLatentConfounders * numVars);
		//		GraphUtils.fixLatents4(LV, dag);
		System.out.println("Variables set to be latent:" +getLatents(dag));
		Path dataFile = Paths.get("/Users/fattanehjabbari/Downloads/", "mcbn1.txt");
		//		DataSet fullData = readInDataSet(dataFile);
		DataSet fullData = im.simulateData(numCases, round * 1000000 + 71512, true);
		DataSet data = DataUtils.restrictToMeasured(fullData);

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

		// get the true underlying PAG
		final DagToPag dagToPag = new DagToPag(dag);
		dagToPag.setCompleteRuleSetUsed(false);
		Graph PAG_True = dagToPag.convert();
		PAG_True = GraphUtils.replaceNodes(PAG_True, data.getVariables());

		// run RFCI to get a PAG using chi-squared test
		long start = System.currentTimeMillis();
		Graph rfciPag = runPagCs(data, alpha);
		long RfciTime = System.currentTimeMillis() - start;
		System.out.println("RFCI done!");

		// run RFCI-BSC (RB) search using BSC test and obtain constraints that
		// are queried during the search
		List<Graph> bscPags = new ArrayList<Graph>();
		start = System.currentTimeMillis();
		IndTestProbabilistic testBSC = runRB(data, bscPags, numModels, threshold1);
		long BscRfciTime = System.currentTimeMillis() - start;
		Map<IndependenceFact, Double> H = testBSC.getH();
		//		out.println("H Size:" + H.size());
		System.out.println("RB (RFCI-BSC) done!");

		// create empirical data for constraints
		start = System.currentTimeMillis();
		DataSet depData = createDepDataFiltering(H, data, numBootstrapSamples, threshold2, lower, upper);
		//		out.println("DepData(row,col):" + depData.getNumRows() + "," + depData.getNumColumns());
		System.out.println("Dep data creation done!");

		// learn structure of constraints using empirical data => constraint meta data
		Graph depPattern = runFGS(depData);
		Graph estDepBN = SearchGraphUtils.dagFromPattern(depPattern);
		System.out.println("estDepBN: " + estDepBN.getEdges());
		//		out.println("DepGraph(nodes,edges):" + estDepBN.getNumNodes() + "," + estDepBN.getNumEdges());
		System.out.println("Dependency graph done!");

		// estimate parameters of the graph learned for constraints
		BayesPm pmHat = new BayesPm(estDepBN, 2, 2);
		DirichletBayesIm prior = DirichletBayesIm.symmetricDirichletIm(pmHat, 0.5);
		BayesIm imHat = DirichletEstimator.estimate(prior, depData);
		Long BscdTime = System.currentTimeMillis() - start;
		System.out.println("Dependency BN_Param done");

		// compute scores of graphs that are output by RB search using BSC-I and
		// BSC-D methods
		start = System.currentTimeMillis();
		allScores lnProbs = getLnProbsAll(bscPags, H, data, imHat, estDepBN);
		Long mutualTime = (System.currentTimeMillis() - start) / 2;

		// normalize the scores
		start = System.currentTimeMillis();
		Map<Graph, Double> normalizedDep = normalProbs(lnProbs.LnBSCD);
		Long dTime = System.currentTimeMillis() - start;

		start = System.currentTimeMillis();
		Map<Graph, Double> normalizedInd = normalProbs(lnProbs.LnBSCI);
		Long iTime = System.currentTimeMillis() - start;

		// get the most probable PAG using each scoring method
		normalizedDep = MapUtil.sortByValue(normalizedDep);
		Graph maxBND = normalizedDep.keySet().iterator().next();

		normalizedInd = MapUtil.sortByValue(normalizedInd);
		Graph maxBNI = normalizedInd.keySet().iterator().next();

		// summarize and write the results into output files
		out.println("*** RFCI time (sec):" + (RfciTime / 1000));
		summarize(rfciPag, PAG_True, out);

		out.println("\n*** RB-I time (sec):" + ((BscRfciTime + mutualTime + iTime) / 1000));
		summarize(maxBNI, PAG_True, out);

		out.println("\n*** RB-D time (sec):" + ((BscRfciTime + BscdTime + mutualTime + dTime) / 1000));
		summarize(maxBND, PAG_True, out);

		out.println("P(maxBNI): \n" + normalizedInd.get(maxBNI));
		out.println("P(maxBND): \n" + normalizedDep.get(maxBND));
		out.println("------------------------------------------");
		out.println(normalizedInd.values());
		out.println("------------------------------------------");
		out.println(normalizedDep.values());
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
*/

    private DataSet refineData(final DataSet fullData) {
        for (int c = 0; c < fullData.getNumColumns(); c++) {
            for (int r = 0; r < fullData.getNumRows(); r++) {
                if (fullData.getInt(r, c) < 0) {
                    fullData.setInt(r, c, 0);
                }
            }
        }

        return fullData;
    }

    private BayesIm getBayesIM(final String type) {
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

    private void summarize(final Graph graph, final Graph trueGraph, final PrintStream out) {
        out.flush();
        final ArrayList<Comparison.TableColumn> tableColumns = new ArrayList<>();

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

        final GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison(graph, trueGraph);

        final List<Node> variables = new ArrayList<>();
        for (final TableColumn column : tableColumns) {
            variables.add(new ContinuousVariable(column.toString()));
        }

        final DataSet dataSet = new BoxDataSet(new DoubleDataBox(0, variables.size()), variables);
        dataSet.setNumberFormat(new DecimalFormat("0"));

        final int newRow = dataSet.getNumRows();

        if (tableColumns.contains(TableColumn.AdjCor)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjCor), comparison.getAdjCor());
        }

        if (tableColumns.contains(TableColumn.AdjFn)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjFn), comparison.getAdjFn());
        }

        if (tableColumns.contains(TableColumn.AdjFp)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjFp), comparison.getAdjFp());
        }

        if (tableColumns.contains(TableColumn.AdjPrec)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjPrec), comparison.getAdjPrec());
        }

        if (tableColumns.contains(TableColumn.AdjRec)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjRec), comparison.getAdjRec());
        }

        if (tableColumns.contains(TableColumn.AhdCor)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AhdCor), comparison.getAhdCor());
        }

        if (tableColumns.contains(TableColumn.AhdFn)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AhdFn), comparison.getAhdFn());
        }

        if (tableColumns.contains(TableColumn.AhdFp)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AhdFp), comparison.getAhdFp());
        }

        if (tableColumns.contains(TableColumn.AhdPrec)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AhdPrec), comparison.getAhdPrec());
        }

        if (tableColumns.contains(TableColumn.AhdRec)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AhdRec), comparison.getAhdRec());
        }

        if (tableColumns.contains(TableColumn.SHD)) {
            dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.SHD), comparison.getShd());
        }

        final int[] cols = new int[tableColumns.size()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        //		out.println(getTextTable(dataSet, cols, new DecimalFormat("0.00")));
        out.println(MisclassificationUtils.edgeMisclassifications(graph, trueGraph));
        //		printCorrectArrows(graph, trueGraph, out);
        //		printCorrectTails(graph, trueGraph, out);
        final int SHDAdj = comparison.getEdgesAdded().size() + comparison.getEdgesRemoved().size();
        final int diffEdgePoint = comparison.getShd() - SHDAdj * 2;
        out.println("# missing/extra edges: " + SHDAdj);
        out.println("# different edge points: " + diffEdgePoint);

        //		out.println("getEdgesAdded: " + comparison.getEdgesAdded());
        //		out.println("getEdgesRemoved: " + comparison.getEdgesRemoved());
        //		out.println("getEdgesReorientedFrom: " + comparison.getEdgesReorientedFrom());
        //		out.println("getEdgesReorientedTo: " + comparison.getEdgesReorientedTo());
        out.println("-------------------------------");

        // return getTextTable(dataSet, cols, new DecimalFormat("0.00"));
        // //deleted .toString()
    }

    private double[] printCorrectArrows(final Graph outGraph, final Graph truePag, final PrintStream out) {
        int correctArrows = 0;
        int totalEstimatedArrows = 0;
        int totalTrueArrows = 0;

        final double[] stats = new double[5];

        for (final Edge edge : outGraph.getEdges()) {
            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            final Endpoint ex = edge.getEndpoint1();
            final Endpoint ey = edge.getEndpoint2();

            final Edge edge1 = truePag.getEdge(x, y);

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

        for (final Edge edge : truePag.getEdges()) {
            final Endpoint ex = edge.getEndpoint1();
            final Endpoint ey = edge.getEndpoint2();

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
        final NumberFormat nf = new DecimalFormat("0.00");
        final double precision = correctArrows / (double) totalEstimatedArrows;
        out.println("Arrow precision: " + nf.format(precision));
        final double recall = correctArrows / (double) totalTrueArrows;
        out.println("Arrow recall: " + nf.format(recall));

        stats[0] = correctArrows;
        stats[1] = totalEstimatedArrows;
        stats[2] = totalTrueArrows;
        stats[3] = precision;
        stats[4] = recall;

        return stats;
    }

    private double[] printCorrectTails(final Graph outGraph, final Graph truePag, final PrintStream out) {
        int correctTails = 0;
        int totalEstimatedTails = 0;
        int totalTrueTails = 0;

        final double[] stats = new double[5];

        for (final Edge edge : outGraph.getEdges()) {
            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            final Endpoint ex = edge.getEndpoint1();
            final Endpoint ey = edge.getEndpoint2();

            final Edge edge1 = truePag.getEdge(x, y);

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

        for (final Edge edge : truePag.getEdges()) {
            final Endpoint ex = edge.getEndpoint1();
            final Endpoint ey = edge.getEndpoint2();

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
        final NumberFormat nf = new DecimalFormat("0.00");
        final double precision = correctTails / (double) totalEstimatedTails;
        out.println("Tail precision: " + nf.format(precision));
        final double recall = correctTails / (double) totalTrueTails;
        out.println("Tail recall: " + nf.format(recall));

        stats[0] = correctTails;
        stats[1] = totalEstimatedTails;
        stats[2] = totalTrueTails;
        stats[3] = precision;
        stats[4] = recall;

        return stats;
    }

    private TextTable getTextTable(final DataSet dataSet, final int[] columns, final NumberFormat nf) {
        final TextTable table = new TextTable(dataSet.getNumRows() + 2, columns.length + 1);

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

        final NumberFormat nf2 = new DecimalFormat("0.00");

        for (int j = 0; j < columns.length; j++) {
            double sum = 0.0;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                sum += dataSet.getDouble(i, columns[j]);
            }

            final double avg = sum / dataSet.getNumRows();

            table.setToken(dataSet.getNumRows() + 2 - 1, j + 1, nf2.format(avg));
        }

        table.setToken(dataSet.getNumRows() + 2 - 1, 0, "Avg");

        return table;
    }

    private DataSet createDepDataFiltering(final Map<IndependenceFact, Double> H, final DataSet data, final int numBootstrapSamples,
                                           final boolean threshold, final double lower, final double upper) {
        final List<Node> vars = new ArrayList<>();
        final Map<IndependenceFact, Double> HCopy = new HashMap<>();
        for (final IndependenceFact f : H.keySet()) {
            if (H.get(f) > lower && H.get(f) < upper) {
                HCopy.put(f, H.get(f));
                final DiscreteVariable var = new DiscreteVariable(f.toString());
                vars.add(var);
            }
        }

        final DataSet depData = new BoxDataSet(new DoubleDataBox(numBootstrapSamples, vars.size()), vars);
        System.out.println("\nDep data rows: " + depData.getNumRows() + ", columns: " + depData.getNumColumns());
        System.out.println("HCopy size: " + HCopy.size());

        for (int b = 0; b < numBootstrapSamples; b++) {
            final DataSet bsData = DataUtils.getBootstrapSample(data, data.getNumRows());
            final IndTestProbabilistic bsTest = new IndTestProbabilistic(bsData);
            bsTest.setThreshold(threshold);
            for (final IndependenceFact f : HCopy.keySet()) {
                final boolean ind = bsTest.isIndependent(f.getX(), f.getY(), f.getZ());
                final int value = ind ? 1 : 0;
                depData.setInt(b, depData.getColumn(depData.getVariable(f.toString())), value);
            }
        }
        return depData;
    }

    private Graph runFGS(final DataSet data) {
        final BDeuScore sd = new BDeuScore(data);
        sd.setSamplePrior(1.0);
        sd.setStructurePrior(1.0);
        final Fges fgs = new Fges(sd);
        fgs.setVerbose(false);
        fgs.setFaithfulnessAssumed(true);
        Graph fgsCPDAG = fgs.search();
        fgsCPDAG = GraphUtils.replaceNodes(fgsCPDAG, data.getVariables());
        return fgsCPDAG;
    }

    private allScores getLnProbsAll(final List<Graph> pags, final Map<IndependenceFact, Double> H, final DataSet data, final BayesIm im,
                                    final Graph dep) {
        // Map<Graph, Double> pagLnBDeu = new HashMap<Graph, Double>();
        final Map<Graph, Double> pagLnBSCD = new HashMap<Graph, Double>();
        final Map<Graph, Double> pagLnBSCI = new HashMap<Graph, Double>();

        for (int i = 0; i < pags.size(); i++) {
            final Graph pagOrig = pags.get(i);
            if (!pagLnBSCD.containsKey(pagOrig)) {
                final double lnInd = getLnProb(pagOrig, H);

                // Filtering
                final double lnDep = getLnProbUsingDepFiltering(pagOrig, H, im, dep);
                pagLnBSCD.put(pagOrig, lnDep);
                pagLnBSCI.put(pagOrig, lnInd);
            }
        }

        System.out.println("pags size: " + pags.size());
        System.out.println("unique pags size: " + pagLnBSCD.size());

        return new allScores(pagLnBSCD, pagLnBSCI);
    }

    private class allScores {
        Map<Graph, Double> LnBSCD;
        Map<Graph, Double> LnBSCI;

        allScores(final Map<Graph, Double> LnBSCD, final Map<Graph, Double> LnBSCI) {
            this.LnBSCD = LnBSCD;
            this.LnBSCI = LnBSCI;
        }

    }

    private IndTestProbabilistic runRB(final DataSet data, final List<Graph> pags, final int numModels, final boolean threshold) {
        final IndTestProbabilistic BSCtest = new IndTestProbabilistic(data);

        BSCtest.setThreshold(threshold);
        final Rfci BSCrfci = new Rfci(BSCtest);

        BSCrfci.setVerbose(false);
        BSCrfci.setCompleteRuleSetUsed(false);
        BSCrfci.setDepth(this.depth);

        for (int i = 0; i < numModels; i++) {
            //			if (i % 100 == 0)
            //				System.out.print(", i: " + i);
            Graph BSCPag = BSCrfci.search();
            BSCPag = GraphUtils.replaceNodes(BSCPag, data.getVariables());
            pags.add(BSCPag);

        }
        return BSCtest;
    }

    private Graph runPagCs(final DataSet data, final double alpha) {
        final IndTestChiSquare test = new IndTestChiSquare(data, alpha);

        final Rfci fci1 = new Rfci(test);
        fci1.setDepth(this.depth);
        fci1.setVerbose(false);
        fci1.setCompleteRuleSetUsed(false);
        Graph PAG_CS = fci1.search();
        PAG_CS = GraphUtils.replaceNodes(PAG_CS, data.getVariables());
        return PAG_CS;
    }

    // private double getLnProbUsingDep(Graph pag, Map<IndependenceFact, Double>
    // H, BayesIm im, Graph dep) {
    // double lnQ = 0;
    //
    // for (IndependenceFact fact : H.keySet()) {
    // BCInference.OP op;
    // double p = 0.0;
    //
    // if (pag.isDSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
    // op = BCInference.OP.independent;
    // } else {
    // op = BCInference.OP.dependent;
    // }
    //
    // Node node = im.getNode(fact.toString());
    //
    // int[] parents = im.getParents(im.getNodeIndex(node));
    //
    // if (parents.length > 0){
    //
    // int[] parentValues = new int[parents.length];
    //
    // for (int parentIndex = 0; parentIndex < parentValues.length;
    // parentIndex++) {
    // String parentName = im.getNode(parents[parentIndex]).getName();
    // String[] splitParent = parentName.split(Pattern.quote("_||_"));
    // Node X = pag.getNode(splitParent[0].trim());
    //
    // String[] splitParent2 = splitParent[1].trim().split(Pattern.quote("|"));
    // Node Y = pag.getNode(splitParent2[0].trim());
    //
    // List<Node> Z = new ArrayList<Node>();
    // if(splitParent2.length>1){
    // String[] splitParent3 = splitParent2[1].trim().split(Pattern.quote(","));
    // for(String s: splitParent3){
    // Z.add(pag.getNode(s.trim()));
    // }
    // }
    // IndependenceFact parentFact = new IndependenceFact(X, Y, Z);
    // if (pag.isDSeparatedFrom(parentFact.getX(), parentFact.getY(),
    // parentFact.getZ())) {
    // parentValues[parentIndex] = 1;
    // } else {
    // parentValues[parentIndex] = 0;
    // }
    // }
    //
    // int rowIndex = im.getRowIndex(im.getNodeIndex(node), parentValues);
    // p = im.getProbability(im.getNodeIndex(node), rowIndex, 1);
    //
    // if (op == BCInference.OP.dependent) {
    // p = 1.0 - p;
    // }
    // }
    // else{
    // p = im.getProbability(im.getNodeIndex(node), 0, 1);
    // if (op == BCInference.OP.dependent) {
    // p = 1.0 - p;
    // }
    // }
    //
    // if (p < -0.0001 || p > 1.0001 || Double.isNaN(p) || Double.isInfinite(p))
    // {
    // throw new IllegalArgumentException("p illegally equals " + p);
    // }
    //
    // double v = lnQ + log(p);
    //
    // if (Double.isNaN(v) || Double.isInfinite(v)) {
    // continue;
    // }
    //
    // lnQ = v;
    // }
    // return lnQ;
    // }

    private double getLnProbUsingDepFiltering(final Graph pag, final Map<IndependenceFact, Double> H, final BayesIm im, final Graph dep) {
        double lnQ = 0;

        for (final IndependenceFact fact : H.keySet()) {
            final BCInference.OP op;
            double p = 0.0;

            if (pag.isDSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
                op = BCInference.OP.independent;
            } else {
                op = BCInference.OP.dependent;
            }

            if (im.getNode(fact.toString()) != null) {
                final Node node = im.getNode(fact.toString());

                final int[] parents = im.getParents(im.getNodeIndex(node));

                if (parents.length > 0) {

                    final int[] parentValues = new int[parents.length];

                    for (int parentIndex = 0; parentIndex < parentValues.length; parentIndex++) {
                        final String parentName = im.getNode(parents[parentIndex]).getName();
                        final String[] splitParent = parentName.split(Pattern.quote("_||_"));
                        final Node X = pag.getNode(splitParent[0].trim());

                        final String[] splitParent2 = splitParent[1].trim().split(Pattern.quote("|"));
                        final Node Y = pag.getNode(splitParent2[0].trim());

                        final List<Node> Z = new ArrayList<Node>();
                        if (splitParent2.length > 1) {
                            final String[] splitParent3 = splitParent2[1].trim().split(Pattern.quote(","));
                            for (final String s : splitParent3) {
                                Z.add(pag.getNode(s.trim()));
                            }
                        }
                        final IndependenceFact parentFact = new IndependenceFact(X, Y, Z);
                        if (pag.isDSeparatedFrom(parentFact.getX(), parentFact.getY(), parentFact.getZ())) {
                            parentValues[parentIndex] = 1;
                        } else {
                            parentValues[parentIndex] = 0;
                        }
                    }

                    final int rowIndex = im.getRowIndex(im.getNodeIndex(node), parentValues);
                    p = im.getProbability(im.getNodeIndex(node), rowIndex, 1);

                    if (op == BCInference.OP.dependent) {
                        p = 1.0 - p;
                    }
                } else {
                    p = im.getProbability(im.getNodeIndex(node), 0, 1);
                    if (op == BCInference.OP.dependent) {
                        p = 1.0 - p;
                    }
                }

                if (p < -0.0001 || p > 1.0001 || Double.isNaN(p) || Double.isInfinite(p)) {
                    throw new IllegalArgumentException("p illegally equals " + p);
                }

                final double v = lnQ + log(p);

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

                final double v = lnQ + log(p);

                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    continue;
                }

                lnQ = v;
            }
        }

        return lnQ;
    }

    private double getLnProb(final Graph pag, final Map<IndependenceFact, Double> H) {
        double lnQ = 0;
        for (final IndependenceFact fact : H.keySet()) {
            final BCInference.OP op;

            if (pag.isDSeparatedFrom(fact.getX(), fact.getY(), fact.getZ())) {
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

            final double v = lnQ + log(p);

            if (Double.isNaN(v) || Double.isInfinite(v)) {
                continue;
            }

            lnQ = v;
        }
        return lnQ;
    }

    private Map<Graph, Double> normalProbs(final Map<Graph, Double> pagLnProbs) {
        final double lnQTotal = lnQTotal(pagLnProbs);
        final Map<Graph, Double> normalized = new HashMap<Graph, Double>();
        for (final Graph pag : pagLnProbs.keySet()) {
            final double lnQ = pagLnProbs.get(pag);
            final double normalizedlnQ = lnQ - lnQTotal;
            normalized.put(pag, Math.exp(normalizedlnQ));
        }
        return normalized;
    }

    private BayesIm loadBayesIm(final String filename, final boolean useDisplayNames) {
        try {
            final Builder builder = new Builder();
            final File dir = new File(this.directory + "/xdsl");
            final File file = new File(dir, filename);
            final Document document = builder.build(file);
            final XdslXmlParser parser = new XdslXmlParser();
            parser.setUseDisplayNames(useDisplayNames);
            return parser.getBayesIm(document.getRootElement());
        } catch (final ParsingException e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
    // private Map<Graph, Double> getLnProbs(List<Graph> pags,
    // Map<IndependenceFact, Double> H) {
    // Map<Graph, Double> pagLnProb = new HashMap<Graph, Double>();
    // for (int i = 0; i < pags.size(); i++) {
    // Graph pag = pags.get(i);
    // double lnQ = getLnProb(pag, H);
    // pagLnProb.put(pag, lnQ);
    // }
    // System.out.println("pags size: " + pags.size());
    // System.out.println("unique pags size: " + pagLnProb.size());
    //
    // return pagLnProb;
    // }

    // private Map<Graph, Double> getLnProbsUsingDep(List<Graph> pags,
    // Map<IndependenceFact, Double> H, BayesIm imHat, Graph estDepBN) {
    // Map<Graph, Double> pagLnProb = new HashMap<Graph, Double>();
    // for (int i = 0; i < pags.size(); i++) {
    // Graph pag = pags.get(i);
    // double lnQ = getLnProbUsingDep(pag, H, imHat, estDepBN);
    // pagLnProb.put(pag, lnQ);
    // }
    // System.out.println("pags size: " + pags.size());
    // System.out.println("unique pags size: " + pagLnProb.size());
    //
    // return pagLnProb;
    // }

    protected double lnXplusY(double lnX, double lnY) {
        final double lnYminusLnX;
        final double temp;

        if (lnY > lnX) {
            temp = lnX;
            lnX = lnY;
            lnY = temp;
        }

        lnYminusLnX = lnY - lnX;

        if (lnYminusLnX < MININUM_EXPONENT) {
            return lnX;
        } else {
            final double w = Math.log1p(exp(lnYminusLnX));
            return w + lnX;
        }
    }

    private double lnQTotal(final Map<Graph, Double> pagLnProb) {
        final Set<Graph> pags = pagLnProb.keySet();
        final Iterator<Graph> iter = pags.iterator();
        double lnQTotal = pagLnProb.get(iter.next());

        while (iter.hasNext()) {
            final Graph pag = iter.next();
            final double lnQ = pagLnProb.get(pag);
            lnQTotal = lnXplusY(lnQTotal, lnQ);
        }

        return lnQTotal;
    }

    private static final int MININUM_EXPONENT = -1022;

    // public Graph makeDAG(int numVars, double edgesPerNode, int
    // numLatentConfounders){
    // final int numEdges = (int) (numVars * edgesPerNode);
    // List<Node> vars = new ArrayList<Node>();
    // for (int i = 0; i < numVars; i++) {
    // vars.add(new DiscreteVariable(Integer.toString(i)));
    // }
    // return GraphUtils.randomGraphRandomForwardEdges(vars,
    // numLatentConfounders, numEdges, 30, 15, 15, false,
    // true);//randomGraphRandomForwardEdges(vars, 0,numEdges);
    // }
    //
    // public Graph makeSimpleDAG(int numLatentConfounders){
    // List<Node> nodes = new ArrayList<Node>();
    // for (int i=0; i<5; i++){
    // nodes.add(new DiscreteVariable(Integer.toString(i+1)));
    // }
    //
    // Graph dag = new EdgeListGraph(nodes);
    // dag.addDirectedEdge(nodes.get(0), nodes.get(1));
    // dag.addDirectedEdge(nodes.get(0), nodes.get(2));
    // dag.addDirectedEdge(nodes.get(1), nodes.get(3));
    // dag.addDirectedEdge(nodes.get(2), nodes.get(3));
    // dag.addDirectedEdge(nodes.get(2), nodes.get(4));
    // return dag;
    // }

    public DataSet bootStrapSampling(final DataSet data, final int numBootstrapSamples, final int bootsrapSampleSize) {

        final DataSet bootstrapSample = DataUtils.getBootstrapSample(data, bootsrapSampleSize);
        return bootstrapSample;
    }

    // private void print(Map<Graph, Double> probs, PrintStream out) {
    // for (Graph g: probs.keySet()){
    // out.println(g +"\t" + probs.get(g));
    // }
    // }
}
