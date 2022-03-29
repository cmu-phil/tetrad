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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the BayesUpdqater
 *
 * @author William Taysom
 */
public final class TestUpdatedBayesIm {

    @Test
    public void testCompound() {
        final Node x0Node = new GraphNode("X0");
        final Node x1Node = new GraphNode("X1");
        final Node x2Node = new GraphNode("X2");
        final Node x3Node = new GraphNode("X3");
        final Node x4Node = new GraphNode("X4");

        final Dag graph = new Dag();
        graph.addNode(x0Node);
        graph.addNode(x1Node);
        graph.addNode(x2Node);
        graph.addNode(x3Node);
        graph.addNode(x4Node);

        graph.addDirectedEdge(x0Node, x1Node);
        graph.addDirectedEdge(x0Node, x2Node);
        graph.addDirectedEdge(x1Node, x3Node);
        graph.addDirectedEdge(x2Node, x3Node);
        graph.addDirectedEdge(x4Node, x0Node);
        graph.addDirectedEdge(x4Node, x2Node);

        final BayesPm bayesPm = new BayesPm(graph);
        final MlBayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        final UpdatedBayesIm updatedIm1 = new UpdatedBayesIm(bayesIm);
        assertEquals(bayesIm, updatedIm1);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                final Evidence evidence1 = updatedIm1.getEvidence();
                evidence1.getProposition().disallowComplement(i, 0);
                final UpdatedBayesIm updatedIm2 =
                        new UpdatedBayesIm(updatedIm1, evidence1);

                final Evidence evidence2 = updatedIm2.getEvidence();
                evidence2.getProposition().setToTautology();
                evidence2.getProposition().disallowComplement(j, 0);

                final CptInvariantMarginalCalculator marginals1 =
                        new CptInvariantMarginalCalculator(updatedIm2,
                                evidence2);
                final double marginal1 = marginals1.getMarginal(0, 0);

                final Evidence evidence3 = updatedIm1.getEvidence();
                evidence3.getProposition().disallowComplement(i, 0);
                evidence3.getProposition().disallowComplement(j, 0);

                final CptInvariantMarginalCalculator marginals2 =
                        new CptInvariantMarginalCalculator(updatedIm1,
                                evidence3);

                final double marginal2 = marginals2.getMarginal(0, 0);

                assertEquals(marginal1, marginal2, 1.0e-2);
            }
        }
    }
}





