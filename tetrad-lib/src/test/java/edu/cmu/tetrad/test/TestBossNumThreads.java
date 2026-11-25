/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BossTopK;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The number of threads is the user's to set. From 2024-2-25 to 2026-11-25 the BOSS algorithms set it to 4 on every
 * run, in the parameters themselves, so that whatever was asked for was ignored and the parameter read 4 afterward.
 *
 * @author Claude
 */
public class TestBossNumThreads {

    @Test
    public void testTheNumberOfThreadsIsLeftAsTheUserSetIt() throws Exception {
        RandomUtil.getInstance().setSeed(7400L);
        Graph truth = RandomGraph.randomGraph(10, 0, 15, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(truth)).simulateData(500, false);

        List<Algorithm> algorithms = List.of(new Boss(new SemBicScore()), new BossTopK(new SemBicScore()));

        for (Algorithm algorithm : algorithms) {
            String name = algorithm.getClass().getSimpleName();

            // Left alone, it is the registered default, before the search and after it.
            Parameters parameters = new Parameters();
            assertEquals(1, parameters.getInt(Params.NUM_THREADS));
            assertNotNull(algorithm.search(data, parameters));
            assertEquals(name, 1, parameters.getInt(Params.NUM_THREADS));

            // Set, it stays as set.
            for (int numThreads : new int[]{1, 2, 3}) {
                parameters = new Parameters();
                parameters.set(Params.NUM_THREADS, numThreads);
                assertNotNull(algorithm.search(data, parameters));
                assertEquals(name, numThreads, parameters.getInt(Params.NUM_THREADS));
            }
        }
    }
}
