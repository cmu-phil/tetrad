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
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The former name of the statistic now called {@link NumCompatiblePotentiallyDirectedEdgeAncestors}.
 * <p>
 * <b>Backward compatibility only.</b> This class is retained under its original name so that comparison sessions
 * saved by Tetrad 7.6.10 and earlier, which refer to it by this class name, continue to load. New work should use
 * {@link NumCompatiblePotentiallyDirectedEdgeAncestors}, to which every method delegates, so the number reported is the same as the current statistic's.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumCompatiblePossiblyDirectedEdgeAncestors implements Statistic {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public NumCompatiblePossiblyDirectedEdgeAncestors() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return new NumCompatiblePotentiallyDirectedEdgeAncestors().getAbbreviation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return new NumCompatiblePotentiallyDirectedEdgeAncestors().getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        return new NumCompatiblePotentiallyDirectedEdgeAncestors().getValue(trueDag, trueGraph, estGraph, dataModel, parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return new NumCompatiblePotentiallyDirectedEdgeAncestors().getNormValue(value);
    }
}
