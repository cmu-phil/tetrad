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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * FCIT-style PC:
 * - starts from complete undirected pattern
 * - FAS-style removals by depth
 * - after each removal: orient colliders + Meek + CPDAG legality gate
 *
 * @author josephramsey
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "PC-FCIT-Style",
//        command = "pc_fcit_style",
//        algoType = AlgType.forbid_latent_common_causes
//)
//@Bootstrapping
public class PcFcitStyle extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Independence test wrapper. */
    private IndependenceWrapper test;

    /** Background knowledge. */
    private Knowledge knowledge = new Knowledge();

    public PcFcitStyle() {}

    public PcFcitStyle(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        // Time series lagging support (matches the PC wrapper pattern).
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }

            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        IndependenceTest indTest = getIndependenceWrapper().getTest(dataModel, parameters);

        // Cache all CI queries (optional but usually beneficial here too).
        indTest = new CachedIndependenceQueries(indTest);

        // Build and configure the FCIT-style PC search.
        edu.cmu.tetrad.search.PcFcitStyle search = new edu.cmu.tetrad.search.PcFcitStyle(indTest);

        search.setKnowledge(this.knowledge);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setStable(parameters.getBoolean(Params.STABLE_FAS));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        // Existing Params includes ALLOW_BIDIRECTED; wire it (default false in your search).
//        search.setAllowBidirected(parameters.getBoolean(Params.ALLOW_BIDIRECTED));

        // Keep the conservative default (cycle guard on). If you later add a Params key,
        // just wire it here:
        // search.setForbidDirectedCycles(parameters.getBoolean(Params.FORBID_DIRECTED_CYCLES));
        // search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));

        Graph graph = search.search();

        stampWithBic(graph, dataModel);

        return graph;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    @Override
    public String getDescription() {
        return "PC-FCIT-Style using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);          // used as "stable" in this algorithm
        parameters.add(Params.ALLOW_BIDIRECTED);    // optional collider gate behavior
        parameters.add(Params.DEPTH);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.TIME_LAG_REPLICATING_GRAPH); // kept for UI consistency; used by TsUtils workflow
        parameters.add(Params.VERBOSE);

        // Not used (on purpose): COLLIDER_ORIENTATION_STYLE, FDR_Q, etc.
        // This algorithm always does sepset-style colliders after each move.

        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}