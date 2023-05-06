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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.mb.MbSearch;

import java.util.LinkedList;
import java.util.List;

/**
 * <p>Implements the Grow-Shrink algorithm of Margaritis and Thrun, a simple yet correct
 * and useful Markov blanket search.</p>
 * <p>Margaritis, D., & Thrun, S. (1999). Bayesian network induction via local neighborhoods.
 * Advances in neural information processing systems, 12.</p>
 *
 * @author Joseph Ramsey
 */
public class GrowShrink implements MbSearch {

    /**
     * The independence test used to perform the search.
     */
    private final IndependenceTest independenceTest;

    /**
     * The list of variables being searched over. Must contain the target.
     */
    private final List<Node> variables;

    /**
     * Constructs a new search.
     *
     * @param test The test used for this search.
     */
    public GrowShrink(IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.independenceTest = test;
        this.variables = test.getVariables();
    }

    /**
     * Finds the Markov blanket of the given target.
     *
     * @param target the target
     * @return the list of node in the Markov blanket.
     */
    public List<Node> findMb(Node target) {
        List<Node> blanket = new LinkedList<>();

        boolean changed = true;

        while (changed) {
            changed = false;

            List<Node> remaining = new LinkedList<>(this.variables);
            remaining.removeAll(blanket);
            remaining.remove(target);

            for (Node node : remaining) {
                if (!this.independenceTest.checkIndependence(node, target, blanket).isIndependent()) {
                    blanket.add(node);
                    changed = true;
                }
            }
        }

        changed = true;

        while (changed) {
            changed = false;

            for (Node node : new LinkedList<>(blanket)) {
                blanket.remove(node);

                if (this.independenceTest.checkIndependence(node, target, blanket).isIndependent()) {
                    changed = true;
                    continue;
                }

                blanket.add(node);
            }
        }

        return blanket;
    }

    /**
     * Returns "Grow Shrink".
     *
     * @return This string.
     */
    public String getAlgorithmName() {
        return "Grow Shrink";
    }

    /**
     * @throws UnsupportedOperationException Since independence tests are not counted.
     */
    public int getNumIndependenceTests() {
        throw new UnsupportedOperationException("Independence tests are not counted in the algorithm.");
    }
}



