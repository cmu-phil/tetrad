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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
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
public class MGM extends ConvexProximal implements GraphSearch{
    private DoubleFactory2D factory2D = DoubleFactory2D.dense;
    private DoubleFactory1D factory1D = DoubleFactory1D.dense;

    //private DoubleFactory2D factory2D = DoubleFactory2D.sparse;
    //private DoubleFactory1D factory1D = DoubleFactory1D.sparse;

    //Continuous Data
    private DoubleMatrix2D xDat;

    //Discrete Data coded as integers, no IntMatrix2D apparently...
    private DoubleMatrix2D yDat;

    private List<Node> variables;
    private List<Node> initVariables = null;

    //Discrete Data coded as dummy variables
    private DoubleMatrix2D dDat;


    private DoubleMatrix1D lambda;
    private Algebra alg = new Algebra();

    private long elapsedTime = 0;

    //Levels of Discrete variables
    private int[] l;
    private int lsum;
    private int[] lcumsum;
    int p;
    int q;
    int n;

    //parameter weights
    private DoubleMatrix1D weights;

    public MGM(DoubleMatrix2D x, DoubleMatrix2D y, List<Node> variables, int[] l, double[] lambda){

        if(l.length != y.columns())
            throw new IllegalArgumentException("length of l doesn't match number of variables in Y");

        if(y.rows() != x.rows())
            throw new IllegalArgumentException("different number of samples for x and y");

        //lambda should have 3 values corresponding to cc, cd, and dd
        if(lambda.length != 3)
            throw new IllegalArgumentException("Lambda should have three values for cc, cd, and dd edges respectively");


        this.xDat = x;
        this.yDat = y;
        this.l = l;
        this.p = x.columns();
        this.q = y.columns();
        this.n = x.rows();
        this.variables = variables;


        this.lambda = factory1D.make(lambda);
        fixData();
        initParameters();
        calcWeights();
        makeDummy();
    }

    public MGM(DataSet ds, double[] lambda){
        this.variables = ds.getVariables();
        DataSet dsCont = MixedUtils.getContinousData(ds);
        DataSet dsDisc = MixedUtils.getDiscreteData(ds);
        this.xDat = factory2D.make(dsCont.getDoubleData().toArray());
        this.yDat = factory2D.make(dsDisc.getDoubleData().toArray());
        this.l = MixedUtils.getDiscLevels(ds);
        this.p = xDat.columns();
        this.q = yDat.columns();
        this.n = xDat.rows();

        //the variables are now ordered continuous first then discrete
        this.variables = new ArrayList<Node>();
        variables.addAll(dsCont.getVariables());
        variables.addAll(dsDisc.getVariables());

        this.initVariables = ds.getVariables();

        this.lambda = factory1D.make(lambda);

        //Data is checked for 0 or 1 indexing and fore missing levels
        fixData();
        initParameters();
        calcWeights();
        makeDummy();
    }

    public static class MGMParams{
        //Model parameters
        private DoubleMatrix2D beta; //continuous-continuous
        private DoubleMatrix1D betad; //cont squared node pot
        private DoubleMatrix2D theta; //continuous-discrete
        private DoubleMatrix2D phi; //discrete-discrete
        private DoubleMatrix1D alpha1; //cont linear node pot
        private DoubleMatrix1D alpha2; //disc node pot

        public MGMParams(){

        }

        //nothing is copied here, all pointers back to inputs...
        public MGMParams(DoubleMatrix2D beta, DoubleMatrix1D betad, DoubleMatrix2D theta,
            DoubleMatrix2D phi, DoubleMatrix1D alpha1, DoubleMatrix1D alpha2) {
            this.beta = beta;
            this.betad = betad;
            this.theta = theta;
            this.phi = phi;
            this.alpha1 = alpha1;
            this.alpha2 = alpha2;
        }

        //copy from another parameter set
        public MGMParams(MGMParams parIn){
            this.beta = parIn.beta.copy();
            this.betad = parIn.betad.copy();
            this.theta = parIn.theta.copy();
            this.phi = parIn.phi.copy();
            this.alpha1 = parIn.alpha1.copy();
            this.alpha2 = parIn.alpha2.copy();
        }

        //copy params from flattened vector
        public MGMParams(DoubleMatrix1D vec, int p, int ltot){
            int[] lens = {p*p, p, p*ltot, ltot*ltot, p, ltot};
            int[] lenSums = new int[lens.length];
            lenSums[0] = lens[0];
            for(int i = 1; i < lenSums.length; i++){
                lenSums[i] = lens[i] + lenSums[i-1];
            }

            if(vec.size() != lenSums[5])
                throw new IllegalArgumentException("Param vector dimension doesn't match: Found " + vec.size() + " need " + lenSums[5]);

            beta = DoubleFactory2D.dense.make(vec.viewPart(0, lens[0]).toArray(), p);
            betad = vec.viewPart(lenSums[0], lens[1]).copy();
            theta = DoubleFactory2D.dense.make(vec.viewPart(lenSums[1], lens[2]).toArray(), ltot);
            phi = DoubleFactory2D.dense.make(vec.viewPart(lenSums[2], lens[3]).toArray(), ltot);
            alpha1 = vec.viewPart(lenSums[3], lens[4]).copy();
            alpha2 = vec.viewPart(lenSums[4], lens[5]).copy();
        }

        public String toString(){
            String outStr = "alpha1: " + alpha1.toString();
            outStr += "\nalpha2: " + alpha2.toString();
            outStr += "\nbeta: " + beta.toString();
            outStr += "\nbetad: " + betad.toString();
            outStr += "\ntheta: " + theta.toString();
            outStr += "\nphi: " + phi.toString();
            return outStr;
        }

        public DoubleMatrix1D getAlpha1() {
            return alpha1;
        }

        public DoubleMatrix1D getAlpha2() {
            return alpha2;
        }

        public DoubleMatrix1D getBetad() {
            return betad;
        }

        public DoubleMatrix2D getBeta() {
            return beta;
        }

        public DoubleMatrix2D getPhi() {
            return phi;
        }

        public DoubleMatrix2D getTheta() {
            return theta;
        }

        public void setAlpha1(DoubleMatrix1D alpha1) {
            this.alpha1 = alpha1;
        }

        public void setAlpha2(DoubleMatrix1D alpha2) {
            this.alpha2 = alpha2;
        }

        public void setBeta(DoubleMatrix2D beta) {
            this.beta = beta;
        }

        public void setBetad(DoubleMatrix1D betad) {
            this.betad = betad;
        }

        public void setPhi(DoubleMatrix2D phi) {
            this.phi = phi;
        }

        public void setTheta(DoubleMatrix2D theta) {
            this.theta = theta;
        }

        /**
         * Copy all params into a single vector
         * @return
         */
        public DoubleMatrix1D toMatrix1D(){
            DoubleFactory1D fac = DoubleFactory1D.dense;
            int p = alpha1.size();
            int ltot = alpha2.size();
            int[] lens = {p*p, p, p*ltot, ltot*ltot, p, ltot};
            int[] lenSums = new int[lens.length];
            lenSums[0] = lens[0];
            for(int i = 1; i < lenSums.length; i++){
                lenSums[i] = lens[i] + lenSums[i-1];
            }

            DoubleMatrix1D outVec = fac.make(p*p + p + p*ltot + ltot*ltot + p + ltot);
            outVec.viewPart(0, lens[0]).assign(flatten(beta));
            outVec.viewPart(lenSums[0],lens[1]).assign(betad);
            outVec.viewPart(lenSums[1],lens[2]).assign(flatten(theta));
            outVec.viewPart(lenSums[2],lens[3]).assign(flatten(phi));
            outVec.viewPart(lenSums[3],lens[4]).assign(alpha1);
            outVec.viewPart(lenSums[4],lens[5]).assign(alpha2);

            return outVec;
        }

        //likely depreciated
        public double[][] toVector(){
            double[][] outArr = new double[1][];
            outArr[0] = toMatrix1D().toArray();
            return outArr;
        }
    }

    private MGMParams params;

    public void setParams(MGMParams newParams){
        params = newParams;
    }

    //create column major vector from matrix (i.e. concatenate columns)
    public static DoubleMatrix1D flatten(DoubleMatrix2D m){
        DoubleMatrix1D[] colArray = new DoubleMatrix1D[m.columns()];
        for(int i = 0; i < m.columns(); i++){
            colArray[i] = m.viewColumn(i);
        }

        return DoubleFactory1D.dense.make(colArray);
    }

    //init all parameters to zeros except for betad which is set to 1s
    private void initParameters(){
        lcumsum = new int[l.length+1];
        lcumsum[0] = 0;
        for(int i = 0; i < l.length; i++){
            lcumsum[i+1] = lcumsum[i] + l[i];
        }
        lsum = lcumsum[l.length];

        //LH init to zeros, maybe should be random init?
        DoubleMatrix2D beta = factory2D.make(xDat.columns(), xDat.columns()); //continuous-continuous
        DoubleMatrix1D betad = factory1D.make(xDat.columns(), 1.0); //cont squared node pot
        DoubleMatrix2D  theta = factory2D.make(lsum, xDat.columns());; //continuous-discrete
        DoubleMatrix2D phi = factory2D.make(lsum, lsum); //discrete-discrete
        DoubleMatrix1D alpha1 = factory1D.make(xDat.columns()); //cont linear node pot
        DoubleMatrix1D alpha2 = factory1D.make(lsum); //disc node potbeta =
        params = new MGMParams(beta, betad, theta, phi, alpha1, alpha2);

        //separate lambda for each type of edge, [cc, cd, dd]
        //lambda = factory1D.make(3);
    }

    // avoid underflow in log(sum(exp(x))) calculation
    private double logsumexp(DoubleMatrix1D x){
        DoubleMatrix1D myX = x.copy();
        double maxX = StatUtils.max(myX.toArray());
        return Math.log(myX.assign(Functions.minus(maxX)).assign(Functions.exp).zSum()) + maxX;
    }

    //calculate parameter weights as in Lee and Hastie
    private void calcWeights(){
        weights = factory1D.make(p+q);
        for(int i = 0; i < p; i++){
            weights.set(i, StatUtils.sd(xDat.viewColumn(i).toArray()));
        }
        for(int j = 0; j < q; j++){
            double curWeight = 0;
            for(int k = 0; k < l[j] ; k++){
                double curp = yDat.viewColumn(j).copy().assign(Functions.equals(k+1)).zSum()/(double) n;
                curWeight += curp*(1-curp);
            }
            weights.set(p+j, Math.sqrt(curWeight));
        }
    }

    /**
     * Convert discrete data (in yDat) to a matrix of dummy variables (stored in dDat)
     */
    private void makeDummy(){
        dDat = factory2D.make(n, lsum);
        for(int i = 0; i < q; i++){
            for(int j = 0; j < l[i]; j++){
                DoubleMatrix1D curCol = yDat.viewColumn(i).copy().assign(Functions.equals(j+1));
                if(curCol.zSum() == 0)
                    throw new IllegalArgumentException("Discrete data is missing a level: variable " + i + " level " + j);
                dDat.viewColumn(lcumsum[i]+j).assign(curCol);
            }
        }
    }

    /**
     * checks if yDat is zero indexed and converts to 1 index. zscores x
     */
    private void fixData(){
        double ymin = StatUtils.min(flatten(yDat).toArray());
        if(ymin < 0 || ymin > 1)
            throw new IllegalArgumentException("Discrete data must be either zero or one indexed. Found min index: " + ymin);

        if(ymin==0){
            yDat.assign(Functions.plus(1.0));
        }


        //z-score columns of X
        for(int i = 0; i < p; i++){
            xDat.viewColumn(i).assign(StatUtils.standardizeData(xDat.viewColumn(i).toArray()));
        }
    }

    /**
     * non-penalized -log(pseudolikelihood) this is the smooth function g(x) in prox gradient
     *
     * @param parIn
     * @return
     */
    public double smoothValue(DoubleMatrix1D parIn){
        //work with copy
        MGMParams par = new MGMParams(parIn, p, lsum);

        for(int i = 0; i < par.betad.size(); i++){
            if(par.betad.get(i)<0)
                return Double.POSITIVE_INFINITY;
        }
        //double nll = 0;
        //int n = xDat.rows();
        //beta=beta+beta';
        //phi=phi+phi';
        upperTri(par.beta, 1);
        par.beta.assign(alg.transpose(par.beta), Functions.plus);

        for(int i = 0; i < q; i++){
            par.phi.viewPart(lcumsum[i], lcumsum[i], l[i], l[i]).assign(0);
        }
        // ensure mats are upper triangular
        upperTri(par.phi,0);
        par.phi.assign(alg.transpose(par.phi), Functions.plus);


        //Xbeta=X*beta*diag(1./betad);
        DoubleMatrix2D divBetaD = factory2D.diagonal(factory1D.make(p,1.0).assign(par.betad, Functions.div));
        DoubleMatrix2D xBeta = alg.mult(xDat,alg.mult(par.beta, divBetaD));

        //Dtheta=D*theta*diag(1./betad);
        DoubleMatrix2D dTheta = alg.mult(alg.mult(dDat, par.theta), divBetaD);

        // Squared loss
        //sqloss=-n/2*sum(log(betad))+...
        //.5*norm((X-e*alpha1'-Xbeta-Dtheta)*diag(sqrt(betad)),'fro')^2;
        DoubleMatrix2D tempLoss = factory2D.make(n, xDat.columns());

        //wxprod=X*(theta')+D*phi+e*alpha2';
        DoubleMatrix2D wxProd = alg.mult(xDat, alg.transpose(par.theta));
        wxProd.assign(alg.mult(dDat, par.phi), Functions.plus);
        for(int i = 0; i < n; i++){
            for(int j = 0; j < xDat.columns(); j++){
                tempLoss.set(i,j,xDat.get(i,j) - par.alpha1.get(j) - xBeta.get(i,j) - dTheta.get(i,j));
            }
            for(int j = 0; j < dDat.columns(); j++){
                wxProd.set(i,j,wxProd.get(i,j) + par.alpha2.get(j));
            }
        }

        double sqloss = -n/2.0*par.betad.copy().assign(Functions.log).zSum() +
                .5 * Math.pow(alg.normF(alg.mult(tempLoss, factory2D.diagonal(par.betad.copy().assign(Functions.sqrt)))), 2);


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
        for(int i = 0; i < yDat.columns(); i++){
            DoubleMatrix2D wxTemp = wxProd.viewPart(0, lcumsum[i], n, l[i]);
            for(int k = 0; k < n; k++){
                DoubleMatrix1D curRow = wxTemp.viewRow(k);

                catloss -= curRow.get((int) yDat.get(k, i) - 1);
                catloss += logsumexp(curRow);
            }
        }

        return (sqloss + catloss)/((double) n);
    }

    /**
     * non-penalized -log(pseudolikelihood) this is the smooth function g(x) in prox gradient
     * this overloaded version calculates both nll and the smooth gradient at the same time
     * any value in gradOut will be replaced by the new calculations
     *
     *
     * @param parIn
     * @param gradOutVec
     * @return
     */
    public double smooth(DoubleMatrix1D parIn, DoubleMatrix1D gradOutVec){
        //work with copy
        MGMParams par = new MGMParams(parIn, p, lsum);
        MGMParams gradOut = new MGMParams();

        for(int i = 0; i < par.betad.size(); i++){
            if(par.betad.get(i)<0)
                return Double.POSITIVE_INFINITY;
        }

        //beta=beta-diag(diag(beta));
        //for r=1:q
        //  phi(Lsum(r)+1:Lsum(r+1),Lsum(r)+1:Lsum(r+1))=0;
        //end
        //beta=triu(beta); phi=triu(phi);
        //beta=beta+beta';
        //phi=phi+phi';
        upperTri(par.beta, 1);
        par.beta.assign(alg.transpose(par.beta), Functions.plus);

        for(int i = 0; i < q; i++){
            par.phi.viewPart(lcumsum[i], lcumsum[i], l[i], l[i]).assign(0);
        }
        //ensure matrix is upper triangular
        upperTri(par.phi,0);
        par.phi.assign(alg.transpose(par.phi), Functions.plus);

        //Xbeta=X*beta*diag(1./betad);
        DoubleMatrix2D divBetaD = factory2D.diagonal(factory1D.make(p,1.0).assign(par.betad, Functions.div));
        DoubleMatrix2D xBeta = alg.mult(xDat,alg.mult(par.beta, divBetaD));

        //Dtheta=D*theta*diag(1./betad);
        DoubleMatrix2D dTheta = alg.mult(alg.mult(dDat, par.theta), divBetaD);

        // Squared loss
        //tempLoss =  (X-e*alpha1'-Xbeta-Dtheta) = -res (in gradient code)
        DoubleMatrix2D tempLoss = factory2D.make(n, xDat.columns());

        //wxprod=X*(theta')+D*phi+e*alpha2';
        DoubleMatrix2D wxProd = alg.mult(xDat, alg.transpose(par.theta));
        wxProd.assign(alg.mult(dDat, par.phi), Functions.plus);
        for(int i = 0; i < n; i++){
            for(int j = 0; j < xDat.columns(); j++){
                tempLoss.set(i,j,xDat.get(i,j) - par.alpha1.get(j) - xBeta.get(i,j) - dTheta.get(i,j));
            }
            for(int j = 0; j < dDat.columns(); j++){
                wxProd.set(i,j,wxProd.get(i,j) + par.alpha2.get(j));
            }
        }

        //sqloss=-n/2*sum(log(betad))+...
        //.5*norm((X-e*alpha1'-Xbeta-Dtheta)*diag(sqrt(betad)),'fro')^2;
        double sqloss = -n/2.0*par.betad.copy().assign(Functions.log).zSum() +
                .5 * Math.pow(alg.normF(alg.mult(tempLoss, factory2D.diagonal(par.betad.copy().assign(Functions.sqrt)))), 2);

        //ok now tempLoss = res
        tempLoss.assign(Functions.mult(-1));

        //gradbeta=X'*(res);
        gradOut.beta = alg.mult(alg.transpose(xDat), tempLoss);

        //gradbeta=gradbeta-diag(diag(gradbeta)); % zero out diag
        //gradbeta=tril(gradbeta)'+triu(gradbeta);
        DoubleMatrix2D lowerBeta = alg.transpose(lowerTri(gradOut.beta.copy(), -1));
        upperTri(gradOut.beta, 1).assign(lowerBeta, Functions.plus);

        //gradalpha1=diag(betad)*sum(res,1)';
        gradOut.alpha1 = alg.mult(factory2D.diagonal(par.betad),margSum(tempLoss, 1));

        //gradtheta=D'*(res);
        gradOut.theta = alg.mult(alg.transpose(dDat), tempLoss);

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
        for(int i = 0; i < yDat.columns(); i++){
            DoubleMatrix2D wxTemp = wxProd.viewPart(0, lcumsum[i], n, l[i]);
            //need to copy init values for calculating nll
            DoubleMatrix2D wxTemp0 = wxTemp.copy();

            // does this need to be done in log space??
            wxTemp.assign(Functions.exp);
            DoubleMatrix1D invDenom = factory1D.make(n,1.0).assign(margSum(wxTemp, 2), Functions.div);
            wxTemp.assign(alg.mult(factory2D.diagonal(invDenom), wxTemp));
            for(int k = 0; k < n; k++){
                DoubleMatrix1D curRow = wxTemp.viewRow(k);
                DoubleMatrix1D curRow0 = wxTemp0.viewRow(k);

                catloss -= curRow0.get((int) yDat.get(k, i) - 1);
                catloss += logsumexp(curRow0);


                //wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))=wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))-1;
                curRow.set((int) yDat.get(k,i)-1, curRow.get((int) yDat.get(k,i)-1) - 1);
            }
        }

        //gradalpha2=sum(wxprod,1)';
        gradOut.alpha2 = margSum(wxProd,1);

        //gradw=X'*wxprod;
        DoubleMatrix2D gradW = alg.mult(alg.transpose(xDat), wxProd);

        //gradtheta=gradtheta+gradw';
        gradOut.theta.assign(alg.transpose(gradW), Functions.plus);

        //gradphi=D'*wxprod;
        gradOut.phi = alg.mult(alg.transpose(dDat), wxProd);

        //zero out gradphi diagonal
        //for r=1:q
        //gradphi(Lsum(r)+1:Lsum(r+1),Lsum(r)+1:Lsum(r+1))=0;
        //end
        for(int i = 0; i < q; i++){
            gradOut.phi.viewPart(lcumsum[i], lcumsum[i], l[i], l[i]).assign(0);
        }

        //gradphi=tril(gradphi)'+triu(gradphi);
        DoubleMatrix2D lowerPhi = alg.transpose(lowerTri(gradOut.phi.copy(), 0));
        upperTri(gradOut.phi, 0).assign(lowerPhi, Functions.plus);

        /*
        for s=1:p
            gradbetad(s)=-n/(2*betad(s))+1/2*norm(res(:,s))^2-res(:,s)'*(Xbeta(:,s)+Dtheta(:,s));
        end
         */
        gradOut.betad = factory1D.make(xDat.columns());
        for(int i = 0; i < p; i++){
            gradOut.betad.set(i, -n / (2.0 * par.betad.get(i)) + alg.norm2(tempLoss.viewColumn(i)) / 2.0 -
                    alg.mult(tempLoss.viewColumn(i), xBeta.viewColumn(i).copy().assign(dTheta.viewColumn(i), Functions.plus)));
        }

        gradOut.alpha1.assign(Functions.div((double) n));
        gradOut.alpha2.assign(Functions.div((double) n));
        gradOut.betad.assign(Functions.div((double) n));
        gradOut.beta.assign(Functions.div((double) n));
        gradOut.theta.assign(Functions.div((double) n));
        gradOut.phi.assign(Functions.div((double) n));

        gradOutVec.assign(gradOut.toMatrix1D());
        return (sqloss + catloss)/((double) n);
    }

    /**
     * Calculates penalty term of objective function
     *
     * @param parIn
     * @return
     */
    public double nonSmoothValue(DoubleMatrix1D parIn){
        //DoubleMatrix1D tlam = lambda.copy().assign(Functions.mult(t));
        //Dimension checked in constructor
        //par is a copy so we can update it
        MGMParams par = new MGMParams(parIn, p, lsum);

        //penbeta = t(1).*(wv(1:p)'*wv(1:p));
        //betascale=zeros(size(beta));
        //betascale=max(0,1-penbeta./abs(beta));
        DoubleMatrix2D weightMat = alg.multOuter(weights,
                weights, null);

        //int p = xDat.columns();

        //weight beta
        //betaw = (wv(1:p)'*wv(1:p)).*abs(beta);
        //betanorms=sum(betaw(:));
        DoubleMatrix2D betaWeight = weightMat.viewPart(0, 0, p, p);
        DoubleMatrix2D absBeta = par.beta.copy().assign(Functions.abs);
        double betaNorms = absBeta.assign(betaWeight, Functions.mult).zSum();


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
        for(int i = 0; i < p; i++){
            for(int j = 0; j < lcumsum.length-1; j++){
                DoubleMatrix1D tempVec = par.theta.viewColumn(i).viewPart(lcumsum[j], l[j]);
                thetaNorms += weightMat.get(i, p+j)*Math.sqrt(alg.norm2(tempVec));
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
        for(int i = 0; i < lcumsum.length-1; i++){
            for(int j = i+1; j < lcumsum.length-1; j++){
                DoubleMatrix2D tempMat = par.phi.viewPart(lcumsum[i], lcumsum[j], l[i], l[j]);
                phiNorms += weightMat.get(p+i,p+j)*alg.normF(tempMat);
            }
        }

        return lambda.get(0)*betaNorms + lambda.get(1)*thetaNorms + lambda.get(2)*phiNorms;
    }


    /**
     * Gradient of the pseudolikelihood
     *
     * @param parIn
     * @return
     */
    public DoubleMatrix1D smoothGradient(DoubleMatrix1D parIn){
        int n = xDat.rows();
        MGMParams grad = new MGMParams();

        //
        MGMParams par = new MGMParams(parIn, p, lsum);
        upperTri(par.beta, 1);
        par.beta.assign(alg.transpose(par.beta), Functions.plus);

        for(int i = 0; i < q; i++){
            par.phi.viewPart(lcumsum[i], lcumsum[i], l[i], l[i]).assign(0);
        }
        upperTri(par.phi, 0);
        par.phi.assign(alg.transpose(par.phi), Functions.plus);

        //Xbeta=X*beta*diag(1./betad);
        //Dtheta=D*theta*diag(1./betad);
        DoubleMatrix2D divBetaD = factory2D.diagonal(factory1D.make(p, 1.0).assign(par.betad, Functions.div));

        DoubleMatrix2D xBeta = alg.mult(alg.mult(xDat, par.beta), divBetaD);
        DoubleMatrix2D dTheta = alg.mult(alg.mult(dDat, par.theta), divBetaD);

        //res=Xbeta-X+e*alpha1'+Dtheta;
        DoubleMatrix2D negLoss = factory2D.make(n, xDat.columns());

        //wxprod=X*(theta')+D*phi+e*alpha2';
        DoubleMatrix2D wxProd = alg.mult(xDat, alg.transpose(par.theta));
        wxProd.assign(alg.mult(dDat, par.phi), Functions.plus);
        for(int i = 0; i < n; i++){
            for(int j = 0; j < p; j++){
                negLoss.set(i,j, xBeta.get(i,j) - xDat.get(i,j) + par.alpha1.get(j) + dTheta.get(i,j));
            }
            for(int j = 0; j < dDat.columns(); j++){
                wxProd.set(i,j,wxProd.get(i,j) + par.alpha2.get(j));
            }
        }

        //gradbeta=X'*(res);
        grad.beta = alg.mult(alg.transpose(xDat), negLoss);

        //gradbeta=gradbeta-diag(diag(gradbeta)); % zero out diag
        //gradbeta=tril(gradbeta)'+triu(gradbeta);
        DoubleMatrix2D lowerBeta = alg.transpose(lowerTri(grad.beta.copy(), -1));
        upperTri(grad.beta, 1).assign(lowerBeta, Functions.plus);

        //gradalpha1=diag(betad)*sum(res,1)';
        grad.alpha1 = alg.mult(factory2D.diagonal(par.betad),margSum(negLoss, 1));

        //gradtheta=D'*(res);
        grad.theta = alg.mult(alg.transpose(dDat), negLoss);

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

        for(int i = 0; i < yDat.columns(); i++){
            DoubleMatrix2D wxTemp = wxProd.viewPart(0, lcumsum[i], n, l[i]);

            // does this need to be done in log space??
            wxTemp.assign(Functions.exp);
            DoubleMatrix1D invDenom = factory1D.make(n,1.0).assign(margSum(wxTemp, 2), Functions.div);
            wxTemp.assign(alg.mult(factory2D.diagonal(invDenom), wxTemp));
            for(int k = 0; k < n; k++){
                DoubleMatrix1D curRow = wxTemp.viewRow(k);

                //wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))=wxtemp(sub2ind(size(wxtemp),(1:n)',Y(:,r)))-1;
                curRow.set((int) yDat.get(k,i)-1, curRow.get((int) yDat.get(k,i)-1) - 1);
            }
        }

        //gradalpha2=sum(wxprod,1)';
        grad.alpha2 = margSum(wxProd,1);

        //gradw=X'*wxprod;
        DoubleMatrix2D gradW = alg.mult(alg.transpose(xDat), wxProd);

        //gradtheta=gradtheta+gradw';
        grad.theta.assign(alg.transpose(gradW), Functions.plus);

        //gradphi=D'*wxprod;
        grad.phi = alg.mult(alg.transpose(dDat), wxProd);

        //zero out gradphi diagonal
        //for r=1:q
        //gradphi(Lsum(r)+1:Lsum(r+1),Lsum(r)+1:Lsum(r+1))=0;
        //end
        for(int i = 0; i < q; i++){
            grad.phi.viewPart(lcumsum[i], lcumsum[i], l[i], l[i]).assign(0);
        }

        //gradphi=tril(gradphi)'+triu(gradphi);
        DoubleMatrix2D lowerPhi = alg.transpose(lowerTri(grad.phi.copy(), 0));
        upperTri(grad.phi, 0).assign(lowerPhi, Functions.plus);

        /*
        for s=1:p
            gradbetad(s)=-n/(2*betad(s))+1/2*norm(res(:,s))^2-res(:,s)'*(Xbeta(:,s)+Dtheta(:,s));
        end
         */
        grad.betad = factory1D.make(xDat.columns());
        for(int i = 0; i < p; i++){
            grad.betad.set(i, -n / (2.0 * par.betad.get(i)) + alg.norm2(negLoss.viewColumn(i)) / 2.0 -
                    alg.mult(negLoss.viewColumn(i), xBeta.viewColumn(i).copy().assign(dTheta.viewColumn(i), Functions.plus)));
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
    public DoubleMatrix1D proximalOperator(double t, DoubleMatrix1D X) {
            //System.out.println("PROX with t = " + t);
        if(t <= 0)
            throw new IllegalArgumentException("t must be positive: " + t);


        DoubleMatrix1D tlam = lambda.copy().assign(Functions.mult(t));

        //Constructor copies and checks dimension
        //par is a copy so we can update it
        MGMParams par = new MGMParams(X.copy(), p, lsum);

        //penbeta = t(1).*(wv(1:p)'*wv(1:p));
        //betascale=zeros(size(beta));
        //betascale=max(0,1-penbeta./abs(beta));
        DoubleMatrix2D weightMat = alg.multOuter(weights,
                weights, null);
        DoubleMatrix2D betaWeight = weightMat.viewPart(0, 0, p, p);
        DoubleMatrix2D betascale = betaWeight.copy().assign(Functions.mult(-tlam.get(0)));
        betascale.assign(par.beta.copy().assign(Functions.abs), Functions.div);
        betascale.assign(Functions.plus(1));
        betascale.assign(Functions.max(0));

        //beta=beta.*betascale;
        //par.beta.assign(betascale, Functions.mult);
        for(int i= 0; i < p; i++){
            for(int j = 0; j < p; j++){
                double curVal =  par.beta.get(i,j);
                if(curVal !=0){
                    par.beta.set(i,j, curVal*betascale.get(i,j));
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
        for(int i = 0; i < p; i++){
            for(int j = 0; j < lcumsum.length-1; j++){
                DoubleMatrix1D tempVec = par.theta.viewColumn(i).viewPart(lcumsum[j], l[j]);
                //double thetaScale = Math.max(0, 1 - tlam.get(1)*weightMat.get(i, p+j)/Math.sqrt(alg.norm2(tempVec)));
                double foo = norm2(tempVec);
                double thetaScale = Math.max(0, 1 - tlam.get(1) * weightMat.get(i, p+j)/norm2(tempVec));
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
        for(int i = 0; i < lcumsum.length-1; i++){
            for(int j = i+1; j < lcumsum.length-1; j++){
                DoubleMatrix2D tempMat = par.phi.viewPart(lcumsum[i], lcumsum[j], l[i], l[j]);

                //Not sure why this isnt Frobenius norm...
                //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.norm2(tempMat));
                double phiScale = Math.max(0, 1 - tlam.get(2) * weightMat.get(p + i,p+j)/norm2(tempMat));
                //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.normF(tempMat));
                tempMat.assign(Functions.mult(phiScale));
            }
        }
        return par.toMatrix1D();
    }

    /**
     * Calculates penalty term and proximal operator at the same time for speed
     *
     * @param t proximal operator parameter
     * @param X input
     * @param pX prox operator solution
     * @return value of penalty term
     */
    public double nonSmooth(double t, DoubleMatrix1D X, DoubleMatrix1D pX) {

        //System.out.println("PROX with t = " + t);
        double nonSmooth = 0;

        DoubleMatrix1D tlam = lambda.copy().assign(Functions.mult(t));

        //Constructor copies and checks dimension
        //par is a copy so we can update it
        MGMParams par = new MGMParams(X, p, lsum);

        //penbeta = t(1).*(wv(1:p)'*wv(1:p));
        //betascale=zeros(size(beta));
        //betascale=max(0,1-penbeta./abs(beta));
        DoubleMatrix2D weightMat = alg.multOuter(weights,
                weights, null);
        DoubleMatrix2D betaWeight = weightMat.viewPart(0, 0, p, p);
        DoubleMatrix2D betascale = betaWeight.copy().assign(Functions.mult(-tlam.get(0)));
        DoubleMatrix2D absBeta = par.beta.copy().assign(Functions.abs);
        betascale.assign(absBeta, Functions.div);
        betascale.assign(Functions.plus(1));
        betascale.assign(Functions.max(0));

        double betaNorms  = 0;

        //beta=beta.*betascale;
        //par.beta.assign(betascale, Functions.mult);
        for(int i= 0; i < p; i++){
            for(int j = 0; j < p; j++){
                double curVal =  par.beta.get(i,j);
                if(curVal !=0){
                    curVal=curVal * betascale.get(i,j);
                    par.beta.set(i,j,curVal);
                    betaNorms += Math.abs(betaWeight.get(i,j)*curVal);
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
        for(int i = 0; i < p; i++){
            for(int j = 0; j < lcumsum.length-1; j++){
                DoubleMatrix1D tempVec = par.theta.viewColumn(i).viewPart(lcumsum[j], l[j]);
                //double thetaScale = Math.max(0, 1 - tlam.get(1)*weightMat.get(i, p+j)/Math.sqrt(alg.norm2(tempVec)));
                double foo = norm2(tempVec);
                double thetaScale = Math.max(0, 1 - tlam.get(1) * weightMat.get(i, p+j)/norm2(tempVec));
                tempVec.assign(Functions.mult(thetaScale));
                thetaNorms += weightMat.get(i, p+j)*Math.sqrt(alg.norm2(tempVec));
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
        for(int i = 0; i < lcumsum.length-1; i++){
            for(int j = i+1; j < lcumsum.length-1; j++){
                DoubleMatrix2D tempMat = par.phi.viewPart(lcumsum[i], lcumsum[j], l[i], l[j]);

                //not sure why this isnt Frobenius norm...
                //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.norm2(tempMat));
                double phiScale = Math.max(0, 1 - tlam.get(2) * weightMat.get(p + i,p+j)/norm2(tempMat));
                //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.normF(tempMat));
                tempMat.assign(Functions.mult(phiScale));
                phiNorms += weightMat.get(p+i,p+j)*alg.normF(tempMat);
            }
        }

        pX.assign(par.toMatrix1D());
        return lambda.get(0)*betaNorms + lambda.get(1)*thetaNorms + lambda.get(2)*phiNorms;
    }

        /*public Matrix compute(double t, Matrix X){
            double[][] out = new double[1][];
            out[0] = computeColt(t, factory1D.make(X.getData()[0])).toArray();
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
     *  Learn MGM traditional way with objective function tolerance. Recommended for inference applications that need
     *  accurate pseudolikelihood
     *
     * @param epsilon tolerance in change of objective function
     * @param iterLimit iteration limit
     */
    public void learn(double epsilon, int iterLimit){
        ProximalGradient pg = new ProximalGradient();
        setParams(new MGMParams(pg.learnBackTrack(this, params.toMatrix1D(), epsilon, iterLimit), p, lsum));
    }

    /**
     *  Learn MGM using edge convergence using default 3 iterations of no edge changes. Recommended when we only care about
     *  edge existence.
     *
     * @param iterLimit
     */
    public void learnEdges(int iterLimit){
        ProximalGradient pg = new ProximalGradient(.5, .9, true);
        setParams(new MGMParams(pg.learnBackTrack(this, params.toMatrix1D(), 0.0, iterLimit), p, lsum));
    }

    /**
     *  Learn MGM using edge convergence using edgeChangeTol (see ProximalGradient for documentation). Recommended when we only care about
     *  edge existence.
     *
     * @param iterLimit
     * @param edgeChangeTol
     */
    public void learnEdges(int iterLimit, int edgeChangeTol){
        ProximalGradient pg = new ProximalGradient(.5, .9, true);
        pg.setEdgeChangeTol(edgeChangeTol);
        setParams(new MGMParams(pg.learnBackTrack(this, params.toMatrix1D(), 0.0, iterLimit), p, lsum));
    }

    /**
     * Converts MGM object to Graph object with edges if edge parameters are non-zero. Loses all edge param information
     *
     * @return
     */
    public Graph graphFromMGM(){

        //List<Node> variables = getVariables();
        Graph g = new EdgeListGraph(variables);

        for (int i = 0; i < p; i++) {
            for (int j = i+1; j < p; j++) {
                double v1 = params.beta.get(i, j);

                if (Math.abs(v1)>0) {
                    if (!g.isAdjacentTo(variables.get(i), variables.get(j))) {
                        g.addUndirectedEdge(variables.get(i), variables.get(j));
                    }
                }
            }
        }

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < q; j++) {
                double v1 = params.theta.viewColumn(i).viewPart(lcumsum[j], l[j]).copy().assign(Functions.abs).zSum();

                if (v1>0) {
                    if (!g.isAdjacentTo(variables.get(i), variables.get(p+j))) {
                        g.addUndirectedEdge(variables.get(i), variables.get(p+j));
                    }
                }
            }
        }

        for (int i = 0; i < q; i++) {
            for (int j = i+1; j < q; j++) {
                double v1 = params.phi.viewPart(lcumsum[i], lcumsum[j], l[i], l[j]).copy().assign(Functions.abs).zSum();

                if (v1>0) {
                    if (!g.isAdjacentTo(variables.get(p+i), variables.get(p+j))) {
                        g.addUndirectedEdge(variables.get(p+i), variables.get(p+j));
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
    public DoubleMatrix2D adjMatFromMGM(){
        //List<Node> variables = getVariables();
        DoubleMatrix2D outMat = DoubleFactory2D.dense.make(p+q,p+q);

        outMat.viewPart(0,0,p,p).assign(params.beta.copy().assign(alg.transpose(params.beta), Functions.plus));

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < q; j++) {
                double val = norm2(params.theta.viewColumn(i).viewPart(lcumsum[j], l[j]));
                outMat.set(i, p + j, val);
                outMat.set(p + j, i, val);
            }
        }

        for (int i = 0; i < q; i++) {
            for (int j = i+1; j < q; j++) {
                double val = alg.normF(params.phi.viewPart(lcumsum[i], lcumsum[j], l[i], l[j]));
                outMat.set(p+i,p+j,val);
                outMat.set(p+j,p+i,val);
            }
        }

        //order the adjmat to be the same as the original DataSet variable ordering
        if(initVariables!=null) {
            int[] varMap = new int[p+q];
            for(int i = 0; i < p+q; i++){
                varMap[i] = variables.indexOf(initVariables.get(i));
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
    public Graph search(){
        long startTime = System.currentTimeMillis();
        learnEdges(1000); //unlikely to hit this limit
        elapsedTime = System.currentTimeMillis() - startTime;
        return graphFromMGM();
    }

    /**
     * Return time of execution for learning.
     * @return
     */
    public long getElapsedTime(){
        return elapsedTime;
    }


    /*
     * PRIVATE UTILS
     */
    //Utils
    //sum rows together if marg == 1 and cols together if marg == 2
    //Using row-major speeds up marg=1 5x
    private static DoubleMatrix1D margSum(DoubleMatrix2D mat, int marg){
        int n = 0;
        DoubleMatrix1D vec = null;
        DoubleFactory1D fac = DoubleFactory1D.dense;

        if(marg==1){
            n = mat.columns();
            vec = fac.make(n);
            for (int j = 0; j < mat.rows(); j++){
                for (int i = 0; i < n; i++){
                    vec.setQuick(i, vec.getQuick(i) + mat.getQuick(j,i));
                }
            }
        } else if (marg ==2){
            n = mat.rows();
            vec = fac.make(n);
            for (int i = 0; i < n; i++) {
                vec.setQuick(i, mat.viewRow(i).zSum());
            }
        }

        return vec;
    }

    //zeros out everthing below di-th diagonal
    public static DoubleMatrix2D upperTri(DoubleMatrix2D mat, int di){
        for(int i = Math.max(-di + 1, 0); i < mat.rows(); i++){
            for(int j = 0; j < Math.min(i + di, mat.rows()); j++){
                mat.set(i,j,0);
            }
        }

        return mat;
    }

    //zeros out everthing above di-th diagonal
    private static DoubleMatrix2D lowerTri(DoubleMatrix2D mat, int di){
        for(int i = 0; i < mat.rows() - Math.max(di + 1, 0); i++){
            for(int j = Math.max(i + di + 1, 0); j <  mat.rows(); j++){
                mat.set(i,j,0);
            }
        }

        return mat;
    }

    // should move somewhere else...
    private static double norm2(DoubleMatrix2D mat){
        //return Math.sqrt(mat.copy().assign(Functions.pow(2)).zSum());
        Algebra al = new Algebra();

        //norm found by svd so we need rows >= cols
        if(mat.rows() < mat.columns()){
            return al.norm2(al.transpose(mat));
        }
        return al.norm2(mat);
    }

    private static double norm2(DoubleMatrix1D vec){
        //return Math.sqrt(vec.copy().assign(Functions.pow(2)).zSum());
        return Math.sqrt(new Algebra().norm2(vec));
    }

    private static void runTests1(){
        try {
            //DoubleMatrix2D xIn = DoubleFactory2D.dense.make(loadDataSelect("/Users/ajsedgewick/tetrad/test_data", "med_test_C.txt"));
            //DoubleMatrix2D yIn = DoubleFactory2D.dense.make(loadDataSelect("/Users/ajsedgewick/tetrad/test_data", "med_test_D.txt"));
            //String path = MGM.class.getResource("test_data").getPath();
            String path = "/Users/ajsedgewick/tetrad_master/tetrad/tetrad-lib/src/main/java/edu/pitt/csb/mgm/test_data";
            System.out.println(path);
            DoubleMatrix2D xIn = DoubleFactory2D.dense.make(MixedUtils.loadDelim(path, "med_test_C.txt").getDoubleData().toArray());
            DoubleMatrix2D yIn = DoubleFactory2D.dense.make(MixedUtils.loadDelim(path, "med_test_D.txt").getDoubleData().toArray());
            int[] L = new int[24];
            Node[] vars = new Node[48];
            for(int i = 0; i < 24; i++){
                L[i] = 2;
                vars[i] = new ContinuousVariable("X" + i);
                vars[i+24] = new DiscreteVariable("Y" + i);
            }

            double lam = .2;
            MGM model = new MGM(xIn, yIn, new ArrayList<Node>(Arrays.asList(vars)), L, new double[]{lam, lam, lam});
            MGM model2 = new MGM(xIn, yIn, new ArrayList<Node>(Arrays.asList(vars)), L, new double[]{lam, lam, lam});

            System.out.println("Weights: " + Arrays.toString(model.weights.toArray()));

            DoubleMatrix2D test = xIn.copy();
            DoubleMatrix2D test2 = xIn.copy();
            long t = System.currentTimeMillis();
            for(int i=0; i<50000; i++) {
                test2 = xIn.copy();
                test.assign(test2);
            }
            System.out.println("assign Time: " + (System.currentTimeMillis() - t));

            t = System.currentTimeMillis();
            double[][] xArr = xIn.toArray();
            for(int i=0; i<50000; i++) {
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
            System.out.println("Orig Time: " + (System.currentTimeMillis()-t));

            System.out.println("nll: " + model.smoothValue(model.params.toMatrix1D()));
            System.out.println("reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));

            System.out.println("params:\n" + model.params);
            System.out.println("adjMat:\n" + model.adjMatFromMGM());


        } catch (IOException ex){
            ex.printStackTrace();
        }
    }

    /**
     * test non penalty use cases
     */
    private static void runTests2(){
        Graph g = GraphConverter.convert("X1-->X2,X3-->X2,X4-->X5");
        //simple graph pm im gen example

        HashMap<String, Integer> nd = new HashMap<>();
        nd.put("X1", 0);
        nd.put("X2", 0);
        nd.put("X3", 4);
        nd.put("X4", 4);
        nd.put("X5", 4);

        g = MixedUtils.makeMixedGraph(g, nd);

        GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(g, "Split(-1.5,-.5,.5,1.5)");
        System.out.println(pm);

        GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);
        System.out.println(im);

        int samps = 1000;
        DataSet ds = im.simulateDataAvoidInfinity(samps, false);
        ds = MixedUtils.makeMixedData(ds, nd);
        //System.out.println(ds);

        double lambda = 0;
        MGM model = new MGM(ds, new double[]{lambda, lambda, lambda});

        System.out.println("Init nll: " + model.smoothValue(model.params.toMatrix1D()));
        System.out.println("Init reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));

        model.learn(1e-8,1000);

        System.out.println("Learned nll: " + model.smoothValue(model.params.toMatrix1D()));
        System.out.println("Learned reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));

        System.out.println("params:\n" + model.params);
        System.out.println("adjMat:\n" + model.adjMatFromMGM());
    }

    public static void main(String[] args){
        runTests1();
    }

}

