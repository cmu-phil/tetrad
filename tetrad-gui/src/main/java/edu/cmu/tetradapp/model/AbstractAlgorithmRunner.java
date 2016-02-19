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
import edu.cmu.tetrad.util.Unmarshallable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements a stub that basic algorithm wrappers can extend if they take
 * either a dataModel model or a workbench model as parent. Contains basic
 * methods for executing algorithms and returning results.
 *
 * @author Joseph Ramsey
 */
public abstract class AbstractAlgorithmRunner
        implements AlgorithmRunner, ParamsResettable, Unmarshallable {
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
    private SearchParams params;

    /**
     * Keeps a reference to the dataModel source that has been provided
     * (hopefully either a dataModel model or a graph).
     *
     * @serial Can be null.
     */
    private transient DataModel dataModel;

    /**
     * Retains a reference to the data model list.
     *
     * @deprecated
     */
    private transient DataModelList dataModelList;

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

    //===========================CONSTRUCTORS===========================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     *
     * @param knowledgeBoxModel
     */
    public AbstractAlgorithmRunner(DataWrapper dataWrapper,
                                   SearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
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

        //temporary workaround to get the knowledge box to coexist with the dataWrapper's knowledge
        if (knowledgeBoxModel == null) {
            getParams().setKnowledge(dataWrapper.getKnowledge());
        } else {
            getParams().setKnowledge(knowledgeBoxModel.getKnowledge());
        }
        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
        new IndTestChooser().adjustIndTestParams(dataSource, params);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     *
     * @param knowledgeBoxModel
     */
    public AbstractAlgorithmRunner(DataWrapper dataWrapper,
                                   SearchParams params, KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts) {
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
            getParams().setKnowledge(dataWrapper.getKnowledge());
        } else {
            getParams().setKnowledge(knowledgeBoxModel.getKnowledge());
        }
        getParams().setIndependenceFacts(facts.getFacts());
        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
        new IndTestChooser().adjustIndTestParams(dataSource, params);
    }

    public AbstractAlgorithmRunner(DataWrapper dataWrapper, SearchParams params) {
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
        new IndTestChooser().adjustIndTestParams(dataSource, params);
    }

    /**
     * Constucts a wrapper for the given graph.
     */
    public AbstractAlgorithmRunner(Graph sourceGraph, SearchParams params) {
        if (sourceGraph == null) {
            throw new NullPointerException(
                    "Source graph must not be null.");
        }
        if (params == null) {
            throw new NullPointerException("Params must not be null.");
        }
        this.params = params;
        List<String> names = measuredNames(sourceGraph);
        transferVarNamesToParams(names);
        new IndTestChooser().adjustIndTestParams(sourceGraph, params);
        this.sourceGraph = sourceGraph;
    }

    public AbstractAlgorithmRunner(Graph graph, SearchParams params,
                                   KnowledgeBoxModel knowledgeBoxModel) {
        this(graph, params);
        if (knowledgeBoxModel != null) {
            getParams().setKnowledge(knowledgeBoxModel.getKnowledge());
        }
    }

    public AbstractAlgorithmRunner(SearchParams params, Graph... graphs) {
        this.graphs = Arrays.asList(graphs);
        this.params = params;
    }

    public AbstractAlgorithmRunner(SearchParams params, KnowledgeBoxModel knowledgeBoxModel, Graph... graphs) {
        this.graphs = Arrays.asList(graphs);
        this.params = params;
        if (knowledgeBoxModel != null) {
            getParams().setKnowledge(knowledgeBoxModel.getKnowledge());
        }
    }

    public AbstractAlgorithmRunner(IndependenceFactsModel model,
                                   SearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        if (model == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;

        DataModel dataSource = model.getFacts();

        if (knowledgeBoxModel != null) {
            getParams().setKnowledge(knowledgeBoxModel.getKnowledge());
        }

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
        new IndTestChooser().adjustIndTestParams(dataSource, params);
        this.dataModel = dataSource;
    }

    public AbstractAlgorithmRunner(Graph graph, SearchParams params,
                                   KnowledgeBoxModel knowledgeBoxModel, IndependenceFacts facts) {
        this(graph, params);
        if (knowledgeBoxModel != null) {
            getParams().setKnowledge(knowledgeBoxModel.getKnowledge());
        }
        if (facts != null) {
            getParams().setIndependenceFacts(facts);
        }
    }


    //============================PUBLIC METHODS==========================//

    public final Graph getResultGraph() {
        return this.resultGraph;
    }

    /**
     * By default, algorithms do not support knowledge. Those that do will
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

    public final DataModelList getDataModelList() {
        if (dataWrapper == null) return null;
        return dataWrapper.getDataModelList();
    }

    public final void setResultGraph(Graph resultGraph) {
        this.resultGraph = resultGraph;
    }

    public final SearchParams getParams() {
        return this.params;
    }

    public Object getResettableParams() {
        return this.getParams();
    }

    public void resetParams(Object params) {
        this.params = (SearchParams) params;
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * Find the dataModel model. (If it's a list, take the one that's
     * selected.)
     */
    public DataModel getSelectedDataModel(DataWrapper dataWrapper) {
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
                    "<br>columns; there are no algorithms in Tetrad currently to " +
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
        List<String> names = new ArrayList<String>();
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                names.add(node.getName());
            }
        }
        return names;
    }

    private void transferVarNamesToParams(List names) {
        getParams().setVarNames(names);
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
}





