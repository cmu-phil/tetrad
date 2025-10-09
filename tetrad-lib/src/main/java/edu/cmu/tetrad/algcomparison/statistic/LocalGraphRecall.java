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
 * LocalGraphRecall implements the Statistic interface and represents the local graph recall statistic. It calculates
 * the recall of the estimated graph with respect to the true graph. The recall is defined as the ratio of true
 * positives (TP) to the sum of true positives and false negatives (TP + FN).
 */
public class LocalGraphRecall implements Statistic {

    /**
     * The default constructor of the LocalGraphRecall class.
     */
    public LocalGraphRecall() {

    }

    @Override
    public String getAbbreviation() {
        return "LGR";
    }

    @Override
    public String getDescription() {
        return "Local Graph Recall";
    }

    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        LocalGraphConfusion lgConfusion = new LocalGraphConfusion(trueGraph, estGraph);
        int lgTp = lgConfusion.getTp();
        int lgFn = lgConfusion.getFn();
        return lgTp / (double) (lgTp + lgFn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}

