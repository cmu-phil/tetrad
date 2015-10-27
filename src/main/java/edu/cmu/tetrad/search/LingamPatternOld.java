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

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.util.*;

/**
 * Given a pattern, chooses the best DAG(s) in the pattern by calculating an objective function for each DAG in the
 * pattern and reporting the best of those. Algorithm by Patrik Hoyer.
 * <p/>
 * It is assumed that the pattern is the result of a pattern search such as PC or GES. In any case, it is important that
 * the residuals be independent for ICA to work.
 *
 * @author Joseph Ramsey
 */
public class LingamPatternOld {
    private int numSamples = 15;

    public LingamPatternOld() {

    }

    public Result search(Graph pattern, DataSet dataSet) throws IllegalArgumentException {
        return search(SearchGraphUtils.getDagsInPatternMeek(pattern, new Knowledge2()), dataSet);
    }

    public Result search(Graph pattern, DataSet dataSet, IKnowledge knowledge) throws IllegalArgumentException {
        return search(SearchGraphUtils.getDagsInPatternMeek(pattern, knowledge), dataSet);
    }

    /**
     * //     * @param pattern
     *
     * @param dataSet
     * @return
     * @throws IllegalArgumentException if the score cannot be calculated.
     */
    public Result search(List<Graph> dags, DataSet dataSet) throws IllegalArgumentException {

//        Collections.shuffle(dags);

        if (dags.isEmpty()) {
            throw new IllegalArgumentException("No input dags");
        }

        System.out.println("DAGS GIVEN TO LINGAM PATTERN:");

        for (int i = 0; i < dags.size(); i++) {
            System.out.println("#" + i + ": " + dags.get(i));
        }

        TetradMatrix data = dataSet.getDoubleData();
        List<Node> variables = dataSet.getVariables();

//        List<Dag> dags = new ArrayList<Dag>();
        int bootstrapSize = data.rows() / 2;

//        if (allDags) {
//            DagInPatternIterator iterator = new DagInPatternIterator(pattern);
//            getAllDags(pattern, dags);
//        }
//        else {
//            DagInPatternIterator iterator = new DagInPatternIterator(pattern);
//        DagIterator iterator = new DagIterator(pattern);
//
//            while (iterator.hasNext()) {
//                Graph graph = iterator.next();
//
//                try {
//                    Dag dag = new Dag(graph);
//                    dags.add(dag);
//                } catch (IllegalArgumentException e) {

//                }
//            }
//        }

        if (dags.size() == 0) {
            return new Result(new ArrayList<Graph>(), new ArrayList<Integer>(), numSamples);
        }

        double[][] scores = new double[dags.size()][getNumSamples()];

        for (int k = 0; k < getNumSamples(); k++) {
            System.out.println("Sample " + k);
            TetradMatrix sample = getBootstrapSample(data, bootstrapSize);

            for (int i = 0; i < dags.size(); i++) {
                Graph dag = dags.get(i);
                scores[i][k] = getScore(dag, sample, variables);
            }
        }

        final Map<Integer, Integer> highestCounts = new TreeMap<Integer, Integer>();

//        for (int i = 0; i < getNumSamples(); i++) {
//            int maxDag = -1;
//            double maxScore = 0.0;
//
//            for (int j = 0; j < dags.size(); j++) {
//                if (scores[j][i] > maxScore) {
//                    maxDag = j;
//                    maxScore = scores[j][i];
//                }
//            }
//
//            if (maxDag == -1) {
//                continue;
//            }
//
////            System.out.println("Max score for sample " + i + " is " + maxDag + " score " + scores[maxDag][i]);
//
//            if (!highestCounts.containsKey(maxDag)) {
//                highestCounts.put(maxDag, 1);
//            }
//            else {
//                highestCounts.put(maxDag, highestCounts.get(maxDag) + 1);
//            }
//        }

        for (int i = 0; i < getNumSamples(); i++) {
            double maxScore = 0.0;

            for (int j = 0; j < dags.size(); j++) {
                if (scores[j][i] > maxScore) {
                    maxScore = scores[j][i];
                }
            }

            int numMax = 0;

            for (int j = 0; j < dags.size(); j++) {
                if (scores[j][i] == maxScore) {
                    numMax++;

                    if (!highestCounts.containsKey(j)) {
                        highestCounts.put(j, 1);
                    } else {
                        highestCounts.put(j, highestCounts.get(j) + 1);
                    }
                }
            }

//            System.out.println("Nummax = " + numMax);
        }

//        System.out.println("Best models: " ) ;

        int maxIndex = -1;
//        int max = -1;

        for (int i : highestCounts.keySet()) {
//            System.out.println("\nDAG # " + i + " count = " + highestCounts.get(i) +
//                 " = " + highestCounts.get(i) / (double) getNumSamples());
//            System.out.println(dags.get(i));

            if (highestCounts.get(i) > maxIndex) {
                maxIndex = highestCounts.get(i);
//                max = i;
            }
        }

        List<Integer> outputIndices = new ArrayList<Integer>(highestCounts.keySet());

        Collections.sort(outputIndices, new Comparator<Integer>() {
            public int compare(Integer o1, Integer o2) {
                return highestCounts.get(o2) - highestCounts.get(o1);
            }
        });

        List<Graph> outputDags = new ArrayList<Graph>();

        for (int i = 0; i < outputIndices.size(); i++) {
            outputDags.add(dags.get(outputIndices.get(i)));
        }

        List<Integer> outputCounts = new ArrayList<Integer>();

        for (int i = 0; i < outputIndices.size(); i++) {
            outputCounts.add(highestCounts.get(outputIndices.get(i)));
        }

        if (outputDags.isEmpty()) {
            throw new IllegalArgumentException("Not output dags");
        }

        return new Result(outputDags, outputCounts, numSamples);
    }

    public static class Result {
        private List<Graph> dags;
        private List<Integer> counts;
        private int numSamples;

        public Result(List<Graph> dags, List<Integer> counts, int numSamples) {
            this.setDags(dags);
            this.setCounts(counts);
            this.numSamples = numSamples;
        }

        public List<Graph> getDags() {
            return dags;
        }

        public void setDags(List<Graph> dags) {
            this.dags = dags;
        }

        public List<Integer> getCounts() {
            return counts;
        }

        public void setCounts(List<Integer> counts) {
            this.counts = counts;
        }

        public int getNumSamples() {
            return numSamples;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();

            for (int i = 0; i < dags.size(); i++) {
                buf.append("#" + i).append("\n");
                buf.append(dags.get(i));
                buf.append(counts.get(i)).append(" votes\n");
            }

            return buf.toString();
        }
    }

    private Double getScore(Graph dag, TetradMatrix data, List<Node> variables) {
//        System.out.println("Scoring DAG: G" + dag);

        Regression regression = new RegressionDataset(data, variables);

        List<Node> nodes = dag.getNodes();
        double score = 0.0;
        TetradMatrix residuals = new TetradMatrix(data.rows(), data.columns());

        for (int i = 0; i < nodes.size(); i++) {
            Node _target = nodes.get(i);
            List<Node> _regressors = dag.getParents(_target);
            Node target = getVariable(variables, _target.getName());
            List<Node> regressors = new ArrayList<Node>();

            for (Node _regressor : _regressors) {
                Node variable = getVariable(variables, _regressor.getName());
                regressors.add(variable);
            }

            RegressionResult result = regression.regress(target, regressors);
            TetradVector residualsColumn = result.getResiduals();
//            residuals.viewColumn(i).assign(residualsColumn);
            residuals.assignColumn(i, residualsColumn);
            DoubleArrayList residualsArray = new DoubleArrayList(residualsColumn.toArray());

            double mean = Descriptive.mean(residualsArray);
            double std = Descriptive.standardDeviation(Descriptive.variance(residualsArray.size(),
                    Descriptive.sum(residualsArray), Descriptive.sumOfSquares(residualsArray)));

            for (int i2 = 0; i2 < residualsArray.size(); i2++) {
                residualsArray.set(i2, (residualsArray.get(i2) - mean) / std);
                residualsArray.set(i2, Math.abs(residualsArray.get(i2)));
            }

            double _mean = Descriptive.mean(residualsArray);
//            score += Math.abs(_mean - Math.sqrt(2.0 / Math.PI));
            double diff = _mean - Math.sqrt(2.0 / Math.PI);
            score += diff * diff;
        }

//        System.out.println("score = " + score);
//        System.out.println();

        int numIndepencies = 0;
        IndependenceTest test = new IndTestFisherZ(residuals, nodes, 0.01);

//        for (int i = 0; i < nodes.size(); i++) {
//            for (int j = 0; j < i; j++) {
//                if (!test.isIndependent(nodes.get(i), nodes.get(j), new LinkedList<Node>())) {
//                    numIndepencies++;
////                    System.out.println(nodes.get(i) + " " + nodes.get(j) + " residuals correlated.");
//                }
//            }
//        }
//
//        if (numIndepencies > 4) {
//            return Double.NEGATIVE_INFINITY;
//        }
////
//        System.out.println();

        return score;
    }

    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    private TetradMatrix getBootstrapSample(TetradMatrix dataSet, int sampleSize) {
        int actualSampleSize = dataSet.rows();

        int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        int[] cols = new int[dataSet.columns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return dataSet.getSelection(rows, cols).copy();
    }

    public int getNumSamples() {
        return numSamples;
    }

    public void setNumSamples(int numSamples) {
        if (numSamples < 1) {
            throw new IllegalArgumentException("Must use at least one sample: " + numSamples);
        }

        this.numSamples = numSamples;
    }
}



