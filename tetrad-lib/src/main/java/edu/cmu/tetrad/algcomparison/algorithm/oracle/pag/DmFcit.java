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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * DM-FCIT, removed in Tetrad 8.0.0.
 * <p>
 * <b>Backward compatibility only, and not runnable.</b> This class is retained under its original name so that
 * sessions saved by Tetrad 7.6.10 and earlier, which refer to it by this class name, continue to load: without it
 * the whole session fails to open, not merely this box. Its registration annotations are commented out, so it does
 * not appear in the algorithm chooser.
 * <p>
 * Unlike the other compatibility classes, this one cannot reproduce the original behavior, because the recursive-blocking API this algorithm was written against no
 * longer exists. Running it therefore throws {@link UnsupportedOperationException} with an explanation rather than
 * returning a graph that was computed some other way. A restored session opens, shows whatever graph it had stored,
 * and reports the removal if the search is re-run.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "DM-FCIT",
//        command = "dm-fcit",
//        algoType = AlgType.allow_latent_common_causes
//)
public class DmFcit extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper, TakesIndependenceWrapper, AcceptsKnowledge, ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;
    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public DmFcit() {
    }

    /**
     * Always throws, because the implementation this class wrapped was removed in Tetrad 8.0.0.
     *
     * @param dataModel  ignored
     * @param parameters ignored
     * @return never returns
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        throw new UnsupportedOperationException("DM-FCIT was removed in Tetrad 8.0.0 and cannot be re-run. This "
                                                + "class exists only so that sessions saved by earlier versions "
                                                + "still open; the graph stored in the session is unaffected.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "DM-FCIT (removed in Tetrad 8.0.0; retained so that older sessions load, not runnable)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
