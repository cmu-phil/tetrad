/////////////////////////////////////////////////////////////////////////////////
//// For information as to what this class does, see the Javadoc, below.       //
//// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
//// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
//// Ramsey, and Clark Glymour.                                                //
////                                                                           //
//// This program is free software; you can redistribute it and/or modify      //
//// it under the terms of the GNU General Public License as published by      //
//// the Free Software Foundation; either version 2 of the License, or         //
//// (at your option) any later version.                                       //
////                                                                           //
//// This program is distributed in the hope that it will be useful,           //
//// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
//// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
//// GNU General Public License for more details.                              //
////                                                                           //
//// You should have received a copy of the GNU General Public License         //
//// along with this program; if not, write to the Free Software               //
//// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
/////////////////////////////////////////////////////////////////////////////////
//
//package edu.cmu.tetrad.search;
//
//import Jama.Matrix;
//import edu.cmu.tetrad.data.DataSet;
//import edu.cmu.tetrad.data.ICovarianceMatrix;
//import edu.cmu.tetrad.graph.Node;
//import edu.cmu.tetrad.util.DepthChoiceGenerator;
//import jgpml.GaussianProcess;
//import jgpml.covariancefunctions.CovLINone;
//
//import java.io.PrintStream;
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//
///**
// * Implements the continuous BIC score for FGES.
// *
// * @author Joseph Ramsey
// */
//public class SemGpScore implements Score {
//
//    private Matrix dataSet;
//
//    // The variables of the covariance matrix.
//    private List<Node> variables;
//
//    // The sample size of the covariance matrix.
//    private int sampleSize;
//
//    // The penalty penaltyDiscount.
//    private double penaltyDiscount = 2.0;
//
//    // True if linear dependencies should return NaN for the score, and hence be
//    // ignored by FGES
//    private boolean ignoreLinearDependent = false;
//
//    // The printstream output should be sent to.
//    private PrintStream out = System.out;
//
//    // True if verbose output should be sent to out.
//    private boolean verbose = false;
//    private Set<Integer> forbidden = new HashSet<>();
//    private final double logn;
//
//    /**
//     * Constructs the score using a covariance matrix.
//     */
//    public SemGpScore(DataSet dataSet) {
//        if (dataSet == null) {
//            throw new NullPointerException();
//        }
//
//        this.dataSet = new Matrix(dataSet.getDoubleData().toArray());
//        this.variables = dataSet.getVariable();
//        this.sampleSize = dataSet.getNumRows();
//        this.penaltyDiscount = 2;
//        logn = Math.log(sampleSize);
//    }
//
//    /**
//     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
//     */
//    public double localScore(int i, int... parents) {
//        for (int p : parents) if (forbidden.contains(p)) return Double.NaN;
//
////        if (parents.length == 0) return localScore(i);
////        else if (parents.length == 1) return localScore(i, parents[0]);
//
//        double residualVariance = covariances.get(i, i);
//        int n = getSampleSize();
//        int p = parents.length;
//        Matrix covxx = getSelection1(covariances, parents);
//
//        try {
//            GaussianProcess gp  = new GaussianProcess(new CovLINone());
//
//            gp.
//
//
//            Matrix covxxInv = covxx.inverse();
//
//            Matrix covxy = getSelection2(covariances, parents, i);
//            Matrix b = covxxInv.times(covxy);
//
//            double dot = 0.0;
//
//            for (int j = 0; j < covxy.getRowDimension(); j++) {
//                for (int k = 0; k < covxy.getColumnDimension(); k++) {
//                    dot += covxy.get(j, k) * b.get(j, k);
//                }
//            }
//
//            residualVariance -= dot; //covxy.dotProduct(b);
//
//            if (residualVariance <= 0) {
//                if (isVerbose()) {
//                    out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / covariances.get(i, i)));
//                }
//                return Double.NaN;
//            }
//
//            double c = getPenaltyDiscount();
//            return score(residualVariance, n, logn, p, c);
//        } catch (Exception e) {
//            boolean removedOne = true;
//
//            while (removedOne) {
//                List<Integer> _parents = new ArrayList<>();
//                for (int y = 0; y < parents.length; y++) _parents.add(parents[y]);
//                _parents.removeAll(forbidden);
//                parents = new int[_parents.size()];
//                for (int y = 0; y < _parents.size(); y++) parents[y] = _parents.get(y);
//                removedOne = printMinimalLinearlyDependentSet(parents, covariances);
//            }
//
//            return Double.NaN;
//        }
//    }
//
//    @Override
//    public double localScoreDiff(int x, int y, int[] z) {
//        return localScore(y, append(z, x)) - localScore(y, z);
//    }
//
//    @Override
//    public double localScoreDiff(int x, int y) {
//        return localScore(y, x) - localScore(y);
//    }
//
//    private int[] append(int[] parents, int extra) {
//        int[] all = new int[parents.length + 1];
//        System.arraycopy(parents, 0, all, 0, parents.length);
//        all[parents.length] = extra;
//        return all;
//    }
//
//    /**
//     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
//     */
//    public double localScore(int i, int parent) {
//        return localScore(i, new int[]{parent});
//    }
//
//    /**
//     * Specialized scoring method for no parents. Used to speed up the effect edges search.
//     */
//    public double localScore(int i) {
//        return localScore(i, new int[0]);
//    }
//
//    /**
//     * True iff edges that cause linear dependence are ignored.
//     */
//    public boolean isIgnoreLinearDependent() {
//        return ignoreLinearDependent;
//    }
//
//    public void setIgnoreLinearDependent(boolean ignoreLinearDependent) {
//        this.ignoreLinearDependent = ignoreLinearDependent;
//    }
//
//    public void setOut(PrintStream out) {
//        this.out = out;
//    }
//
//    public double getPenaltyDiscount() {
//        return penaltyDiscount;
//    }
//
//    public int getSampleSize() {
//        return sampleSize;
//    }
//
//    @Override
//    public boolean isEffectEdge(double bump) {
//        return bump > 0;//-0.25 * getPenaltyDiscount() * Math.log(sampleSize);
//    }
//
//    public DataSet getDataModel() {
//        throw new UnsupportedOperationException();
//    }
//
//    public void setCorrErrorsAlpha(double penaltyDiscount) {
//        this.penaltyDiscount = penaltyDiscount;
//    }
//
//    public boolean isVerbose() {
//        return verbose;
//    }
//
//    public void setVerbose(boolean verbose) {
//        this.verbose = verbose;
//    }
//
//    @Override
//    public List<Node> getVariable() {
//        return variables;
//    }
//
//    @Override
//    public boolean isDiscrete() {
//        return false;
//    }
//
//    @Override
//    public double getAlternativePenalty() {
//        return penaltyDiscount;
//    }
//
//    @Override
//    public void setAlternativePenalty(double alpha) {
//        this.penaltyDiscount = alpha;
//    }
//
//    // Calculates the BIC score.
//    private double score(double residualVariance, int n, double logn, int p, double c) {
//        return -n * Math.log(residualVariance) - c * (p + 1) * logn;
//    }
//
//    private Matrix getSelection1(Matrix cov, int[] rows) {
//        return cov.getMatrix(rows, rows);
//    }
//
//    private Matrix getSelection2(Matrix cov, int[] rows, int k) {
//        return cov.getMatrix(rows, new int[]{k});
//    }
//
//    // Prints a smallest subset of parents that causes a singular matrix exception.
//    private boolean printMinimalLinearlyDependentSet(int[] parents, Matrix cov) {
//        List<Node> _parents = new ArrayList<>();
//        for (int p : parents) _parents.add(variables.get(p));
//
//        DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
//        int[] choice;
//
//        while ((choice = gen.next()) != null) {
//            int[] sel = new int[choice.length];
//            List<Node> _sel = new ArrayList<>();
//            for (int m = 0; m < choice.length; m++) {
//                sel[m] = parents[m];
//                _sel.add(variables.get(sel[m]));
//            }
//
//            Matrix m = cov.getMatrix(sel, sel);
//
//            try {
//                m.inverse();
//            } catch (Exception e2) {
//                forbidden.add(sel[0]);
//                out.println("### Linear dependence among variables: " + _sel);
//                out.println("### Removing " + _sel.get(0));
//                return true;
//            }
//        }
//
//        return false;
//    }
//
//    public void setVariables(List<Node> variables) {
////        covariances.setVariables(variables);
//        this.variables = variables;
//    }
//
//    @Override
//    public Node getVariable(String targetName) {
//        for (Node node : variables) {
//            if (node.getNode().equals(targetName)) {
//                return node;
//            }
//        }
//
//        return null;
//    }
//}
//
//
//
