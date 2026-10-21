///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.missing.MissingDataPolicy;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.data.missing.PairwiseCovarianceEstimator;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks the pairwise-deletion covariance: it equals the sample covariance on complete data, it is made positive
 * definite when pairwise entries are inconsistent, the interface reaches it as its own policy, and the policy survives
 * serialization.
 */
public class TestPairwiseCovariance {

    @Test
    public void equalsSampleCovarianceOnCompleteData() {
        DataSet data = chain(300, 0.0);
        ICovarianceMatrix pairwise = PairwiseCovarianceEstimator.estimate(data);
        CovarianceMatrix sample = new CovarianceMatrix(data);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(sample.getValue(i, j), pairwise.getValue(i, j), 1e-9);
            }
        }
    }

    @Test
    public void inconsistentPairsAreMadePositiveDefinite() {
        // Three variables observed only in pairs, each pair on its own rows, with correlations +0.9, +0.9 and -0.9:
        // no correlation matrix has all three, so the raw pairwise matrix is indefinite.
        Random r = new Random(2);
        int m = 200;
        double[][] x = new double[3 * m][3];
        for (double[] row : x) java.util.Arrays.fill(row, Double.NaN);
        int[][] pairs = {{0, 1}, {1, 2}, {0, 2}};
        double[] rho = {0.9, 0.9, -0.9};
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < m; i++) {
                double u = r.nextGaussian();
                double v = rho[k] * u + Math.sqrt(1 - rho[k] * rho[k]) * r.nextGaussian();
                x[k * m + i][pairs[k][0]] = u;
                x[k * m + i][pairs[k][1]] = v;
            }
        }
        List<Node> vars = List.of(new ContinuousVariable("A"), new ContinuousVariable("B"),
                new ContinuousVariable("C"));
        ICovarianceMatrix cov = PairwiseCovarianceEstimator.estimate(new BoxDataSet(new DoubleDataBox(x), vars));

        // Positive definite: the Cholesky factorization of the matrix succeeds.
        assertTrue(choleskySucceeds(cov));
        // Shrunk, not zeroed.
        assertTrue(Math.abs(cov.getValue(0, 1)) > 0.1);
    }

    @Test
    public void interfacePolicyReachesTheEstimator() {
        DataSet data = chain(500, 0.3);
        Parameters p = new Parameters();
        p.set(Params.MISSING_DATA_POLICY, "pairwise");
        p.set(Params.MISSING_ESS_MODE, "minPairwise");
        p.set(Params.PENALTY_DISCOUNT, 1.0);

        MissingDataSpec spec = MissingDataUtils.fromParameters(p);
        assertEquals(MissingDataPolicy.PAIRWISE_COVARIANCE, spec.getPolicy());

        Score score = new SemBicScore().getScore(data, p);
        double withParent = score.localScore(1, 0);
        double without = score.localScore(1);
        assertFalse(Double.isNaN(withParent));
        assertTrue("X0 -> X1 should be preferred", withParent > without);
    }

    @Test
    public void pairwiseSpecSurvivesSerialization() throws Exception {
        MissingDataSpec spec = MissingDataSpec.pairwiseCovariance()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE);
        assertTrue(spec.getPolicy().estimatesCovariance());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(spec);
        }
        MissingDataSpec back;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            back = (MissingDataSpec) in.readObject();
        }

        assertEquals(MissingDataPolicy.PAIRWISE_COVARIANCE, back.getPolicy());
        assertEquals(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE, back.getEssMode());
    }

    private static boolean choleskySucceeds(ICovarianceMatrix cov) {
        int p = cov.getDimension();
        double[][] l = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j <= i; j++) {
                double s = cov.getValue(i, j);
                for (int k = 0; k < j; k++) s -= l[i][k] * l[j][k];
                if (i == j) {
                    if (s <= 0) return false;
                    l[i][i] = Math.sqrt(s);
                } else {
                    l[i][j] = s / l[j][j];
                }
            }
        }
        return true;
    }

    /** X0 -> X1 -> X2 -> X3, linear Gaussian, with the given MCAR rate on every variable. */
    private static DataSet chain(int n, double rate) {
        Random random = new Random(9);
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + j));
        double[][] x = new double[n][4];
        for (int i = 0; i < n; i++) {
            x[i][0] = random.nextGaussian();
            for (int j = 1; j < 4; j++) x[i][j] = 0.7 * x[i][j - 1] + random.nextGaussian();
        }
        DataSet data = new BoxDataSet(new DoubleDataBox(x), vars);
        if (rate == 0.0) return data;
        RandomUtil.getInstance().setSeed(9L);
        return MissingnessInjector.inject(data, MissingnessInjector.Spec.uniform(4, rate, 0.0)).data();
    }
}
