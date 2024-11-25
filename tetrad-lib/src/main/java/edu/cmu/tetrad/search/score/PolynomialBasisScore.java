package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PolynomialBasisScore implements Score {
    private final List<Node> variables;
    private final Map<Integer, List<Integer>> embedding;
    private final SemBicScore bic;
    private boolean usePseudoInverse = false;

    public PolynomialBasisScore(DataSet dataSet, boolean precomputeCovariances, int truncationLimit) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.variables = dataSet.getVariables();
        int n = dataSet.getNumRows();
        this.embedding = new HashMap<>();

        List<Node> A = new ArrayList<>();
        List<double[]> B = new ArrayList<>();

        int index = 0;
        int i = 0;
        int i_ = 0;
        while (i_ < this.variables.size()) {
            Node v = this.variables.get(i_);

            if (v instanceof DiscreteVariable) {
                Map<List<Integer>, Integer> keys = new HashMap<>();
                Map<Integer, List<Integer>> keysReverse = new HashMap<>();
                for (int j = 0; j < n; j++) {
                    List<Integer> key = new ArrayList<>();
                    key.add(dataSet.getInt(j, i_));
                    if (!keys.containsKey(key)) {
                        keys.put(key, i);
                        keysReverse.put(i, key);
                        Node v_ = new ContinuousVariable("V__" + ++index);
                        A.add(v_);
                        B.add(new double[n]);
                        i++;
                    }
                    B.get(keys.get(key))[j] = 1;
                }

                i--;
                keys.remove(keysReverse.get(i));
                A.remove(i);
                B.remove(i);

                this.embedding.put(i_, new ArrayList<>(keys.values()));
            } else {
                List<Integer> indexList = new ArrayList<>();
                for (int p = 1; p <= truncationLimit; p++) {
                    Node vPower = new ContinuousVariable("V__" + ++index);
                    A.add(vPower);
                    double[] bPower = new double[n];
                    for (int j = 0; j < n; j++) {
                        bPower[j] = Math.pow(0.8, p) * Math.pow(dataSet.getDouble(j, i_), p);
                    }
                    B.add(bPower);
                    indexList.add(i);
                    i++;
                }
                this.embedding.put(i_, indexList);
            }
            i_++;
        }

        double[][] B_ = new double[n][B.size()];
        for (int j = 0; j < B.size(); j++) {
            for (int k = 0; k < n; k++) {
                B_[k][j] = B.get(j)[k];
            }
        }

        RealMatrix D = MatrixUtils.createRealMatrix(B_);
        this.bic = new SemBicScore(new BoxDataSet(new DoubleDataBox(D.getData()), A), precomputeCovariances);
        this.bic.setUsePseudoInverse(usePseudoInverse);
        this.bic.setStructurePrior(0);
    }

    public double localScore(int i, int... parents) {
        double score = 0;

        List<Integer> A = new ArrayList<>(this.embedding.get(i));
        List<Integer> B = new ArrayList<>();
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        for (Integer i_ : A) {
            int[] parents_ = new int[B.size()];
            for (int i__ = 0; i__ < B.size(); i__++) {
                parents_[i__] = B.get(i__);
            }
            score += this.bic.localScore(i_, parents_);
            B.add(i_);
        }

        return score;
    }

    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return this.bic.isEffectEdge(bump);
    }

    @Override
    public int getSampleSize() {
        return this.bic.getSampleSize();
    }

    @Override
    public int getMaxDegree() {
        return this.bic.getMaxDegree();
    }

    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Degenerate Gaussian Score Penalty " + nf.format(this.bic.getPenaltyDiscount());
    }

    public double getPenaltyDiscount() {
        return this.bic.getPenaltyDiscount();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.bic.setPenaltyDiscount(penaltyDiscount);
    }

    public void setUsePseudoInverse(boolean usePseudoInverse) {
        this.usePseudoInverse = usePseudoInverse;
    }
}