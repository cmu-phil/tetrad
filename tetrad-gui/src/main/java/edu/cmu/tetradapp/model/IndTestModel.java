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
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.editor.IndependenceResult;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores a list of independence facts.
 *
 * @author Joseph Ramsey
 */
public class IndTestModel implements SessionModel {
    static final long serialVersionUID = 23L;

    private List<IndTestProducer> indTestProducers;
    private String name = "";
    private LinkedList<String> vars = new LinkedList<String>();
    private List<List<IndependenceResult>> results;

    public IndTestModel() {
        // do nothing.
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static IKnowledge serializableInstance() {
        return new Knowledge2();
    }

    public IndTestModel(IndTestProducer producer) {
        indTestProducers = new ArrayList<IndTestProducer>();
        indTestProducers.add(producer);
    }

    public IndTestModel(IndTestProducer producer1, IndTestProducer producer2) {
        indTestProducers = new ArrayList<IndTestProducer>();
        indTestProducers.add(producer1);
        indTestProducers.add(producer2);
    }

    public IndTestModel(IndTestProducer producer1, IndTestProducer producer2, IndTestProducer producer3) {
        indTestProducers = new ArrayList<IndTestProducer>();
        indTestProducers.add(producer1);
        indTestProducers.add(producer2);
        indTestProducers.add(producer3);
    }

    public IndTestModel(IndTestProducer producer1, IndTestProducer producer2, IndTestProducer producer3, IndTestProducer producer4) {
        indTestProducers = new ArrayList<IndTestProducer>();
        indTestProducers.add(producer1);
        indTestProducers.add(producer2);
        indTestProducers.add(producer3);
        indTestProducers.add(producer4);
    }

    public List<IndTestProducer> getIndTestProducers() {
        return indTestProducers;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setVars(LinkedList<String> vars) {
        this.vars = vars;
    }

    public LinkedList<String> getVars() {
        return vars;
    }

    public List<List<IndependenceResult>> getResults() {
        return results;
    }

    public void setResults(List<List<IndependenceResult>> results) {
        this.results = results;
    }
}



