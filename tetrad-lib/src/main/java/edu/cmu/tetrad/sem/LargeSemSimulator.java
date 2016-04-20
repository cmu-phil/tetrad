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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradAlgebra;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Split;
import edu.cmu.tetrad.util.dist.Uniform;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.Well1024a;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import static java.lang.Math.sqrt;

/**
 * Stores a SEM model, pared down, for purposes of simulating data sets with
 * large numbers of variables and sample sizes. Assumes acyclicity.
 *
 * @author Joseph Ramsey
 */
public final class LargeSemSimulator {
    static final long serialVersionUID = 23L;

    private int[][] parents;
    private double[][] coefs;
    private double[] errorVars;
    private double[] means;
    private int maxThreads = 80;//Runtime.getRuntime().availableProcessors() * 5;

    /**
     * Used for some linear algebra calculations.
     */
    private transient TetradAlgebra algebra;
    private List<Node> variableNodes;
    private Graph graph;
    private double coefLow = .2;
    private double coefHigh = 1.5;
    private double varLow = 1.0;
    private double varHigh = 3.0;
    private PrintStream out = System.out;
    private ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();
    private int[] tierIndices;
    private boolean verbose = false;


    //=============================CONSTRUCTORS============================//

    public LargeSemSimulator(Graph graph) {
        List<Node> nodes = graph.getCausalOrdering();
        int[] tierIndices = new int[nodes.size()];
        for (int j = 0; j < nodes.size(); j++) {
            tierIndices[j] = j;
        }

        this.graph = graph;
        this.variableNodes = nodes;
        this.tierIndices = tierIndices;

        if (graph instanceof SemGraph) {
            ((SemGraph) graph).setShowErrorTerms(false);
        }
    }

    public LargeSemSimulator(Graph graph, List<Node> nodes, int[] tierIndices) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.graph = graph;
        this.variableNodes = nodes;
        this.tierIndices = tierIndices;

        if (graph instanceof SemGraph) {
            ((SemGraph) graph).setShowErrorTerms(false);
        }
    }

    /**
     * This simulates data by picking random values for the exogenous terms and
     * percolating this information down through the SEM, assuming it is
     * acyclic. Works, but will hang for cyclic models, and is very slow for
     * large numbers of variables (probably due to the heavyweight lookups of
     * various values--could be improved).
     */
    public DataSet simulateDataAcyclic1(int sampleSize) {
        int size = variableNodes.size();
        setupModel(size);

//        final DataSet dataSet = new ColtDataSet(sampleSize, variableNodes);
        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variableNodes.size()), variableNodes);

        for (int row = 0; row < sampleSize; row++) {
            for (int col : tierIndices) {
                double value = RandomUtil.getInstance().nextNormal(0, sqrt(errorVars[col]));

                for (int j = 0; j < parents[col].length; j++) {
                    value += dataSet.getDouble(row, parents[col][j]) * coefs[col][j];
                }

                value += means[col];
                dataSet.setDouble(row, col, value);
            }
        }

        return dataSet;
    }

    long seed = new Date().getTime();


    // Trying again to parallelize simulateDataAcyclic.
    public DataSet simulateDataAcyclic2(int sampleSize) {
        int size = variableNodes.size();
        setupModel(size);

        class SimulateRowTask extends RecursiveTask<double[]> {
            private final int i;

            public SimulateRowTask(int i) {
                this.i = i;
            }

            @Override
            protected double[] compute() {
                NormalDistribution normal = new NormalDistribution(new Well1024a(++seed), 0, 1);//sqrt(errorVars[col]));
                normal.sample();

                if (verbose && (i + 1) % 50 == 0)
                    System.out.println("Simulating " + (i + 1));

                double[] _row = new double[tierIndices.length];

                for (int col : tierIndices) {
                    double value = normal.sample() * sqrt(errorVars[col]);

//                    double value = RandomUtil.getInstance().nextNormal(0, sqrt(errorVars[col]));

                    for (int j = 0; j < parents[col].length; j++) {
                        value += _row[parents[col][j]] * coefs[col][j];
                    }

                    value += means[col];

                    _row[col] = value;
                }

                return _row;
            }
        }

        class SimulateTask extends RecursiveTask<double[][]> {

            private final int numRows;

            public SimulateTask(int numRows) {
                this.numRows = numRows;
            }

            @Override
            protected double[][] compute() {
                Queue<SimulateRowTask> tasks = new ArrayDeque<>();
                List<double[]> rows = new ArrayList<>();

                for (int i = 0; i < numRows; i++) {
                    SimulateRowTask task = new SimulateRowTask(i);
                    tasks.add(task);
                    task.fork();

                    for (SimulateRowTask _task : new ArrayList<>(tasks)) {
                        if (_task.isDone()) {
                            rows.add(_task.join());
                            tasks.remove(_task);
                        }
                    }

                    if (tasks.size() >= maxThreads) {
                        SimulateRowTask _task = tasks.poll();
                        rows.add(_task.join());
                    }
                }

                for (SimulateRowTask task : tasks) {
                    rows.add(task.join());
                }

                double[][] ret = new double[rows.size()][];

                for (int i = 0; i < ret.length; i++) {
                    ret[i] = rows.get(i);
                }

                return ret;
            }
        }

        double[][] all = ForkJoinPoolInstance.getInstance().getPool().invoke(new SimulateTask(sampleSize));

        return new BoxDataSet(new DoubleDataBox(all), variableNodes);
    }

    public DataSet simulateDataAcyclic(int sampleSize) {
        int size = variableNodes.size();
        setupModel(size);

        class SimulateTask extends RecursiveTask<Boolean> {
            private final int from;
            private final int to;
            private double[][] all;
            private int chunk;

            public SimulateTask(int from, int to, double[][] all, int chunk) {
                this.from = from;
                this.to = to;
                this.all = all;
                this.chunk = chunk;
            }

            @Override
            protected Boolean compute() {
                if (from - to > chunk) {
                    int mid = from + to / 2;
                    SimulateTask left = new SimulateTask(from, mid, all, chunk);
                    SimulateTask right = new SimulateTask(mid, to, all, chunk);
                    left.fork();
                    right.compute();
                    left.join();
                    return true;
                } else {
                    for (int i = from; i < to; i++) {
                        NormalDistribution normal = new NormalDistribution(new Well1024a(++seed), 0, 1);//sqrt(errorVars[col]));
                        normal.sample();

                        if (verbose && (i + 1) % 50 == 0)
                            System.out.println("Simulating " + (i + 1));

                        for (int col : tierIndices) {
                            double value = normal.sample() * sqrt(errorVars[col]);

                            for (int j = 0; j < parents[col].length; j++) {
                                value += all[parents[col][j]][i] * coefs[col][j];
                            }

                            value += means[col];

                            all[col][i] = value;
                        }
                    }

                    return true;
                }
            }
        }

        double[][] all = new double[variableNodes.size()][sampleSize];

        int chunk = sampleSize / ForkJoinPoolInstance.getInstance().getPool().getParallelism() + 1;

        ForkJoinPoolInstance.getInstance().getPool().invoke(new SimulateTask(0, sampleSize, all, chunk));

        return new BoxDataSet(new VerticalDoubleDataBox(all), variableNodes);
    }

    private void setupModel(int size) {
        Map<Node, Integer> nodesHash = new HashedMap<>();

        for (int i = 0; i < variableNodes.size(); i++) {
            nodesHash.put(variableNodes.get(i), i);
        }

        this.parents = new int[size][];
        this.coefs = new double[size][];
        this.errorVars = new double[size];
        this.means = new double[size];

        for (int i = 0; i < size; i++) {
            this.parents[i] = new int[0];
            this.coefs[i] = new double[0];
        }

        Distribution edgeCoefDist = new Split(coefLow, coefHigh);
        Distribution errorCovarDist = new Uniform(varLow, varHigh);
        Distribution meanDist = new Uniform(-1.0, 1.0);

        for (Edge edge : graph.getEdges()) {
            Node tail = Edges.getDirectedEdgeTail(edge);
            Node head = Edges.getDirectedEdgeHead(edge);

            int _tail = nodesHash.get(tail);
            int _head = nodesHash.get(head);

            int[] parents = this.parents[_head];
            int[] newParents = new int[parents.length + 1];
            System.arraycopy(parents, 0, newParents, 0, parents.length);
            newParents[newParents.length - 1] = _tail;
            double[] coefs = this.coefs[_head];
            double[] newCoefs = new double[coefs.length + 1];

            System.arraycopy(coefs, 0, newCoefs, 0, coefs.length);

            newCoefs[newCoefs.length - 1] = edgeCoefDist.nextRandom();

            this.parents[_head] = newParents;
            this.coefs[_head] = newCoefs;
        }

        for (int i = 0; i < size; i++) {
            this.errorVars[i] = errorCovarDist.nextRandom();
            this.means[i] = meanDist.nextRandom();
        }
    }

    public TetradAlgebra getAlgebra() {
        if (algebra == null) {
            algebra = new TetradAlgebra();
        }

        return algebra;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setCoefRange(double coefLow, double coefHigh) {
        this.coefLow = coefLow;
        this.coefHigh = coefHigh;
    }

    public void setVarRange(double varLow, double varHigh) {
        this.varLow = varLow;
        this.varHigh = varHigh;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public PrintStream getOut() {
        return out;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}




