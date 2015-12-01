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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Iterator;

/**
 * <p>Provides a static method for finding the Euclidean distance between a pair
 * of BayesIm's.  That is, it computes the square root of the sum of the squares
 * of the differences between corresponding parameters of the two Bayes
 * nets.</p> <p>The BayesPm's should be equal in the sense of the "equals"
 * method of that class.</p>
 *
 * @author Frank Wimberly
 */
final class BayesImDistanceFunction {

    /**
     * The static distance method's arguments are the two BayesIM's whose
     * BayesPm's are "equal".
     */
    public static double distance(BayesIm firstBn, BayesIm secondBn) {
        if (!firstBn.getBayesPm().equals(secondBn.getBayesPm())) {
            throw new IllegalArgumentException("BayesPms must be equal.");
        }

        Graph graph = firstBn.getBayesPm().getDag();
        Node[] nodes = new Node[graph.getNumNodes()];
        Iterator<Node> it = graph.getNodes().iterator();

        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = it.next();
            //System.out.println("node " + i + " " + nodes[i]);
        }

        double sum = 0.0;

        for (int i = 0; i < nodes.length; i++) {
            //int numRows = bayesImMixed.getNumRows(i);
            int numRows = firstBn.getNumRows(i);

            //for(int j = 0; j < bayesImMixed.getNumRows(i); j++) {
            for (int j = 0; j < numRows; j++) {
                int numCols = firstBn.getNumColumns(i);

                for (int k = 0; k < numCols; k++) {
                    double diff = firstBn.getProbability(i, j, k) -
                            secondBn.getProbability(i, j, k);
//                    if (!Double.isNaN(diff)) {
                    sum += diff * diff;
//                    }
                }
            }
        }

        return Math.sqrt(sum);
    }
}





