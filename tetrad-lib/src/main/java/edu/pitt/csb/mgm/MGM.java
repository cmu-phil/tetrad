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

package edu.pitt.csb.mgm;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphConverter;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.StatUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

//import cern.colt.Arrays;
//import la.matrix.Matrix;
//import la.matrix.DenseMatrix;
//import ml.optimization.AcceleratedProximalGradient;
//import ml.optimization.ProximalMapping;
//import ml.utils.Matlab;

/**
 * Implementation of Lee and Hastie's (2012) pseudolikelihood method for learning
 * Mixed Gaussian-Categorical Graphical Models
 * Created by ajsedgewick on 7/15/15.
 */
public class MGM extends ConvexProximal implements GraphSearch {
    private final DoubleFactory2D factory2D = DoubleFactory2D.dense;
    private final DoubleFactory1D factory1D = DoubleFactory1D.dense;

    //private DoubleFactory2D factory2D = DoubleFactory2D.sparse;
    //private DoubleFactory1D factory1D = DoubleFactory1D.sparse;

    //Continuous Data
    private final DoubleMatrix2D xDat;

    //Discrete Data coded as integers, no IntMatrix2D apparently...
    private final DoubleMatrix2D yDat;

    private List<Node> variables;
    private List<Node> initVariables = null;

    //Discrete Data coded as dummy variables
    private DoubleMatrix2D dDat;


    private final DoubleMatrix1D lambda;
    private final Algebra alg = new Algebra();

    private long elapsedTime = 0;

    //Levels of Discrete variables
    private final int[] l;
    private int lsum;
    private int[] lcumsum;
    int p;
    int q;
    int n;

    //parameter weights
    private DoubleMatrix1D weights;

    public MGM(final DoubleMatrix2D x, final DoubleMatrix2D y, final List<Node> variables, final int[] l, final double[] lambda) {

        if (l.length != y.columns())
            throw new IllegalArgumentException("length of l doesn't match number of variables in Y");

        if (y.rows() != x.rows())
            throw new IllegalArgumentException("different number of samples for x and y");

        //lambda should have 3 values corresponding to cc, cd, and dd
        if (lambda.length != 3)
            throw new IllegalArgumentException("Lambda should have three values for cc, cd, and dd edges respectively");


        this.xDat = x;
        this.yDat = y;
        this.l = l;
        this.p = x.columns();
        this.q = y.columns();
        this.n = x.rows();
        this.variables = variables;


        this.lambda = this.factory1D.make(lambda);
        fixData();
        initParameters();
        calcWeights();
        makeDummy();
    }

    public MGM(final DataSet ds, final double[] lambda) {
        this.variables = ds.getVariables();

        // Notify the user that you need at least one continuous and one discrete variable to run MGM
        boolean hasContinuous = false;
        boolean hasDiscrete = false;

        for (final Node node : this.variables) {
            if (node instanceof ContinuousVariable) {
                hasContinuous = true;
            }

            if (node instanceof DiscreteVariable) {
                hasDiscrete = true;
            }
        }

        if (!hasContinuous || !hasDiscrete) {
            throw new IllegalArgumentException("Please give data with at least one discrete and one continuous variable to run MGM.");
        }

        final DataSet dsCont = MixedUtils.getContinousData(ds);
        final DataSet dsDisc = MixedUtils.getDiscreteData(ds);
        this.xDat = this.factory2D.make(dsCont.getDoubleData().toArray());
        this.yDat = this.factory2D.make(dsDisc.getDoubleData().toArray());
        this.l = MixedUtils.getDiscLevels(ds);
        this.p = this.xDat.columns();
        this.q = this.yDat.columns();
        this.n = this.xDat.rows();

        //the variables are now ordered continuous first then discrete
        this.variables = new ArrayList<>();
        this.variables.addAll(dsCont.getVariables());
        this.variables.addAll(dsDisc.getVariables());

        this.initVariables = ds.getVariables();

        this.lambda = this.factory1D.make(lambda);

        //Data is checked for 0 or 1 indexing and fore missing levels
        fixData();
        initParameters();
        calcWeights();
        makeDummy();
    }

    public static class MGMParams {
        //Model parameters
        private DoubleMatrix2D beta; //continuous-continuous
        private DoubleMatrix1D betad; //cont squared node pot
        private DoubleMatrix2D theta; //continuous-discrete
        private DoubleMatrix2D phi; //discrete-discrete
        private DoubleMatrix1D alpha1; //cont linear node pot
        private DoubleMatrix1D alpha2; //disc node pot

        public MGMParams() {

        }

        //nothing is copied here, all pointers back to inputs...
        public MGMParams(final DoubleMatrix2D beta, final DoubleMatrix1D betad, final DoubleMatrix2D theta,
                         final DoubleMatrix2D phi, final DoubleMatrix1D alpha1, final DoubleMatrix1D alpha2) {
            this.beta = beta;
            this.betad = betad;
            this.theta = theta;
            this.phi = phi;
            this.alpha1 = alpha1;
            this.alpha2 = alpha2;
        }

        //copy from another parameter set
        public MGMParams(final MGMParams parIn) {
            this.beta = parIn.beta.copy();
            this.betad = parIn.betad.copy();
            this.theta = parIn.theta.copy();
            this.phi = parIn.phi.copy();
            this.alpha1 = parIn.alpha1.copy();
            this.alpha2 = parIn.alpha2.copy();
        }

        //copy params from flattened vector
        public MGMParams(final DoubleMatrix1D vec, final int p, final int ltot) {
            final int[] lens = {p * p, p, p * ltot, ltot * ltot, p, ltot};
            final int[] lenSums = new int[lens.length];
            lenSums[0] = lens[0];
            for (int i = 1; i < lenSums.length; i++) {
                lenSums[i] = lens[i] + lenSums[i - 1];
            }

            if (vec.size() != lenSums[5])
                throw new IllegalArgumentException("Param vector dimension doesn't match: Found " + vec.size() + " need " + lenSums[5]);

            this.beta = DoubleFactory2D.dense.make(vec.viewPart(0, lens[0]).toArray(), p);
            this.betad = vec.viewPart(lenSums[0], lens[1]).copy();
            this.theta = DoubleFactory2D.dense.make(vec.viewPart(lenSums[1], lens[2]).toArray(), ltot);
            this.phi = DoubleFactory2D.dense.make(vec.viewPart(lenSums[2], lens[3]).toArray(), ltot);
            this.alpha1 = vec.viewPart(lenSums[3], lens[4]).copy();
            this.alpha2 = vec.viewPart(lenSums[4], lens[5]).copy();
        }

        public String toString() {
            String outStr = "alpha1: " + this.alpha1.toString();
            outStr += "\nalpha2: " + this.alpha2.toString();
            outStr += "\nbeta: " + this.beta.toString();
            outStr += "\nbetad: " + this.betad.toString();
            outStr += "\ntheta: " + this.theta.toString();
            outStr += "\nphi: " + this.phi.toString();
            return outStr;
        }

        public DoubleMatrix1D getAlpha1() {
            return this.alpha1;
        }

        public DoubleMatrix1D getAlpha2() {
            return this.alpha2;
        }

        public DoubleMatrix1D getBetad() {
            return this.betad;
        }

        public DoubleMatrix2D getBeta() {
            return this.beta;
        }

        public DoubleMatrix2D getPhi() {
            return this.phi;
        }

        public DoubleMatrix2D getTheta() {
            return this.theta;
        }

        public void setAlpha1(final DoubleMatrix1D alpha1) {
            this.alpha1 = alpha1;
        }

        public void setAlpha2(final DoubleMatrix1D alpha2) {
            this.alpha2 = alpha2;
        }

        public void setBeta(final DoubleMatrix2D beta) {
            this.beta = beta;
        }

        public void setBetad(final DoubleMatrix1D betad) {
            this.betad = betad;
        }

        public void setPhi(final DoubleMatrix2D phi) {
            this.phi = phi;
        }

        public void setTheta(final DoubleMatrix2D theta) {
            this.theta = theta;
        }

        /**
         * Copy all params into a single vector
         *
         * @return
         */
        public DoubleMatrix1D toMatrix1D() {
            final DoubleFactory1D fac = DoubleFactory1D.dense;
            final int p = this.alpha1.size();
            final int ltot = this.alpha2.size();
            final int[] lens = {p * p, p, p * ltot, ltot * ltot, p, ltot};
            final int[] lenSums = new int[lens.length];
            lenSums[0] = lens[0];
            for (int i = 1; i < lenSums.length; i++) {
                lenSums[i] = lens[i] + lenSums[i - 1];
            }

            final DoubleMatrix1D outVec = fac.make(p * p + p + p * ltot + ltot * ltot + p + ltot);
            outVec.viewPart(0, lens[0]).assign(MGM.flatten(this.beta));
            outVec.viewPart(lenSums[0], lens[1]).assign(this.betad);
            outVec.viewPart(lenSums[1], lens[2]).assign(MGM.flatten(this.theta));
            outVec.viewPart(lenSums[2], lens[3]).assign(MGM.flatten(this.phi));
            outVec.viewPart(lenSums[3], lens[4]).assign(this.alpha1);
            outVec.viewPart(lenSums[4], lens[5]).assign(this.alpha2);

            return outVec;
        }

        //likely depreciated
        public double[][] toVector() {
            final double[][] outArr = new double[1][];
            outArr[0] = toMatrix1D().toArray();
            return outArr;
        }
    }

    private MGMParams params;

    public void setParams(final MGMParams newParams) {
        this.params = newParams;
    }

    //create column major vector from matrix (i.e. concatenate columns)
    public static DoubleMatrix1D flatten(final DoubleMatrix2D m) {
        final DoubleMatrix1D[] colArray = new DoubleMatrix1D[m.columns()];
        for (int i = 0; i < m.columns(); i++) {
            colArray[i] = m.viewColumn(i);
        }

        return DoubleFactory1D.dense.make(colArray);
    }

    //init all parameters to zeros except for betad which is set to 1s
    private void initParameters() {
        this.lcumsum = new int[this.l.length + 1];
        this.lcumsum[0] = 0;
        for (int i = 0; i < this.l.length; i++) {
            this.lcumsum[i + 1] = this.lcumsum[i] + this.l[i];
        }
        this.lsum = this.lcumsum[this.l.length];

        //LH init to zeros, maybe should be random init?
        final DoubleMatrix2D beta = this.factory2D.make(this.xDat.columns(), this.xDat.columns()); //continuous-continuous
        final DoubleMatrix1D betad = this.factory1D.make(this.xDat.columns(), 1.0); //cont squared node pot
        final DoubleMatrix2D theta = this.factory2D.make(this.lsum, this.xDat.columns());
        //continuous-discrete
        final DoubleMatrix2D phi = this.factory2D.make(this.lsum, this.lsum); //discrete-discrete
        final DoubleMatrix1D alpha1 = this.factory1D.make(this.xDat.columns()); //cont linear node pot
        final DoubleMatrix1D alpha2 = this.factory1D.make(this.lsum); //disc node potbeta =
        this.params = new MGMParams(beta, betad, theta, phi, alpha1, alpha2);

        //separate lambda for each type of edge, [cc, cd, dd]
        //lambda = factory1D.make(3);
    }

    // avoid underflow in log(sum(exp(x))) calculation
    private double logsumexp(final DoubleMatrix1D x) {
        final DoubleMatrix1D myX = x.copy();
        final double maxX = StatUtils.max(myX.toArray());
        return Math.log(myX.assign(Functions.minus(maxX)).assign(Functions.exp).zSum()) + maxX;
    }

    //calculate parameter weights as in Lee and Hastie
    private void calcWeights() {
        this.weights = this.factory1D.make(this.p + this.q);
        for (int i = 0; i < this.p; i++) {
            this.weights.set(i, StatUtils.sd(this.xDat.viewColumn(i).toArray()));
        }
        for (int j = 0; j < this.q; j++) {
            double curWeight = 0;
            for (int k = 0; k < this.l[j]; k++) {
                final double curp = this.yDat.viewColumn(j).copy().assign(Functions.equals(k + 1)).zSum() / (double) this.n;
                curWeight += curp * (1 - curp);
            }
            this.weights.set(this.p + j, Math.sqrt(curWeight));
        }
    }

    /**
     * Convert discrete data (in yDat) to a matrix of dummy variables (stored in dDat)
     */
    private void makeDummy() {
        this.dDat = this.factory2D.make(this.n, this.lsum);
        for (int i = 0; i < this.q; i++) {
            for (int j = 0; j < this.l[i]; j++) {
                final DoubleMatrix1D curCol = this.yDat.viewColumn(i).copy().assign(Functions.equals(j + 1));
                if (curCol.zSum() == 0)
                    throw new IllegalArgumentException("Discrete data is missing a level: variable " + i + " level " + j);
                this.dDat.viewColumn(this.lcumsum[i] + j).assign(curCol);
            }
        }
    }

    /**
     * checks if yDat is zero indexed and converts to 1 index. zscores x
     */
    private void fixData() {
        final double ymin = StatUtils.min(MGM.flatten(this.yDat).toArray());
        if (ymin < 0 || ymin > 1)
            throw new IllegalArgumentException("Discrete data must be either zero or one indexed. Found min index: " + ymin);

        if (ymin == 0) {
            this.yDat.assign(Functions.plus(1.0));
        }


        //z-score columns of X
        for (int i = 0; i < this.p; i++) {
            this.xDat.viewColumn(i).assign(StatUtils.standardizeData(this.xDat.viewColumn(i).toArray()));
        }
    }

    /**
     * non-penalized -log(pseudolikelihood) this is the smooth function g(x) in prox gradient
     *
     * @param parIn
     * @return
     */
    public double smoothValue(final DoubleMatrix1D parIn) {
        //work with copy
        final MGMParams par = new MGMParams(parIn, this.p, this.lsum);

        for (int i = 0; i < par.betad.size(); i++) {
            if (par.betad.get(i) < 0)
                return Double.POSITIVE_INFINITY;
        }
        //double nll = 0;
        //int n = xDat.rows();
        //beta=beta+beta';
        //phi=phi+phi';
        MGM.upperTri(par.beta, 1);
        par.beta.assign(this.alg.transpose(par.beta), Functions.plus);

        for (int i = 0; i < this.q; i++) {
            par.phi.viewPart(this.lcumsum[i], this.lcumsum[i], this.l[i], this.l[i]).assign(0);
        }
        // ensure mats are upper triangular
        MGM.upperTri(par.phi, 0);
        par.phi.assign(this.alg.transpose(par.phi), Functions.plus);


        //Xbeta=X*beta*diag(1./betad);
        final DoubleMatrix2D divBetaD = this.factory2D.diagonal(this.factory1D.make(this.p, 1.0).assign(par.betad, Functions.div));
        final DoubleMatrix2D xBeta = this.alg.mult(this.xDat, this.alg.mult(par.beta, divBetaD));

        //Dtheta=D*theta*diag(1./betad);
        final DoubleMatrix2D dTheta = this.alg.mult(this.alg.mult(this.dDat, par.theta), divBetaD);

        // Squared loss
        //sqloss=-n/2*sum(log(betad))+...
        //.5*norm((X-e*alpha1'-Xbeta-Dtheta)*diag(sqrt(betad)),'fro')^2;
        final DoubleMatrix2D tempLoss = this.factory2D.make(this.n, this.xDat.columns());

        //wxprod=X*(theta')+D*phi+e*alpha2';
        final DoubleMatrix2D wxProd = this.alg.mult(this.xDat, this.alg.transpose(par.theta));
        wxProd.assign(this.alg.mult(this.dDat, par.phi), Functions.plus);
        for (int i = 0; i < this.n; i++) {
            for (int j = 0; j < this.xDat.columns(); j++) {
                tempLoss.set(i, j, this.xDat.get(i, j) - par.alpha1.get(j) - xBeta.get(i, j) - dTheta.get(i, j));
            }
            for (int j = 0; j < this.dDat.columns(); j++) {
                wxProd.set(i, j, wxProd.get(i, j) + par.alpha2.get(j));
            }
        }

        final double sqloss = -this.n / 2.0 * par.betad.copy().assign(Functions.log).zSum() +
                .5 * Math.pow(this.alg.normF(this.alg.mult(tempLoss, this.factory2D.diagonal(par.betad.copy().assign(Functions.sqrt)))), 2);


        // categorical loss
        /*catloss=0;
        wxprod=X*(theta')+D*phi+e*alpha2'; %this is n by Ltot
        for r=1:q
            wxtemp=wxprod(:,Lsum(r)+1:Lsum(r)+L(r));
            denom= logsumexp(wxtemp,2); %this is n by 1
            catloss=catloss-sum(wxtemp(sub2ind([n L(r)],(1:n)',Y(:,r))));
            catloss=catloss+sum(denom);
        end
        */

        double catloss = 0;
        for (int i = 0; i < this.yDat.columns(); i++) {
            final DoubleMatrix2D wxTemp = wxProd.viewPart(0, this.lcumsum[i], this.n, this.l[i]);
            for (int k = 0; k < this.n; k++) {
                final DoubleMatrix1D curRow = wxTemp.viewRow(k);

                catloss -= curRow.get((int) this.yDat.get(k, i) - 1);
                catloss += logsumexp(curRow);
            }
        }

        return (sqloss + catloss) / ((double) this.n);
    }

    /**
     * non-penalized -log(pseudolikelihood) this is the smooth function g(x) in prox gradient
     * this overloaded version calculates both nll and the smooth gradient at the same time
     * any value in gradOut will be replaced by the new calculations
     *
     * @param parIn
     * @param gradOutVec
     * @return
     */
    public double smooth(final DoubleMatrix1D parIn, final DoubleMatrix1D gradOutVec) {
        //work with copy
        final MGMParams par = new MGMParams(parIn, this.p, this.lsum);
        final MGMParams gradOut = new MGMParams();

        for (int i = 0; i < par.betad.size(); i++) {
            if (par.betad.get(i) < 0)
                return Double.POSITIVE_INFINITY;
        }

        //beta=beta-diag(diag(beta));
        //for r=1:q
        //  phi(Lsum(r)+1:Lsum(r+1),Lsum(r)+1:Lsum(r+1))=0;
        //end
        //beta=triu(beta); phi=triu(phi);
        //beta=beta+beta';
        //phi=phi+phi';
        MGM.upperTri(par.beta, 1);
        par.beta.assign(this.alg.transpose(par.beta), Functions.plus);

        for (int i = 0; i < this.q; i++) {
            par.phi.viewPart(this.lcumsum[i], this.lcumsum[i], this.l[i], this.l[i]).assign(0);
        }
        //ensure matrix is upper triangular
        MGM.upperTri(par.phi, 0);
        par.phi.assign(this.alg.transpose(par.phi), Functions.plus);

        //Xbeta=X*beta*diag(1./betad);
        final DoubleMatrix2D divBetaD = this.factory2D.diagonal(this.factory1D.make(this.p, 1.0).assign(par.betad, Functions.div));
        final DoubleMatrix2D xBeta = this.alg.mult(this.xDat, this.alg.mult(par.beta, divBetaD));

        //Dtheta=D*theta*diag(1./betad);
        final DoubleMatrix2D dTheta = this.alg.mult(this.alg.mult(this.dDat, par.theta), divBetaD);

        // Squared loss
        //tempLoss =  (X-e*alpha1'-Xbeta-Dtheta) = -res (in gradient code)
        final DoubleMatrix2D tempLoss = this.factory2D.make(this.n, this.xDat.columns());

        //wxprod=X*(theta')+D*phi+e*alpha2';
        final DoubleMatrix2D wxProd = this.alg.mult(this.xDat, this.alg.transpose(par.theta));
        wxProd.assign(this.alg.mult(this.dDat, par.phi), Functions.plus);
        for (int i = 0; i < this.n; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.xDat.columns(); j++) {
                tempLoss.set(i, j, this.xDat.get(i, j) - par.alpha1.get(j) - xBeta.get(i, j) - dTheta.get(i, j));
            }
            for (int j = 0; j < this.dDat.columns(); j++) {
                wxProd.set(i, j, wxProd.get(i, j) + par.alpha2.get(j));
            }
        }

        //sqloss=-n/2*sum(log(betad))+...
        //.5*norm((X-e*alpha1'-Xbeta-Dtheta)*diag(sqrt(betad)),'fro')^2;
        final double sqloss = -this.n / 2.0 * par.betad.copy().assign(Functions.log).zSum() +
                .5 * Math.pow(this.alg.normF(this.alg.mult(tempLoss, this.factory2D.diagonal(par.betad.copy().assign(Functions.sqrt)))), 2);

        //ok now tempLoss = res
        tempLoss.assign(Functions.mult(-1));

        //gradbeta=X'*(res);
        gradOut.beta = this.alg.mult(this.alg.transpose(this.xDat), tempLoss);

        //gradbeta=gradbeta-diag(diag(gradbeta)); % zero out diag
        //gradbeta=tril(gradbeta)'+triu(gradbeta);
        final DoubleMatrix2D lowerBeta = this.alg.transpose(MGM.lowerTri(gradOut.beta.copy(), -1));
        MGM.upperTri(gradOut.beta, 1).assign(lowerBeta, Functions.plus);

        //gradalpha1=diag(betad)*sum(res,1)';
        gradOut.alpha1 = this.alg.mult(this.factory2D.diagonal(par.betad), MGM.margSum(tempLoss, 1));

        //gradtheta=D'*(res);
        gradOut.theta = this.alg.mult(this.alg.transpose(this.dDat), tempLoss);

        // categorical loss
        /*catloss=0;
        wxprod=X*(theta')+D*phi+e*alpha2'; %this is n by Ltot
        for r=1:q
            wxtemp=wxprod(:,Lsum(r)+1:Lsum(r)+L(r));
            denom= logsumexp(wxtemp,2); %this is n by 1
            catloss=catloss-sum(wxtemp(sub2ind([n L(r)],(1:n)',Y(:,r))));
            catloss=catloss+sum(denom);
        end
        */

        double catloss = 0;
        for (int i = 0; i < this.yDat.columns(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final DoubleMatrix2D wxTemp = wxProd.viewPart(0, this.lcumsum[i], this.n, this.l[i]);
            //need to copy init values for calculating nll
            final DoubleMatrix2D wxTemp0 = wxTemp.copy();

            // does this need to be done in log space??
            wxTemp.assign(Functions.exp);
            final DoubleMatrix1D invDenom = this.factory1D.make(this.n, 1.0).assign(MGM.margSum(wxTemp, 2), Functions.div);
            wxTemp.assign(this.alg.mult(this.factory2D.diagonal(invDenom), wxTemp));
            for (int k = 0; k < this.n; k++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final DoubleMatrix1D curRow = wxTemp.viewRow(k);
                final DoubleMatrix1D curRow0 = wxTemp0.viewRow(k);

                catloss -= curRow0.get((int) this.yDat.get(k, i) - 1);
                catloss += logsumexp(curRow0);


                //wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))=wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))-1;
                curRow.set((int) this.yDat.get(k, i) - 1, curRow.get((int) this.yDat.get(k, i) - 1) - 1);
            }
        }

        //gradalpha2=sum(wxprod,1)';
        gradOut.alpha2 = MGM.margSum(wxProd, 1);

        //gradw=X'*wxprod;
        final DoubleMatrix2D gradW = this.alg.mult(this.alg.transpose(this.xDat), wxProd);

        //gradtheta=gradtheta+gradw';
        gradOut.theta.assign(this.alg.transpose(gradW), Functions.plus);

        //gradphi=D'*wxprod;
        gradOut.phi = this.alg.mult(this.alg.transpose(this.dDat), wxProd);

        //zero out gradphi diagonal
        //for r=1:q
        //gradphi(Lsum(r)+1:Lsum(r+1),Lsum(r)+1:Lsum(r+1))=0;
        //end
        for (int i = 0; i < this.q; i++) {
            gradOut.phi.viewPart(this.lcumsum[i], this.lcumsum[i], this.l[i], this.l[i]).assign(0);
        }

        //gradphi=tril(gradphi)'+triu(gradphi);
        final DoubleMatrix2D lowerPhi = this.alg.transpose(MGM.lowerTri(gradOut.phi.copy(), 0));
        MGM.upperTri(gradOut.phi, 0).assign(lowerPhi, Functions.plus);

        /*
        for s=1:p
            gradbetad(s)=-n/(2*betad(s))+1/2*norm(res(:,s))^2-res(:,s)'*(Xbeta(:,s)+Dtheta(:,s));
        end
         */
        gradOut.betad = this.factory1D.make(this.xDat.columns());
        for (int i = 0; i < this.p; i++) {
            gradOut.betad.set(i, -this.n / (2.0 * par.betad.get(i)) + this.alg.norm2(tempLoss.viewColumn(i)) / 2.0 -
                    this.alg.mult(tempLoss.viewColumn(i), xBeta.viewColumn(i).copy().assign(dTheta.viewColumn(i), Functions.plus)));
        }

        gradOut.alpha1.assign(Functions.div((double) this.n));
        gradOut.alpha2.assign(Functions.div((double) this.n));
        gradOut.betad.assign(Functions.div((double) this.n));
        gradOut.beta.assign(Functions.div((double) this.n));
        gradOut.theta.assign(Functions.div((double) this.n));
        gradOut.phi.assign(Functions.div((double) this.n));

        gradOutVec.assign(gradOut.toMatrix1D());
        return (sqloss + catloss) / ((double) this.n);
    }

    /**
     * Calculates penalty term of objective function
     *
     * @param parIn
     * @return
     */
    public double nonSmoothValue(final DoubleMatrix1D parIn) {
        //DoubleMatrix1D tlam = lambda.copy().assign(Functions.mult(t));
        //Dimension checked in constructor
        //par is a copy so we can update it
        final MGMParams par = new MGMParams(parIn, this.p, this.lsum);

        //penbeta = t(1).*(wv(1:p)'*wv(1:p));
        //betascale=zeros(size(beta));
        //betascale=max(0,1-penbeta./abs(beta));
        final DoubleMatrix2D weightMat = this.alg.multOuter(this.weights,
                this.weights, null);

        //int p = xDat.columns();

        //weight beta
        //betaw = (wv(1:p)'*wv(1:p)).*abs(beta);
        //betanorms=sum(betaw(:));
        final DoubleMatrix2D betaWeight = weightMat.viewPart(0, 0, this.p, this.p);
        final DoubleMatrix2D absBeta = par.beta.copy().assign(Functions.abs);
        final double betaNorms = absBeta.assign(betaWeight, Functions.mult).zSum();


        /*
        thetanorms=0;
        for s=1:p
            for j=1:q
                tempvec=theta(Lsums(j)+1:Lsums(j+1),s);
                thetanorms=thetanorms+(wv(s)*wv(p+j))*norm(tempvec);
            end
        end
        */
        double thetaNorms = 0;
        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.lcumsum.length - 1; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final DoubleMatrix1D tempVec = par.theta.viewColumn(i).viewPart(this.lcumsum[j], this.l[j]);
                thetaNorms += weightMat.get(i, this.p + j) * Math.sqrt(this.alg.norm2(tempVec));
            }
        }

        /*
        for r=1:q
            for j=1:q
                if r<j
                    tempmat=phi(Lsums(r)+1:Lsums(r+1),Lsums(j)+1:Lsums(j+1));
                    tempmat=max(0,1-t(3)*(wv(p+r)*wv(p+j))/norm(tempmat))*tempmat; % Lj by 2*Lr
                    phinorms=phinorms+(wv(p+r)*wv(p+j))*norm(tempmat,'fro');
                    phi( Lsums(r)+1:Lsums(r+1),Lsums(j)+1:Lsums(j+1) )=tempmat;
                end
            end
        end
         */
        double phiNorms = 0;
        for (int i = 0; i < this.lcumsum.length - 1; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = i + 1; j < this.lcumsum.length - 1; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final DoubleMatrix2D tempMat = par.phi.viewPart(this.lcumsum[i], this.lcumsum[j], this.l[i], this.l[j]);
                phiNorms += weightMat.get(this.p + i, this.p + j) * this.alg.normF(tempMat);
            }
        }

        return this.lambda.get(0) * betaNorms + this.lambda.get(1) * thetaNorms + this.lambda.get(2) * phiNorms;
    }


    /**
     * Gradient of the pseudolikelihood
     *
     * @param parIn
     * @return
     */
    public DoubleMatrix1D smoothGradient(final DoubleMatrix1D parIn) {
        final int n = this.xDat.rows();
        final MGMParams grad = new MGMParams();

        //
        final MGMParams par = new MGMParams(parIn, this.p, this.lsum);
        MGM.upperTri(par.beta, 1);
        par.beta.assign(this.alg.transpose(par.beta), Functions.plus);

        for (int i = 0; i < this.q; i++) {
            par.phi.viewPart(this.lcumsum[i], this.lcumsum[i], this.l[i], this.l[i]).assign(0);
        }
        MGM.upperTri(par.phi, 0);
        par.phi.assign(this.alg.transpose(par.phi), Functions.plus);

        //Xbeta=X*beta*diag(1./betad);
        //Dtheta=D*theta*diag(1./betad);
        final DoubleMatrix2D divBetaD = this.factory2D.diagonal(this.factory1D.make(this.p, 1.0).assign(par.betad, Functions.div));

        final DoubleMatrix2D xBeta = this.alg.mult(this.alg.mult(this.xDat, par.beta), divBetaD);
        final DoubleMatrix2D dTheta = this.alg.mult(this.alg.mult(this.dDat, par.theta), divBetaD);

        //res=Xbeta-X+e*alpha1'+Dtheta;
        final DoubleMatrix2D negLoss = this.factory2D.make(n, this.xDat.columns());

        //wxprod=X*(theta')+D*phi+e*alpha2';
        final DoubleMatrix2D wxProd = this.alg.mult(this.xDat, this.alg.transpose(par.theta));
        wxProd.assign(this.alg.mult(this.dDat, par.phi), Functions.plus);
        for (int i = 0; i < n; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.p; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                negLoss.set(i, j, xBeta.get(i, j) - this.xDat.get(i, j) + par.alpha1.get(j) + dTheta.get(i, j));
            }
            for (int j = 0; j < this.dDat.columns(); j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                wxProd.set(i, j, wxProd.get(i, j) + par.alpha2.get(j));
            }
        }

        //gradbeta=X'*(res);
        grad.beta = this.alg.mult(this.alg.transpose(this.xDat), negLoss);

        //gradbeta=gradbeta-diag(diag(gradbeta)); % zero out diag
        //gradbeta=tril(gradbeta)'+triu(gradbeta);
        final DoubleMatrix2D lowerBeta = this.alg.transpose(MGM.lowerTri(grad.beta.copy(), -1));
        MGM.upperTri(grad.beta, 1).assign(lowerBeta, Functions.plus);

        //gradalpha1=diag(betad)*sum(res,1)';
        grad.alpha1 = this.alg.mult(this.factory2D.diagonal(par.betad), MGM.margSum(negLoss, 1));

        //gradtheta=D'*(res);
        grad.theta = this.alg.mult(this.alg.transpose(this.dDat), negLoss);

        /*
        wxprod=X*(theta')+D*phi+e*alpha2'; %this is n by Ltot
        Lsum=[0;cumsum(L)];
        for r=1:q
            idx=Lsum(r)+1:Lsum(r)+L(r);
            wxtemp=wxprod(:,idx); %n by L(r)
            denom=sum(exp(wxtemp),2); % this is n by 1
            wxtemp=diag(sparse(1./denom))*exp(wxtemp);
            wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))=wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))-1;
            wxprod(:,idx)=wxtemp;
        end
        */

        for (int i = 0; i < this.yDat.columns(); i++) {
            final DoubleMatrix2D wxTemp = wxProd.viewPart(0, this.lcumsum[i], n, this.l[i]);

            // does this need to be done in log space??
            wxTemp.assign(Functions.exp);
            final DoubleMatrix1D invDenom = this.factory1D.make(n, 1.0).assign(MGM.margSum(wxTemp, 2), Functions.div);
            wxTemp.assign(this.alg.mult(this.factory2D.diagonal(invDenom), wxTemp));
            for (int k = 0; k < n; k++) {
                final DoubleMatrix1D curRow = wxTemp.viewRow(k);

                //wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))=wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))-1;
                curRow.set((int) this.yDat.get(k, i) - 1, curRow.get((int) this.yDat.get(k, i) - 1) - 1);
            }
        }

        //gradalpha2=sum(wxprod,1)';
        grad.alpha2 = MGM.margSum(wxProd, 1);

        //gradw=X'*wxprod;
        final DoubleMatrix2D gradW = this.alg.mult(this.alg.transpose(this.xDat), wxProd);

        //gradtheta=gradtheta+gradw';
        grad.theta.assign(this.alg.transpose(gradW), Functions.plus);

        //gradphi=D'*wxprod;
        grad.phi = this.alg.mult(this.alg.transpose(this.dDat), wxProd);

        //zero out gradphi diagonal
        //for r=1:q
        //gradphi(Lsum(r)+1:Lsum(r+1),Lsum(r)+1:Lsum(r+1))=0;
        //end
        for (int i = 0; i < this.q; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            grad.phi.viewPart(this.lcumsum[i], this.lcumsum[i], this.l[i], this.l[i]).assign(0);
        }

        //gradphi=tril(gradphi)'+triu(gradphi);
        final DoubleMatrix2D lowerPhi = this.alg.transpose(MGM.lowerTri(grad.phi.copy(), 0));
        MGM.upperTri(grad.phi, 0).assign(lowerPhi, Functions.plus);

        /*
        for s=1:p
            gradbetad(s)=-n/(2*betad(s))+1/2*norm(res(:,s))^2-res(:,s)'*(Xbeta(:,s)+Dtheta(:,s));
        end
         */
        grad.betad = this.factory1D.make(this.xDat.columns());
        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            grad.betad.set(i, -n / (2.0 * par.betad.get(i)) + this.alg.norm2(negLoss.viewColumn(i)) / 2.0 -
                    this.alg.mult(negLoss.viewColumn(i), xBeta.viewColumn(i).copy().assign(dTheta.viewColumn(i), Functions.plus)));
        }

        grad.alpha1.assign(Functions.div((double) n));
        grad.alpha2.assign(Functions.div((double) n));
        grad.betad.assign(Functions.div((double) n));
        grad.beta.assign(Functions.div((double) n));
        grad.theta.assign(Functions.div((double) n));
        grad.phi.assign(Functions.div((double) n));

        return grad.toMatrix1D();
    }

    /**
     * A proximal operator for the MGM
     *
     * @param t parameter for operator, must be positive
     * @param X input vector to operator
     * @return output vector, same dimension as X
     */
    public DoubleMatrix1D proximalOperator(final double t, final DoubleMatrix1D X) {
        //System.out.println("PROX with t = " + t);
        if (t <= 0)
            throw new IllegalArgumentException("t must be positive: " + t);


        final DoubleMatrix1D tlam = this.lambda.copy().assign(Functions.mult(t));

        //Constructor copies and checks dimension
        //par is a copy so we can update it
        final MGMParams par = new MGMParams(X.copy(), this.p, this.lsum);

        //penbeta = t(1).*(wv(1:p)'*wv(1:p));
        //betascale=zeros(size(beta));
        //betascale=max(0,1-penbeta./abs(beta));
        final DoubleMatrix2D weightMat = this.alg.multOuter(this.weights,
                this.weights, null);
        final DoubleMatrix2D betaWeight = weightMat.viewPart(0, 0, this.p, this.p);
        final DoubleMatrix2D betascale = betaWeight.copy().assign(Functions.mult(-tlam.get(0)));
        betascale.assign(par.beta.copy().assign(Functions.abs), Functions.div);
        betascale.assign(Functions.plus(1));
        betascale.assign(Functions.max(0));

        //beta=beta.*betascale;
        //par.beta.assign(betascale, Functions.mult);
        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.p; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double curVal = par.beta.get(i, j);
                if (curVal != 0) {
                    par.beta.set(i, j, curVal * betascale.get(i, j));
                }
            }
        }

        //weight beta
        //betaw = (wv(1:p)'*wv(1:p)).*beta;
        //betanorms=sum(abs(betaw(:)));
        //double betaNorm = betaWeight.copy().assign(par.beta, Functions.mult).assign(Functions.abs).zSum();

        /*
        thetanorms=0;
        for s=1:p
            for j=1:q
                tempvec=theta(Lsums(j)+1:Lsums(j+1),s);
                tempvec=max(0,1-t(2)*(wv(s)*wv(p+j))/norm(tempvec))*tempvec;
                thetanorms=thetanorms+(wv(s)*wv(p+j))*norm(tempvec);
                theta(Lsums(j)+1:Lsums(j+1),s)=tempvec(1:L(j));
            end
        end
        */
        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.lcumsum.length - 1; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final DoubleMatrix1D tempVec = par.theta.viewColumn(i).viewPart(this.lcumsum[j], this.l[j]);
                //double thetaScale = Math.max(0, 1 - tlam.get(1)*weightMat.get(i, p+j)/Math.sqrt(alg.norm2(tempVec)));
                final double foo = MGM.norm2(tempVec);
                final double thetaScale = Math.max(0, 1 - tlam.get(1) * weightMat.get(i, this.p + j) / MGM.norm2(tempVec));
                tempVec.assign(Functions.mult(thetaScale));
            }
        }

        /*
        for r=1:q
            for j=1:q
                if r<j
                    tempmat=phi(Lsums(r)+1:Lsums(r+1),Lsums(j)+1:Lsums(j+1));
                    tempmat=max(0,1-t(3)*(wv(p+r)*wv(p+j))/norm(tempmat))*tempmat; % Lj by 2*Lr
                    phinorms=phinorms+(wv(p+r)*wv(p+j))*norm(tempmat,'fro');
                    phi( Lsums(r)+1:Lsums(r+1),Lsums(j)+1:Lsums(j+1) )=tempmat;
                end
            end
        end
         */
        for (int i = 0; i < this.lcumsum.length - 1; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = i + 1; j < this.lcumsum.length - 1; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final DoubleMatrix2D tempMat = par.phi.viewPart(this.lcumsum[i], this.lcumsum[j], this.l[i], this.l[j]);

                //Not sure why this isnt Frobenius norm...
                //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.norm2(tempMat));
                final double phiScale = Math.max(0, 1 - tlam.get(2) * weightMat.get(this.p + i, this.p + j) / MGM.norm2(tempMat));
                //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.normF(tempMat));
                tempMat.assign(Functions.mult(phiScale));
            }
        }
        return par.toMatrix1D();
    }

    /**
     * Calculates penalty term and proximal operator at the same time for speed
     *
     * @param t  proximal operator parameter
     * @param X  input
     * @param pX prox operator solution
     * @return value of penalty term
     */
    public double nonSmooth(final double t, final DoubleMatrix1D X, final DoubleMatrix1D pX) {

        //System.out.println("PROX with t = " + t);
        final double nonSmooth = 0;

        final DoubleMatrix1D tlam = this.lambda.copy().assign(Functions.mult(t));

        //Constructor copies and checks dimension
        //par is a copy so we can update it
        final MGMParams par = new MGMParams(X, this.p, this.lsum);

        //penbeta = t(1).*(wv(1:p)'*wv(1:p));
        //betascale=zeros(size(beta));
        //betascale=max(0,1-penbeta./abs(beta));
        final DoubleMatrix2D weightMat = this.alg.multOuter(this.weights,
                this.weights, null);
        final DoubleMatrix2D betaWeight = weightMat.viewPart(0, 0, this.p, this.p);
        final DoubleMatrix2D betascale = betaWeight.copy().assign(Functions.mult(-tlam.get(0)));
        final DoubleMatrix2D absBeta = par.beta.copy().assign(Functions.abs);
        betascale.assign(absBeta, Functions.div);
        betascale.assign(Functions.plus(1));
        betascale.assign(Functions.max(0));

        double betaNorms = 0;

        //beta=beta.*betascale;
        //par.beta.assign(betascale, Functions.mult);
        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.p; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                double curVal = par.beta.get(i, j);
                if (curVal != 0) {
                    curVal = curVal * betascale.get(i, j);
                    par.beta.set(i, j, curVal);
                    betaNorms += Math.abs(betaWeight.get(i, j) * curVal);
                }
            }
        }

        //weight beta
        //betaw = (wv(1:p)'*wv(1:p)).*beta;
        //betanorms=sum(abs(betaw(:)));
        //double betaNorm = betaWeight.copy().assign(par.beta, Functions.mult).assign(Functions.abs).zSum();

        /*
        thetanorms=0;
        for s=1:p
            for j=1:q
                tempvec=theta(Lsums(j)+1:Lsums(j+1),s);
                tempvec=max(0,1-t(2)*(wv(s)*wv(p+j))/norm(tempvec))*tempvec;
                thetanorms=thetanorms+(wv(s)*wv(p+j))*norm(tempvec);
                theta(Lsums(j)+1:Lsums(j+1),s)=tempvec(1:L(j));
            end
        end
        */
        double thetaNorms = 0;
        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.lcumsum.length - 1; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final DoubleMatrix1D tempVec = par.theta.viewColumn(i).viewPart(this.lcumsum[j], this.l[j]);
                //double thetaScale = Math.max(0, 1 - tlam.get(1)*weightMat.get(i, p+j)/Math.sqrt(alg.norm2(tempVec)));
                final double foo = MGM.norm2(tempVec);
                final double thetaScale = Math.max(0, 1 - tlam.get(1) * weightMat.get(i, this.p + j) / MGM.norm2(tempVec));
                tempVec.assign(Functions.mult(thetaScale));
                thetaNorms += weightMat.get(i, this.p + j) * Math.sqrt(this.alg.norm2(tempVec));
            }
        }

        /*
        for r=1:q
            for j=1:q
                if r<j
                    tempmat=phi(Lsums(r)+1:Lsums(r+1),Lsums(j)+1:Lsums(j+1));
                    tempmat=max(0,1-t(3)*(wv(p+r)*wv(p+j))/norm(tempmat))*tempmat; % Lj by 2*Lr
                    phinorms=phinorms+(wv(p+r)*wv(p+j))*norm(tempmat,'fro');
                    phi( Lsums(r)+1:Lsums(r+1),Lsums(j)+1:Lsums(j+1) )=tempmat;
                end
            end
        end
         */
        double phiNorms = 0;
        for (int i = 0; i < this.lcumsum.length - 1; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = i + 1; j < this.lcumsum.length - 1; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final DoubleMatrix2D tempMat = par.phi.viewPart(this.lcumsum[i], this.lcumsum[j], this.l[i], this.l[j]);

                //not sure why this isnt Frobenius norm...
                //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.norm2(tempMat));
                final double phiScale = Math.max(0, 1 - tlam.get(2) * weightMat.get(this.p + i, this.p + j) / MGM.norm2(tempMat));
                //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.normF(tempMat));
                tempMat.assign(Functions.mult(phiScale));
                phiNorms += weightMat.get(this.p + i, this.p + j) * this.alg.normF(tempMat);
            }
        }

        pX.assign(par.toMatrix1D());
        return this.lambda.get(0) * betaNorms + this.lambda.get(1) * thetaNorms + this.lambda.get(2) * phiNorms;
    }

        /*public Matrix compute(double t, Matrix X){
            double[][] out = new double[1][];
            out[0] = computeColt(t, factory1D.make(X.getContinuousData()[0])).toArray();
            return new DenseMatrix(out);
        }*/

    /*public static class softThreshold implements DoubleFunction{
        public double th;
        public softThreshold(double th){
            this.th = Math.abs(th);
        }

        public double apply(double x){
            if(x > th){
                return x-th;
            } else if(x < -th){
                return x+th;
            } else {
                return 0;
            }
        }
    }*/


    /**
     * Learn MGM traditional way with objective function tolerance. Recommended for inference applications that need
     * accurate pseudolikelihood
     *
     * @param epsilon   tolerance in change of objective function
     * @param iterLimit iteration limit
     */
    public void learn(final double epsilon, final int iterLimit) {
        final ProximalGradient pg = new ProximalGradient();
        setParams(new MGMParams(pg.learnBackTrack(this, this.params.toMatrix1D(), epsilon, iterLimit), this.p, this.lsum));
    }

    /**
     * Learn MGM using edge convergence using default 3 iterations of no edge changes. Recommended when we only care about
     * edge existence.
     *
     * @param iterLimit
     */
    public void learnEdges(final int iterLimit) {
        final ProximalGradient pg = new ProximalGradient(.5, .9, true);
        setParams(new MGMParams(pg.learnBackTrack(this, this.params.toMatrix1D(), 0.0, iterLimit), this.p, this.lsum));
    }

    /**
     * Learn MGM using edge convergence using edgeChangeTol (see ProximalGradient for documentation). Recommended when we only care about
     * edge existence.
     *
     * @param iterLimit
     * @param edgeChangeTol
     */
    public void learnEdges(final int iterLimit, final int edgeChangeTol) {
        final ProximalGradient pg = new ProximalGradient(.5, .9, true);
        pg.setEdgeChangeTol(edgeChangeTol);
        setParams(new MGMParams(pg.learnBackTrack(this, this.params.toMatrix1D(), 0.0, iterLimit), this.p, this.lsum));
    }

    /**
     * Converts MGM object to Graph object with edges if edge parameters are non-zero. Loses all edge param information
     *
     * @return
     */
    public Graph graphFromMGM() {

        //List<Node> variables = getVariable();
        final Graph g = new EdgeListGraph(this.variables);

        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = i + 1; j < this.p; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double v1 = this.params.beta.get(i, j);

                if (Math.abs(v1) > 0) {
                    if (!g.isAdjacentTo(this.variables.get(i), this.variables.get(j))) {
                        g.addUndirectedEdge(this.variables.get(i), this.variables.get(j));
                    }
                }
            }
        }

        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.q; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double v1 = this.params.theta.viewColumn(i).viewPart(this.lcumsum[j], this.l[j]).copy().assign(Functions.abs).zSum();

                if (v1 > 0) {
                    if (!g.isAdjacentTo(this.variables.get(i), this.variables.get(this.p + j))) {
                        g.addUndirectedEdge(this.variables.get(i), this.variables.get(this.p + j));
                    }
                }
            }
        }

        for (int i = 0; i < this.q; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = i + 1; j < this.q; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double v1 = this.params.phi.viewPart(this.lcumsum[i], this.lcumsum[j], this.l[i], this.l[j]).copy().assign(Functions.abs).zSum();

                if (v1 > 0) {
                    if (!g.isAdjacentTo(this.variables.get(this.p + i), this.variables.get(this.p + j))) {
                        g.addUndirectedEdge(this.variables.get(this.p + i), this.variables.get(this.p + j));
                    }
                }
            }
        }


        return g;
    }

    /**
     * Converts MGM to matrix of doubles. uses 2-norm to combine c-d edge parameters into single value and f-norm for
     * d-d edge parameters.
     *
     * @return
     */
    public DoubleMatrix2D adjMatFromMGM() {
        //List<Node> variables = getVariable();
        DoubleMatrix2D outMat = DoubleFactory2D.dense.make(this.p + this.q, this.p + this.q);

        outMat.viewPart(0, 0, this.p, this.p).assign(this.params.beta.copy().assign(this.alg.transpose(this.params.beta), Functions.plus));

        for (int i = 0; i < this.p; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < this.q; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double val = MGM.norm2(this.params.theta.viewColumn(i).viewPart(this.lcumsum[j], this.l[j]));
                outMat.set(i, this.p + j, val);
                outMat.set(this.p + j, i, val);
            }
        }

        for (int i = 0; i < this.q; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = i + 1; j < this.q; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double val = this.alg.normF(this.params.phi.viewPart(this.lcumsum[i], this.lcumsum[j], this.l[i], this.l[j]));
                outMat.set(this.p + i, this.p + j, val);
                outMat.set(this.p + j, this.p + i, val);
            }
        }

        //order the adjmat to be the same as the original DataSet variable ordering
        if (this.initVariables != null) {
            final int[] varMap = new int[this.p + this.q];
            for (int i = 0; i < this.p + this.q; i++) {
                varMap[i] = this.variables.indexOf(this.initVariables.get(i));
            }
            outMat = outMat.viewSelection(varMap, varMap);
        }

        return outMat;
    }

    /**
     * Simple search command for GraphSearch implementation. Uses default edge convergence, 1000 iter limit.
     *
     * @return
     */
    public Graph search() {
        final long startTime = System.currentTimeMillis();
        learnEdges(1000); //unlikely to hit this limit
        this.elapsedTime = System.currentTimeMillis() - startTime;
        return graphFromMGM();
    }

    /**
     * Return time of execution for learning.
     *
     * @return
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }


    /*
     * PRIVATE UTILS
     */
    //Utils
    //sum rows together if marg == 1 and cols together if marg == 2
    //Using row-major speeds up marg=1 5x
    private static DoubleMatrix1D margSum(final DoubleMatrix2D mat, final int marg) {
        int n = 0;
        DoubleMatrix1D vec = null;
        final DoubleFactory1D fac = DoubleFactory1D.dense;

        if (marg == 1) {
            n = mat.columns();
            vec = fac.make(n);
            for (int j = 0; j < mat.rows(); j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                for (int i = 0; i < n; i++) {
                    vec.setQuick(i, vec.getQuick(i) + mat.getQuick(j, i));
                }
            }
        } else if (marg == 2) {
            n = mat.rows();
            vec = fac.make(n);
            for (int i = 0; i < n; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                vec.setQuick(i, mat.viewRow(i).zSum());
            }
        }

        return vec;
    }

    //zeros out everthing below di-th diagonal
    public static DoubleMatrix2D upperTri(final DoubleMatrix2D mat, final int di) {
        for (int i = Math.max(-di + 1, 0); i < mat.rows(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = 0; j < Math.min(i + di, mat.rows()); j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                mat.set(i, j, 0);
            }
        }

        return mat;
    }

    //zeros out everthing above di-th diagonal
    private static DoubleMatrix2D lowerTri(final DoubleMatrix2D mat, final int di) {
        for (int i = 0; i < mat.rows() - Math.max(di + 1, 0); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (int j = Math.max(i + di + 1, 0); j < mat.rows(); j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                mat.set(i, j, 0);
            }
        }

        return mat;
    }

    // should move somewhere else...
    private static double norm2(final DoubleMatrix2D mat) {
        //return Math.sqrt(mat.copy().assign(Functions.pow(2)).zSum());
        final Algebra al = new Algebra();

        //norm found by svd so we need rows >= cols
        if (mat.rows() < mat.columns()) {
            return al.norm2(al.transpose(mat));
        }
        return al.norm2(mat);
    }

    private static double norm2(final DoubleMatrix1D vec) {
        //return Math.sqrt(vec.copy().assign(Functions.pow(2)).zSum());
        return Math.sqrt(new Algebra().norm2(vec));
    }

    private static void runTests1() {
        try {
            //DoubleMatrix2D xIn = DoubleFactory2D.dense.make(loadDataSelect("/Users/ajsedgewick/tetrad/test_data", "med_test_C.txt"));
            //DoubleMatrix2D yIn = DoubleFactory2D.dense.make(loadDataSelect("/Users/ajsedgewick/tetrad/test_data", "med_test_D.txt"));
            //String path = MGM.class.getResource("test_data").getPath();
            final String path = "/Users/ajsedgewick/tetrad_master/tetrad/tetrad-lib/src/main/java/edu/pitt/csb/mgm/test_data";
            System.out.println(path);
            final DoubleMatrix2D xIn = DoubleFactory2D.dense.make(MixedUtils.loadDelim(path, "med_test_C.txt").getDoubleData().toArray());
            final DoubleMatrix2D yIn = DoubleFactory2D.dense.make(MixedUtils.loadDelim(path, "med_test_D.txt").getDoubleData().toArray());
            final int[] L = new int[24];
            final Node[] vars = new Node[48];
            for (int i = 0; i < 24; i++) {
                L[i] = 2;
                vars[i] = new ContinuousVariable("X" + i);
                vars[i + 24] = new DiscreteVariable("Y" + i);
            }

            final double lam = .2;
            final MGM model = new MGM(xIn, yIn, new ArrayList<>(Arrays.asList(vars)), L, new double[]{lam, lam, lam});
            final MGM model2 = new MGM(xIn, yIn, new ArrayList<>(Arrays.asList(vars)), L, new double[]{lam, lam, lam});

            System.out.println("Weights: " + Arrays.toString(model.weights.toArray()));

            DoubleMatrix2D test = xIn.copy();
            DoubleMatrix2D test2 = xIn.copy();
            long t = System.currentTimeMillis();
            for (int i = 0; i < 50000; i++) {
                test2 = xIn.copy();
                test.assign(test2);
            }
            System.out.println("assign Time: " + (System.currentTimeMillis() - t));

            t = System.currentTimeMillis();
            final double[][] xArr = xIn.toArray();
            for (int i = 0; i < 50000; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                //test = DoubleFactory2D.dense.make(xArr);
                test2 = xIn.copy();
                test = test2;
            }
            System.out.println("equals Time: " + (System.currentTimeMillis() - t));


            System.out.println("Init nll: " + model.smoothValue(model.params.toMatrix1D()));
            System.out.println("Init reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));

            t = System.currentTimeMillis();
            model.learnEdges(700);
            //model.learn(1e-7, 700);
            System.out.println("Orig Time: " + (System.currentTimeMillis() - t));

            System.out.println("nll: " + model.smoothValue(model.params.toMatrix1D()));
            System.out.println("reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));

            System.out.println("params:\n" + model.params);
            System.out.println("adjMat:\n" + model.adjMatFromMGM());


        } catch (final IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * test non penalty use cases
     */
    private static void runTests2() {
        Graph g = GraphConverter.convert("X1-->X2,X3-->X2,X4-->X5");
        //simple graph pm im gen example

        final HashMap<String, Integer> nd = new HashMap<>();
        nd.put("X1", 0);
        nd.put("X2", 0);
        nd.put("X3", 4);
        nd.put("X4", 4);
        nd.put("X5", 4);

        g = MixedUtils.makeMixedGraph(g, nd);

        final GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(g, "Split(-1.5,-.5,.5,1.5)");
        System.out.println(pm);

        final GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);
        System.out.println(im);

        final int samps = 1000;
        DataSet ds = im.simulateDataFisher(samps);
        ds = MixedUtils.makeMixedData(ds, nd);
        //System.out.println(ds);

        final double lambda = 0;
        final MGM model = new MGM(ds, new double[]{lambda, lambda, lambda});

        System.out.println("Init nll: " + model.smoothValue(model.params.toMatrix1D()));
        System.out.println("Init reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));

        model.learn(1e-8, 1000);

        System.out.println("Learned nll: " + model.smoothValue(model.params.toMatrix1D()));
        System.out.println("Learned reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));

        System.out.println("params:\n" + model.params);
        System.out.println("adjMat:\n" + model.adjMatFromMGM());
    }

    public static void main(final String[] args) {
        MGM.runTests1();
    }

}

