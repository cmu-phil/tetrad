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
 * Implementation of HITON-MB following Matlab code by authors. Note that the full HITON algorithm includes a
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
    private IndependenceTest independenceTest;

    /**
     * The list of variables being searched over. Must contain the target.
     */
    private List<Node> variables;

    /**
     * Variables sorted by decreasing association with the target.
     */
    private List<Node> sortedVariables;

    /**
     * The maximum number of conditioning variables.
     */
    private int depth;

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
    public HitonMb(IndependenceTest test, int depth, boolean symmetric) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.independenceTest = test;
        this.variables = test.getVariables();
        this.depth = depth;
        this.symmetric = symmetric;
    }

    public List<Node> findMb(String targetName) {
        TetradLogger.getInstance().log("info", "target = " + targetName);
        numIndTests = 0;
        long time = System.currentTimeMillis();

        pc = new HashMap<Node, List<Node>>();
        trimmed = new HashSet<Node>();

        final Node t = getVariableForName(targetName);

        // Sort variables by decreasing association with the target.
        sortedVariables = new LinkedList<Node>(variables);

        Collections.sort(sortedVariables, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                double score1 = o1 == t ? 1.0 : association(o1, t);
                double score2 = o2 == t ? 1.0 : association(o2, t);

                if (score1 < score2) {
                    return 1;
                } else if (score1 > score2) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        List<Node> nodes = hitonMb(t);

        long time2 = System.currentTimeMillis() - time;
        TetradLogger.getInstance().log("info", "Number of seconds: " + (time2 / 1000.0));
        TetradLogger.getInstance().log("info", "Number of independence tests performed: " +
                numIndTests);

//        System.out.println("Number of calls to hiton_pc = " + pc.size());

        return nodes;
    }

    private List<Node> hitonMb(Node t) {
        // MB <- {}
        Set<Node> mb = new HashSet<Node>();

        Set<Node> _pcpc = new HashSet<Node>();

        for (Node node : getPc(t)) {
            List<Node> f = getPc(node);
            this.pc.put(node, f);
            _pcpc.addAll(f);
        }

        List<Node> pcpc = new LinkedList<Node>(_pcpc);

        Set<Node> currentMb = new HashSet<Node>(getPc(t));
        currentMb.addAll(pcpc);
        currentMb.remove(t);

        HashSet<Node> diff = new HashSet<Node>(currentMb);
        diff.removeAll(getPc(t));
        diff.remove(t);

        //for each x in PCPC \ PC
        for (Node x : diff) {
            List<Node> s = null;

            // Find an S such PC such that x _||_ t | S
            DepthChoiceGenerator generator =
                    new DepthChoiceGenerator(pcpc.size(), depth);
            int[] choice;

            while ((choice = generator.next()) != null) {
                List<Node> _s = new LinkedList<Node>();

                for (int index : choice) {
                    _s.add(pcpc.get(index));
                }

                numIndTests++;
                if (independenceTest.isIndependent(t, x, _s)) {
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
            Set<Node> ySet = new HashSet<Node>();
            for (Node y : getPc(t)) {
                if (this.pc.get(y).contains(x)) {
                    ySet.add(y);
                }
            }

            //  For each y in y_set
            for (Node y : ySet) {
                if (x == y) continue;

                List<Node> _s = new LinkedList<Node>(s);
                _s.add(y);

                // If x NOT _||_ t | S U {y}
                numIndTests++;
                if (!independenceTest.isIndependent(t, x, _s)) {
                    mb.add(x);
                    break;
                }
            }
        }

        mb.addAll(getPc(t));
        return new LinkedList<Node>(mb);
    }

    private List<Node> hitonPc(Node t) {
        LinkedList<Node> variables = new LinkedList<Node>(sortedVariables);

        variables.remove(t);

        List<Node> cpc = new ArrayList<Node>();

        while (!variables.isEmpty()) {
            Node vi = variables.removeFirst();
            cpc.add(vi);

            VARS:
            for (Node x : new LinkedList<Node>(cpc)) {
                cpc.remove(x);

                for (int d = 0; d <= Math.min(cpc.size(), depth); d++) {
                    ChoiceGenerator generator =
                            new ChoiceGenerator(cpc.size(), d);
                    int[] choice;

                    while ((choice = generator.next()) != null) {
                        List<Node> s = new LinkedList<Node>();

                        for (int index : choice) {
                            s.add(cpc.get(index));
                        }

                        // Only do new ones.
                        if (!(x == vi || s.contains(vi))) {
                            continue;
                        }

                        // If it's independent of the target given this
                        // subset...
                        numIndTests++;
                        if (independenceTest.isIndependent(x, t, s)) {

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
    private List<Node> getPc(Node t) {
        if (!pc.containsKey(t)) {
            pc.put(t, hitonPc(t));
        }

        if (symmetric && !trimmed.contains(t)) {
            trimPc(t);
            trimmed.add(t);
        }

        return pc.get(t);
    }

    /**
     * Trims away false positives from the given node. Used in the symmetric algorithm.
     */
    private void trimPc(Node t) {
        for (Node x : new LinkedList<Node>(pc.get(t))) {
            if (!pc.containsKey(x)) {
                pc.put(x, hitonPc(x));
            }

            if (!pc.get(x).contains(t)) {
                pc.get(t).remove(x);
            }
        }
    }

    /**
     * A measure of strength of association.
     */
    private double association(Node x, Node y) {
        numIndTests++;
        independenceTest.isIndependent(x, y, new LinkedList<Node>());
        return 1.0 - independenceTest.getPValue();
    }

    public String getAlgorithmName() {
        return symmetric ? "HITON-MB-SYM" : "HITON-MB";
    }

    public int getNumIndependenceTests() {
        return numIndTests;
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
}



