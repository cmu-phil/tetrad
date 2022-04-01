///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.ArrayList;
import java.util.List;


/**
 * Implements the Washdown algorithm,
 * <p>
 * Initialization: Current Model = M1 := single factor pure model in
 * which L1 is a cause of all Vi in v
 * <p>
 * 1) Purify step:  run Purify on getModel model.
 * 2) Create new model: for each indicator Vi in Vdiscard (variables
 * discarded) by Purify in step 1, move Vi from being an effect of its
 * latent Lj to being an effect of Lj+1, where if Lj+1 does not exist,
 * create it and freely correlate Lj+1 with all latents L1 to Lj.
 * 3) stop check:  estimate new model and stop if goodness-of-fit test
 * passes, else getModel model:= new model, and go to step 1.
 * <p>
 * Purify
 * 1) Vkeep := v, Vdiscard := null set
 * 2) Do goodness-of-fit test on getModel model Mc, stop if Mc passes,
 * return Vkeep and Vdiscard
 * 2) For each indicator Vi, do goodness-of-fit test on Mc - Vi, store
 * foodness-of-fit test score as gof(Vi)
 * 3) New getModel model := Mc - Vi, for Vi with max gof(Vi) from step 3.
 * 4) Vkeep:= Vkeep - Vi, Vdiscard:= Vdiscard + Vi
 * 5) Go to step 2.
 * <p>
 * Clearly we can use any goodness of fit test we think is appropriate -
 * the default being the chi-square test.
 *
 * @author Joseph Ramsey
 */

public class Washdown {

    private ICovarianceMatrix cov;
    private DataSet dataSet;
    private final List<Node> variables;
    private final double alpha;

    public Washdown(ICovarianceMatrix cov, double alpha) {
        this.cov = cov;
        this.variables = cov.getVariables();
        this.alpha = alpha;
    }

    public Washdown(DataSet data, double alpha) {
        this.dataSet = data;
        this.variables = data.getVariables();
        this.alpha = alpha;
    }

    public Graph search() {
        List<List<Node>> clusters = new ArrayList<>();
        clusters.add(new ArrayList<>(this.variables));

        double pValue;

        do {
            clusters = purify(clusters);


            List<Node> disgards = getDiscards(clusters, this.variables);

            clusters.add(disgards);

            pValue = pValue(clusters);

            System.out.println("\nSearch PValue = " + pValue + " clusters = " + clusters + "\n");
        } while (pValue < this.alpha);

        return pureMeasurementModel(clusters);
    }

    private List<Node> getDiscards(List<List<Node>> clusters, List<Node> variables) {
        List<Node> disgards = new ArrayList<>();

        for (Node node : variables) {
            boolean found = false;

            for (List<Node> cluster : clusters) {
                if (cluster.contains(node)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                disgards.add(node);
            }
        }

        return disgards;
    }

    private List<List<Node>> purify(List<List<Node>> clusters) {
        List<Node> keep = new ArrayList<>(this.variables);
        double bestGof = gof(clusters);
        System.out.println("Purify Best GOF = " + bestGof + " clusters = " + clusters);

        while (true) {

            if (pValue(clusters) > this.alpha) {
                return clusters;
            }

//            double bestGof = Double.POSITIVE_INFINITY;
            Node bestNode = null;

            for (Node node : keep) {
                List<List<Node>> _clusters = removeVar(node, clusters);
                double _gof = gof(_clusters);
                System.out.println("     GOF = " + gof(_clusters) + "P value = " + pValue(_clusters) + " clusters = " + _clusters);

                if (_gof < bestGof) {
                    bestGof = _gof;
                    bestNode = node;
                }
            }

            if (bestNode == null) {
                return clusters;
            }

            clusters = removeVar(bestNode, clusters);
            keep.remove(bestNode);
        }
    }

    private List<List<Node>> removeVar(Node node, List<List<Node>> clusters) {
        List<List<Node>> _clusters = new ArrayList<>();

        for (List<Node> cluster : clusters) {
            List<Node> _cluster = new ArrayList<>(cluster);
            _cluster.remove(node);
            if (!cluster.isEmpty()) {
                _clusters.add(_cluster);
            }
        }

        return _clusters;
    }

    /**
     * @return the p value of the given model.
     */

    private double gof(List<List<Node>> clusters) {
        clusters = removeEmpty(clusters);

        Graph graph = pureMeasurementModel(clusters);
        SemPm pm = new SemPm(graph);

        SemEstimator estimator;

        if (this.cov != null) {
            estimator = new SemEstimator(this.cov, pm);
        } else {
            estimator = new SemEstimator(this.dataSet, pm);
        }

        SemIm est = estimator.estimate();

        return est.getBicScore();
    }

    private double pValue(List<List<Node>> clusters) {
        clusters = removeEmpty(clusters);

        Graph graph = pureMeasurementModel(clusters);
        SemPm pm = new SemPm(graph);

        SemEstimator estimator;

        if (this.cov != null) {
            estimator = new SemEstimator(this.cov, pm);
        } else {
            estimator = new SemEstimator(this.dataSet, pm);
        }

        SemIm est = estimator.estimate();

        return est.getPValue();
    }

    private List<List<Node>> removeEmpty(List<List<Node>> clusters) {
        List<List<Node>> _clusters = new ArrayList<>();

        for (List<Node> cluster : clusters) {
            if (!cluster.isEmpty()) {
                _clusters.add(cluster);
            }
        }

        return _clusters;
    }

    private Graph pureMeasurementModel(List<List<Node>> clusters) {
        Graph G = new EdgeListGraph();

        List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            Node node = new GraphNode("L" + i);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
            G.addNode(node);
        }

        for (int i = 0; i < latents.size(); i++) {
            for (int j = i + 1; j < latents.size(); j++) {
                G.addBidirectedEdge(latents.get(i), latents.get(j));
            }
        }

        for (int i = 0; i < clusters.size(); i++) {
            for (Node node : clusters.get(i)) {
                G.addNode(node);
                G.addDirectedEdge(latents.get(i), node);
            }
        }

        return G;
    }
}




