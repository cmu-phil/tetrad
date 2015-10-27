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

package edu.pitt.isp.sverchkov.data;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Author : Jeremy Espino MD
 * Created  6/24/15 3:32 PM
 */
public class ADTreeTest {

    public static void main(String[] args) throws Exception {
        int columns = 40;
        int numEdges = 40;
        int rows = 500;

        List<Node> variables = new ArrayList<Node>();
        List<String> varNames = new ArrayList<String>();

        for (int i = 0; i < columns; i++) {
            final String name = "X" + (i + 1);
            varNames.add(name);
            variables.add(new ContinuousVariable(name));
        }

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(variables, 0, numEdges);

        BayesPm pm = new BayesPm(graph);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        DataSet data = im.simulateData(rows, false);

        // This implementation uses a DataTable to represent the data
        // The first type parameter is the type for the variables
        // The second type parameter is the type for the values of the variables
        DataTableImpl<Node, Short> dataTable = new DataTableImpl<Node, Short>(variables);

        for (int i = 0; i < rows; i++) {
            ArrayList<Short> intArray = new ArrayList<Short>();
            for (int j = 0; j < columns; j++) {
                intArray.add((short) data.getInt(i, j));
            }
            dataTable.addRow(intArray);
        }

        // create the tree
        long start = System.currentTimeMillis();
        ADTree<Node, Short> adTree = new ADTree<Node, Short>(dataTable);
        System.out.println(String.format("Generated tree in %s millis", System.currentTimeMillis() - start));

        // the query is an arbitrary map of vars and their values
        TreeMap<Node, Short> query = new TreeMap<Node, Short>();
        query.put(node(pm, "X1"), (short) 1);
        query.put(node(pm, "X5"), (short) 0);
        start = System.currentTimeMillis();
        System.out.println(String.format("Count is %d", adTree.count(query)));
        System.out.println(String.format("Query in %s ms", System.currentTimeMillis() - start));

        query.clear();
        query.put(node(pm, "X1"), (short) 1);
        query.put(node(pm, "X2"), (short) 1);
        query.put(node(pm, "X5"), (short) 0);
        query.put(node(pm, "X10"), (short) 1);
        start = System.currentTimeMillis();
        System.out.println(String.format("Count is %d", adTree.count(query)));
        System.out.println(String.format("Query in %s ms", System.currentTimeMillis() - start));



    }

    private static Node node(BayesPm pm, String x1) {
        return pm.getNode(x1);
    }
}

