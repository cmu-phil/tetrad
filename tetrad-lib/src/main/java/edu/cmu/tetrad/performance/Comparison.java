package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
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
        DataSet dataSet = null;
        Graph trueDag = null;
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
            if (params.getDataType() == null) {
                throw new IllegalArgumentException("Data type not set.");
            }

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
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false);

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
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false);

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

            score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet), params.getPenaltyDiscount());

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

    public static String summarize(List<ComparisonResult> results, List<TableColumns> tableColumns) {
        ContinuousVariable adjCorrect = new ContinuousVariable("AdjCor");
        ContinuousVariable adjFn = new ContinuousVariable("AdjFn");
        ContinuousVariable adjFp = new ContinuousVariable("AdjFp");

        ContinuousVariable arrowptCorrect = new ContinuousVariable("AhdCor");
        ContinuousVariable arrowptFn = new ContinuousVariable("AhdFn");
        ContinuousVariable arrowptFp = new ContinuousVariable("AhdFp");

        ContinuousVariable adjPrec = new ContinuousVariable("AdjPrec");
        ContinuousVariable adjRec = new ContinuousVariable("AdjRec");
        ContinuousVariable arrowptPrec = new ContinuousVariable("AhdPrec");
        ContinuousVariable arrowptRec = new ContinuousVariable("AhdRec");
        ContinuousVariable shd = new ContinuousVariable("SHD");
        ContinuousVariable elapsed = new ContinuousVariable("Elapsed");

//        ContinuousVariable twoCycleCorrect = new ContinuousVariable("TC_COR");
//        ContinuousVariable twoCycleFn = new ContinuousVariable("TC_FN");
//        ContinuousVariable twoCycleFp = new ContinuousVariable("TC_FP");

        List<Node> variables = new LinkedList<Node>();
        variables.add(adjCorrect);
        variables.add(adjFn);
        variables.add(adjFp);
        variables.add(arrowptCorrect);
        variables.add(arrowptFn);
        variables.add(arrowptFp);
        variables.add(adjPrec);
        variables.add(adjRec);
        variables.add(arrowptPrec);
        variables.add(arrowptRec);
        variables.add(shd);
        variables.add(elapsed);
//        variables.add(twoCycleCorrect);
//        variables.add(twoCycleFn);
//        variables.add(twoCycleFp);

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
            dataSet.setDouble(newRow, 0, comparison.getAdjCorrect());
            dataSet.setDouble(newRow, 1, comparison.getAdjFn());
            dataSet.setDouble(newRow, 2, comparison.getAdjFp());
            dataSet.setDouble(newRow, 3, comparison.getAhdCorrect());
            dataSet.setDouble(newRow, 4, comparison.getAhdFn());
            dataSet.setDouble(newRow, 5, comparison.getAhdFp());
            dataSet.setDouble(newRow, 6, comparison.getAdjPrec());
            dataSet.setDouble(newRow, 7, comparison.getAdjRec());
            dataSet.setDouble(newRow, 8, comparison.getAhdPrec());
            dataSet.setDouble(newRow, 9, comparison.getAhdRec());
            dataSet.setDouble(newRow, 10, comparison.getShd());
            dataSet.setDouble(newRow, 11, _result.getElapsed());
        }

        int[] cols = new int[tableColumns.size()];

        for (int i = 0; i < tableColumns.size(); i++) {
            switch (tableColumns.get(i)) {
                case AdjCor:
                    cols[i] = 0;
                    break;
                case AdjFn:
                    cols[i] = 1;
                    break;
                case AdjFp:
                    cols[i] = 2;
                    break;
                case AhdCor:
                    cols[i] = 3;
                    break;
                case AhdFn:
                    cols[i] = 4;
                    break;
                case AhdFp:
                    cols[i] = 5;
                    break;
                case SHD:
                    cols[i] = 10;
                    break;
                case AdjPrec:
                    cols[i] = 7;
                    break;
                case AdjRec:
                    cols[i] = 7;
                    break;
                case AhdPrec:
                    cols[i] = 8;
                    break;
                case AhdRec:
                    cols[i] = 9;
                    break;
                case Elapsed:
                    cols[i] = 11;
                    break;
                default:
                    throw new IllegalStateException("That column has not yet been programmed in the " +
                            "tables. Bug the programmer.");

            }
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

    public enum TableColumns {AdjCor, AdjFp, AdjFn, AhdCor, AhdFn, AhdFp, SHD,
        AdjPrec, AdjRec, AhdPrec, AhdRec, Elapsed}
}
