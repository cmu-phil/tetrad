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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * IdaCheckAvgSquaredDifference is a class that implements the Statistic interface. It calculates the average squared
 * difference between the estimated and true values for a given data model and graphs.
 */
public class IdaCheckAvgSquaredDifference implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * IdaCheckAvgSquaredDifference is a class that implements the Statistic interface. It calculates the average
     * minimum squared difference between the estimated and true values for a given data model and graphs.
     */
    public IdaCheckAvgSquaredDifference() {

    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "IDA-ASD-ET";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "IDA check Avg Squared Diff Est True";
    }

    /**
     * Retrieves the value of the statistic, which is the average squared difference between the estimated and true
     * values for a given data model and graphs.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The value of the statistic.
     * @throws IllegalArgumentException if the data model is null.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {

        if (dataModel == null) {
            throw new IllegalArgumentException("Data model is null.");
        }

        if (!estGraph.paths().isLegalMpdag()) {
            return Double.NaN;
        }

        SemPm trueSemPm = new SemPm(trueGraph);
        SemIm trueSemIm = new SemEstimator((DataSet) dataModel, trueSemPm).estimate();

        IdaCheck idaCheck = new IdaCheck(estGraph, (DataSet) dataModel, trueSemIm);
        return idaCheck.getAverageSquaredDistance(idaCheck.getOrderedPairs());
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

