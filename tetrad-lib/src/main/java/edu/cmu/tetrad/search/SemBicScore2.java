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

import java.io.PrintStream;
import java.util.List;

/**
 * Implements the continuous BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class SemBicScore2 implements ISemBicScore {

    private SemBicScore score;

    public SemBicScore2(ICovarianceMatrix covariances) {
        this(covariances, 2.0);
    }

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScore2(ICovarianceMatrix covariances, double penaltyDiscount) {
        this.score = new SemBicScore(covariances, penaltyDiscount);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int...parents) {
        return score.localScore(i, parents);
    }

    public double localScoreDiff(int x, int y, int[] z) {
        double b = score.localScore(y, append(z, x)) - localScore(y, z);

        if (b > 0) {
            for (int r : z) {


                b += score.localScore(r, y, x) - localScore(r, y);
            }
        }

        return b;
    }

    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(int i, int parent) {
        return localScore(i, new int[]{parent});
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(int i) {
        return localScore(i, new int[0]);
    }

    /**
     * True iff edges that cause linear dependence are ignored.
     */
    public boolean isIgnoreLinearDependent() {
        return score.isIgnoreLinearDependent();
    }

    public void setIgnoreLinearDependent(boolean ignoreLinearDependent) {
        score.setIgnoreLinearDependent(ignoreLinearDependent);
    }

    public void setOut(PrintStream out) {
        score.setOut(out);
    }

    public double getPenaltyDiscount() {
        return score.getPenaltyDiscount();
    }

    public ICovarianceMatrix getCovariances() {
        return score.getCovariances();
    }

    public int getSampleSize() {
        return score.getSampleSize();
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return score.isEffectEdge(bump);
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        score.setPenaltyDiscount(penaltyDiscount);
    }

    public boolean isVerbose() {
        return score.isVerbose();
    }

    public void setVerbose(boolean verbose) {
        score.setVerbose(verbose);
    }
    @Override
    public List<Node> getVariables() {
        return score.getVariables();
    }

    @Override
    public boolean isDiscrete() {
        return false;
    }

    @Override
    public double getParameter1() {
        return score.getParameter1();
    }

    @Override
    public void setParameter1(double alpha) {
        score.setParameter1(alpha);
    }
}



