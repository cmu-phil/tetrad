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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.LinkedList;
import java.util.List;

/**
 * A model for the IDA check. This takes a graph and a data model and checks IDA facts for all pairs of distinct nodes.
 * It defers these judgments to the class IdaCheck.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IdaModel implements SessionModel, GraphSource, KnowledgeBoxInput {
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
    private transient IdaCheck idaCheck = null;
    /**
     * The knowledge to use. This will be passed to the underlying Markov check object. For facts odf the form X _||_ Y
     * | Z, X and Y should be in the last tier, and Z should be in previous tiers.
     */
    private Knowledge knowledge;

    /**
     * Constructs a new Markov checker with the given data model, graph, and parameters.
     *
     * @param dataModel   the data model.
     * @param graphSource the graph source.
     * @param parameters  the parameters.
     */
    public IdaModel(DataWrapper dataModel, GraphSource graphSource,
                    Parameters parameters) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.graph = graphSource.getGraph();
        this.parameters = parameters;

        // Make sure the data model is a DataSet.
        if (!(this.dataModel instanceof DataSet)) {
            throw new IllegalArgumentException("Expecting a data set.");
        }

        this.idaCheck = new IdaCheck(this.graph, (DataSet) this.dataModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link Knowledge} object
     * @see TetradSerializableUtils
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    /**
     * Returns the underlying MarkovCheck object.
     *
     * @return the underlying MarkovCheck object.
     */
    public IdaCheck getIdaCheck() {
        return this.idaCheck;
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
        return idaCheck.getNodes();
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



