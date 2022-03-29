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
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implementation of HITON-MB following Matlab code by authors. Note that the allowUnfaithfulness HITON algorithm includes a
 * cross-classification wrapper, which is not implemented here.
 *
 * @author Joseph Ramsey
 */
public class HitonMb implements MbSearch {

    /**
     * True if the symmetric algorithm is to be used.
     */
    private boolean symmetric = false;

    /**
     * The independence test used to perform the search.
     */
    private final IndependenceTest independenceTest;

    /**
     * The list of variables being searched over. Must contain the target.
     */
    private final List<Node> variables;

    /**
     * Variables sorted by decreasing association with the target.
     */
    private List<Node> sortedVariables;

    /**
     * The maximum number of conditioning variables.
     */
    private final int depth;

    /**
     * Number of independence tests.
     */
    private int numIndTests = 0;

    /**
     * The function from nodes to their sets of parents and children.
     */
    Map<Node, List<Node>> pc;

    /**
     * Set of trimmed nodes (for the symmetric implementation).
     */
    private Set<Node> trimmed;

    /**
     * Constructs a new search.
     *
     * @param test      The source of conditional independence information for the search.
     * @param depth     The maximum number of conditioning variables.
     * @param symmetric True if the symmetric algorithm is to be used.
     */
    public HitonMb(final IndependenceTest test, final int depth, final boolean symmetric) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.independenceTest = test;
        this.variables = test.getVariables();
        this.depth = depth;
        this.symmetric = symmetric;
    }

    public List<Node> findMb(final String targetName) {
        TetradLogger.getInstance().log("info", "target = " + targetName);
        this.numIndTests = 0;
        final long time = System.currentTimeMillis();

        this.pc = new HashMap<>();
        this.trimmed = new HashSet<>();

        final Node t = getVariableForName(targetName);

        // Sort variables by decreasing association with the target.
        this.sortedVariables = new LinkedList<>(this.variables);

        Collections.sort(this.sortedVariables, new Comparator<Node>() {
            public int compare(final Node o1, final Node o2) {
                final double score1 = o1 == t ? 1.0 : association(o1, t);
                final double score2 = o2 == t ? 1.0 : association(o2, t);

                if (score1 < score2) {
                    return 1;
                } else if (score1 > score2) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        final List<Node> nodes = hitonMb(t);

        final long time2 = System.currentTimeMillis() - time;
        TetradLogger.getInstance().log("info", "Number of seconds: " + (time2 / 1000.0));
        TetradLogger.getInstance().log("info", "Number of independence tests performed: " +
                this.numIndTests);

//        System.out.println("Number of calls to hiton_pc = " + pc.size());

        return nodes;
    }

    private List<Node> hitonMb(final Node t) {
        // MB <- {}
        final Set<Node> mb = new HashSet<>();

        final Set<Node> _pcpc = new HashSet<>();

        for (final Node node : getPc(t)) {
            final List<Node> f = getPc(node);
            this.pc.put(node, f);
            _pcpc.addAll(f);
        }

        final List<Node> pcpc = new LinkedList<>(_pcpc);

        final Set<Node> currentMb = new HashSet<>(getPc(t));
        currentMb.addAll(pcpc);
        currentMb.remove(t);

        final HashSet<Node> diff = new HashSet<>(currentMb);
        diff.removeAll(getPc(t));
        diff.remove(t);

        //for each x in PCPC \ PC
        for (final Node x : diff) {
            List<Node> s = null;

            // Find an S such PC such that x _||_ t | S
            final DepthChoiceGenerator generator =
                    new DepthChoiceGenerator(pcpc.size(), this.depth);
            int[] choice;

            while ((choice = generator.next()) != null) {
                final List<Node> _s = new LinkedList<>();

                for (final int index : choice) {
                    _s.add(pcpc.get(index));
                }

                this.numIndTests++;
                if (this.independenceTest.isIndependent(t, x, _s)) {
                    s = _s;
                    break;
                }
            }

            if (s == null) {
                System.out.println("S not found.");
//                mb.add(x);
                continue;
            }

            // y_set <- {y in PC(t) : x in PC(y)}
            final Set<Node> ySet = new HashSet<>();
            for (final Node y : getPc(t)) {
                if (this.pc.get(y).contains(x)) {
                    ySet.add(y);
                }
            }

            //  For each y in y_set
            for (final Node y : ySet) {
                if (x == y) continue;

                final List<Node> _s = new LinkedList<>(s);
                _s.add(y);

                // If x NOT _||_ t | S U {y}
                this.numIndTests++;
                if (!this.independenceTest.isIndependent(t, x, _s)) {
                    mb.add(x);
                    break;
                }
            }
        }

        mb.addAll(getPc(t));
        return new LinkedList<>(mb);
    }

    private List<Node> hitonPc(final Node t) {
        final LinkedList<Node> variables = new LinkedList<>(this.sortedVariables);

        variables.remove(t);

        final List<Node> cpc = new ArrayList<>();

        while (!variables.isEmpty()) {
            final Node vi = variables.removeFirst();
            cpc.add(vi);

            VARS:
            for (final Node x : new LinkedList<>(cpc)) {
                cpc.remove(x);

                for (int d = 0; d <= Math.min(cpc.size(), this.depth); d++) {
                    final ChoiceGenerator generator =
                            new ChoiceGenerator(cpc.size(), d);
                    int[] choice;

                    while ((choice = generator.next()) != null) {
                        final List<Node> s = new LinkedList<>();

                        for (final int index : choice) {
                            s.add(cpc.get(index));
                        }

                        // Only do new ones.
                        if (!(x == vi || s.contains(vi))) {
                            continue;
                        }

                        // If it's independent of the target given this
                        // subset...
                        this.numIndTests++;
                        if (this.independenceTest.isIndependent(x, t, s)) {

                            // Leave it removed.
                            continue VARS;
                        }
                    }
                }

                // Stick it back.
                cpc.add(x);
            }

        }

        return cpc;
    }

    /**
     * @return a supserset of PC, or, if the symmetric algorithm is used, PC.
     */
    private List<Node> getPc(final Node t) {
        if (!this.pc.containsKey(t)) {
            this.pc.put(t, hitonPc(t));
        }

        if (this.symmetric && !this.trimmed.contains(t)) {
            trimPc(t);
            this.trimmed.add(t);
        }

        return this.pc.get(t);
    }

    /**
     * Trims away false positives from the given node. Used in the symmetric algorithm.
     */
    private void trimPc(final Node t) {
        for (final Node x : new LinkedList<>(this.pc.get(t))) {
            if (!this.pc.containsKey(x)) {
                this.pc.put(x, hitonPc(x));
            }

            if (!this.pc.get(x).contains(t)) {
                this.pc.get(t).remove(x);
            }
        }
    }

    /**
     * A measure of strength of association.
     */
    private double association(final Node x, final Node y) {
        this.numIndTests++;
        this.independenceTest.isIndependent(x, y, new LinkedList<Node>());
        return 1.0 - this.independenceTest.getPValue();
    }

    public String getAlgorithmName() {
        return this.symmetric ? "HITON-MB-SYM" : "HITON-MB";
    }

    public int getNumIndependenceTests() {
        return this.numIndTests;
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



