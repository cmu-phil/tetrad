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
 * Represents a markov check statistic that calculates the Binomial P value for whether the p-values for the estimated
 * graph are distributed as U(0, 1). This version reports the best p-value out of 10 repetitions.
 *
 * @author josephramsey
 */
public class MarkovCheckBinomialPBestOf10 implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Wrapper for independence tests used in statistical computations. This field represents the specific
     * implementation of the IndependenceWrapper interface that supplies methods for determining independence between
     * variables, describing the type of data involved, and retrieving test parameters.
     * <p>
     * It is used to calculate statistical measures and perform conditional independence tests based on provided data
     * and parameters.
     */
    private final IndependenceWrapper independenceWrapper;
    /**
     * Specifies the type of conditioning set used in the Markov check to assess conditional independence relationships
     * within the graph. The conditioning set determines the subset of variables to condition on during independence
     * testing, which impacts how independence and dependence relationships are evaluated based on the causal structure
     * of the graph.
     * <p>
     * This variable is integral to configuring the statistical computation of independence facts in the
     * MarkovCheckBinomialPBestOf10 class.
     */
    private final ConditioningSetType conditioningSetType;

    /**
     * Calculates the Kolmogorov-Smirnoff P value for the Markov check of whether the p-values for the estimated graph
     * are distributed as U(0, 1).
     *
     * @param independenceWrapper An instance of {@link IndependenceWrapper} used to encapsulate and perform
     *                            independence tests on the dataset with specific configurations.
     * @param conditioningSetType The type of conditioning set employed during Markov checks, represented by the
     *                            {@link ConditioningSetType} enum; this dictates how variables are conditioned in
     *                            independence tests.
     */
    public MarkovCheckBinomialPBestOf10(IndependenceWrapper independenceWrapper, ConditioningSetType conditioningSetType) {
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
        return "MC-BP10";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Binomial P; best of 10 reps";
    }

    /**
     * Calculates the Binomial P value for the Markov check of whether the p-values for the estimated graph are
     * distributed as U(0, 1).
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The Binomial P value.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {

        if (dataModel == null) {
            throw new IllegalArgumentException("Data model is null.");
        }

        IndependenceTest test = independenceWrapper.getTest(dataModel, parameters);

        // Find the best of 10 repetitions
        double max = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 10; i++) {
            MarkovCheck markovCheck = new MarkovCheck(estGraph, test, conditioningSetType);
            markovCheck.generateResults(true, true);
            double p = markovCheck.getBinomialPValue(true);
            if (p > max) {
                max = p;
            }
        }

        return max;
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

