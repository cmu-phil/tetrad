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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * Implements a really simple idea for building pure clusters, just using the Purify algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BpcTetradPurifyWashdown {
    private final List<Node> variables;
    private final TetradTest test;

//    /**
//     * Construct the algorithm using a covariance matrix.
//     *
//     * @param cov      A covariance matrix.
//     * @param testType A Test type.
//     * @param alpha    An alpha cutoff
//     * @see edu.cmu.tetrad.data.CovarianceMatrix
//     * @see BpcTestType
//     */
//    public BpcTetradPurifyWashdown(ICovarianceMatrix cov, BpcTestType testType, double alpha) {
//        this.variables = cov.getVariables();
//        this.test = new TetradTestContinuous(cov, testType, alpha);
//    }

    /**
     * Construct the algorithm using a data set.
     *
     * @param dataSet  A DataSet.
     * @param testType A Test type.
     * @param alpha    An alpha cutoff
     * @see DataSet
     * @see BpcTestType
     */
    public BpcTetradPurifyWashdown(DataSet dataSet, BpcTestType testType, double alpha) {
        this.variables = dataSet.getVariables();
        this.test = new TetradTestContinuous(dataSet, testType, alpha);
    }

    /**
     * Runs the search and returns a graph.
     *
     * @return The discovered graph.
     */
    public Graph search() {
        IPurify purify = new PurifyTetradBased(this.test);
        List<Node> variables = new ArrayList<>(this.variables);
        List<List<Node>> clustering = new ArrayList<>();
        List<Node> disgards;
        List<Node> _disgards;

        do {
            _disgards = calculateDisgards(clustering, variables);
            clustering.add(_disgards);
            clustering = purify.purify(clustering);
            disgards = calculateDisgards(clustering, variables);
        } while (!new HashSet<>(disgards).equals(new HashSet<>(_disgards)));

        Graph graph = new EdgeListGraph();

        clustering.removeIf(cluster -> cluster.size() < 3);

        for (int i = 0; i < clustering.size(); i++) {
            List<Node> cluster = clustering.get(i);
            Node latent = new GraphNode("_L" + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            graph.addNode(latent);

            for (Node node : cluster) {
                graph.addNode(node);
                graph.addDirectedEdge(latent, node);
            }
        }

        return graph;

    }

    private List<Node> calculateDisgards(List<List<Node>> clustering, List<Node> variables) {
        List<Node> disgards = new ArrayList<>();

        NODE:
        for (Node node : variables) {
            for (List<Node> cluster : clustering) {
                if (cluster.contains(node)) {
                    continue NODE;
                }
            }

            disgards.add(node);
        }

        return disgards;
    }


}





