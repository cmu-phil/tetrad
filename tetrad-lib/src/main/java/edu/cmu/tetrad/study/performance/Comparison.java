///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.study.performance;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.FgesFci;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.score.BdeuScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.sem.ScoreType;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Does a comparison of algorithm results across algorithm type, sample sizes, etc.
 *
 * @author josephramsey 2016.03.24
 * @version $Id: $Id
 */
public class Comparison {

    /**
     * Private constructor to prevent instantiation.
     */
    private Comparison() {
    }

    /**
     * Simulates data from model paramerizing the given DAG, and runs the algorithm on that data, printing out error
     * statistics.
     *
     * @param params a {@link edu.cmu.tetrad.study.performance.ComparisonParameters} object
     * @return a {@link edu.cmu.tetrad.study.performance.ComparisonResult} object
     * @throws InterruptedException if any
     */
    public static ComparisonResult compare(ComparisonParameters params) throws InterruptedException {
        DataSet dataSet;
        Graph trueDag;
        IndependenceTest test = null;
        Score score = null;

        ComparisonResult result = new ComparisonResult(params);

        if (params.getDataFile() != null) {
            dataSet = Comparison.loadDataFile();

            if (params.getGraphFile() == null) {
                throw new IllegalArgumentException("True graph file not set.");
            }

            trueDag = Comparison.loadGraphFile();
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
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false, true, -1);

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

                trueDag = RandomGraph.randomGraphRandomForwardEdges(
                        nodes, 0, params.getNumEdges(), 10, 10, 10, false, true, -1);

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
            if (test == null) throw new IllegalArgumentException("Test not set.");
            Pc search = new Pc(test);
            result.setResultGraph(search.search());
            Graph dag = new EdgeListGraph(trueDag);
            result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.CPC) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            Pc search = new Pc(test);
            search.setColliderOrientationStyle(Pc.ColliderOrientationStyle.CONSERVATIVE);
            result.setResultGraph(search.search());
            Graph dag = new EdgeListGraph(trueDag);
            result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FGES) {
            if (score == null) throw new IllegalArgumentException("Score not set.");
            Fges search = new Fges(score);
            search.setFaithfulnessAssumed(params.isOneEdgeFaithfulnessAssumed());
            result.setResultGraph(search.search());
            Graph dag = new EdgeListGraph(trueDag);
            result.setCorrectResult(GraphTransforms.dagToCpdag(dag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FCI) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            Fci search = new Fci(test);
            result.setResultGraph(search.search());
            result.setCorrectResult(GraphTransforms.dagToPag(trueDag));
        } else if (params.getAlgorithm() == ComparisonParameters.Algorithm.FGES_FCI) {
            if (test == null) throw new IllegalArgumentException("Test not set.");
            FgesFci search = new FgesFci(test, score);
            result.setResultGraph(search.search());
            result.setCorrectResult(GraphTransforms.dagToPag(trueDag));
        } else {
            throw new IllegalArgumentException("Unrecognized algorithm.");
        }

        long time2 = MillisecondTimes.timeMillis();

        long elapsed = time2 - time1;
        result.setElapsed(elapsed);


        result.setTrueDag(trueDag);

        return result;
    }

    private static Graph loadGraphFile() {
        return null;
    }

    private static DataSet loadDataFile() {
        return null;
    }

    /**
     * <p>summarize.</p>
     *
     * @param results      a {@link java.util.List} object
     * @param tableColumns a {@link java.util.List} object
     * @return a {@link java.lang.String} object
     */
    public static String summarize(List<ComparisonResult> results, List<TableColumn> tableColumns) {

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

        return Comparison.getTextTable(dataSet, cols, new DecimalFormat("0.00")).toString();
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
     * An enumeration of the columns in the comparison table.
     */
    public enum TableColumn {

        /**
         * The number of adjacency correct edges.
         */
        AdjCor,

        /**
         * The number of adjacency false negative edges.
         */
        AdjFn,

        /**
         * The number of adjacency false positive edges.
         */
        AdjFp,

        /**
         * The number of arrowhead false positive edges.
         */
        AhdCor,

        /**
         * The number of arrowhead false negative edges.
         */
        AhdFn,

        /**
         * The number of arrowhead false positive edges.
         */
        AhdFp,

        /**
         * The structural Hamming distance.
         */
        SHD,

        /**
         * The adjacency precision.
         */
        AdjPrec,

        /**
         * The adjacency recall.
         */
        AdjRec,

        /**
         * The arrowhead precision.
         */
        AhdPrec,

        /**
         * The arrowhead recall.
         */
        AhdRec,

        /**
         * The elapsed time.
         */
        Elapsed
    }
}

