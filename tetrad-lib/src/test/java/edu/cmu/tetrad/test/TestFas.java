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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Joseph Ramsey
 */
public class TestFas {

    @Test
    public void test1() {
        int numVars = 10;
        double edgesPerNode = 1.0;

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode), 30, 15, 15, false);

        IndependenceTest test = new IndTestDSep(graph);

        Graph fasGraph = new FasStableConcurrent(test).search();
        Graph pcGraph = new Pc(test).search();

        assertEquals(fasGraph, GraphUtils.undirectedGraph(pcGraph));
    }
}





