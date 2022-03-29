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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class BdeuScoreImages implements IBDeuScore {

    // The covariance matrix.
    private final List<BDeuScore> scores;

    // The variables of the covariance matrix.
    private final List<Node> variables;

    // The sample size of the covariance matrix.
    private int sampleSize;

    // The printstream output should be sent to.
    private PrintStream out = System.out;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    private double samplePrior = 1.0;

    private double structurePrior = 1.0;

    /**
     * Constructs the score using a covariance matrix.
     */
    public BdeuScoreImages(final List<DataModel> dataModels) {
        if (dataModels == null) {
            throw new NullPointerException();
        }

        final List<BDeuScore> scores = new ArrayList<>();

        for (final DataModel model : dataModels) {
            if (model instanceof DataSet) {
                final DataSet dataSet = (DataSet) model;

                if (!dataSet.isDiscrete()) {
                    throw new IllegalArgumentException("Datasets must be discrete.");
                }

                scores.add(new BDeuScore(dataSet));
            } else {
                throw new IllegalArgumentException("Only continuous data sets and covariance matrices may be used as input.");
            }
        }

        final List<Node> variables = scores.get(0).getVariables();

        for (int i = 2; i < scores.size(); i++) {
            scores.get(i).setVariables(variables);
        }

        this.scores = scores;
        this.variables = variables;
    }


    public double localScoreDiff(final int x, final int y, final int[] z) {
        double sum = 0.0;

        for (final BDeuScore score : this.scores) {
            sum += score.localScoreDiff(x, y, z);
        }

        return sum / this.scores.size();
    }

    @Override
    public double localScoreDiff(final int x, final int y) {
        return localScoreDiff(x, y, new int[0]);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(final int i, final int[] parents) {
        double sum = 0.0;

        for (final BDeuScore score : this.scores) {
            sum += score.localScore(i, parents);
        }

        return sum / this.scores.size();
    }

    public double localScore(final int i, final int[] parents, final int index) {
        return localScoreOneDataSet(i, parents, index);
    }

    private double localScoreOneDataSet(final int i, final int[] parents, final int index) {
        return this.scores.get(index).localScore(i, parents);
    }


    int[] append(final int[] parents, final int extra) {
        final int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(final int i, final int parent) {
        double sum = 0.0;

        for (final BDeuScore score : this.scores) {
            sum += score.localScore(i, parent);
        }

        return sum / this.scores.size();
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(final int i) {
        double sum = 0.0;

        for (final BDeuScore score : this.scores) {
            sum += score.localScore(i);
        }

        return sum / this.scores.size();
    }

    public void setOut(final PrintStream out) {
        this.out = out;
    }

    @Override
    public boolean isEffectEdge(final double bump) {
        return false;
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public int getSampleSize() {
        return this.scores.get(0).getSampleSize();
    }

    // Calculates the BIC score.
    private double score(final double residualVariance, final int n, final int p, final double c) {
        return -n * Math.log(residualVariance) - c * (p + 1) * Math.log(n);
    }

    private Matrix getSelection1(final ICovarianceMatrix cov, final int[] rows) {
        return cov.getSelection(rows, rows);
    }

    private Vector getSelection2(final ICovarianceMatrix cov, final int[] rows, final int k) {
        return cov.getSelection(rows, new int[]{k}).getColumn(0);
    }

    // Prints a smallest subset of parents that causes a singular matrix exception.
    private void printMinimalLinearlyDependentSet(final int[] parents, final ICovarianceMatrix cov) {
        final List<Node> _parents = new ArrayList<>();
        for (final int p : parents) _parents.add(this.variables.get(p));

        final DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            final int[] sel = new int[choice.length];
            final List<Node> _sel = new ArrayList<>();
            for (int m = 0; m < choice.length; m++) {
                sel[m] = parents[m];
                _sel.add(this.variables.get(sel[m]));
            }

            final Matrix m = cov.getSelection(sel, sel);

            try {
                m.inverse();
            } catch (final Exception e2) {
                this.out.println("### Linear dependence among variables: " + _sel);
            }
        }
    }

    public double getSamplePrior() {
        return this.samplePrior;
    }

    public void setSamplePrior(final double samplePrior) {
        for (final BDeuScore score : this.scores) {
            score.setSamplePrior(samplePrior);
        }
        this.samplePrior = samplePrior;
    }

    public double getStructurePrior() {
        return this.structurePrior;
    }

    public void setStructurePrior(final double structurePrior) {
        for (final BDeuScore score : this.scores) {
            score.setStructurePrior(structurePrior);
        }
        this.structurePrior = structurePrior;
    }

    @Override
    public Node getVariable(final String targetName) {
        for (final Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return 1000;
    }

    @Override
    public boolean determines(final List<Node> z, final Node y) {
        return false;
    }

    @Override
    public String toString() {
        return "BDeu Score Images";
    }

}



