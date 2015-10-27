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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradAlgebra;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Split;
import edu.cmu.tetrad.util.dist.Uniform;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import org.apache.commons.math3.random.*;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    private int NTHREADS = Runtime.getRuntime().availableProcessors() * 10;
    private final int minChunk = 11;
    private ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();
    private int[] tierIndices;


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

//        this.variableNodes = graph.getNodes();
        final List<Node> causalOrdering = graph.getCausalOrdering();
        this.tierIndices = new int[causalOrdering.size()];

        for (int i = 0; i < causalOrdering.size(); i++) {
            this.tierIndices[i] = variableNodes.indexOf(causalOrdering.get(i));
        }

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
    public DataSet simulateDataAcyclic(int sampleSize) {
        if (false) {
            return simulateDataAcyclicConcurrent(sampleSize);
        }

        int size = variableNodes.size();
        setupModel(size);

//        final DataSet dataSet = new ColtDataSet(sampleSize, variableNodes);
        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variableNodes.size()), variableNodes);

        for (int i = 0; i < tierIndices.length; i++) {
            int col = tierIndices[i];

            for (int row = 0; row < sampleSize; row++) {
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

    private void setupModel(int size) {
        System.out.println("Setup model start");
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

            int _tail = variableNodes.indexOf(tail);
            int _head = variableNodes.indexOf(head);

            int[] parents = this.parents[_head];
            int[] newParents = new int[parents.length + 1];
            for (int i = 0; i < parents.length; i++) {
                newParents[i] = parents[i];
            }
            newParents[newParents.length - 1] = _tail;
            double[] coefs = this.coefs[_head];
            double[] newCoefs = new double[coefs.length + 1];

            for (int i = 0; i < coefs.length; i++) {
                newCoefs[i] = coefs[i];
            }

            newCoefs[newCoefs.length - 1] = edgeCoefDist.nextRandom();

            this.parents[_head] = newParents;
            this.coefs[_head] = newCoefs;
        }

        for (int i = 0; i < size; i++) {
            this.errorVars[i] = errorCovarDist.nextRandom();
            this.means[i] = meanDist.nextRandom();
        }

        System.out.println("Setup model done");
    }

    // Tier ordering is the order of the variables.
    public BoxDataSet simulateDataAcyclicConcurrent(int sampleSize) {
        int numVars = variableNodes.size();
        setupModel(numVars);

        System.out.println("Tier ordering");

        final int[][] _parents = parents;
        final double[][] _coefs = coefs;

//        final double[][] _data = new double[sampleSize][numVars];
        final double[][] _data = new double[numVars][sampleSize];

        System.out.println("Starting simulation task");

        // This random number generator is not thread safe, so we make a new one each time.
        RandomGenerator apacheGen = new Well19937a(new Date().getTime());
        final RandomDataGenerator generator = new RandomDataGenerator(new SynchronizedRandomGenerator(apacheGen));

        //Do the simulation.
        class SimulationTask extends RecursiveTask<Boolean> {
            private int chunk;
            private int from;
            private int to;

            public SimulationTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                RandomGenerator apacheGen = new Well44497b(generator.nextLong(0, Long.MAX_VALUE));
                RandomDataGenerator generatorLocal = new RandomDataGenerator(apacheGen);

                if (to - from <= chunk) {
                    for (int row = from; row < to; row++) {
                        if (row % 100 == 0) out.println("Row " + row);

                        for (int i = 0; i < tierIndices.length; i++) {
                            int col = tierIndices[i];

                            double value = generatorLocal.nextGaussian(0, sqrt(errorVars[col]));

                            for (int j = 0; j < _parents[col].length; j++) {
                                int parent = _parents[col][j];
                                final double coef = _coefs[col][j];
                                final double v = _data[parent][row];
                                value += v * coef;

                                if (Double.isNaN(value)) {
                                    throw new IllegalArgumentException();
                                }
                            }

                            value += means[col];

                            _data[col][row] = value;
                        }
                    }

                    return true;
                } else {
                    List<SimulationTask> simulationTasks = new ArrayList<SimulationTask>();

                    int mid = (to - from) / 2;

                    simulationTasks.add(new SimulationTask(chunk, from, from + mid));
                    simulationTasks.add(new SimulationTask(chunk, from + mid, to));

                    invokeAll(simulationTasks);

                    return true;
                }
            }
        }

        int chunk = 25;

        pool.invoke(new SimulationTask(chunk, 0, sampleSize));

        return new BoxDataSet(new VerticalDoubleDataBox(_data), variableNodes);
//        return ColtDataSet.makeContinuousData(variableNodes, _data);
    }


    // Tier ordering is the order of the variables.
    private DataSet constructSimulation2(List<Node> variables, int sampleSize) {
        // Create some index arrays to hopefully speed up the simulation.
//        final int[] tierIndices = new int[variableNodes.size()];
//
//        for (int i = 0; i < tierIndices.length; i++) {
//            tierIndices[i] = i;
//        }


        List<Node> tierOrdering = graph.getCausalOrdering();

        final int[] tierIndices = new int[variableNodes.size()];

        for (int i = 0; i < tierIndices.length; i++) {
            tierIndices[i] = variableNodes.indexOf(tierOrdering.get(i));
        }

        for (int i = 0; i < tierIndices.length; i++) {
            tierIndices[i] = i;
        }

        final int[][] _parents = parents;
        final double[][] _coefs = coefs;
//
//        int numVars = variables.size();
//
//        final double[][] _data = new double[numVars][sampleSize];

        final DataSet dataSet = new ColtDataSet(sampleSize, variableNodes);
//        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variableNodes.size()), variables);

//        //Do the simulation.
        class Task extends RecursiveTask<Boolean> {
            private int chunk;
            private int from;
            private int to;

            public Task(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {

                    for (int row = from; row < to; row++) {
//                        System.out.println("Row = " + row);
                        for (int i = 0; i < tierIndices.length; i++) {
                            int col = tierIndices[i];
                            double value1 = RandomUtil.getInstance().nextNormal(0, sqrt(errorVars[col]));

                            for (int j = 0; j < _parents[col].length; j++) {
                                int parent = _parents[col][j];
                                double aDouble = dataSet.getDouble(row, parent);
                                value1 += aDouble * _coefs[col][j];
                            }

                            value1 += means[col];
                            double value = value1;
                            dataSet.setDouble(row, col, value);
                        }
                    }

                    return true;
                } else {
                    int numIntervals = 4;

                    int step = (to - from) / numIntervals + 1;

                    List<Task> tasks = new ArrayList<Task>();

                    for (int i = 0; i < numIntervals; i++) {
//                        System.out.println("From = " + (from + i * step) + " to + " + Math.min(from + (i + 1) * step, to));
                        tasks.add(new Task(chunk, from + i * step, Math.min(from + (i + 1) * step, to)));
                    }

                    invokeAll(tasks);

                    return true;
                }

            }

        }

        int _chunk = variables.size() / NTHREADS;
        final int chunk = _chunk < minChunk ? minChunk : _chunk;

        System.out.println("Starting data simulation 2");

        pool.invoke(new Task(chunk, 0, sampleSize));

        System.out.println("Finishing data simulation 2");

        return dataSet;
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

}




