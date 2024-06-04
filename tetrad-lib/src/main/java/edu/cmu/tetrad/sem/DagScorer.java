///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/**
 * Estimates a SemIm given a CovarianceMatrix and a SemPm. (A DataSet may be substituted for the CovarianceMatrix.) Uses
 * regression to do the estimation, so this is only for DAG models. But the DAG model may be reset on the fly and the
 * estimation redone. Variables whose parents have not changed will not be reestimated. Intended to speed up estimation
 * for algorithm that require repeated estimation of DAG models over the same variables. Assumes all variables are
 * measured.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class DagScorer implements TetradSerializable, Scorer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The covariance matrix.
     */
    private final ICovarianceMatrix covMatrix;
    /**
     * The edge coefficients.
     */
    private final Matrix edgeCoef;
    /**
     * The error covariance.
     */
    private final Matrix errorCovar;
    /**
     * The variables.
     */
    private final List<Node> variables;
    /**
     * The sample covariance.
     */
    private final Matrix sampleCovar;
    /**
     * The data set.
     */
    private DataSet dataSet;
    /**
     * The DAG.
     */
    private Graph dag;
    /**
     * The implied covariance matrix for the measured variables.
     */
    private Matrix implCovarMeasC;
    /**
     * The log determinant of the sample covariance matrix.
     */
    private double logDetSample;

    /**
     * The fml score.
     */
    private double fml = Double.NaN;


    /**
     * Constructs a new SemEstimator that uses the specified optimizer.
     *
     * @param dataSet a DataSet, all of whose variables are contained in the given SemPm. (They are identified by
     *                name.)
     */
    public DagScorer(DataSet dataSet) {
        this(new CovarianceMatrix(dataSet));
        this.dataSet = dataSet;
    }

    /**
     * Constructs a new SemEstimator that uses the specified optimizer.
     *
     * @param covMatrix a covariance matrix, all of whose variables are contained in the given SemPm. (They are
     *                  identified by name.)
     */
    public DagScorer(ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException(
                    "CovarianceMatrix must not be null.");
        }

        this.variables = covMatrix.getVariables();
        this.covMatrix = covMatrix;

        int m = this.getVariables().size();
        this.edgeCoef = new Matrix(m, m);
        this.errorCovar = new Matrix(m, m);
        this.sampleCovar = covMatrix.getMatrix();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.Scorer} object
     */
    public static Scorer serializableInstance() {
        return new DagScorer(CovarianceMatrix.serializableInstance());
    }

    /**
     * Scores the given DAG using the implemented algorithm.
     *
     * @param dag the DAG to be scored
     * @return the score of the DAG
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
            List<Node> parents = new ArrayList<>(dag.getParents(node));

            for (int i = 0; i < parents.size(); i++) {
                Node nextParent = parents.get(i);
                if (nextParent.getNodeType() == NodeType.ERROR) {
                    parents.remove(nextParent);
                    break;
                }
            }

            double variance = getSampleCovar().get(idx, idx);

            if (parents.size() > 0) {
                Vector nodeParentsCov = new Vector(parents.size());
                Matrix parentsCov = new Matrix(parents.size(), parents.size());

                for (int i = 0; i < parents.size(); i++) {
                    int idx2 = indexOf(parents.get(i));
                    nodeParentsCov.set(i, getSampleCovar().get(idx, idx2));

                    for (int j = i; j < parents.size(); j++) {
                        int idx3 = indexOf(parents.get(j));
                        parentsCov.set(i, j, getSampleCovar().get(idx2, idx3));
                        parentsCov.set(j, i, getSampleCovar().get(idx3, idx2));
                    }
                }

                Vector edges = parentsCov.inverse().times(nodeParentsCov);

                for (int i = 0; i < edges.size(); i++) {
                    int idx2 = indexOf(parents.get(i));
                    this.edgeCoef.set(idx2, indexOf(node), edges.get(i));
                }

                variance -= nodeParentsCov.dotProduct(edges);
            }

            this.errorCovar.set(i1, i1, variance);
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

        if (!new HashSet<>(this.getVariables()).equals(new HashSet<>(dag.getNodes()))) {
            System.out.println(new TreeSet<>(dag.getNodes()));
            System.out.println(new TreeSet<>(this.variables));
            throw new IllegalArgumentException("Dag must have the same nodes as the data.");
        }

        List<Node> changedNodes = new ArrayList<>();

        for (Node node : dag.getNodes()) {
            if (!new HashSet<>(this.dag.getParents(node)).equals(new HashSet<>(dag.getParents(node)))) {
                changedNodes.add(node);
            }
        }

        return changedNodes;
    }

    /**
     * <p>Getter for the field <code>covMatrix</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public ICovarianceMatrix getCovMatrix() {
        return this.covMatrix;
    }

    /**
     * <p>toString.</p>
     *
     * @return a string representation of the Sem.
     */
    public String toString() {

        return "\nSemEstimator";
    }

    /**
     * The value of the maximum likelihood function for the getModel the model (Bollen 107). To optimize, this should be
     * minimized.
     *
     * @return a double
     */
    public double getFml() {
        if (!Double.isNaN(this.fml)) {
            return this.fml;
        }

        Matrix implCovarMeas; // Do this once.

        try {
            implCovarMeas = implCovarMeas();
        } catch (Exception e) {
            e.printStackTrace();
            return Double.NaN;
        }

        Matrix sampleCovar = sampleCovar();

        double logDetSigma = logDet(implCovarMeas);
        double traceSSigmaInv = traceABInv(sampleCovar, implCovarMeas);
        double logDetSample = logDetSample();
        int pPlusQ = getMeasuredNodes().size();

        double fml = logDetSigma + traceSSigmaInv - logDetSample - pPlusQ;

        if (FastMath.abs(fml) < 0) {
            fml = 0.0;
        }

        this.fml = fml;
        return fml;
    }

    private Matrix sampleCovar() {
        return getSampleCovar();
    }

    private Matrix implCovarMeas() {
        computeImpliedCovar();
        return this.implCovarMeasC;
    }

    /**
     * <p>getBicScore.</p>
     *
     * @return BIC score, calculated as chisq - dof. This is equal to getFullBicScore() up to a constant.
     */
    public double getBicScore() {
        int dof = getDof();
        return getChiSquare() - dof * FastMath.log(getSampleSize());
    }

    /**
     * <p>getChiSquare.</p>
     *
     * @return the chi square value for the model.
     */
    public double getChiSquare() {
        return (getSampleSize() - 1) * getFml();
    }

    /**
     * <p>getPValue.</p>
     *
     * @return the p-value for the model.
     */
    public double getPValue() {
        return 1.0 - ProbUtils.chisqCdf(getChiSquare(), getDof());
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
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
        Matrix implCovarC = MatrixUtils.impliedCovar(edgeCoef().transpose(), errCovar());

        // Submatrix of implied covar for measured vars only.
        int size = getMeasuredNodes().size();
        this.implCovarMeasC = new Matrix(size, size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                this.implCovarMeasC.set(i, j, implCovarC.get(i, j));
            }
        }
    }

    private Matrix errCovar() {
        return getErrorCovar();
    }

    private Matrix edgeCoef() {
        return getEdgeCoef();
    }

    private double logDet(Matrix matrix2D) {
        return FastMath.log(matrix2D.det());
    }

    private double traceAInvB(Matrix A, Matrix B) {

        // Note that at this point the sem and the sample covar MUST have the
        // same variables in the same order.
        Matrix inverse = A.inverse();
        Matrix product = inverse.times(B);

        double trace = product.trace();

//        double trace = MatrixUtils.trace(product);

        if (trace < -1e-8) {
            throw new IllegalArgumentException("Trace was negative: " + trace);
        }

        return trace;
    }

    private double traceABInv(Matrix A, Matrix B) {

        // Note that at this point the sem and the sample covar MUST have the
        // same variables in the same order.
        try {

            Matrix product = A.times(B.inverse());

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
        if (this.logDetSample == 0.0 && sampleCovar() != null) {
            double det = sampleCovar().det();
            this.logDetSample = FastMath.log(det);
        }

        return this.logDetSample;
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * <p>getNumFreeParams.</p>
     *
     * @return a int
     */
    public int getNumFreeParams() {
        return this.dag.getEdges().size() + this.dag.getNodes().size();
    }

    /**
     * <p>getDof.</p>
     *
     * @return a int
     */
    public int getDof() {
        return (this.dag.getNodes().size() * (this.dag.getNodes().size() + 1)) / 2 - getNumFreeParams();
    }

    /**
     * <p>getSampleSize.</p>
     *
     * @return a int
     */
    public int getSampleSize() {
        return this.covMatrix.getSampleSize();
    }


    /**
     * <p>getMeasuredNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getMeasuredNodes() {
        return this.getVariables();
    }

    /**
     * <p>Getter for the field <code>sampleCovar</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix getSampleCovar() {
        return this.sampleCovar;
    }

    /**
     * <p>Getter for the field <code>edgeCoef</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix getEdgeCoef() {
        return this.edgeCoef;
    }

    /**
     * <p>Getter for the field <code>errorCovar</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix getErrorCovar() {
        return this.errorCovar;
    }

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * <p>getEstSem.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm getEstSem() {
        SemPm pm = new SemPm(this.dag);

        if (this.dataSet != null) {
            return new SemEstimator(this.dataSet, pm, new SemOptimizerRegression()).estimate();
        } else if (this.covMatrix != null) {
            return new SemEstimator(this.covMatrix, pm, new SemOptimizerRegression()).estimate();
        } else {
            throw new IllegalStateException();
        }
    }


}


