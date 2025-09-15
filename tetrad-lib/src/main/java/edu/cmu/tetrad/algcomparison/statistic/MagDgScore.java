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
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.work_in_progress.MagDgBicScore;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Takes a MAG in a PAG using Zhang's method and then reports the MAG DG BIC score for it.
 */
public class MagDgScore implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public MagDgScore() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "MagDgScore";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "MAG DG BIC score for the Zhang MAG in the given PAG.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet))
            throw new IllegalArgumentException("Expecting a dataset for MAG DG Score.");

        Graph mag = GraphTransforms.zhangMagFromPag(estGraph);
        MagDgBicScore magDgScore = new MagDgBicScore((DataSet) dataModel);
        magDgScore.setMag(mag);
        List<Node> nodes = mag.getNodes();
        double score = 0.0;

        for (Node node : nodes) {
            int i = nodes.indexOf(node);
            var parents = mag.getNodesInTo(node, Endpoint.ARROW);
            int[] _p = new int[parents.size()];
            for (int j = 0; j < parents.size(); j++) {
                _p[j] = nodes.indexOf(parents.get(j));
            }
            score += magDgScore.localScore(i, _p);
        }

        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return (1 + tanh(value / 1.0e8)) / 2;
    }
}

