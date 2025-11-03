package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Example adapter for a Conditional-Gaussian LRT residual-vs-environment change test. Replace TODOs with your concrete
 * API (you already ran CG-LRT this morning).
 */
public final class CgLrtChangeTest implements ChangeTest {

    /**
     * Default constructor for the CgLrtChangeTest class. This class provides an implementation of a change test using a
     * conditional-Gaussian likelihood ratio test (LRT) to evaluate the dependence of residuals on an environmental
     * variable.
     */
    public CgLrtChangeTest() {
    }

    /**
     * Determines whether changes occur based on a test of the dependence of residuals on an environmental variable
     * using a conditional-Gaussian approach.
     *
     * @param data  the dataset containing the variables and observations
     * @param y     the target node to be tested for change
     * @param Z     a set of nodes representing the conditioning variables
     * @param env   the environmental variable being tested for changes
     * @param alpha the significance level for the hypothesis test
     * @return true if the p-value computed by the independence test is less than the significance level (alpha), false
     * otherwise
     */
    @Override
    public boolean changes(DataSet data, Node y, Set<Node> Z, Node env, double alpha) {
        RawMarginalIndependenceTest marginalIndependenceTest = new IndTestBasisFunctionBlocks(data, 4, 1);

        int yIndex = data.getVariables().indexOf(y);

        double[] yCol = new double[data.getNumRows()];
        for (int i = 0; i < data.getNumRows(); i++) {
            yCol[i] = data.getDouble(i, yIndex);
        }

        List<Node> _Z = new ArrayList<Node>(Z);

        double[][] Zcols = new double[data.getNumRows()][Z.size()];
        for (int j = 0; j < Z.size(); j++) {
            int col = data.getVariables().indexOf(_Z.get(j));
            for (int i = 0; i < data.getNumRows(); i++) Zcols[i][j] = data.getDouble(i, col);
        }

        try {
            double p = marginalIndependenceTest.computePValue(yCol, Zcols);
            return p < alpha;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Pseudocode outline you can wire to your existing plumbing:
        // 1) Fit Y ~ Z under conditional-Gaussian; obtain residuals R.
        // 2) Test dependence of R on E via (degenerate or conditional Gaussian) LRT.
        // 3) Return (p < alpha).
    }
}