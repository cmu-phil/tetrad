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
 * Reimplemented HITON for purposes of comparison to other algorithms, to get it closer to the published definition.
 *
 * @author Joseph Ramsey
 */
public class HitonVariant implements MbSearch {

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
     * Constructs a new search.
     *
     * @param test The source of conditional independence information for the search.
     */
    public HitonVariant(IndependenceTest test, int depth) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.independenceTest = test;
        this.variables = test.getVariables();
        this.depth = depth;
    }

    public List<Node> findMb(String targetName) {
        TetradLogger.getInstance().log("info", "target = " + targetName);
        //        numIndTests = 0;
        long time = System.currentTimeMillis();

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
        //        LogUtils.getInstance().info("Number of independence tests performed: " +
//            numIndTests);

        return nodes;
    }


    private List<Node> hitonMb(Node t) {
        // MB <- {}
        Set<Node> mb = new HashSet<Node>();
        Map<Node, List<Node>> pcSets = new HashMap<Node, List<Node>>();

        List<Node> pc = hitonPc(t);
        pcSets.put(t, pc);
        Set<Node> _pcpc = new HashSet<Node>();

        for (Node node : pc) {
            List<Node> f = hitonPc(node);
            pcSets.put(node, f);
            _pcpc.addAll(f);
        }

        List<Node> pcpc = new LinkedList<Node>(_pcpc);

        Set<Node> currentMb = new HashSet<Node>(pc);
        currentMb.addAll(pcpc);
        currentMb.remove(t);

        HashSet<Node> diff = new HashSet<Node>(currentMb);
        diff.removeAll(pc);
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
            for (Node y : pc) {
                if (pcSets.get(y).contains(x)) {
                    ySet.add(y);
                }
            }

            //  For each y in y_set
            for (Node y : ySet) {
                if (x == y) continue;

                List<Node> _s = new LinkedList<Node>(s);
                _s.add(y);

                // If x NOT _||_ t | S U {y}
                if (!independenceTest.isIndependent(t, x, _s)) {
                    mb.add(x);
                    break;
                }
            }
        }

        mb.addAll(pc);
        return new LinkedList<Node>(mb);
    }

//    public List<Node> findMb(String targetName) {
//        final Node t = getVariableForName(targetName);
//
//        // Sort variables by decreasing association with the target.
//        sortedVariables = new LinkedList<Node>(variables);
//
//        Collections.sort(sortedVariables, new Comparator<Node>() {
//            public int compare(Node o1, Node o2) {
//                double score1 = o1 == t ? 1.0 : association(o1, t);
//                double score2 = o2 == t ? 1.0 : association(o2, t);
//
//                if (score1 < score2) {
//                    return 1;
//                }
//                else if (score1 > score2) {
//                    return -1;
//                }
//                else {
//                    return 0;
//                }
//            }
//        });
//
//        Set<Node> pc = new HashSet<Node>(hitonPc(t));
//        Set<Node> pcpc = new HashSet<Node>();
//
//        for (Node node : pc) {
//            pcpc.addAll(hitonPc(node));
//        }
//
//        Set<Node> currentMb = new HashSet<Node>(pc);
//        currentMb.addAll(pcpc);
//        currentMb.remove(t);
//
//        // For each x...
//
//        X:
//        for (Node x : new HashSet<Node>(currentMb)) {
//
//            // Look for a y such that conditioning on any set containing y
//            // renders x dependent on the target...
//
//            Y:
//            for (Node y : pc) {
//                if (x == y) continue;
//
//                List<Node> b = new LinkedList<Node>(currentMb);
//                b.remove(x);
//                b.remove(y);
//
//                // We're going to add y to it, so go on lower.
//                for (int d = 0; d <= Math.min(b.size(), depth) - 1; d++) {
//                    ChoiceGenerator generator =
//                            new ChoiceGenerator(b.size(), d);
//                    int[] choice;
//
//                    while ((choice = generator.next()) != null) {
//                        List<Node> s = new LinkedList<Node>();
//
//                        for (int index : choice) {
//                            s.add(b.get(index));
//                        }
//
//                        s.add(y);
//
//                        if (independenceTest.isIndependent(t, x, s)) {
//                            continue Y;
//                        }
//                    }
//                }
//
//                // If there is such a y, leave x in.
//                continue X;
//            }
//
//            // Otherwise, take x out.
//            currentMb.remove(x);
//        }
//
//        return new ArrayList<Node>(currentMb);
//    }

    private List<Node> hitonPc(Node t) {
        LinkedList<Node> variables = new LinkedList<Node>(sortedVariables);

        variables.remove(t);

        List<Node> currentPc = new ArrayList<Node>();

        while (!variables.isEmpty()) {
            Node vi = variables.removeFirst();
            currentPc.add(vi);

            VARS:
            for (Node x : new LinkedList<Node>(currentPc)) {
                currentPc.remove(x);

                for (int d = 0; d <= Math.min(currentPc.size(), depth); d++) {
                    ChoiceGenerator generator =
                            new ChoiceGenerator(currentPc.size(), d);
                    int[] choice;

                    while ((choice = generator.next()) != null) {
                        List<Node> s = new LinkedList<Node>();

                        for (int index : choice) {
                            s.add(currentPc.get(index));
                        }

                        // Only do new ones.
                        if (!(x == vi || s.contains(vi))) {
                            continue;
                        }

                        // If it's independent of the target given this
                        // subset...
                        if (independenceTest.isIndependent(x, t, s)) {

                            // Leave it removed.
                            continue VARS;
                        }
                    }
                }

                // Stick it back.
                currentPc.add(x);
            }
        }

        return currentPc;
    }

    /**
     * A measure of strength of association.
     */
    private double association(Node x, Node y) {
        independenceTest.isIndependent(x, y, new LinkedList<Node>());
        return 1.0 - independenceTest.getPValue();
    }

    public String getAlgorithmName() {
        return "HITON-VARIANT";
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
}



