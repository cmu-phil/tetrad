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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Serial;
import java.util.*;

/**
 * Stores a list of independence facts.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndependenceFactsModel implements KnowledgeBoxInput {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence facts.
     */
    private IndependenceFacts facts = new IndependenceFacts();

    /**
     * The name of the model.
     */
    private String name = "";

    /**
     * <p>Constructor for IndependenceFactsModel.</p>
     */
    public IndependenceFactsModel() {
        // do nothing.
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    /**
     * <p>loadFacts.</p>
     *
     * @param reader a {@link java.io.Reader} object
     * @return a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @throws java.io.IOException if any.
     */
    public static IndependenceFactsModel loadFacts(Reader reader) throws IOException {
        IndependenceFactsModel facts = new IndependenceFactsModel();
        Set<String> names = new HashSet<>();
        Map<String, Node> nodes = new HashMap<>();

        BufferedReader in = new BufferedReader(reader);
        String line;

        while ((line = in.readLine()) != null) {
            String[] tokens = line.split("[ ,;_|]+");

            if (tokens.length == 0) continue;
            if (tokens.length < 2) throw new IllegalArgumentException(
                    "Must specify at least two variables--e.g. X1 X2, for X1 _||_ X2.");

            for (String token : tokens) {
                names.add(token);

                if (!nodes.containsKey(token)) {
                    nodes.put(token, new GraphNode(token));
                }
            }

            Set<Node> z = new HashSet<>();

            for (int i = 2; i < tokens.length; i++) {
                z.add(nodes.get(tokens[i]));
            }

            facts.add(new IndependenceFact(nodes.get(tokens[0]), nodes.get(tokens[1]), z));
        }

        return facts;
    }

    /**
     * <p>add.</p>
     *
     * @param fact a {@link edu.cmu.tetrad.graph.IndependenceFact} object
     */
    public void add(IndependenceFact fact) {
        this.facts.add(fact);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.facts.toString();
    }

    /**
     * <p>remove.</p>
     *
     * @param fact a {@link edu.cmu.tetrad.graph.IndependenceFact} object
     */
    public void remove(IndependenceFact fact) {
        this.facts.remove(fact);
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /** {@inheritDoc} */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>facts</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.IndependenceFacts} object
     */
    public IndependenceFacts getFacts() {
        return this.facts;
    }

    /**
     * <p>Setter for the field <code>facts</code>.</p>
     *
     * @param facts a {@link edu.cmu.tetrad.data.IndependenceFacts} object
     */
    public void setFacts(IndependenceFacts facts) {
        if (facts == null) throw new NullPointerException("FActs is null.");
        this.facts = facts;
    }

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        return null;
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return null;
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return this.facts.getVariables();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        return this.facts.getVariableNames();
    }
}



