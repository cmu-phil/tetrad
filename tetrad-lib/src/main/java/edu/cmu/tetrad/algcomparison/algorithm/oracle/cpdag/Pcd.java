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
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.work_in_progress.SemBicScoreDeterministic;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * PC.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@Bootstrapping
public class Pcd extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for Pcd.</p>
     */
    public Pcd() {
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        ScoreIndTest test;

        if (dataModel instanceof ICovarianceMatrix) {
            SemBicScoreDeterministic score = new SemBicScoreDeterministic((ICovarianceMatrix) dataModel);
            score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            score.setDeterminismThreshold(parameters.getDouble(Params.DETERMINISM_THRESHOLD));
            test = new ScoreIndTest(score);
        } else if (dataModel instanceof DataSet) {
            SemBicScoreDeterministic score = new SemBicScoreDeterministic(new CovarianceMatrix((DataSet) dataModel));
            score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            score.setDeterminismThreshold(parameters.getDouble(Params.DETERMINISM_THRESHOLD));
            test = new ScoreIndTest(score);
        } else {
            throw new IllegalArgumentException("Expecting a dataset or a covariance matrix.");
        }

        edu.cmu.tetrad.search.Pcd search = new edu.cmu.tetrad.search.Pcd(test);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        Graph search1 = search.search();
        LogUtilsSearch.stampWithBic(search1, dataModel);

        return search1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToCpdag(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "PC (\"Peter and Clark\") Deternimistic";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.DEPTH);
        parameters.add(Params.DETERMINISM_THRESHOLD);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

}

