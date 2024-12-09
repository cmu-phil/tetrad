///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.BdeuScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Contains some utilities for doing autoregression. Should probably be improved by somebody.
 *
 * @author josephramsey
 * @author danielmalinsky (some improvements)
 * @version $Id: $Id
 */
public class TsUtils {

    /**
     * This class is not meant to be instantiated.
     */
    private TsUtils() {
    }

    /**
     * <p>ar.</p>
     *
     * @param timeSeries a {@link edu.cmu.tetrad.data.DataSet} object
     * @param numLags    a int
     * @return the VAR residuals of the given time series with the given number of lags. That is, every variable at the
     * model lag is regressed onto every variable at previous lags, up to the given number of lags, and the residuals of
     * these regressions for each variable are returned.
     */
    public static DataSet ar(DataSet timeSeries, int numLags) {
        DataSet timeLags = TsUtils.createLagData(timeSeries, numLags);
        List<Node> regressors = new ArrayList<>();

        for (int i = timeSeries.getNumColumns(); i < timeLags.getNumColumns(); i++) {
            regressors.add(timeLags.getVariable(i));
        }

        Regression regression = new RegressionDataset(timeLags);
//        Regression regression = new RegressionDatasetGeneralized(timeLags);

        Matrix residuals = new Matrix(timeLags.getNumRows(), timeSeries.getNumColumns());

        for (int i = 0; i < timeSeries.getNumColumns(); i++) {
            Node target = timeLags.getVariable(i);
            RegressionResult result = regression.regress(target, regressors);
            Vector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
        }

        return new BoxDataSet(new DoubleDataBox(residuals.toArray()), timeSeries.getVariables());
    }

    /**
     * <p>ar2.</p>
     *
     * @param timeSeries a {@link edu.cmu.tetrad.data.DataSet} object
     * @param numLags    a int
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet ar2(DataSet timeSeries, int numLags) {
        List<Node> missingVariables = new ArrayList<>();

        for (Node node : timeSeries.getVariables()) {
            int index = timeSeries.getVariables().indexOf(node);
            boolean missing = true;

            for (int i = 0; i < timeSeries.getNumRows(); i++) {
                if (!Double.isNaN(timeSeries.getDouble(i, index))) {
                    missing = false;
                    break;
                }
            }

            if (missing) {
                missingVariables.add(node);
            }
        }

        DataSet timeLags = TsUtils.createLagData(timeSeries, numLags);

        Regression regression = new RegressionDataset(timeLags);

        Matrix residuals = new Matrix(timeLags.getNumRows(), timeSeries.getNumColumns());

        for (int i = 0; i < timeSeries.getNumColumns(); i++) {
            Node target = timeLags.getVariable(i);
            int index = timeSeries.getVariables().indexOf(target);

            if (missingVariables.contains(target)) {
                for (int i2 = 0; i2 < residuals.getNumRows(); i2++) {
                    residuals.set(i2, index, Double.NaN);
                }

                continue;
            }

            List<Node> regressors = new ArrayList<>();

            for (int i2 = timeSeries.getNumColumns(); i2 < timeLags.getNumColumns(); i2++) {
                int varIndex = i2 % timeSeries.getNumColumns();
                Node var = timeSeries.getVariable(varIndex);

                if (missingVariables.contains(var)) {
                    continue;
                }

                regressors.add(timeLags.getVariable(i2));
            }

            RegressionResult result = regression.regress(target, regressors);
            Vector residualsColumn = result.getResiduals();
            residuals.assignColumn(i, residualsColumn);
        }

        return new BoxDataSet(new DoubleDataBox(residuals.toArray()), timeSeries.getVariables());
    }

    /**
     * <p>structuralVar.</p>
     *
     * @param timeSeries a {@link edu.cmu.tetrad.data.DataSet} object
     * @param numLags    a int
     * @return a {@link edu.cmu.tetrad.search.utils.TsUtils.VarResult} object
     */
    public static VarResult structuralVar(DataSet timeSeries, int numLags) throws InterruptedException {
        DataSet timeLags = TsUtils.createLagData(timeSeries, numLags);
        Knowledge knowledge = timeLags.getKnowledge().copy();

        for (int i = 0; i <= numLags; i++) {
            knowledge.setTierForbiddenWithin(i, true);
        }

        Score score;

        if (timeLags.isDiscrete()) {
            score = new BdeuScore(timeLags);
        } else if (timeLags.isContinuous()) {
            SemBicScore semBicScore = new SemBicScore(new CovarianceMatrix(timeLags));
            semBicScore.setPenaltyDiscount(2.0);
            score = semBicScore;
        } else {
            throw new IllegalArgumentException("Mixed data set");
        }

        Fges search = new Fges(score);
        search.setKnowledge(knowledge);
        Graph graph = search.search();

        // want to collapse graph here...
        Graph collapsedVarGraph = new EdgeListGraph(timeSeries.getVariables());

        for (Edge edge : graph.getEdges()) {
            String node1_before = edge.getNode1().getName();
            String node2_before = edge.getNode2().getName();

            String node1_after = node1_before.substring(0, node1_before.indexOf("."));
            String node2_after = node2_before.substring(0, node2_before.indexOf("."));

            Node node1 = collapsedVarGraph.getNode(node1_after);
            Node node2 = collapsedVarGraph.getNode(node2_after);

            Edge _edge = new Edge(node1, node2, edge.getEndpoint1(), edge.getEndpoint2());

            if (!collapsedVarGraph.containsEdge(_edge)) {
                collapsedVarGraph.addEdge(_edge);
            }
        }

        Matrix residuals = new Matrix(timeLags.getNumRows(), timeSeries.getNumColumns());
        Regression regression = new RegressionDataset(timeLags);

        for (int i = 0; i < timeSeries.getNumColumns(); i++) {
            Node target = timeLags.getVariable(i);

            List<Node> regressors = new ArrayList<>();

            // Collect up parents from each lagged variable behind
            // timelags.getVariable(i).
            for (int j = 0; j <= 0 /*numLags*/; j++) {
                Node variable = timeLags.getVariable(i + j * timeSeries.getNumColumns());
                regressors.addAll(graph.getParents(variable));
            }

            RegressionResult result = regression.regress(target, regressors);
            Vector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
        }

        return new VarResult(new BoxDataSet(new DoubleDataBox(residuals.toArray()), timeSeries.getVariables()),
                collapsedVarGraph);
    }

    /**
     * <p>createShiftedData.</p>
     *
     * @param data   a {@link edu.cmu.tetrad.data.DataSet} object
     * @param shifts an array of  objects
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet createShiftedData(DataSet data, int[] shifts) {
        Matrix data2 = data.getDoubleData();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int shift : shifts) {
            if (shift < min) {
                min = shift;
            }
            if (shift > max) {
                max = shift;
            }
        }

        int shiftRange = max - min;

        int[] _shifts = new int[shifts.length];

        for (int i = 0; i < shifts.length; i++) {
            _shifts[i] = shiftRange - (shifts[i] - min);
        }

        if (shiftRange > data2.getNumRows()) {
            throw new IllegalArgumentException("Range of shifts greater than sample size.");
        }

        int shiftedDataLength = data2.getNumRows() - shiftRange;
        Matrix shiftedData = new Matrix(shiftedDataLength, data2.getNumColumns());

        for (int j = 0; j < shiftedData.getNumColumns(); j++) {
            for (int i = 0; i < shiftedDataLength; i++) {
                shiftedData.set(i, j, data2.get(i + _shifts[j], j));
            }
        }

        return new BoxDataSet(new DoubleDataBox(shiftedData.toArray()), data.getVariables());
    }

    /**
     * <p>getSelfLoopCoefs.</p>
     *
     * @param timeSeries a {@link edu.cmu.tetrad.data.DataSet} object
     * @return an array of  objects
     */
    public static double[] getSelfLoopCoefs(DataSet timeSeries) {
        DataSet timeLags = TsUtils.createLagData(timeSeries, 1);

        double[] coefs = new double[timeSeries.getNumColumns()];

        for (int j = 0; j < timeSeries.getNumColumns(); j++) {
            Node target = timeLags.getVariable(j);
            Node selfLoop = timeLags.getVariable(j + timeSeries.getNumColumns());
            List<Node> regressors = Collections.singletonList(selfLoop);

            Regression regression = new RegressionDataset(timeLags);
            RegressionResult result = regression.regress(target, regressors);
            coefs[j] = result.getCoef()[1];
        }

        return coefs;
    }

    /**
     * <p>sumOfArCoefficients.</p>
     *
     * @param timeSeries a {@link edu.cmu.tetrad.data.DataSet} object
     * @param numLags    a int
     * @return a double
     */
    public static double sumOfArCoefficients(DataSet timeSeries, int numLags) {
        DataSet timeLags = TsUtils.createLagData(timeSeries, numLags);
        List<Node> regressors = new ArrayList<>();

        for (int i = timeSeries.getNumColumns(); i < timeLags.getNumColumns(); i++) {
            regressors.add(timeLags.getVariable(i));
        }

        Regression regression = new RegressionDataset(timeLags);
        Matrix residuals = new Matrix(timeLags.getNumRows(), timeSeries.getNumColumns());

        double sum = 0.0;
        int n = 0;

        for (int i = 0; i < timeSeries.getNumColumns(); i++) {
            Node target = timeLags.getVariable(i);
            RegressionResult result = regression.regress(target, regressors);

            double[] coef = result.getCoef();

            for (double v : coef) {
                sum += v * v;
                n++;
            }

            Vector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
        }

        return sum / n;
    }

    /**
     * Calculates the dth difference of the given data. If d = 0, the original data is returned. If d = 1, the data
     * (with one fewer rows) is returned, with each row subtracted from its successor. If d = 1, the same operation is
     * applied to the result of d = 1. And so on.
     *
     * @param data the data to be differenced.
     * @param d    the number of differences to take, &gt;= 0.
     * @return the differenced data.
     */
    public static DataSet difference(DataSet data, int d) {
        if (d == 0) {
            return data;
        }

        Matrix _data = data.getDoubleData();

        for (int k = 1; k <= d; k++) {
            Matrix _data2 = new Matrix(_data.getNumRows() - 1, _data.getNumColumns());

            for (int i = 1; i < _data.getNumRows(); i++) {
                for (int j = 0; j < _data.getNumColumns(); j++) {
                    _data2.set(i - 1, j, _data.get(i, j) - _data.get(i - 1, j));
                }
            }

            _data = _data2;
        }

        return new BoxDataSet(new DoubleDataBox(_data.toArray()), data.getVariables());
    }

    /**
     * Creates new time series dataset from the given one (fixed to deal with mixed datasets)
     *
     * @param data    a {@link edu.cmu.tetrad.data.DataSet} object
     * @param numLags a int
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet createLagData(DataSet data, int numLags) {
        List<Node> variables = data.getVariables();
        int dataSize = variables.size();
        int laggedRows = data.getNumRows() - numLags;
        Knowledge knowledge = new Knowledge();
        Node[][] laggedNodes = new Node[numLags + 1][dataSize];
        List<Node> newVariables = new ArrayList<>((numLags + 1) * dataSize + 1);

        for (int lag = 0; lag <= numLags; lag++) {
            for (int col = 0; col < dataSize; col++) {
                Node node = variables.get(col);
                String varName = node.getName();
                Node laggedNode;
                String name = varName;

                if (lag != 0) {
                    name = name + ":" + lag;
                }

                if (node instanceof ContinuousVariable) {
                    laggedNode = new ContinuousVariable(name);
                } else if (node instanceof DiscreteVariable var) {
                    laggedNode = new DiscreteVariable(var);
                    laggedNode.setName(name);
                } else {
                    throw new IllegalStateException("Node must be either continuous or discrete");
                }
                newVariables.add(laggedNode);
                laggedNode.setCenter(80 * col + 50, 80 * (numLags - lag) + 50);
                laggedNodes[lag][col] = laggedNode;
//                knowledge.addToTier(numLags - lag, laggedNode.getName());
            }
        }

        try {
            for (Node node : newVariables) {
                String varName = node.getName();
                String tmp;
                int lag;
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
        } catch (NumberFormatException e) {
            return data;
        }

        DataSet laggedData = new BoxDataSet(new DoubleDataBox(laggedRows, newVariables.size()), newVariables);
        for (int lag = 0; lag <= numLags; lag++) {
            for (int col = 0; col < dataSize; col++) {
                for (int row = 0; row < laggedRows; row++) {
                    Node laggedNode = laggedNodes[lag][col];
                    if (laggedNode instanceof ContinuousVariable) {
                        double value = data.getDouble(row + numLags - lag, col);
                        laggedData.setDouble(row, col + lag * dataSize, value);
                    } else {
                        int value = data.getInt(row + numLags - lag, col);
                        laggedData.setInt(row, col + lag * dataSize, value);
                    }
                }
            }
        }

        knowledge.setDefaultToKnowledgeLayout(true);
//        knowledge.setLagged(true);
        laggedData.setKnowledge(knowledge);
//        laggedData.setName(data.getNode());
        return laggedData;
    }

    /**
     * Creates new time series dataset from the given one with index variable (e.g., time)
     *
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet addIndex(DataSet data) {
        data = data.copy();
        ContinuousVariable timeVar = new ContinuousVariable("Time");
        data.addVariable(timeVar);
        int c = data.getColumn(timeVar);

        for (int r = 0; r < data.getNumRows(); r++) {
            data.setDouble(r, c, (r + 1));
        }

        return data;


    }

    /**
     * <p>graphToLagGraph.</p>
     *
     * @param _graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param numLags a int
     * @return a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public static TimeLagGraph graphToLagGraph(Graph _graph, int numLags) {
        TimeLagGraph graph = new TimeLagGraph();
        graph.setMaxLag(numLags);

        for (Node node : _graph.getNodes()) {
            Node graphNode = new ContinuousVariable(node.getName());
            graphNode.setNodeType(node.getNodeType());
            graph.addNode(graphNode);

            /* adding node from Lag 1 to Lag 0 for every node */
            Node from = graph.getNode(node.getName(), 1);
            Node to = graph.getNode(node.getName(), 0);
            Edge edge = new Edge(from, to, Endpoint.TAIL, Endpoint.ARROW);
            graph.addEdge(edge);
            //graph.addDirectedEdge(from, to);
        }

        for (Edge edge : _graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) {
                throw new IllegalArgumentException();
            }

            Node from = edge.getNode1();
            Node to = edge.getNode2();
            Node _from = graph.getNode(from.getName(), 0);
            Node _to = graph.getNode(to.getName(), 0);
            Edge edge1 = new Edge(_from, _to, Endpoint.TAIL, Endpoint.ARROW);
            graph.addEdge(edge1);
            //graph.addDirectedEdge(_from, _to);
        }

        //for lag
        // for node
        //  with probability 0.3 add edge from node to *random* node at lag0
        for (int lag = 1; lag <= numLags; lag++) {
            for (Node node1 : graph.getLag0Nodes()) {
                Node from = graph.getNode(node1.getName(), lag);
                for (Node node2 : graph.getLag0Nodes()) {
                    Node to = graph.getNode(node2.getName(), 0);
                    if (node1.getName().equals(node2.getName())) {
                        continue;
                    }
                    if (RandomUtil.getInstance().nextUniform(0, 1) <= 0.15) {
                        Edge edge = new Edge(from, to, Endpoint.TAIL, Endpoint.ARROW);
                        graph.addEdge(edge);
                        //graph.addDirectedEdge(from, to);
                    }
                } // for node at lag0 (to)
            } // for node at lag (from)
        } // for lag

        return graph;
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
     * <p>getKnowledge.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public static Knowledge getKnowledge(Graph graph) {
//        System.out.println("Entering getKnowledge ... ");
        int numLags = 1; // need to fix this!
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
        Collections.sort(variables, new Comparator<Node>() {
            @Override
            public int compare(Node o1, Node o2) {
                String name1 = TsUtils.getNameNoLag(o1);
                String name2 = TsUtils.getNameNoLag(o2);

                String prefix1 = TsUtils.getPrefix(name1);
                String prefix2 = TsUtils.getPrefix(name2);

                int index1 = TsUtils.getIndex(name1);
                int index2 = TsUtils.getIndex(name2);

                if (TsUtils.getLag(o1.getName()) == TsUtils.getLag(o2.getName())) {
                    if (prefix1.compareTo(prefix2) == 0) {
                        return Integer.compare(index1, index2);
                    } else {
                        return prefix1.compareTo(prefix2);
                    }
                } else {
                    return TsUtils.getLag(o1.getName()) - TsUtils.getLag(o2.getName());
                }
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
     * <p>allEigenvaluesAreSmallerThanOneInModulus.</p>
     *
     * @param mat a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a boolean
     */
    public static boolean allEigenvaluesAreSmallerThanOneInModulus(Matrix mat) {

        double[] realEigenvalues = new double[0];
        double[] imagEigenvalues = new double[0];
        try {
            EigenDecomposition dec = new EigenDecomposition(MatrixUtils.createRealMatrix(mat.toArray()));
            realEigenvalues = dec.getRealEigenvalues();
            imagEigenvalues = dec.getImagEigenvalues();
        } catch (MaxCountExceededException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < realEigenvalues.length; i++) {
            double realEigenvalue = realEigenvalues[i];
            double imagEigenvalue = imagEigenvalues[i];
            System.out.println("Real eigenvalues are : " + realEigenvalue + " and imag part : " + imagEigenvalue);
            double modulus = FastMath.sqrt(FastMath.pow(realEigenvalue, 2) + FastMath.pow(imagEigenvalue, 2));

            if (modulus >= 1.0) {
                return false;
            }
        }
        return true;
    }

    private int[] eliminateMissing(int[] parents, int dataIndex, DataSet dataSet, List<Node> missingVariables) {
        List<Integer> _parents = new ArrayList<>();

        for (int k : parents) {
            if (!missingVariables.contains(dataSet.getVariable(k))) {
                _parents.add(k);
            }
        }

        int[] _parents2 = new int[_parents.size()];

        for (int i = 0; i < _parents.size(); i++) {
            _parents2[i] = _parents.get(i);
        }

        return _parents2;
    }

    /**
     * Gives a result consisting of the residuals and collapsed var graphs.
     */
    public static class VarResult {

        /**
         * Residuals from the VAR model.
         */
        private final DataSet residuals;

        /**
         * Collapsed var graph.
         */
        private final Graph collapsedVarGraph;

        /**
         * Constructs a new result.
         *
         * @param dataSet           a {@link edu.cmu.tetrad.data.DataSet} object
         * @param collapsedVarGraph a {@link edu.cmu.tetrad.graph.Graph} object
         */
        public VarResult(DataSet dataSet, Graph collapsedVarGraph) {
            this.residuals = dataSet;
            this.collapsedVarGraph = collapsedVarGraph;
        }

        /**
         * <p>Getter for the field <code>residuals</code>.</p>
         *
         * @return a {@link edu.cmu.tetrad.data.DataSet} object
         */
        public DataSet getResiduals() {
            return this.residuals;
        }

        /**
         * <p>Getter for the field <code>collapsedVarGraph</code>.</p>
         *
         * @return a {@link edu.cmu.tetrad.graph.Graph} object
         */
        public Graph getCollapsedVarGraph() {
            return this.collapsedVarGraph;
        }
    }

}
