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
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

        //TODO check data
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

        //row vector in matrix form for easy conversion to LAML matrix...
        //may be depreciated...
        public double[][] toVector(){
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

            //TODO make this better...
            //return DoubleFactory2D.dense.make(outVec.toArray(),1);
            double[][] outArr = new double[1][];
            outArr[0] = outVec.toArray();
            return outArr;
        }

        public DoubleMatrix1D toMatrix1D(){
            return DoubleFactory1D.dense.make(toVector()[0]);
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

    //
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
    public double logsumexp(DoubleMatrix1D x){
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

    private void makeDummy(){
        dDat = factory2D.make(n, lsum);
        for(int i = 0; i < q; i++){
            for(int j = 0; j < l[i]; j++){
                dDat.viewColumn(lcumsum[i]+j).assign(yDat.viewColumn(i).copy().assign(Functions.equals(j+1)));
            }
        }
    }

    private void fixData(){
        //TODO ydat needs to be converted to levels reliably for now, just check if its 0 index and convert to 1
        if(StatUtils.min(flatten(yDat).toArray())==0){
            yDat.assign(Functions.plus(1.0));
        }


        //z-score columns of X
        for(int i = 0; i < p; i++){
            xDat.viewColumn(i).assign(StatUtils.standardizeData(xDat.viewColumn(i).toArray()));
        }
    }

    // non-penalized -log(likelihood) this is the smooth function g(x) in prox gradient
    //public double smoothValue(MGMParams parIn){
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
        upperTri(par.phi,0);
        par.phi.assign(alg.transpose(par.phi), Functions.plus);

        // TODO LH ensure mats are upper triangular here, skipping for now

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

                // TODO: assert that yDat is only ints
                catloss -= curRow.get((int) yDat.get(k, i) - 1);
                catloss += logsumexp(curRow);
            }
        }

        return (sqloss + catloss)/((double) n);
    }

    // non-penalized -log(likelihood) this is the smooth function g(x) in prox gradient
    // this overloaded version calculates both nll and the smooth gradient at the same time
    // any value in gradOut will be replaces by the new calculations
    //public double smooth(MGMParams parIn, MGMParams gradOut){
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
        upperTri(par.phi,0);
        par.phi.assign(alg.transpose(par.phi), Functions.plus);

        // TODO LH ensure mats are upper triangular here, skipping for now

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

                // TODO: assert that yDat is only ints
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

    //public double nonSmoothValue(MGMParams par){
    public double nonSmoothValue(DoubleMatrix1D parIn){
        //DoubleMatrix1D tlam = lambda.copy().assign(Functions.mult(t));
        //TODO check dim of X...
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


    //public MGMParams smoothGradient(MGMParams parIn){
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



    public DoubleMatrix1D proximalOperator(double t, DoubleMatrix1D X) {
            //System.out.println("PROX with t = " + t);

            DoubleMatrix1D tlam = lambda.copy().assign(Functions.mult(t));
            //TODO check dim of X...
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

                    //TODO figure out why this isnt Frobenius norm...
                    //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.norm2(tempMat));
                    double phiScale = Math.max(0, 1 - tlam.get(2) * weightMat.get(p + i,p+j)/norm2(tempMat));
                    //double phiScale = Math.max(0, 1-tlam.get(2)*weightMat.get(p+i,p+j)/alg.normF(tempMat));
                    tempMat.assign(Functions.mult(phiScale));
                }
            }
        return par.toMatrix1D();
    }

    //values in pX will be replaced by result
    public double nonSmooth(double t, DoubleMatrix1D X, DoubleMatrix1D pX) {
        //System.out.println("PROX with t = " + t);
        double nonSmooth = 0;

        DoubleMatrix1D tlam = lambda.copy().assign(Functions.mult(t));
        //TODO check dim of X...
        //par is a copy so we can update it
        MGMParams par = new MGMParams(X.copy(), p, lsum);

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

                //TODO figure out why this isnt Frobenius norm...
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


    //
    public void learn(double epsilon, int iterLimit){
        ProximalGradient pg = new ProximalGradient();
        setParams(new MGMParams(pg.learnBackTrack(this, params.toMatrix1D(), epsilon, iterLimit), p, lsum));
    }


    public void learnEdges(int iterLimit){
        ProximalGradient pg = new ProximalGradient(.5, .9, true);
        setParams(new MGMParams(pg.learnBackTrack(this, params.toMatrix1D(), 0.0, iterLimit), p, lsum));
    }


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

    //This converts edge parameters to a single value
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

    public Graph search(){
        long startTime = System.currentTimeMillis();
        learnEdges(1000); //unlikely to hit this limit
        elapsedTime = System.currentTimeMillis() - startTime;
        return graphFromMGM();
    }

    public long getElapsedTime(){
        return elapsedTime;
    }

    //Utils
    //sum rows together if marg == 1 and cols together if marg == 2
    public static DoubleMatrix1D margSum(DoubleMatrix2D mat, int marg){
        int n = 0;
        DoubleMatrix1D vec = null;
        DoubleFactory1D fac = DoubleFactory1D.dense;
        Algebra alg = new Algebra();

        //add all rows together output is 1 x ncolumns)
        //TODO test if faster than forloop
        if(marg==1){
            n = mat.rows();
            vec = fac.make(n,1.0);
            vec = alg.mult(alg.transpose(mat), vec);
        } else if (marg ==2){
            n = mat.columns();
            vec = fac.make(n,1.0);
            vec = alg.mult(mat, vec);
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
    public static DoubleMatrix2D lowerTri(DoubleMatrix2D mat, int di){
        for(int i = 0; i < mat.rows() - Math.max(di + 1, 0); i++){
            for(int j = Math.max(i + di + 1, 0); j <  mat.rows(); j++){
                mat.set(i,j,0);
            }
        }

        return mat;
    }

    //
    public static double norm2(DoubleMatrix2D mat){
        //return Math.sqrt(mat.copy().assign(Functions.pow(2)).zSum());
        Algebra al = new Algebra();

        //norm found by svd so we need rows >= cols
        if(mat.rows() < mat.columns()){
            return al.norm2(al.transpose(mat));
        }
        return al.norm2(mat);
    }

    public static double norm2(DoubleMatrix1D vec){
        //return Math.sqrt(vec.copy().assign(Functions.pow(2)).zSum());
        return Math.sqrt(new Algebra().norm2(vec));
    }

    public static void main(String[] args){
        try {
            //DoubleMatrix2D xIn = DoubleFactory2D.dense.make(loadDataSelect("/Users/ajsedgewick/tetrad/test_data", "med_test_C.txt"));
            //DoubleMatrix2D yIn = DoubleFactory2D.dense.make(loadDataSelect("/Users/ajsedgewick/tetrad/test_data", "med_test_D.txt"));
            String path = MGM.class.getResource("test_data").getPath();
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

            /*DoubleMatrix2D test = model.alg.transpose(xIn.copy());
            long t = System.currentTimeMillis();
            for(int i=0; i<100; i++) {
                model.alg.mult(test, xIn);
            }
            System.out.println("colt mult Time: " + (System.currentTimeMillis() - t));

            TetradMatrix tx = new TetradMatrix(xIn.toArray());
            TetradMatrix ttx = tx.transpose();

            t = System.currentTimeMillis();
            for(int i=0; i<100; i++) {
                tx.times(ttx);
            }
            System.out.println("tetrad mult Time: " + (System.currentTimeMillis() - t));*/
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


            System.out.println("Inf Test: " + 1.0/Double.POSITIVE_INFINITY);

            System.out.println("Init nll: " + model.smoothValue(model.params.toMatrix1D()));
            System.out.println("Init reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));
            /*System.out.println("Weights: " + Arrays.toString(model.weights.toArray()));
            //System.out.println("Init Params: " + model.params);
            //System.out.println("max(0,nan)" + Math.max(0,Double.NaN));
            //System.out.println("dDat: " + model.dDat.toString());

            //model.learn(1e-7, 700);
            //Matrix X = new DenseMatrix(model.params.toVector());
            //System.out.println("X params:\n" + new MGMParams(DoubleFactory1D.dense.make(X.getData()[0]), 5, 10));

            //Matrix G = new DenseMatrix(model.gradient().toVector());
            //System.out.println("G norm test: " + Matlab.norm(G));
            //model.setParams(model.gradient());
            MGMParams grad = model.gradient(model.params);
            System.out.println("grad nll: " + model.negLogLikelihood(grad));
            System.out.println("grad reg term: " + model.regTerm(grad));
            //System.out.println("grad params:\n" + grad);

            DoubleMatrix1D X = DoubleFactory1D.dense.make(model.params.toVector()[0]);
            DoubleMatrix1D G = DoubleFactory1D.dense.make(grad.toVector()[0]);

            ProxMGM pm = model.new ProxMGM();
            MGMParams X2 = new MGMParams(pm.computeColt(1, X.copy().assign(G.copy(), Functions.minus)), 24, 48);
            System.out.println("X2 nll: " + model.negLogLikelihood(X2));
            System.out.println("X2 reg term: " + model.regTerm(X2));*/
            //System.out.println("X2 params:\n" + X2);



            //System.out.println("triu: " + upperTri(model.gradient().beta.copy(),-1));
            //System.out.println("tril: " + lowerTri(model.gradient().beta.copy(), -1));

            //System.out.println("X+.1G params:\n" + new MGMParams(DoubleFactory1D.dense.make(X.plus(G.times(.1)).getData()[0]), 5, 10));

            //ProxMGM testProx = model.new ProxMGM();
            //Matrix newP = testProx.compute(1, G);
            //Matrix newP = testProx.compute(.9, X);
            //System.out.println("prox G t=1:\n" + new MGMParams(DoubleFactory1D.dense.make(newP.getData()[0]), 5, 10));
            //Matrix newP = new DenseMatrix(1, X.getColumnDimension());;
            //testProx.compute(newP, .9, X.minus(G.times(.9)));
            //System.out.println("newP is null? " + (newP==null));

            //model.setParams(new MGMParams(DoubleFactory1D.dense.make(newP.getData()[0]), 5, 10));
            //System.out.println("params:\n" + model.params);
            //System.out.println("prox .9 nll: " + model.negLogLikelihood());
            //System.out.println("prox .9 reg term: " + model.regTerm());

            t = System.currentTimeMillis();
            model.learnEdges(700);
            //model.learn(1e-7, 700);
            System.out.println("Orig Time: " + (System.currentTimeMillis()-t));

            System.out.println("nll: " + model.smoothValue(model.params.toMatrix1D()));
            System.out.println("reg term: " + model.nonSmoothValue(model.params.toMatrix1D()));

            System.out.println("params:\n" + model.params);
            System.out.println("adjMat:\n" + model.adjMatFromMGM());

            //System.out.println("Init nll: " + model2.negLogLikelihood(model2.params));
            //System.out.println("Init reg term: " + model2.regTerm(model2.params));

            /*t = System.currentTimeMillis();
            model2.learnBackTrack2(1e-7, 700);
            System.out.println("New Time: " + (System.currentTimeMillis()-t));

            System.out.println("nll: " + model2.negLogLikelihood(model2.params));
            System.out.println("reg term: " + model2.regTerm(model2.params));*/

            //System.out.println("params:\n" + model.params);
            //System.out.println("new params: " + Arrays.toString(newP.getData()[0]));
            //System.out.println("params: " + Arrays.toString(model.params.toVector()[0]));
            //System.out.println("grad: " + Arrays.toString(model.gradient().toVector()[0]));

        } catch (IOException ex){
            ex.printStackTrace();
        }



    }

        /*
     ***Learning moved to ProximalGradient class
    //FISTA update step
    public void myFISTAOld(DoubleMatrix1D Yk, DoubleMatrix1D Gk, DoubleMatrix1D Xk, DoubleMatrix1D Xold, double t, double L, ProxMGM pm){
        //double t = 1;
        //double L = 2;
        //DoubleMatrix1D Yt = Yk.copy();
        //DoubleMatrix1D Gt = Gk.copy();
        DoubleMatrix1D Xnew = pm.computeColt(1.0 / L, Yk.copy().assign(Gk.copy().assign(Functions.mult(1.0 / L)), Functions.minus));

        //double tn = (1.0 + Math.sqrt(1.0 + 4.0*Math.pow(t,2)))/2.0;

        //assign yk+1 to yk
        Yk.assign(Xnew.copy().assign(Xold.copy(), Functions.minus).assign(Functions.mult(t)));
        //Yk.assign(Xnew.copy().assign(Xk, Functions.minus).assign(Functions.mult((t - 1.0) / tn)));
        //Yk.assign(Xnew.copy().assign(Xk, Functions.minus).assign(Functions.mult((t - 2.0) / (t + 1.0))));
        //Yk.assign(Xnew.copy().assign(Xk, Functions.minus).assign(Functions.mult((t) / (t + 3.0))));
        Yk.assign(Xnew.copy(), Functions.plus);

        Xk.assign(Xnew);

        //return tn;
    }

    //FISTA update step
    public void myFISTA(DoubleMatrix1D Yk, DoubleMatrix1D Gk, DoubleMatrix1D Xk, DoubleMatrix1D Xold, double t, double L, ProxMGM pm){
        //double t = 1;
        //double L = 2;
        //DoubleMatrix1D Yt = Yk.copy();
        //DoubleMatrix1D Gt = Gk.copy();
        Yk.assign(Xk.copy().assign(Xold.copy(), Functions.minus).assign(Functions.mult(t)));
        Yk.assign(Xk.copy(), Functions.plus);
        Gk.assign(factory1D.make(gradient(new MGMParams(Yk, p, lsum)).toVector()[0]));
        Xk.assign(pm.computeColt(1.0 / L, Yk.copy().assign(Gk.copy().assign(Functions.mult(1.0 / L)), Functions.minus)));

        //double tn = (1.0 + Math.sqrt(1.0 + 4.0*Math.pow(t,2)))/2.0;

        //assign yk+1 to yk

        //Yk.assign(Xnew.copy().assign(Xk, Functions.minus).assign(Functions.mult((t - 1.0) / tn)));
        //Yk.assign(Xnew.copy().assign(Xk, Functions.minus).assign(Functions.mult((t - 2.0) / (t + 1.0))));
        //Yk.assign(Xnew.copy().assign(Xk, Functions.minus).assign(Functions.mult((t) / (t + 3.0))));


        //Xk.assign(Xnew);

        //return tn;
    }

    //run FISTA with constant step size
    public void learnConst(double epsilon, int iterLimit){
        ProxMGM pm = new ProxMGM();
        DoubleMatrix1D X = factory1D.make(params.toVector()[0]);
        DoubleMatrix1D Y = X.copy();
        DoubleMatrix1D G = factory1D.make(gradient(params).toVector()[0]);
        int iterCount = 1;
        //double t = .5;//has to do with momentum
        double t = (iterCount-2)/(iterCount+3);
        double L = 4;
        while(true){
            DoubleMatrix1D Xold = X.copy();
            //X and Y are updated in place
            //myFISTA(Y, G, X, iterCount+2,pm);
            myFISTAOld(Y, G, X, Xold, t, L, pm);
            t = iterCount/(iterCount+3);
            MGMParams Xpar = new MGMParams(X.copy(), p, lsum);

            //squared or not?
            double dx = alg.norm2(Xold.assign(X, Functions.minus))/alg.norm2(X);
            if(dx < epsilon){
                System.out.println("Converged at iter: " + iterCount + " with |dx|/|x|: " + dx + " < epsilon: " + epsilon);
                break;
            }

            if(iterCount%10 == 0){
                System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " nll: " + negLogLikelihood(Xpar) + " reg: " + regTerm(Xpar));
                //System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " nll: " + negLogLikelihood(params) + " reg: " + regTerm(params));
            }
            //setParams(new MGMParams(X.copy(), p, lsum));
            //setParams(Xpar);
            G = factory1D.make(gradient(new MGMParams(Y, p, lsum)).toVector()[0]);
            //setParams(Xpar);

            //System.out.println("t: " + t);
            //System.out.println("Params: " + params);

            iterCount++;
            if(iterCount >= iterLimit){
                System.out.println("Iter limit reached");
                break;
            }
        }
        setParams(new MGMParams(X, p, lsum));
    }

    //run FISTA with step size backtracking
    public void learnBackTrack(double epsilon, int iterLimit) {
        ProxMGM pm = new ProxMGM();
        DoubleMatrix1D X = pm.computeColt(1.0, factory1D.make(params.toVector()[0]));
        DoubleMatrix1D Y = X.copy();
        DoubleMatrix1D Z = X.copy();
        DoubleMatrix1D GrY = factory1D.make(gradient(params).toVector()[0]);
        int iterCount = 0;
        int printIter = 50;
        //double t = .5;//has to do with momentum
        double theta = Double.POSITIVE_INFINITY;
        double thetaOld = theta;
        double thetaTerm = 0;
        double L = 1.0;
        double Lold = L;
        double alpha = .9;
        double eta = 2.0;
        double gamma = 1e-10;
        boolean backtrackSwitch = true;
        double dx = Double.POSITIVE_INFINITY;
        double Fx = Double.POSITIVE_INFINITY;;
        double Gx = Double.POSITIVE_INFINITY;;
        double obj;
        MGMParams Xpar;

        while (true) {
            Lold = L;
            L = L*alpha;
            thetaOld = theta;
            DoubleMatrix1D Xold = X.copy();
            obj = Fx + Gx;

            while(true) {
                theta = 2.0/(1.0+Math.sqrt(1.0+(4.0*L)/(Lold*Math.pow(thetaOld,2))));
                if(theta < 1){
                    Y.assign(Xold.copy().assign(Functions.mult(1-theta)));
                    Y.assign(Z.copy().assign(Functions.mult(theta)), Functions.plus);
                }

                //System.out.println("Iter: " + iterCount +  " 1/L: " + (1.0/L) + " theta " + theta + " obj " + obj + " Fx " + Fx + " Gx " + Gx);
                //DoubleMatrix1D Yold = Y.copy();
                //X and Y are updated in place
                //myFISTA(Y, G, X, iterCount+2,pm);
                //" L: " + L

                //from tfocs, increase step size each iter

                //myFISTA(Y, G, X, Z, theta, L, pm);
                GrY.assign(factory1D.make(gradient(new MGMParams(Y, p, lsum)).toVector()[0]));
                X.assign(pm.computeColt(1.0 / L, Y.copy().assign(GrY.copy().assign(Functions.mult(1.0 / L)), Functions.minus)));

                Xpar = new MGMParams(X.copy(), p, lsum);
                MGMParams Ypar = new MGMParams(Y.copy(), p, lsum);

                //squared or not?
                //double dx = norm2(Xold.assign(X, Functions.minus)) / Math.max(1,norm2(X));

                //setParams(new MGMParams(X, p, lsum));
                //G = factory1D.make(gradient(Ypar).toVector()[0]);

                //backtracking test potential new X

                //DoubleMatrix1D Xnew = pm.computeColt(1.0 / L, Y.copy().assign(G.assign(Functions.mult(1.0 / L)), Functions.minus));
                //MGMParams XnewParams = new MGMParams(Xnew, p, lsum);
                //double gXnew = regTerm(XnewParams);
                //double Fx = negLogLikelihood(XnewParams);
                Gx = regTerm(Xpar);
                Fx = negLogLikelihood(Xpar);

                double Fy = negLogLikelihood(Ypar);
                //DoubleMatrix1D YmX = Y.copy().assign(Xnew, Functions.minus);
                //DoubleMatrix1D XmY = Xnew.copy().assign(Y, Functions.minus);
                DoubleMatrix1D XmY = X.copy().assign(Y, Functions.minus);
                double normXY = alg.norm2(XmY);
                if(normXY==0)
                    break;

                double Qx;
                double LocalL;

                if(backtrackSwitch){
                    //System.out.println("Back Norm");
                    Qx = Fy + alg.mult(XmY, GrY) + (L / 2.0) * normXY;
                    LocalL = L + 2*Math.max(Fx - Qx, 0)/normXY;
                    backtrackSwitch =  Math.abs(Fy - Fx) >= gamma * Math.max(Math.abs(Fx), Math.abs(Fy));
                } else {
                    //System.out.println("Close Rule");
                    DoubleMatrix1D GrX = factory1D.make(gradient(Xpar).toVector()[0]);
                    //Fx = alg.mult(YmX, Gx.assign(G, Functions.minus));
                    //Qx = (L / 2.0) * alg.norm2(YmX);
                    LocalL = 2*alg.mult(XmY, GrX.assign(GrY, Functions.minus))/normXY;

                }
                //System.out.println("Iter: " + iterCount + " Fx: " + Fx + " Qx: " + Qx + " L : " + L );
                //if(-1e-8 <= Qx - Fx){
                //if(Fx <= Qx){
                //System.out.println("LocalL: " + LocalL + " L: " + L);
                if(LocalL <= L){
                    break;
                } else if (LocalL != Double.POSITIVE_INFINITY) {
                    L = LocalL;
                } else {
                    LocalL = L;
                }

                L = Math.max(LocalL, L*eta);

            }
            dx = norm2(X.copy().assign(Xold, Functions.minus)) / Math.max(1,norm2(X));
            if (dx < epsilon) {
                System.out.println("Converged at iter: " + iterCount + " with |dx|/|x|: " + dx + " < epsilon: " + epsilon);
                break;
            }

            //restart acceleration if objective got worse
            if(Fx + Gx > obj){
                theta = Double.POSITIVE_INFINITY;
                Y.assign(X.copy());
                Z.assign(X.copy());
            }else if(theta==1){
                Z.assign(X.copy());
            } else {
                Z.assign(X.copy().assign(Functions.mult(1 / theta)));
                Z.assign(Xold.copy().assign(Functions.mult(1 - (1.0 / theta))), Functions.plus);
            }

            int diffEdges = 0;
            for(int i =0; i<X.size(); i++){
                double a = X.get(i);
                double b = Xold.get(i);
                if(a!=0 &  b==0){
                    diffEdges++;
                } else if (a==0 & b!=0){
                    diffEdges++;
                }
            }

            if (iterCount % printIter == 0) {
                System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " normX: " + norm2(X) + " nll: " +
                        negLogLikelihood(Xpar) + " reg: " + regTerm(Xpar) + " DiffEdges: " + diffEdges);
                //System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " nll: " + negLogLikelihood(params) + " reg: " + regTerm(params));
            }
            //System.out.println("t: " + t);
            //System.out.println("Params: " + params);

            iterCount++;
            if (iterCount >= iterLimit) {
                System.out.println("Iter limit reached");
                break;
            }
        }
        setParams(new MGMParams(X, p, lsum));
    }

    //run FISTA with step size backtracking attempt to speed up
    public void learnBackTrack2(double epsilon, int iterLimit) {
        ProxMGM pm = new ProxMGM();
        DoubleMatrix1D X = pm.computeColt(1.0, factory1D.make(params.toVector()[0]));
        DoubleMatrix1D Y = X.copy();
        DoubleMatrix1D Z = X.copy();
        DoubleMatrix1D GrY = factory1D.make(gradient(params).toVector()[0]);
        DoubleMatrix1D GrX = factory1D.make(gradient(new MGMParams(X.copy(), p, lsum)).toVector()[0]);
        int iterCount = 0;
        int printIter = 50;
        //double t = .5;//has to do with momentum
        double theta = Double.POSITIVE_INFINITY;
        double thetaOld = theta;
        double thetaTerm = 0;
        double L = 1.0;
        double Lold = L;
        double alpha = .9;
        double eta = 2.0;
        double gamma = 1e-10;
        boolean backtrackSwitch = true;
        double dx = Double.POSITIVE_INFINITY;
        double Fx = Double.POSITIVE_INFINITY;
        double Gx = Double.POSITIVE_INFINITY;
        double Fy = Double.POSITIVE_INFINITY;
        double obj;
        MGMParams Xpar;
        MGMParams Ypar = new MGMParams(Y.copy(), p, lsum);;
        MGMParams tempPar;

        while (true) {
            Lold = L;
            L = L*alpha;
            thetaOld = theta;
            DoubleMatrix1D Xold = X.copy();
            obj = Fx + Gx;

            while(true) {
                theta = 2.0/(1.0+Math.sqrt(1.0+(4.0*L)/(Lold*Math.pow(thetaOld,2))));
                if(theta < 1){
                    Y.assign(Xold.copy().assign(Functions.mult(1-theta)));
                    Y.assign(Z.copy().assign(Functions.mult(theta)), Functions.plus);
                    Ypar = new MGMParams(Y.copy(), p, lsum);
                    //GrY = null;
                }


                //System.out.println("Iter: " + iterCount +  " 1/L: " + (1.0/L) + " theta " + theta + " obj " + obj + " Fx " + Fx + " Gx " + Gx);
                //DoubleMatrix1D Yold = Y.copy();
                //X and Y are updated in place
                //myFISTA(Y, G, X, iterCount+2,pm);
                //" L: " + L

                //from tfocs, increase step size each iter

                //myFISTA(Y, G, X, Z, theta, L, pm);
                //TODO is using nulls worth the tradeoff of re-allocating memory?
                //if(GrY == null) {

                //}
                //if(theta < 1) {
                    tempPar = new MGMParams();
                    Fy = negLogLikelihood(Ypar, tempPar);
                    GrY.assign(factory1D.make(tempPar.toVector()[0]));
                //}

                //X.assign(pm.computeColt(1.0 / L, Y.copy().assign(GrY.copy().assign(Functions.mult(1.0 / L)), Functions.minus)));
                Gx = pm.computeColt(1.0 / L, Y.copy().assign(GrY.copy().assign(Functions.mult(1.0 / L)), Functions.minus),X);
                //GrX = null;

                Xpar = new MGMParams(X.copy(), p, lsum);

                //squared or not?
                //double dx = norm2(Xold.assign(X, Functions.minus)) / Math.max(1,norm2(X));

                //setParams(new MGMParams(X, p, lsum));
                //G = factory1D.make(gradient(Ypar).toVector()[0]);

                //backtracking test potential new X

                //DoubleMatrix1D Xnew = pm.computeColt(1.0 / L, Y.copy().assign(G.assign(Functions.mult(1.0 / L)), Functions.minus));
                //MGMParams XnewParams = new MGMParams(Xnew, p, lsum);
                //double gXnew = regTerm(XnewParams);
                //double Fx = negLogLikelihood(XnewParams);
                //Gx = regTerm(Xpar);
                //Fx = negLogLikelihood(Xpar);
                if(backtrackSwitch){
                    Fx = negLogLikelihood(Xpar);
                } else {
                    tempPar = new MGMParams();
                    Fx = negLogLikelihood(Xpar, tempPar);
                    GrX.assign(factory1D.make(tempPar.toVector()[0]));
                }

                //DoubleMatrix1D YmX = Y.copy().assign(Xnew, Functions.minus);
                //DoubleMatrix1D XmY = Xnew.copy().assign(Y, Functions.minus);
                DoubleMatrix1D XmY = X.copy().assign(Y, Functions.minus);
                double normXY = alg.norm2(XmY);
                if(normXY==0)
                    break;

                double Qx;
                double LocalL;

                if(backtrackSwitch){
                    //System.out.println("Back Norm");
                    Qx = Fy + alg.mult(XmY, GrY) + (L / 2.0) * normXY;
                    LocalL = L + 2*Math.max(Fx - Qx, 0)/normXY;
                    backtrackSwitch =  Math.abs(Fy - Fx) >= gamma * Math.max(Math.abs(Fx), Math.abs(Fy));
                } else {
                    //System.out.println("Close Rule");

                    //it shouldn't be possible for GrX to be null here...
                    //if(GrX==null)
                        //GrX = factory1D.make(gradient(Xpar).toVector()[0]);
                    //Fx = alg.mult(YmX, Gx.assign(G, Functions.minus));
                    //Qx = (L / 2.0) * alg.norm2(YmX);
                    LocalL = 2*alg.mult(XmY, GrX.assign(GrY, Functions.minus))/normXY;

                }
                //System.out.println("Iter: " + iterCount + " Fx: " + Fx + " Qx: " + Qx + " L : " + L );
                //if(-1e-8 <= Qx - Fx){
                //if(Fx <= Qx){
                //System.out.println("LocalL: " + LocalL + " L: " + L);
                if(LocalL <= L){
                    break;
                } else if (LocalL != Double.POSITIVE_INFINITY) {
                    L = LocalL;
                } else {
                    LocalL = L;
                }

                L = Math.max(LocalL, L*eta);

            }

            int diffEdges = 0;
            for(int i =0; i<X.size(); i++){
                double a = X.get(i);
                double b = Xold.get(i);
                if(a!=0 &  b==0){
                    diffEdges++;
                } else if (a==0 & b!=0){
                    diffEdges++;
                }
            }

            dx = norm2(X.copy().assign(Xold, Functions.minus)) / Math.max(1,norm2(X));
            if (dx < epsilon) {
                System.out.println("Converged at iter: " + iterCount + " with |dx|/|x|: " + dx + " < epsilon: " + epsilon);
                System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " normX: " + norm2(X) + " nll: " +
                        negLogLikelihood(Xpar) + " reg: " + regTerm(Xpar) + " DiffEdges: " + diffEdges + " L: " + L);
                break;
            }

            //restart acceleration if objective got worse
            if(Fx + Gx > obj) {
                theta = Double.POSITIVE_INFINITY;
                Y.assign(X.copy());
                Ypar = new MGMParams(Xpar);
                Z.assign(X.copy());
                //Fy = Fx;
                //GrY.assign(GrX.copy());
            }else if(theta==1){
                Z.assign(X.copy());
            } else {
                Z.assign(X.copy().assign(Functions.mult(1 / theta)));
                Z.assign(Xold.copy().assign(Functions.mult(1 - (1.0 / theta))), Functions.plus);
            }


            if (iterCount % printIter == 0) {
                System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " normX: " + norm2(X) + " nll: " +
                        negLogLikelihood(Xpar) + " reg: " + regTerm(Xpar) + " DiffEdges: " + diffEdges + " L: " + L);
                //System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " nll: " + negLogLikelihood(params) + " reg: " + regTerm(params));
            }
            //System.out.println("t: " + t);
            //System.out.println("Params: " + params);

            iterCount++;
            if (iterCount >= iterLimit) {
                System.out.println("Iter limit reached");
                break;
            }
        }
        setParams(new MGMParams(X, p, lsum));
    }*/
}

