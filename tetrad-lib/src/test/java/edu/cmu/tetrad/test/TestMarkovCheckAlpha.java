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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link MarkovCheck} when the base independence test has no usable alpha.
 * <p>
 * {@link ScoreIndTest} reports an alpha of -1 (its "p-value" is a score difference, not a probability), and
 * {@code IndTestIndependenceFacts} reports NaN. Passing either to {@code BinomialDistribution} threw
 * {@code OutOfRangeException: -1 out of [0, 1] range} from {@code generateResults}, crashing the Markov Checker in
 * the GUI; the fraction-dependent statistic was meanwhile silently computed as zero against a nonsense threshold.
 * Both statistics are now reported as NaN, which the editor displays as "-".
 *
 * @author josephramsey
 */
public class TestMarkovCheckAlpha {

    /**
     * A small continuous dataset with A -> C <- B.
     */
    private static DataSet collider(int n, long seed) {
        Random rng = new Random(seed);

        List<Node> vars = new ArrayList<>();
        for (String s : new String[]{"A", "B", "C"}) vars.add(new ContinuousVariable(s));
        DataSet data = new BoxDataSet(new DoubleDataBox(n, 3), vars);

        for (int i = 0; i < n; i++) {
            double a = rng.nextGaussian();
            double b = rng.nextGaussian();
            data.setDouble(i, 0, a);
            data.setDouble(i, 1, b);
            data.setDouble(i, 2, a + b + 0.5 * rng.nextGaussian());
        }

        return data;
    }

    private static Graph colliderGraph(DataSet data) {
        Graph g = new EdgeListGraph(new ArrayList<>(data.getVariables()));
        g.addDirectedEdge(data.getVariable("A"), data.getVariable("C"));
        g.addDirectedEdge(data.getVariable("B"), data.getVariable("C"));
        return g;
    }

    /**
     * Generating results with a score-based test must not throw, and the statistics that require a p-value
     * threshold must be reported as undefined rather than fabricated.
     */
    @Test
    public void testScoreBasedTestDoesNotThrow() {
        DataSet data = collider(300, 42L);
        Graph g = colliderGraph(data);

        ScoreIndTest test = new ScoreIndTest(new SemBicScore(data, true), data);
        assertTrue("Precondition: a score-based test reports an alpha outside [0, 1]",
                test.getAlpha() < 0.0 || test.getAlpha() > 1.0 || Double.isNaN(test.getAlpha()));

        MarkovCheck mc = new MarkovCheck(g, test, ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);

        mc.generateResults(true, true);   // threw OutOfRangeException before the fix

        assertFalse("A score-based test must not be reported as providing calibrated p-values",
                mc.providesCalibratedPValues());
        assertTrue("Binomial p-value must be undefined without a usable alpha",
                Double.isNaN(mc.getBinomialPValue_(true)));
        assertTrue("Fraction dependent must be undefined (not 0.0) without a usable alpha",
                Double.isNaN(mc.getFractionDependent(true)));
    }

    /**
     * With an ordinary alpha-bearing test the statistics remain defined, so the guard has not disabled the normal
     * path.
     */
    @Test
    public void testAlphaBearingTestStillComputesStats() {
        DataSet data = collider(300, 42L);
        Graph g = colliderGraph(data);

        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        MarkovCheck mc = new MarkovCheck(g, test, ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);

        mc.generateResults(true, true);

        assertTrue("Fisher-Z must be reported as providing calibrated p-values",
                mc.providesCalibratedPValues());
        assertFalse("Binomial p-value must be defined for an alpha-bearing test",
                Double.isNaN(mc.getBinomialPValue_(true)));
        assertFalse("Fraction dependent must be defined for an alpha-bearing test",
                Double.isNaN(mc.getFractionDependent(true)));
    }
}
