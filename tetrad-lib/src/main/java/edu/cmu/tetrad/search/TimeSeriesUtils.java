///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.linear.EigenDecomposition;

/**
 * Contains some utilities for doing autoregression. Should probably be improved
 * by somebody.
 *
 * @author Joseph Ramsey
 * @author Daniel Malinsky (some improvements)
 */
public class TimeSeriesUtils {

    /**
     * @return the VAR residuals of the given time series with the given number
     * of lags. That is, every variable at the model lag is regressed onto every
     * variable at previous lags, up to the given number of lags, and the
     * residuals of these regressions for each variable are returned.
     */
    public static DataSet ar(DataSet timeSeries, int numLags) {
        DataSet timeLags = createLagData(timeSeries, numLags);
        List<Node> regressors = new ArrayList<>();

        for (int i = timeSeries.getNumColumns(); i < timeLags.getNumColumns(); i++) {
            regressors.add(timeLags.getVariable(i));
        }

        Regression regression = new RegressionDataset(timeLags);
//        Regression regression = new RegressionDatasetGeneralized(timeLags);

        TetradMatrix residuals = new TetradMatrix(timeLags.getNumRows(), timeSeries.getNumColumns());

        for (int i = 0; i < timeSeries.getNumColumns(); i++) {
            Node target = timeLags.getVariable(i);
            RegressionResult result = regression.regress(target, regressors);
            TetradVector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
        }

        return ColtDataSet.makeContinuousData(timeSeries.getVariables(), residuals);
    }

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

        DataSet timeLags = createLagData(timeSeries, numLags);

        Regression regression = new RegressionDataset(timeLags);

        TetradMatrix residuals = new TetradMatrix(timeLags.getNumRows(), timeSeries.getNumColumns());

        for (int i = 0; i < timeSeries.getNumColumns(); i++) {
            Node target = timeLags.getVariable(i);
            int index = timeSeries.getVariables().indexOf(target);

            if (missingVariables.contains(target)) {
                for (int i2 = 0; i2 < residuals.rows(); i2++) {
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
            TetradVector residualsColumn = result.getResiduals();
            residuals.assignColumn(i, residualsColumn);
        }

        return ColtDataSet.makeContinuousData(timeSeries.getVariables(), residuals);
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

    public static VarResult structuralVar(DataSet timeSeries, int numLags) {
        DataSet timeLags = TimeSeriesUtils.createLagData(timeSeries, numLags);
        IKnowledge knowledge = timeLags.getKnowledge().copy();

        for (int i = 0; i <= numLags; i++) {
            knowledge.setTierForbiddenWithin(i, true);
        }

        Score score;

        if (timeLags.isDiscrete()) {
            score = new BDeuScore(timeLags);
        } else if (timeLags.isContinuous()) {
            SemBicScore semBicScore = new SemBicScore(new CovarianceMatrixOnTheFly(timeLags));
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

        TetradMatrix residuals = new TetradMatrix(timeLags.getNumRows(), timeSeries.getNumColumns());
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
            TetradVector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
        }

        return new VarResult(ColtDataSet.makeContinuousData(timeSeries.getVariables(), residuals),
                collapsedVarGraph);
    }

    public static DataSet createShiftedData(DataSet data, int[] shifts) {
        TetradMatrix data2 = data.getDoubleData();

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int i1 = 0; i1 < shifts.length; i1++) {
            if (shifts[i1] < min) {
                min = shifts[i1];
            }
            if (shifts[i1] > max) {
                max = shifts[i1];
            }
        }

        int shiftRange = max - min;

        int[] _shifts = new int[shifts.length];

        for (int i = 0; i < shifts.length; i++) {
            _shifts[i] = shiftRange - (shifts[i] - min);
        }

        if (shiftRange > data2.rows()) {
            throw new IllegalArgumentException("Range of shifts greater than sample size.");
        }

        int shiftedDataLength = data2.rows() - shiftRange;
        TetradMatrix shiftedData = new TetradMatrix(shiftedDataLength, data2.columns());

        for (int j = 0; j < shiftedData.columns(); j++) {
            for (int i = 0; i < shiftedDataLength; i++) {
                shiftedData.set(i, j, data2.get(i + _shifts[j], j));
            }
        }

        return ColtDataSet.makeContinuousData(data.getVariables(), shiftedData);
    }

    public static class VarResult {

        private DataSet residuals;
        private Graph collapsedVarGraph;

        public VarResult(DataSet dataSet, Graph collapsedVarGraph) {
            this.residuals = dataSet;
            this.collapsedVarGraph = collapsedVarGraph;
        }

        public DataSet getResiduals() {
            return residuals;
        }

        public Graph getCollapsedVarGraph() {
            return collapsedVarGraph;
        }
    }

    public static double[] getSelfLoopCoefs(DataSet timeSeries) {
        DataSet timeLags = createLagData(timeSeries, 1);

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

    public static double sumOfArCoefficients(DataSet timeSeries, int numLags) {
        DataSet timeLags = createLagData(timeSeries, numLags);
        List<Node> regressors = new ArrayList<>();

        for (int i = timeSeries.getNumColumns(); i < timeLags.getNumColumns(); i++) {
            regressors.add(timeLags.getVariable(i));
        }

        Regression regression = new RegressionDataset(timeLags);
        TetradMatrix residuals = new TetradMatrix(timeLags.getNumRows(), timeSeries.getNumColumns());

        double sum = 0.0;
        int n = 0;

        for (int i = 0; i < timeSeries.getNumColumns(); i++) {
            Node target = timeLags.getVariable(i);
            RegressionResult result = regression.regress(target, regressors);

            double[] coef = result.getCoef();

            for (int k = 0; k < coef.length; k++) {
                sum += coef[k] * coef[k];
                n++;
            }

            TetradVector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
        }

        return sum / n;
    }

    /**
     * Calculates the dth difference of the given data. If d = 0, the original
     * data is returned. If d = 1, the data (with one fewer rows) is returned,
     * with each row subtracted from its successor. If d = 1, the same operation
     * is applied to the result of d = 1. And so on.
     *
     * @param data the data to be differenced.
     * @param d the number of differences to take, >= 0.
     * @return the differenced data.
     */
    public static DataSet difference(DataSet data, int d) {
        if (d == 0) {
            return data;
        }

        TetradMatrix _data = data.getDoubleData();

        for (int k = 1; k <= d; k++) {
            TetradMatrix _data2 = new TetradMatrix(_data.rows() - 1, _data.columns());

            for (int i = 1; i < _data.rows(); i++) {
                for (int j = 0; j < _data.columns(); j++) {
                    _data2.set(i - 1, j, _data.get(i, j) - _data.get(i - 1, j));
                }
            }

            _data = _data2;
        }

        return ColtDataSet.makeContinuousData(data.getVariables(), _data);
    }

    /**
     * Creates new time series dataset from the given one (fixed to deal with
     * mixed datasets)
     */
    public static DataSet createLagData(DataSet data, int numLags) {
        List<Node> variables = data.getVariables();
        int dataSize = variables.size();
        int laggedRows = data.getNumRows() - numLags;
        IKnowledge knowledge = new Knowledge2();
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
                } else if (node instanceof DiscreteVariable) {
                    DiscreteVariable var = (DiscreteVariable) node;
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

////        System.out.println("Variable list before the sort = " + newVariables);
//        Collections.sort(newVariables, new Comparator<Node>() {
//            @Override
//            public int compare(Node o1, Node o2) {
//                String name1 = getNameNoLag(o1);
//                String name2 = getNameNoLag(o2);
//
////                System.out.println("name 1 = " + name1);
////                System.out.println("name 2 = " + name2);
//                String prefix1 = getPrefix(name1);
//                String prefix2 = getPrefix(name2);
//
////                System.out.println("prefix 1 = " + prefix1);
////                System.out.println("prefix 2 = " + prefix2);
//                int index1 = getIndex(name1);
//                int index2 = getIndex(name2);
//
////                System.out.println("index 1 = " + index1);
////                System.out.println("index 2 = " + index2);
//                if (getLag(o1.getName()) == getLag(o2.getName())) {
//                    if (prefix1.compareTo(prefix2) == 0) {
//                        return Integer.compare(index1, index2);
//                    } else {
//                        return prefix1.compareTo(prefix2);
//                    }
//
//                } else {
//                    return getLag(o1.getName()) - getLag(o2.getName());
//                }
//            }
//        });

//        System.out.println("Variable list after the sort = " + newVariables);
        for (Node node : newVariables) {
            String varName = node.getName();
            String tmp;
            int lag;
            if (varName.indexOf(':') == -1) {
                lag = 0;
//                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1, varName.length());
                lag = Integer.parseInt(tmp);
//                laglist.add(lag);
            }
            knowledge.addToTier(numLags - lag, node.getName());
        }

        DataSet laggedData = new ColtDataSet(laggedRows, newVariables);
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
     * Creates new time series dataset from the given one with index variable
     * (e.g., time)
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


//        List<Node> variables = data.getVariables();
//        int dataSize = variables.size();
//        int laggedRows = data.getNumRows() - numLags;
//        IKnowledge knowledge = new Knowledge2();
//        Node[][] laggedNodes = new Node[numLags + 1][dataSize];
//        List<Node> newVariables = new ArrayList<>((numLags + 1) * dataSize + 2); // added 1 to this
//
//        for (int lag = 0; lag <= numLags; lag++) {
//            for (int col = 0; col < dataSize; col++) {
//                Node node = variables.get(col);
//                String varName = node.getName();
//                Node laggedNode;
//                String name = varName;
//
//                if (lag != 0) {
//                    name = name + ":" + lag;
//                }
//
//                if (node instanceof ContinuousVariable) {
//                    laggedNode = new ContinuousVariable(name);
//                } else if (node instanceof DiscreteVariable) {
//                    DiscreteVariable var = (DiscreteVariable) node;
//                    laggedNode = new DiscreteVariable(var);
//                    laggedNode.setName(name);
//                } else {
//                    throw new IllegalStateException("Node must be either continuous or discrete");
//                }
//                newVariables.add(laggedNode);
//                laggedNode.setCenter(80 * col + 50, 80 * (numLags - lag) + 50);
//                laggedNodes[lag][col] = laggedNode;
////                knowledge.addToTier(numLags - lag + 1, laggedNode.getName());
//            }
//        }
//
//        String name = "time";
//        Node indexNode = new ContinuousVariable(name);
//        indexNode.setName(name);
//        newVariables.add(indexNode);
//        indexNode.setCenter(50, 80 * (numLags - 1) + 50);
//        knowledge.addToTier(0, indexNode.getName());
//
//        //        System.out.println("Variable list before the sort = " + variables);
//        Collections.sort(newVariables, new Comparator<Node>() {
//            @Override
//            public int compare(Node o1, Node o2) {
//                String name1 = getNameNoLag(o1);
//                String name2 = getNameNoLag(o2);
//
////                System.out.println("name 1 = " + name1);
////                System.out.println("name 2 = " + name2);
//                String prefix1 = getPrefix(name1);
//                String prefix2 = getPrefix(name2);
//
////                System.out.println("prefix 1 = " + prefix1);
////                System.out.println("prefix 2 = " + prefix2);
//                int index1 = getIndex(name1);
//                int index2 = getIndex(name2);
//
////                System.out.println("index 1 = " + index1);
////                System.out.println("index 2 = " + index2);
//                if (getLag(o1.getName()) == getLag(o2.getName())) {
//                    if (prefix1.compareTo(prefix2) == 0) {
//                        return Integer.compare(index1, index2);
//                    } else {
//                        return prefix1.compareTo(prefix2);
//                    }
//
//                } else {
//                    return getLag(o1.getName()) - getLag(o2.getName());
//                }
//            }
//        });
//
////        System.out.println("Variable list after the sort = " + variables);
//        for (Node node : newVariables) {
//            String varName = node.getName();
//            if (varName.equals("time")) {
//                continue;
//            }
//            String tmp;
//            int lag;
//            if (varName.indexOf(':') == -1) {
//                lag = 0;
////                laglist.add(lag);
//            } else {
//                tmp = varName.substring(varName.indexOf(':') + 1, varName.length());
//                lag = Integer.parseInt(tmp);
////                laglist.add(lag);
//            }
//            knowledge.addToTier(numLags - lag + 1, node.getName());
//        }
//
//        DataSet laggedData = new ColtDataSet(laggedRows, newVariables);
//        for (int lag = 0; lag <= numLags; lag++) {
//            for (int col = 0; col < dataSize; col++) {
//                for (int row = 0; row < laggedRows; row++) {
//                    Node laggedNode = laggedNodes[lag][col];
//                    if (laggedNode instanceof ContinuousVariable) {
//                        double value = data.getDouble(row + numLags - lag, col);
//                        laggedData.setDouble(row, col + lag * dataSize, value);
//                    } else {
//                        int value = data.getInt(row + numLags - lag, col);
//                        laggedData.setInt(row, col + lag * dataSize, value);
//                    }
//                }
//            }
//        }
//
//        // fill indexNode with for loop over rows
//        for (int row = 0; row < laggedRows; row++) {
//            laggedData.setDouble(row, dataSize + numLags * dataSize, row + 1);
//        }
//
//        knowledge.setDefaultToKnowledgeLayout(true);
//        laggedData.setKnowledge(knowledge);
//        System.out.println("Knowledge set to : " + knowledge);
//        return laggedData;
    }

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
//            System.out.println("From node = " + from.getName());
//            System.out.println("To node = " + to.getName());
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

    public static String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else {
            return tempS.substring(0, tempS.indexOf(':'));
        }
    }

    public static String getPrefix(String s) {
//        int y = 0;
//        for (int i = s.length() - 1; i >= 0; i--) {
//            try {
//                y = Integer.parseInt(s.substring(i));
//            } catch (NumberFormatException e) {
//                return s.substring(0, y);
//            }
//        }
//
//        throw new IllegalArgumentException("Not character prefix.");

//        if(s.indexOf(':')== -1) return s;
//        String tmp = s.substring(0,s.indexOf(':')-1);
//        return tmp;
        return s.substring(0, 1);
    }

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

    public static int getLag(String s) {
        if (s.indexOf(':') == -1) {
            return 0;
        }
        String tmp = s.substring(s.indexOf(':') + 1, s.length());
        return (Integer.parseInt(tmp));
    }

    public static IKnowledge getKnowledge(Graph graph) {
//        System.out.println("Entering getKnowledge ... ");
        int numLags = 1; // need to fix this!
        List<Node> variables = graph.getNodes();
        List<Integer> laglist = new ArrayList<>();
        IKnowledge knowledge = new Knowledge2();
        int lag;
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1, varName.length());
                lag = Integer.parseInt(tmp);
                laglist.add(lag);
            }
        }
        numLags = Collections.max(laglist);

//        System.out.println("Variable list before the sort = " + variables);
        Collections.sort(variables, new Comparator<Node>() {
            @Override
            public int compare(Node o1, Node o2) {
                String name1 = getNameNoLag(o1);
                String name2 = getNameNoLag(o2);

//                System.out.println("name 1 = " + name1);
//                System.out.println("name 2 = " + name2);
                String prefix1 = getPrefix(name1);
                String prefix2 = getPrefix(name2);

//                System.out.println("prefix 1 = " + prefix1);
//                System.out.println("prefix 2 = " + prefix2);
                int index1 = getIndex(name1);
                int index2 = getIndex(name2);

//                System.out.println("index 1 = " + index1);
//                System.out.println("index 2 = " + index2);
                if (getLag(o1.getName()) == getLag(o2.getName())) {
                    if (prefix1.compareTo(prefix2) == 0) {
                        return Integer.compare(index1, index2);
                    } else {
                        return prefix1.compareTo(prefix2);
                    }
                } else {
                    return getLag(o1.getName()) - getLag(o2.getName());
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
                tmp = varName.substring(varName.indexOf(':') + 1, varName.length());
                lag = Integer.parseInt(tmp);
//                laglist.add(lag);
            }
            knowledge.addToTier(numLags - lag, node.getName());
        }

        //System.out.println("Knowledge in graph = " + knowledge);
        return knowledge;
    }

    public static boolean allEigenvaluesAreSmallerThanOneInModulus(TetradMatrix mat) {

        double[] realEigenvalues = new double[0];
        double[] imagEigenvalues = new double[0];
        try {
            EigenDecomposition dec = new EigenDecomposition(mat.getRealMatrix());
            realEigenvalues = dec.getRealEigenvalues();
            imagEigenvalues = dec.getImagEigenvalues();
        } catch (MaxCountExceededException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < realEigenvalues.length; i++) {
            double realEigenvalue = realEigenvalues[i];
            double imagEigenvalue = imagEigenvalues[i];
            System.out.println("Real eigenvalues are : " + realEigenvalue + " and imag part : " + imagEigenvalue);
            double modulus = Math.sqrt(Math.pow(realEigenvalue, 2) + Math.pow(imagEigenvalue, 2));

            if (modulus >= 1.0) {
                return false;
            }
        }
        return true;
    }

}
