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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Checks the graphoid axioms for a set of Independence Model statements.
 *
 * @author josephramsey
 */
public class GraphoidAxioms {
    private final Set<GraphoidIndFact> facts;
    private final List<Node> nodes;
    private boolean trivialtyAssumed = false;
    private Map<GraphoidAxioms.GraphoidIndFact, String> textSpecs = null;

    /**
     * Constructor.
     *
     * @param facts A set of GraphoidIndFacts.
     * @param nodes The list of nodes.
     * @see GraphoidIndFact k
     */
    public GraphoidAxioms(Set<GraphoidIndFact> facts, List<Node> nodes) {
        this.facts = new LinkedHashSet<>(facts);
        this.nodes = new ArrayList<>(nodes);
    }

    /**
     * Constructor.
     *
     * @param facts     A list of GraphoidIndFacts.
     * @param nodes     The list of nodes.
     * @param textSpecs A map from GraphoidIndFacts to String text specs. The
     *                  text specs are used for printing information to the user
     *                  about which facts are found or are missing.
     * @see GraphoidIndFact
     */
    public GraphoidAxioms(Set<GraphoidIndFact> facts,
                          List<Node> nodes,
                          Map<GraphoidAxioms.GraphoidIndFact, String> textSpecs) {
        this.facts = new LinkedHashSet<>(facts);
        this.nodes = new ArrayList<>(nodes);
        this.textSpecs = new HashMap<>(textSpecs);
    }

    /**
     * The main methods.
     *
     * @param args E.g., "java -cp tetrad-gui-7.1.3-SNAPSHOT-launch.jar edu.cmu.tetrad.search.GraphoidAxioms  udags5.txt 5"
     *             Here, udgas5.txt is a file containing independence models, one per line, where each independence
     *             fast is specified by, e.g., "1:23|56", indicating that 1 is independence of 2 and 3 conditional
     *             on 5 and 6. No more than 9 variables can be handled this way. If you need more, let us know and
     *             we'll change the format.
     */
    public static void main(String... args) {
        try {
            File file = new File(args[0]);
            int numVars = Integer.parseInt(args[1]);
            System.out.println(file.getAbsolutePath());
            FileReader in1 = new FileReader(file);
            BufferedReader in = new BufferedReader(in1);
            String line;
            int index = 0;

            while ((line = in.readLine()) != null) {
                index++;

                System.out.println("\nLine " + index + " " + line);
                line = line.trim();

                List<Node> variables = new ArrayList<>();

                for (int i = 0; i < numVars; i++) {
                    variables.add(new ContinuousVariable("" + i));
                }

                GraphoidAxioms axioms = getGraphoidAxioms(line, variables);
                axioms.ensureTriviality();
                axioms.ensureSymmetry();

                System.out.println(axioms.getIndependenceFacts().getVariableNames());

                axioms.compositionalGraphoid();
            }
        } catch (IOException e) {
            System.out.println("E.g., java -cp tetrad-gui-7.1.3-SNAPSHOT-launch.jar edu.cmu.tetrad.search.GraphoidAxioms  udags5.txt 5\n");
            throw new RuntimeException(e);
        }
    }

    private static GraphoidAxioms getGraphoidAxioms(String line, List<Node> nodes) throws IOException {
        Set<GraphoidAxioms.GraphoidIndFact> facts = new LinkedHashSet<>();
        Map<GraphoidAxioms.GraphoidIndFact, String> textSpecs = new HashMap<>();

        if (!line.isEmpty()) {
            String[] split = line.split(",");
            for (String ic : split) {
                Set<Node> x = new HashSet<>();
                Set<Node> y = new HashSet<>();
                Set<Node> z = new HashSet<>();

                String[] tokens1 = ic.split("\\|");
                String[] tokens2 = tokens1[0].split(":");

                for (int i = 0; i < tokens2[0].length(); i++) {
                    int i1 = Integer.parseInt(tokens2[0].substring(i, i + 1).trim());
                    x.add(nodes.get(i1));
                }

                for (int i = 0; i < tokens2[1].length(); i++) {
                    String substring = tokens2[1].substring(i, i + 1);
                    int i1 = Integer.parseInt(substring.trim());
                    y.add(nodes.get(i1));
                }

                if (tokens1.length == 2) {
                    for (int i = 0; i < tokens1[1].length(); i++) {
                        int i1 = Integer.parseInt(tokens1[1].substring(i, i + 1).trim());
                        z.add(nodes.get(i1));
                    }
                }

                GraphoidAxioms.GraphoidIndFact fact = new GraphoidAxioms.GraphoidIndFact(x, y, z);
                facts.add(fact);
                textSpecs.put(fact, ic);
            }
        }

        return new GraphoidAxioms(facts, nodes, textSpecs);
    }

    /**
     * Checked whether the IM is a semigraphoid.
     *
     * @return True if so.
     */
    public boolean semigraphoid() {
        return symmetry() && decomposition() && weakUnion() && contraction();
    }

    /**
     * Checks whether teh IM is a semigraphoid.
     *
     * @return True if so.
     */
    public boolean graphoid() {
        return semigraphoid() && intersection();
    }

    /**
     * Checks whether the IM is a compositional graphoid.
     *
     * @return True if so.
     */
    public boolean compositionalGraphoid() {
        return graphoid() && composition();
    }

    /**
     * Returns the independence facts in the form 1:2|3 for use in various Tetrad algorithms. Assumes
     * decomposition and compositios, so that there are no complex independence facts.
     */
    public IndependenceFacts getIndependenceFacts() {
        IndependenceFacts ifFacts = new IndependenceFacts();

        for (GraphoidIndFact ic : facts) {
            for (Node x : ic.getX()) {
                for (Node y : ic.getY()) {
                    ifFacts.add(new IndependenceFact(x, y, ic.getZ()));
                }
            }
        }

        ifFacts.setNodes(nodes);

        return ifFacts;
    }

    /**
     * Checks is symmetry holds--i.e., X ⊥⊥ Y | Z ==&gt; Y ⊥⊥ X | Z
     */
    public boolean symmetry() {

        F:
        for (GraphoidIndFact fact : facts) {
            for (GraphoidIndFact fact2 : facts) {
                if (fact == fact2) continue;
                if (fact.getX().equals(fact2.getY()) && fact.getY().equals(fact2.getX())
                        && fact.getZ().equals(fact2.getZ())) {
                    continue F;
                }
            }

            TetradLogger.getInstance().forceLogMessage("Symmetry fails for " + fact);

            return false;
        }

        return true;
    }

    /**
     * Checks if decomposition holds, e.g., X ⊥⊥ (Y ∪ W) |Z ==&gt; (X ⊥⊥ Y |Z) ∧ (X ⊥⊥ W |Z)
     */
    public boolean decomposition() {
        boolean found0 = false;

        for (GraphoidIndFact fact : facts) {
            Set<Node> X = fact.getX();
            Set<Node> YW = fact.getY();
            Set<Node> Z = fact.getZ();

            List<Node> YWList = new ArrayList<>(YW);

            SublistGenerator gen = new SublistGenerator(YWList.size(), YWList.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                Set<Node> Y = GraphUtils.asSet(choice, YWList);
                Set<Node> W = new HashSet<>(YW);
                W.removeAll(Y);

                if (trivialtyAssumed && X.isEmpty() || Y.isEmpty() || W.isEmpty()) continue;

                boolean found = false;

                for (GraphoidIndFact _fact : facts) {
                    if (_fact.getY().equals(Y)) {
                        found = true;
                    }
                }

                if (!found) {
                    GraphoidIndFact fact1 = new GraphoidIndFact(X, Y, Z);

                    if (textSpecs != null) {
                        TetradLogger.getInstance().forceLogMessage("Decomposition fails:" +
                                " Have " + textSpecs.get(fact) +
                                "; Missing " + fact1);
                    } else {
                        TetradLogger.getInstance().forceLogMessage("Decomposition fails:" +
                                " Have " + fact +
                                "; Missing " + fact1);
                    }

                    found0 = true;
                }

                found = false;

                for (GraphoidIndFact _fact : facts) {
                    if (_fact.getY().equals(W)) {
                        found = true;
                    }
                }

                if (!found) {
                    GraphoidIndFact fact1 = new GraphoidIndFact(X, W, Z);

                    if (textSpecs != null) {
                        TetradLogger.getInstance().forceLogMessage("Decomposition fails:" +
                                " Have " + textSpecs.get(fact) +
                                "; Missing " + fact1);
                    } else {
                        TetradLogger.getInstance().forceLogMessage("Decomposition fails:" +
                                " Have " + fact +
                                "; Missing " + fact1);
                    }

                    found0 = true;
                }
            }
        }

        return !found0;
    }

    /**
     * Checks is weak union holds, e.g., X _||_ Y U W | Z ==&gt; X _||_ Y | Z U W
     */
    public boolean weakUnion() {
        boolean found0 = false;

        for (GraphoidIndFact fact : facts) {
            Set<Node> X = fact.getX();
            Set<Node> YW = fact.getY();
            Set<Node> Z = fact.getZ();

            List<Node> YWList = new ArrayList<>(YW);

            SublistGenerator gen = new SublistGenerator(YW.size(), YW.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                Set<Node> Y = GraphUtils.asSet(choice, YWList);
                Set<Node> W = new HashSet<>(YW);
                W.removeAll(Y);

                Set<Node> ZW = new HashSet<>(Z);
                ZW.addAll(W);

                if (trivialtyAssumed && X.isEmpty() || Y.isEmpty() || W.isEmpty()) continue;

                boolean found = false;

                for (GraphoidIndFact _fact : facts) {
                    if (_fact.getX().equals(X) && _fact.getY().equals(Y) && _fact.getZ().equals(ZW)) {
                        found = true;
                    }
                }

                if (!found) {
                    GraphoidIndFact newFact = new GraphoidIndFact(X, Y, ZW);

                    if (textSpecs != null) {
                        TetradLogger.getInstance().forceLogMessage("Weak Union fails:" +
                                " Have " + textSpecs.get(fact) +
                                "; Missing " + newFact);
                    } else {
                        TetradLogger.getInstance().forceLogMessage("Weak Union fails:" +
                                " Have " + fact +
                                "; Missing " + newFact);
                    }

                    found0 = true;
                }
            }
        }

        return !found0;
    }

    /**
     * Checks if contraction holds--e.g., (X ⊥⊥ Y |Z) ∧ (X ⊥⊥ W |Z ∪ Y) ==&gt; X ⊥⊥ (Y ∪ W) |Z
     */
    public boolean contraction() {
        boolean found0 = false;

        for (GraphoidIndFact fact1 : new HashSet<>(facts)) {
            Set<Node> X = fact1.getX();
            Set<Node> Y = fact1.getY();
            Set<Node> Z = fact1.getZ();

            Set<Node> ZY = new HashSet<>(Z);
            ZY.addAll(Y);

            for (GraphoidIndFact fact2 : new HashSet<>(facts)) {
                if (fact1 == fact2) continue;

                if (fact2.getX().equals(X) && fact2.getZ().equals(ZY)) {
                    Set<Node> W = fact2.getY();
                    if (X.equals(W)) continue;
                    if (X.equals(ZY)) continue;
                    if (W.equals(ZY)) continue;

                    Set<Node> YW = new HashSet<>(Y);
                    YW.addAll(W);

                    boolean found = false;

                    for (GraphoidIndFact _fact : facts) {
                        if (_fact.getX().equals(X) && _fact.getY().equals(YW) && _fact.getZ().equals(Z)) {
                            found = true;
                        }
                    }

                    if (!found) {

                        GraphoidIndFact newFact = new GraphoidIndFact(X, YW, Z);

                        if (textSpecs != null) {
                            TetradLogger.getInstance().forceLogMessage("Contraction fails:" +
                                    " Have " + textSpecs.get(fact1) + " and " + textSpecs.get(fact2) +
                                    "; Missing " + newFact);
                        } else {
                            TetradLogger.getInstance().forceLogMessage("Contraction fails:" +
                                    " Have " + fact1 + " and " + fact2 +
                                    "; Missing " + newFact);
                        }

                        found0 = true;
                    }
                }
            }
        }

        return !found0;
    }

    /**
     * Checks if intersection holds--e.g., (X ⊥⊥ Y | (Z ∪ W)) ∧ (X ⊥⊥ W | (Z ∪ Y)) ==&gt; X ⊥⊥ (Y ∪ W) |Z
     */
    public boolean intersection() {
        boolean found0 = false;

        for (GraphoidIndFact fact1 : facts) {
            final Set<Node> X = fact1.getX();
            Set<Node> Y = fact1.getY();
            Set<Node> ZW = fact1.getZ();

            List<Node> ZWList = new ArrayList<>(ZW);

            SublistGenerator gen = new SublistGenerator(ZWList.size(), ZWList.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                Set<Node> Z = GraphUtils.asSet(choice, ZWList);
                Set<Node> W = new HashSet<>(ZW);
                W.removeAll(Z);

                if (trivialtyAssumed && X.isEmpty() || W.isEmpty()) continue;

                Set<Node> ZY = new HashSet<>(Z);
                ZY.addAll(Y);

                boolean found = false;

                for (GraphoidIndFact _fact : facts) {
                    if (_fact.getX().equals(X) && _fact.getY().equals(W) && _fact.getZ().equals(ZY)) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    Set<Node> YW = new HashSet<>(Y);
                    YW.addAll(W);

                    boolean found2 = false;

                    for (GraphoidIndFact _fact : facts) {
                        if (_fact.getX().equals(X) && _fact.getY().equals(YW) && _fact.getZ().equals(Z)) {
                            found2 = true;
                        }
                    }

                    if (!found2) {
                        GraphoidIndFact fact2 = new GraphoidIndFact(X, W, ZY);
                        GraphoidIndFact newFact = new GraphoidIndFact(X, YW, Z);

                        if (textSpecs != null) {
                            TetradLogger.getInstance().forceLogMessage("Intersection fails:" +
                                    " Have " + textSpecs.get(fact1) + " and " + textSpecs.get(fact2) +
                                    "; Missing " + newFact);
                        } else {
                            TetradLogger.getInstance().forceLogMessage("Intersection fails:" +
                                    " Have " + fact1 + " and " + fact2 +
                                    "; Missing " + newFact);
                        }

                        found0 = true;
                    }
                }
            }
        }

        return !found0;
    }

    /**
     * Checks if composition holds--e.g., (X ⊥⊥ Y | Z) ∧ (X ⊥⊥ W |Z) ==&gt; X ⊥⊥ (Y ∪ W) |Z
     */
    public boolean composition() {
        boolean found0 = false;

        for (GraphoidIndFact fact1 : new HashSet<>(facts)) {
            Set<Node> X = fact1.getX();
            Set<Node> Y = fact1.getY();
            Set<Node> Z = fact1.getZ();

            for (GraphoidIndFact fact2 : new HashSet<>(facts)) {
                if (fact1 == fact2) continue;

                if (fact2.getX().equals(X) && fact2.getZ().equals(Z)) {
                    Set<Node> W = fact2.Y;

                    Set<Node> YW = new HashSet<>(Y);
                    YW.addAll(W);

                    boolean found = false;

                    for (GraphoidIndFact _fact : facts) {
                        if (_fact.getX().equals(X) && _fact.getY().equals(YW) && _fact.getZ().equals(Z)) {
                            found = true;
                        }
                    }

                    if (!found) {
                        GraphoidIndFact newFact = new GraphoidIndFact(X, YW, Z);

                        if (textSpecs != null) {
                            TetradLogger.getInstance().forceLogMessage("Composition fails:" +
                                    " Have " + textSpecs.get(fact1) + " and " + textSpecs.get(fact2) +
                                    "; Missing " + newFact);
                        } else {
                            TetradLogger.getInstance().forceLogMessage("Composition fails:" +
                                    " Have " + fact1 + " and " + fact2 +
                                    "; Missing " + newFact);
                        }

                        found0 = true;
                    }
                }
            }
        }

        return !found0;
    }

    /**
     * Sets whether triviality as assumed.
     */
    public void ensureTriviality() {
        this.trivialtyAssumed = true;
    }

    /**
     * Sets symmetry as assumed--i.e., ensures that X ⊥⊥ Y | Z ==&gt; Y ⊥⊥ X | Z.
     */
    public void ensureSymmetry() {
        for (GraphoidIndFact fact : new HashSet<>(facts)) {
            Set<Node> X = fact.getX();
            Set<Node> Y = fact.getY();
            Set<Node> Z = fact.getZ();
            GraphoidIndFact newFact = new GraphoidIndFact(Y, X, Z);
            facts.add(newFact);
            if (textSpecs != null) {
                textSpecs.put(newFact, textSpecs.get(fact));
            }
        }
    }

    /**
     * Represents a graphoid independence fact--i.e., a fact in a general independence model (IM)
     * X _||_Y | Z.
     */
    public static class GraphoidIndFact {
        private final Set<Node> X;
        private final Set<Node> Y;
        private final Set<Node> Z;

        public GraphoidIndFact(Set<Node> X, Set<Node> Y, Set<Node> Z) {
            if (X.isEmpty() || Y.isEmpty()) throw new IllegalArgumentException("X or Y is empty");
            if (!disjoint(X, Y, Z)) throw new IllegalArgumentException();

            this.X = new HashSet<>(X);
            this.Y = new HashSet<>(Y);
            this.Z = new HashSet<>(Z);
        }

        public Set<Node> getX() {
            return new HashSet<>(X);
        }

        public Set<Node> getY() {
            return new HashSet<>(Y);
        }

        public Set<Node> getZ() {
            return new HashSet<>(Z);
        }

        public int hashCode() {
            return 1;
        }

        public boolean equals(Object o) {
            if (!(o instanceof GraphoidIndFact)) return false;
            GraphoidIndFact _fact = (GraphoidIndFact) o;
            return X.equals(_fact.X) && Y.equals(_fact.Y) && Z.equals(_fact.Z);
        }

        public String toString() {
            return X + " : " + Y + " | " + Z;
        }

        private boolean disjoint(Set<Node> set1, Set<Node> set2, Set<Node> set3) {
            return intersection(set1, set2).isEmpty()
                    && intersection(set1, set3).isEmpty()
                    || !intersection(set2, set3).isEmpty();
        }

        private Set<Node> intersection(Set<Node> set1, Set<Node> set2) {
            Set<Node> W = new HashSet<>(set1);
            W.retainAll(set2);
            return W;
        }
    }
}
