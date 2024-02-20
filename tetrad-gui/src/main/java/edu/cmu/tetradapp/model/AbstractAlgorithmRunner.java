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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Unmarshallable;
import edu.cmu.tetradapp.session.ParamsResettable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.*;

/**
 * Implements a stub that basic algorithm wrappers can extend if they take either a dataModel model or a workbench model
 * as parent. Contains basic methods for executing algorithm and returning results.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public abstract class AbstractAlgorithmRunner
        implements AlgorithmRunner, ParamsResettable, Unmarshallable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The parameters settings.
     */
    final Map<String, String> paramSettings = new LinkedHashMap<>();

    /**
     * The data wrapper.
     */
    private DataWrapper dataWrapper;

    /**
     * The name of the algorithm.
     */
    private String name;

    /**
     * The parameters.
     */
    private Parameters params;

    /**
     * The data model.
     */
    private transient DataModel dataModel;

    /**
     * The source graph.
     */
    private Graph sourceGraph;

    /**
     * The result graph.
     */
    private Graph resultGraph = new EdgeListGraph();

    /**
     * The external graph.
     */
    private Graph externalGraph;

    /**
     * The graphs.
     */
    private List<Graph> graphs;

    /**
     * The all param settings.
     */
    private Map<String, String> allParamSettings;

    //===========================CONSTRUCTORS===========================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       the data wrapper
     * @param params            the parameters
     * @param knowledgeBoxModel the knowledge box model
     */
    public AbstractAlgorithmRunner(DataWrapper dataWrapper,
                                   Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModelList dataSource = dataWrapper.getDataModelList();

        this.dataWrapper = dataWrapper;

        //temporary workaround to get the knowledge box to coexist with the dataWrapper's knowledge
        if (knowledgeBoxModel == null) {
            getParams().set("knowledge", dataWrapper.getKnowledge());
        } else {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }
        List<String> names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       the data wrapper
     * @param params            the parameters
     * @param knowledgeBoxModel the knowledge box model
     * @param facts             the independence facts model
     */
    public AbstractAlgorithmRunner(DataWrapper dataWrapper,
                                   Parameters params, KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel dataSource = getSelectedDataModel(dataWrapper);

        this.dataWrapper = dataWrapper;

        //temporary workaround to get the knowledge box to coexist with the dataWrapper's knowledge
        if (knowledgeBoxModel == null) {
            getParams().set("knowledge", dataWrapper.getKnowledge());
        } else {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }

        getParams().set("independenceFacts", facts.getFacts());
        List<String> names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper the data wrapper
     * @param params      the parameters
     */
    public AbstractAlgorithmRunner(DataWrapper dataWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel dataSource = getSelectedDataModel(dataWrapper);

        this.dataWrapper = dataWrapper;

        List<String> names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
    }

    /**
     * Constructs a wrapper for the given graph.
     *
     * @param sourceGraph the source graph
     * @param params      the parameters
     */
    public AbstractAlgorithmRunner(Graph sourceGraph, Parameters params) {
        if (sourceGraph == null) {
            throw new NullPointerException(
                    "Source graph must not be null.");
        }
        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }
        this.params = params;
        List<String> names = measuredNames(sourceGraph);
        transferVarNamesToParams(names);
        this.sourceGraph = sourceGraph;
    }

    /**
     * Constructs a wrapper for the given graph.
     *
     * @param graph             the graph
     * @param params            the parameters
     * @param knowledgeBoxModel the knowledge box model
     */
    public AbstractAlgorithmRunner(Graph graph, Parameters params,
                                   KnowledgeBoxModel knowledgeBoxModel) {
        this(graph, params);
        if (knowledgeBoxModel != null) {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }
    }

    /**
     * Constructs a wrapper for the given graph.
     *
     * @param params the parameters
     * @param graphs the graphs
     */
    public AbstractAlgorithmRunner(Parameters params, Graph... graphs) {
        this.graphs = Arrays.asList(graphs);
        this.params = params;
    }

    /**
     * Constructs a wrapper for the given graph.
     *
     * @param params            the parameters
     * @param knowledgeBoxModel the knowledge box model
     * @param graphs            the graphs
     */
    public AbstractAlgorithmRunner(Parameters params, KnowledgeBoxModel knowledgeBoxModel, Graph... graphs) {
        this.graphs = Arrays.asList(graphs);
        this.params = params;
        if (knowledgeBoxModel != null) {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }
    }

    /**
     * Constructs a wrapper for the given graph.
     *
     * @param model             the model
     * @param params            the parameters
     * @param knowledgeBoxModel the knowledge box model
     */
    public AbstractAlgorithmRunner(IndependenceFactsModel model,
                                   Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        if (model == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;

        DataModel dataSource = model.getFacts();

        if (knowledgeBoxModel != null) {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }

        List<String> names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = dataSource;
    }

    /**
     * Constructs a wrapper for the given graph.
     *
     * @param graph             the graph
     * @param params            the parameters
     * @param knowledgeBoxModel the knowledge box model
     * @param facts             the independence facts model
     */
    public AbstractAlgorithmRunner(Graph graph, Parameters params,
                                   KnowledgeBoxModel knowledgeBoxModel, IndependenceFacts facts) {
        this(graph, params);
        if (knowledgeBoxModel != null) {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }
        if (facts != null) {
            getParams().set("independenceFacts", facts);
        }
    }


    //============================PUBLIC METHODS==========================//

    /**
     * Returns the graph that was the result of the algorithm's execution.
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public final Graph getResultGraph() {
        return this.resultGraph;
    }

    /**
     * Sets the graph that was the result of the algorithm's execution.
     *
     * @param resultGraph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public final void setResultGraph(Graph resultGraph) {
        this.resultGraph = resultGraph;
    }

    /**
     * By default, algorithm do not support knowledge. Those that do will speak up.
     *
     * @return true if the algorithm supports knowledge.
     */
    public boolean supportsKnowledge() {
        return false;
    }

    /**
     * By default, algorithm do not support Meek rules. Those that do will speak up.
     *
     * @return null
     */
    public MeekRules getMeekRules() {
        return null;
    }

    /**
     * By default, algorithm do not support independence facts. Those that do will speak up.
     *
     * @return the external graph
     */
    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the external graph for the algorithm.
     */
    public void setExternalGraph(Graph graph) {
        this.externalGraph = graph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the algorithm's name.
     */
    @Override
    public abstract String getAlgorithmName();

    /**
     * Returns the source graph.
     *
     * @return the source graph
     */
    public final Graph getSourceGraph() {
        return this.sourceGraph;
    }

    /**
     * Returns the data model.
     *
     * @return the data model
     */
    public final DataModel getDataModel() {
        if (this.dataWrapper != null) {
            DataModelList dataModelList = this.dataWrapper.getDataModelList();

            if (dataModelList.size() == 1) {
                return dataModelList.get(0);
            } else {
                return dataModelList;
            }
        } else if (this.dataModel != null) {
            return this.dataModel;
        } else {

            // Do not throw an exception here!
            return null;
        }
    }

    /**
     * Returns the data model list.
     *
     * @return the data model list
     */
    final DataModelList getDataModelList() {
        if (this.dataWrapper == null) return null;
        return this.dataWrapper.getDataModelList();
    }

    /**
     * Returns the search parameters.
     *
     * @return the search parameters
     */
    public final Parameters getParams() {
        return this.params;
    }

    /**
     * Returns the pameters.
     *
     * @return the parameters
     */
    public Object getResettableParams() {
        return this.getParams();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Resets the parameters.
     */
    public void resetParams(Object params) {
        this.params = (Parameters) params;
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * Find the dataModel model. (If it's a list, take the one that's selected.)
     */
    private DataModel getSelectedDataModel(DataWrapper dataWrapper) {
        DataModelList dataModelList = dataWrapper.getDataModelList();

        if (dataModelList.size() > 1) {
            return dataModelList;
        }

        DataModel dataModel = dataWrapper.getSelectedDataModel();

        if (dataModel instanceof DataSet dataSet) {
            if (dataSet.isDiscrete()) {
                return dataSet;
            } else if (dataSet.isContinuous()) {
                return dataSet;
            } else if (dataSet.isMixed()) {
                return dataSet;
            }

            throw new IllegalArgumentException("<html>" +
                    "This data set contains a mixture of discrete and continuous " +
                    "<br>columns; there are no algorithm in Tetrad currently to " +
                    "<br>search over such data sets." + "</html>");
        } else if (dataModel instanceof ICovarianceMatrix) {
            return dataModel;
        } else if (dataModel instanceof TimeSeriesData) {
            return dataModel;
        }

        throw new IllegalArgumentException(
                "Unexpected dataModel source: " + dataModel);
    }

    private List<String> measuredNames(Graph graph) {
        List<String> names = new ArrayList<>();
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                names.add(node.getName());
            }
        }
        return names;
    }

    private void transferVarNamesToParams(List<String> names) {
        getParams().set("varNames", names);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

    }

    /**
     * Returns the name of the algorithm.
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the algorithm.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the list of graphs.
     *
     * @return the graphs
     */
    public List<Graph> getGraphs() {
        return this.graphs;
    }


    /**
     * {@inheritDoc}
     * <p>
     * Returns the param settings.
     */
    @Override
    public Map<String, String> getParamSettings() {
        this.paramSettings.put("Algorithm", getAlgorithmName());
        return this.paramSettings;
    }

    /**
     * Returns all param settings.
     *
     * @return all param settings
     */
    public Map<String, String> getAllParamSettings() {
        return this.allParamSettings;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets all param settings.
     */
    public void setAllParamSettings(Map<String, String> allParamSettings) {
        this.allParamSettings = allParamSettings;
    }
}





