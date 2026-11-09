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
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * BOSS-FCI, the former name of BFCI.
 * <p>
 * <b>Backward compatibility only.</b> This class is retained under its original name so that sessions saved by
 * Tetrad 7.6.10 and earlier, which refer to it by this class name, continue to load. Its registration annotations
 * are commented out, so it does not appear in the algorithm chooser; new work should use {@link Bfci}. Every method delegates to {@link Bfci}, so a restored session runs the same search as the BFCI box, including any corrections made to it since 7.6.10.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "BOSS-FCI",
//        command = "boss-fci",
//        algoType = AlgType.allow_latent_common_causes
//)
@Bootstrapping
public class BossFci extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper,
        TakesIndependenceWrapper, AcceptsKnowledge, ReturnsBootstrapGraphs,
        TakesCovarianceMatrix, LatentStructureAlgorithm {

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
    public BossFci() {
    }

    /**
     * Constructs a new BOSS-FCI algorithm using the given test and score.
     *
     * @param test  the independence test to use
     * @param score the score to use
     */
    public BossFci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Builds the BFCI algorithm this class defers to, configured from this object's state.
     *
     * @return the configured delegate
     */
    private Bfci delegate() {
        Bfci delegate = new Bfci(this.test, this.score);
        delegate.setKnowledge(this.knowledge);
        return delegate;
    }

    /**
     * Runs the search on the given data by delegating to the successor algorithm and returns the resulting graph.
     *
     * @param dataModel the data to search over.
     * @param parameters the parameters of the search.
     * @return the graph the successor returns.
     * @throws InterruptedException if the search is interrupted.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        return delegate().runSearch(dataModel, parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return delegate().getComparisonGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "BOSS-FCI (retained for sessions saved by Tetrad 7.6.10 and earlier; equivalent to BFCI) using "
               + (this.test == null ? "no test" : this.test.getDescription())
               + " and " + (this.score == null ? "no score" : this.score.getDescription());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return delegate().getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return delegate().getParameters();
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
