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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;

/**
 * Calculates the Anderson Darling P value for the Markov check of whether the p-values for the estimated graph are
 * distributed as U(0, 1).
 *
 * @author josephramsey
 */
public class MarkovCheckAdPasses implements Statistic, MarkovCheckerStatistic {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Provides an interface to independence tests for evaluating conditional independence relationships in a data set.
     * It is used as a dependency for statistical computations and Markov checks within the containing class.
     */
    private final IndependenceWrapper independenceWrapper;
    /**
     * Represents the type of conditioning set to be used in Markov checks for evaluating independence relationships in
     * a graph or data set. The conditioning set determines which variables are conditioned on during independence
     * tests, influencing the nature and scope of the independence facts being tested.
     * <p>
     * This variable works in conjunction with {@link IndependenceWrapper} to perform statistical evaluations related to
     * Markov property testing. Its specific type is defined by the {@link ConditioningSetType} enum, which provides
     * various options such as GLOBAL_MARKOV, LOCAL_MARKOV, and others, each corresponding to a particular approach to
     * selecting the conditioning set.
     */
    private final ConditioningSetType conditioningSetType;

    /**
     * Calculates the Anderson Darling P value for the Markov check of whether the p-values for the estimated graph are
     * distributed as U(0, 1).
     *
     * @param independenceWrapper An instance of {@link IndependenceWrapper} used to encapsulate and perform
     *                            independence tests on the dataset with specific configurations.
     * @param conditioningSetType The type of conditioning set employed during Markov checks, represented by the
     *                            {@link ConditioningSetType} enum; this dictates how variables are conditioned in
     *                            independence tests.
     */
    public MarkovCheckAdPasses(IndependenceWrapper independenceWrapper, ConditioningSetType conditioningSetType) {
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
        return "MC-ADPass";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Markov Check Anderson Darling P Passes (1 = p > 0.05, 0 = p <= 0.05)";
    }

    /**
     * Calculates the Anderson Darling p-value > 0.05.
     *
     * @param trueDag
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters.
     * @return 1 if p > 0.05, 0 if not.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double p = new MarkovCheckAndersonDarlingP(independenceWrapper, conditioningSetType).getValue(trueDag, trueGraph, estGraph, dataModel, new Parameters());
        double alpha = parameters.getDouble(Params.MC_ALPHA);

        System.out.println("Markov check alpha = " + alpha);

        return p > alpha ? 1.0 : 0.0;
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

