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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.search.test.IndTestStratifiedRank;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.StratifiedRankTests;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the stratified (domain-pooled) rank conditional independence test, IndTestStratifiedRank -- the Phase
 * 1 test of LCD-NOD (Dong, Dai, Ng, Spirtes, and Zhang, ICML 2026). For data gathered in several domains (batches,
 * sites, cohorts, countries) whose causal structure is shared but whose linear coefficients, means, and noise
 * variances may differ by domain, each independence question is answered by pooling the within-domain Bartlett
 * statistic over domains, rather than from the covariance of the domains mixed together, which over-rejects true
 * independences and can mask true dependences.
 * <p>
 * The domain is a column of the data set, named by the domainColumn parameter: a discrete column, or a continuous
 * column whose values are all whole numbers. It is removed from the variable list and used only to split the rows.
 * If no column of that name exists but the data set has exactly one discrete column, that column is used and a note
 * is logged; otherwise the error message lists the discrete columns found.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "Stratified Rank (Domain-Pooled)",
        command = "stratified-rank-test",
        dataType = {DataType.Continuous, DataType.Mixed}
)
@LinearGaussian
public class StratifiedRank implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public StratifiedRank() {
    }

    /**
     * Gets an independence test based on the given data model and parameters. The data model must be a tabular data
     * set containing the domain column; covariance matrices cannot be used, since the rows must be split by domain.
     *
     * @param dataModel  The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An IndependenceTest object.
     * @throws IllegalArgumentException if the dataModel is not a tabular data set, or the domain column cannot be
     *                                  resolved.
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        dataModel = MissingDataUtils.gate(dataModel, parameters, false, "Stratified Rank (Domain-Pooled)");

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("The Stratified Rank test needs a tabular data set containing the "
                                               + "domain column; it cannot be run on a covariance matrix, since the "
                                               + "rows must be split by domain.");
        }

        String domainColumn = StratifiedRankTests.resolveDomainColumn(dataSet,
                parameters.getString(Params.DOMAIN_COLUMN));
        double alpha = parameters.getDouble(Params.ALPHA);

        IndTestStratifiedRank test = new IndTestStratifiedRank(dataSet, domainColumn, alpha);
        test.setNumPermutations(parameters.getInt(Params.STRATIFIED_PERMUTATIONS));
        return test;
    }

    /**
     * Retrieves the description of the test.
     *
     * @return The description of the test.
     */
    @Override
    public String getDescription() {
        return "Stratified Rank (Bartlett statistic pooled over the domains of the domain column)";
    }

    /**
     * Retrieves the data type of the independence test.
     *
     * @return The data type of the independence test.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Retrieves the parameters of the test.
     *
     * @return A list of strings representing the parameters of the test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.DOMAIN_COLUMN);
        params.add(Params.ALPHA);
        params.add(Params.STRATIFIED_PERMUTATIONS);
        return params;
    }
}
