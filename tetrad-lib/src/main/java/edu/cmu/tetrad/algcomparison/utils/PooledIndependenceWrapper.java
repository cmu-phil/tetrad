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

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestMulti;
import edu.cmu.tetrad.search.utils.ResolveSepsets;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The test-side counterpart of {@link PooledScoreWrapper}: builds the inner test on each pooled data set and combines
 * them with {@link IndTestMulti} using Fisher's method (default) or Tippett's method on the per-data-set p-values,
 * per {@code Params.POOLED_TEST_METHOD} (data sets are assumed
 * independent of one another, e.g. different subjects or regions). This makes constraint-based algorithms
 * multi-data-set in the same way IMaGES makes score-based ones: PC + Fisher Z on a list of data sets is a pooled PC.
 * <p>
 * The data model passed to {@link #getTest} is ignored; the data sets registered for the calling thread (set by the
 * bootstrap loop for each replicate) or the default list are used. Instances are transient, existing only during a
 * search.
 *
 * @author josephramsey
 */
public final class PooledIndependenceWrapper implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The inner independence wrapper used to construct independence tests for pooled data sets.
     * This wrapper is encapsulated within the {@code PooledIndependenceWrapper} to delegate
     * independence testing on individual data sets while maintaining the pooled data operations.
     * <ul>
     * <li>Responsible for evaluating independence conditional on given inputs.</li>
     * <li>Defines the underlying description, data type, and parameters of the test.</li>
     * <li>Serves as the core component around which the pooled independence tests are built.</li>
     * </ul>
     * It is required to be non-null when constructing a {@code PooledIndependenceWrapper}.
     */
    private final IndependenceWrapper inner;
    /**
     * A defensive copy of the default data sets used when none have been registered for the calling thread via
     * {@link #setThreadDataSets(List)}; all pooled data sets must have the same variables by name.
     */
    private final List<DataModel> defaultDataSets;
    /**
     * A defensive copy of the data sets registered for the calling thread via {@link #setThreadDataSets(List)}; all
     * pooled data sets must have the same variables by name.
     */
    private final transient ThreadLocal<List<DataModel>> threadDataSets = new ThreadLocal<>();

    /**
     * Constructs a pooled independence wrapper.
     *
     * @param inner           the independence wrapper used to build one test per pooled data set.
     * @param defaultDataSets the data sets pooled when none have been registered for the calling thread via
     *                        {@link #setThreadDataSets(List)}; a defensive copy is kept. All pooled data sets must
     *                        have the same variables by name.
     * @throws NullPointerException     if {@code inner} is null.
     * @throws IllegalArgumentException if {@code defaultDataSets} is null or empty.
     */
    public PooledIndependenceWrapper(IndependenceWrapper inner, List<DataModel> defaultDataSets) {
        if (inner == null) throw new NullPointerException("inner");
        if (defaultDataSets == null || defaultDataSets.isEmpty()) throw new IllegalArgumentException("No data sets.");
        this.inner = inner;
        this.defaultDataSets = new ArrayList<>(defaultDataSets);
    }

    /**
     * Builds one test per pooled data set and combines them into an {@link IndTestMulti}, which pools the
     * per-data-set p-values by the method selected with {@link #method(Parameters)} (Fisher's method by default,
     * or Tippett's). The data sets used are those registered for the calling thread by
     * {@link #setThreadDataSets(List)}, or the default list given at construction if none are registered.
     *
     * @param ignored    ignored; the pooled data sets are used instead of this argument.
     * @param parameters the parameters, passed through to the inner wrapper for every data set and consulted for
     *                   {@code Params.POOLED_TEST_METHOD}.
     * @return the pooled independence test.
     */
    @Override
    public IndependenceTest getTest(DataModel ignored, Parameters parameters) {
        List<DataModel> dataSets = threadDataSets.get();
        if (dataSets == null) dataSets = defaultDataSets;

        List<IndependenceTest> tests = new ArrayList<>(dataSets.size());
        for (DataModel dataModel : dataSets) tests.add(inner.getTest(dataModel, parameters));
        return new IndTestMulti(tests, method(parameters));
    }

    /**
     * Returns the p-value combining method selected by {@code Params.POOLED_TEST_METHOD}. The value "tippett"
     * (case-insensitive, surrounding whitespace ignored) selects Tippett's minimum-p method with a Sidak
     * adjustment; any other value, or no value, selects Fisher's method. Both assume the pooled data sets are
     * independent of one another.
     *
     * @param parameters the parameters.
     * @return the combining method.
     */
    public static ResolveSepsets.Method method(Parameters parameters) {
        String name = parameters.getString(Params.POOLED_TEST_METHOD, "fisher");
        return "tippett".equalsIgnoreCase(name.trim()) ? ResolveSepsets.Method.tippett : ResolveSepsets.Method.fisher;
    }

    /**
     * Registers the data sets to pool for tests subsequently built on the calling thread, overriding the default
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
     * Returns the independence wrapper that is built on each pooled data set.
     *
     * @return the inner independence wrapper.
     */
    public IndependenceWrapper getInner() {
        return inner;
    }

    /**
     * Returns a description of the pooled test, formed by prefixing the inner wrapper's description with "Pooled".
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return "Pooled " + inner.getDescription();
    }

    /**
     * Returns the data type the inner independence wrapper accepts; pooling does not change it.
     *
     * @return the data type of the inner independence wrapper.
     */
    @Override
    public DataType getDataType() {
        return inner.getDataType();
    }

    /**
     * Returns the names of the parameters the inner independence wrapper uses. The combining method is read from
     * {@code Params.POOLED_TEST_METHOD}, which is not added to this list here; callers that expose it should add
     * it themselves.
     *
     * @return the parameter names of the inner independence wrapper.
     */
    @Override
    public List<String> getParameters() {
        return inner.getParameters();
    }
}
