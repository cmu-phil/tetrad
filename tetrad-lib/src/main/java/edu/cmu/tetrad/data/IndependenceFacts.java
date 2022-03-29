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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores a list of independence facts.
 *
 * @author Joseph Ramsey
 */
public class IndependenceFacts implements DataModel {
    static final long serialVersionUID = 23L;

    private Set<IndependenceFact> unsortedFacts = new HashSet<>();
    private String name = "";
    private IKnowledge knowledge = new Knowledge2();

    public IndependenceFacts() {
        // blank
    }

    public void add(final IndependenceFact fact) {
        this.unsortedFacts.add(fact);
    }

    public IndependenceFacts(final IndependenceFacts facts) {
        this();
        this.unsortedFacts = new HashSet<>(facts.unsortedFacts);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static IndependenceFacts serializableInstance() {
        return new IndependenceFacts();
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();

        for (final IndependenceFact fact : this.unsortedFacts) {
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
    public Node getVariable(final String name) {
        return null;
    }

    @Override
    public DataModel copy() {
        return null;
    }

    public void remove(final IndependenceFact fact) {
//        this.facts.remove(fact);
        this.unsortedFacts.remove(fact);
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public boolean isIndependent(final Node x, final Node y, final Node... z) {
        final IndependenceFact fact = new IndependenceFact(x, y, z);
        return this.unsortedFacts.contains(fact);
    }

    public boolean isIndependent(final Node x, final Node y, final List<Node> z) {
        final IndependenceFact fact = new IndependenceFact(x, y, z);
        System.out.println("Looking up " + fact + " in " + this.unsortedFacts);
        return this.unsortedFacts.contains(fact);
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    public List<Node> getVariables() {
        final Set<Node> variables = new HashSet<>();

        for (final IndependenceFact fact : this.unsortedFacts) {
            variables.add(fact.getX());
            variables.add(fact.getY());

            for (final Node z : fact.getZ()) {
                variables.add(z);
            }
        }

        return new ArrayList<>(variables);
    }

    public List<String> getVariableNames() {
        final List<Node> variables = getVariables();
        final List<String> names = new ArrayList<>();

        for (final Node node : variables) {
            names.add(node.getName());
        }

        return names;
    }
}



