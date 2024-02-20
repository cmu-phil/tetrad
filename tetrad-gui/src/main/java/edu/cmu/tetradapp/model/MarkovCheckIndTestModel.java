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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.LinkedList;
import java.util.List;

/**
 * A model for the Markov check. The Markov check for a given graph and dataset checks whether the graph is Markov with
 * respect to the dataset. The Markov check can be used to check whether a graph is Markov with respect to a dataset, or
 * whether a graph is Markov with respect to a dataset and a set of variables. The Markov check can also be used to
 * check whether a graph is Markov with respect to a dataset and a set of variables, given a set of knowledge. For facts
 * of the form X _||_ Y | Z, X and Y should be in the last tier of the knowledge, and Z should be in previous tiers.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MarkovCheckIndTestModel implements SessionModel, GraphSource, KnowledgeBoxInput {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The data model to check.
     */
    private final DataModel dataModel;
    /**
     * The graph to check.
     */
    private final Graph graph;
    /**
     * The parameters.
     */
    private final Parameters parameters;
    /**
     * The name of this model.
     */
    private String name = "";
    /**
     * The variables to check.
     */
    private List<String> vars = new LinkedList<>();
    /**
     * The Markov check object.
     */
    private transient MarkovCheck markovCheck = null;
    /**
     * The knowledge to use. This will be passed to the underlying Markov check object. For facts odf the form X _||_ Y
     * | Z, X and Y should be in the last tier, and Z should be in previous tiers.
     */
    private Knowledge knowledge;

    /**
     * Constructs a new Markov checer with the given data model, graph, and parameters.
     *
     * @param dataModel   the data model.
     * @param graphSource the graph source.
     * @param parameters  the parameters.
     */
    public MarkovCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, Parameters parameters) {
        this(dataModel, graphSource, null, parameters);
    }

    /**
     * Constructs a new Markov checker with the given data model, graph, and parameters.
     *
     * @param dataModel   the data model.
     * @param graphSource the graph source.
     * @param parameters  the parameters.
     * @param knowlegeBox a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public MarkovCheckIndTestModel(DataWrapper dataModel, GraphSource graphSource, KnowledgeBoxModel knowlegeBox,
                                   Parameters parameters) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.graph = graphSource.getGraph();
        this.parameters = parameters;

        if (knowlegeBox != null) {
            this.knowledge = knowlegeBox.getKnowledge();
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     * @see TetradSerializableUtils
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    /**
     * Sets the independence test and constructs the underlying MarkovCheck object.
     *
     * @param test the independence test.
     */
    public void setIndependenceTest(IndependenceTest test) {
        this.markovCheck = new MarkovCheck(this.graph, test, this.markovCheck == null ? ConditioningSetType.LOCAL_MARKOV : this.markovCheck.getSetType());

        if (this.knowledge != null) {
            this.markovCheck.setKnowledge(this.knowledge);
        }
    }

    /**
     * Returns the underlying MarkovCheck object.
     *
     * @return the underlying MarkovCheck object.
     */
    public MarkovCheck getMarkovCheck() {
        return this.markovCheck;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the graph.
     */
    @Override
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * Returns the data model.
     *
     * @return the data model.
     */
    public DataModel getDataModel() {
        return dataModel;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters.
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of this model.
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of this model.
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the variables to check.
     *
     * @return the variables to check.
     */
    public List<String> getVars() {
        return this.vars;
    }

    /**
     * Sets the variables to check.
     *
     * @param vars the variables to check.
     */
    public void setVars(List<String> vars) {
        this.vars = vars;
    }

    /**
     * Returns the results of the Markov check.
     *
     * @param indep whether to return the results for the independence test or the dependence test.
     * @return the results of the Markov check.
     */
    public List<IndependenceResult> getResults(boolean indep) {
        return markovCheck.getResults(indep);
    }

    /**
     * Returns the knowledge to use. This will be passed to the underlying Markov check object. For facts of the form X
     * _||_ Y | Z, X and Y should be in the last tier, and Z should be in previous tiers.
     *
     * @return the knowledge to use.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge to use. This will be passed to the underlying Markov check object. For facts of the form X
     * _||_ Y | Z, X and Y should be in the last tier, and Z should be in previous tiers.
     *
     * @param knowledge a knowledge object.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge.copy();

        if (this.markovCheck != null) {
            this.markovCheck.setKnowledge(this.knowledge);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the source graph.
     */
    @Override
    public Graph getSourceGraph() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the result graph.
     */
    @Override
    public Graph getResultGraph() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the variables.
     */
    @Override
    public List<Node> getVariables() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the variable names.
     */
    @Override
    public List<String> getVariableNames() {
        return null;
    }
}



