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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.ImpliedOrientation;
import edu.cmu.tetrad.session.ParamsResettable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Unmarshallable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Implements a stub that basic algorithm wrappers can extend if they take
 * either a dataModel model or a workbench model as parent. Contains basic
 * methods for executing algorithm and returning results.
 *
 * @author Joseph Ramsey
 */
public abstract class AbstractAlgorithmRunner
        implements AlgorithmRunner, ParamsResettable, Unmarshallable, MultipleGraphSource {
    static final long serialVersionUID = 23L;
    private DataWrapper dataWrapper;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * The parameters guiding this search (when executed).
     *
     * @serial Cannot be null.
     */
    private Parameters params;

    /**
     * Keeps a reference to the dataModel source that has been provided
     * (hopefully either a dataModel model or a graph).
     *
     * @serial Can be null.
     */
    private transient DataModel dataModel;

    /**
     * Keeps a reference to the source graph, if there is one.
     *
     * @serial Can be null.
     */
    private Graph sourceGraph;

    /**
     * Keeps a reference to the result graph for the algorithm.
     *
     * @serial Can be null.
     */
    private Graph resultGraph = new EdgeListGraph();

    /**
     * The initial graph for the algorithm, if feasible.
     */
    private Graph initialGraph;

    /**
     * A series of graphs that the search algorithm might search over, if
     * it's that kind of algorithm.
     */
    private List<Graph> graphs = null;
    private Map<String, String> allParamSettings;
    final Map<String, String> paramSettings = new LinkedHashMap<>();

    //===========================CONSTRUCTORS===========================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     *
     * @param knowledgeBoxModel
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

//        if (dataSource instanceof ColtDataSet) {
//            dataSource = new ColtDataSet((ColtDataSet) dataSource);
//        }

        this.dataWrapper = dataWrapper;

        //temporary workaround to get the knowledge box to coexist with the dataWrapper's knowledge
        if (knowledgeBoxModel == null) {
            getParams().set("knowledge", dataWrapper.getKnowledge());
        } else {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }
        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     *
     * @param knowledgeBoxModel
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
        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
    }

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

//        if (dataSource instanceof ColtDataSet) {
//            dataSource = new ColtDataSet((ColtDataSet) dataSource);
//        }

        this.dataWrapper = dataWrapper;

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
    }

    /**
     * Constucts a wrapper for the given graph.
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

    public AbstractAlgorithmRunner(Graph graph, Parameters params,
                                   KnowledgeBoxModel knowledgeBoxModel) {
        this(graph, params);
        if (knowledgeBoxModel != null) {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }
    }

    public AbstractAlgorithmRunner(Parameters params, Graph... graphs) {
        this.graphs = Arrays.asList(graphs);
        this.params = params;
    }

    public AbstractAlgorithmRunner(Parameters params, KnowledgeBoxModel knowledgeBoxModel, Graph... graphs) {
        this.graphs = Arrays.asList(graphs);
        this.params = params;
        if (knowledgeBoxModel != null) {
            getParams().set("knowledge", knowledgeBoxModel.getKnowledge());
        }
    }

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

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = dataSource;
    }

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

    public final Graph getResultGraph() {
        return this.resultGraph;
    }

    /**
     * By default, algorithm do not support knowledge. Those that do will
     * speak up.
     */
    public boolean supportsKnowledge() {
        return false;
    }

    public ImpliedOrientation getMeekRules() {
        return null;
    }

    public void setInitialGraph(Graph graph) {
        this.initialGraph = graph;
    }

    public Graph getInitialGraph() {
        return this.initialGraph;
    }

    @Override
    public abstract String getAlgorithmName();

    public final Graph getSourceGraph() {
        return this.sourceGraph;
    }

    public final DataModel getDataModel() {
        if (dataWrapper != null) {
            DataModelList dataModelList = dataWrapper.getDataModelList();

            if (dataModelList.size() == 1) {
                return dataModelList.get(0);
            } else {
                return dataModelList;
            }
        } else if (dataModel != null) {
            return dataModel;
        } else {

            // Do not throw an exception here!
            return null;
        }
    }

    final DataModelList getDataModelList() {
        if (dataWrapper == null) return null;
        return dataWrapper.getDataModelList();
    }

    public final void setResultGraph(Graph resultGraph) {
        this.resultGraph = resultGraph;
    }

    public final Parameters getParams() {
        return this.params;
    }

    public Object getResettableParams() {
        return this.getParams();
    }

    public void resetParams(Object params) {
        this.params = (Parameters) params;
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * Find the dataModel model. (If it's a list, take the one that's
     * selected.)
     */
    private DataModel getSelectedDataModel(DataWrapper dataWrapper) {
        DataModelList dataModelList = dataWrapper.getDataModelList();

        if (dataModelList.size() > 1) {
            return dataModelList;
        }

        DataModel dataModel = dataWrapper.getSelectedDataModel();

        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;

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

    private void transferVarNamesToParams(List names) {
        getParams().set("varNames", names);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

//        if (getParams() == null) {
//            throw new NullPointerException();
//        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Graph> getGraphs() {
        return graphs;
    }


    @Override
    public Map<String, String> getParamSettings() {
        paramSettings.put("Algorithm", getAlgorithmName());
        return paramSettings;
    }

    public Map<String, String> getAllParamSettings() {
        return allParamSettings;
    }

    public void setAllParamSettings(Map<String, String> allParamSettings) {
        this.allParamSettings = allParamSettings;
    }
}





