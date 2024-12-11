package edu.cmu.tetrad.study.performance;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.BdeuScore;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.TsDagToPag;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.sem.ScoreType;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TextTable;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Does a comparison of algorithm results across algorithm type, sample sizes, etc.
 *
 * @author josephramsey 2016.03.24
 * @author dmalinsky 2016.05.20
 * @version $Id: $Id
 */
public class Comparison2 {

    /**
     * Private constructor to prevent instantiation.
     */
    private Comparison2() {

    }

    /**
     * Simulates data from model parameterizing the given DAG, and runs the algorithm on that data, printing out error
     * statistics.
     *
     * @param params a {@link edu.cmu.tetrad.study.performance.ComparisonParameters} object
     * @return a {@link edu.cmu.tetrad.study.performance.ComparisonResult} object
     */
    public static ComparisonResult compare(ComparisonParameters params) throws InterruptedException {
        DataSet dataSet = null;
        Graph trueDag = null;
        IndependenceTest test = null;
        Score score = null;

        ComparisonResult result = new ComparisonResult(params);

        if (params.isDataFromFile()) {

            final String path = "/Users/dmalinsky/Documents/research/data/danexamples";

            File dir = new File(path);
            File[] files = dir.listFiles();

            if (files == null) {
                throw new NullPointerException("No files in " + path);
            }

            for (File file : files) {

                if (file.getName().startsWith("graph") && file.getName().contains(String.valueOf(params.getGraphNum()))
                    && file.getName().endsWith(".g.txt")) {
                    params.setGraphFile(file.getName());
                    trueDag = GraphSaveLoadUtils.loadGraphTxt(file);
                    break;
                }

            }

            String trialGraph = String.valueOf(params.getGraphNum()).concat("-").concat(String.valueOf(params.getTrial())).concat(".dat.txt");

            for (File file : files) {

                if (file.getName().startsWith("graph") && file.getName().endsWith(trialGraph)) {

                    Path dataFile = Paths.get(path.concat("/").concat(file.getName()));
                    final Delimiter delimiter = Delimiter.TAB;

                    if (params.getDataType() == ComparisonParameters.DataType.Continuous) {
                        try {
                            ContinuousTabularDatasetFileReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, delimiter);
                            dataSet = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } else {
                        try {
                            VerticalDiscreteTabularDatasetFileReader dataReader = new VerticalDiscreteTabularDatasetFileReader(dataFile, delimiter);
                            dataSet = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                    params.setDataFile(file.getName());
                    break;
                }

            }
            System.out.println("current graph file = " + params.getGraphFile());
            System.out.println("current data set file = " + params.getDataFile());
        } // end isDataFromFile()

        if (params.isNoData()) {
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < params.getNumVars(); i++) {
                nodes.add(new ContinuousVariable("X" + (i + 1)));
            }

            trueDag = RandomGraph.randomGraphRandomForwardEdges(
                    nodes, 0, params.getNumEdges(), 10, 10, 10, false, true);

            if (params.getAlgorithm() == ComparisonParameters.Algorithm.SVARFCI) {
                trueDag = RandomGraph.randomGraphRandomForwardEdges(
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false, true);
                trueDag = TsUtils.graphToLagGraph(trueDag, 2);
                System.out.println("Creating Time Lag Graph : " + trueDag);
            }

            test = new MsepTest(trueDag);
            score = new GraphScore(trueDag);

            if (params.getAlgorithm() == null) {
                throw new IllegalArgumentException("Algorithm not set.");
            }

            long time1 = MillisecondTimes.timeMillis();

            if (params.getAlgorithm() == ComparisonParameters.Algorithm.PC) {
                Pc search = new Pc(test);
                result.setResultGraph(search.search());
                Graph dag = new EdgeListGraph(trueDag);
                result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.CPC) {
                Cpc search = new Cpc(test);
                result.setResultGraph(search.search());
                Graph dag = new EdgeListGraph(trueDag);
                result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FGES) {
                Fges search = new Fges(score);
                //search.setFaithfulnessAssumed(params.isOneEdgeFaithfulnessAssumed());
                result.setResultGraph(search.search());
                Graph dag = new EdgeListGraph(trueDag);
                result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FCI) {
                Fci search = new Fci(test);
                result.setResultGraph(search.search());
                result.setCorrectResult(GraphTransforms.dagToPag(trueDag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.GFCI) {
                GFci search = new GFci(test, score);
                result.setResultGraph(search.search());
                result.setCorrectResult(GraphTransforms.dagToPag(trueDag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.SVARFCI) {
                SvarFci search = new SvarFci(test);
                Knowledge knowledge = getKnowledge(trueDag);
                search.setKnowledge(knowledge);
                result.setResultGraph(search.search());
                result.setCorrectResult(new TsDagToPag(trueDag).convert());
                System.out.println("Correct result for trial = " + result.getCorrectResult());
                System.out.println("Search result for trial = " + result.getResultGraph());
            } else {
                throw new IllegalArgumentException("Unrecognized algorithm.");
            }

            long time2 = MillisecondTimes.timeMillis();

            long elapsed = time2 - time1;
            result.setElapsed(elapsed);

            result.setTrueDag(trueDag);

            return result;

        } else if (params.getDataFile() != null) {
//            dataSet = loadDataFile(params.getDataFile());
            System.out.println("Using data from file... ");
            if (params.getGraphFile() == null) {
                throw new IllegalArgumentException("True graph file not set.");
            } else {
                System.out.println("Using graph from file... ");
//                trueDag = GraphUtils.loadGraph(File params.getGraphFile());
            }

        } else {

            if (params.getNumVars() == -1) {
                throw new IllegalArgumentException("Number of variables not set.");
            }

            if (params.getNumEdges() == -1) {
                throw new IllegalArgumentException("Number of edges not set.");
            }

            if (params.getDataType() == ComparisonParameters.DataType.Continuous) {
                List<Node> nodes = new ArrayList<>();

                for (int i = 0; i < params.getNumVars(); i++) {
                    nodes.add(new ContinuousVariable("X" + (i + 1)));
                }

                trueDag = RandomGraph.randomGraphRandomForwardEdges(
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false, true);

                if (params.getAlgorithm() == ComparisonParameters.Algorithm.SVARFCI) {
                    trueDag = RandomGraph.randomGraphRandomForwardEdges(
                            nodes, 0, params.getNumEdges(), 10, 10, 10, false, true);
                    trueDag = TsUtils.graphToLagGraph(trueDag, 2);
                    System.out.println("Creating Time Lag Graph : " + trueDag);
                }

                if (params.getDataType() == null) {
                    throw new IllegalArgumentException("Data type not set or inferred.");
                }

                if (params.getSampleSize() == -1) {
                    throw new IllegalArgumentException("Sample size not set.");
                }

                LargeScaleSimulation sim = new LargeScaleSimulation(trueDag);

                if (params.getAlgorithm() == ComparisonParameters.Algorithm.SVARFCI) {
                    sim.setCoefRange(0.20, 0.50);
                }

//                dataSet = sim.simulateDataAcyclic(params.getSampleSize());
                if (params.getAlgorithm() == ComparisonParameters.Algorithm.SVARFCI) {
                    boolean isStableTetradMatrix;
                    int attempt = 1;
                    int tierSize = params.getNumVars();
                    int[] sub = new int[tierSize];
                    int[] sub2 = new int[tierSize];
                    for (int i = 0; i < tierSize; i++) {
                        sub[i] = i;
                        sub2[i] = tierSize + i;
                    }
                    do {
                        dataSet = sim.simulateDataFisher(params.getSampleSize());

                        Matrix coefMat = new Matrix(sim.getCoefficientMatrix());
                        Matrix B = coefMat.getSelection(sub, sub);
                        Matrix Gamma1 = coefMat.getSelection(sub2, sub);
                        Matrix Gamma0 = Matrix.identity(tierSize).minus(B);
                        Matrix A1 = Gamma0.inverse().times(Gamma1);

                        isStableTetradMatrix = TsUtils.allEigenvaluesAreSmallerThanOneInModulus(A1);
                        System.out.println("isStableTetradMatrix? : " + isStableTetradMatrix);
                        attempt++;
                    } while ((!isStableTetradMatrix) && attempt <= 5);
                    if (!isStableTetradMatrix) {
                        System.out.println("%%%%%%%%%% WARNING %%%%%%%% not a stable coefficient matrix, forcing coefs to [0.15,0.3]");
                        System.out.println("Made " + (attempt - 1) + " attempts to get stable matrix.");
                        sim.setCoefRange(0.15, 0.3);
                        dataSet = sim.simulateDataFisher(params.getSampleSize());
                    } else {
                        System.out.println("Coefficient matrix is stable.");
                    }
                }

            } else if (params.getDataType() == ComparisonParameters.DataType.Discrete) {
                List<Node> nodes = new ArrayList<>();

                for (int i = 0; i < params.getNumVars(); i++) {
                    nodes.add(new DiscreteVariable("X" + (i + 1), 3));
                }

                trueDag = RandomGraph.randomGraphRandomForwardEdges(
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false, true);

                if (params.getDataType() == null) {
                    throw new IllegalArgumentException("Data type not set or inferred.");
                }

                if (params.getSampleSize() == -1) {
                    throw new IllegalArgumentException("Sample size not set.");
                }

                int[] tiers = new int[nodes.size()];

                for (int i = 0; i < nodes.size(); i++) {
                    tiers[i] = i;
                }

                BayesPm pm = new BayesPm(trueDag, 3, 3);
                MlBayesIm im = new MlBayesIm(pm, MlBayesIm.InitializationMethod.RANDOM);
                dataSet = im.simulateData(params.getSampleSize(), false, tiers);
            } else {
                throw new IllegalArgumentException("Unrecognized data type.");
            }
            if (dataSet == null) {
                throw new IllegalArgumentException("No data set.");
            }
        }

        if (params.getIndependenceTest() == ComparisonParameters.IndependenceTestType.FisherZ) {
            if (params.getDataType() != null && params.getDataType() != ComparisonParameters.DataType.Continuous) {
                throw new IllegalArgumentException("Data type previously set to something other than continuous.");
            }

            if (Double.isNaN(params.getAlpha())) {
                throw new IllegalArgumentException("Alpha not set.");
            }

            assert dataSet != null;
            test = new IndTestFisherZ(dataSet, params.getAlpha());

            params.setDataType(ComparisonParameters.DataType.Continuous);
        } else if (params.getIndependenceTest() == ComparisonParameters.IndependenceTestType.ChiSquare) {
            if (params.getDataType() != null && params.getDataType() != ComparisonParameters.DataType.Discrete) {
                throw new IllegalArgumentException("Data type previously set to something other than discrete.");
            }

            if (Double.isNaN(params.getAlpha())) {
                throw new IllegalArgumentException("Alpha not set.");
            }

            assert dataSet != null;
            test = new IndTestChiSquare(dataSet, params.getAlpha());

            params.setDataType(ComparisonParameters.DataType.Discrete);
        }

        if (params.getScore() == ScoreType.SemBic) {
            if (params.getDataType() != null && params.getDataType() != ComparisonParameters.DataType.Continuous) {
                throw new IllegalArgumentException("Data type previously set to something other than continuous.");
            }

            if (Double.isNaN(params.getPenaltyDiscount())) {
                throw new IllegalArgumentException("Penalty discount not set.");
            }

            SemBicScore semBicScore = new SemBicScore(new CovarianceMatrix(dataSet));
            semBicScore.setPenaltyDiscount(params.getPenaltyDiscount());
            score = semBicScore;

            params.setDataType(ComparisonParameters.DataType.Continuous);
        } else if (params.getScore() == ScoreType.BDeu) {
            if (params.getDataType() != null && params.getDataType() != ComparisonParameters.DataType.Discrete) {
                throw new IllegalArgumentException("Data type previously set to something other than discrete.");
            }

            if (Double.isNaN(params.getSamplePrior())) {
                throw new IllegalArgumentException("Sample prior not set.");
            }

            if (Double.isNaN(params.getStructurePrior())) {
                throw new IllegalArgumentException("Structure prior not set.");
            }

            score = new BdeuScore(dataSet);
            ((BdeuScore) score).setSamplePrior(params.getSamplePrior());
            ((BdeuScore) score).setStructurePrior(params.getStructurePrior());

            params.setDataType(ComparisonParameters.DataType.Discrete);

            params.setDataType(ComparisonParameters.DataType.Discrete);
        }

        if (params.getAlgorithm() == null) {
            throw new IllegalArgumentException("Algorithm not set.");
        }

        long time1 = MillisecondTimes.timeMillis();

        if (params.getAlgorithm() == ComparisonParameters.Algorithm.PC) {
            if (test == null) {
                throw new IllegalArgumentException("Test not set.");
            }
            Pc search = new Pc(test);
            result.setResultGraph(search.search());
            Graph dag = new EdgeListGraph(trueDag);
            result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.CPC) {
            if (test == null) {
                throw new IllegalArgumentException("Test not set.");
            }
            Cpc search = new Cpc(test);
            result.setResultGraph(search.search());
            Graph dag = new EdgeListGraph(trueDag);
            result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FGES) {
            if (score == null) {
                throw new IllegalArgumentException("Score not set.");
            }
            Fges search = new Fges(score);
            //search.setFaithfulnessAssumed(params.isOneEdgeFaithfulnessAssumed());
            result.setResultGraph(search.search());
            Graph dag = new EdgeListGraph(trueDag);
            result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FCI) {
            if (test == null) {
                throw new IllegalArgumentException("Test not set.");
            }
            Fci search = new Fci(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(GraphTransforms.dagToPag(trueDag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.GFCI) {
            if (test == null) {
                throw new IllegalArgumentException("Test not set.");
            }
            GFci search = new GFci(test, score);
            result.setResultGraph(search.search());
            result.setCorrectResult(GraphTransforms.dagToPag(trueDag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.SVARFCI) {
            if (test == null) {
                throw new IllegalArgumentException("Test not set.");
            }
            SvarFci search = new SvarFci(test);
            assert trueDag != null;
            Knowledge knowledge = Comparison2.getKnowledge(trueDag);
            search.setKnowledge(knowledge);
            result.setResultGraph(search.search());
            result.setCorrectResult(new TsDagToPag(trueDag).convert());
        } else {
            throw new IllegalArgumentException("Unrecognized algorithm.");
        }

        long time2 = MillisecondTimes.timeMillis();

        long elapsed = time2 - time1;
        result.setElapsed(elapsed);

        result.setTrueDag(trueDag);

        return result;
    }

    //    private static Graph loadGraphFile(String graphFile) {
//        return null;
//    }
//
//    private static DataSet loadDataFile(String dataFile) {
////        DataReader reader = new DataReader();
////        reader.setDelimiter(DelimiterType.TAB);
////        reader.setMaxIntegralDiscrete(0);
////        DataSet dataset = reader.parseTabular(new File(dataFile));
////        return dataset;
//        return null;
//    }
    // changed return type of 'summarize' to TextTable

    /**
     * <p>summarize.</p>
     *
     * @param results      a {@link java.util.List} object
     * @param tableColumns a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.util.TextTable} object
     */
    public static TextTable summarize(List<ComparisonResult> results, List<TableColumn> tableColumns) {

        List<Node> variables = new ArrayList<>();
        for (TableColumn column : tableColumns) {
            variables.add(new ContinuousVariable(column.toString()));
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(0, variables.size()), variables);
        dataSet.setNumberFormat(new DecimalFormat("0"));

        for (int i = 0; i < results.size(); i++) {
            System.out.println("\nRun " + (i + 1) + "\n" + results.get(i));
        }

        System.out.println();

        for (ComparisonResult _result : results) {
            Graph correctGraph = _result.getCorrectResult();
            Graph resultGraph = _result.getResultGraph();

            GraphUtils.GraphComparison comparison = GraphSearchUtils.getGraphComparison(correctGraph, resultGraph);

            int newRow = dataSet.getNumRows();

            if (tableColumns.contains(TableColumn.AdjCor)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjCor), comparison.getAdjCor());
            }

            if (tableColumns.contains(TableColumn.AdjFn)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjFn), comparison.getAdjFn());
            }

            if (tableColumns.contains(TableColumn.AdjFp)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjFp), comparison.getAdjFp());
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

            if (tableColumns.contains(TableColumn.AdjPrec)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjPrec), comparison.getAdjPrec());
            }

            if (tableColumns.contains(TableColumn.AdjRec)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AdjRec), comparison.getAdjRec());
            }

            if (tableColumns.contains(TableColumn.AhdPrec)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AhdPrec), comparison.getAhdPrec());
            }

            if (tableColumns.contains(TableColumn.AhdRec)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.AhdRec), comparison.getAhdRec());
            }

            if (tableColumns.contains(TableColumn.Elapsed)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.Elapsed), _result.getElapsed());
            }

            if (tableColumns.contains(TableColumn.SHD)) {
                dataSet.setDouble(newRow, tableColumns.indexOf(TableColumn.SHD), comparison.getShd());
            }
        }

        int[] cols = new int[tableColumns.size()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        return Comparison2.getTextTable(dataSet, cols, new DecimalFormat("0.00")); //deleted .toString()
    }

    private static TextTable getTextTable(DataSet dataSet, int[] columns, NumberFormat nf) {
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

    /**
     * <p>getKnowledge.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public static Knowledge getKnowledge(Graph graph) {
//        System.out.println("Entering getKnowledge ... ");
        int numLags; // need to fix this!
        List<Node> variables = graph.getNodes();
        List<Integer> laglist = new ArrayList<>();
        Knowledge knowledge = new Knowledge();
        int lag;
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
            }
            laglist.add(lag);
        }
        numLags = Collections.max(laglist);

//        System.out.println("Variable list before the sort = " + variables);
        variables.sort((o1, o2) -> {
            String name1 = Comparison2.getNameNoLag(o1);
            String name2 = Comparison2.getNameNoLag(o2);

            String prefix1 = Comparison2.getPrefix(name1);
            String prefix2 = Comparison2.getPrefix(name2);

            int index1 = Comparison2.getIndex(name1);
            int index2 = Comparison2.getIndex(name2);

            if (Comparison2.getLag(o1.getName()) == Comparison2.getLag(o2.getName())) {
                if (prefix1.compareTo(prefix2) == 0) {
                    return Integer.compare(index1, index2);
                } else {
                    return prefix1.compareTo(prefix2);
                }
            } else {
                return Comparison2.getLag(o1.getName()) - Comparison2.getLag(o2.getName());
            }
        });

//        System.out.println("Variable list after the sort = " + variables);
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
//                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
//                laglist.add(lag);
            }
            knowledge.addToTier(numLags - lag, node.getName());
        }

        //System.out.println("Knowledge in graph = " + knowledge);
        return knowledge;
    }

    /**
     * <p>getNameNoLag.</p>
     *
     * @param obj a {@link java.lang.Object} object
     * @return a {@link java.lang.String} object
     */
    public static String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else {
            return tempS.substring(0, tempS.indexOf(':'));
        }
    }

    /**
     * <p>getPrefix.</p>
     *
     * @param s a {@link java.lang.String} object
     * @return a {@link java.lang.String} object
     */
    public static String getPrefix(String s) {

        return s.substring(0, 1);
    }

    /**
     * <p>getIndex.</p>
     *
     * @param s a {@link java.lang.String} object
     * @return a int
     */
    public static int getIndex(String s) {
        int y = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            try {
                y = Integer.parseInt(s.substring(i));
            } catch (NumberFormatException e) {
                return y;
            }
        }
        throw new IllegalArgumentException("Not integer suffix.");
    }

    /**
     * <p>getLag.</p>
     *
     * @param s a {@link java.lang.String} object
     * @return a int
     */
    public static int getLag(String s) {
        if (s.indexOf(':') == -1) {
            return 0;
        }
        String tmp = s.substring(s.indexOf(':') + 1);
        return (Integer.parseInt(tmp));
    }

    /**
     * An enum of table columns.
     */
    public enum TableColumn {

        /**
         * Constant <code>AdjCor</code>
         */
        AdjCor,

        /**
         * Constant <code>AdjFn</code>
         */
        AdjFn,

        /**
         * Constant <code>AdjFp</code>
         */
        AdjFp,

        /**
         * Constant <code>AhdCor</code>
         */
        AhdCor,

        /**
         * Constant <code>AhdFn</code>
         */
        AhdFn,

        /**
         * Constant <code>AhdFp</code>
         */
        AhdFp,

        /**
         * Constant <code>SHD</code>
         */
        SHD,

        /**
         * Constant <code>AdjPrec</code>
         */
        AdjPrec,

        /**
         * Constant <code>AdjRec</code>
         */
        AdjRec,

        /**
         * Constant <code>AhdPrec</code>
         */
        AhdPrec,

        /**
         * Constant <code>AhdRec</code>
         */
        AhdRec,

        /**
         * Constant <code>Elapsed</code>
         */
        Elapsed
    }
}
