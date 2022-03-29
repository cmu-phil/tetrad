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

package edu.cmu.tetrad.search.mb;

import edu.cmu.tetrad.graph.Node;
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
    public InterIamb(final IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.independenceTest = test;
        this.variables = test.getVariables();
    }

    public List<Node> findMb(final String targetName) {
        final Node target = getVariableForName(targetName);
        final List<Node> cmb = new LinkedList<>();
        boolean cont = true;

        // Forward phase.
        while (cont) {
            cont = false;

            final List<Node> remaining = new LinkedList<>(this.variables);
            remaining.removeAll(cmb);
            remaining.remove(target);

            double strength = Double.NEGATIVE_INFINITY;
            Node f = null;

            for (final Node v : remaining) {
                if (v == target) {
                    continue;
                }

                final double _strength = associationStrength(v, target, cmb);

                if (_strength > strength) {
                    strength = _strength;
                    f = v;
                }
            }

            if (f == null) {
                break;
            }

            if (!this.independenceTest.isIndependent(f, target, cmb)) {
                cmb.add(f);
                cont = true;
            }

            // Backward phase.
            for (final Node _f : new LinkedList<>(cmb)) {
                cmb.remove(_f);

                if (this.independenceTest.isIndependent(_f, target, cmb)) {
                    continue;
                }

                cmb.add(_f);
            }

//            boolean changed = true;
//
//            while (changed) {
//                changed = false;
//
//                for (Node node : new LinkedList<Node>(cmb)) {
//                    cmb.remove(node);
//
//                    if (independenceTest.isIndependent(node, target, cmb)) {
//                        changed = true;
//                        continue;
//                    }
//
//                    cmb.add(node);
//                }
//            }
        }

        return cmb;
    }

    private double associationStrength(final Node v, final Node target, final List<Node> cmb) {
        this.independenceTest.isIndependent(v, target, cmb);
        return 1.0 - this.independenceTest.getPValue();
    }

    public String getAlgorithmName() {
        return "InterIAMB";
    }

    public int getNumIndependenceTests() {
        return 0;
    }

    private Node getVariableForName(final String targetName) {
        Node target = null;

        for (final Node V : this.variables) {
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



