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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Stores a list of independence facts.
 *
 * @author josephramsey
 */
public class MarkovCheckIndTestModel implements SessionModel, GraphSource {
    private static final long serialVersionUID = 23L;
    private final DataModel dataModel;
    private final Parameters parameters;
    private final Graph graph;
    private String name = "";
    private List<String> vars = new LinkedList<>();
    private transient MarkovCheck markovCheck = null;

    public MarkovCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, Parameters parameters) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.graph = graphSource.getGraph();
        this.parameters = parameters;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    public void setIndependenceTest(IndependenceTest test) {
        this.markovCheck = new MarkovCheck(this.graph, test, this.markovCheck == null ? ConditioningSetType.LOCAL_MARKOV : this.markovCheck.getSetType());

    }

    public MarkovCheck getMarkovCheck() {
        return this.markovCheck;
    }

    @Override
    public Graph getGraph() {
        return this.graph;
    }

    public DataModel getDataModel() {
        return dataModel;
    }

    public Parameters getParameters() {
        return parameters;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public List<String> getVars() {
        return this.vars;
    }

    public void setVars(List<String> vars) {
        this.vars = vars;
    }

    public List<IndependenceResult> getResults(boolean indep) {
        return markovCheck.getResults(indep);
    }
}



