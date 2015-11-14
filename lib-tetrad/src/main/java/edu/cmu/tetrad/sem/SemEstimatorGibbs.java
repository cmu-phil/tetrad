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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.List;

/**
 * Implements the Gibbs sampler apporach to obtain samples of arbitrary size
 * from the posterior distribution over the freeParameters of a SEM given a
 * continuous dataset and a SemPm. Point estimates, standard deviations and
 * interval estimates for the freeParameters can be computed from these samples. See
 * "Bayesian Estimation and Testing of Structural Equation Models" by Scheines,
 * Hoijtink and Boomsma, Psychometrika, v. 64, no. 1.
 *
 * @author Frank Wimberly
 */
public final class SemEstimatorGibbs {
    static final long serialVersionUID = 23L;

	/**
	 * For now, we are moving SemEstimatorGibbsParams variables into this
	 * class for easier testing
	 */

	private double[][] sampleCovars; // why is this never used?
	private int numIterations;
	private double stretch1;
	private double stretch2;
	private double tolerance;
	private double priorVariance;

	/**
     * The SemPm containing the graph and the freeParameters to be estimated.
     *
     * @serial Cannot be null.
     */
    private SemPm semPm;

    /**
     * The freeParameters of the SEM (i.e. edge coeffs, error cov, etc.
     */

    private double[] parameterMeans;
    private ParamConstraint[] paramConstraints;

    /**
     * An instance containing the freeParameters of this run.  These will eventually
     * be specified by the user via the GUI.
     */
    //private SemEstimatorGibbsParams params;

    /**
     * The initial semIm, obtained via params.
     */
    private SemIm startIm;

    private TetradMatrix priorCov;

    //private TetradMatrix sampleCovar;

    /**
     * The most recently estimated model, or null if no model has been estimated
     * yet.
     *
     * @serial Can be null.
     */
    private SemIm estimatedSem;

    private boolean flatPrior;

    private TetradMatrix dataSet;

    //=============================CONSTRUCTORS============================//

    /**
     * @return a new SemEstimator for the given SemPm and continuous data set.
     * (Uses a default optimizer.)
     *
     * @param semPm  		a SemPm specifying the graph and parameterization for the
     *             			model.
	 * @param startIm		SemIm
	 * @param sampleCovars	sample covariance matrix
	 * @param flatPrior		whether or not the prior is informative
	 * @param stretch		scaling for the variance
	 * @param numIterations	number of times to iterate sampler
	 *
     */

	// using different constructor for now
	//public SemEstimatorGibbs(SemPm semPm, SemEstimatorGibbsParams params) {
    //	this.dataSet = dataSet;
    //	this.semPm = semPm;
    //	this.params = params;
    //}

	public SemEstimatorGibbs(SemPm semPm, SemIm startIm, double[][] sampleCovars, boolean flatPrior, double stretch, int numIterations) {
		this.sampleCovars = sampleCovars;
    	this.semPm = semPm;
        this.startIm = startIm;
        this.flatPrior = flatPrior;
        this.stretch1 = stretch;
		this.stretch2 = 1.0;
		this.numIterations = numIterations;
	    this.tolerance = 0.0001;
		this.priorVariance = 16;
	}

	/**
     * Generates a simple exemplar of this class to test serialization.
     */
//    public static SemEstimatorGibbs serializableInstance() {
 //       return new SemEstimatorGibbs(null, null);
  //  }

    //==============================PUBLIC METHODS=========================//

    /**
     * Runs the estimator on the data and SemPm passed in through the
     * constructor.
     */
    public void estimate() {  //dogibbs in pascal

        //In the comments, getgibsprefs, PRIORINIT, GIBBSINIT, FORMAPPROXDIST,
        //DRAWFROMAPPROX refer to procedure in the Pascal version from which
        //this was adapted.  The same is true of the private methods such
        //as brent, neglogpost, etc.

		// Initialize method variables

		boolean lrtest = false; // likelihood ratio test

        List<Parameter> parameters = this.semPm.getParameters();                                     // semPm from constructor

		int numParameters = parameters.size();
		double[][] parameterCovariances = new double[numParameters][numParameters];
        this.parameterMeans = new double[numParameters];
        this.paramConstraints = new ParamConstraint[numParameters];

        TetradMatrix data = new TetradMatrix(parameters.size(), numIterations / 50);

		//PRIORINIT
        if (flatPrior) {
            // this is used to construct the prior covariance matrix, means
            for (int i = 0; i < numParameters; i++) {
                Parameter param = parameters.get(i);

				this.parameterMeans[i] = (param.isFixed())
						? 0.0
						: this.priorVariance ;

                //Default parameter constraints.  The user should have the
                // option to change these via the GUI

                paramConstraints[i] = (param.getType() == ParamType.VAR) // ParamType.VAR = 'Error Variance'
                    ? new ParamConstraint(this.startIm, param, ParamConstraintType.GT, 0.0)
					: new ParamConstraint(this.startIm, param, ParamConstraintType.NONE, 0.0);

                for (int j = 0; j < numParameters; j++) {
                    parameterCovariances[i][j] = (i == j && !param.isFixed())
						? this.priorVariance
                    	: 0.0;
                }
            }

            this.priorCov = new TetradMatrix(parameterCovariances);

        } else {
            //Informative prior
            // hence, multivariate normal truncated below zero for variance params

            // mean in the prior is used as starting value... q^0 = m_0

            if (!lrtest) {
                // need something in here to ask for prior variance between 0 and 500
            }

            System.out.println("Informative Prior. Exiting.");
            return;
        }
        //END PRIORINIT

        //GIBBSINIT
        TetradMatrix impliedCovMatrix = this.startIm.getImplCovar(true); // why is this never used?
        //GIBBSINIT DONE


        SemIm posteriorIm = new SemIm(this.startIm);

        List postFreeParams = posteriorIm.getFreeParameters();

		System.out.println("entering main loop");

		for (int iter = 1; iter <= this.numIterations; iter++) {
            System.out.println(iter);

            PARAMETERS:
            for (int param = 0; param < postFreeParams.size(); param++) {

                Parameter p = parameters.get(param);
                ParamConstraint constraint = this.paramConstraints[param];

                if (!p.isFixed()) {
                    //FORMAPPROXDIST begin
                    double number = (constraint.getParam2() == null)
						? constraint.getNumber()
						: this.startIm.getParamValue(constraint.getParam2());

                    double ax, bx, cx;

                    // Mark - these constraints follow pascal code
					if (constraint.getType() == ParamConstraintType.NONE) {
						ax = -500.0;
						bx = 0.0;
						cx = 500.0;
					} else if (constraint.getType() == ParamConstraintType.GT) {
						ax = number;
						cx = number + 500.0;
						bx = (ax + cx) / 2.0;
					} else if (constraint.getType() == ParamConstraintType.LT) {
						cx = number;
						ax = number - 500.0;
						bx = (ax + cx) / 2.0;
					} else if (constraint.getType() == ParamConstraintType.EQ) {
						bx = number;
						ax = number - 500.0;
						cx = number + 500.0;
					} else {
						ax = -500.0;
						bx = 0.0;
						cx = 500.0;
					}

                    double[] mean = new double[1];
                    // dmean is the density at the mean
                    double dmean = -brent(param, ax, bx, cx, this.tolerance, mean, parameters);
                    double gap = 0.005;
                    double denom;

					do {
						gap = 2.0 * gap;

                        int gapThreshold = 1;
                        double minDenom = 0.01;

                        if (gap > gapThreshold) {
                            denom = minDenom;
                            break;
                        }

                        System.out.println(p.getNodeA() + " " + p.getNodeA().getNodeType());
                        System.out.println(p.getNodeB() + " " + p.getNodeB().getNodeType());
						double dmeanplus = neglogpost(param, mean[0] + gap, parameters);
						denom = dmean + dmeanplus;

                        if (denom < minDenom) denom = minDenom;

//						System.out.println("gap = "+gap+"; denom = "+denom+"; dmean = "+dmean+"; dmeanplus = "+dmeanplus);
					} while (denom < 0.0);

                    double vr = ( this.stretch1 * 0.5 * gap * gap ) / denom;

					//System.out.println("vr = "+vr+" param = "+param);

					//FORMAPPROXDIST end

                    //DRAWFROMAPPROX begin
                    boolean realdraw = false;
                    double rj = 0.0, accept = 0.0, cand = 0.0;

					while (!realdraw || rj <= accept) {
                        cand = mean[0] + Math.max(RandomUtil.getInstance().nextNormal(0, 1) * Math.sqrt(vr),0);
                        realdraw = (constraint.wouldBeSatisfied(cand));
                        if (realdraw) {

//							System.out.println("dcand start");

							double dcand = -1.0 * neglogpost(param, cand, parameters);
//							System.out.println("dcand end");
                            double numer = dcand - dmean;
                            double denom1 = (-1.0 * Math.sqrt(cand - mean[0]) /
                                    (2.0 * vr)) - Math.log(this.stretch2);
                            rj = numer - denom1;
                            accept = Math.log(RandomUtil.getInstance().nextDouble());

                            int rejectionThreshold = 5;

                            if (rj > rejectionThreshold) {
								//System.out.println("rj = "+rj);
								rj = rejectionThreshold;
							}
						}
                    }
                    //DRAWFROMAPPROX end

					//System.out.println("end of iteration");

					//UPDATEPARM
					Parameter ppost = (Parameter) postFreeParams.get(param);
					if (ppost.isFixed())
						posteriorIm.setFixedParamValue(ppost, cand);
					else
						posteriorIm.setParamValue(ppost, cand);
					//UPDATEPARM end
                }

            }

            int subsampleStride = 50;

            if (iter % subsampleStride == 0 && iter > 0) {
                for (int i = 0; i < numParameters; i++) {
                    Parameter ppost = (posteriorIm.getSemPm()).getParameters().get(i);
                    data.set(i, iter/subsampleStride -1, posteriorIm.getParamValue(ppost));
                }
            }
        }

        dataSet = data;
		this.estimatedSem = posteriorIm;
		//setMeans(posteriorIm, data);

	}

    private double brent(int param, double ax, double bx, double cx, double tol, double[] xmin, List<Parameter> parameters) {

		int ITMAX = 100, iter;
        double CGOLD = 0.3819660;
        double ZEPS = 1.0e-10;
        double a, b, d, e, etemp, p, q, r, tol1,tol2, u, v, w, x, xm, fu, fv, fw, fx;

        //init
        x = w = v = bx;
        e = d = 0.0;
        a = (ax < cx) ? ax : cx;
        b = (ax > cx) ? ax : cx;
        fw = fv = fx = neglogpost(param, x, parameters);

        for (iter = 1; iter <= ITMAX; iter++) {
            xm = 0.5 * (a + b);
            tol1 = tol * Math.abs(x) + ZEPS;
            tol2 = 2.0 * tol1;

            if (Math.abs(x - xm) <= tol2 - 0.5 * (b - a)) {
                xmin[0] = x;
                return fx;
            }

            if (Math.abs(e) > tol1) {
                r = (x - w) * (fx - fv);
                q = (x - v) * (fx - fw);
                p = (x - v) * q - (x - w) * r;
                q = 2.0 * (q - r);

                if (q > 0.0) p = -p;

                q = Math.abs(q);
                etemp = e;
                e = d;

                if ((Math.abs(p) >= Math.abs(0.5 * q * etemp)) ||
                        (p <= q * (a - x)) || (p >= q * (b - x))) {
                    e = (x >= xm) ? a - x : b - x;
                    d = CGOLD * e;
                } else {
                    d = p / q;
                    u = x + d;
                    if ((u - a) < tol2 || (b - u) < tol2)
						d = (xm - x >= 0.0) ? Math.abs(tol1) : -Math.abs(tol1);
                }
            } else {
                e = (x >= xm) ? a - x : b - x;
                d = CGOLD * e;
            }

            double s = (tol1 > -0.0) ? Math.abs(d) : -Math.abs(d);
            u = (Math.abs(d) >= tol1) ? x + d : x + s;
            fu = neglogpost(param, u, parameters);
            if (fu <= fx) {
                if (u >= x)	a = x;
                else 		b = x;

                v = w;
                fv = fw;
                w = x;
                fw = fx;
                x = u;
                fx = fu;
            } else {
                if (u < x) a = u;
                else 	   b = u;

                if (fu <= fw || w == x) {
                    v = w;
                    fv = fw;
                    w = u;
                    fw = fu;
                } else if (fu <= fv || v == x || v == w) {
                    v = u;
                    fv = fu;
                }
            }
        }

        xmin[0] = x;
        return fx;

    }

    private double neglogpost(int param, double x, List<Parameter> parameters) {
        double a = negloglike(param, x);
        double b = 0.0;

		// this is never called since flatprior is never false
        if (!this.flatPrior) b = neglogprior(param, x, parameters);

        return a + b;
    }

    private double negloglike(int param, double x) {
        // Mark - I'm not entirely sure about this method

        Parameter p = this.semPm.getParameters().get(param);

		double tparm = this.startIm.getParamValue(p);

//		System.out.println(tparm);

        if ((p.getType() == ParamType.VAR || p.getType() == ParamType.COEF) && paramConstraints[param].wouldBeSatisfied(x)) {
			this.startIm.setParamValue(p, x);
        }


		double nll = -this.startIm.getTruncLL();

        this.startIm.setParamValue(p, tparm);

        return nll;

    }

    private double negchi2(int param, double x, List<Parameter> parameters) {
        // Mark - I modified some code in here that I thought to be inaccurate based on pascal code
        // this is only called when flatprior is false, which it will never be with the getModel code

		double answer = 0.0;
		int n = 0;
        int numParameters = parameters.size();
        double[] xvec = new double[numParameters];
        double[] temp = new double[numParameters];

        for (int i = 0; i < numParameters; i++) {
            Parameter p = parameters.get(i);

            if (p.isFixed()) continue;

			xvec[n] = (i == param)
					? x - parameterMeans[i]
					: this.startIm.getParamValue(p) - parameterMeans[i];
        }

        TetradMatrix invPrior = priorCov.inverse();

        for (int i = 0; i < n; i++) temp[i] = 0.0;
        for (int col = 0; col < n; col++) {
            for (int k = 0; k < n; k++) {
                temp[col] = temp[col] + (xvec[k] * invPrior.get(k, col));
            }
        }

        for (int k = 0; k < n; k++) {
            answer += temp[k] * xvec[k];
        }

        return -answer;
    }

    private double neglogprior(int param, double x, List<Parameter> parameters) {
        return -negchi2(param, x, parameters) / 2.0;
    }

    /**
     * @return the estimated SemIm. If the <code>estimate</code> method has not
     * yet been called, <code>null</code> is returned.
	 *
	 * @return SemIm
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

        if (this.getEstimatedSem() == null) {
            buf.append("\n\t...SemIm has not been estimated yet.");
        } else {
            SemIm sem = this.getEstimatedSem();
            buf.append("\n\n\tfml = ");

            buf.append("\n\n\tnegtruncll = ");
            buf.append(nf.format(-sem.getTruncLL()));

            buf.append("\n\n\tmeasuredNodes:\n\t");
            buf.append(sem.getMeasuredNodes());

            buf.append("\n\n\tedgeCoef:\n");
            buf.append(MatrixUtils.toString(sem.getEdgeCoef().toArray()));

            buf.append("\n\n\terrCovar:\n");
            buf.append(MatrixUtils.toString(sem.getErrCovar().toArray()));
        }

        return buf.toString();
    }


    private DataSet subset(DataSet dataSet, SemPm semPm) {

        String[] measuredVarNames = semPm.getMeasuredVarNames();
        int[] varIndices = new int[measuredVarNames.length];

        for (int i = 0; i < measuredVarNames.length; i++) {
            Node variable = dataSet.getVariable(measuredVarNames[i]);
            varIndices[i] = dataSet.getVariables().indexOf(variable);
        }

        return dataSet.subsetColumns(varIndices);
    }


    /**
     * Sets the means of variables in the SEM IM based on the given data set.
	 *
	 * @param semIm		SemIm
	 * @param dataSet	The data produced by iterating the sampler
     */
    private void setMeans(SemIm semIm, TetradMatrix dataSet) {

		double[] means = new double[semIm.getSemPm().getVariableNodes().size()];
        int numMeans = means.length;

        if (dataSet == null) {
            for (int i = 0; i < numMeans; i++) {
                means[i] = 0.0;
            }
        } else {
            double[] sum = new double[numMeans];

            for (int j = 0; j < dataSet.columns(); j++) {
                for (int i = 0; i < dataSet.rows(); i++) {
                    sum[j] += dataSet.get(i, j);
                }

                means[j] = sum[j] / dataSet.rows();
            }
        }

        for (int i = 0; i < semIm.getVariableNodes().size(); i++) {
            Node node = semIm.getVariableNodes().get(i);
            semIm.setMean(node, means[i]);
        }
    }

    public void setEstimatedSem(SemIm estimatedSem) {
        this.estimatedSem = estimatedSem;
    }

    public SemPm getSemPm() {
        return semPm;
    }

    public TetradMatrix getDataSet() {
        return dataSet;
    }


    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

//        if (semPm == null) {
//            throw new NullPointerException();
//        }

//        if (dataSet == null) {
//            throw new NullPointerException();
//        }
    }
}



