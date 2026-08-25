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

    private final IndependenceWrapper inner;
    private final List<DataModel> defaultDataSets;
    private final transient ThreadLocal<List<DataModel>> threadDataSets = new ThreadLocal<>();

    /**
     * Constructs a pooled independence wrapper.
     *
     * @param inner           the test to build for each data set.
     * @param defaultDataSets the data sets pooled when none are registered for the calling thread.
     */
    public PooledIndependenceWrapper(IndependenceWrapper inner, List<DataModel> defaultDataSets) {
        if (inner == null) throw new NullPointerException("inner");
        if (defaultDataSets == null || defaultDataSets.isEmpty()) throw new IllegalArgumentException("No data sets.");
        this.inner = inner;
        this.defaultDataSets = new ArrayList<>(defaultDataSets);
    }

    /**
     * Builds one test per pooled data set and combines them by Fisher's method.
     *
     * @param ignored    ignored; the pooled data sets are used instead.
     * @param parameters the parameters.
     * @return the pooled test.
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
     * The combining method selected by {@code Params.POOLED_TEST_METHOD}: "tippett" for Tippett's min-p (Sidak
     * adjusted), anything else for Fisher's method.
     *
     * @param parameters the parameters.
     * @return the method.
     */
    public static ResolveSepsets.Method method(Parameters parameters) {
        String name = parameters.getString(Params.POOLED_TEST_METHOD, "fisher");
        return "tippett".equalsIgnoreCase(name.trim()) ? ResolveSepsets.Method.tippett : ResolveSepsets.Method.fisher;
    }

    /**
     * Registers the data sets to pool for tests built on the calling thread; pass null to clear.
     *
     * @param dataSets the data sets, or null.
     */
    public void setThreadDataSets(List<DataModel> dataSets) {
        if (dataSets == null) threadDataSets.remove();
        else threadDataSets.set(dataSets);
    }

    /**
     * @return the inner independence wrapper.
     */
    public IndependenceWrapper getInner() {
        return inner;
    }

    @Override
    public String getDescription() {
        return "Pooled " + inner.getDescription();
    }

    @Override
    public DataType getDataType() {
        return inner.getDataType();
    }

    @Override
    public List<String> getParameters() {
        return inner.getParameters();
    }
}
