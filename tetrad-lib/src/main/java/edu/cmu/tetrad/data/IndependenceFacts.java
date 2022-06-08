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
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.PermutationGenerator;

import java.util.*;

/**
 * Stores a list of independence facts.
 *
 * @author Joseph Ramsey
 */
public class IndependenceFacts implements DataModel {
    static final long serialVersionUID = 23L;
    private List<Node> nodes = new ArrayList<>();

    private Set<IndependenceFact> unsortedFacts = new LinkedHashSet<>();
    private String name = "";
    private IKnowledge knowledge = new Knowledge2();

    public IndependenceFacts() {
        // blank, used in reflection so don't delete.
    }

    public IndependenceFacts(Graph graph) {
        IndTestDSep dsep = new IndTestDSep(graph);

        Set<IndependenceFact> facts = new HashSet<>();

        nodes = graph.getNodes();

        DepthChoiceGenerator gen = new DepthChoiceGenerator(nodes.size(), nodes.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (choice.length < 2) continue;

            PermutationGenerator permGen = new PermutationGenerator(choice.length);
            int[] perm;

            while (((perm = permGen.next()) != null)) {
                Node x = nodes.get(choice[perm[0]]);
                Node y = nodes.get(choice[perm[1]]);

                List<Node> Z = new ArrayList<>();

                for (int i = 2; i < perm.length; i++) {
                    Z.add(nodes.get(choice[perm[i]]));
                }

                if (dsep.checkIndependence(x, y, Z).independent()) {
                    facts.add(new IndependenceFact(x, y, Z));
                }
            }

            this.unsortedFacts = facts;
        }
    }

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
     */
    public static IndependenceFacts serializableInstance() {
        return new IndependenceFacts();
    }

    public void add(IndependenceFact fact) {
        this.unsortedFacts.add(fact);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");

        for (IndependenceFact fact : unsortedFacts) {
            builder.append(fact).append("\n");
        }

        return builder.toString();
    }

    @Override
    public boolean isContinuous() {
        return false;
    }

    @Override
    public boolean isDiscrete() {
        return false;
    }

    @Override
    public boolean isMixed() {
        return false;
    }

    @Override
    public Node getVariable(String name) {
        return null;
    }

    @Override
    public DataModel copy() {
        return null;
    }

    public void remove(IndependenceFact fact) {
//        this.facts.remove(fact);
        this.unsortedFacts.remove(fact);
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public boolean isIndependent(Node x, Node y, List<Node> z) {
        boolean found = false;

        for (IndependenceFact fact : unsortedFacts) {
            if (((fact.getX().equals(x) && fact.getY().equals(y))
                    || (fact.getX().equals(y) && fact.getY().equals(x)))
                    &&  new HashSet<>(fact.getZ()).equals(new HashSet<>(z))) {
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

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    public List<Node> getVariables() {
        if (nodes != null) {
            return nodes;
        }

        Set<Node> variables = new HashSet<>();

        for (IndependenceFact fact : unsortedFacts) {
            variables.add(fact.getX());
            variables.add(fact.getY());
            variables.addAll(fact.getZ());
        }

        if (nodes != null) {
            if (new HashSet<>(variables).equals(new HashSet<>(nodes))) {
                return nodes;
            } else {
                throw new IllegalArgumentException("The supplied order is not precisely for the variables in the facts.");
            }
        }

        return new ArrayList<>(variables);
    }

    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> names = new ArrayList<>();

        for (Node node : variables) {
            names.add(node.getName());
        }

        return names;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = Collections.unmodifiableList(nodes);
    }

    public int size() {
        return unsortedFacts.size();
    }

    public List<IndependenceFact> getFacts() {
        return new ArrayList<>(unsortedFacts);
    }
}



