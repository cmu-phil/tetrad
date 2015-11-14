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
import edu.cmu.tetrad.util.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/**
 * Estimates a SemIm given a CovarianceMatrix and a SemPm. (A DataSet may be
 * substituted for the CovarianceMatrix.) Uses regression to do the estimation,
 * so this is only for DAG models. But the DAG model may be reset on the fly
 * and the estimation redone. Variables whose parents have not changed will
 * not be reestimated. Intended to speed up estimation for algorithms that
 * require repeated estimation of DAG models over the same variables.
 * Assumes all variables are measured.
 *
 * @author Joseph Ramsey
 */
public final class DagScorer implements TetradSerializable, Scorer {
    static final long serialVersionUID = 23L;

    private ICovarianceMatrix covMatrix;
    private DataSet dataSet = null;
    private TetradMatrix edgeCoef;
    private TetradMatrix errorCovar;
    private Graph dag = null;
    private List<Node> variables;
    private TetradMatrix implCovarC;
    private TetradMatrix implCovarMeasC;
    private TetradMatrix sampleCovar;
    private double logDetSample;
    private double fml = Double.NaN;


    //=============================CONSTRUCTORS============================//

    /**
     * Constructs a new SemEstimator that uses the specified optimizer.
     *
     * @param dataSet      a DataSet, all of whose variables are contained in
     *                     the given SemPm. (They are identified by name.)
     */
    public DagScorer(DataSet dataSet) {
        this(new CovarianceMatrix(dataSet));
        this.dataSet = dataSet;
    }

    /**
     * Constructs a new SemEstimator that uses the specified optimizer.
     *
     * @param covMatrix    a covariance matrix, all of whose variables are
     *                     contained in the given SemPm. (They are identified by
     *                     name.)
     */
    public DagScorer(ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException(
                    "CovarianceMatrix must not be null.");
        }

        this.variables = covMatrix.getVariables();
        this.covMatrix = covMatrix;

        int m = this.getVariables().size();
        this.edgeCoef = new TetradMatrix(m, m);
        this.errorCovar = new TetradMatrix(m, m);
        this.sampleCovar = covMatrix.getMatrix();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Scorer serializableInstance() {
        return new DagScorer(CovarianceMatrix.serializableInstance());
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * Runs the estimator on the data and SemPm passed in through the
     * constructor. Returns the fml score of the resulting model.
     */
    public double score(Graph dag) {
        List<Node> changedNodes = getChangedNodes(dag);

        for (Node node : changedNodes) {
            int i1 = indexOf(node);
            getErrorCovar().set(i1, i1, 0);
            for (int _j = 0; _j < getVariables().size(); _j++) {
                getEdgeCoef().set(_j, i1, 0);
            }

            if (node.getNodeType() != NodeType.MEASURED) {
                continue;
            }

            int idx = indexOf(node);
            List<Node> parents = dag.getParents(node);

            for (int i = 0; i < parents.size(); i++) {
                Node nextParent = parents.get(i);
                if (nextParent.getNodeType() == NodeType.ERROR) {
                    parents.remove(nextParent);
                    break;
                }
            }

            double variance = getSampleCovar().get(idx, idx);

            if (parents.size() > 0) {
                TetradVector nodeParentsCov = new TetradVector(parents.size());
                TetradMatrix parentsCov = new TetradMatrix(parents.size(), parents.size());

                for (int i = 0; i < parents.size(); i++) {
                    int idx2 = indexOf(parents.get(i));
                    nodeParentsCov.set(i, getSampleCovar().get(idx, idx2));

                    for (int j = i; j < parents.size(); j++) {
                        int idx3 = indexOf(parents.get(j));
                        parentsCov.set(i, j, getSampleCovar().get(idx2, idx3));
                        parentsCov.set(j, i, getSampleCovar().get(idx3, idx2));
                    }
                }

                TetradVector edges = parentsCov.inverse().times(nodeParentsCov);

                for (int i = 0; i < edges.size(); i++) {
                    int idx2 = indexOf(parents.get(i));
                    edgeCoef.set(idx2, indexOf(node), edges.get(i));
                }

                variance -= nodeParentsCov.dotProduct(edges);
            }

            errorCovar.set(i1, i1, variance);
        }


        this.dag = dag;
        this.fml = Double.NaN;

        return getFml();
    }

    private int indexOf(Node node) {
        for (int i = 0; i < getVariables().size(); i++) {
            if (node.getName().equals(this.getVariables().get(i).getName())) {
                return i;
            }
        }

        throw new IllegalArgumentException("Dag must have the same nodes as the data.");
    }

    private List<Node> getChangedNodes(Graph dag) {
        if (this.dag == null) {
            return dag.getNodes();
        }

        if (!new HashSet<Node>(this.getVariables()).equals(new HashSet<Node>(dag.getNodes()))) {
            System.out.println(new TreeSet<Node>(dag.getNodes()));
            System.out.println(new TreeSet<Node>(variables));
            throw new IllegalArgumentException("Dag must have the same nodes as the data.");
        }

        List<Node> changedNodes = new ArrayList<Node>();

        for (Node node : dag.getNodes()) {
            if (!new HashSet<Node>(this.dag.getParents(node)).equals(new HashSet<Node>(dag.getParents(node)))) {
                changedNodes.add(node);
            }
        }

        return changedNodes;
    }

    public ICovarianceMatrix getCovMatrix() {
        return covMatrix;
    }

    /**
     * @return a string representation of the Sem.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("\nSemEstimator");

        return buf.toString();
    }

    //============================PRIVATE METHODS==========================//

    /**
     * The value of the maximum likelihood function for the getModel the model
     * (Bollen 107). To optimize, this should be minimized.
     */
    public double getFml() {
        if (!Double.isNaN(this.fml)) {
            return this.fml;
        }

        TetradMatrix implCovarMeas; // Do this once.

        try {
            implCovarMeas = implCovarMeas();
        } catch (Exception e) {
            e.printStackTrace();
            return Double.NaN;
        }

        TetradMatrix sampleCovar = sampleCovar();

        double logDetSigma = logDet(implCovarMeas);
        double traceSSigmaInv = traceABInv(sampleCovar, implCovarMeas);
        double logDetSample = logDetSample();
        int pPlusQ = getMeasuredNodes().size();

        double fml = logDetSigma + traceSSigmaInv - logDetSample - pPlusQ;

        if (Math.abs(fml) < 0) {
            fml = 0.0;
        }

        this.fml = fml;
        return fml;
    }

    public double getLogLikelihood() {
        TetradMatrix SigmaTheta; // Do this once.

        try {
            SigmaTheta = implCovarMeas();
        } catch (Exception e) {
            return Double.NaN;
        }

        TetradMatrix sStar = sampleCovar();

        double logDetSigmaTheta = logDet(SigmaTheta);
        double traceSStarSigmaInv = traceABInv(sStar, SigmaTheta);
        int pPlusQ = getMeasuredNodes().size();

        return -(getSampleSize() / 2.) * pPlusQ * Math.log(2 * Math.PI)
                - (getSampleSize() / 2.) * logDetSigmaTheta
                - (getSampleSize() / 2.) * traceSStarSigmaInv;
    }

    public double getFml2() {
        TetradMatrix sigma; // Do this once.

        try {
            sigma = implCovarMeas();
        } catch (Exception e) {
//            e.printStackTrace();
            return Double.NaN;
        }

        TetradMatrix s = sampleCovar();

        TetradMatrix sInv = s.inverse();

        TetradMatrix prod = sigma.times(sInv);
        TetradMatrix identity = TetradAlgebra.identity(s.rows());
        prod = prod.minus(identity);
        double trace = prod.times(prod).trace();

        return 0.5 * trace;
    }

    /**
     * The negative  of the log likelihood function for the getModel model, with
     * the constant chopped off. (Bollen 134). This is an alternative, more
     * efficient, optimization function to Fml which produces the same result
     * when minimized.
     */
    public double getTruncLL() {
        // Formula Bollen p. 263.
        TetradMatrix Sigma = implCovarMeas();

        // Using (n - 1) / n * s as in Bollen p. 134 causes sinkholes to open
        // up immediately. Not sure why.
        TetradMatrix S = sampleCovar();
        int n = getSampleSize();
        return -(n - 1) / 2. * (logDet(Sigma) + traceAInvB(Sigma, S));
    }

    private TetradMatrix sampleCovar() {
        return getSampleCovar();
    }

    private TetradMatrix implCovarMeas () {
        computeImpliedCovar();
        return this.implCovarMeasC;
    }

    /**
     * @return BIC score, calculated as chisq - dof. This is equal to getFullBicScore() up to a constant.
     */
    public double getBicScore() {
        int dof = getDof();
        return getChiSquare() - dof * Math.log(getSampleSize());
    }

    public double getAicScore() {
        int dof = getDof();
        return getChiSquare() - 2 * dof;
    }

    public double getKicScore() {
        double fml = getFml();
        int edgeCount = dag.getNumEdges();
        int sampleSize = getSampleSize();

        return -fml + (edgeCount * Math.log(sampleSize));
    }

    /**
     * @return the chi square value for the model.
     */
    public double getChiSquare() {
        return (getSampleSize() - 1) * getFml();
    }

    /**
     * @return the p-value for the model.
     */
    public double getPValue() {
        return 1.0 - ProbUtils.chisqCdf(getChiSquare(), getDof());
    }

    /**
     * Adds semantic checks to the default deserialization method. This
     * method must have the standard signature for a readObject method, and
     * the body of the method must begin with "s.defaultReadObject();".
     * Other than that, any semantic checks can be specified and do not need
     * to stay the same from version to version. A readObject method of this
     * form may be added to any class, even if Tetrad sessions were
     * previously saved out using a version of the class that didn't include
     * it. (That's what the "s.defaultReadObject();" is for. See J. Bloch,
     * Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject
    (ObjectInputStream
             s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (getCovMatrix() == null) {
            throw new NullPointerException();
        }

    }

    /**
     * Computes the implied covariance matrices of the Sem. There are two:
     * <code>implCovar </code> contains the covariances of all the variables and
     * <code>implCovarMeas</code> contains covariance for the measured variables
     * only.
     */
    private void computeImpliedCovar() {

        // Note. Since the sizes of the temp matrices in this calculation
        // never change, we ought to be able to reuse them.
        this.implCovarC = MatrixUtils.impliedCovar(edgeCoef().transpose(), errCovar());

        // Submatrix of implied covar for measured vars only.
        int size = getMeasuredNodes().size();
        this.implCovarMeasC = new TetradMatrix(size, size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                this.implCovarMeasC.set(i, j, this.implCovarC.get(i, j));
            }
        }
    }

    private TetradMatrix errCovar() {
        return getErrorCovar();
    }

    private TetradMatrix edgeCoef() {
        return getEdgeCoef();
    }

    private double logDet(TetradMatrix matrix2D) {
        return Math.log(matrix2D.det());
    }

    private double traceAInvB(TetradMatrix A, TetradMatrix B) {

        // Note that at this point the sem and the sample covar MUST have the
        // same variables in the same order.
        TetradMatrix inverse = A.inverse();
        TetradMatrix product = inverse.times(B);

        double trace = product.trace();

//        double trace = MatrixUtils.trace(product);

        if (trace < -1e-8) {
            throw new IllegalArgumentException("Trace was negative: " + trace);
        }

        return trace;
    }

    private double traceABInv(TetradMatrix A, TetradMatrix B) {

        // Note that at this point the sem and the sample covar MUST have the
        // same variables in the same order.
        try {

            TetradMatrix product = A.times(B.inverse());

            double trace = product.trace();

            if (trace < -1e-8) {
                throw new IllegalArgumentException("Trace was negative: " + trace);
            }

            return trace;
        } catch (Exception e) {
            System.out.println(B);
            throw new RuntimeException(e);
        }
    }

    private double logDetSample() {
        if (logDetSample == 0.0 && sampleCovar() != null) {
            double det = sampleCovar().det();
            logDetSample = Math.log(det);
        }

        return logDetSample;
    }

//    private double traceSSigmaInv2(TetradMatrix s,
//                                  TetradMatrix sigma) {
//
//        // Note that at this point the sem and the sample covar MUST have the
//        // same variables in the same order.
//        TetradMatrix inverse = TetradAlgebra.inverse(sigma);
//
//        for (int i = 0; i < sigma.rows(); i++) {
//            for (int j = 0; j < sigma.columns(); j++) {
//                if (sigma.get(i, j) < 1e-10) {
//                    sigma.set(i, j, 0);
//                }
//            }
//        }
//
//        System.out.println("Sigma = " + sigma);
//
//        for (int i = 0; i < inverse.rows(); i++) {
//            for (int j = 0; j < inverse.columns(); j++) {
//                if (inverse.get(i, j) < 1e-10) {
//                    inverse.set(i, j, 0);
//                }
//            }
//        }
//
//        System.out.println("Inverse of signa = " + inverse);
//
//        for (int i = 0; i < getFreeParameters().size(); i++) {
//            System.out.println(i + ". " + getFreeParameters().get(i));
//        }
//
//        TetradMatrix product = TetradAlgebra.times(s, inverse);
//
//        double v = MatrixUtils.trace(product);
//
//        if (v < -1e-8) {
//            throw new IllegalArgumentException("Trace was negative.");
//        }
//
//        return v;
//    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public int getNumFreeParams() {
        return dag.getEdges().size() + dag.getNodes().size();
    }

    public int getDof() {
        return (dag.getNodes().size() * (dag.getNodes().size() + 1)) / 2 - getNumFreeParams();
    }

    public int getSampleSize() {
        return covMatrix.getSampleSize();
    }


    public List<Node> getMeasuredNodes() {
        return this.getVariables();
    }

    public TetradMatrix getSampleCovar() {
        return sampleCovar;
    }

    public TetradMatrix getEdgeCoef() {
        return edgeCoef;
    }

    public TetradMatrix getErrorCovar() {
        return errorCovar;
    }

    public List<Node> getVariables() {
        return variables;
    }

    public SemIm getEstSem() {
        SemPm pm = new SemPm(dag);

        if (dataSet != null) {
            return new SemEstimator(dataSet, pm, new SemOptimizerRegression()).estimate();
        }
        else if (covMatrix != null) {
            return new SemEstimator(covMatrix, pm, new SemOptimizerRegression()).estimate();
        }
        else {
            throw new IllegalStateException();
        }
    }
}


