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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Split;
import edu.cmu.tetrad.util.dist.Uniform;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.random.Well1024a;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.RecursiveTask;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

/**
 * Stores a SEM model, pared down, for purposes of simulating data sets with
 * large numbers of variables and sample sizes.
 *
 * @author Joseph Ramsey
 */
public final class LargeScaleSimulation {

    static final long serialVersionUID = 23L;

    private int[][] parents;
    private double[][] coefs;
    private double[] errorVars;
    private double[] means;
    private transient TetradAlgebra algebra;
    private final List<Node> variableNodes;
    private final Graph graph;
    private double coefLow;
    private double coefHigh = 1.0;
    private double varLow = 1.0;
    private double varHigh = 3.0;
    private double meanLow;
    private double meanHigh;
    private PrintStream out = System.out;
    private int[] tierIndices;
    private boolean verbose;
    private long seed = new Date().getTime();
    private boolean alreadySetUp;
    private boolean includePositiveCoefs = true;
    private boolean includeNegativeCoefs = true;

    private boolean errorsNormal = true;
    private double selfLoopCoef;

    //=============================CONSTRUCTORS============================//
    public LargeScaleSimulation(final Graph graph) {
        this.graph = graph;
        this.variableNodes = graph.getNodes();

        if (graph instanceof SemGraph) {
            ((SemGraph) graph).setShowErrorTerms(false);
        }

        final List<Node> causalOrdering = graph.getCausalOrdering();
        this.tierIndices = new int[causalOrdering.size()];
        for (int i = 0; i < this.tierIndices.length; i++) {
            this.tierIndices[i] = this.variableNodes.indexOf(causalOrdering.get(i));
        }
    }

    public LargeScaleSimulation(final Graph graph, final List<Node> nodes, final int[] tierIndices) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.graph = GraphUtils.replaceNodes(graph, nodes);
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
     * various values--could be improved). The model must be acyclic, or else
     * this will spin.
     */
    public DataSet simulateDataRecursive(int sampleSize) {
        if (this.tierIndices == null) {
            final List<Node> nodes = this.graph.getNodes();
            this.tierIndices = new int[nodes.size()];
            for (int j = 0; j < nodes.size(); j++) {
                this.tierIndices[j] = j;
            }
        }

        final int size = this.variableNodes.size();
        setupModel(size);

        class SimulateTask extends RecursiveTask<Boolean> {

            private final int from;
            private final int to;
            private final double[][] all;
            private final int chunk;

            public SimulateTask(final int from, final int to, final double[][] all, final int chunk) {
                this.from = from;
                this.to = to;
                this.all = all;
                this.chunk = chunk;
            }

            @Override
            protected Boolean compute() {
                if (this.from - this.to > this.chunk) {
                    final int mid = this.from + this.to / 2;
                    final SimulateTask left = new SimulateTask(this.from, mid, this.all, this.chunk);
                    final SimulateTask right = new SimulateTask(mid, this.to, this.all, this.chunk);
                    left.fork();
                    right.compute();
                    left.join();
                    return true;
                } else {
                    for (int i = this.from; i < this.to; i++) {
                        final NormalDistribution normal = new NormalDistribution(new Well1024a(++LargeScaleSimulation.this.seed), 0, 1);//sqrt(errorVars[col]));
                        normal.sample();

                        if (LargeScaleSimulation.this.verbose && (i + 1) % 50 == 0) {
                            System.out.println("Simulating " + (i + 1));
                        }

                        for (final int col : LargeScaleSimulation.this.tierIndices) {
                            double value = normal.sample() * sqrt(LargeScaleSimulation.this.errorVars[col]);

                            for (int j = 0; j < LargeScaleSimulation.this.parents[col].length; j++) {
                                value += this.all[LargeScaleSimulation.this.parents[col][j]][i] * LargeScaleSimulation.this.coefs[col][j];
                            }

                            value += LargeScaleSimulation.this.means[col];

                            this.all[col][i] = value;
                        }
                    }

                    return true;
                }
            }
        }

        if (this.graph instanceof TimeLagGraph) {
            sampleSize += 200;
        }

        final double[][] all = new double[this.variableNodes.size()][sampleSize];

        final int chunk = sampleSize / ForkJoinPoolInstance.getInstance().getPool().getParallelism() + 1;

        ForkJoinPoolInstance.getInstance().getPool().invoke(new SimulateTask(0, sampleSize, all, chunk));

        if (this.graph instanceof TimeLagGraph) {
            final int[] rem = new int[200];
            for (int i = 0; i < 200; ++i) {
                rem[i] = i;
            }
            final BoxDataSet dat = new BoxDataSet(new VerticalDoubleDataBox(all), this.variableNodes);
            dat.removeRows(rem);
            return dat;
        }

        return new BoxDataSet(new VerticalDoubleDataBox(all), this.variableNodes);
    }

    /**
     * Simulates data using the model X = (I - B)Y^-1 * e. Errors are
     * uncorrelated.
     *
     * @param sampleSize The nubmer of samples to draw.
     */
    public DataSet simulateDataReducedForm(final int sampleSize) {
        if (sampleSize < 1) {
            throw new IllegalArgumentException(
                    "Sample size must be >= 1: " + sampleSize);
        }

        final int size = this.variableNodes.size();
        setupModel(size);

        final NormalDistribution normal = new NormalDistribution(new Well1024a(++this.seed), 0, 1);

        final Matrix B = new Matrix(getCoefficientMatrix());
        final Matrix iMinusBInv = TetradAlgebra.identity(B.rows()).minus(B).inverse();

        final double[][] all = new double[this.variableNodes.size()][sampleSize];

        for (int row = 0; row < sampleSize; row++) {
            final Vector e = new Vector(B.rows());

            for (int j = 0; j < e.size(); j++) {
                e.set(j, normal.sample() * sqrt(this.errorVars[j]));
            }

            final Vector x = iMinusBInv.times(e);

            for (int j = 0; j < x.size(); j++) {
                all[j][row] = x.get(j);
            }
        }

        final List<Node> continuousVars = new ArrayList<>();

        for (final Node node : getVariableNodes()) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        final BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);
        return DataUtils.restrictToMeasured(boxDataSet);
    }

    /**
     * Simulates data using the model of R. A. Fisher, for a linear model.
     * Shocks are applied every so many steps. A data point is recorded before
     * each shock is administered. If convergence happens before that number of
     * steps has been reached, a data point is recorded and a new shock
     * immediately applied. The model may be cyclic. If cyclic, all eigenvalues
     * for the coefficient matrix must be less than 1, though this is not
     * checked. Uses an interval between shocks of 50 and a convergence
     * threshold of 1e-5. Uncorrelated Gaussian shocks are used.
     *
     * @param sampleSize The number of samples to be drawn. Must be a positive
     *                   integer.
     */
    public DataSet simulateDataFisher(final int sampleSize) {
        return simulateDataFisher(getSoCalledPoissonShocks(sampleSize), 50, 1e-5);
    }

    /**
     * Simulates data using the model of R. A. Fisher, for a linear model.
     * Shocks are applied every so many steps. A data point is recorded before
     * each shock is administered. If convergence happens before that number of
     * steps has been reached, a data point is recorded and a new shock
     * immediately applied. The model may be cyclic. If cyclic, all eigenvalues
     * for the coefficient matrix must be less than 1, though this is not
     * checked.
     *
     * @param shocks                A matrix of shocks. The value at shocks[i][j] is the shock
     *                              for the i'th time step, for the j'th variables.
     * @param intervalBetweenShocks External shock is applied every this many
     *                              steps. Must be positive integer.
     * @param epsilon               The convergence criterion; |xi.t - xi.t-1| < epsilon.fff
     */
    public DataSet simulateDataFisher(final double[][] shocks, final int intervalBetweenShocks, final double epsilon) {
        if (intervalBetweenShocks < 1) {
            throw new IllegalArgumentException(
                    "Interval between shocks must be >= 1: " + intervalBetweenShocks);
        }
        if (epsilon <= 0.0) {
            throw new IllegalArgumentException(
                    "Epsilon must be > 0: " + epsilon);
        }

        final int size = this.variableNodes.size();
        if (shocks[0].length != size) {
            throw new IllegalArgumentException("The number of columns in the shocks matrix does not equal "
                    + "the number of variables.");
        }

        setupModel(size);

        double[] t1 = new double[this.variableNodes.size()];
        double[] t2 = new double[this.variableNodes.size()];
        final double[][] all = new double[this.variableNodes.size()][shocks.length];

        // Do the simulation.
        for (int row = 0; row < shocks.length; row++) {
            for (int j = 0; j < t1.length; j++) {
                t2[j] = shocks[row][j];
            }

            for (int i = 0; i < intervalBetweenShocks; i++) {
                for (int j = 0; j < t1.length; j++) {
                    for (int k = 0; k < this.parents[j].length; k++) {
                        t2[j] += t1[this.parents[j][k]] * this.coefs[j][k];
                    }
                }

                boolean converged = true;

                for (int j = 0; j < t1.length; j++) {
                    if (abs(t2[j] - t1[j]) > epsilon) {
                        converged = false;
                        break;
                    }
                }

                final double[] t3 = t1;
                t1 = t2;
                t2 = t3;

                if (converged) {
                    break;
                }
            }

            for (int j = 0; j < t1.length; j++) {
                all[j][row] = t2[j];
            }
        }

        final List<Node> continuousVars = new ArrayList<>();

        for (final Node node : getVariableNodes()) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        final BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);
        return DataUtils.restrictToMeasured(boxDataSet);
    }

    public DataSet simulateDataFisher(final int intervalBetweenShocks, final int intervalBetweenRecordings, final int sampleSize, final double epsilon, final boolean saveLatentVars) {
        if (intervalBetweenShocks < 1) {
            throw new IllegalArgumentException(
                    "Interval between shocks must be >= 1: " + intervalBetweenShocks);
        }
        if (epsilon <= 0.0) {
            throw new IllegalArgumentException(
                    "Epsilon must be > 0: " + epsilon);
        }

        final int size = this.variableNodes.size();

        setupModel(size);

        double[] t1 = new double[this.variableNodes.size()];
        double[] t2 = new double[this.variableNodes.size()];
        final double[][] all = new double[this.variableNodes.size()][sampleSize];

        int s = 0;
        int shockIndex = 0;
        int recordingIndex = 0;
        double[] shock = getUncorrelatedShocks(1)[0];

        for (int j = 0; j < t1.length; j++) {
            t1[j] = shock[j];
        }

        while (s < sampleSize) {
            if ((++recordingIndex) % intervalBetweenRecordings == 0) {
                for (int j = 0; j < t1.length; j++) {
                    all[j][s] += t1[j];
                }

                s++;
            }

            if ((++shockIndex) % intervalBetweenShocks == 0) {
                shock = getUncorrelatedShocks(1)[0];

                for (int j = 0; j < t1.length; j++) {
                    t1[j] += shock[j];
                }
            }

            for (int j = 0; j < t1.length; j++) {
                t2[j] = shock[j];
                t2[j] += getSelfLoopCoef() * t1[j];

                for (int k = 0; k < this.parents[j].length; k++) {
                    t2[j] += t1[this.parents[j][k]] * this.coefs[j][k];
                }
            }

            final double[] t3 = t1;
            t1 = t2;
            t2 = t3;
        }

        final List<Node> continuousVars = new ArrayList<>();

        for (final Node node : getVariableNodes()) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        final BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);

        return saveLatentVars ? boxDataSet : DataUtils.restrictToMeasured(boxDataSet);
    }

    private void setupModel(final int size) {
        if (this.alreadySetUp) {
            return;
        }

        final Map<Node, Integer> nodesHash = new HashedMap<>();

        for (int i = 0; i < this.variableNodes.size(); i++) {
            nodesHash.put(this.variableNodes.get(i), i);
        }

        this.parents = new int[size][];
        this.coefs = new double[size][];
        this.errorVars = new double[size];
        this.means = new double[size];

        for (int i = 0; i < size; i++) {
            this.parents[i] = new int[0];
            this.coefs[i] = new double[0];
        }

        final Distribution edgeCoefDist = new Split(this.coefLow, this.coefHigh);
        final Distribution errorCovarDist = new Uniform(this.varLow, this.varHigh);
        final Distribution meanDist = new Uniform(this.meanLow, this.meanHigh);

        for (final Edge edge : this.graph.getEdges()) {
            final Node tail = Edges.getDirectedEdgeTail(edge);
            final Node head = Edges.getDirectedEdgeHead(edge);

            final int _tail = nodesHash.get(tail);
            final int _head = nodesHash.get(head);

            final int[] parents = this.parents[_head];
            final int[] newParents = new int[parents.length + 1];
            System.arraycopy(parents, 0, newParents, 0, parents.length);
            newParents[newParents.length - 1] = _tail;
            final double[] coefs = this.coefs[_head];
            final double[] newCoefs = new double[coefs.length + 1];

            System.arraycopy(coefs, 0, newCoefs, 0, coefs.length);

            double coef = edgeCoefDist.nextRandom();

            if (this.includePositiveCoefs && !this.includeNegativeCoefs) {
                coef = abs(coef);
            } else if (!this.includePositiveCoefs && this.includeNegativeCoefs) {
                coef = -abs(coef);
            } else if (!this.includePositiveCoefs && !this.includeNegativeCoefs) {
                coef = 0;
            }

            newCoefs[newCoefs.length - 1] = coef;

            this.parents[_head] = newParents;
            this.coefs[_head] = newCoefs;
        }

        if (this.graph instanceof TimeLagGraph) {
            final TimeLagGraph lagGraph = (TimeLagGraph) this.graph;
            final IKnowledge knowledge = getKnowledge(lagGraph); //TimeSeriesUtils.getKnowledge(lagGraph);
            final List<Node> lag0 = lagGraph.getLag0Nodes();

            for (final Node y : lag0) {
                final List<Node> _parents = lagGraph.getParents(y);

                for (final Node x : _parents) {
                    final List<List<Node>> similar = returnSimilarPairs(x, y, knowledge);

                    final int _x = this.variableNodes.indexOf(x);
                    final int _y = this.variableNodes.indexOf(y);
                    double first = Double.NaN;

                    for (int i = 0; i < this.parents[_y].length; i++) {
                        if (_x == this.parents[_y][i]) {
                            first = this.coefs[_y][i];
                        }
                    }

                    for (int j = 0; j < similar.get(0).size(); j++) {
                        final int _xx = this.variableNodes.indexOf(similar.get(0).get(j));
                        final int _yy = this.variableNodes.indexOf(similar.get(1).get(j));

                        for (int i = 0; i < this.parents[_yy].length; i++) {
                            if (_xx == this.parents[_yy][i]) {
                                this.coefs[_yy][i] = first;
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < size; i++) {
            this.errorVars[i] = errorCovarDist.nextRandom();
            this.means[i] = meanDist.nextRandom();
        }

        this.alreadySetUp = true;
    }

    public TetradAlgebra getAlgebra() {
        if (this.algebra == null) {
            this.algebra = new TetradAlgebra();
        }

        return this.algebra;
    }

    public Graph getGraph() {
        return this.graph;
    }

    public void setCoefRange(final double coefLow, final double coefHigh) {
        this.coefLow = coefLow;
        this.coefHigh = coefHigh;
    }

    public void setVarRange(final double varLow, final double varHigh) {
        this.varLow = varLow;
        this.varHigh = varHigh;
    }

    public void setMeanRange(final double meanLow, final double meanHigh) {
        this.meanLow = meanLow;
        this.meanHigh = meanHigh;
    }

    public void setOut(final PrintStream out) {
        this.out = out;
    }

    public PrintStream getOut() {
        return this.out;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public double[][] getCoefficientMatrix() {
        final double[][] c = new double[this.coefs.length][this.coefs.length];

        for (int i = 0; i < this.coefs.length; i++) {
            for (int j = 0; j < this.coefs[i].length; j++) {
                c[i][this.parents[i][j]] = this.coefs[i][j];
            }
        }

        return c;
    }

    public List<Node> getVariableNodes() {
        return this.variableNodes;
    }

    // returnSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    private List<List<Node>> returnSimilarPairs(final Node x, final Node y, final IKnowledge knowledge) {
        System.out.println("$$$$$ Entering returnSimilarPairs method with x,y = " + x + ", " + y);
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return new ArrayList<>();
        }
        final int ntiers = knowledge.getNumTiers();
        final int indx_tier = knowledge.isInWhichTier(x);
        final int indy_tier = knowledge.isInWhichTier(y);
        final int tier_diff = Math.max(indx_tier, indy_tier) - Math.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        final List tier_x = knowledge.getTier(indx_tier);
        final List tier_y = knowledge.getTier(indy_tier);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        System.out.println("original independence: " + x + " and " + y);

        if (indx_comp == -1) {
            System.out.println("WARNING: indx_comp = -1!!!! ");
        }
        if (indy_comp == -1) {
            System.out.println("WARNING: indy_comp = -1!!!! ");
        }

        final List<Node> simListX = new ArrayList<>();
        final List<Node> simListY = new ArrayList<>();

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (knowledge.getTier(i).size() == 1) {
                continue;
            }
            final String A;
            final Node x1;
            final String B;
            final Node y1;
            if (indx_tier >= indy_tier) {
                final List tmp_tier1 = knowledge.getTier(i + tier_diff);
                final List tmp_tier2 = knowledge.getTier(i);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) {
                    continue;
                }
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) {
                    continue;
                }
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) {
                    continue;
                }
                x1 = this.graph.getNode(A);
                y1 = this.graph.getNode(B);
                System.out.println("Adding pair to simList = " + x1 + " and " + y1);
                simListX.add(x1);
                simListY.add(y1);
            } else {
                final List tmp_tier1 = knowledge.getTier(i);
                final List tmp_tier2 = knowledge.getTier(i + tier_diff);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) {
                    continue;
                }
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) {
                    continue;
                }
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) {
                    continue;
                }
                x1 = this.graph.getNode(A);
                y1 = this.graph.getNode(B);
                System.out.println("Adding pair to simList = " + x1 + " and " + y1);
                simListX.add(x1);
                simListY.add(y1);
            }
        }

        final List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return (pairList);
    }

    public String getNameNoLag(final Object obj) {
        final String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else {
            return tempS.substring(0, tempS.indexOf(':'));
        }
    }

    public IKnowledge getKnowledge(final Graph graph) {
        final int numLags;
        final List<Node> variables = graph.getNodes();
        final List<Integer> laglist = new ArrayList<>();
        final IKnowledge knowledge = new Knowledge2();
        int lag;
        for (final Node node : variables) {
            final String varName = node.getName();
            final String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
                laglist.add(lag);
            }
        }
        numLags = Collections.max(laglist);

        Collections.sort(variables, new Comparator<Node>() {
            @Override
            public int compare(final Node o1, final Node o2) {
                final String name1 = getNameNoLag(o1);
                final String name2 = getNameNoLag(o2);

                final String prefix1 = LargeScaleSimulation.getPrefix(name1);
                final String prefix2 = LargeScaleSimulation.getPrefix(name2);

                final int index1 = LargeScaleSimulation.getIndex(name1);
                final int index2 = LargeScaleSimulation.getIndex(name2);

                if (LargeScaleSimulation.getLag(o1.getName()) == LargeScaleSimulation.getLag(o2.getName())) {
                    if (prefix1.compareTo(prefix2) == 0) {
                        return Integer.compare(index1, index2);
                    } else {
                        return prefix1.compareTo(prefix2);
                    }
                } else {
                    return LargeScaleSimulation.getLag(o1.getName()) - LargeScaleSimulation.getLag(o2.getName());
                }
            }
        });

//        System.out.println("Variable list after the sort = " + variables);
        for (final Node node : variables) {
            final String varName = node.getName();
            final String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
            }

            knowledge.addToTier(numLags - lag, node.getName());
        }

        //System.out.println("Knowledge in graph = " + knowledge);
        return knowledge;
    }

    public static String getPrefix(final String s) {
        return s.substring(0, 1);
    }

    public static int getIndex(final String s) {
        int y = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            try {
                y = Integer.parseInt(s.substring(i));
            } catch (final NumberFormatException e) {
                return y;
            }
        }
        throw new IllegalArgumentException("Not integer suffix.");
    }

    public static int getLag(final String s) {
        if (s.indexOf(':') == -1) {
            return 0;
        }
        final String tmp = s.substring(s.indexOf(':') + 1);
        return (Integer.parseInt(tmp));
    }

    public double[][] getUncorrelatedGaussianShocks(final int sampleSize) {
        final NormalDistribution normal = new NormalDistribution(new Well1024a(++this.seed), 0, 1);

        final int numVars = this.variableNodes.size();
        setupModel(numVars);

        final double[][] shocks = new double[sampleSize][numVars];

        for (int i = 0; i < sampleSize; i++) {
            for (int j = 0; j < numVars; j++) {
                shocks[i][j] = normal.sample() * sqrt(this.errorVars[j]);
            }
        }

        return shocks;
    }

    public double[][] getUncorrelatedShocks(final int sampleSize) {
        final AbstractRealDistribution distribution;
        AbstractRealDistribution varDist = null;

        distribution = new NormalDistribution(new Well1024a(++this.seed), 0, 1);
        varDist = new UniformRealDistribution(this.varLow, this.varHigh);

        final int numVars = this.variableNodes.size();
        setupModel(numVars);

        final double[][] shocks = new double[sampleSize][numVars];

        for (int j = 0; j < numVars; j++) {
            final double sd = sqrt(varDist.sample());

            for (int i = 0; i < sampleSize; i++) {
                double sample = distribution.sample();
                sample *= sd;

                if (!this.errorsNormal) {
                    sample = sample * sample;
                }

                shocks[i][j] = sample;
            }
        }

        return shocks;
    }

    public double[][] getSoCalledPoissonShocks(final int sampleSize) {
        final int numVars = this.variableNodes.size();
        setupModel(numVars);

        final double[][] shocks = new double[sampleSize][numVars];

        for (int j = 0; j < numVars; j++) {
            int v = 0;

            for (int i = 0; i < sampleSize; i++) {
                if (RandomUtil.getInstance().nextDouble() < 0.3) {
                    v = 1 - v;
                }

                shocks[i][j] = v + RandomUtil.getInstance().nextNormal(0, 0.1);
            }
        }

        return shocks;
    }

    public void setIncludePositiveCoefs(final boolean includePositiveCoefs) {
        this.includePositiveCoefs = includePositiveCoefs;
    }

    public void setIncludeNegativeCoefs(final boolean includeNegativeCoefs) {
        this.includeNegativeCoefs = includeNegativeCoefs;
    }

    public boolean isErrorsNormal() {
        return this.errorsNormal;
    }

    public void setErrorsNormal(final boolean errorsNormal) {
        this.errorsNormal = errorsNormal;
    }

//    public boolean isErrorsPositivelySkewedIfNonNormal() {
//        return errorsPositivelySkewedIfNonNormal;
//    }
//
//    public void setErrorsPositivelySkewedIfNonNormal(boolean errorsPositivelySkewedIfNonNormal) {
//        this.errorsPositivelySkewedIfNonNormal = errorsPositivelySkewedIfNonNormal;
//    }

    public double getSelfLoopCoef() {
        return this.selfLoopCoef;
    }

    public void setSelfLoopCoef(final double selfLoopCoef) {
        this.selfLoopCoef = selfLoopCoef;
    }
}
