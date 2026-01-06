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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Calculates the structural Hamming distance (SHD) between the estimated graph and the true graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StructuralHammingDistance implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Indicates whether the Structural Hamming Distance (SHD) calculation should be based on the CPDAG (Completed
     * Partially Directed Acyclic Graph) form of the true graph rather than its original PDAG (Partially Directed
     * Acyclic Graph) form. When set to true, the SHD computation compares the estimated graph to the CPDAG of the true
     * graph; otherwise, it compares the estimated graph to the true PDAG.
     */
    private boolean compareToCpdag = false;

    /**
     * Constructs the statistic.
     */
    public StructuralHammingDistance() {
    }

    /**
     * Constructs a StructuralHammingDistance instance with the specified comparison mode.
     *
     * @param compareToCpdag A boolean flag indicating whether the Structural Hamming Distance should be calculated by
     *                       comparing the estimated graph to the CPDAG (Completed Partially Directed Acyclic Graph) of
     *                       the true graph. If true, the calculation uses the CPDAG; otherwise, it uses the original
     *                       PDAG (Partially Directed Acyclic Graph) of the true graph.
     */
    public StructuralHammingDistance(boolean compareToCpdag) {
        this.compareToCpdag = compareToCpdag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "SHD " + (compareToCpdag ? "(CPDAG)" : "(PDAG)");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Structural Hamming Distance" + (compareToCpdag ? " compared to CDAG of true PDAG" : " compared to true PDAG");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        return GraphSearchUtils.structuralhammingdistance(trueGraph, estGraph, compareToCpdag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(0.001 * value);
    }
}

