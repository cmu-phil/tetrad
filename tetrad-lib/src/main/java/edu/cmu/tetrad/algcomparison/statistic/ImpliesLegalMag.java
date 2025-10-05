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
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.utils.GraphLegalityCheck;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.HashSet;
import java.util.List;

/**
 * Implies Legal MAG
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ImpliesLegalMag implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LegalPag.</p>
     */
    public ImpliesLegalMag() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "ImpliesMag";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the estimated graph implies a legal MAG, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        List<Node> latent = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.LATENT).toList();

        List<Node> measured = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.MEASURED).toList();

        List<Node> selection = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        Graph mag = GraphTransforms.zhangMagFromPag(estGraph);
        GraphLegalityCheck.LegalMagRet legalPag = GraphLegalityCheck.isLegalMag(estGraph, new HashSet<>(selection));

        if (legalPag.isLegalMag()) {
            return 1.0;
        } else {
            return 0.0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

