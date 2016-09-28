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
//import edu.cmu.tetrad.search.TimeSeriesUtils;
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

import static java.lang.Math.PI;
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

        int size = variableNodes.size();
        setupModel(size);
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

        int size = variableNodes.size();
        setupModel(size);
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

        if(graph instanceof TimeLagGraph){
            sampleSize += 200;
        }

        double[][] all = new double[variableNodes.size()][sampleSize];

        int chunk = sampleSize / ForkJoinPoolInstance.getInstance().getPool().getParallelism() + 1;

        ForkJoinPoolInstance.getInstance().getPool().invoke(new SimulateTask(0, sampleSize, all, chunk));

        if(graph instanceof TimeLagGraph){
            int [] rem = new int[200];
            for (int i=0;i <200;++i){
                rem[i]=i;
            }
            BoxDataSet dat = new BoxDataSet(new VerticalDoubleDataBox(all), variableNodes);
            dat.removeRows(rem);
            return dat;
        }
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
        if(x.getName().equals("time") || y.getName().equals("time")){
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
        for(i = 0; i < tier_x.size(); ++i) {
            if(getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for(i = 0; i < tier_y.size(); ++i) {
            if(getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        System.out.println("original independence: " + x + " and " + y);

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");


        List<Node> simListX = new ArrayList<>();
        List<Node> simListY = new ArrayList<>();

        for(i = 0; i < ntiers - tier_diff; ++i) {
            if(knowledge.getTier(i).size()==1) continue;
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
        return(pairList);
    }

    public String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if(tempS.indexOf(':')== -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }
    public IKnowledge getKnowledge(Graph graph) {
//        System.out.println("Entering getKnowledge ... ");
        int numLags = 1; // need to fix this!
        List<Node> variables = graph.getNodes();
        List<Integer> laglist = new ArrayList<>();
        IKnowledge knowledge = new Knowledge2();
        int lag;
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if(varName.indexOf(':')== -1){
                lag = 0;
                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':')+1,varName.length());
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
                    return getLag(o1.getName())-getLag(o2.getName());
                }
            }
        });

//        System.out.println("Variable list after the sort = " + variables);

        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if(varName.indexOf(':')== -1){
                lag = 0;
//                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':')+1,varName.length());
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
        return s.substring(0,1);
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
        if(s.indexOf(':')== -1) return 0;
        String tmp = s.substring(s.indexOf(':')+1,s.length());
        return (Integer.parseInt(tmp));
    }

}




