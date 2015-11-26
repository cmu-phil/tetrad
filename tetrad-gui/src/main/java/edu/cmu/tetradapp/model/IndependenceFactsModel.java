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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * Stores a list of independence facts.
 *
 * @author Joseph Ramsey
 */
public class IndependenceFactsModel implements SessionModel, KnowledgeBoxInput {
    static final long serialVersionUID = 23L;

    private IndependenceFacts facts = new IndependenceFacts();
    private String name = "";

    public IndependenceFactsModel() {
        // do nothing.
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
//     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static IKnowledge serializableInstance() {
        return new Knowledge2();
    }

    public void add(IndependenceFact fact) {
        this.facts.add(fact);
    }

    public String toString() {
        return facts.toString();
    }

    public void remove(IndependenceFact fact) {
        this.facts.remove(fact);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public IndependenceFacts getFacts() {
        return facts;
    }

    public static IndependenceFactsModel loadFacts(Reader reader) throws IOException {
        IndependenceFactsModel facts = new IndependenceFactsModel();
        Set<String> names = new HashSet<String>();
        Map<String, Node> nodes = new HashMap<String, Node>();

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

            List<Node> z = new ArrayList<Node>();

            for (int i = 2; i < tokens.length; i++) {
                z.add(nodes.get(tokens[i]));
            }

            facts.add(new IndependenceFact(nodes.get(tokens[0]), nodes.get(tokens[1]), z));
        }

        return facts;
    }

    public void setFacts(IndependenceFacts facts) {
        this.facts = facts;
    }

    public Graph getSourceGraph() {
        return null;
    }

    public Graph getResultGraph() {
        return null;
    }

    public List<Node> getVariables() {
        return facts.getVariables();
    }

    public List<String> getVariableNames() {
        return facts.getVariableNames();
    }
}



