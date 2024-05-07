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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.PermutationGenerator;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.Serial;
import java.util.*;

/**
 * Stores a list of independence facts.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndependenceFacts implements DataModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The nodes in the graph.
     */
    private List<Node> nodes = new ArrayList<>();

    /**
     * The unsorted facts.
     */
    private Set<IndependenceFact> unsortedFacts = new LinkedHashSet<>();

    /**
     * The name of the independence facts.
     */
    private String name = "";

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for IndependenceFacts.</p>
     */
    public IndependenceFacts() {
        // blank, used in reflection so don't delete.
    }

    /**
     * <p>Constructor for IndependenceFacts.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public IndependenceFacts(Graph graph) {
        MsepTest msep = new MsepTest(graph);

        Set<IndependenceFact> facts = new HashSet<>();

        nodes = graph.getNodes();

        SublistGenerator gen = new SublistGenerator(nodes.size(), nodes.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (choice.length < 2) continue;

            PermutationGenerator permGen = new PermutationGenerator(choice.length);
            int[] perm;

            while (((perm = permGen.next()) != null)) {
                Node x = nodes.get(choice[perm[0]]);
                Node y = nodes.get(choice[perm[1]]);

                Set<Node> Z = new HashSet<>();

                for (int i = 2; i < perm.length; i++) {
                    Z.add(nodes.get(choice[perm[i]]));
                }

                if (msep.checkIndependence(x, y, Z).isIndependent()) {
                    facts.add(new IndependenceFact(x, y, Z));
                }
            }

            this.unsortedFacts = facts;
        }
    }

    /**
     * <p>Constructor for IndependenceFacts.</p>
     *
     * @param facts a {@link edu.cmu.tetrad.data.IndependenceFacts} object
     */
    public IndependenceFacts(IndependenceFacts facts) {
        this();
        if (facts == null) throw new NullPointerException("Facts is null.");
        if (facts.nodes == null) throw new NullPointerException("Facts nodes is null.");
        this.unsortedFacts = new HashSet<>(facts.unsortedFacts);
        this.name = facts.name;
        this.knowledge = facts.knowledge.copy();
        this.nodes = new ArrayList<>(facts.nodes);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.IndependenceFacts} object
     */
    public static IndependenceFacts serializableInstance() {
        return new IndependenceFacts();
    }

    /**
     * <p>add.</p>
     *
     * @param fact a {@link edu.cmu.tetrad.graph.IndependenceFact} object
     */
    public void add(IndependenceFact fact) {
        this.unsortedFacts.add(fact);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");

        for (IndependenceFact fact : unsortedFacts) {
            builder.append(fact).append("\n");
        }

        return builder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContinuous() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDiscrete() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMixed() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel copy() {
        return null;
    }

    /**
     * <p>remove.</p>
     *
     * @param fact a {@link edu.cmu.tetrad.graph.IndependenceFact} object
     */
    public void remove(IndependenceFact fact) {
//        this.facts.remove(fact);
        this.unsortedFacts.remove(fact);
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>isIndependent.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isIndependent(Node x, Node y, Node... z) {
        for (IndependenceFact fact : unsortedFacts) {
            if ((fact.getX().equals(x) && fact.getY().equals(y)) || (fact.getX().equals(y) && fact.getY().equals(x))) {
                Set<Node> cond = new HashSet<>();
                Collections.addAll(cond, z);

                if (cond.equals(new HashSet<>(fact.getZ()))) {
                    return true;
                }

//                && fact.getZ().equals(z)) {
//
//                return true;
            }
        }

        return false;


//        IndependenceFact fact = new IndependenceFact(x, y, z);
//        return unsortedFacts.contains(fact);
    }

    /**
     * <p>isIndependent.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return a boolean
     */
    public boolean isIndependent(Node x, Node y, Set<Node> z) {
        boolean found = false;

        for (IndependenceFact fact : unsortedFacts) {
            if (((fact.getX().equals(x) && fact.getY().equals(y))
                 || (fact.getX().equals(y) && fact.getY().equals(x)))
                && new HashSet<>(fact.getZ()).equals(new HashSet<>(z))) {
                found = true;
                break;
            }
        }

//        IndependenceFact fact = new IndependenceFact(x, y, z);
//        boolean found2 = unsortedFacts.contains(fact);

//        if (found != found2) {
//            System.out.println("Not the same.");
//        }

        return found;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        if (nodes != null && !nodes.isEmpty()) {
            return nodes;
        }

        Set<Node> variables = new HashSet<>();

        for (IndependenceFact fact : unsortedFacts) {
            variables.add(fact.getX());
            variables.add(fact.getY());
            variables.addAll(fact.getZ());
        }

        if (nodes != null && !nodes.isEmpty()) {
            if (new HashSet<>(variables).equals(new HashSet<>(nodes))) {
                return nodes;
            } else {
                throw new IllegalArgumentException("The supplied order is not precisely for the variables in the facts.");
            }
        }

        return new ArrayList<>(variables);
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> names = new ArrayList<>();

        for (Node node : variables) {
            names.add(node.getName());
        }

        return names;
    }

    /**
     * <p>Setter for the field <code>nodes</code>.</p>
     *
     * @param nodes a {@link java.util.List} object
     */
    public void setNodes(List<Node> nodes) {
        this.nodes = Collections.unmodifiableList(nodes);
    }

    /**
     * <p>size.</p>
     *
     * @return a int
     */
    public int size() {
        return unsortedFacts.size();
    }

    /**
     * <p>getFacts.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<IndependenceFact> getFacts() {
        return new ArrayList<>(unsortedFacts);
    }
}



