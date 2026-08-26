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

    /**
     * Represents the inner score wrapper used to calculate individual scores for each
     * pooled data set. This field is immutable and is utilized to delegate the scoring
     * logic to the underlying implementation of {@link ScoreWrapper}.
     *
     * The inner score wrapper is essential for coordinating the scoring process within
     * the pooled context. If the provided score wrapper implements {@link MultiDataSetScoreWrapper},
     * its specific scoring mechanism is utilized.
     *
     * This field is initialized during the construction of the containing {@code PooledScoreWrapper}
     * and must not be null. Attempts to construct with a null value result in a
     * {@link NullPointerException}.
     */
    private final ScoreWrapper inner;
    /**
     * A defensive copy of the default data sets used when none have been registered for the calling thread via
     * {@link #setThreadDataSets(List)}.
     */
    private final List<DataModel> defaultDataSets;
    /**
     * A thread-local storage variable for maintaining thread-specific lists of {@link DataModel} instances.
     * This variable allows each thread to register and manage its own collection of data sets independently,
     * enabling concurrent operations such as parallel searches or bootstrap replicates.
     *
     * If no data sets are registered on a particular thread, default data sets, as specified at the time of
     * the containing class's construction, are used instead. To register data sets for the current thread,
     * the {@link PooledScoreWrapper#setThreadDataSets(List)} method should be utilized.
     *
     * The thread-local nature ensures that the data sets registered by one thread do not interfere with
     * those registered by another thread, supporting safe parallel usage in multi-threaded environments.
     */
    private final transient ThreadLocal<List<DataModel>> threadDataSets = new ThreadLocal<>();

    /**
     * Constructs a pooled score wrapper.
     *
     * @param inner           the score wrapper used to build one score per pooled data set; if it implements
     *                        {@link MultiDataSetScoreWrapper}, its coordinated multi-data-set method is used instead.
     * @param defaultDataSets the data sets pooled when none have been registered for the calling thread via
     *                        {@link #setThreadDataSets(List)}; a defensive copy is kept. All pooled data sets must
     *                        have the same variables by name.
     * @throws NullPointerException     if {@code inner} is null.
     * @throws IllegalArgumentException if {@code defaultDataSets} is null or empty.
     */
    public PooledScoreWrapper(ScoreWrapper inner, List<DataModel> defaultDataSets) {
        if (inner == null) throw new NullPointerException("inner");
        if (defaultDataSets == null || defaultDataSets.isEmpty()) throw new IllegalArgumentException("No data sets.");
        this.inner = inner;
        this.defaultDataSets = new ArrayList<>(defaultDataSets);
    }

    /**
     * Builds one score per pooled data set and combines them into an {@link ImagesScore}, which sums the local
     * scores across data sets. The data sets used are those registered for the calling thread by
     * {@link #setThreadDataSets(List)}, or the default list given at construction if none are registered.
     *
     * @param ignored    ignored; the pooled data sets are used instead of this argument.
     * @param parameters the parameters, passed through to the inner wrapper for every data set.
     * @return the pooled IMaGES score.
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
     * Registers the data sets to pool for scores subsequently built on the calling thread, overriding the default
     * list. The registration is thread-local, so concurrent searches (e.g., bootstrap replicates run in parallel)
     * can each pool their own resampled data sets through a single shared wrapper. Pass null to clear the
     * registration and revert to the default list.
     *
     * @param dataSets the data sets to pool on this thread, or null to clear.
     */
    public void setThreadDataSets(List<DataModel> dataSets) {
        if (dataSets == null) threadDataSets.remove();
        else threadDataSets.set(dataSets);
    }

    /**
     * Returns the score wrapper that is built on each pooled data set.
     *
     * @return the inner score wrapper.
     */
    public ScoreWrapper getInner() {
        return inner;
    }

    /**
     * Returns a description of the pooled score, formed by prefixing the inner wrapper's description with
     * "Pooled (IMaGES)".
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return "Pooled (IMaGES) " + inner.getDescription();
    }

    /**
     * Returns the data type the inner score wrapper accepts; pooling does not change it.
     *
     * @return the data type of the inner score wrapper.
     */
    @Override
    public DataType getDataType() {
        return inner.getDataType();
    }

    /**
     * Returns the names of the parameters the inner score wrapper uses. Pooling itself adds no parameters.
     *
     * @return the parameter names of the inner score wrapper.
     */
    @Override
    public List<String> getParameters() {
        return inner.getParameters();
    }

    /**
     * Returns the variable with the given name. The inner wrapper is consulted first, since it records the last data
     * set it built a score for; if that yields nothing, the first pooled data set is used, whose variables are shared
     * by name across all pooled data sets.
     *
     * @param name the variable name.
     * @return the variable, or null if no pooled data set has a variable of that name.
     */
    @Override
    public Node getVariable(String name) {
        Node node = inner.getVariable(name);
        return node != null ? node : defaultDataSets.getFirst().getVariable(name);
    }
}
