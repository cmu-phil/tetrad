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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionCovariance;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Split;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.rmi.MarshalledObject;
import java.util.*;

import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Stores an instantiated structural equation model (SEM), with error covariance terms, possibly cyclic, suitable for
 * estimation and simulation. For estimation, the maximum likelihood fitting function and the negative log likelihood
 * function (Bollen 1989, p. 109) are calculated; these can be maximized by an estimator to estimate optimal parameter
 * values. The values of freeParameters are set as indicated in their corresponding Parameter objects as initial values
 * for estimation. Provides multiple ways to get and set the values of free freeParameters. For simulation, cyclic and
 * acyclic methods are provided; the cyclic method is used by default, although the acyclic method is considerably
 * faster for large data sets.
 * <p>
 * Let V be the set of variables in the model. The freeParameters of the model are as follows: (a) the list of linear
 * coefficients for all edges u--&gt;v in the model, where u, v are in V, (b) the list of variances for all variables in
 * V, (c) the list of all error covariances d&lt;-&gt;e, where d an e are exogenous terms in the model (either exogenous
 * variables or error terms for endogenous variables), and (d) the list of means for all variables in V.
 * <p>
 * It is important to note that the likelihood functions this class calculates do not depend on variable means. They
 * depend only on edge coefficients and error covariances. Hence, variable means are treated differently from edge
 * coefficients and error covariances in the model.
 * <p>
 * Reference: Bollen, K. A. (1989). Structural Equations with Latent Variables. New York: John Wiley and Sons.
 *
 * @author Frank Wimberly
 * @author Ricardo Silva
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SemIm implements Im, ISemIm {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The Sem PM containing the graph and the freeParameters to be estimated. For now a defensive copy of this is not
     * being constructed, since it is not used anywhere in the code except in the the constructor and in its accessor
     * method. If somebody changes it, it's their own fault, but it won't affect this class.
     */
    private final SemPm semPm;
    /**
     * The list of measured and latent variableNodes for the semPm. (Unmodifiable.)
     */
    private final List<Node> variableNodes;
    /**
     * The list of measured variableNodes from the semPm. (Unmodifiable.)
     */
    private final List<Node> measuredNodes;
    /**
     * Matrix of edge coefficients. edgeCoefC[i][j] is the coefficient of the edge from getVariableNodes().get(i) to
     * getVariableNodes().get(j), or 0.0 if this edge is not in the graph. The values of these may be changed, but the
     * array itself may not.
     */
    private final Matrix edgeCoef;
    /**
     * Standard Deviations of means. Needed to calculate standard errors.
     */
    private final double[] variableMeansStdDev;
    /**
     * The list of free freeParameters (Unmodifiable). This must be in the same order as this.freeMappings.
     */
    private List<Parameter> freeParameters;
    /**
     * The list of fixed freeParameters (Unmodifiable). This must be in the same order as this.fixedMappings.
     */
    private List<Parameter> fixedParameters;
    /**
     * The list of mean freeParameters (Unmodifiable). This must be in the same order as variableMeans.
     */
    private List<Parameter> meanParameters;
    /**
     * Matrix of error covariances. errCovar[i][j] is the covariance of the error term of getExoNodes().get(i) and
     * getExoNodes().get(j), with the special case (duh!) that errCovar[i][i] is the variance of getExoNodes.get(i). The
     * values of these may be changed, but the array itself may not.
     */
    private Matrix errCovar;
    /**
     * Means of variables. These will not be counted for purposes of calculating degrees of freedom, since the increase
     * in dof is exactly balanced by a decrease in dof.
     */
    private double[] variableMeans;
    /**
     * Replaced by sampleCovar. Please do not delete. Required for serialization backward compatibility.
     */
    private Matrix sampleCovarC;
    /**
     * The sample size.
     */
    private int sampleSize;
    /**
     * Replaced by implCovarC. Please do not delete. Required for serialization backward compatibility.
     */
    private Matrix implCovar;
    /**
     * The list of freeMappings. This is an unmodifiable list. It is fixed (up to order) by the SemPm. This must be in
     * the same order as this.freeParameters.
     */
    private List<Mapping> freeMappings;
    /**
     * The list of fixed freeParameters (Unmodifiable). This must be in the same order as this.fixedParameters.
     */
    private List<Mapping> fixedMappings;
    /**
     * Stores the standard errors for the freeParameters. May be null.
     */
    private double[] standardErrors;
    /**
     * True iff setting freeParameters to out-of-bound values throws exceptions.
     */
    private boolean parameterBoundsEnforced = true;

    /**
     * True iff this SemIm is estimated.
     */
    private boolean estimated;
    /**
     * True just in case the graph for the SEM is cyclic.
     */
    private boolean cyclic;

    /**
     * True just in case cyclicity has already been checked.
     */
    private boolean cyclicChecked;

    /**
     * Parameters to help guide how values are chosen for freeParameters.
     */
    private Parameters params = new Parameters();
    /**
     * Stores a distribution for each variable. Initialized to N(0, 1) for each.
     */
    private Map<Node, Distribution> distributions;
    /**
     * Stores the connection functions of specified nodes.
     */
    private Map<Node, ConnectionFunction> functions;

    /**
     * The type of score to use for estimation.
     */
    private Map<Node, Integer> variablesHash;

    /**
     * The type of score to use for estimation.
     */
    private Matrix sampleCovInv;
    /**
     * Types of scores that yield a chi square value when minimized.
     */
    private ScoreType scoreType = ScoreType.Fml;

    /**
     * Error distribution parameters.
     */
    private double errorParam1;

    /**
     * Error distribution parameters.
     */
    private double errorParam2 = 1.0;

    /**
     * Number of random calls.
     */
    private int numRandomCalls = 0;

    /**
     * Constructs a new SEM IM from a SEM PM.
     *
     * @param semPm a {@link edu.cmu.tetrad.sem.SemPm} object
     */
    public SemIm(SemPm semPm) {
        this(semPm, null, new Parameters());
    }

    /**
     * Constructs a new SEM IM from the given SEM PM, using the given params object to guide the choice of parameter
     * values.
     *
     * @param semPm  a {@link edu.cmu.tetrad.sem.SemPm} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemIm(SemPm semPm, Parameters params) {
        this(semPm, null, params);
    }

    /**
     * Constructs a new SEM IM from the given SEM PM, using the old SEM IM and params object to guide the choice of
     * parameter values. If old values are retained, they are gotten from the old SEM IM.
     *
     * @param semPm      a {@link edu.cmu.tetrad.sem.SemPm} object
     * @param oldSemIm   a {@link edu.cmu.tetrad.sem.SemIm} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemIm(SemPm semPm, SemIm oldSemIm, Parameters parameters) {
        if (semPm == null) {
            throw new NullPointerException("Sem PM must not be null.");
        }

        this.params = parameters;

        this.sampleSize = parameters.getInt(Params.SAMPLE_SIZE);

        this.semPm = new SemPm(semPm);

        this.variableNodes
                = Collections.unmodifiableList(semPm.getVariableNodes());
        this.measuredNodes
                = Collections.unmodifiableList(semPm.getMeasuredNodes());

        int numVars = this.variableNodes.size();

        this.edgeCoef = new Matrix(numVars, numVars);
        this.errCovar = new Matrix(numVars, numVars);
        this.variableMeans = new double[numVars];
        this.variableMeansStdDev = new double[numVars];

        this.freeParameters = initFreeParameters();
        this.freeMappings = createMappings(getFreeParameters());
        this.fixedParameters = initFixedParameters();
        this.fixedMappings = createMappings(getFixedParameters());
        this.meanParameters = initMeanParameters();

        // Set variable means to 0.0 pending the program creating the SemIm
        // setting them. I.e. by default they are set to 0.0.
        for (int i = 0; i < numVars; i++) {
            this.variableMeans[i] = 0;
            this.variableMeansStdDev[i] = Double.NaN;
        }

        initializeValues();

        // Note that we want to use the default params object here unless a bona fide
        // subistute has been provided.
        if (oldSemIm != null && this.getParams().getBoolean("retainPreviousValues", false)) {
            retainPreviousValues(oldSemIm);
        }

        this.distributions = new HashMap<>();

        // Careful with this! These must not be left in! They override the
        // normal behavior!
//        for (Node node : variableNodes) {
//            this.distributions.put(node, new Uniform(-1, 1));
//        }


        // 1 = Normal, 2 = Uniform, 3 = Exponential, 4 = Gumbel
        this.errorParam1 = parameters.getDouble(Params.SIMULATION_PARAM1);
        this.errorParam2 = parameters.getDouble(Params.SIMULATION_PARAM2);

        this.functions = new HashMap<>();
    }

    /**
     * Constructs a SEM model using the given SEM PM and sample covariance matrix.
     *
     * @param semPm     a {@link edu.cmu.tetrad.sem.SemPm} object
     * @param covMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public SemIm(SemPm semPm, ICovarianceMatrix covMatrix) {
        this(semPm);
        setCovMatrix(covMatrix);
    }

    /**
     * Special hidden constructor to generate updated models.
     */
    private SemIm(SemIm semIm, Matrix covariances,
                  Vector means) {
        this(semIm);

        if (covariances.getNumRows() != covariances.getNumColumns()) {
            throw new IllegalArgumentException(
                    "Expecting covariances to be square.");
        }

        if (!MatrixUtils.isPositiveDefinite(covariances)) {
            throw new IllegalArgumentException("Covariances must be symmetric "
                                               + "positive definite.");
        }

        if (means.size() != this.semPm.getVariableNodes().size()) {
            throw new IllegalArgumentException(
                    "Number of means does not equal " + "number of variables.");
        }

        if (covariances.getNumRows() != this.semPm.getVariableNodes().size()) {
            throw new IllegalArgumentException(
                    "Dimension of covariance matrix "
                    + "does not equal number of variables.");
        }

        this.errCovar = covariances;
        this.variableMeans = means.toArray();

        this.freeParameters = initFreeParameters();
        this.freeMappings = createMappings(getFreeParameters());
        this.fixedParameters = initFixedParameters();
        this.fixedMappings = createMappings(getFixedParameters());
    }

    /**
     * Copy constructor.
     *
     * @param semIm a {@link edu.cmu.tetrad.sem.SemIm} object
     * @throws java.lang.RuntimeException if the given SemIm cannot be serialized and deserialized correctly.
     */
    public SemIm(SemIm semIm) {
        try {

            // We make a deep copy of semIm and then copy all of its fields
            // into this SEM IM. Otherwise, it's just too HARD to make a deep copy!
            // (Complain, complain.) jdramsey 4/20/2005
            SemIm _semIm = new MarshalledObject<>(semIm).get();

            this.semPm = _semIm.semPm;
            this.variableNodes = _semIm.variableNodes;
            this.measuredNodes = _semIm.measuredNodes;
            this.freeParameters = _semIm.freeParameters;
            this.fixedParameters = _semIm.fixedParameters;
            this.meanParameters = _semIm.meanParameters;
            this.edgeCoef = _semIm.edgeCoef;
            this.errCovar = _semIm.errCovar;
            this.variableMeans = _semIm.variableMeans;
            this.variableMeansStdDev = _semIm.variableMeansStdDev;
            this.sampleCovarC = _semIm.sampleCovarC;
            this.sampleSize = _semIm.sampleSize;
            this.implCovar = _semIm.implCovar;
            this.freeMappings = _semIm.freeMappings;
            this.fixedMappings = _semIm.fixedMappings;
            this.standardErrors = _semIm.standardErrors;
            this.parameterBoundsEnforced = _semIm.parameterBoundsEnforced;
            this.estimated = _semIm.estimated;
            this.cyclic = _semIm.cyclic;
            this.distributions = new HashMap<>(_semIm.distributions);
            this.scoreType = _semIm.scoreType;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("SemIm could not be deep cloned.", e);
        }
    }

    /**
     * <p>Constructor for SemIm.</p>
     *
     * @param semPm               a {@link edu.cmu.tetrad.sem.SemPm} object
     * @param variableNodes       a {@link java.util.List} object
     * @param measuredNodes       a {@link java.util.List} object
     * @param edgeCoef            a {@link edu.cmu.tetrad.util.Matrix} object
     * @param variableMeansStdDev an array of {@link double} objects
     */
    public SemIm(SemPm semPm, List<Node> variableNodes, List<Node> measuredNodes, Matrix edgeCoef, double[] variableMeansStdDev) {
        this.semPm = semPm;
        this.variableNodes = new ArrayList<>(variableNodes);
        this.measuredNodes = new ArrayList<>(measuredNodes);
        this.edgeCoef = new Matrix(edgeCoef);
        this.variableMeansStdDev = Arrays.copyOf(variableMeansStdDev, variableMeansStdDev.length);
    }

    /**
     * <p>getParameterNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public static List<String> getParameterNames() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.COEF_LOW);
        parameters.add(Params.COEF_HIGH);
        parameters.add(Params.COV_LOW);
        parameters.add(Params.COV_HIGH);
        parameters.add(Params.VAR_LOW);
        parameters.add(Params.VAR_HIGH);
        parameters.add(Params.COEF_SYMMETRIC);
        parameters.add(Params.COV_SYMMETRIC);
        return parameters;
    }

    /**
     * Constructs a new SEM IM with the given graph, retaining parameter values from <code>semIm</code> for nodes of the
     * same name and edges connecting nodes of the same names.
     *
     * @param semIm The old SEM IM.
     * @param graph The graph for the new SEM IM.
     * @return The new SEM IM, retaining values from <code>semIm</code>.
     */
    public static SemIm retainValues(SemIm semIm, SemGraph graph) {
        SemPm newSemPm = new SemPm(graph);
        SemIm newSemIm = new SemIm(newSemPm);

        for (Parameter p1 : newSemIm.getSemPm().getParameters()) {
            Node nodeA = semIm.getSemPm().getGraph().getNode(p1.getNodeA().getName());
            Node nodeB = semIm.getSemPm().getGraph().getNode(p1.getNodeB().getName());

            for (Parameter p2 : semIm.getSemPm().getParameters()) {
                if (p2.getNodeA() == nodeA && p2.getNodeB() == nodeB && p2.getType() == p1.getType()) {
                    newSemIm.setParamValue(p1, semIm.getParamValue(p2));
                }
            }
        }

        newSemIm.sampleSize = semIm.sampleSize;
        return newSemIm;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public static SemIm serializableInstance() {
        return new SemIm(SemPm.serializableInstance());
    }

    /**
     * <p>updatedIm.</p>
     *
     * @param covariances a {@link edu.cmu.tetrad.util.Matrix} object
     * @param means       a {@link edu.cmu.tetrad.util.Vector} object
     * @return a variant of the getModel model with the given covariance matrix and means. Used for updating.
     */
    public SemIm updatedIm(Matrix covariances, Vector means) {
        return new SemIm(this, covariances, means);
    }

    /**
     * Sets the sample covariance matrix for this Sem as a submatrix of the given matrix. The variable names used in the
     * SemPm for this model must all appear in this CovarianceMatrix.
     *
     * @param covMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public void setCovMatrix(ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException(
                    "Covariance matrix must not be null.");
        }

        ICovarianceMatrix covMatrix2 = fixVarOrder(covMatrix);
        this.sampleCovarC = covMatrix2.getMatrix().copy();
        covMatrix2.getVariables();
        this.sampleSize = covMatrix2.getSampleSize();
        this.sampleCovInv = null;

        if (this.sampleSize < 0) {
            throw new IllegalArgumentException(
                    "Sample size out of range: " + this.sampleSize);
        }
    }

    /**
     * Calculates the covariance matrix of the given DataSet and sets the sample covariance matrix for this model to a
     * subset of it. The measured variable names used in the SemPm for this model must all appear in this data set.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public void setDataSet(DataSet dataSet) {
        setCovMatrix(new CovarianceMatrix(dataSet));
    }

    /**
     * <p>Getter for the field <code>semPm</code>.</p>
     *
     * @return the Digraph which describes the causal structure of the Sem.
     */
    public SemPm getSemPm() {
        return this.semPm;
    }

    /**
     * <p>getFreeParamValues.</p>
     *
     * @return an array containing the getModel values for the free freeParameters, in the order in which the
     * freeParameters appear in getFreeParameters(). That is, getFreeParamValues()[i] is the value for
     * getFreeParameters()[i].
     */
    public double[] getFreeParamValues() {
        double[] paramValues = new double[freeMappings().size()];

        for (int i = 0; i < freeMappings().size(); i++) {
            Mapping mapping = freeMappings().get(i);
            paramValues[i] = mapping.getValue();
        }

        return paramValues;
    }

    /**
     * Sets the values of the free freeParameters (in the order in which they appear in getFreeParameters()) to the
     * values contained in the given array. That is, params[i] is the value for getFreeParameters()[i].
     *
     * @param params an array of {@link double} objects
     */
    public void setFreeParamValues(double[] params) {
        if (params.length != getNumFreeParams()) {
            throw new IllegalArgumentException("The array provided must be "
                                               + "of the same length as the number of free parameters.");
        }

        for (int i = 0; i < freeMappings().size(); i++) {
            Mapping mapping = freeMappings().get(i);
            mapping.setValue(params[i]);
        }
    }

    /**
     * Retrieves the value associated with the given parameter.
     *
     * @param parameter The parameter for which to retrieve the value.
     * @return The value associated with the parameter.
     * @throws NullPointerException     if the parameter is null.
     * @throws IllegalArgumentException if the parameter is not present in the model.
     */
    public double getParamValue(Parameter parameter) {
        if (parameter == null) {
            throw new NullPointerException();
        }

        if (getFreeParameters().contains(parameter)) {
            int index = getFreeParameters().indexOf(parameter);
            Mapping mapping = this.freeMappings.get(index);
            return mapping.getValue();
        } else if (getFixedParameters().contains(parameter)) {
            int index = getFixedParameters().indexOf(parameter);
            Mapping mapping = this.fixedMappings.get(index);
            return mapping.getValue();
        } else if (getMeanParameters().contains(parameter)) {
            int index = getMeanParameters().indexOf(parameter);
            return this.variableMeans[index];
        }

//        return Double.NaN;
        throw new IllegalArgumentException(
                "Not a parameter in this model: " + parameter);
    }

    /**
     * Sets the value of a parameter in the model.
     *
     * @param parameter the parameter to set the value for
     * @param value     the value to set for the parameter
     * @throws IllegalArgumentException if the parameter cannot be set in this model
     */
    public void setParamValue(Parameter parameter, double value) {
        if (getFreeParameters().contains(parameter)) {
            // Note this assumes the freeMappings are in the same order as the
            // free freeParameters.                                        get
            int index = getFreeParameters().indexOf(parameter);
            Mapping mapping = this.freeMappings.get(index);
            mapping.setValue(value);
        } else if (getMeanParameters().contains(parameter)) {
            int index = getMeanParameters().indexOf(parameter);
            this.variableMeans[index] = value;
        } else {
            throw new IllegalArgumentException("That parameter cannot be set in "
                                               + "this model: " + parameter);
        }
    }

    /**
     * Sets the fixed value for a specified parameter in the model.
     *
     * @param parameter the parameter whose value is to be set. Must be a {@link Parameter} object.
     * @param value     the new value for the parameter. Must be a double.
     * @throws IllegalArgumentException if the parameter is not a fixed parameter in the model.
     */
    public void setFixedParamValue(Parameter parameter, double value) {
        if (!getFixedParameters().contains(parameter)) {
            throw new IllegalArgumentException(
                    "Not a fixed parameter in " + "this model: " + parameter);
        }

        // Note this assumes the fixedMappings are in the same order as the
        // fixed freeParameters.
        int index = getFixedParameters().indexOf(parameter);
        Mapping mapping = this.fixedMappings.get(index);
        mapping.setValue(value);
    }

    /**
     * <p>getErrVar.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @return a double
     */
    public double getErrVar(Node x) {
        Parameter param = this.semPm.getVarianceParameter(x);
        return getParamValue(param);
    }

    /**
     * <p>Getter for the field <code>errCovar</code>.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return a double
     */
    public double getErrCovar(Node x, Node y) {
        Parameter param = this.semPm.getCovarianceParameter(x, y);
        return getParamValue(param);
    }

    /**
     * <p>Getter for the field <code>edgeCoef</code>.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return a double
     */
    public double getEdgeCoef(Node x, Node y) {
        Parameter param = this.semPm.getCoefficientParameter(x, y);
        return getParamValue(param);
    }

    /**
     * <p>Getter for the field <code>edgeCoef</code>.</p>
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a double
     */
    public double getEdgeCoef(Edge edge) {
        if (!Edges.isDirectedEdge(edge)) {
            throw new IllegalArgumentException("Only directed edges have 'edge coefficients'");
        }
        return getEdgeCoef(edge.getNode1(), edge.getNode2());
    }

    /**
     * Sets the error variance value for a specific node in the model's structural equation.
     *
     * @param x     The node for which the error variance should be set.
     * @param value The value to set as the error variance.
     * @throws NullPointerException     If the given node is null.
     * @throws IllegalArgumentException If the given value is not a valid error variance.
     */
    public void setErrVar(Node x, double value) {
        Parameter param = this.semPm.getVarianceParameter(x);
        setParamValue(param, value);
    }

    /**
     * Sets the coefficient value for the edge between two nodes in the graph.
     *
     * @param x     The first node in the edge. Must be a {@link Node} object.
     * @param y     The second node in the edge. Must be a {@link Node} object.
     * @param value The value of the coefficient. Must be a double.
     */
    public void setEdgeCoef(Node x, Node y, double value) {
        Parameter param = this.semPm.getCoefficientParameter(x, y);
        setParamValue(param, value);
    }

    /**
     * <p>existsEdgeCoef.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean existsEdgeCoef(Node x, Node y) {
        return x != y && this.semPm.getCoefficientParameter(x, y) != null;
    }

    /**
     * <p>Setter for the field <code>errCovar</code>.</p>
     *
     * @param x     a {@link edu.cmu.tetrad.graph.Node} object
     * @param value a double
     */
    public void setErrCovar(Node x, double value) {
        SemGraph graph = getSemPm().getGraph();
        Node exogenousX = graph.getExogenous(x);
        setParamValue(exogenousX, exogenousX, value);
    }

    /**
     * <p>Setter for the field <code>errCovar</code>.</p>
     *
     * @param x     a {@link edu.cmu.tetrad.graph.Node} object
     * @param y     a {@link edu.cmu.tetrad.graph.Node} object
     * @param value a double
     */
    public void setErrCovar(Node x, Node y, double value) {
        Parameter param = this.semPm.getCovarianceParameter(x, y);
        setParamValue(param, value);
    }

    /**
     * Sets the mean value for a given node in the variableNodes list.
     *
     * @param node The Node object for which the mean value needs to be set.
     * @param mean The double value representing the mean value to be set.
     */
    public void setMean(Node node, double mean) {
        int index = this.variableNodes.indexOf(node);
        this.variableMeans[index] = mean;
    }

    /**
     * Sets the mean associated with the given node.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @param mean a double
     */
    public void setMeanStandardDeviation(Node node, double mean) {
        int index = this.variableNodes.indexOf(node);
        this.variableMeansStdDev[index] = mean;
    }

    /**
     * Sets the intercept for a specified node in the SEM model.
     *
     * @param node      a {@link Node} object representing the node
     * @param intercept a double value representing the new intercept to be set
     * @throws UnsupportedOperationException if the SEM model is cyclic
     */
    public void setIntercept(Node node, double intercept) {
        if (isCyclic()) {
            throw new UnsupportedOperationException("Setting and getting of "
                                                    + "intercepts is supported for acyclic SEMs only. The internal "
                                                    + "parameterizations uses variable means; the relationship "
                                                    + "between variable means and intercepts has not been fully "
                                                    + "worked out for the cyclic case.");
        }

        SemGraph semGraph = getSemPm().getGraph();

        System.out.println(semGraph);

        semGraph.setShowErrorTerms(false);
        Paths paths = new Paths(semGraph);
        List<Node> initialOrder = semGraph.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);

        double[] intercepts = new double[tierOrdering.size()];

        for (int i = 0; i < tierOrdering.size(); i++) {
            Node _node = tierOrdering.get(i);
            intercepts[i] = getIntercept(_node);
        }

        intercepts[tierOrdering.indexOf(node)] = intercept;

        for (int i = 0; i < tierOrdering.size(); i++) {
            Node _node = tierOrdering.get(i);

            List<Node> parents = semGraph.getParents(_node);

            double weightedSumOfParentMeans = 0.0;

            for (Node parent : parents) {
                if (parent.getNodeType() == NodeType.ERROR) {
                    continue;
                }

                double coef = getEdgeCoef(parent, _node);
                double mean = getMean(parent);
                weightedSumOfParentMeans += coef * mean;
            }

            double mean = weightedSumOfParentMeans + intercepts[i];
            setMean(_node, mean);
        }
    }

    /**
     * Calculates the intercept for a given node.
     *
     * @param node the node for which to calculate the intercept
     * @return the intercept value
     */
    public double getIntercept(Node node) {
        node = this.semPm.getGraph().getNode(node.getName());

        if (isCyclic()) {
            return Double.NaN;
        }

        SemGraph semGraph = getSemPm().getGraph();
        List<Node> parents = semGraph.getParents(node);

        double weightedSumOfParentMeans = 0.0;

        for (Node parent : parents) {
            if (parent.getNodeType() == NodeType.ERROR) {
                continue;
            }

            double coef = getEdgeCoef(parent, node);
            double mean = getMean(parent);
            weightedSumOfParentMeans += coef * mean;
        }

        double mean = getMean(node);
        return mean - weightedSumOfParentMeans;
    }

    /**
     * Calculates the mean value associated with a given {@link Node}.
     *
     * @param node the node for which the mean value is to be calculated
     * @return the mean value associated with the given node
     */
    public double getMean(Node node) {
        int index = this.variableNodes.indexOf(node);

        if (index == -1) {
            System.out.println("Expecting this node: " + node);
            System.out.println("Node list = " + this.variableNodes);
        }

        return this.variableMeans[index];
    }

    /**
     * <p>getMeans.</p>
     *
     * @return the means for variables in order.
     */
    public double[] getMeans() {
        double[] means = new double[this.variableMeans.length];
        System.arraycopy(this.variableMeans, 0, means, 0, this.variableMeans.length);
        return means;
    }

    /**
     * Calculates the mean standard deviation for the given node.
     *
     * @param node the node for which to calculate the mean standard deviation
     * @return the mean standard deviation of the node
     */
    public double getMeanStdDev(Node node) {
        int index = this.variableNodes.indexOf(node);
        return this.variableMeansStdDev[index];
    }

    /**
     * Returns the variance for a given node.
     *
     * @param node      The node for which the variance is calculated. Must be a {@link Node} object.
     * @param implCovar The implementation covariance matrix. Must be a {@link Matrix} object.
     * @return The variance value.
     */
    public double getVariance(Node node, Matrix implCovar) {
        if (getSemPm().getGraph().isExogenous(node)) {
//            if (node.getNodeType() == NodeType.ERROR) {
            Parameter parameter = getSemPm().getVarianceParameter(node);

            // This seems to be required to get the show/hide error terms
            // feature to work in the SemImEditor.
            if (parameter == null) {
                return Double.NaN;
            }

            return getParamValue(parameter);
        } else {
            int index = this.variableNodes.indexOf(node);
//            TetradMatrix implCovar = getImplCovar();
            return implCovar.get(index, index);
        }
    }

    /**
     * {@inheritDoc}
     */
    public double getStdDev(Node node, Matrix implCovar) {
        return sqrt(getVariance(node, implCovar));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gets the value of a single free parameter to the given value, where the free parameter is specified by the
     * endpoint nodes of its edge in the w graph. Note that coefficient freeParameters connect elements of
     * getVariableNodes(), whereas variance and covariance freeParameters connect elements of getExogenousNodes(). (For
     * variance freeParameters, nodeA and nodeB are the same.)
     */
    public double getParamValue(Node nodeA, Node nodeB) {
        Parameter parameter = null;

        if (nodeA == nodeB) {
            parameter = getSemPm().getVarianceParameter(nodeA);
        }

        if (parameter == null) {
            parameter = getSemPm().getCovarianceParameter(nodeA, nodeB);
        }

        if (parameter == null) {
            parameter = getSemPm().getCoefficientParameter(nodeA, nodeB);
        }

        if (parameter == null) {
            return Double.NaN;
        }

        if (!getFreeParameters().contains(parameter)) {
            return Double.NaN;
        }

        return getParamValue(parameter);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value of a single free parameter to the given value, where the free parameter is specified by the
     * endpoint nodes of its edge in the graph. Note that coefficient freeParameters connect elements of
     * getVariableNodes(), whereas variance and covariance freeParameters connect elements of getExogenousNodes(). (For
     * variance freeParameters, nodeA and nodeB are the same.)
     */
    public void setParamValue(Node nodeA, Node nodeB, double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Please remove or impute missing data.");
        }

        if (nodeA == null || nodeB == null) {
            throw new NullPointerException("Nodes must not be null: nodeA = " + nodeA + ", nodeB = " + nodeB);
        }

        Parameter parameter = null;

        if (nodeA == nodeB) {
            parameter = getSemPm().getVarianceParameter(nodeA);
        }

        if (parameter == null) {
            parameter = getSemPm().getCoefficientParameter(nodeA, nodeB);
        }

        if (parameter == null) {
            parameter = getSemPm().getCovarianceParameter(nodeA, nodeB);
        }

        if (parameter == null) {
            throw new IllegalArgumentException("There is no parameter in "
                                               + "model for an edge from " + nodeA + " to " + nodeB + ".");
        }

        if (!this.getFreeParameters().contains(parameter)) {
            throw new IllegalArgumentException(
                    "Not a free parameter in " + "this model: " + parameter);
        }

        setParamValue(parameter, value);
    }

    /**
     * <p>Getter for the field <code>freeParameters</code>.</p>
     *
     * @return the (unmodifiable) list of free freeParameters in the model.
     */
    public List<Parameter> getFreeParameters() {
        return this.freeParameters;
    }

    /**
     * <p>getNumFreeParams.</p>
     *
     * @return the number of free freeParameters.
     */
    public int getNumFreeParams() {
        return getFreeParameters().size();
    }

    /**
     * <p>Getter for the field <code>fixedParameters</code>.</p>
     *
     * @return the (unmodifiable) list of fixed freeParameters in the model.
     */
    public List<Parameter> getFixedParameters() {
        return this.fixedParameters;
    }

    private List<Parameter> getMeanParameters() {
        return this.meanParameters;
    }

    /**
     * <p>getNumFixedParams.</p>
     *
     * @return the number of free freeParameters.
     */
    public int getNumFixedParams() {
        return getFixedParameters().size();
    }

    /**
     * The list of measured and latent nodes for the semPm. (Unmodifiable.)
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariableNodes() {
        return this.variableNodes;
    }

    /**
     * The list of measured nodes for the semPm. (Unmodifiable.)
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getMeasuredNodes() {
        return this.measuredNodes;
    }

    /**
     * <p>Getter for the field <code>sampleSize</code>.</p>
     *
     * @return the sample size (that is, the sample size of the CovarianceMatrix provided at construction time).
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * <p>Getter for the field <code>edgeCoef</code>.</p>
     *
     * @return a copy of the matrix of edge coefficients. Note that edgeCoefC[i][j] is the coefficient of the edge from
     * getVariableNodes().get(i) to getVariableNodes().get(j), or 0.0 if this edge is not in the graph. The values of
     * these may be changed, but the array itself may not.
     */
    public Matrix getEdgeCoef() {
        return this.edgeCoef.copy();
    }

    /**
     * <p>Getter for the field <code>errCovar</code>.</p>
     *
     * @return a copy of the matrix of error covariances. Note that errCovar[i][j] is the covariance of the error term
     * of getExoNodes().get(i) and getExoNodes().get(j), with the special case (duh!) that errCovar[i][i] is the
     * variance of getExoNodes.get(i). The values of these may be changed, but the array itself may not.
     */
    public Matrix getErrCovar() {
        return errCovar().copy();
    }

    /**
     * {@inheritDoc}
     */
    public Matrix getImplCovar(boolean recalculate) {
        if (!recalculate && this.implCovar != null) {
            return this.implCovar;
        } else {
            return implCovar();
        }
    }

    /**
     * <p>getImplCovarMeas.</p>
     *
     * @return a copy of the implied covariance matrix over the measured variables only.
     */
    public Matrix getImplCovarMeas() {
        return implCovarMeas().copy();
    }

    /**
     * <p>getSampleCovar.</p>
     *
     * @return a copy of the sample covariance matrix, or null if no sample covar has been set.
     */
    public Matrix getSampleCovar() {
        return this.sampleCovarC == null ? null : this.sampleCovarC.copy();
    }

    /**
     * The value of the maximum likelihood function for the getModel the model (Bollen 107). To optimize, this should be
     * minimized.
     *
     * @return a double
     */
    public double getScore() {
        if (this.scoreType == ScoreType.Fml) {
            return getFml2();
        } else if (this.scoreType == ScoreType.Fgls) {
            return getFgls();
        } else {
            throw new IllegalStateException("Unrecognized score type; " + this.scoreType);
        }
    }

    private double getFml2() {
        Matrix sigma;

        try {
            sigma = implCovarMeas();
        } catch (Exception e) {
            return Double.NaN;
        }

        Matrix s = this.sampleCovarC;

        double fml;

        try {
            fml = FastMath.log(sigma.det()) + (s.times(sigma.inverse())).trace() - FastMath.log(s.det()) - getMeasuredNodes().size();
        } catch (Exception e) {
            return Double.NaN;
        }

        return fml;
    }

    private double getFgls() {
        Matrix implCovarMeas;

        try {
            implCovarMeas = implCovarMeas();
        } catch (Exception e) {
            return Double.NaN;
        }

        if (this.sampleCovInv == null) {
            Matrix sampleCovar = this.sampleCovarC;
            this.sampleCovInv = sampleCovar.inverse();
        }

        Matrix I = Matrix.identity(implCovarMeas.getNumRows());
        Matrix diff = I.minus((implCovarMeas.times(this.sampleCovInv)));

        return 0.5 * (diff.times(diff)).trace();
    }

    /**
     * The negative of the log likelihood function for the getModel model, with the constant chopped off. (Bollen 134).
     * This is an alternative, more efficient, optimization function to Fml which produces the same result when
     * minimized.
     *
     * @return a double
     */
    public double getTruncLL() {
        // Formula Bollen p. 263.

        Matrix Sigma = implCovarMeas();

        // Using (n - 1) / n * s as in Bollen p. 134 causes sinkholes to open
        // up immediately. Not sure why.
        Matrix S = this.sampleCovarC;
        int n = getSampleSize();
        return -(n - 1) / 2. * (logDet(Sigma) + traceAInvB(Sigma, S));
    }

    /**
     * <p>getBicScore.</p>
     *
     * @return BIC score, calculated as chisq - dof. This is equal to getFullBicScore() up to a constant.
     */
    public double getBicScore() {
        int dof = getSemPm().getDof();
        return getChiSquare() - dof * FastMath.log(this.sampleSize);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getRmsea() {
        double v = getChiSquare() - this.semPm.getDof();
        double v1 = this.semPm.getDof() * (getSampleSize() - 1);
        return sqrt(v) / sqrt(v1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getCfi() {
        if (getSampleCovar() == null) {
            return Double.NaN;
        }

        SemIm nullIm = independenceModel();
        double nullChiSq = nullIm.getChiSquare();
        double dNull = nullChiSq - nullIm.getSemPm().getDof();
        double dProposed = getChiSquare() - getSemPm().getDof();
        return (dNull - dProposed) / dNull;
    }

    private SemIm independenceModel() {
        Graph nullModel = new SemGraph(getSemPm().getGraph());
        nullModel.removeEdges(nullModel.getEdges());
        SemPm nullPm = new SemPm(nullModel);
        CovarianceMatrix sampleCovar = new CovarianceMatrix(getMeasuredNodes(), getSampleCovar(), getSampleSize());

        return new SemEstimator(sampleCovar, nullPm).estimate();
    }

    /**
     * <p>getChiSquare.</p>
     *
     * @return the chi square value for the model.
     */
    public double getChiSquare() {
        return (getSampleSize() - 1) * getScore();
    }

    /**
     * <p>getPValue.</p>
     *
     * @return the p-value for the model.
     */
    public double getPValue() {
        double chiSquare = getChiSquare();
        int dof = this.semPm.getDof();
        if (dof <= 0) {
            return Double.NaN;
        } else if (chiSquare < 0) {
            return Double.NaN;
        } else {
            return 1.0 - new ChiSquaredDistribution(dof).cumulativeProbability(chiSquare);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This simulate method uses the implied covariance metrix directly to simulate data, instead of going tier by tier.
     * It should work for cyclic graphs as well as acyclic graphs.
     */
    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        if (this.semPm.getGraph().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize, latentDataSaved);
        }

        return simulateDataReducedForm(sampleSize, latentDataSaved);
    }

    /**
     * <p>Setter for the field <code>scoreType</code>.</p>
     *
     * @param scoreType a {@link edu.cmu.tetrad.sem.ScoreType} object
     */
    public void setScoreType(ScoreType scoreType) {
        if (scoreType == null) {
            scoreType = ScoreType.Fgls;
        }
        this.scoreType = scoreType;
    }

    private DataSet simulateTimeSeries(int sampleSize, boolean latentDataSaved) {
        SemGraph semGraph = new SemGraph(this.semPm.getGraph());
        semGraph.setShowErrorTerms(true);
        TimeLagGraph timeSeriesGraph = this.semPm.getGraph().getTimeLagGraph();

        List<Node> variables = new ArrayList<>();

        List<Node> lag0Nodes = timeSeriesGraph.getLag0Nodes();

        for (Node node : lag0Nodes) {
            ContinuousVariable _node = new ContinuousVariable(timeSeriesGraph.getNodeId(node).getName());
            _node.setNodeType(node.getNodeType());
            variables.add(_node);
        }

        DataSet fullData = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);

        Map<Node, Integer> nodeIndices = new HashMap<>();

        for (int i = 0; i < lag0Nodes.size(); i++) {
            nodeIndices.put(lag0Nodes.get(i), i);
        }

        Graph contemporaneousDag = timeSeriesGraph.subgraph(timeSeriesGraph.getLag0Nodes());

        Paths paths = contemporaneousDag.paths();
        List<Node> initialOrder = contemporaneousDag.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);

        for (int currentStep = 0; currentStep < sampleSize; currentStep++) {
            for (Node to : tierOrdering) {
                List<Node> parents = semGraph.getNodesInTo(to, Endpoint.ARROW);

                double sum = 0.0;

                for (Node parent : parents) {
                    if (parent.getNodeType() == NodeType.ERROR) {
                        if (semGraph.getChildren(parent).size() != 1) {
                            continue;
                        }
                        Node child = semGraph.getChildren(parent).iterator().next();
                        double paramValue = getParamValue(child, child);
                        sum += getNextNormal(0.0, paramValue);
                    } else {
                        TimeLagGraph.NodeId id = timeSeriesGraph.getNodeId(parent);
                        int fromIndex = nodeIndices.get(timeSeriesGraph.getNode(id.getName(), 0));
                        int lag = id.getLag();
                        if (currentStep > lag) {
                            double coef = getParamValue(parent, to);
                            double fromValue = fullData.getDouble(currentStep - lag, fromIndex);
                            sum += coef * fromValue;
                        } else {
                            sum += getNextNormal(0.0, 0.5);
                        }
                    }
                }

                if (to.getNodeType() != NodeType.ERROR) {
                    int toIndex = nodeIndices.get(to);
                    fullData.setDouble(currentStep, toIndex, sum);
                }
            }
        }

        return latentDataSaved ? fullData : DataTransforms.restrictToMeasured(fullData);
    }

    private double getNextNormal(double mean, double stdDev) {
        numRandomCalls++;
        return RandomUtil.getInstance().nextNormal(mean, stdDev);
    }

//    /**
//     * This simulate method uses the implied covariance metrix directly to
//     * simulate data, instead of going tier by tier. It should work for cyclic
//     * graphs as well as acyclic graphs.
//     *
//     * @param sampleSize how many data points in sample
//     * @param seed       a seed for random number generation
//     */
//    @Override
//    public DataSet simulateData(int sampleSize, long seed, boolean latentDataSaved) {
//        RandomUtil random = RandomUtil.getInstance();
//        random.setSeed(seed);
//        return simulateData(sampleSize, latentDataSaved);
//    }

    /**
     * Simulates data from this Sem using a Cholesky decomposition of the implied covariance matrix. This method works
     * even when the underlying graph is cyclic.
     *
     * @param sampleSize      the number of rows of data to simulate.
     * @param latentDataSaved True iff data for latents should be saved.
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet simulateDataCholesky(int sampleSize, boolean latentDataSaved) {
        List<Node> variables = new LinkedList<>();

        if (latentDataSaved) {
            variables.addAll(getVariableNodes());
        } else {
            variables.addAll(getMeasuredNodes());
        }

        List<Node> newVariables = new ArrayList<>();

        for (Node node : variables) {
            ContinuousVariable continuousVariable = new ContinuousVariable(node.getName());
            continuousVariable.setNodeType(node.getNodeType());
            newVariables.add(continuousVariable);
        }

        Matrix impliedCovar = implCovar();

        DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, newVariables.size()), newVariables);
        Matrix cholesky = MatrixUtils.cholesky(impliedCovar);

        // Simulate the data by repeatedly calling the Cholesky.exogenousData
        // method. Store only the data for the measured variables.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            double[] exoData = new double[cholesky.getNumRows()];

            for (int i = 0; i < exoData.length; i++) {
                exoData[i] = RandomUtil.getInstance().nextNormal(0, 1);
                //            exoData[i] = randomUtil.nextUniform(-1, 1);
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            double[] point = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j = 0; j <= i; j++) {
                    sum += cholesky.get(i, j) * exoData[j];
                }

                point[i] = sum;
            }

            for (int col = 0; col < variables.size(); col++) {
                int index = getVariableNodes().indexOf(variables.get(col));
                double value = point[index] + this.variableMeans[col];

                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException("Value out of range: " + value);
                }

                if (isSimulatedPositiveDataOnly() && value < 0) {
                    row--;
                    continue ROW;
                }

                fullDataSet.setDouble(row, col, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataTransforms.restrictToMeasured(fullDataSet);
        }
    }

    /**
     * <p>simulateDataRecursive.</p>
     *
     * @param sampleSize      a int
     * @param latentDataSaved a boolean
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet simulateDataRecursive(int sampleSize,
                                         boolean latentDataSaved) {
        return simulateDataRecursive(sampleSize, null, latentDataSaved);
    }

    /**
     * This simulates data by picking random values for the exogenous terms and percolating this information down
     * through the SEM, assuming it is acyclic. Fast for large simulations but hangs for cyclic models.
     *
     * @param sampleSize > 0.
     * @return the simulated data set.
     */
    private DataSet simulateDataRecursive(int sampleSize, DataSet initialValues,
                                          boolean latentDataSaved) {
        int errorType = this.params.getInt(Params.SIMULATION_ERROR_TYPE);

        List<Node> variables = new LinkedList<>();
        List<Node> variableNodes = getVariableNodes();

        for (Node node : variableNodes) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            variables.add(var);
        }

        DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);

        // Create some index arrays to hopefully speed up the simulation.
        Graph graph = new EdgeListGraph(getSemPm().getGraph());
        Paths paths = graph.paths();
        List<Node> initialOrder = graph.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);

        int[] tierIndices = new int[variableNodes.size()];

        for (int i = 0; i < tierIndices.length; i++) {
            tierIndices[i] = variableNodes.indexOf(tierOrdering.get(i));
        }

        int[][] _parents = new int[variableNodes.size()][];

        for (int i = 0; i < variableNodes.size(); i++) {
            Node node = variableNodes.get(i);
            List<Node> parents = new ArrayList<>(graph.getParents(node));

            parents.removeIf(_node -> _node.getNodeType() == NodeType.ERROR);

            _parents[i] = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                Node _parent = parents.get(j);
                _parents[i][j] = variableNodes.indexOf(_parent);
            }
        }

        Matrix cholesky = MatrixUtils.cholesky(errCovar());

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            double[] exoData = new double[cholesky.getNumRows()];

            for (int i = 0; i < exoData.length; i++) {
                if (errorType == 1) {
                    exoData[i] = getNextNormal(0,
                            sqrt(this.errCovar.get(i, i)));
                } else if (errorType == 2) {
                    exoData[i] = getNextUniform();
                } else if (errorType == 3) {
                    exoData[i] = getNextExponential();
                } else if (errorType == 4) {
                    exoData[i] = getNextGumbel();
                } else if (errorType == 5) {
                    exoData[i] = getNextGamma();
                }
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            double[] point = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j1 = 0; j1 < exoData.length; j1++) {
                    sum += cholesky.get(i, j1) * exoData[j1];
                }

                point[i] = sum;
            }

            Vector e = new Vector(point);

            for (int tier = 0; tier < tierOrdering.size(); tier++) {
                Node node = tierOrdering.get(tier);
                ConnectionFunction function = this.functions.get(node);
                int col = tierIndices[tier];

                Distribution distribution = this.distributions.get(node);
                double value;

                // If it's an exogenous node and initial data has been specified, use that data instead.
                Node node1 = tierOrdering.get(tier);

                Node initNode = null;
                int initCol = -1;

                if (initialValues != null) {
                    initNode = initialValues.getVariable(node1.getName());
                    initCol = initialValues.getColumn(initNode);
                }

                if (_parents[col].length == 0 && initialValues != null
                    && initCol != -1) {
                    int column = initialValues.getColumn(initNode);
                    value = initialValues.getDouble(row, column);
                } else {
                    if (distribution == null) {
                        value = e.get(col);
                    } else {
                        value = distribution.nextRandom();
                    }
                }

                if (function != null) {
                    Node[] parents = function.getInputNodes();
                    double[] parentValues = new double[parents.length];

                    for (int j = 0; j < parents.length; j++) {
                        Node parent = parents[j];
                        int index = variableNodes.indexOf(parent);
                        parentValues[j] = fullDataSet.getDouble(row, index);
                    }

                    value += function.valueAt(parentValues);

                    if (initialValues == null && isSimulatedPositiveDataOnly() && value < 0) {
                        row--;
                        continue ROW;
                    }

                } else {
                    for (int j = 0; j < _parents[col].length; j++) {
                        int parent = _parents[col][j];
                        double parentValue = fullDataSet.getDouble(row, parent);
                        double parentCoef = this.edgeCoef.get(parent, col);
                        value += parentValue * parentCoef;
                    }

                    if (isSimulatedPositiveDataOnly() && value < 0) {
                        row--;
                        continue ROW;
                    }

                }
                fullDataSet.setDouble(row, col, value);
            }
        }

        for (int i = 0; i < fullDataSet.getNumRows(); i++) {
            for (int j = 0; j < fullDataSet.getNumColumns(); j++) {
                fullDataSet.setDouble(i, j, fullDataSet.getDouble(i, j) + this.variableMeans[j]);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataTransforms.restrictToMeasured(fullDataSet);
        }
    }

    private double getNextGamma() {
        numRandomCalls++;
        return RandomUtil.getInstance().nextGamma(this.errorParam1,
                this.errorParam2);
    }

    private double getNextGumbel() {
        numRandomCalls++;
        return RandomUtil.getInstance().nextGumbel(this.errorParam1,
                this.errorParam2);
    }

    private double getNextExponential() {
        numRandomCalls++;
        return RandomUtil.getInstance().nextExponential(this.errorParam1);
    }

    private double getNextUniform() {
        numRandomCalls++;
        return RandomUtil.getInstance().nextUniform(this.errorParam1, this.errorParam2);
    }

    /**
     * <p>simulateDataReducedForm.</p>
     *
     * @param sampleSize      a int
     * @param latentDataSaved a boolean
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet simulateDataReducedForm(int sampleSize, boolean latentDataSaved) {
        int errorType = this.params.getInt(Params.SIMULATION_ERROR_TYPE);
        double errorParam1 = params.getDouble(Params.SIMULATION_PARAM1);
        double errorParam2 = params.getDouble(Params.SIMULATION_PARAM2);

        int numVars = getVariableNodes().size();

        // Calculate inv(I - edgeCoefC)
        Matrix B = edgeCoef().transpose();
        Matrix iMinusBInv = Matrix.identity(B.getNumRows()).minus(B).inverse();

        // Pick error values e, for each calculate inv * e.
        Matrix sim = new Matrix(sampleSize, numVars);

        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            Vector e = new Vector(this.edgeCoef.getNumColumns());

            for (int i = 0; i < e.size(); i++) {
                if (errorType == 1) {
                    double errCovar = this.errCovar.get(i, i);
                    if (errCovar == 0.0) {
                        e.set(i, 0.0);
                    } else {
                        e.set(i, RandomUtil.getInstance().nextNormal(0, sqrt(errCovar)));
                    }
                } else if (errorType == 2) {
                    e.set(i, RandomUtil.getInstance().nextUniform(errorParam1, errorParam2));
                } else if (errorType == 3) {
                    e.set(i, RandomUtil.getInstance().nextExponential(errorParam1));
                } else if (errorType == 4) {
                    e.set(i, RandomUtil.getInstance().nextGumbel(errorParam1, errorParam2));
                } else if (errorType == 5) {
                    e.set(i, RandomUtil.getInstance().nextGamma(errorParam1, errorParam2));
                }
            }

            // Step 3. Calculate the new rows in the data.
            Vector sample = iMinusBInv.times(e);
            sim.assignRow(row, sample);

            for (int col = 0; col < sample.size(); col++) {
                double value = sim.get(row, col) + this.variableMeans[col];

                if (isSimulatedPositiveDataOnly() && value < 0) {
                    row--;
                    continue ROW;
                }

                sim.set(row, col, value);
            }
        }

        List<Node> continuousVars = new ArrayList<>();

        for (Node node : getVariableNodes()) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        DataSet fullDataSet = new BoxDataSet(new DoubleDataBox(sim.toArray()), continuousVars);

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataTransforms.restrictToMeasured(fullDataSet);
        }
    }

    // For testing.

    /**
     * <p>simulateOneRecord.</p>
     *
     * @param e a {@link edu.cmu.tetrad.util.Vector} object
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector simulateOneRecord(Vector e) {
        // Calculate inv(I - edgeCoefC)
        Matrix edgeCoef = edgeCoef().copy().transpose();

        Matrix iMinusB = Matrix.identity(edgeCoef.getNumRows()).minus(edgeCoef);

        Matrix inv = iMinusB.inverse();

        return inv.times(e);
    }

    /**
     * Iterates through all freeParameters, picking values for them from the distributions that have been set for them.
     */
    public void initializeValues() {
        for (Mapping fixedMapping : this.fixedMappings) {
            Parameter parameter = fixedMapping.getParameter();
            fixedMapping.setValue(initialValue(parameter));
        }

        for (Mapping freeMapping : this.freeMappings) {
            Parameter parameter = freeMapping.getParameter();
            freeMapping.setValue(initialValue(parameter));
        }
    }

    /**
     * {@inheritDoc}
     */
    public double getStandardError(Parameter parameter, int maxFreeParams) {
        Matrix sampleCovar = getSampleCovar();

        if (sampleCovar == null) {
            return Double.NaN;
        }

        if (getFreeParameters().contains(parameter)) {
            if (getNumFreeParams() <= maxFreeParams) {
                if (parameter.getNodeA() != parameter.getNodeB()) {
                    Node nodeA = parameter.getNodeA();
                    Node nodeB = parameter.getNodeB();
                    Node parent;
                    Node child;

                    Graph graph = getSemPm().getGraph();

                    if (graph.isParentOf(nodeA, nodeB)) {
                        parent = nodeA;
                        child = nodeB;
                    } else {
                        parent = nodeB;
                        child = nodeA;
                    }

                    if (child.getName().startsWith("E_")) {
                        return Double.NaN;
                    }

                    CovarianceMatrix cov = new CovarianceMatrix(this.measuredNodes, sampleCovar, this.sampleSize);
                    Regression regression = new RegressionCovariance(cov);
                    List<Node> parents = graph.getParents(child);

                    parents.removeIf(node -> node.getName().startsWith("E_"));

                    if (!(child.getNodeType() == NodeType.LATENT) && !containsLatent(parents)) {
                        RegressionResult result = regression.regress(child, parents);
                        double[] se = result.getSe();
                        return se[parents.indexOf(parent) + 1];
                    }
                }

                if (this.sampleCovarC == null) {
                    this.standardErrors = null;
                    return Double.NaN;
                }

                int index = getFreeParameters().indexOf(parameter);
                double[] doubles = standardErrors();

                if (doubles == null) {
                    return Double.NaN;
                }

                return doubles[index];
            } else {
                return Double.NaN;
            }
        } else if (getFixedParameters().contains(parameter)) {
            return 0.0;
        }

        throw new IllegalArgumentException(
                "That is not a parameter of this model: " + parameter);
    }

    private boolean containsLatent(List<Node> parents) {
        for (Node node : parents) {
            if (node.getNodeType() == NodeType.LATENT) {
                return true;
            }
        }

        return false;
    }

    /**
     * <p>listUnmeasuredLatents.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> listUnmeasuredLatents() {
        return unmeasuredLatents(getSemPm());
    }

    /**
     * {@inheritDoc}
     */
    public double getTValue(Parameter parameter, int maxFreeParams) {
        return getParamValue(parameter)
               / getStandardError(parameter, maxFreeParams);
    }

    /**
     * {@inheritDoc}
     */
    public double getPValue(Parameter parameter, int maxFreeParams) {
        double tValue = getTValue(parameter, maxFreeParams);
        int df = getSampleSize() - 1;
        return 2.0 * (1.0 - ProbUtils.tCdf(FastMath.abs(tValue), df));
    }

    /**
     * <p>isParameterBoundsEnforced.</p>
     *
     * @return a boolean
     */
    public boolean isParameterBoundsEnforced() {
        return this.parameterBoundsEnforced;
    }

    /**
     * {@inheritDoc}
     */
    public void setParameterBoundsEnforced(
            boolean parameterBoundsEnforced) {
        this.parameterBoundsEnforced = parameterBoundsEnforced;
    }

    /**
     * <p>isEstimated.</p>
     *
     * @return a boolean
     */
    public boolean isEstimated() {
        return this.estimated;
    }

    /**
     * <p>Setter for the field <code>estimated</code>.</p>
     *
     * @param estimated a boolean
     */
    public void setEstimated(boolean estimated) {
        this.estimated = estimated;
    }

    /**
     * <p>isCyclic.</p>
     *
     * @return a boolean
     */
    public boolean isCyclic() {
        if (!this.cyclicChecked) {
            this.cyclic = this.semPm.getGraph().paths().existsDirectedCycle();
            this.cyclicChecked = true;
        }

        return this.cyclic;
    }

    /**
     * <p>getVariableNode.</p>
     *
     * @param name a {@link java.lang.String} object
     * @return the variable by the given name, or null if none exists.
     * @throws java.lang.NullPointerException if name is null.
     */
    public Node getVariableNode(String name) {
        if (name == null) {
            throw new NullPointerException();
        }

        List<Node> variables = getVariableNodes();

        for (Node variable : variables) {
            if (name.equals(variable.getName())) {
                return variable;
            }
        }

        return null;
    }

    /**
     * <p>toString.</p>
     *
     * @return a string representation of the Sem (pretty detailed).
     */
    public String toString() {
        List<String> varNames = new ArrayList<>();

        for (Node node : this.variableNodes) {
            varNames.add(node.getName());
        }

        StringBuilder buf = new StringBuilder();

        buf.append("\nVariable nodes:\n\n");
        buf.append(getVariableNodes());

        buf.append("\n\nMeasured nodes:\n\n");
        buf.append(getMeasuredNodes());

        buf.append("\n\nEdge coefficient matrix:\n");
        buf.append(MatrixUtils.toStringSquare(edgeCoef().toArray(), varNames));

        buf.append("\n\nError covariance matrix:\n");
        buf.append(MatrixUtils.toStringSquare(getErrCovar().toArray(), varNames));

        buf.append("\n\nVariable means:\n");

        for (int i = 0; i < getVariableNodes().size(); i++) {
            buf.append("\nMean(");
            buf.append(getVariableNodes().get(i));
            buf.append(") = ");
            buf.append(this.variableMeans[i]);
        }

        buf.append("\n\nSample size = ");
        buf.append(this.sampleSize);

        if (this.sampleCovarC == null) {
            buf.append("\n\nSample covaraince matrix not specified**");
        } else {
            buf.append("\n\nsample cov:\n");
            buf.append(MatrixUtils.toString(this.sampleCovarC.toArray()));
        }

        buf.append("\n\nimplCovar:\n");

        try {
            buf.append(MatrixUtils.toString(implCovar().toArray()));
        } catch (IllegalFormatException e) {
            e.printStackTrace();
        }

        buf.append("\n\nimplCovarMeas:\n");
        buf.append(MatrixUtils.toString(implCovarMeas().toArray()));

        if (this.sampleCovarC != null) {
            buf.append("\n\nmodel chi square = ");
            buf.append(getChiSquare());

            buf.append("\nmodel dof = ");
            buf.append(this.semPm.getDof());

            buf.append("\nmodel p-value = ");
            buf.append(getPValue());
        }

        buf.append("\n\nfree mappings:\n");
        for (int i = 0; i < this.freeMappings.size(); i++) {
            Mapping iMapping = this.freeMappings.get(i);
            buf.append("\n");
            buf.append(i);
            buf.append(". ");
            buf.append(iMapping);
        }

        buf.append("\n\nfixed mappings:\n");
        for (int i = 0; i < this.fixedMappings.size(); i++) {
            Mapping iMapping = this.fixedMappings.get(i);
            buf.append("\n");
            buf.append(i);
            buf.append(". ");
            buf.append(iMapping);
        }

        return buf.toString();
    }

    private void retainPreviousValues(SemIm oldSemIm) {
        if (oldSemIm == null) {
            System.out.println("old sem im null");
            return;
        }

        List<Node> nodes = this.semPm.getGraph().getNodes();
        Graph oldGraph = oldSemIm.getSemPm().getGraph();

        for (Node nodeA : nodes) {
            for (Node nodeB : nodes) {
                Node _nodeA = oldGraph.getNode(nodeA.getName());
                Node _nodeB = oldGraph.getNode(nodeB.getName());

                if (_nodeA == null || _nodeB == null) {
                    continue;
                }

                double _value = oldSemIm.getParamValue(_nodeA, _nodeB);

                if (!Double.isNaN(_value)) {
                    try {
                        Parameter _parameter = oldSemIm.getSemPm().getParameter(_nodeA, _nodeB);
                        Parameter parameter = getSemPm().getParameter(nodeA, nodeB);

                        if (parameter.getType() != _parameter.getType()) {
                            continue;
                        }

                        if (parameter.isFixed()) {
                            continue;
                        }

                        setParamValue(nodeA, nodeB, _value);
                    } catch (IllegalArgumentException e) {
                        System.out.println("Couldn't set " + nodeA + ", " + nodeB);
                    }
                }
            }
        }
    }

    private List<Node> unmeasuredLatents(SemPm semPm) {
        SemGraph graph = semPm.getGraph();

        List<Node> unmeasuredLatents = new LinkedList<>();

        NODES:
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                for (Node child : graph.getChildren(node)) {
                    if (child.getNodeType() == NodeType.MEASURED) {
                        continue NODES;
                    }
                }

                unmeasuredLatents.add(node);
            }
        }

        return unmeasuredLatents;
    }

    private Matrix errCovar() {
        return this.errCovar;
    }

    private Matrix implCovar() {
        computeImpliedCovar();
        return this.implCovar;
    }

    private Matrix implCovarMeas() {
        computeImpliedCovar();
        // Submatrix of implied covar for measured vars only.
        int size = getMeasuredNodes().size();
        /*
      The covariance matrix of the measured variables only. May be null if
      implCovar has not been calculated yet. This is the submatrix of
      implCovar, restricted to just the measured variables. It is recalculated
      each time the F_ML function is recalculated.

         */
        Matrix implCovarMeas = new Matrix(size, size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Node iNode = getMeasuredNodes().get(i);
                Node jNode = getMeasuredNodes().get(j);

                if (this.variablesHash == null) {
                    this.variablesHash = new HashMap<>();

                    for (int k = 0; k < this.variableNodes.size(); k++) {
                        this.variablesHash.put(this.variableNodes.get(k), k);
                    }
                }

                int _i = this.variablesHash.get(iNode);
                int _j = this.variablesHash.get(jNode);

                implCovarMeas.set(i, j, this.implCovar.get(_i, _j));
            }
        }
        return implCovarMeas;
    }

    private List<Parameter> initFreeParameters() {
        return Collections.unmodifiableList(this.semPm.getFreeParameters());
    }

    /**
     * @return a random value from the appropriate distribution for the given parameter.
     */
    private double initialValue(Parameter parameter) {
        if (!getSemPm().getParameters().contains(parameter)) {
            throw new IllegalArgumentException("Not a parameter for this SEM: " + parameter);
        }

        if (parameter.isInitializedRandomly()) {
            if (parameter.getType() == ParamType.COEF) {
                double coefLow = getParams().getDouble("coefLow", 0.0);
                double coefHigh = getParams().getDouble("coefHigh", 1.0);
                double value = new Split(coefLow, coefHigh).nextRandom();
                if (getParams().getBoolean("coefSymmetric", true)) {
                    return value;
                } else {
                    return FastMath.abs(value);
                }
            } else if (parameter.getType() == ParamType.COVAR) {
                double covLow = getParams().getDouble("covLow", 0.1);
                double covHigh = getParams().getDouble("covHigh", 0.2);
                double value = new Split(covLow, covHigh).nextRandom();
                if (getParams().getBoolean("covSymmetric", true)) {
                    return value;
                } else {
                    return FastMath.abs(value);
                }
            } else { //if (parameter.getType() == ParamType.VAR) {
                return RandomUtil.getInstance().nextUniform(getParams().getDouble("varLow", 1), getParams().getDouble("varHigh", 3));
            }
        } else {
            return parameter.getStartingValue();
        }
    }

    /**
     * @return the (unmodifiable) list of freeParameters (type Param).
     */
    private List<Mapping> freeMappings() {
        return this.freeMappings;
    }

    /**
     * @return A submatrix of <code>covMatrix</code> with the order of its variables the same as in <code>semPm</code>.
     * @throws IllegalArgumentException if not all of the variables of
     *                                  <code>semPm</code> are in <code>covMatrix</code>.
     */
    private ICovarianceMatrix fixVarOrder(ICovarianceMatrix covMatrix) {
        List<String> varNamesList = new ArrayList<>();

        for (int i = 0; i < getMeasuredNodes().size(); i++) {
            Node node = getMeasuredNodes().get(i);
            varNamesList.add(node.getName());
        }

        //System.out.println("CovarianceMatrix ar order: " + varNamesList);
        String[] measuredVarNames = varNamesList.toArray(new String[0]);
        return covMatrix.getSubmatrix(measuredVarNames);
    }

    /**
     * Creates an unmodifiable list of freeMappings in the same order as the given list of freeParameters.
     */
    private List<Mapping> createMappings(List<Parameter> parameters) {
        List<Mapping> mappings = new ArrayList<>();
        SemGraph graph = getSemPm().getGraph();

        for (Parameter parameter : parameters) {
            Node nodeA = graph.getVarNode(parameter.getNodeA());
            Node nodeB = graph.getVarNode(parameter.getNodeB());

            if (nodeA == null || nodeB == null) {
                throw new IllegalArgumentException("Missing variable--either " + nodeA + " or " + nodeB + " parameter = " + parameter + ".");
            }

            int i = getVariableNodes().indexOf(nodeA);
            int j = getVariableNodes().indexOf(nodeB);

            if (parameter.getType() == ParamType.COEF) {
                Mapping mapping = new Mapping(this, parameter, edgeCoef(), i, j);
                mappings.add(mapping);
            } else if (parameter.getType() == ParamType.VAR) {
                Mapping mapping = new Mapping(this, parameter, errCovar(), i, i);
                mappings.add(mapping);
            } else if (parameter.getType() == ParamType.COVAR) {
                Mapping mapping = new Mapping(this, parameter, errCovar(), i, j);
                mappings.add(mapping);
            }
        }

        return Collections.unmodifiableList(mappings);
    }

    private List<Parameter> initFixedParameters() {
        List<Parameter> fixedParameters = new ArrayList<>();

        for (Parameter _parameter : getSemPm().getParameters()) {
            ParamType type = _parameter.getType();

            if (type == ParamType.VAR || type == ParamType.COVAR || type == ParamType.COEF) {
                if (_parameter.isFixed()) {
                    fixedParameters.add(_parameter);
                }
            }
        }

        return Collections.unmodifiableList(fixedParameters);
    }

    private List<Parameter> initMeanParameters() {
        List<Parameter> meanParameters = new ArrayList<>();

        for (Parameter param : getSemPm().getParameters()) {
            if (param.getType() == ParamType.MEAN) {
                meanParameters.add(param);
            }
        }

        return Collections.unmodifiableList(meanParameters);
    }

    /**
     * Computes the implied covariance matrices of the Sem. There are two:
     * <code>implCovar </code> contains the covariances of all the variables and
     * <code>implCovarMeas</code> contains covariance for the measured variables
     * only.
     */
    private void computeImpliedCovar() {
        Matrix edgeCoefT = edgeCoef().transpose();// getAlgebra().transpose(edgeCoefC());

        // Note. Since the sizes of the temp matrices in this calculation
        // never change, we ought to be able to reuse them.
        this.implCovar = MatrixUtils.impliedCovar(edgeCoefT, errCovar());

    }

    private double logDet(Matrix matrix2D) {
        double det = matrix2D.det();
        return FastMath.log(FastMath.abs(det));
//        return det > 0 ? FastMath.log(det) : 0;
    }

    private double traceAInvB(Matrix A, Matrix B) {

        // Note that at this point the sem and the sample covar MUST have the
        // same variables in the same order.
        Matrix inverse = A.inverse();
        Matrix product = inverse.times(B);

        double trace = product.trace();

        if (trace < -1e-8) {
            return 0;
        }

        return trace;
    }

    private Matrix edgeCoef() {
        return this.edgeCoef;
    }

    private double[] standardErrors() {
        if (this.standardErrors == null) {
            SemStdErrorEstimator estimator = new SemStdErrorEstimator();
            try {
                estimator.computeStdErrors(this);
            } catch (Exception e) {
                return null;
            }
            this.standardErrors = estimator.getStdErrors();
        }
        return this.standardErrors;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.semPm == null) {
            throw new NullPointerException();
        }

        if (this.variableNodes == null) {
            throw new NullPointerException();
        }

        if (this.measuredNodes == null) {
            throw new NullPointerException();
        }

        if (this.variableMeans == null) {
            throw new NullPointerException();
        }

        if (this.freeParameters == null) {
            throw new NullPointerException();
        }

        if (this.freeMappings == null) {
            throw new NullPointerException();
        }

        if (this.fixedParameters == null) {
            throw new NullPointerException();
        }

        if (this.fixedMappings == null) {
            throw new NullPointerException();
        }

        if (this.meanParameters == null) {
            this.meanParameters = initMeanParameters();
        }

        if (this.sampleSize < 0) {
            throw new IllegalArgumentException(
                    "Sample size out of range: " + this.sampleSize);
        }

        if (getParams() == null) {
            setParams(new Parameters());
        }

        if (this.distributions == null) {
            this.distributions = new HashMap<>();
        }
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.params;
    }

    /**
     * <p>Setter for the field <code>params</code>.</p>
     *
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * <p>Getter for the field <code>variableMeans</code>.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getVariableMeans() {
        return this.variableMeans;
    }

    /**
     * <p>isSimulatedPositiveDataOnly.</p>
     *
     * @return a boolean
     */
    public boolean isSimulatedPositiveDataOnly() {
        return false;
    }

    /**
     * <p>Getter for the field <code>implCovar</code>.</p>
     *
     * @param nodes a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix getImplCovar(List<Node> nodes) {
        computeImpliedCovar();
        // Submatrix of implied covar for listed nodes only
        int size = nodes.size();
        Matrix implCovarMeas = new Matrix(size, size);

        Map<Node, Integer> variablesHash = new HashMap<>();

        for (int k = 0; k < this.variableNodes.size(); k++) {
            variablesHash.put(this.variableNodes.get(k), k);
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Node iNode = nodes.get(i);
                Node jNode = nodes.get(j);

                int _i = variablesHash.get(iNode);
                int _j = variablesHash.get(jNode);

                implCovarMeas.set(i, j, this.implCovar.get(_i, _j));
            }
        }

        return implCovarMeas;
    }

    /**
     * <p>Getter for the field <code>numRandomCalls</code>.</p>
     *
     * @return a int
     */
    public int getNumRandomCalls() {
        return numRandomCalls;
    }

    /**
     * Calculates the total effect between two nodes.
     *
     * @param x the source node
     * @param y the target node
     * @return the total effect from node x to node y
     */
    public synchronized double getTotalEffect(Node x, Node y) {
        List<Node> parents = getSemPm().getGraph().getParents(x);

        Map<Parameter, Double> paramValues = new HashMap<>();

        for (Node parent : parents) {
            Parameter param = this.semPm.getCoefficientParameter(parent, x);
            paramValues.put(param, getParamValue(param));
            setParamValue(param, 0);
        }

        Matrix impl = getImplCovar(!paramValues.isEmpty());

        int i = variableNodes.indexOf(x);
        int j = variableNodes.indexOf(y);

        double totalEffect = impl.get(i, j);
        totalEffect /= impl.get(i, i);

        if (!paramValues.isEmpty()) {
            for (Parameter param : paramValues.keySet()) {
                setParamValue(param, paramValues.get(param));
            }

            getImplCovar(true);
        }

        return totalEffect;
    }
}
