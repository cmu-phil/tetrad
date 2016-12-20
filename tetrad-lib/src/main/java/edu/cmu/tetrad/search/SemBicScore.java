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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.io.PrintStream;
import java.util.*;

import static java.lang.Math.PI;
import static java.lang.Math.log;

/**
 * Implements the continuous BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class SemBicScore implements Score {

    // The covariance matrix.
    private ICovarianceMatrix covariances;

    // The variables of the covariance matrix.
    private List<Node> variables;

    // The sample size of the covariance matrix.
    private int sampleSize;

    // The penalty penaltyDiscount.
    private double penaltyDiscount = 1.0;

    // True if linear dependencies should return NaN for the score, and hence be
    // ignored by FGS
    private boolean ignoreLinearDependent = false;

    // The printstream output should be sent to.
    private PrintStream out = System.out;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    // Variables that caused computational problems and so are to be avoided.
    private Set<Integer> forbidden = new HashSet<>();

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScore(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        this.setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        for (int p : parents) if (forbidden.contains(p)) return Double.NaN;

        try {
            double s2 = getCovariances().getValue(i, i);
            int k = parents.length;

            TetradMatrix covxx = getSelection(getCovariances(), parents, parents);
            TetradVector covxy = getSelection(getCovariances(), parents, new int[]{i}).getColumn(0);
            s2 -= covxx.inverse().times(covxy).dotProduct(covxy);

            if (s2 <= 0) {
                if (isVerbose()) {
                    out.println("Nonpositive residual varianceY: resVar / varianceY = " + (s2 / getCovariances().getValue(i, i)));
                }

                return Double.NaN;
            }

            int n = getSampleSize();
//            n = n - 1;
            int  p = (k * (k + 1)) / 2 + 1;
//            int p = k + 1;
            s2 = ((n) / (double) (n - p)) * s2;

            return -n * log(s2) - getPenaltyDiscount() * p * (log(n));// - log(2.0 * PI));
        } catch (Exception e) {
            boolean removedOne = true;

            while (removedOne) {
                List<Integer> _parents = new ArrayList<>();
                for (int parent : parents) _parents.add(parent);
                _parents.removeAll(forbidden);
                parents = new int[_parents.size()];
                for (int y = 0; y < _parents.size(); y++) parents[y] = _parents.get(y);
                removedOne = printMinimalLinearlyDependentSet(parents, getCovariances());
            }

            return Double.NaN;
        }
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScore(y, x) - localScore(y);
    }

    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 1, parents.length);
        all[0] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(int i, int parent) {
        return localScore(i, new int[]{parent});

//        double residualVariance = getCovariances().getValue(i, i);
//        int n = getSampleSize();
//        int p = 1;
//        final double covXX = getCovariances().getValue(parent, parent);
//
//        if (covXX == 0) {
//            if (isVerbose()) {
//                out.println("Dividing by zero");
//            }
//            return Double.NaN;
//        }
//
//        double covxxInv = 1.0 / covXX;
//        double covxy = getCovariances().getValue(i, parent);
//        double b = covxxInv * covxy;
//        residualVariance -= covxy * b;
//
//        if (residualVariance <= 0) {
//            if (isVerbose()) {
//                out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / getCovariances().getValue(i, i)));
//            }
//            return Double.NaN;
//        }
//
//        return score(residualVariance, n, p);
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(int i) {
        return localScore(i, new int[0]);
//        double residualVariance = getCovariances().getValue(i, i);
//        int n = getSampleSize();
//        int p = 0;
//
//        if (residualVariance <= 0) {
//            if (isVerbose()) {
//                out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / getCovariances().getValue(i, i)));
//            }
//            return Double.NaN;
//        }
//
//        double c = getPenaltyDiscount();
//        return score(residualVariance, n, p);
    }

    /**
     * True iff edges that cause linear dependence are ignored.
     */
    public boolean isIgnoreLinearDependent() {
        return ignoreLinearDependent;
    }

    public void setIgnoreLinearDependent(boolean ignoreLinearDependent) {
        this.ignoreLinearDependent = ignoreLinearDependent;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public ICovarianceMatrix getCovariances() {
        return covariances;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;//-0.25 * getPenaltyDiscount() * Math.log(sampleSize);
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public double getParameter1() {
        return penaltyDiscount;
    }

    @Override
    public void setParameter1(double alpha) {
        this.penaltyDiscount = alpha;
    }

    // Calculates the BIC score.
//    private double score(double residualVariance, int p) {
//        double q = 1.0 / covariances.getVariables().size();
//        double prior = p * Math.log(q) + (getSampleSize() - p) * Math.log(1.0 - q);
//        return -getSampleSize() * Math.log(residualVariance)
//                - getPenaltyDiscount() * (p + 1) * Math.log(getSampleSize())
//                ;//+ prior;
//    }


    private TetradMatrix getSelection(ICovarianceMatrix cov, int[] rows, int[] cols) {
        return cov.getSelection(rows, cols);
    }

    // Prints a smallest subset of parents that causes a singular matrix exception.
    private boolean printMinimalLinearlyDependentSet(int[] parents, ICovarianceMatrix cov) {
        List<Node> _parents = new ArrayList<>();
        for (int p : parents) _parents.add(variables.get(p));

        DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            int[] sel = new int[choice.length];
            List<Node> _sel = new ArrayList<>();
            for (int m = 0; m < choice.length; m++) {
                sel[m] = parents[m];
                _sel.add(variables.get(sel[m]));
            }

            TetradMatrix m = cov.getSelection(sel, sel);

            try {
                m.inverse();
            } catch (Exception e2) {
                forbidden.add(sel[0]);
                out.println("### Linear dependence among variables: " + _sel);
                out.println("### Removing " + _sel.get(0));
                return true;
            }
        }

        return false;
    }

    private void setCovariances(ICovarianceMatrix covariances) {
        this.covariances = covariances;
    }

    public void setVariables(List<Node> variables) {
        covariances.setVariables(variables);
        this.variables = variables;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(log(sampleSize));
    }
}



