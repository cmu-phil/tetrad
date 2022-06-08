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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceResult;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores a list of independence facts.
 *
 * @author Joseph Ramsey
 */
public class MarkovCheckIndTestModel implements SessionModel, GraphSource {
    static final long serialVersionUID = 23L;

    private final IndTestProducer indTestProducer;
    private String name = "";
    private List<String> vars = new LinkedList<>();
    private List<IndependenceResult> results = new ArrayList<>();
    private final Graph graph;

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static IKnowledge serializableInstance() {
        return new Knowledge2();
    }


    public MarkovCheckIndTestModel(IndTestProducer producer, GraphWrapper graphWrapper) {
        this.indTestProducer = producer;
        this.graph = graphWrapper.getGraph();
    }

    public IndTestProducer getIndTestProducer() {
        return this.indTestProducer;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Graph getGraph() {
        return this.graph;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setVars(List<String> vars) {
        this.vars = vars;
    }

    public List<String> getVars() {
        return this.vars;
    }

    public List<IndependenceResult> getResults() {
        return this.results;
    }

    public void setResults(List<IndependenceResult> results) {
        this.results = results;
    }

}



