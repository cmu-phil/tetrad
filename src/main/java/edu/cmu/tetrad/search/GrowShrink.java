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

import edu.cmu.tetrad.graph.Node;

import java.util.LinkedList;
import java.util.List;

/**
 * Implements the Grow-Shrink algorithm of Margaritis and Thrun. Reference: "Bayesian Network Induction via Local
 * Neighborhoods."
 *
 * @author Joseph Ramsey
 */
public class GrowShrink implements MbSearch {

    /**
     * The independence test used to perform the search.
     */
    private IndependenceTest independenceTest;

    /**
     * The list of variables being searched over. Must contain the target.
     */
    private List<Node> variables;

    /**
     * Constructs a new search.
     *
     * @param test The source of conditional independence information for the search.
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
     * @param targetName the name of the target
     * @return the list of node in the Markov blanket.
     */
    public List<Node> findMb(String targetName) {
        Node target = getVariableForName(targetName);
        List<Node> blanket = new LinkedList<Node>();

        boolean changed = true;

        while (changed) {
            changed = false;

            List<Node> remaining = new LinkedList<Node>(variables);
            remaining.removeAll(blanket);
            remaining.remove(target);

            for (Node node : remaining) {
                if (!independenceTest.isIndependent(node, target, blanket)) {
                    blanket.add(node);
                    changed = true;
                }
            }
        }

        changed = true;

        while (changed) {
            changed = false;

            for (Node node : new LinkedList<Node>(blanket)) {
                blanket.remove(node);

                if (independenceTest.isIndependent(node, target, blanket)) {
                    changed = true;
                    continue;
                }

                blanket.add(node);
            }
        }

        return blanket;
    }

    public String getAlgorithmName() {
        return "Grow Shrink";
    }

    public int getNumIndependenceTests() {
        return 0;
    }

    private Node getVariableForName(String targetName) {
        Node target = null;

        for (Node V : variables) {
            if (V.getName().equals(targetName)) {
                target = V;
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException(
                    "Target variable not in dataset: " + targetName);
        }

        return target;
    }

    public void setVariables(List<Node> variables) {
        this.variables = variables;
    }
}



