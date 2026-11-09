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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.missing.MissingDataPolicy;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the Phase 2 parameter plumbing: MissingDataUtils.fromParameters (string parameters to MissingDataSpec, as
 * set by algcomparison, causal-cmd, the GUI, or py-tetrad) and the flow of the policy through the score/test
 * wrappers.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestMissingDataParams {

    /**
     * Constructs a new test.
     */
    public TestMissingDataParams() {
    }

    /**
     * fromParameters: absent or "default" yields null (legacy behavior); policy strings parse case-insensitively;
     * EM settings and ESS mode flow through; bad strings are rejected informatively.
     */
    @Test
    public void testFromParameters() {
        assertNull(MissingDataUtils.fromParameters(new Parameters()));

        Parameters def = new Parameters();
        def.set(Params.MISSING_DATA_POLICY, "default");
        assertNull(MissingDataUtils.fromParameters(def));

        Parameters em = new Parameters();
        em.set(Params.MISSING_DATA_POLICY, "EM");
        em.set(Params.MISSING_EM_RIDGE, 1e-4);
        em.set(Params.MISSING_ESS_MODE, "minPairwise");
        MissingDataSpec spec = MissingDataUtils.fromParameters(em);
        assertEquals(MissingDataPolicy.EM_COVARIANCE, spec.getPolicy());
        assertEquals(1e-4, spec.getEmRidge(), 0.0);
        assertEquals(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE, spec.getEssMode());

        Parameters mi = new Parameters();
        mi.set(Params.MISSING_DATA_POLICY, "mi");
        mi.set(Params.MISSING_NUM_IMPUTATIONS, 25);
        assertEquals(25, MissingDataUtils.fromParameters(mi).getNumImputations());

        Parameters bad = new Parameters();
        bad.set(Params.MISSING_DATA_POLICY, "banana");

        try {
            MissingDataUtils.fromParameters(bad);
            throw new AssertionError("Expected an IllegalArgumentException for an unrecognized policy.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("banana"));
        }
    }

    /**
     * Wrapper flow under the missing-data gate: with no policy parameter and incomplete data, the SemBicScore
     * wrapper refuses to run (the Phase 1 contract; silent test-wise deletion is no longer the default); with the
     * "testwise" policy it runs and scores; with the "em" policy it produces a different (EM-based) score on MAR
     * data; with "listwise" the FisherZ wrapper's sample size drops to the complete rows.
     */
    @Test
    public void testWrapperFlow() {
        Random rand = new Random(51);
        int n = 800, p = 5;
        double[][] d = new double[n][p];

        for (int i = 0; i < n; i++) {
            d[i][0] = rand.nextGaussian();
            for (int j = 1; j < p; j++) d[i][j] = 0.6 * d[i][j - 1] + rand.nextGaussian();
        }

        int completeRows = 0;

        for (int i = 0; i < n; i++) {
            if (d[i][1] > 0.3) d[i][2] = Double.NaN; // MAR
            else completeRows++;
        }

        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < p; j++) vars.add(new ContinuousVariable("X" + (j + 1)));
        DataSet ds = new BoxDataSet(new DoubleDataBox(d), vars);

        edu.cmu.tetrad.algcomparison.score.SemBicScore wrapper
                = new edu.cmu.tetrad.algcomparison.score.SemBicScore();

        // Phase 1 contract: incomplete data with no policy (or "default") is refused with an instructive message,
        // rather than silently falling back to test-wise deletion.
        try {
            wrapper.getScore(ds, new Parameters());
            throw new AssertionError("Expected an IllegalArgumentException for missing data with no policy.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("missingDataPolicy"));
        }

        // With an explicit policy, the wrapper runs; testwise reproduces the pre-gate behavior.
        Parameters testwise = new Parameters();
        testwise.set(Params.MISSING_DATA_POLICY, "testwise");
        double testwiseScore = wrapper.getScore(ds, testwise).localScore(2, 1, 3);
        assertTrue(Double.isFinite(testwiseScore));

        Parameters em = new Parameters();
        em.set(Params.MISSING_DATA_POLICY, "em");
        double emScore = wrapper.getScore(ds, em).localScore(2, 1, 3);
        assertTrue(emScore != testwiseScore);

        Parameters lw = new Parameters();
        lw.set(Params.MISSING_DATA_POLICY, "listwise");
        edu.cmu.tetrad.algcomparison.independence.FisherZ fzw
                = new edu.cmu.tetrad.algcomparison.independence.FisherZ();
        assertEquals(completeRows, fzw.getTest(ds, lw).getSampleSize());
    }
}
