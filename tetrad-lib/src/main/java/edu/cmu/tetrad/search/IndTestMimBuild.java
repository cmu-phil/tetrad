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
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

//import edu.cmu.tetrad.sem.MimBuildEstimator;

/**
 * Checks the independence of latent variables in a measurement model by constructing estimating its parameters under
 * the special conditions illustrated by Figure 12.18 of CPS, 2nd edition. This is basically a test of the rank of the
 * covariance matrix of latents.
 *
 * @author Ricardo Silva
 * @deprecated
 */
public final class IndTestMimBuild implements IndependenceTest {
    public static final int MIMBUILD_MLE = 0;
    public static final int MIMBUILD_2SLS = 1;
    public static final int MIMBUILD_BOOTSTRAP = 2;

    public static final int MIMBUILD_GES_ABIC = 0;
    public static final int MIMBUILD_GES_SBIC = -1;
    public static final int MIMBUILD_PC = 1;

    /**
     * The dataset passed in through the constructor.
     */
    private DataSet dataSet;

    /**
     * The covariance matrix of <code>dataSet</code>.
     */
    private ICovarianceMatrix covMatrix;

    /**
     * The variables of <code>dataSet</code>.
     */
    private List<Node> vars;

    private IKnowledge measurements;

    private List<String> latents;

    private SemGraph graph;

    /**
     * The significance level of the independence tests.
     */
    private double sig = Double.NaN;

    private Hashtable measureTable;

    private int testType;

    private int algorithmType;

    private int numBootstrapSamples;

    private double bootstrapSamples[][][];

    /**
     * Constructs a new IndependenceChecker which checks independence facts based on a correlation matrix.
     *
     * @param dataSet a DataSet containing a continuous data set.
     * @param sig     the significance level of the tests.
     */

    public IndTestMimBuild(DataSet dataSet, double sig,
                           Clusters measurements) {

        setData(dataSet);
        vars = dataSet.getVariables();
        latents = new ArrayList();
        measureTable = new Hashtable();
        setMeasurementsSource(measurements);
        setSignificance(sig);
        this.testType = MIMBUILD_MLE;
        this.algorithmType = MIMBUILD_GES_ABIC;
        numBootstrapSamples = 100;
    }

    public IndTestMimBuild(ICovarianceMatrix covMatrix, double sig,
                           Clusters measurements) {
        setCovMatrix(covMatrix);
        vars = covMatrix.getVariables();
        latents = new ArrayList();
        measureTable = new Hashtable();
        setMeasurementsSource(measurements);
        setSignificance(sig);
        this.testType = MIMBUILD_MLE;
        this.algorithmType = MIMBUILD_GES_ABIC;
        numBootstrapSamples = 100;
    }

    /**
     * Required by IndependenceTest.
     */
    public IndependenceTest indTestSubset(List vars) {
        throw new UnsupportedOperationException();
    }

    public List<String> getAllVariablesStrings() {
        List<String> list = new LinkedList<String>();

        KnowledgeEdge temp;
        Iterator<KnowledgeEdge> it = measurements.requiredEdgesIterator();

        while (it.hasNext()) {
            temp = it.next();
            String x = temp.getFrom();
            String y = temp.getTo();
            if (list.indexOf(x) == -1) {
                list.add(x);
            }
            list.add(y);
        }

        return list;
    }

    public List<Node> getVariableList() {
        List<String> listNames = new LinkedList<String>();
        List<Node> outputList = new LinkedList<Node>();

        Iterator<KnowledgeEdge> it = measurements.requiredEdgesIterator();
        KnowledgeEdge temp;

        while (it.hasNext()) {
            temp = it.next();
            String x = temp.getFrom();
            String y = temp.getTo();
            if (listNames.indexOf(x) == -1) {
                listNames.add(x);
                outputList.add(new ContinuousVariable(x));
            }
            if (listNames.indexOf(y) == -1) {
                listNames.add(y);
                outputList.add(new ContinuousVariable(y));
            }
        }

        return outputList;
    }

    public void setData(DataSet dataSet) {
        this.dataSet = dataSet;
        covMatrix = new CovarianceMatrix(dataSet);
    }

    public DataSet getData() {
        return dataSet;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return null;
    }

    @Override
    public List<DataSet> getDataSets() {
        return null;
    }

    @Override
    public int getSampleSize() {
        return 0;
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }

    @Override
    public double getScore() {
        return getPValue();
    }

    public void setCovMatrix(ICovarianceMatrix covMatrix) {
        this.covMatrix = covMatrix;
    }

    public ICovarianceMatrix getCovMatrix() {
        return covMatrix;
    }

    /**
     * Method setNumBootstrapSamples
     */
    public void setNumBootstrapSamples(int numSamples) {
        numBootstrapSamples = numSamples;
    }

    public int getNumBootstrapSamples() {
        return numBootstrapSamples;
    }

    /**
     * Method getMeasurements
     */
    public IKnowledge getMeasurements() {
        return measurements;
    }

    public void initMeasurements() {
        latents.clear();
        measureTable.clear();

        Iterator<KnowledgeEdge> it = measurements.requiredEdgesIterator();
        while (it.hasNext()) {
            KnowledgeEdge temp = it.next();
            String x = temp.getFrom(); // this the latent
            String y = temp.getTo(); // this is the measured
            if (!measureTable.containsKey(x)) {
                latents.add(x);
                List measures = new ArrayList();
                measures.add(y);
                measureTable.put(x, measures);
            } else {
                ((List) measureTable.get(x)).add(y);
            }
        }
    }

    public void setMeasurementsSource(Clusters clusters) {
        measurements = new Knowledge2();
        Node included_latents[] = new Node[clusters.getNumClusters()];
        for (String varName : clusters.getClusters().keySet()) {
            Object relatedClusters = clusters.getClusters().get(varName);
            List listRelated;
            if (relatedClusters instanceof Integer) {
                listRelated = new ArrayList();
                listRelated.add(relatedClusters);
            } else {
                listRelated = (List) relatedClusters;
            }

            for (Object aListRelated : listRelated) {
                int cluster_id = (Integer) aListRelated;
                String latent_string =
                        MimBuild.LATENT_PREFIX + (cluster_id + 1);
                if (included_latents[cluster_id] == null) {
                    included_latents[cluster_id] = new GraphNode(latent_string);
                    Node tetradNode = included_latents[cluster_id];
                    tetradNode.setNodeType(NodeType.LATENT);
                }
                measurements.setRequired(latent_string, varName);
            }
        }
        initMeasurements();
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     *
     * @param sig the new significance level.
     */
    public void setSignificance(double sig) {

        if ((sig >= 0.0) && (sig <= 1.0)) {
            this.sig = sig;
        } else {
            throw new IllegalArgumentException("Significance out of range.");
        }
    }

    /**
     * Gets the getModel significance level.
     *
     * @return this number.
     */
    public double getSignificance() {
        return sig;
    }

    /*public void setIndTestType(int testType) {

        if (testType == MIMBUILD_MLE || testType == MIMBUILD_2SLS || testType == MIMBUILD_BOOTSTRAP)
            this.testType = testType;
        else
            throw new IllegalArgumentException("Invalid independence test.");
    }*/

    public void setAlgorithmType(int algoType) {

        if (algoType == MIMBUILD_GES_SBIC || algoType == MIMBUILD_GES_ABIC ||
                algoType == MIMBUILD_PC) {
            this.algorithmType = algoType;
        } else {
            throw new IllegalArgumentException("Invalid algorithm test.");
        }
    }

    /*public int getIndTestType() {
        return testType;
    }*/

    public int getAlgorithmType() {
        return algorithmType;
    }

    /**
     * @return this list of variables.
     */
    public List<Node> getVariables() {
        return Collections.unmodifiableList(vars);
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning varNames z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning varNames.
     * @return true iff x _||_ y | z.
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        throw new UnsupportedOperationException(); // Need to remove dependendence on PAL.
    }
//        int indices0[] = {0, 5, 6, 7, 10, 11};
//        int indices1[] = {1, 2, 4};
//        int indices2[] = {3, 9};
//        double corr = 0;
//        for (int p = 0; p < indices0.length; p++)
//            for (int q = 0; q < indices0.length; q++)
//                corr += covMatrix.getValue(indices0[p], indices0[q]);
//        System.out.println(corr / (indices0.length * indices0.length));
//        corr = 0;
//        for (int p = 0; p < indices1.length; p++)
//            for (int q = 0; q < indices0.length; q++)
//                corr += covMatrix.getValue(indices1[p], indices0[q]);
//        System.out.print(corr / (indices1.length * indices0.length) + " ");
//        corr = 0;
//        for (int p = 0; p < indices1.length; p++)
//            for (int q = 0; q < indices1.length; q++)
//                corr += covMatrix.getValue(indices1[p], indices1[q]);
//        System.out.println(corr / (indices1.length * indices1.length));
//        corr = 0;
//        for (int p = 0; p < indices2.length; p++)
//            for (int q = 0; q < indices0.length; q++)
//                corr += covMatrix.getValue(indices2[p], indices0[q]);
//        System.out.print(corr / (indices2.length * indices0.length) + " ");
//        corr = 0;
//        for (int p = 0; p < indices2.length; p++)
//            for (int q = 0; q < indices1.length; q++)
//                corr += covMatrix.getValue(indices2[p], indices1[q]);
//        System.out.print(corr / (indices2.length * indices1.length) + " ");
//        corr = 0;
//        for (int p = 0; p < indices2.length; p++)
//            for (int q = 0; q < indices2.length; q++)
//                corr += covMatrix.getValue(indices2[p], indices2[q]);
//        System.out.println(corr / (indices2.length * indices2.length));
//        System.exit(0);

        // precondition:  this.varNames, this.latents properly set up.
        //                also, all these variables belong to latents.
        //
        // PROCEDURE:
        // create a measurement model composed of x, items measured by x,
        // y and items measured by y, complete graph among variables in z,
        // adding directed edges from all members of z to x and y, and
        // finally adding an edge from x to y. A chi-squared score suggested
        // by Bollen (1989, page 110) is used to test the significance of this
        // edge. If it passes, it returns false.

//        System.out.println(
//                "\n\n************************************************");
//        System.out.println(" Testing " + x + " against " + y);
//        System.out.print(" Conditional on " + z);
//        if (z.size() > 0) {
//            for (Node node : z) {
//                System.out.print(node);
//            }
//        }
//        else {
//            System.out.print("empty set");
//        }
//        System.out.println();
//        System.out.println("************************************************");
//
//        if (testType == MIMBUILD_BOOTSTRAP) {
//            return isIndependentBootstrap(x, y, z);
//        }
//
//        List<String> subset = new ArrayList<String>();
//
//        Node node_x, node_y, node_z[], measured;
//        String z_names[] = new String[z.size()];
//
//        node_z = new Node[z.size()];
//
//        //        graph = new ProtoSemGraph();
//        graph = new SemGraph();
//
//        // Plug in the latent variables
//        node_x = new GraphNode(x.getName());
//        node_x.setNodeType(NodeType.LATENT);
//        graph.addNode(node_x);
//        node_y = new GraphNode(y.getName());
//        node_y.setNodeType(NodeType.LATENT);
//        graph.addNode(node_y);
//        Iterator it = z.iterator();
//        int i = 0;
//        while (it.hasNext()) {
//            String current_z = it.next().toString();
//            node_z[i] = new GraphNode(current_z);
//            node_z[i].setNodeType(NodeType.LATENT);
//            z_names[i] = current_z;
//            graph.addNode(node_z[i]);
//            graph.addDirectedEdge(node_z[i], node_x);
//            graph.addDirectedEdge(node_z[i], node_y);
//            i++;
//        }
//        for (int p = 0; p < z.size() - 1; p++) {
//            for (int q = p + 1; q < z.size(); q++) {
//                graph.addDirectedEdge(node_z[p], node_z[q]);
//            }
//        }
//
//        // Plug in the observed variables
//        it = ((List) measureTable.get(x.toString())).iterator();
//        while (it.hasNext()) {
//            String next_measure = (String) it.next();
//            measured = new GraphNode(next_measure);
//            measured.setNodeType(NodeType.MEASURED);
//            graph.addNode(measured);
//            graph.addDirectedEdge(node_x, measured);
//            subset.add(next_measure);
//        }
//        it = ((List) measureTable.get(y.toString())).iterator();
//        while (it.hasNext()) {
//            String next_measure = (String) it.next();
//            measured = new GraphNode(next_measure);
//            measured.setNodeType(NodeType.MEASURED);
//            graph.addNode(measured);
//            graph.addDirectedEdge(node_y, measured);
//            subset.add(next_measure);
//        }
//        for (i = 0; i < z.size(); i++) {
//            it = ((List) measureTable.get(z_names[i])).iterator();
//            while (it.hasNext()) {
//                String next_measure = (String) it.next();
//                measured = new GraphNode(next_measure);
//                measured.setNodeType(NodeType.MEASURED);
//                graph.addNode(measured);
//                graph.addDirectedEdge(node_z[i], measured);
//                subset.add(next_measure);
//            }
//        }
//
//        // Finally, compute the chi-squared statistics
//        String v[] = new String[graph.getNodes().size()];
//        int count = 0;
//        for (Node node : graph.getNodes()) {
//            v[count++] = node.getName();
//        }
//        String variables[] = new String[subset.size()];
//        for (int j = 0; j < subset.size(); j++) {
//            variables[j] = subset.get(j);
//        }
//        ICovarianceMatrix newCov = covMatrix.getSubmatrix(variables);
//
//        if (testType == MIMBUILD_MLE) {
////            SemPm pm = new SemPm(new SemGraph(graph));
////            MimBuildEstimator estimator =
////                    MimBuildEstimator.newInstance(newCov, pm);
//////            System.out.println("\nEvaluating model without edge, MLE...");
////            estimator.estimate();
////            SemIm sem = estimator.getEstimatedSem();
////            double prob_wo_edge = sem.getScore();
//////            System.out.println("Prob significance = " + prob_wo_edge);
////
////            graph.addDirectedEdge(node_x, node_y);
////            //            pm = new SemPm(new SemGraph(graph));
////            pm = new SemPm(graph);
////            estimator = MimBuildEstimator.newInstance(newCov, pm);
//////            System.out.println("Evaluating model with edge, MLE...");
////            estimator.estimate();
////            SemIm sem2 = estimator.getEstimatedSem();
////            double prob_w_edge = sem2.getScore();
//////            System.out.println("Prob significance = " + prob_w_edge);
////
////            /*if (prob_wo_edge > sig) {
////                System.out.println("Independent!");
////            }
////            else {
////                System.out.println("NOT independent!");
////            }
////            return (prob_wo_edge > sig);*/
////            double pValue = 1. - ProbUtils.chisqCdf(
////                    sem.getChiSquare() - sem2.getChiSquare(), 1);
////            if (pValue > sig) {
////                TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(x, y, z, pValue));
//////                System.out.println("Independent!");
////            } else {
////                TetradLogger.getInstance().log("dependencies", SearchLogUtils.dependenceFactMsg(x, y, z, pValue));
//////                System.out.println("NOT independent!");
////            }
////            return (pValue > sig);
//        } else if (testType == MIMBUILD_2SLS) {
//            /*graph.addDirectedEdge(node_x, node_y);
//            SemPm pm = new SemPm(new SemGraph(graph));
//            Tsls tsls = new Tsls(pm, covMatrix, y.getName());
//            System.out.println("\nEvaluating model with edge, 2SLS...\n");
//            tsls.estimate();
//            double prob_edge = tsls.getEdgePValue(x.getName());
//            System.out.println("Prob significance = " + prob_edge);
//            if (prob_edge > sig) {
//                System.out.println("Independent!");
//            }
//            else {
//                System.out.println("NOT independent!");
//            }
//            return (prob_edge > sig);*/
//            throw new RuntimeException("Not currently supported!");
//        }
//
//        return true;
//    }
//
    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isIndependent(x, y, zList);
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * This is just an adaptation of the isIndependent method of IndTestCramerT
     */

    public boolean isIndependentBootstrap(Node x, Node y, List<Node> z) {
        // Create index array for the given variables.
        int size = z.size() + 2;
        int[] indices = new int[size];

        indices[0] = latents.indexOf(x);
        indices[1] = latents.indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = latents.indexOf(z.get(i));
        }

        double sum_r = 0., sum_r2 = 0., r;

        for (int iter = 0; iter < numBootstrapSamples; iter++) {
            // Extract submatrix of correlation matrix using this index array.
            double[][] submatrix = new double[size][size];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    submatrix[i][j] =
                            bootstrapSamples[iter][indices[i]][indices[j]];
                }
            }

            // Invert submatrix.
            try {
                submatrix = MatrixUtils.inverse(submatrix);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Matrix singularity detected while using correlations " +
                                "\nto check for independence; probably due to collinearity " +
                                "\nin the data. The independence fact being checked was " +
                                "\n" + x + " _||_ " + y + " | " + z + ".", e);
            }

            r = -1 * submatrix[0][1] /
                    Math.pow(submatrix[0][0] * submatrix[1][1], .5);

            sum_r += r;
            sum_r2 += r * r;
        }

        double mean = sum_r / numBootstrapSamples;
        double variance = sum_r2 / numBootstrapSamples - mean * mean;
        // Determine whether this partial correlation is statistically
        // nondifferent from zero.
//        System.out.println("Statistic: " + (mean / variance));
        return isZeroBootstrap(mean, variance, this.sig);
    }

    boolean isZeroBootstrap(double mean, double variance, double sig) {
        return Math.abs(mean / variance) < 1.96;
    }

    /**
     * Needed for the IndependenceTest interface.  Not meaningful here.
     */
    public double getPValue() {
        return Double.NaN;
    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> variableNames = new ArrayList<String>();

        for (Node variable : variables) {
            variableNames.add(variable.getName());
        }

        return variableNames;
    }

    public boolean determines(List z, Node x1) {
        throw new UnsupportedOperationException(
                "This independence test does not " +
                        "test whether Z determines X for list Z of variable and variable X.");
    }

    public double getAlpha() {
        return sig;
    }

    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);

            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

//    /**
//     * Fixes the coefficient parameter for each latent node to the first of its
//     * measured children, sorted alphabetically. Not used for the moment, since
//     * I changed the code from SemEstimator (which requires fixing the
//     * parameters a priori) to MimBuildEstimator (which fixes the parameters
//     * internally). I'm just keeping this code because it might be useful again
//     * eventually. </p> Ricardo
//     */
//    private void fixParameters(SemPm semPm) {
//        SemGraph graph = semPm.getGraph();
//
//        for (Node node : semPm.getLatentNodes()) {
//            // For each latent node, get its list of children and sort them
//            // alphabetically.
//            Node nodeA = (Node) node;
//            List<Node> children = new ArrayList<Node>(graph.getChildren(nodeA));
//            Collections.sort(children, new Comparator() {
//                public int compare(Object o1, Object o2) {
//                    return ((Node) o1).getName().compareTo(
//                            ((Node) o2).getName());
//                }
//            });
//
//            // Fix the first measured node in the list only.
//            for (Node nodeB : children) {
//                if (nodeB.getNodeType() == NodeType.MEASURED) {
//                    Parameter param = semPm.getParameter(nodeA, nodeB);
//                    param.setFixed(true);
//                    break;
//                }
//            }
//        }
//    }

    public void bootstrap() {
        bootstrapSamples = getBootstrapSamples(numBootstrapSamples);
    }

    private double[][][] getBootstrapSamples(int numSamples) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
//        //        ProtoSemGraph graph = new ProtoSemGraph();
//        SemGraph graph = new SemGraph();
//        DataSet dataContinuous = getData();
//        int totalLatents = latents.size();
//        Node latentsArray[] = new Node[totalLatents];
//        double samples[][][] =
//                new double[numSamples][totalLatents][totalLatents];
//
//        // Insert the latents
//        int count = 0;                   //
//        Iterator it = latents.iterator();
//
//        while (it.hasNext()) {
//            String current_z = (String) it.next();
//            latentsArray[count] = new GraphNode(current_z);
//            latentsArray[count].setNodeType(NodeType.LATENT);
//            graph.addNode(latentsArray[count]);
//            count++;
//        }
//
//        for (int p = 0; p < latents.size() - 1; p++) {
//            for (int q = p + 1; q < latents.size(); q++) {
//                graph.addDirectedEdge(latentsArray[p], latentsArray[q]);
//            }
//        }
//        //Insert the indicators
//        for (Node aLatentsArray : latentsArray) {
//            String key = aLatentsArray.toString();
//            List list = (List) measureTable.get(key);
//            it = list.iterator();
//
//            while (it.hasNext()) {
//                String next_measure = (String) it.next();
//                Node measured = new GraphNode(next_measure);
//                measured.setNodeType(NodeType.MEASURED);
//                graph.addNode(measured);
//                graph.addDirectedEdge(aLatentsArray, measured);
//            }
//        }
//
//        //        SemPm pm = new SemPm(new SemGraph(graph));
//        SemPm pm = new SemPm(graph);
//
//        fixLatentOrder(pm);
//
//        int sampleSize = dataContinuous.getNumRows(), numColumns =
//                dataContinuous.getNumColumns();
////        double dummyData[][] = new double[numColumns][sampleSize];
//        DataSet dummyDataSet =
//                new ColtDataSet(sampleSize, getVariables());
////        count = 0;
////        it = getVariables().iterator();
////        while (it.hasNext()) {
////            ContinuousVariable variable = (ContinuousVariable) it.next();
////            dummyDataSet.addVariable(variable);
////        }
//
//        // Maybe a row shuffle like this could take advantage of COLT...
//        // don't know how quite yet... jdramsey 7/4/05
//        for (int iter = 0; iter < numSamples; iter++) {
//            for (int i = 0; i < sampleSize; i++) {
//                int row =
//                        RandomUtil.getInstance().nextInt(sampleSize);
//                for (int j = 0; j < numColumns; j++) {
////                    Column column = dataContinuous.getColumnObject(j);
////                    double[] rawData = (double[]) column.getRawData();
////                    dummyData[j][i] = rawData[row];
//
//                    dummyDataSet.setDouble(row, j,
//                            dataContinuous.getDouble(row, j));
//                }
//            }
////            System.out.println(
////                    "********\n Estimating latent covariance matrix #" + iter +
////                            "...");
//            MimBuildEstimator estimator =
//                    MimBuildEstimator.newInstance(dummyDataSet, pm);
//            estimator.estimate();
//            // Copy only the latent covariance matrix
//            int row = 0, i = 0;
//            TetradMatrix implCovarC =
//                    estimator.getEstimatedSem().getImplCovar(true);
//            double implCov[][] = implCovarC.toArray();
//            Iterator<Node> vi1 = pm.getVariableNodes().iterator();
//            while (vi1.hasNext()) {
//                Node pmNext1 = vi1.next();
//                if (pmNext1.getNodeType() == NodeType.LATENT) {
//                    int column = 0, j = 0;
//                    Iterator<Node> vi2 = pm.getVariableNodes().iterator();
//                    while (vi2.hasNext()) {
//                        Node pmNext2 = vi2.next();
//                        if (pmNext2.getNodeType() == NodeType.LATENT) {
//                            samples[iter][i][j++] = implCov[row][column];
//                        }
//                        column++;
//                    }
//                    i++;
//                }
//                row++;
//            }
//        }
//        return samples;
    }

    /**
     * Change the order of the elements in the latents list so they correspond to the order of the elements in this
     * semPm. The reason is that we want the position of elements in the bootstrapped latent covariance matrices to
     * correspond to the position of the latents in the latents list
     */

    private void fixLatentOrder(SemPm semPm) {
        List newLatents = new ArrayList(latents.size());

        Iterator<Node> it = semPm.getVariableNodes().iterator();
        while (it.hasNext()) {
            Node pmNext = it.next();
            if (pmNext.getNodeType() == NodeType.LATENT) {
                Iterator it2 = latents.iterator();
                while (it2.hasNext()) {
                    String latentNext = (String) it2.next();
                    if (latentNext.equals(pmNext.getName())) {
                        newLatents.add(latentNext);
                        break;
                    }
                }
            }
        }
        latents = newLatents;
    }

    public String toString() {
        return "MimBuild independence test";
    }
}





