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

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.TrekMimic;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * Benchmark runner for Trek-MIMIC.
 *
 * @author josephramsey
 */
public final class TrekMimicRunner implements MimicSearchRunner {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "TM";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph run(DataSet data, Knowledge knowledge, List<Node> inputs, List<Node> outputs, Parameters parameters) {
        TrekMimic search = new TrekMimic();

        search.setInputs(inputs);
        search.setOutputs(outputs);

        try {
            return search.search(data, parameters);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}