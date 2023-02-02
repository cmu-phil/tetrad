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
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Implements the Min-Max Markov Blanks (MMMB) algorithm as defined in Tsamardinos, Aliferis, and Statnikov, Time and
 * Sample Efficient Discovery of Markov Blankets and Direct Causal Relations (KDD 2003).
 *
 * @author Joseph Ramsey
 */
public final class Mmmb implements MbSearch {

    /**
     * True if the symmetric algorithm is to be used.
     */
    private final boolean symmetric;

    /**
     * The independence test used to perform the search.
     */
    private final IndependenceTest independenceTest;

    /**
     * The list of variables being searched over. Must contain the target.
     */
    private final List<Node> variables;

    /**
     * The maximum number of variables conditioned on.
     */
    int depth;

    /**
     * Number of independence tests.
     */
    private int numIndTests;

    /**
     * The function from nodes to their sets of parents and children.
     */
    Map<Node, List<Node>> pc;

    /**
     * Set of trimmed nodes (for the symmetric implementation).
     */
    private Set<Node> trimmed;

    //=============================CONSTRUCTOR=============================//

    /**
     * Constructs.
     *
     * @param test      The independence test used in the search.
     * @param depth     The maximum number of variables conditioned on.
     * @param symmetric True if the symmetric algorithm is to be used.
     */
    public Mmmb(IndependenceTest test, int depth, boolean symmetric) {
        if (test == null) {
            throw new NullPointerException();
        }

        if (depth < -1) {
            throw new IllegalArgumentException();
        }

        this.independenceTest = test;
        this.variables = test.getVariables();
        this.depth = depth;
        this.symmetric = symmetric;

        this.pc = new HashMap<>();
        this.trimmed = new HashSet<>();
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * Searches for the Markov blanket of the node by the given name.
     *
     * @param target The name of the target node.
     * @return The Markov blanket of the target.
     */
    public List<Node> findMb(Node target) {
        TetradLogger.getInstance().log("info", "target = " + target);
        this.numIndTests = 0;
        long time = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

        this.pc = new HashMap<>();
        this.trimmed = new HashSet<>();

        List<Node> nodes = mmmb(target);

        long time2 = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - time;
        TetradLogger.getInstance().log("info", "Number of seconds: " + (time2 / 1000.0));
        TetradLogger.getInstance().log("info", "Number of independence tests performed: " +
                this.numIndTests);
        //        System.out.println("Number of calls to mmpc = " + pc.size());

        return nodes;
    }

    //===========================PRIVATE METHODS==========================//

    private List<Node> mmmb(Node t) {
        // MB <- {}
        Set<Node> mb = new HashSet<>();

        Set<Node> _pcpc = new HashSet<>();

        for (Node node : getPc(t)) {
            List<Node> f = getPc(node);
            this.pc.put(node, f);
            _pcpc.addAll(f);
        }

        List<Node> pcpc = new LinkedList<>(_pcpc);

        Set<Node> currentMb = new HashSet<>(getPc(t));
        currentMb.addAll(pcpc);
        currentMb.remove(t);

        HashSet<Node> diff = new HashSet<>(currentMb);
        getPc(t).forEach(diff::remove);
        diff.remove(t);

        //for each x in PCPC \ PC
        for (Node x : diff) {
            List<Node> s = null;

            // Find an S such PC such that x _||_ t | S
            SublistGenerator generator =
                    new SublistGenerator(pcpc.size(), this.depth);
            int[] choice;

            while ((choice = generator.next()) != null) {
                List<Node> _s = new LinkedList<>();

                for (int index : choice) {
                    _s.add(pcpc.get(index));
                }

                this.numIndTests++;
                if (this.independenceTest.checkIndependence(t, x, _s).independent()) {
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
            Set<Node> ySet = new HashSet<>();
            for (Node y : getPc(t)) {
                if (this.pc.get(y).contains(x)) {
                    ySet.add(y);
                }
            }

            //  For each y in y_set
            for (Node y : ySet) {
                if (x == y) continue;

                List<Node> _s = new LinkedList<>(s);
                _s.add(y);

                // If x NOT _||_ t | S U {y}
                this.numIndTests++;
                if (!this.independenceTest.checkIndependence(t, x, _s).independent()) {
                    mb.add(x);
                    break;
                }
            }
        }

        mb.addAll(getPc(t));
        return new LinkedList<>(mb);
    }

    private List<Node> mmpc(Node t) {
        List<Node> pc = new LinkedList<>();
        boolean pcIncreased = true;

        // First optimization: Don't consider adding again variables that have
        // already been found independent of t.
        List<Node> indepOfT = new LinkedList<>();

        // Phase 1
        while (pcIncreased) {
            pcIncreased = false;

            MaxMinAssocResult ret = maxMinAssoc(t, pc, indepOfT);
            Node f = ret.getNode();
            List<Node> assocSet = ret.getAssocSet();

            if (f == null) {
                break;
            }

            this.numIndTests++;

            if (!this.independenceTest.checkIndependence(f, t, assocSet).independent()) {
                pcIncreased = true;
                pc.add(f);
            }
        }

        // Phase 2.
        backwardsConditioning(pc, t);

        TetradLogger.getInstance().log("details", "PC(" + t + ") = " + pc);
        //        System.out.println("PC(" + t + ") = " + pc);

        return pc;
    }

    /**
     * @return a supserset of PC, or, if the symmetric algorithm is used, PC.
     */
    public List<Node> getPc(Node t) {
        if (!this.pc.containsKey(t)) {
            this.pc.put(t, mmpc(t));
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
    private void trimPc(Node t) {
        for (Node x : new LinkedList<>(this.pc.get(t))) {
            if (!this.pc.containsKey(x)) {
                this.pc.put(x, mmpc(x));
            }

            if (!this.pc.get(x).contains(t)) {
                this.pc.get(t).remove(x);
            }
        }
    }

    private MaxMinAssocResult maxMinAssoc(Node t, List<Node> pc,
                                          List<Node> indepOfT) {
        Node f = null;
        List<Node> maxAssocSet = null;
        double maxAssoc = 0.0;

        for (Node v : this.variables) {
            if (t == v) continue;
            if (pc.contains(v)) continue;

            if (indepOfT.contains(v)) {
                continue;
            }

            List<Node> minAssoc = minAssoc(v, t, pc);
            double assoc = association(v, t, minAssoc);

            // If v is conditionally independent of t, don't consider it
            // again. Note if this code is right, then we have to use the
            // association test as an independence test... ugh.
            if (assoc < 1.0 - this.independenceTest.getAlpha()) {
                indepOfT.add(v);
            }

            if (assoc > maxAssoc) {
                maxAssocSet = minAssoc;
                maxAssoc = assoc;
                f = v;
            }
        }

        return new MaxMinAssocResult(f, maxAssocSet);
    }

    private List<Node> minAssoc(Node x, Node target, List<Node> pc) {
        double assoc = 1.0;
        List<Node> set = new LinkedList<>();

        if (pc.contains(x)) throw new IllegalArgumentException();
        if (pc.contains(target)) throw new IllegalArgumentException();
        if (x == target) throw new IllegalArgumentException();

        SublistGenerator generator =
                new SublistGenerator(pc.size(), this.depth);
        int[] choice;

        while ((choice = generator.next()) != null) {
            List<Node> s = new LinkedList<>();

            for (int index : choice) {
                s.add(pc.get(index));
            }

            // Second optimization. In line 4, condider only conditioning
            // sets that contain the last node added to PC.
            if (pc.size() > 0 && !s.contains(pc.get(pc.size() - 1))) {
                continue;
            }

            double _assoc = association(x, target, s);

            if (_assoc < assoc) {
                assoc = _assoc;
                set = s;
            }
        }

        return set;
    }

    private void backwardsConditioning(List<Node> pc, Node target) {
        for (Node x : new LinkedList<>(pc)) {
            List<Node> _pc = new LinkedList<>(pc);
            _pc.remove(x);
            _pc.remove(target);

            List<Node> minAssoc = minAssoc(x, target, _pc);

            this.numIndTests++;

            if (this.independenceTest.checkIndependence(x, target, minAssoc).independent()) {
                pc.remove(x);
            }
        }
    }

    private double association(Node x, Node target, List<Node> s) {
        this.numIndTests++;

        IndependenceResult result = this.independenceTest.checkIndependence(x, target, s);
        return 1.0 - result.getPValue();
    }

    public String getAlgorithmName() {
        return this.symmetric ? "MMMB-SYM" : "MMMB";
    }

    public int getNumIndependenceTests() {
        return this.numIndTests;
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

    private static class MaxMinAssocResult {
        private final Node node;
        private final List<Node> assocSet;

        public MaxMinAssocResult(Node node, List<Node> assocSet) {
            this.node = node;
            this.assocSet = assocSet;
        }

        public Node getNode() {
            return this.node;
        }

        public List<Node> getAssocSet() {
            return this.assocSet;
        }
    }
}





