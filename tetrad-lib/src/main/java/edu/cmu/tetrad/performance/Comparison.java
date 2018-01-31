package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.sem.ScoreType;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Does a comparison of algorithm results across algorithm type, sample sizes, etc.
 *
 * @author jdramsey 2016.03.24
 */
public class Comparison {

    /**
     * Simulates data from model paramerizing the given DAG, and runs the algorithm on that data,
     * printing out error statistics.
     */
    public static ComparisonResult compare(ComparisonParameters params) {
        DataSet dataSet;
        Graph trueDag;
        IndependenceTest test = null;
        Score score = null;

        ComparisonResult result = new ComparisonResult(params);

        if (params.getDataFile() != null) {
            dataSet = loadDataFile(params.getDataFile());

            if (params.getGraphFile() == null) {
                throw new IllegalArgumentException("True graph file not set.");
            }

            trueDag = loadGraphFile(params.getGraphFile());
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

                LargeScaleSimulation sim = new LargeScaleSimulation(trueDag);
                dataSet = sim.simulateDataFisher(params.getSampleSize());
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

        if (params.getScore() == ScoreType.SemBic) {
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
        else if (params.getScore() == ScoreType.BDeu) {
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
            result.setCorrectResult(SearchGraphUtils.patternForDag(new EdgeListGraph(trueDag)));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.CPC) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            Cpc search = new Cpc(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(new EdgeListGraph(trueDag)));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.PCLocal) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            PcLocal search = new PcLocal(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(new EdgeListGraph(trueDag)));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.PCStableMax) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            PcStableMax search = new PcStableMax(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(new EdgeListGraph(trueDag)));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FGES) {
            if (score == null) throw new IllegalArgumentException("Score not set.");
            Fges search = new Fges(score);
            search.setFaithfulnessAssumed(params.isOneEdgeFaithfulnessAssumed());
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(new EdgeListGraph(trueDag)));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FGES2) {
            if (score == null) throw new IllegalArgumentException("Score not set.");
            Fges search = new Fges(score);
            search.setFaithfulnessAssumed(params.isOneEdgeFaithfulnessAssumed());
            result.setResultGraph(search.search());
            result.setCorrectResult(SearchGraphUtils.patternForDag(new EdgeListGraph(trueDag)));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FCI) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            Fci search = new Fci(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(new DagToPag(trueDag).convert());
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.GFCI) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            GFci search = new GFci(test, score);
            result.setResultGraph(search.search());
            result.setCorrectResult(new DagToPag(trueDag).convert());
        } else {
            throw new IllegalArgumentException("Unrecognized algorithm.");
        }

        long time2 = System.currentTimeMillis();

        long elapsed = time2 - time1;
        result.setElapsed(elapsed);


        result.setTrueDag(trueDag);

        return result;
    }

    private static Graph loadGraphFile(String graphFile) {
        return null;
    }

    private static DataSet loadDataFile(String dataFile) {
        return null;
    }

    public static String summarize(List<ComparisonResult> results, List<TableColumn> tableColumns) {

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

        return getTextTable(dataSet, cols, new DecimalFormat("0.00")).toString();
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
