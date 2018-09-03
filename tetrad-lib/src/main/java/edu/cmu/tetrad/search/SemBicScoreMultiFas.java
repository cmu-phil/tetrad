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

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import edu.cmu.tetrad.search.ISemBicScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.SemBicScore;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class SemBicScoreMultiFas implements ISemBicScore, Score {

    // The covariance matrix.
    private List<SemBicScore> semBicScores;

    // The variables of the covariance matrix.
    private List<Node> variables;

    private int sampleSize;

    // The penalty penaltyDiscount.
    private double penaltyDiscount = 2.0;

    // True if linear dependencies should return NaN for the score, and hence be
    // ignored by FGES
    private boolean ignoreLinearDependent = false;

    // The printstream output should be sent to.
    private PrintStream out = System.out;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    // Variables that caused computational problems and so are to be avoided.
    private Set<Integer> forbidden = new HashSet<>();

    private Map<String, Integer> indexMap;

    private Map<Score, ICovarianceMatrix> covMap;

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScoreMultiFas(List<DataModel> dataModels) {
        if (dataModels == null) {
            throw new NullPointerException();
        }

        List<SemBicScore> semBicScores = new ArrayList<>();

        for (DataModel model : dataModels) {
            if (model instanceof DataSet) {
                DataSet dataSet = (DataSet) model;

                if (!dataSet.isContinuous()) {
                    throw new IllegalArgumentException("Datasets must be continuous.");
                }

                SemBicScore semBicScore = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
                semBicScore.setPenaltyDiscount(penaltyDiscount);
                semBicScores.add(semBicScore);
            } else if (model instanceof ICovarianceMatrix) {
                SemBicScore semBicScore = new SemBicScore((ICovarianceMatrix) model);
                semBicScore.setPenaltyDiscount(penaltyDiscount);
                semBicScores.add(semBicScore);
            } else {
                throw new IllegalArgumentException("Only continuous data sets and covariance matrices may be used as input.");
            }
        }

        List<Node> variables = semBicScores.get(0).getVariables();

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
    public double localScoreDiff(int x, int y, int[] z) {
        double sum = 0.0;

        Node _x = variables.get(x);
        Node _y = variables.get(y);
        List<Node> _z = getVariableList(z);



        double r;
        int p;
        int N;

        for (SemBicScore score : semBicScores) {
            try {
                r = partialCorrelation(_x, _y, _z, score);
            } catch (SingularMatrixException e) {
//            System.out.println(SearchLogUtils.determinismDetected(_z, _x));
                return Double.NaN;
            }

            p = 2 + z.length;

            N = covMap.get(score).getSampleSize();

            sum += -N * Math.log(1.0 - r * r) - p * getPenaltyDiscount() * Math.log(N);
        }

        return sum;
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScoreDiff(x, y, new int[0]);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int[] parents) {
        double sum = 0.0;
        int count = 0;

        for (SemBicScore score : semBicScores) {
            double _score = score.localScore(i, parents);

            if (!Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        return sum / count;
    }

    public double localScore(int i, int[] parents, int index) {
        return localScoreOneDataSet(i, parents, index);
    }

    private double localScoreOneDataSet(int i, int[] parents, int index) {
        return semBicScores.get(index).localScore(i, parents);
    }


    int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(int i, int parent) {
        double sum = 0.0;
        int count = 0;

        for (SemBicScore score : semBicScores) {
            double _score = score.localScore(i, parent);

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
    public double localScore(int i) {
        double sum = 0.0;
        int count = 0;

        for (SemBicScore score : semBicScores) {
            double _score = score.localScore(i);

            if (!Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        return sum / count;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > -0.25 * getPenaltyDiscount() * Math.log(sampleSize);
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
        for (SemBicScore score : semBicScores) {
            score.setPenaltyDiscount(penaltyDiscount);
        }
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

    public boolean getAlternativePenalty() {
        return false;
    }

    public void setAlternativePenalty(double value) {
    }

    @Override
    public int getSampleSize() {
        return sampleSize;
    }

    // Calculates the BIC score.
//    private double score(double residualVariance, int n, int p, double c) {
//        return -n * Math.log(residualVariance) - c * (2 * p + 1) * Math.log(n);
//    }
//
    private TetradMatrix getSelection1(ICovarianceMatrix cov, int[] rows) {
        return cov.getSelection(rows, rows);
    }

    private TetradVector getSelection2(ICovarianceMatrix cov, int[] rows, int k) {
        return cov.getSelection(rows, new int[]{k}).getColumn(0);
    }

    // Prints a smallest subset of parents that causes a singular matrix exception.
    private void printMinimalLinearlyDependentSet(int[] parents, ICovarianceMatrix cov) {
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
                out.println("### Linear dependence among variables: " + _sel);
            }
        }
    }

    private List<Node> getVariableList(int[] indices) {
        List<Node> variables = new ArrayList<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }
    private double partialCorrelation(Node x, Node y, List<Node> z, SemBicScore score) throws SingularMatrixException {
        int[] indices = new int[z.size() + 2];
        indices[0] = indexMap.get(x.getName());
        indices[1] = indexMap.get(y.getName());
        for (int i = 0; i < z.size(); i++) indices[i + 2] = indexMap.get(z.get(i).getName());
        TetradMatrix submatrix = covMap.get(score).getSubmatrix(indices).getMatrix();
        return StatUtils.partialCorrelation(submatrix);
    }

    private Map<String, Integer> indexMap(List<Node> variables) {
        Map<String, Integer> indexMap = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i).getName(), i);
        }

        return indexMap;
    }

    private Map<Score, ICovarianceMatrix> covMap(List<SemBicScore> scores){
        Map<Score, ICovarianceMatrix> covMap = new HashMap<>();
        SemBicScore score;

        for (int i = 0; i < scores.size(); i++){
            score = scores.get(i);
            covMap.put(score, score.getCovariances());
        }

        return covMap;
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
        return 1000;
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }
}



