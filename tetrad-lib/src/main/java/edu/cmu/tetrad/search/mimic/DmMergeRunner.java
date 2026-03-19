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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.List;

/**
 * Benchmark runner for the DM-Merge algorithm.
 *
 * @author josephramsey
 */
public final class DmMergeRunner implements MimicSearchRunner {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "DM-Merge";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph run(DataSet data, Knowledge knowledge, List<Node> inputs, List<Node> outputs, Parameters parameters) {
        IndependenceTest test = new FisherZ().getTest(data, parameters);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setVerbose(false);

        knowledge = knowledge.copy();
        knowledge.clear();

        for (Node input : inputs) {
            knowledge.addToTier(0, input.getName());
        }

        for (Node output : outputs) {
            knowledge.addToTier(1, output.getName());
        }

        knowledge.setTierForbiddenWithin(0, true);
        knowledge.setTierForbiddenWithin(1, true);

        DmMerge search = new DmMerge(test);
        search.setKnowledge(knowledge);

        try {
            return search.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}