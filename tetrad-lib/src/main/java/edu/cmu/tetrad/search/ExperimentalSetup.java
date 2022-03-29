///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.List;

/**
 * Stores a graph with policy variables (randomizations and locks) as parents of measured and latent
 * variables in the graph. The original
 *
 * @author Joseph Ramsey
 */
public class ExperimentalSetup {
    private enum PmType {bayes, sem, generalized}

    private enum EsType {simpleSurgical, simpleSoft, fullExperimental, counterfactualExperimental}

    private final PmType pmType;
    private EsType esType = EsType.simpleSurgical;
    private BayesPm bayesPm;
    private SemPm semPm;
    private GeneralizedSemPm generalizedSemPm;

    private final Graph gNat;
    private final Graph gManip;

    public ExperimentalSetup(BayesPm pm) {
        bayesPm = pm;
        pmType = PmType.bayes;

        Graph dag = pm.getDag();
        gNat = new EdgeListGraph(dag);
        gManip = new EdgeListGraph(gNat);
    }

    public ExperimentalSetup(SemPm pm) {
        semPm = pm;
        pmType = PmType.sem;

        SemGraph graph = pm.getGraph();
        graph.setShowErrorTerms(false);
        gNat = new EdgeListGraph(graph);
        gManip = new EdgeListGraph(gNat);
    }

    public ExperimentalSetup(GeneralizedSemPm pm) {
        generalizedSemPm = pm;
        pmType = PmType.generalized;

        SemGraph graph = pm.getGraph();
        graph.setShowErrorTerms(false);
        gNat = new EdgeListGraph(graph);
        gManip = new EdgeListGraph(gNat);
    }

    private void updateManipulated() {
        if (esType == EsType.simpleSurgical) {
            this.addSimpleSurgicalEdges(gNat, gManip);
        } else if (esType == EsType.simpleSoft) {
            this.addSimpleSoftEdges(gNat, gManip);
        } else if (esType == EsType.fullExperimental) {
            this.addFullExperimentalEdges(gNat, gManip);
        } else if (esType == EsType.counterfactualExperimental) {
            this.addCounterfactualExperimentalEdges(gNat, gManip);
        }
    }

    private void addSimpleSurgicalEdges(Graph gNat, Graph gManip) {
        this.removeCausalEdges(gManip);

        EDGE:
        for (Edge edge : gNat.getEdges()) {
            Node to = Edges.getDirectedEdgeHead(edge);
            List<Node> parents = gNat.getParents(to);

            for (Node node : parents) {
                if (node.getNodeType() == NodeType.LOCK) {
                    continue EDGE;
                }

                if (node.getNodeType() == NodeType.RANDOMIZE) {
                    continue EDGE;
                }

                gManip.addEdge(edge);
            }
        }
    }

    private void addSimpleSoftEdges(Graph gNat, Graph gManip) {
        this.removeCausalEdges(gManip);

        //To change body of created methods use File | Settings | File Templates.
    }

    private void addFullExperimentalEdges(Graph gNat, Graph gManip) {
        this.removeCausalEdges(gManip);

        //To change body of created methods use File | Settings | File Templates.
    }

    private void addCounterfactualExperimentalEdges(Graph gNat, Graph gManip) {
        this.removeCausalEdges(gManip);

        //To change body of created methods use File | Settings | File Templates.
    }

    private void removeCausalEdges(Graph manipulatedGraph) {
        for (Edge edge : manipulatedGraph.getEdges()) {
            if (edge.getNode1().getNodeType() == NodeType.LOCK) continue;
            if (edge.getNode1().getNodeType() == NodeType.RANDOMIZE) continue;
            manipulatedGraph.removeEdge(edge);
        }
    }


    public EsType getEsType() {
        return esType;
    }

    public void setEsType(EsType esType) {
        this.esType = esType;
    }

}



