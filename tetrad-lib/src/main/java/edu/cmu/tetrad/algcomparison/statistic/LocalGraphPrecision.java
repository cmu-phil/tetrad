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

import edu.cmu.tetrad.algcomparison.statistic.utils.LocalGraphConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

/**
 * The LocalGraphPrecision class implements the Statistic interface and represents the Local Graph Precision statistic.
 * It calculates the precision between the true graph and the estimated graph locally.
 */
public class LocalGraphPrecision implements Statistic {

    /**
     * The default constructor of the LocalGraphPrecision class.
     */
    public LocalGraphPrecision() {
    }

    /**
     * This method returns the abbreviation for the statistic.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "LGP";
    }

    /**
     * Returns a short one-line description of this statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Local Graph Precision";
    }

    /**
     * This method calculates the Local Graph Precision. It calculates the precision between the true graph and the
     * estimated graph locally.
     *
     * @param trueGraph  The true graph.
     * @param estGraph   The estimated graph.
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The local graph precision.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        LocalGraphConfusion lgConfusion = new LocalGraphConfusion(trueGraph, estGraph);
        int lgTp = lgConfusion.getTp();
        int lgFp = lgConfusion.getFp();
        return lgTp / (double) (lgTp + lgFp);
    }

    /**
     * This method returns the normalized value of a given statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

