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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;

import java.rmi.MarshalledObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

//import pal.math.ConjugateGradientSearch;
//import pal.math.MFWithGradient;
//import pal.math.OrthogonalHints;

/**
 * Implements a variation of SemEstimator for  pure measurement/structural
 * models. It is mainly used for use along MimBuild (and variations), where
 * computational efficienty is very important (e.g., the class
 * MimBuildScoreSearch has to estimate dozens of measurement/structural models).
 * This is also used for BuildPureClusters, when the user wants to use Gaussian
 * one/two-factor models to test tetrad constraints. </p> The MimBuildEstimator
 * class implements the methods needed to compute the maximum likelihood
 * estimates of the freeParameters of a linear, Gaussian SEM for a given covariance
 * matrix; it contains member variables for storing the results of such a
 * computation. The values of the freeParameters are a search procedure based on
 * gradient descent. The gradient is computed analytically here, which is much
 * faster than previous implementations. </p> IMPORTANT ASSUMPTIONS: this class
 * is currently being used *only* for pure measurement/structural models. When
 * initializing an MimBuildEstimator object, one has to specify which is the
 * measurement model and which is the structural model.
 *
 * @author Ricardo Silva
 */

public class MimBuildEstimator {

    /**
     * Defines the number of iterations
     */
    static int NUM_ITER_TSLS = 10;
    static int NUM_ITER = 20;
    int numIterTsls, numIter;

    /**
     * The SemPm containing the graph and the freeParameters to be estimated.
     */
    private SemPm semPm;

    /**
     * The covariance matrix used to estimate the SemIm. Note that the variables
     * names in the covariance matrix must be in the same order as the variable
     * names in the semPm.
     */
    private ICovarianceMatrix covMatrix;

    /**
     * The most recently estimated model, or null if no model has been estimated
     * yet.
     */
    private SemIm estimatedSem;
    private DataSet dataSet = null;

    /**
     * Absolute tolerance of function value.
     */
    private static final double FUNC_TOLERANCE = 1.0e-4;

    /**
     * Absolute tolerance of each parameter.
     */
    private static final double PARAM_TOLERANCE = 1.0e-3;

    int numIndicators, numLatents, numFixedIndicators;
    Node indicators[], latents[];
    double beta[][], fi[][], iBeta[][], iSigma[][], theta[], bigLambda[][];
    double iMinusB[][], iMinusBT[][], J[][];
    List<int[]> latentParents;
    int indicatorParents[];
    int lambdaIndex[], betaIndex[], indicatorErrorsIndex, latentErrorsIndex;
    double latentImpliedCovar[][], observedImpliedCovar[][], covMatrixValue[][];
    boolean latentImportant[][][];

    //=============================CONSTRUCTORS============================//

    /**
     * A maximum likelihood estimate of the freeParameters of a SEM is done with
     * respect to a sample.  The constructor therefore takes a SEM and a
     * covariance matrix as arguments.
     */

    public MimBuildEstimator(ICovarianceMatrix covMatrix, SemPm semPm,
                             int numIter, int numIterTsls) {

        if (covMatrix == null) {
            throw new NullPointerException("DataWrapper must not be null.");
        }

        if (semPm == null) {
            throw new NullPointerException("Sem PM must not be null.");
        }

        if (!checkPurifiedStructure(semPm.getGraph())) {
            throw new java.lang.RuntimeException(
                    "Input graph should be a pure " +
                            "measurement/structural model.");
        }

        List<Node> latents = new ArrayList<Node>();
        List<Node> measured = new ArrayList<Node>();

        for (Node node : semPm.getVariableNodes()) {
            if (node.getNodeType() == NodeType.LATENT) latents.add(node);
            if (node.getNodeType() == NodeType.MEASURED) measured.add(node);
        }

        if (latents.size() > 0 && measured.size() == 0) {
            throw new IllegalArgumentException("If there are no measured variables, there can't be latents.");
        }

        this.covMatrix = fixVarOrder(semPm, covMatrix);
        covMatrixValue = this.covMatrix.getMatrix().toArray();
        this.semPm = semPm;
        this.numIter = numIter;
        this.numIterTsls = numIterTsls;
        buildMeasurementStructuralModel();
        buildIndexes();
    }

    /**
     * @param semPm   a SemPm specifying the graph and parameterization for the
     *                model.
     * @param dataSet a DataSet, all of whose variables are contained in the
     *                given SemPm. (They are identified by name.)
     * @return a new SemEstimator.
     */

    public static MimBuildEstimator newInstance(DataSet dataSet,
                                                SemPm semPm) {
        MimBuildEstimator me = new MimBuildEstimator(
                new CovarianceMatrix(dataSet), semPm, NUM_ITER, NUM_ITER_TSLS);
        me.dataSet = dataSet;
        return me;
    }

    public static MimBuildEstimator newInstance(DataSet dataSet,
                                                SemPm semPm, int numIter, int numIterTsls) {
        MimBuildEstimator me = new MimBuildEstimator(
                new CovarianceMatrix(dataSet), semPm, numIter, numIterTsls);
        me.dataSet = dataSet;
        return me;
    }


    /**
     * @param semPm     a SemPm specifying the graph and parameterization for
     *                  the model.
     * @param covMatrix a CovarianceMatrix, all of whose variables are contained
     *                  in the given SemPm. (They are identified by name.)
     * @return a new SemEstimator.
     */

    public static MimBuildEstimator newInstance(ICovarianceMatrix covMatrix,
                                                SemPm semPm) {
        return new MimBuildEstimator(covMatrix, semPm, NUM_ITER, NUM_ITER_TSLS);
    }

    public static MimBuildEstimator newInstance(ICovarianceMatrix covMatrix,
                                                SemPm semPm, int numIter, int numIterTsls) {
        return new MimBuildEstimator(covMatrix, semPm, numIter, numIterTsls);
    }

    public static boolean checkPurifiedStructure(Graph graph) {
        return true; //FIXME
    }

    //==============================PRIVATE METHODS========================//

    private void buildMeasurementStructuralModel() {
        List<Node> semPmVars = semPm.getVariableNodes();
        List<Node> indicatorsL = new ArrayList<Node>(semPmVars.size());
        List<Node> latentsL = new ArrayList<Node>(semPmVars.size());

        for (Node nextNode : semPmVars) {
            if (nextNode.getNodeType() == NodeType.LATENT) {
                latentsL.add(nextNode);
            } else {
                indicatorsL.add(nextNode);
            }
        }
        numIndicators = indicatorsL.size();
        numLatents = numFixedIndicators = latentsL.size() > 0 ? latentsL.size() : 0;

        // In principle, there should be one latent fixed per cluster, so long as the clusters aren't empty.
        // I don't know how to express that here, but I'm doing the following to avoid a startup bug.
        // Insisting that the input have no latents if it has no measurements may be a better idea... jdramsey 8/1/2009
        if (numFixedIndicators > numIndicators) numFixedIndicators = numIndicators;
        indicators = new Node[indicatorsL.size()];
        for (int i = 0; i < numIndicators; i++) {
            indicators[i] = indicatorsL.get(i);
        }
        latents = new Node[latentsL.size()];
        for (int i = 0; i < numLatents; i++) {
            latents[i] = latentsL.get(i);
        }
        latentImpliedCovar = new double[numLatents][numLatents];
        observedImpliedCovar = new double[numIndicators][numIndicators];
        latentImportant = new boolean[numLatents][numIndicators][numIndicators];
        for (int l = 0; l < numLatents; l++) {
            for (int i = 0; i < numIndicators; i++) {
                for (int j = 0; j < numIndicators; j++) {
                    latentImportant[l][i][j] = semPm.getGraph().isAncestorOf(
                            latents[l], indicators[i]) && semPm.getGraph()
                            .isAncestorOf(latents[l], indicators[j]);
                }
            }
        }
        iSigma = new double[numIndicators][numIndicators];
        for (int i = 0; i < numIndicators; i++) {
            for (int j = 0; j < numIndicators; j++) {
                if (i == j) {
                    iSigma[i][j] = 1.;
                } else {
                    iSigma[i][j] = 0.;
                }
            }
        }
    }

    private void buildIndexes() {
        boolean fixedIndicators[] = new boolean[numIndicators];
        boolean markedLatents[] = new boolean[numLatents];
        indicatorParents = new int[numIndicators];
        for (int i = 0; i < numLatents; i++) {
            markedLatents[i] = false;
        }
        for (int i = 0; i < numIndicators; i++) {
            for (int j = 0; j < numLatents; j++) {
                if (semPm.getGraph().isParentOf(latents[j], indicators[i])) {
                    indicatorParents[i] = j;
                    if (!markedLatents[j]) {
                        markedLatents[j] = true;
                        fixedIndicators[i] = true;
                    } else {
                        fixedIndicators[i] = false;
                    }
                    break;
                }
            }
        }

        int count = 0;
        lambdaIndex = new int[numIndicators];
        for (int i = 0; i < numIndicators; i++) {
            if (fixedIndicators[i]) {
                lambdaIndex[i] = -1;
            } else {
                lambdaIndex[i] = count++;
            }
        }

        latentParents = new ArrayList<int[]>(numLatents);
        beta = new double[numLatents][numLatents];
        fi = new double[numLatents][numLatents];
        iBeta = new double[numLatents][numLatents];
        for (int i = 0; i < numLatents; i++) {
            List<Integer> parentsL = new LinkedList<Integer>();
            for (int j = 0; j < numLatents; j++) {
                if (semPm.getGraph().isParentOf(latents[j], latents[i])) {
                    parentsL.add(j);
                }
                beta[i][j] = 0.;
                fi[i][j] = 0.;
                if (i == j) {
                    iBeta[i][j] = 1.;
                } else {
                    iBeta[i][j] = 0.;
                }
            }
            int parents[] = new int[parentsL.size()];
            for (int j = 0; j < parentsL.size(); j++) {
                parents[j] = (parentsL.get(j));
            }
            latentParents.add(parents);
        }

        betaIndex = new int[numLatents];

        System.out.println("EE numIndicators = " + numIndicators + " numFixedIndicators = " + numFixedIndicators);

        betaIndex[0] = numIndicators - numFixedIndicators;
        for (int i = 1; i < numLatents; i++) {
            System.out.println("DD betaIndex[i - 1]" + betaIndex[i - 1]);
            System.out.println("DD latentParents.get(i - 1).length = " + latentParents.get(i - 1).length);

            betaIndex[i] = betaIndex[i - 1] + (latentParents.get(i - 1))
                    .length;
        }

//        System.out.println("CC betaIndex[numLatents - 1] = " + betaIndex[numLatents - 1]);
//        System.out.println("CC (latentParents.get(numLatents - 1)).length" + (latentParents.get(numLatents - 1)).length);

        indicatorErrorsIndex = betaIndex[numLatents - 1] +
                (latentParents.get(numLatents - 1)).length;

//        System.out.println("BB indicatorErrorsIndex = " + indicatorErrorsIndex);
//        System.out.println("BB numIndicators = " + numIndicators);

        latentErrorsIndex = indicatorErrorsIndex + numIndicators;
        theta = new double[latentErrorsIndex + numLatents];
        bigLambda = new double[numIndicators][numLatents];
        for (int i = 0; i < numIndicators; i++) {
            for (int j = 0; j < numLatents; j++) {
                bigLambda[i][j] = 0.;
            }
        }
        J = new double[numLatents][numLatents];
        for (int i = 0; i < numLatents; i++) {
            for (int j = 0; j < numLatents; j++) {
                J[i][j] = 0.;
            }
        }
    }

    private void randomizeParameters() {
        for (int i = 0; i < theta.length; i++) {
            if (i < indicatorErrorsIndex) {
                do {
                    theta[i] =
                            RandomUtil.getInstance().nextNormal(0, 1);
                    //theta[i] = r.nextDouble() + 0.1;
                } while (Math.abs(theta[i]) < 0.1);
            } else {
                theta[i] =
                        RandomUtil.getInstance().nextDouble() + 0.1;
            }
            //theta[i] = 1.;
        }
    }

    private void randomizeVariancesOnly() {
        for (int i = indicatorErrorsIndex; i < theta.length; i++) {
//            System.out.println("AA indicatorErrorsIndex = " + indicatorErrorsIndex);
            theta[i] = RandomUtil.getInstance().nextDouble() + 0.1;
        }
    }

    private void initializeByTSLS() {
        randomizeVariancesOnly();
        if (dataSet == null) {
            return;
        }
        List<String> fixedLoadings = new ArrayList<String>();
        for (int i = 0; i < indicators.length; i++) {
            if (lambdaIndex[i] < 0) {
                fixedLoadings.add(indicators[i].getName());
            }
        }
        Tsls tsls = new Tsls(semPm, dataSet, null, fixedLoadings);
        SemIm semIm = tsls.estimate();
        List<Parameter> parameters = semIm.getFreeParameters();

        for (Parameter nextP : parameters) {
            if (nextP.getType() == ParamType.COEF) {
                Node nodeA = nextP.getNodeA();
                Node nodeB = nextP.getNodeB();
                int aIndex = -1, bIndex = -1;
                boolean bIsIndicator = false;
                for (int i = 0; i < numLatents; i++) {
                    if (latents[i].getName().equals(nodeA.getName())) {
                        aIndex = i;
                        break;
                    }
                }
                for (int i = 0; i < numIndicators; i++) {
                    if (indicators[i].getName().equals(nodeB.getName())) {
                        bIsIndicator = true;
                        bIndex = i;
                        break;
                    }
                }
                if (!bIsIndicator) {
                    for (int i = 0; i < numLatents; i++) {
                        if (latents[i].getName().equals(nodeB.getName())) {
                            bIndex = i;
                            break;
                        }
                    }
                }
                if (bIsIndicator) {
                    if (lambdaIndex[bIndex] >= 0) {
                        theta[lambdaIndex[bIndex]] = semIm.getParamValue(nextP);
                    }
                } else {
                    int parents[] = latentParents.get(bIndex);
                    for (int i = 0; i < parents.length; i++) {
                        if (latents[parents[i]].getName().equals(
                                latents[aIndex].getName())) {
                            theta[betaIndex[bIndex] + i] =
                                    semIm.getParamValue(nextP);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Fixes the coefficient parameter for each latent node to the first of its
     * measured children, sorted alphabetically.
     */
    /*private void fixParameters(SemPm semPm) {
        SemGraph2 graph = semPm.getDag();
        for (Iterator it = semPm.getLatentNodes().iterator(); it.hasNext();) {
            // For each latent node, get its list of children and sort them
            // alphabetically.
            Node nodeA = (Node) it.next();
            List children = new ArrayList(graph.getChildren(nodeA));
            Collections.sort(children, new Comparator() {
                public int compare(Object o1, Object o2) {
                    return ((Node) o1).getName().compareTo(((Node) o2).getName());
                }
            });

            // Fix the first measured node in the list only.
            for (int i = 0; i < children.size(); i++) {
                Node nodeB = (Node) children.get(i);

                if (nodeB.getNodeType() == NodeType.MEASURED) {
                    Parameter param = semPm.getParameter(nodeA, nodeB);
                    param.setFixed(true);
                    break;
                }
            }
        }
    }*/
    private void computeImpliedCovar() throws IllegalArgumentException {
        for (int i = 0; i < numLatents; i++) {
            int parents[] = latentParents.get(i);
            for (int j = 0; j < parents.length; j++) {
                beta[i][parents[j]] = theta[betaIndex[i] + j];
            }
            fi[i][i] = theta[latentErrorsIndex + i];
        }
        iMinusB = MatrixUtils.inverse(MatrixUtils.subtract(iBeta, beta));
        iMinusBT = MatrixUtils.transpose(iMinusB);
        latentImpliedCovar =
                MatrixUtils.product(iMinusB, MatrixUtils.product(fi, iMinusBT));
        for (int i = 0; i < numIndicators; i++) {
            for (int j = i; j < numIndicators; j++) {
                double coeff1, coeff2;
                if (lambdaIndex[i] < 0) {
                    coeff1 = 1.;
                } else {
                    coeff1 = theta[lambdaIndex[i]];
                }
                if (lambdaIndex[j] < 0) {
                    coeff2 = 1.;
                } else {
                    coeff2 = theta[lambdaIndex[j]];
                }
                observedImpliedCovar[i][j] = coeff1 * coeff2 *
                        latentImpliedCovar[indicatorParents[i]][indicatorParents[j]];
                if (i == j) {
                    observedImpliedCovar[i][j] +=
                            theta[indicatorErrorsIndex + i];
                } else {
                    observedImpliedCovar[j][i] = observedImpliedCovar[i][j];
                }
            }
        }
    }

    /**
     * Compute the gradient of the likelihood function with respect to the
     * (observed) covariance matrix, storing in a local matrix
     */

    private double[] computeGradient(double gradient[]) {
        //Find the gradient of the loglikelihood with respect to sigma
        computeImpliedCovar();
        try {
            double inverse[][] = MatrixUtils.inverse(observedImpliedCovar);
            double inner_term[][] =
                    MatrixUtils.product(covMatrixValue, inverse);
            double gradient_sigma[][] = MatrixUtils.subtract(inverse,
                    MatrixUtils.product(inverse, inner_term));

            //Compute gradient wrt lambda coefficients and measurement error
            for (int d1 = 0; d1 < numIndicators; d1++) {
                gradient[indicatorErrorsIndex + d1] = gradient_sigma[d1][d1];
                if (lambdaIndex[d1] < 0) {
                    continue;
                }
                gradient[lambdaIndex[d1]] = 0.;
                for (int d2 = 0; d2 < numIndicators; d2++) {
                    double coeff;
                    if (lambdaIndex[d2] < 0) {
                        coeff = 1.;
                    } else {
                        coeff = theta[lambdaIndex[d2]];
                    }
                    gradient[lambdaIndex[d1]] += 2. * gradient_sigma[d1][d2] *
                            coeff *
                            latentImpliedCovar[indicatorParents[d1]][indicatorParents[d2]];
                }
            }

            //Compute gradient wrt beta coefficients and latent variances

            //Initialize some variables
            for (int i = 0; i < numIndicators; i++) {
                if (lambdaIndex[i] < 0) {
                    bigLambda[i][indicatorParents[i]] = 1.;
                } else {
                    bigLambda[i][indicatorParents[i]] = theta[lambdaIndex[i]];
                }
            }
            double deltaB0[][] = MatrixUtils.product(bigLambda, iMinusB);
            double deltaB1[][] = MatrixUtils.transpose(MatrixUtils.product(
                    MatrixUtils.product(deltaB0, fi), iMinusBT));
            double deltaB2[][] = MatrixUtils.transpose(deltaB0);

            for (int i = 0; i < numLatents; i++) {
                int parents[] = latentParents.get(i);
                for (int j = 0; j < parents.length; j++) {
                    gradient[betaIndex[i] + j] = 0.;
                    for (int d1 = 0; d1 < numIndicators; d1++) {
                        for (int d2 = 0; d2 < numIndicators; d2++) {
                            gradient[betaIndex[i] + j] +=
                                    gradient_sigma[d1][d2] * (deltaB0[d1][i] *
                                            deltaB1[parents[j]][d2] +
                                            deltaB0[d2][i] *
                                                    deltaB1[parents[j]][d1]);
                        }
                    }
                }
                gradient[latentErrorsIndex + i] = 0.;
                for (int d1 = 0; d1 < numIndicators; d1++) {
                    for (int d2 = 0; d2 < numIndicators; d2++) {
                        gradient[latentErrorsIndex + i] +=
                                gradient_sigma[d1][d2] * deltaB0[d1][i] *
                                        deltaB2[i][d2];
                    }
                }
            }
        } catch (Exception e) {
            for (int i = 0; i < gradient.length; i++) {
                gradient[i] = 0.;
            }
        }
        return gradient;
    }

    /**
     * @return A submatrix of <code>covMatrix</code> with the order of its
     * variables the same as in <code>semPm</code>.
     * @throws IllegalArgumentException if not all of the variables of
     *                                  <code>semPm</code> are in <code>covMatrix</code>.
     */
    private ICovarianceMatrix fixVarOrder(SemPm semPm,
                                          ICovarianceMatrix covMatrix) {
        List<Node> semPmVars = semPm.getVariableNodes();
        int observedNodes = 0;
        for (Node semPmVar : semPmVars) {
            if (semPmVar.getNodeType() != NodeType.LATENT) {
                observedNodes++;
            }
        }

        int index = 0;
        String[] semPmVarNames = new String[observedNodes];
        for (Node semPmVar1 : semPmVars) {
            if (semPmVar1.getNodeType() != NodeType.LATENT) {
                semPmVarNames[index++] = semPmVar1.toString();
            }
        }

        return covMatrix.getSubmatrix(semPmVarNames);
    }

    private SemIm getOptimizedSem() {
        //First, create a copy of semPm where the proper freeParameters
        //are fixed
        SemPm fixedPm;
        try {
            fixedPm = (SemPm) new MarshalledObject(semPm).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Parameter nextP : fixedPm.getParameters()) {
            if (nextP.getType() == ParamType.COEF) {
                Node nodeB = nextP.getNodeB();
                int bIndex = -1;
                boolean bIsIndicator = false;
                for (int i = 0; i < numIndicators; i++) {
                    if (indicators[i].getName().equals(nodeB.getName())) {
                        bIsIndicator = true;
                        bIndex = i;
                        break;
                    }
                }
                if (bIsIndicator && lambdaIndex[bIndex] < 0) {
                    nextP.setFixed(true);
                }
            }
        }

        SemIm semIm = new SemIm(fixedPm, covMatrix);
        List<Parameter> parameters = semIm.getFreeParameters();
        for (Parameter nextP : parameters) {
            if (nextP.getType() == ParamType.COEF) {
                Node nodeA = nextP.getNodeA();
                Node nodeB = nextP.getNodeB();
                int aIndex = -1, bIndex = -1;
                boolean bIsIndicator = false;
                for (int i = 0; i < numLatents; i++) {
                    if (latents[i].getName().equals(nodeA.getName())) {
                        aIndex = i;
                        break;
                    }
                }
                for (int i = 0; i < numIndicators; i++) {
                    if (indicators[i].getName().equals(nodeB.getName())) {
                        bIsIndicator = true;
                        bIndex = i;
                        break;
                    }
                }
                if (!bIsIndicator) {
                    for (int i = 0; i < numLatents; i++) {
                        if (latents[i].getName().equals(nodeB.getName())) {
                            bIndex = i;
                            break;
                        }
                    }
                }
                if (bIsIndicator) {
                    if (lambdaIndex[bIndex] < 0)
                    //It should never get to here, actually... (Ricardo, 07/07/2003)
                    {
                        semIm.setParamValue(nextP, 1.);
                    } else {
                        semIm.setParamValue(nextP, theta[lambdaIndex[bIndex]]);
                    }
                } else {
                    int[] parents = latentParents.get(bIndex);
                    for (int i = 0; i < parents.length; i++) {
                        if (latents[parents[i]].getName().equals(
                                latents[aIndex].getName())) {
                            semIm.setParamValue(nextP,
                                    theta[betaIndex[bIndex] + i]);
                            break;
                        }
                    }
                }
            } else if (nextP.getType() == ParamType.VAR) {
                Node nodeA = nextP.getNodeA();
                if (nodeA.getNodeType() == NodeType.LATENT) {
                    //exogenous latent
                    for (int i = 0; i < numLatents; i++) {
                        if (latents[i].getName().equals(nodeA.getName())) {
                            semIm.setParamValue(nextP,
                                    theta[latentErrorsIndex + i]);
                            break;
                        }
                    }
                } else {
                    //error term
                    boolean foundIndicator = false;

                    String childName = semIm.getSemPm().getGraph().getVarNode(nodeA).toString();


//                    Iterator<Node> itChild = semIm.getEstIm().getGraph()
//                            .getChildren(nodeA).iterator();
//                    String childName = (itChild.next()).getName();
                    for (int i = 0; i < numIndicators; i++) {
                        if (indicators[i].getName().equals(childName)) {
                            foundIndicator = true;
                            semIm.setParamValue(nextP,
                                    theta[indicatorErrorsIndex + i]);
                            break;
                        }
                    }
                    if (!foundIndicator) {
                        for (int i = 0; i < numLatents; i++) {
                            if (latents[i].getName().equals(childName)) {
                                semIm.setParamValue(nextP,
                                        theta[latentErrorsIndex + i]);
                                break;
                            }
                        }
                    }
                }
            }
//            else if (nextP.getType() == ParamType.COVAR) {
//                continue;
//            }
            else {
                throw new RuntimeException("Invalid parameter!");
            }
        }

        parameters = semIm.getFixedParameters();
        for (Parameter next : parameters) {
            semIm.setFixedParamValue(next, 1.);
        }

        return semIm;
    }

    /*private void testIt() {
        try {
            SemIm semIm = getOptimizedSem();
            double cov[][] = semIm.getImplCovarMeas();
            computeImpliedCovar();
            System.out.println();
            System.out.println();
            System.out.println("THETA: ");
            for (int i = 0; i < theta.length; i++)
                System.out.println("    " + theta[i]);
            System.out.println();
            System.exit(0);
        }
        catch (MathException e) {
        }
        ;
    }*/

    private double getFittingScore() {
        computeImpliedCovar();
        double[][] inverse = new double[0][];
        inverse = MatrixUtils.inverse(observedImpliedCovar);
        double[][] product = MatrixUtils.product(covMatrixValue, inverse);
        return Math.log(MatrixUtils.determinant(observedImpliedCovar)) +
                MatrixUtils.trace(product);
    }

    /**
     * This is the BIC score
     */

    /*private double getFittingScore2() {
        try {
            computeImpliedCovar();
            double[][] inverse = MatrixUtils.inverse(observedImpliedCovar);
            double[][] outerProduct = MatrixUtils.outerProduct(covMatrixValue, inverse);
            double sampleSize = covMatrix.getN();
            double fml = Math.log(MatrixUtils.determinant(observedImpliedCovar))
                    + MatrixUtils.trace(outerProduct)
                   - Math.log(MatrixUtils.determinant(covMatrixValue)) - numIndicators;
            return -0.5 * sampleSize * fml -
                    0.5 * theta.length * Math.log(sampleSize);
        }
        catch (MathException e) {
            throw new RuntimeException(e);
        }
    }*/

    /**
     * This is the chi-square score
     */

    /*private double getFittingScore3() {
        computeImpliedCovar();
        double[][] inverse = MatrixUtils.inverseGj(observedImpliedCovar, observedImpliedCovar.length);
        double[][] outerProduct = MatrixUtils.outerProduct(covMatrixValue, inverse);
        double sampleSize = covMatrix.getN();
        double fml = Math.log(MatrixUtils.determinant(observedImpliedCovar))
                + MatrixUtils.trace(outerProduct)
                - Math.log(MatrixUtils.determinant(covMatrixValue)) - numIndicators;
        return (sampleSize - 1) * fml;
    }*/
    public void debugFml() {
        computeImpliedCovar();
        double[][] inverse = MatrixUtils.inverse(observedImpliedCovar);
        double[][] product = MatrixUtils.product(covMatrixValue, inverse);
        System.out.println("logDetSigma = " +
                Math.log(MatrixUtils.determinant(observedImpliedCovar)));
        System.out.println("traceSSigmaInv = " + MatrixUtils.trace(product));
        System.out.println("logDetS = " +
                Math.log(MatrixUtils.determinant(covMatrixValue)));
        System.out.println("numMeasVars = " + numIndicators);
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * Runs the estimator on the data and SemPm passed in through the
     * constructor.
     */
    public void estimate() {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.

//        // Forget any previous estimation results. (It the estimation fails,
//        // the estimatedSem should be null.)
//        this.estimatedSem = null;
//
//        int iter = 0;
//        double bestScore = Double.MAX_VALUE, score;
//
//        initializeByTSLS();
//        double thetaTSLS[] = new double[theta.length];
//        System.arraycopy(theta, 0, thetaTSLS, 0, theta.length);
//        do {
//            if (iter < numIterTsls) {
//                if (iter > 0) {
//                    System.arraycopy(thetaTSLS, 0, theta, 0, theta.length);
//                    randomizeVariancesOnly();
//                }
//            }
//            else {
//                randomizeParameters();
//            }
//            double start[] = new double[theta.length];
//
//            System.arraycopy(theta, 0, start, 0, theta.length);
//
//            try {
//                new ConjugateGradientSearch().optimize(
//                        new MimBuildFittingFunction(this), start, FUNC_TOLERANCE,
//                        PARAM_TOLERANCE);
//            } catch (Exception e) {
//                continue;
//            }
//
//            System.arraycopy(start, 0, theta, 0, theta.length);
//
//            score = getFittingScore();
//
//            if (score < bestScore) {
//                estimatedSem = getOptimizedSem();
//                bestScore = score;
//            }
//
//            iter++;
//        } while (iter < numIter);
    }

    /**
     * @return the estimated SemIm. If the <code>estimate</code> method has not
     * yet been called, <code>null</code> is returned.
     */
    public SemIm getEstimatedSem() {
        return this.estimatedSem;
    }


    /**
     * @return a string representation of the Sem.
     */
    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder buf = new StringBuilder();
        buf.append("\nSemEstimator");

        if (this.estimatedSem == null) {
            buf.append("\n\t...SemIm has not been estimated yet.");
        } else {
            SemIm sem = this.estimatedSem;

            buf.append("\n\n\tfml = ");
            buf.append(nf.format(sem.getScore()));

            buf.append("\n\n\tnegtruncll = ");
            buf.append(nf.format(-sem.getTruncLL()));

            buf.append("\n\n\tmeasuredNodes:\n");
            buf.append("\t").append(sem.getMeasuredNodes());

            buf.append("\n\n\tedgeCoef:\n");
            buf.append(MatrixUtils.toString(sem.getEdgeCoef().toArray()));

            buf.append("\n\n\terrCovar:\n");
            buf.append(MatrixUtils.toString(sem.getErrCovar().toArray()));
        }

        return buf.toString();
    }

    // Need to remove dependence on PAL. this is the crux of it--this gradient
    // search needs to be rewritten using another library.
//    /**
//     * Wraps the MIM Build maximum likelihood fitting function for purposes of
//     * being evaluated using the PAL ConjugateGradient optimizer.
//     *
//     * @author Ricardo Silva
//     */
//
//    static class MimBuildFittingFunction implements MFWithGradient {
//        static final long serialVersionUID = 23L;
//
//        /**
//         * The wrapped model.
//         */
//        private final MimBuildEstimator mim;
//
//        /**
//         * Constructs a new CoefFittingFunction for the given Sem.
//         */
//        public MimBuildFittingFunction(MimBuildEstimator mim) {
//            this.mim = mim;
//        }
//
//        /**
//         * Computes the maximum likelihood function value for the given
//         * argument values as given by the optimizer. These values are mapped to
//         * parameter values.
//         */
//        public double evaluate(final double[] argument) {
//            System.arraycopy(argument, 0, mim.theta, 0, mim.theta.length);
//            return mim.getFittingScore();
//        }
//
//        public double evaluate(final double[] argument, double gradient[]) {
//            computeGradient(argument, gradient);
//            return mim.getFittingScore();
//        }
//
//        public void computeGradient(final double[] argument, double[] gradient) {
//            System.arraycopy(argument, 0, mim.theta, 0, mim.theta.length);
//            mim.computeGradient(gradient);
//        }
//
//        /**
//         * @return the number of arguments. Required by the MultivariateFunction
//         * interface.
//         */
//        public int getNumArguments() {
//            return mim.theta.length;
//        }
//
//        /**
//         * @return the lower bound of argument n. Required by the
//         * MultivariateFunction interface.
//         */
//        public double getLowerBound(final int n) {
//            if (n >= mim.indicatorErrorsIndex) {
//                return 0.0001;
//            }
//            return -1000.;
//        }
//
//        /**
//         * @return the upper bound of argument n. Required by the
//         * MultivariateFunction interface.
//         */
//        public double getUpperBound(final int n) {
//            return 1000.0;
//        }
//
//        public OrthogonalHints getOrthogonalHints() {
//            return null;
//        }
//    }

}




