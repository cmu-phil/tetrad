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

package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * A score wrapper that pools several data sets into one IMaGES score built from an inner score wrapper. This is how
 * "IMaGES" becomes a property of the score rather than a separate algorithm: any score-based algorithm that extends
 * {@code AbstractBootstrapAlgorithm}, when handed a {@code DataModelList}, has its score wrapper replaced by one of
 * these for the duration of the search, so BOSS + SEM-BIC on a list of data sets IS IMaGES-BOSS, and BOSS + BF-BIC is
 * IMaGES with the basis-function score, with no separate wrapper class per combination.
 * <p>
 * The data model passed to {@link #getScore} is ignored; the pooled score is built over the data sets registered for
 * the calling thread (set by the bootstrap loop for each replicate) or, failing that, the default list given at
 * construction. Inner wrappers implementing {@link MultiDataSetScoreWrapper} are asked for a coordinated list of
 * scores so that data-dependent representation choices are shared across data sets; other wrappers are called once
 * per data set.
 * <p>
 * Instances are transient: they exist only during a search and are never serialized as the algorithm's score.
 *
 * @author josephramsey
 */
public final class PooledScoreWrapper implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    private final ScoreWrapper inner;
    private final List<DataModel> defaultDataSets;
    private final transient ThreadLocal<List<DataModel>> threadDataSets = new ThreadLocal<>();

    /**
     * Constructs a pooled score wrapper.
     *
     * @param inner           the score to build for each data set.
     * @param defaultDataSets the data sets pooled when none are registered for the calling thread.
     */
    public PooledScoreWrapper(ScoreWrapper inner, List<DataModel> defaultDataSets) {
        if (inner == null) throw new NullPointerException("inner");
        if (defaultDataSets == null || defaultDataSets.isEmpty()) throw new IllegalArgumentException("No data sets.");
        this.inner = inner;
        this.defaultDataSets = new ArrayList<>(defaultDataSets);
    }

    /**
     * Builds one score per pooled data set and sums them as an IMaGES score.
     *
     * @param ignored    ignored; the pooled data sets are used instead.
     * @param parameters the parameters.
     * @return the pooled score.
     */
    @Override
    public Score getScore(DataModel ignored, Parameters parameters) {
        List<DataModel> dataSets = threadDataSets.get();
        if (dataSets == null) dataSets = defaultDataSets;

        List<Score> scores;
        if (inner instanceof MultiDataSetScoreWrapper multi) {
            scores = multi.getScores(dataSets, parameters);
        } else {
            scores = new ArrayList<>(dataSets.size());
            for (DataModel dataModel : dataSets) scores.add(inner.getScore(dataModel, parameters));
        }
        return new ImagesScore(scores);
    }

    /**
     * Registers the data sets to pool for scores built on the calling thread; pass null to clear.
     *
     * @param dataSets the data sets, or null.
     */
    public void setThreadDataSets(List<DataModel> dataSets) {
        if (dataSets == null) threadDataSets.remove();
        else threadDataSets.set(dataSets);
    }

    /**
     * @return the inner score wrapper.
     */
    public ScoreWrapper getInner() {
        return inner;
    }

    @Override
    public String getDescription() {
        return "Pooled (IMaGES) " + inner.getDescription();
    }

    @Override
    public DataType getDataType() {
        return inner.getDataType();
    }

    @Override
    public List<String> getParameters() {
        return inner.getParameters();
    }

    /**
     * Looks the variable up in the inner wrapper (which records the last data set it built a score for), falling
     * back to the first pooled data set, whose variables are shared by name across all pooled data sets.
     */
    @Override
    public Node getVariable(String name) {
        Node node = inner.getVariable(name);
        return node != null ? node : defaultDataSets.getFirst().getVariable(name);
    }
}
