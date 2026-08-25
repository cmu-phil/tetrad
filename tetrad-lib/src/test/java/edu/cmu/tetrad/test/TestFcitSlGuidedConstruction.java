///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
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
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA. //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.FcitSl;
import edu.cmu.tetrad.search.FcitSl2;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pins the guided witness construction generator in {@link FcitSl} to the class-walk
 * default: on oracle problems with latents, the guided generator (with and without the
 * legacy fallback) must return exactly the same PAG as the default configuration.
 * Deterministic seeds; oracle tests, so any disagreement is a correctness regression
 * in the guided generator, not sampling noise.
 *
 * @author josephramsey
 */
public class TestFcitSlGuidedConstruction {

    /**
     * Constructs a new test instance.
     */
    public TestFcitSlGuidedConstruction() {
    }

    /**
     * Guided construction (fallback enabled and disabled) agrees exactly with the
     * class-walk default on random 10-node, 2-latent oracle problems.
     */
    @Test
    public void testGuidedAgreesWithDefaultOracle10() {
        agreeOnRandomDags(10, 3, 2, 5, 42L);
    }

    /**
     * Same pin at 15 nodes, 3 latents, average degree 3.
     */
    @Test
    public void testGuidedAgreesWithDefaultOracle15() {
        agreeOnRandomDags(15, 3, 3, 3, 99L);
    }

    private void agreeOnRandomDags(int numMeasures, int avgDegree, int numLatents,
                                   int numRuns, long baseSeed) {
        for (int run = 0; run < numRuns; run++) {
            RandomUtil.getInstance().setSeed(baseSeed + 1000L * run
                    + 100_000L * numMeasures + 17L * avgDegree);
            Graph dag = RandomGraph.randomGraph(numMeasures, numLatents,
                    numMeasures * avgDegree / 2, 100, 100, 100, false);

            Graph base;
            Graph guided;
            Graph guidedNoFallback;

            try {
                base = run(dag, false, true);
                guided = run(dag, true, true);
                guidedNoFallback = run(dag, true, false);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assertEquals("Guided construction disagreed with the class-walk default, run "
                    + run + " (" + numMeasures + ":" + avgDegree + "+" + numLatents + ")",
                    base, guided);
            assertEquals("Guided construction without fallback disagreed with the "
                    + "class-walk default, run " + run + " (" + numMeasures + ":"
                    + avgDegree + "+" + numLatents + ")",
                    base, guidedNoFallback);
        }
    }

    private Graph run(Graph dag, boolean guided, boolean fallback) throws InterruptedException {
        MsepTest oracle = new MsepTest(dag);
        GraphScore score = new GraphScore(dag);

        FcitSl2 fcit = new FcitSl2(oracle, score);
        fcit.setKnowledge(new Knowledge());
        fcit.setCompleteRuleSetUsed(true);
        fcit.setExcludeSelectionBias(true);
        fcit.setVerbose(false);

        if (guided) {
            fcit.setUseGuidedConstruction(true);
            fcit.setGuidedFallbackToLegacy(fallback);
        }

        return fcit.search();
    }
}
