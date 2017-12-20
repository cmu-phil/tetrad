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
import edu.cmu.tetrad.util.*;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Split;
import edu.cmu.tetrad.util.dist.Uniform;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.Well1024a;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.RecursiveTask;

import static java.lang.Math.abs;
import static java.lang.Math.signum;
import static java.lang.Math.sqrt;

/**
 * Stores a SEM model, pared down, for purposes of simulating data sets with
 * large numbers of variables and sample sizes. Assumes acyclicity.
 *
 * @author Joseph Ramsey
 */
public final class LargeScaleSimulationSmithSim {
    static final long serialVersionUID = 23L;

    private int[][] parents;
    private double[][] coefs;
    private double[][] coefs2;
    private double[] errorVars;
    private double[] errorVars2;
    private double[] means;
    private double[] means2;
    private transient TetradAlgebra algebra;
    private List<Node> variableNodes;
    private Graph graph;
    private double coefLow = .2;
    private double coefHigh = 1.5;
    private double varLow = 1.0;
    private double varHigh = 3.0;
    private double meanLow = 0;
    private double meanHigh = 0;
    private PrintStream out = System.out;
    private int[] tierIndices;
    private boolean verbose = false;
    long seed = new Date().getTime();
    private boolean alreadySetUp = false;
    //    private boolean coefSymmetric = false;
    private boolean includePositiveCoefs = true;
    private boolean includeNegativeCoefs = true;

    private boolean errorsNormal = true;
    //    private boolean errorsPositivelySkewedIfNonNormal = true;
    private double betaLeftValue;
    private double betaRightValue;

    //=============================CONSTRUCTORS============================//

    public LargeScaleSimulationSmithSim(Graph graph) {
        this.graph = graph;
        this.variableNodes = graph.getNodes();

        if (graph instanceof SemGraph) {
            ((SemGraph) graph).setShowErrorTerms(false);
        }

        List<Node> causalOrdering = graph.getCausalOrdering();
        this.tierIndices = new int[causalOrdering.size()];
        for (int i = 0; i < tierIndices.length; i++) tierIndices[i] = variableNodes.indexOf(causalOrdering.get(i));
    }

    public LargeScaleSimulationSmithSim(Graph graph, List<Node> nodes, int[] tierIndices) {
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
        if (tierIndices == null) {
            List<Node> nodes = graph.getNodes();
            tierIndices = new int[nodes.size()];
            for (int j = 0; j < nodes.size(); j++) {
                tierIndices[j] = j;
            }
        }

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

        if (graph instanceof TimeLagGraph) {
            sampleSize += 200;
        }

        double[][] all = new double[variableNodes.size()][sampleSize];

        int chunk = sampleSize / ForkJoinPoolInstance.getInstance().getPool().getParallelism() + 1;

        ForkJoinPoolInstance.getInstance().getPool().invoke(new SimulateTask(0, sampleSize, all, chunk));

        if (graph instanceof TimeLagGraph) {
            int[] rem = new int[200];
            for (int i = 0; i < 200; ++i) {
                rem[i] = i;
            }
            BoxDataSet dat = new BoxDataSet(new VerticalDoubleDataBox(all), variableNodes);
            dat.removeRows(rem);
            return dat;
        }

        return new BoxDataSet(new VerticalDoubleDataBox(all), variableNodes);
    }

    /**
     * Simulates data using the model X = (I - B)Y^-1 * e. Errors are uncorrelated.
     *
     * @param sampleSize The nubmer of samples to draw.
     */
    public DataSet simulateDataReducedForm(int sampleSize) {
        if (sampleSize < 1) throw new IllegalArgumentException(
                "Sample size must be >= 1: " + sampleSize);

        int size = variableNodes.size();
        setupModel(size);

        NormalDistribution normal = new NormalDistribution(new Well1024a(++seed), 0, 1);

        TetradMatrix B = new TetradMatrix(getCoefficientMatrix());
        TetradMatrix iMinusBInv = TetradAlgebra.identity(B.rows()).minus(B).inverse();

        double[][] all = new double[variableNodes.size()][sampleSize];

        for (int row = 0; row < sampleSize; row++) {
            TetradVector e = new TetradVector(B.rows());

            for (int j = 0; j < e.size(); j++) {
                e.set(j, normal.sample() * sqrt(errorVars[j]));
            }

            TetradVector x = iMinusBInv.times(e);

            for (int j = 0; j < x.size(); j++) {
                all[j][row] = x.get(j);
            }
        }

        List<Node> continuousVars = new ArrayList<>();

        for (Node node : getVariableNodes()) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);
        return DataUtils.restrictToMeasured(boxDataSet);
    }

    /**
     * Simulates data using the model of R. A. Fisher, for a linear model. Shocks are
     * applied every so many steps. A data point is recorded before each shock is
     * administered. If convergence happens before that number of steps has been reached,
     * a data point is recorded and a new shock immediately applied. The model may be
     * cyclic. If cyclic, all eigenvalues for the coefficient matrix must be less than 1,
     * though this is not checked. Uses an interval between shocks of 50 and a convergence
     * threshold of 1e-5. Uncorrelated Gaussian shocks are used.
     *
     * @param sampleSize The number of samples to be drawn. Must be a positive
     *                   integer.
     */
    public DataSet simulateDataFisher(int sampleSize) {
        return simulateDataFisher(getSoCalledPoissonShocks(sampleSize), 50, 1e-5);
    }

    /**
     * Simulates data using the model of R. A. Fisher, for a linear model. Shocks are
     * applied every so many steps. A data point is recorded before each shock is
     * administered. If convergence happens before that number of steps has been reached,
     * a data point is recorded and a new shock immediately applied. The model may be
     * cyclic. If cyclic, all eigenvalues for the coefficient matrix must be less than 1,
     * though this is not checked.
     *
     * @param shocks                A matrix of shocks. The value at shocks[i][j] is the shock
     *                              for the i'th time step, for the j'th variables.
     * @param intervalBetweenShocks External shock is applied every this many steps.
     *                              Must be positive integer.
     * @param epsilon               The convergence criterion; |xi.t - xi.t-1| < epsilon.
     */
    public DataSet simulateDataFisher(double[][] shocks, int intervalBetweenShocks, double epsilon) {
        if (intervalBetweenShocks < 1) throw new IllegalArgumentException(
                "Interval between shocks must be >= 1: " + intervalBetweenShocks);
        if (epsilon <= 0.0) throw new IllegalArgumentException(
                "Epsilon must be > 0: " + epsilon);

        int size = variableNodes.size();
        if (shocks[0].length != size) {
            throw new IllegalArgumentException("The number of columns in the shocks matrix does not equal " +
                    "the number of variables.");
        }

        setupModel(size);
        setupModel2(size);

        double[] t1 = new double[variableNodes.size()];
        double[] t2 = new double[variableNodes.size()];
        double[][] all = new double[variableNodes.size()][shocks.length];

        // Do the simulation.
        for (int row = 0; row < shocks.length; row++) {
            for (int j = 0; j < t1.length; j++) {
                t2[j] = shocks[row][j];
            }

            for (int i = 0; i < intervalBetweenShocks; i++) {
                for (int j = 0; j < t1.length; j++) {
                    for (int k = 0; k < parents[j].length; k++) {
                        t2[j] += t1[parents[j][k]] * coefs[j][k];
                    }
                }

                boolean converged = true;

                for (int j = 0; j < t1.length; j++) {
                    if (abs(t2[j] - t1[j]) > epsilon) {
                        converged = false;
                        break;
                    }
                }

                double[] t3 = t1;
                t1 = t2;
                t2 = t3;

                if (converged) {
                    break;
                }
            }

            for (int j = 0; j < t1.length; j++) {
                all[j][row] = t1[j];
            }
        }

        List<Node> continuousVars = new ArrayList<>();

        for (Node node : getVariableNodes()) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);
        return DataUtils.restrictToMeasured(boxDataSet);
    }

    public DataSet simulateDataFisher(int intervalBetweenShocks, int intervalBetweenRecordings, int sampleSize, double epsilon) {
        if (intervalBetweenShocks < 1) throw new IllegalArgumentException(
                "Interval between shocks must be >= 1: " + intervalBetweenShocks);
        if (epsilon <= 0.0) throw new IllegalArgumentException(
                "Epsilon must be > 0: " + epsilon);

        int size = variableNodes.size();

        setupModel(size);
        setupModel2(size);

        // Temporary to mimic Simulation 22 from Smith et al.
        double[][] states = new double[variableNodes.size()][sampleSize];

        for (int v = 0; v < variableNodes.size(); v++) {
            for (int t = 1; t < sampleSize; t++) {
                if (states[v][t - 1] == 0) { //if the previous data point is zero.
                    if (RandomUtil.getInstance().nextDouble() < .1)
                        states[v][t] = 1;
                    else {
                        states[v][t] = 0;
                    }
                } else if (states[v][t - 1] == 1) { //if the previous data point is one.
                    if (RandomUtil.getInstance().nextDouble() < .1)
                        states[v][t] = 0;
                    else {
                        states[v][t] = 1;
                    }
                }
            }
        }

        double[] t1 = new double[variableNodes.size()];
        double[] t2 = new double[variableNodes.size()];
        double[][] all = new double[variableNodes.size()][sampleSize];

        int s = 0;
        int shockIndex = 0;
        int recordingIndex = 0;
        double[] shock = getUncorrelatedShocks(1)[0];

        while (s < sampleSize) {
            if ((++shockIndex) % intervalBetweenShocks == 0) {
                shock = getUncorrelatedShocks(1)[0];
            }

            if ((++recordingIndex) % intervalBetweenRecordings == 0) {
                for (int j = 0; j < t1.length; j++) {
                    all[j][s] = t1[j];
                }

                s++;
            }

            for (int j = 0; j < t1.length; j++) {
                t2[j] = shock[j];
            }

            double inStrengthStd = .5;
            double inStrengthMean = .5;

            for (int j = 0; j < t1.length; j++) {
                t2[j] = shock[j];
                for (int k = 0; k < parents[j].length; k++) {
                    double coef = 0.0;
                    double stim = 0.0;

                    if (s < sampleSize) {
                        if (states[k][s] == 0) {
                            coef = coefs[j][k];
//                            stim = RandomUtil.getInstance().nextNormal(0, inStrengthStd);
                        } else {
                            coef = coefs2[j][k];


                            coef = signum(coefs[j][k]) * abs(coef);
//                            coef = 0;
//                            stim = RandomUtil.getInstance().nextNormal(inStrengthMean, inStrengthStd);
                        }

//                        coef = coefs[j][k] + stim * coefs2[j][k];

//                        if (coef < 0.2) coef = 0.2;
//                        if (coef > 0.6) coef = 0.6;
                    }


//                    System.out.println(coef);

//                    double coef = coefs[j][k];
//                    double _stim = s < sampleSize ? stim[s][k] : 0;
                    t2[j] = t2[j] + (t1[parents[j][k]]) * coef;
                }
            }

            double[] t3 = t1;
            t1 = t2;
            t2 = t3;
        }

        List<Node> continuousVars = new ArrayList<>();

        for (Node node : getVariableNodes()) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(all), continuousVars);
        return DataUtils.restrictToMeasured(boxDataSet);
    }

    private void setupModel(int size) {
        if (alreadySetUp) return;

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
        Distribution meanDist = new Uniform(meanLow, meanHigh);

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

            double coef = edgeCoefDist.nextRandom();
//            if (coefSymmetric) coef = Math.abs(coef);

            if (includePositiveCoefs && !includeNegativeCoefs) {
                coef = abs(coef);
            } else if (!includePositiveCoefs && includeNegativeCoefs) {
                coef = -abs(coef);
            } else if (!includePositiveCoefs && !includeNegativeCoefs) {
                coef = 0;
            }

            newCoefs[newCoefs.length - 1] = coef;

            this.parents[_head] = newParents;
            this.coefs[_head] = newCoefs;
        }

        if (graph instanceof TimeLagGraph) {
            TimeLagGraph lagGraph = (TimeLagGraph) graph;
            IKnowledge knowledge = getKnowledge(lagGraph); //TimeSeriesUtils.getKnowledge(lagGraph);
            List<Node> lag0 = lagGraph.getLag0Nodes();

            for (Node y : lag0) {
                List<Node> _parents = lagGraph.getParents(y);

                for (Node x : _parents) {
                    List<List<Node>> similar = returnSimilarPairs(x, y, knowledge);

                    int _x = variableNodes.indexOf(x);
                    int _y = variableNodes.indexOf(y);
                    double first = Double.NaN;

                    for (int i = 0; i < parents[_y].length; i++) {
                        if (_x == parents[_y][i]) {
                            first = coefs[_y][i];
                        }
                    }

                    for (int j = 0; j < similar.get(0).size(); j++) {
                        int _xx = variableNodes.indexOf(similar.get(0).get(j));
                        int _yy = variableNodes.indexOf(similar.get(1).get(j));

                        for (int i = 0; i < parents[_yy].length; i++) {
                            if (_xx == parents[_yy][i]) {
                                coefs[_yy][i] = first;
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

        alreadySetUp = true;
    }

    private void setupModel2(int size) {
//        if (alreadySetUp) return;

        Map<Node, Integer> nodesHash = new HashedMap<>();

        for (int i = 0; i < variableNodes.size(); i++) {
            nodesHash.put(variableNodes.get(i), i);
        }

//        this.parents = new int[size][];
        this.coefs2 = new double[size][];
        this.errorVars2 = new double[size];
        this.means2 = new double[size];

        for (int i = 0; i < size; i++) {
//            this.parents[i] = new int[0];
            this.coefs2[i] = new double[0];
        }

        Distribution edgeCoefDist = new Split(coefLow, coefHigh);
        Distribution errorCovarDist = new Uniform(varLow, varHigh);
        Distribution meanDist = new Uniform(meanLow, meanHigh);

        for (Edge edge : graph.getEdges()) {
            Node tail = Edges.getDirectedEdgeTail(edge);
            Node head = Edges.getDirectedEdgeHead(edge);

//            int _tail = nodesHash.get(tail);
            int _head = nodesHash.get(head);

//            int[] parents = this.parents[_head];
//            int[] newParents = new int[parents.length + 1];
//            System.arraycopy(parents, 0, newParents, 0, parents.length);
//            newParents[newParents.length - 1] = _tail;
            double[] coefs = this.coefs2[_head];
            double[] newCoefs = new double[coefs.length + 1];

            System.arraycopy(coefs, 0, newCoefs, 0, coefs.length);

            double coef = edgeCoefDist.nextRandom();
//            if (coefSymmetric) coef = Math.abs(coef);

            if (includePositiveCoefs && !includeNegativeCoefs) {
                coef = abs(coef);
            } else if (!includePositiveCoefs && includeNegativeCoefs) {
                coef = -abs(coef);
            } else if (!includePositiveCoefs && !includeNegativeCoefs) {
                coef = 0;
            }

            newCoefs[newCoefs.length - 1] = coef;

//            this.parents[_head] = newParents;
            this.coefs2[_head] = newCoefs;
        }

        for (int i = 0; i < size; i++) {
            this.errorVars2[i] = errorCovarDist.nextRandom();
            this.means2[i] = meanDist.nextRandom();
        }

//        alreadySetUp = true;
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

    public void setMeanRange(double meanLow, double meanHigh) {
        this.meanLow = meanLow;
        this.meanHigh = meanHigh;
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

    public double[][] getCoefficientMatrix() {
        double[][] c = new double[coefs.length][coefs.length];

        for (int i = 0; i < coefs.length; i++) {
            for (int j = 0; j < coefs[i].length; j++) {
                c[i][parents[i][j]] = coefs[i][j];
            }
        }

        return c;
    }

    public List<Node> getVariableNodes() {
        return variableNodes;
    }

    // returnSimilarPairs based on orientSimilarPairs in TsFciOrient.java by Entner and Hoyer
    private List<List<Node>> returnSimilarPairs(Node x, Node y, IKnowledge knowledge) {
        System.out.println("$$$$$ Entering returnSimilarPairs method with x,y = " + x + ", " + y);
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return new ArrayList<>();
        }
//        System.out.println("Knowledge within returnSimilar : " + knowledge);
        int ntiers = knowledge.getNumTiers();
        int indx_tier = knowledge.isInWhichTier(x);
        int indy_tier = knowledge.isInWhichTier(y);
        int tier_diff = Math.max(indx_tier, indy_tier) - Math.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List tier_x = knowledge.getTier(indx_tier);
//        Collections.sort(tier_x);
        List tier_y = knowledge.getTier(indy_tier);
//        Collections.sort(tier_y);

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

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");


        List<Node> simListX = new ArrayList<>();
        List<Node> simListY = new ArrayList<>();

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (knowledge.getTier(i).size() == 1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            if (indx_tier >= indy_tier) {
                List tmp_tier1 = knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier1);
                List tmp_tier2 = knowledge.getTier(i);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = graph.getNode(A);
                y1 = graph.getNode(B);
                System.out.println("Adding pair to simList = " + x1 + " and " + y1);
                simListX.add(x1);
                simListY.add(y1);
            } else {
                //System.out.println("############## WARNING (returnSimilarPairs): did not catch x,y pair " + x + ", " + y);
                //System.out.println();
                List tmp_tier1 = knowledge.getTier(i);
//                Collections.sort(tmp_tier1);
                List tmp_tier2 = knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = graph.getNode(A);
                y1 = graph.getNode(B);
                System.out.println("Adding pair to simList = " + x1 + " and " + y1);
                simListX.add(x1);
                simListY.add(y1);
            }
        }

        List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return (pairList);
    }

    public String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }

    public IKnowledge getKnowledge(Graph graph) {
//        System.out.println("Entering getKnowledge ... ");
        int numLags; // need to fix this!
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
        if (s.indexOf(':') == -1) return 0;
        String tmp = s.substring(s.indexOf(':') + 1, s.length());
        return (Integer.parseInt(tmp));
    }

    public double[][] getUncorrelatedGaussianShocks(int sampleSize) {
        NormalDistribution normal = new NormalDistribution(new Well1024a(++seed), 0, 1);

        int numVars = variableNodes.size();
        setupModel(numVars);

        double[][] shocks = new double[sampleSize][numVars];

        for (int i = 0; i < sampleSize; i++) {
            for (int j = 0; j < numVars; j++) {
                shocks[i][j] = normal.sample() * sqrt(errorVars[j]);
            }
        }

        return shocks;
    }

    public double[][] getUncorrelatedShocks(int sampleSize) {
        AbstractRealDistribution distribution;

        if (errorsNormal) {
            distribution = new NormalDistribution(new Well1024a(++seed), 0, 1);
        } else {
            distribution = new BetaDistribution(new Well1024a(++seed), getBetaLeftValue(), getBetaRightValue());
//            distribution = new UniformRealDistribution(0, 1);
        }

//        TDistribution dist = new TDistribution(new Well1024a(++seed), 6);
//        BetaDistribution dist = new BetaDistribution(new Well1024a(++seed), 2, 5);
//        NormalDistribution dist = new NormalDistribution(new Well1024a(++seed), 0, 1);
//        UniformRealDistribution dist = new UniformRealDistribution(0, 1);

        int numVars = variableNodes.size();
        setupModel(numVars);

        double[][] shocks = new double[sampleSize][numVars];

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i < sampleSize; i++) {
                double sample = distribution.sample();

//                if (!errorsNormal) {
//                    sample = Math.pow(sample, 3);
//                }

//                if (!errorsNormal && !errorsPositivelySkewedIfNonNormal) {
//                    sample *= -1.0;
//                }

//                if (RandomUtil.getInstance().nextDouble() > 0.9) {
//                    sample *= -1.0;
//                }

                shocks[i][j] = sample;
            }
        }

        return shocks;
    }

    public double[][] getSoCalledPoissonShocks(int sampleSize) {
        int numVars = variableNodes.size();
        setupModel(numVars);

        double[][] shocks = new double[sampleSize][numVars];

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

    public void setIncludePositiveCoefs(boolean includePositiveCoefs) {
        this.includePositiveCoefs = includePositiveCoefs;
    }

    public void setIncludeNegativeCoefs(boolean includeNegativeCoefs) {
        this.includeNegativeCoefs = includeNegativeCoefs;
    }

    public boolean isErrorsNormal() {
        return errorsNormal;
    }

    public void setErrorsNormal(boolean errorsNormal) {
        this.errorsNormal = errorsNormal;
    }

//    public boolean isErrorsPositivelySkewedIfNonNormal() {
//        return errorsPositivelySkewedIfNonNormal;
//    }
//
//    public void setErrorsPositivelySkewedIfNonNormal(boolean errorsPositivelySkewedIfNonNormal) {
//        this.errorsPositivelySkewedIfNonNormal = errorsPositivelySkewedIfNonNormal;
//    }

    public double getBetaRightValue() {
        return betaRightValue;
    }

    public void setBetaRightValue(double betaRightValue) {
        this.betaRightValue = betaRightValue;
    }

    public double getBetaLeftValue() {
        return betaLeftValue;
    }

    public void setBetaLeftValue(double betaLeftValue) {
        this.betaLeftValue = betaLeftValue;
    }
}




