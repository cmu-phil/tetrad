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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class SemBicScoreMultiFas implements ISemBicScore {

    // The covariance matrix.
    private final List<SemBicScore> semBicScores;

    // The variables of the covariance matrix.
    private final List<Node> variables;

    private final int sampleSize;

    // The penalty penaltyDiscount.
    private double penaltyDiscount = 2.0;

    // True if linear dependencies should return NaN for the score, and hence be
    // ignored by FGES
    private final boolean ignoreLinearDependent = false;

    // The printstream output should be sent to.
    private PrintStream out = System.out;

    // True if verbose output should be sent to out.
    private boolean verbose;

    // Variables that caused computational problems and so are to be avoided.
    private final Set<Integer> forbidden = new HashSet<>();

    private final Map<String, Integer> indexMap;

    private final Map<Score, ICovarianceMatrix> covMap;

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScoreMultiFas(final List<DataModel> dataModels) {
        if (dataModels == null) {
            throw new NullPointerException();
        }

        final List<SemBicScore> semBicScores = new ArrayList<>();

        for (final DataModel model : dataModels) {
            if (model instanceof DataSet) {
                final DataSet dataSet = (DataSet) model;

                if (!dataSet.isContinuous()) {
                    throw new IllegalArgumentException("Datasets must be continuous.");
                }

                final SemBicScore semBicScore = new SemBicScore(new CovarianceMatrix(dataSet));
                semBicScore.setPenaltyDiscount(this.penaltyDiscount);
                semBicScores.add(semBicScore);
            } else if (model instanceof ICovarianceMatrix) {
                final SemBicScore semBicScore = new SemBicScore((ICovarianceMatrix) model);
                semBicScore.setPenaltyDiscount(this.penaltyDiscount);
                semBicScores.add(semBicScore);
            } else {
                throw new IllegalArgumentException("Only continuous data sets and covariance matrices may be used as input.");
            }
        }

        final List<Node> variables = semBicScores.get(0).getVariables();

        for (int i = 2; i < semBicScores.size(); i++) {
            semBicScores.get(i).setVariables(variables);
        }

        this.semBicScores = semBicScores;
        this.variables = variables;
        this.sampleSize = semBicScores.get(0).getSampleSize();

        this.indexMap = indexMap(this.variables);
        this.covMap = covMap(this.semBicScores);
    }


    @Override
    public double localScoreDiff(final int x, final int y, final int[] z) {
        double sum = 0.0;

        final Node _x = this.variables.get(x);
        final Node _y = this.variables.get(y);
        final List<Node> _z = getVariableList(z);


        double r;
        int p;
        int N;

        for (final SemBicScore score : this.semBicScores) {
            try {
                r = partialCorrelation(_x, _y, _z, score);
            } catch (final SingularMatrixException e) {
//            System.out.println(SearchLogUtils.determinismDetected(_z, _x));
                return Double.NaN;
            }

            p = 2 + z.length;

            N = this.covMap.get(score).getSampleSize();

            sum += -N * Math.log(1.0 - r * r) - p * getPenaltyDiscount() * Math.log(N);
        }

        return sum;
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
        int count = 0;

        for (final SemBicScore score : this.semBicScores) {
            final double _score = score.localScore(i, parents);

            if (!Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        return sum / count;
    }

    public double localScore(final int i, final int[] parents, final int index) {
        return localScoreOneDataSet(i, parents, index);
    }

    private double localScoreOneDataSet(final int i, final int[] parents, final int index) {
        return this.semBicScores.get(index).localScore(i, parents);
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
        int count = 0;

        for (final SemBicScore score : this.semBicScores) {
            final double _score = score.localScore(i, parent);

            if (!Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        return sum / count;
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(final int i) {
        double sum = 0.0;
        int count = 0;

        for (final SemBicScore score : this.semBicScores) {
            final double _score = score.localScore(i);

            if (!Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        return sum / count;
    }

    public void setOut(final PrintStream out) {
        this.out = out;
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    @Override
    public boolean isEffectEdge(final double bump) {
        return bump > -0.25 * getPenaltyDiscount() * Math.log(this.sampleSize);
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
        for (final SemBicScore score : this.semBicScores) {
            score.setPenaltyDiscount(penaltyDiscount);
        }
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

    public boolean getAlternativePenalty() {
        return false;
    }

    public void setAlternativePenalty(final double value) {
    }

    @Override
    public int getSampleSize() {
        return this.sampleSize;
    }

    // Calculates the BIC score.
//    private double score(double residualVariance, int n, int p, double c) {
//        return -n * Math.log(residualVariance) - c * (2 * p + 1) * Math.log(n);
//    }
//
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

    private List<Node> getVariableList(final int[] indices) {
        final List<Node> variables = new ArrayList<>();
        for (final int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }

    private double partialCorrelation(final Node x, final Node y, final List<Node> z, final SemBicScore score) throws SingularMatrixException {
        final int[] indices = new int[z.size() + 2];
        indices[0] = this.indexMap.get(x.getName());
        indices[1] = this.indexMap.get(y.getName());
        for (int i = 0; i < z.size(); i++) indices[i + 2] = this.indexMap.get(z.get(i).getName());
        final Matrix submatrix = this.covMap.get(score).getSubmatrix(indices).getMatrix();
        return StatUtils.partialCorrelation(submatrix);
    }

    private Map<String, Integer> indexMap(final List<Node> variables) {
        final Map<String, Integer> indexMap = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i).getName(), i);
        }

        return indexMap;
    }

    private Map<Score, ICovarianceMatrix> covMap(final List<SemBicScore> scores) {
        final Map<Score, ICovarianceMatrix> covMap = new HashMap<>();
        SemBicScore score;

        for (int i = 0; i < scores.size(); i++) {
            score = scores.get(i);
            covMap.put(score, score.getCovariances());
        }

        return covMap;
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
}



