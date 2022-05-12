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

package edu.cmu.tetrad.search.mb;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceResult;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.MbSearch;

import java.util.LinkedList;
import java.util.List;

/**
 * Implements the Inter-IAMB algorithm.
 */
public class InterIamb implements MbSearch {

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
     * @param test The source of conditional independence information for the search.
     */
    public InterIamb(IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.independenceTest = test;
        this.variables = test.getVariables();
    }

    public List<Node> findMb(String targetName) {
        Node target = getVariableForName(targetName);
        List<Node> cmb = new LinkedList<>();
        boolean cont = true;

        // Forward phase.
        while (cont) {
            cont = false;

            List<Node> remaining = new LinkedList<>(this.variables);
            remaining.removeAll(cmb);
            remaining.remove(target);

            double strength = Double.NEGATIVE_INFINITY;
            Node f = null;

            for (Node v : remaining) {
                if (v == target) {
                    continue;
                }

                double _strength = associationStrength(v, target, cmb);

                if (_strength > strength) {
                    strength = _strength;
                    f = v;
                }
            }

            if (f == null) {
                break;
            }

            if (!this.independenceTest.checkIndependence(f, target, cmb).independent()) {
                cmb.add(f);
                cont = true;
            }

            // Backward phase.
            for (Node _f : new LinkedList<>(cmb)) {
                cmb.remove(_f);

                if (this.independenceTest.checkIndependence(_f, target, cmb).independent()) {
                    continue;
                }

                cmb.add(_f);
            }

        }

        return cmb;
    }

    private double associationStrength(Node v, Node target, List<Node> cmb) {
        IndependenceResult result = this.independenceTest.checkIndependence(v, target, cmb);
        return 1.0 - result.getPValue();
    }

    public String getAlgorithmName() {
        return "InterIAMB";
    }

    public int getNumIndependenceTests() {
        return 0;
    }

    private Node getVariableForName(String targetName) {
        Node target = null;

        for (Node V : this.variables) {
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
}



