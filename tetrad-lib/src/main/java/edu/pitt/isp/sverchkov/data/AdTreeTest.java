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

package edu.pitt.isp.sverchkov.data;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.MillisecondTimes;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * A test of the AD tree implementation.
 *
 * @author Jeremy Espino MD Created  6/24/15 3:32 PM
 */
public class AdTreeTest {

    /**
     * Creates a new AdTreeTest object.
     */
    public AdTreeTest() {
    }

    /**
     * Test the AD tree
     *
     * @param args ignored
     * @throws Exception if something goes wrong
     */
    public static void main(String[] args) throws Exception {
        final int columns = 40;
        final int numEdges = 40;
        final int rows = 500;

        List<Node> variables = new ArrayList<>();
        List<String> varNames = new ArrayList<>();

        for (int i = 0; i < columns; i++) {
            String name = "X" + (i + 1);
            varNames.add(name);
            variables.add(new ContinuousVariable(name));
        }

        Graph graph = RandomGraph.randomGraphRandomForwardEdges(variables, 0, numEdges, 30, 15, 15, false, true);

        BayesPm pm = new BayesPm(graph);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        DataSet data = im.simulateData(rows, false);

        // This implementation uses a DataTable to represent the data
        // The first type parameter is the type for the variables
        // The second type parameter is the type for the values of the variables
        DataTableImpl<Node, Short> dataTable = new DataTableImpl<>(variables);

        for (int i = 0; i < rows; i++) {
            ArrayList<Short> intArray = new ArrayList<>();
            for (int j = 0; j < columns; j++) {
                intArray.add((short) data.getInt(i, j));
            }
            dataTable.addRow(intArray);
        }

        // create the tree
        long start = MillisecondTimes.timeMillis();
        AdTree<Node, Short> adTree = new AdTree<>(dataTable);
        System.out.printf("Generated tree in %s millis%n", MillisecondTimes.timeMillis() - start);

        // the query is an arbitrary map of vars and their values
        TreeMap<Node, Short> query = new TreeMap<>();
        query.put(AdTreeTest.node(pm, "X1"), (short) 1);
        query.put(AdTreeTest.node(pm, "X5"), (short) 0);
        start = MillisecondTimes.timeMillis();
        System.out.printf("Count is %d%n", adTree.count(query));
        System.out.printf("Query in %s ms%n", MillisecondTimes.timeMillis() - start);

        query.clear();
        query.put(AdTreeTest.node(pm, "X1"), (short) 1);
        query.put(AdTreeTest.node(pm, "X2"), (short) 1);
        query.put(AdTreeTest.node(pm, "X5"), (short) 0);
        query.put(AdTreeTest.node(pm, "X10"), (short) 1);
        start = MillisecondTimes.timeMillis();
        System.out.printf("Count is %d%n", adTree.count(query));
        System.out.printf("Query in %s ms%n", MillisecondTimes.timeMillis() - start);


    }

    private static Node node(BayesPm pm, String x1) {
        return pm.getNode(x1);
    }
}

