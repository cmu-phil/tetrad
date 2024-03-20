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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.LinkedList;
import java.util.List;

/**
 * A model for the IDA check. This model is used to store the data model, graph, and parameters for the IDA check.
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
     * The IDA check object.
     */
    private transient IdaCheck idaCheck;

    /**
     * Constructs a new IDA checker with the given data model, graph, and parameters.
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
     * Returns the underlying IdaCheck object.
     *
     * @return the underlying IdaCheck object.
     */
    public IdaCheck getIdaCheck() {
        if (this.idaCheck != null) {
            return this.idaCheck;
        }

        this.idaCheck = new IdaCheck(this.graph, (DataSet) this.dataModel);
        return this.idaCheck;
    }

    /**
     * Returns the graph associated with the current instance of IdaModel.
     *
     * @return the graph object representing the current instance of IdaModel.
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
     * Returns the name of the session model.
     *
     * @return the name of the session model
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the session model.
     *
     * @param name the name of the session model.
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
     * Returns the source graph associated with this model.
     *
     * @return the source graph object representing the current instance of IdaModel.
     */
    @Override
    public Graph getSourceGraph() {
        return null;
    }

    /**
     * Returns the result graph associated with the current instance of IdaModel.
     *
     * @return the result graph object representing the current instance of IdaModel.
     */
    @Override
    public Graph getResultGraph() {
        return null;
    }

    /**
     * Retrieves the variables to check.
     *
     * @return a List of Node objects representing the variables to check.
     */
    @Override
    public List<Node> getVariables() {
        return idaCheck.getNodes();
    }

    /**
     * Returns the names of the variables to check.
     *
     * @return a {@link List} of {@link String} representing the names of the variables to check.
     */
    @Override
    public List<String> getVariableNames() {
        return null;
    }
}



