package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.io.TabularContinuousDataReader;
import edu.cmu.tetrad.io.VerticalTabularDiscreteDataReader;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.TextTable;
import edu.cmu.tetrad.data.DataReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.io.*;

/**
 * Does a comparison of algorithm results across algorithm type, sample sizes, etc.
 *
 * @author jdramsey 2016.03.24
 * @author dmalinsky 2016.05.20
 */
public class Comparison2 {

    /**
     * Simulates data from model parameterizing the given DAG, and runs the algorithm on that data,
     * printing out error statistics.
     */
    public static ComparisonResult compare(ComparisonParameters params) {
        DataSet dataSet = null;
        Graph trueDag = null;
        IndependenceTest test = null;
        Score score = null;

        ComparisonResult result = new ComparisonResult(params);

        if (params.isDataFromFile()) {

            /** Set path to the data directory **/
            String path = "/Users/dmalinsky/Documents/research/data/danexamples";


            File dir = new File(path);
            File[] files = dir.listFiles();

            if (files == null) throw new NullPointerException("No files in " + path);

            for (File file : files) {

                if (file.getName().startsWith("graph") && file.getName().contains(String.valueOf(params.getGraphNum()))
                        && file.getName().endsWith(".g.txt")) {
                    params.setGraphFile(file.getName());
                    trueDag = GraphUtils.loadGraphTxt(file);
                    break;
                }

            }

            String trialGraph = String.valueOf(params.getGraphNum()).concat("-").concat(String.valueOf(params.getTrial())).concat(".dat.txt");

            for (File file : files) {

                if (file.getName().startsWith("graph") && file.getName().endsWith(trialGraph)) {

                    Path dataFile = Paths.get(path.concat("/").concat(file.getName()));
                    Character delimiter = '\t';

                    if (params.getDataType() == ComparisonParameters.DataType.Continuous) {
                        try {
                            edu.cmu.tetrad.io.DataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
                            dataSet = dataReader.readInData();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        params.setDataFile(file.getName());
                        break;

                    } else {
                        try {
                            edu.cmu.tetrad.io.DataReader dataReader = new VerticalTabularDiscreteDataReader(dataFile, delimiter);
                            dataSet = dataReader.readInData();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        params.setDataFile(file.getName());
                        break;

                    }
                }

            }
            System.out.println("current graph file = " + params.getGraphFile());
            System.out.println("current data set file = " + params.getDataFile());
        } // end isDataFromFile()




        if (params.isNoData()){
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < params.getNumVars(); i++) {
                nodes.add(new ContinuousVariable("X" + (i + 1)));
            }

            trueDag = GraphUtils.randomGraphRandomForwardEdges(
                    nodes, 0, params.getNumEdges(), 10, 10, 10, false, true);

            /** added 5.25.16 for tsFCI **/
            if (params.getAlgorithm() == ComparisonParameters.Algorithm.TsFCI) {
                trueDag = GraphUtils.randomGraphRandomForwardEdges(
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false, true); //need lag version of this
            }
            /***************************/

            test = new IndTestDSep(trueDag);
            score = new GraphScore(trueDag);

            if (params.getAlgorithm() == null) {
                throw new IllegalArgumentException("Algorithm not set.");
            }

            long time1 = System.currentTimeMillis();

            if (params.getAlgorithm() == ComparisonParameters.Algorithm.PC) {
                if (test == null) throw new IllegalArgumentException("Test not set.");
                Pc search = new Pc(test);
                result.setResultGraph(search.search());
                result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.CPC) {
                if (test == null) throw new IllegalArgumentException("Test not set.");
                Cpc search = new Cpc(test);
                result.setResultGraph(search.search());
                result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.PCLocal) {
                if (test == null) throw new IllegalArgumentException("Test not set.");
                PcLocal search = new PcLocal(test);
                result.setResultGraph(search.search());
                result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.PCMax) {
                if (test == null) throw new IllegalArgumentException("Test not set.");
                PcMax search = new PcMax(test);
                result.setResultGraph(search.search());
                result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FGS) {
                if (score == null) throw new IllegalArgumentException("Score not set.");
                Fgs search = new Fgs(score);
                //search.setFaithfulnessAssumed(params.isOneEdgeFaithfulnessAssumed());
                result.setResultGraph(search.search());
                result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FCI) {
                if (test == null) throw new IllegalArgumentException("Test not set.");
                Fci search = new Fci(test);
                result.setResultGraph(search.search());
                result.setCorrectResult(new DagToPag(trueDag).convert());
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.GFCI) {
                if (test == null) throw new IllegalArgumentException("Test not set.");
                GFci search = new GFci(test);
                result.setResultGraph(search.search());
                result.setCorrectResult(new DagToPag(trueDag).convert());
            } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.TsFCI) {
                if (test == null) throw new IllegalArgumentException("Test not set.");
                TsFci search = new TsFci(test);
                result.setResultGraph(search.search());
                result.setCorrectResult(new TsDagToPag(trueDag).convert());
            } else {
                throw new IllegalArgumentException("Unrecognized algorithm.");
            }

            long time2 = System.currentTimeMillis();

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

                trueDag = GraphUtils.randomGraphRandomForwardEdges(
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false, true);

                if (params.getDataType() == null) {
                    throw new IllegalArgumentException("Data type not set or inferred.");
                }

                if (params.getSampleSize() == -1) {
                    throw new IllegalArgumentException("Sample size not set.");
                }

                LargeSemSimulator sim = new LargeSemSimulator(trueDag);
                dataSet = sim.simulateDataAcyclic(params.getSampleSize());
            } else if (params.getDataType() == ComparisonParameters.DataType.Discrete) {
                List<Node> nodes = new ArrayList<>();

                for (int i = 0; i < params.getNumVars(); i++) {
                    nodes.add(new DiscreteVariable("X" + (i + 1), 3));
                }

                trueDag = GraphUtils.randomGraphRandomForwardEdges(
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
                MlBayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
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

            test = new IndTestFisherZ(dataSet, params.getAlpha());

            params.setDataType(ComparisonParameters.DataType.Continuous);
        }  else if (params.getIndependenceTest() == ComparisonParameters.IndependenceTestType.ChiSquare) {
            if (params.getDataType() != null && params.getDataType() != ComparisonParameters.DataType.Discrete) {
                throw new IllegalArgumentException("Data type previously set to something other than discrete.");
            }

            if (Double.isNaN(params.getAlpha())) {
                throw new IllegalArgumentException("Alpha not set.");
            }

            test = new IndTestChiSquare(dataSet, params.getAlpha());

            params.setDataType(ComparisonParameters.DataType.Discrete);
        }

        if (params.getScore() == ComparisonParameters.ScoreType.SemBic) {
            if (params.getDataType() != null && params.getDataType() != ComparisonParameters.DataType.Continuous) {
                throw new IllegalArgumentException("Data type previously set to something other than continuous.");
            }

            if (Double.isNaN(params.getPenaltyDiscount())) {
                throw new IllegalArgumentException("Penalty discount not set.");
            }

            SemBicScore semBicScore = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            semBicScore.setPenaltyDiscount(params.getPenaltyDiscount());
            score = semBicScore;

            params.setDataType(ComparisonParameters.DataType.Continuous);
        }
        else if (params.getScore() == ComparisonParameters.ScoreType.BDeu) {
            if (params.getDataType() != null && params.getDataType() != ComparisonParameters.DataType.Discrete) {
                throw new IllegalArgumentException("Data type previously set to something other than discrete.");
            }

            if (Double.isNaN(params.getSamplePrior())) {
                throw new IllegalArgumentException("Sample prior not set.");
            }

            if (Double.isNaN(params.getStructurePrior())) {
                throw new IllegalArgumentException("Structure prior not set.");
            }

            score = new BDeuScore(dataSet);
            ((BDeuScore) score).setSamplePrior(params.getSamplePrior());
            ((BDeuScore) score).setStructurePrior(params.getStructurePrior());

            params.setDataType(ComparisonParameters.DataType.Discrete);

            params.setDataType(ComparisonParameters.DataType.Discrete);
        }

        if (params.getAlgorithm() == null) {
            throw new IllegalArgumentException("Algorithm not set.");
        }

        long time1 = System.currentTimeMillis();

        if (params.getAlgorithm() == ComparisonParameters.Algorithm.PC) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            Pc search = new Pc(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.CPC) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            Cpc search = new Cpc(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.PCLocal) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            PcLocal search = new PcLocal(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.PCMax) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            PcMax search = new PcMax(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FGS) {
            if (score == null) throw new IllegalArgumentException("Score not set.");
            Fgs search = new Fgs(score);
            //search.setFaithfulnessAssumed(params.isOneEdgeFaithfulnessAssumed());
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(trueDag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FCI) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            Fci search = new Fci(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(new DagToPag(trueDag).convert());
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.GFCI) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            GFci search = new GFci(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(new DagToPag(trueDag).convert());
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.TsFCI) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            TsFci search = new TsFci(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(new TsDagToPag(trueDag).convert());
        } else {
            throw new IllegalArgumentException("Unrecognized algorithm.");
        }

        long time2 = System.currentTimeMillis();

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
    public static TextTable summarize(List<ComparisonResult> results, List<TableColumn> tableColumns) {

        List<Node> variables = new ArrayList<>();
        for (TableColumn column : tableColumns) {
            variables.add(new ContinuousVariable(column.toString()));
        }

        DataSet dataSet = new ColtDataSet(0, variables);
        dataSet.setNumberFormat(new DecimalFormat("0"));

        for (int i = 0; i < results.size(); i++) {
            System.out.println("\nRun " + (i + 1) + "\n" + results.get(i));
        }

        System.out.println();

        for (ComparisonResult _result : results) {
            Graph correctGraph = _result.getCorrectResult();
            Graph resultGraph = _result.getResultGraph();

            GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(correctGraph, resultGraph);

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

        return getTextTable(dataSet, cols, new DecimalFormat("0.00")); //deleted .toString()
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

    public enum TableColumn {AdjCor, AdjFn, AdjFp, AhdCor, AhdFn, AhdFp, SHD,
        AdjPrec, AdjRec, AhdPrec, AhdRec, Elapsed}
}
