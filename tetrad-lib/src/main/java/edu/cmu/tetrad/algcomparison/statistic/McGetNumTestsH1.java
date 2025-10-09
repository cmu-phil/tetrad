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

package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Calculates the number of tests Kolmogorov-Smirnoff under the alternative hypothesis H1 of dependennce for the Markov
 * check of whether the p-values for the estimated graph are distributed as U(0, 1).
 *
 * @author josephramsey
 */
public class McGetNumTestsH1 implements Statistic, MarkovCheckerStatistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * An instance of the IndependenceWrapper interface used to perform independence tests in the context of statistical
     * computations and graph evaluations. It provides the necessary methods to retrieve and configure independence
     * tests, as well as to obtain a description of the specific independence testing algorithm being employed.
     */
    private final IndependenceWrapper independenceWrapper;
    /**
     * Specifies the type of conditioning set used in the Markov check for evaluating independence or dependence
     * relationships. This variable is associated with the configuration of the independence tests and determines how
     * the conditioning set is constructed based on the selected type from the {@link ConditioningSetType} enum. The
     * conditioning set influences the statistical properties of the independence tests performed during Markov checks.
     */
    private final ConditioningSetType conditioningSetType;

    /**
     * Calculates the number of tests for the Markov check of whether the p-values for the estimated graph are
     * distributed as U(0, 1).
     *
     * @param independenceWrapper An instance of {@link IndependenceWrapper} used to encapsulate and perform
     *                            independence tests on the dataset with specific configurations.
     * @param conditioningSetType The type of conditioning set employed during Markov checks, represented by the
     *                            {@link ConditioningSetType} enum; this dictates how variables are conditioned in
     *                            independence tests.
     */
    public McGetNumTestsH1(IndependenceWrapper independenceWrapper, ConditioningSetType conditioningSetType) {
        this.independenceWrapper = independenceWrapper;
        this.conditioningSetType = conditioningSetType;
    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "MC-H1-NumTests";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Num Tests H0";
    }

    /**
     * Calculates the number of tests done under the null hypothesis of independence for the Markov check of whether the
     * p-values for the estimated graph are distributed as U(0, 1).
     *
     * @param trueDag
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The Anderson Darling P value.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {

        if (dataModel == null) {
            throw new IllegalArgumentException("Data model is null.");
        }

        IndependenceTest test = independenceWrapper.getTest(dataModel, parameters);
        MarkovCheck markovCheck = new MarkovCheck(estGraph, test, conditioningSetType);
        markovCheck.generateResults(false, true);
        return markovCheck.getNumTests(false);
    }

    /**
     * Calculates the normalized value of a statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

