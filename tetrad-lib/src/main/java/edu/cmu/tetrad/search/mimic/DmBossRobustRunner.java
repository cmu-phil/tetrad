///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DmBossRobust;
import edu.cmu.tetrad.search.DmPc;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * Benchmark runner for the DM-PC algorithm.
 *
 * @author josephramsey
 */
public final class DmBossRobustRunner implements MimicSearchRunner {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "DM-BOSS-Robust";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph run(DataSet data, Knowledge knowledge, Parameters parameters) {
        IndependenceTest test = new FisherZ().getTest(data, parameters);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setVerbose(false);

        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        DmBossRobust search = new DmBossRobust(score, knowledge);

        return search.search();
    }
}