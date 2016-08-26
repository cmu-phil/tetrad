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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fgs;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ImpliedOrientation;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.session.ParamsResettable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Unmarshallable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores an algorithms in the format of the algorithm comparison API.
 *
 * @author jdramsey
 */
public class GeneralAlgorithmRunner implements AlgorithmRunner, ParamsResettable,
        MultipleGraphSource, Unmarshallable {

    static final long serialVersionUID = 23L;

    private DataWrapper dataWrapper;
    private String name;
    private Algorithm algorithm = new Fgs(new BdeuScore());
    private Parameters parameters;
    private DataModel dataModel;
    private Graph sourceGraph;
    private Graph resultGraph = new EdgeListGraph();
    private Graph initialGraph;
    private List<Graph> graphList = new ArrayList<>();
    private IKnowledge knowledge = new Knowledge2();

    //===========================CONSTRUCTORS===========================//

    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel dataSource = getSelectedDataModel(dataWrapper);

        this.dataWrapper = dataWrapper;

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        this.knowledge = dataWrapper.getKnowledge();

        if (knowledge == null) {
            this.knowledge = new Knowledge2(dataWrapper.getVariableNames());
        }
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;

        DataModelList dataSource = dataWrapper.getDataModelList();

        this.dataWrapper = dataWrapper;

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }
    }

    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }
        if (graphSource == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel dataSource = getSelectedDataModel(dataWrapper);

        this.dataWrapper = dataWrapper;
        this.sourceGraph = graphSource.getGraph();

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        this.knowledge = dataWrapper.getKnowledge();

        if (knowledge == null) {
            this.knowledge = new Knowledge2(dataWrapper.getVariableNames());
        }
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }
        if (graphSource == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;

        DataModelList dataSource = dataWrapper.getDataModelList();

        this.dataWrapper = dataWrapper;
        this.sourceGraph = graphSource.getGraph();

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel dataSource = getSelectedDataModel(dataWrapper);

        this.dataWrapper = dataWrapper;

        getParameters().set("independenceFacts", facts.getFacts());
        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }
    }


    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel dataSource = getSelectedDataModel(dataWrapper);

        this.dataWrapper = dataWrapper;

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        this.knowledge = dataWrapper.getKnowledge();

        if (knowledge == null) {
            this.knowledge = new Knowledge2(dataWrapper.getVariableNames());
        }

        this.name = runner.name;
        this.algorithm = runner.algorithm;
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;

        DataModelList dataSource = dataWrapper.getDataModelList();

        this.dataWrapper = dataWrapper;

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }

        this.name = runner.name;
        this.algorithm = runner.algorithm;
    }

    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, GeneralAlgorithmRunner runner,
                                  Parameters parameters) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }
        if (graphSource == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel dataSource = getSelectedDataModel(dataWrapper);

        this.dataWrapper = dataWrapper;
        this.sourceGraph = graphSource.getGraph();

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        this.knowledge = dataWrapper.getKnowledge();

        if (knowledge == null) {
            this.knowledge = new Knowledge2(dataWrapper.getVariableNames());
        }

        this.name = runner.name;
        this.algorithm = runner.algorithm;
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, GeneralAlgorithmRunner runner,
                                  Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }
        if (graphSource == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;

        DataModelList dataSource = dataWrapper.getDataModelList();

        this.dataWrapper = dataWrapper;
        this.sourceGraph = graphSource.getGraph();

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }

        this.name = runner.name;
        this.algorithm = runner.algorithm;
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;
        this.sourceGraph = dataWrapper.getSourceGraph();

        DataModel dataSource = getSelectedDataModel(dataWrapper);

        this.dataWrapper = dataWrapper;

        getParameters().set("independenceFacts", facts.getFacts());
        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }

        this.name = runner.name;
        this.algorithm = runner.algorithm;
    }


    /**
     * Constucts a wrapper for the given graph.
     */
    public GeneralAlgorithmRunner(Graph sourceGraph, GeneralAlgorithmRunner runner, Parameters parameters) {
        if (sourceGraph == null) {
            throw new NullPointerException(
                    "Source graph must not be null.");
        }
        if (parameters == null) {
            throw new NullPointerException("Parameters must not be null.");
        }
        this.parameters = parameters;
        List<String> names = measuredNames(sourceGraph);
        transferVarNamesToParams(names);
        this.sourceGraph = sourceGraph;
        knowledge = new Knowledge2(sourceGraph.getNodeNames());

        this.name = runner.name;
        this.algorithm = runner.algorithm;
    }


    public GeneralAlgorithmRunner(GraphSource graph, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        this(graph, parameters);

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }
    }

    public GeneralAlgorithmRunner(IndependenceFactsModel model,
                                  Parameters parameters, KnowledgeBoxModel knowledgeBoxModel) {
        if (model == null) {
            throw new NullPointerException();
        }
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;

        DataModel dataSource = model.getFacts();

        List names = dataSource.getVariableNames();
        transferVarNamesToParams(names);
        this.dataModel = dataSource;

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }
    }

    /**
     * Constucts a wrapper for the given graph.
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, Parameters parameters) {
        if (graphSource == null) {
            throw new NullPointerException(
                    "Source graph must not be null.");
        }
        if (parameters == null) {
            throw new NullPointerException("Parameters must not be null.");
        }
        this.parameters = parameters;
        this.sourceGraph = graphSource.getGraph();
        List<String> names = measuredNames(sourceGraph);
        transferVarNamesToParams(names);
        knowledge = new Knowledge2(sourceGraph.getNodeNames());
    }

    public GeneralAlgorithmRunner(GraphSource graph, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel, IndependenceFacts facts) {
        this(graph, parameters);
        if (facts != null) {
            getParameters().set("independenceFacts", facts);
        }

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }
    }


    //============================PUBLIC METHODS==========================//

    @Override
    public final Graph getResultGraph() {
        return this.resultGraph;
    }

    @Override
    public void execute() {
        List<Graph> graphList = new ArrayList<>();
        int i = 0;

        if (getDataModelList() != null) {
            for (DataModel data : getDataModelList()) {
                System.out.println("Analyzing data set # " + (++i));
                DataSet dataSet = (DataSet) data;
                Algorithm algorithm = getAlgorithm();

                if (algorithm instanceof HasKnowledge) {
                    ((HasKnowledge) algorithm).setKnowledge(getKnowledge());
                }

                DataType algDataType = algorithm.getDataType();

                if (dataSet.isContinuous() && (algDataType == DataType.Continuous || algDataType == DataType.Mixed)) {
                    graphList.add(algorithm.search(dataSet, parameters));
                } else if (dataSet.isDiscrete() && (algDataType == DataType.Discrete || algDataType == DataType.Mixed) && dataSet.isDiscrete()) {
                    graphList.add(algorithm.search(dataSet, parameters));
                } else if (((DataSet) data).isMixed() && algDataType == DataType.Mixed) {
                    graphList.add(algorithm.search(dataSet, parameters));
                } else {
                    throw new IllegalArgumentException("The stored algorithm configuration is not compatible with " +
                            "this type of data.");
                }
            }
        } else {
            Algorithm algorithm = getAlgorithm();

            if (algorithm instanceof HasKnowledge) {
                ((HasKnowledge) algorithm).setKnowledge(getKnowledge());
            }

            graphList.add(algorithm.search(null, parameters));
        }

        if (getKnowledge().getVariablesNotInTiers().size()
                < getKnowledge().getVariables().size()) {
            for (Graph graph : graphList) {
                SearchGraphUtils.arrangeByKnowledgeTiers(graph, getKnowledge());
            }
        } else {
            for (Graph graph : graphList) {
                GraphUtils.circleLayout(graph, 225, 200, 150);
            }
        }

        this.graphList = graphList;
    }

    /**
     * By default, algorithm do not support knowledge. Those that do will
     * speak up.
     */
    @Override
    public boolean supportsKnowledge() {
        return false;
    }

    @Override
    public ImpliedOrientation getMeekRules() {
        return null;
    }

    @Override
    public void setInitialGraph(Graph graph) {
        this.initialGraph = graph;
    }

    @Override
    public Graph getInitialGraph() {
        return this.initialGraph;
    }

    @Override
    public String getAlgorithmName() {
        return null;
    }

    @Override
    public final Graph getSourceGraph() {
        return this.sourceGraph;
    }

    @Override
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
            return new ColtDataSet(0, new ArrayList<Node>());
        }
    }

    @Override
    public Parameters getParams() {
        return null;
    }

    public final DataModelList getDataModelList() {
        if (dataWrapper == null) return new DataModelList();
        return dataWrapper.getDataModelList();
    }

    public final void setResultGraph(Graph resultGraph) {
        this.resultGraph = resultGraph;
    }

    public final Parameters getParameters() {
        return this.parameters;
    }

    @Override
    public Object getResettableParams() {
        return this.getParameters();
    }

    @Override
    public void resetParams(Object params) {
        this.parameters = (Parameters) params;
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
        getParameters().set("varNames", names);
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
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        if (algorithm == null) return;
        this.algorithm = algorithm;
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return null;
    }

    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        return null;
    }

    @Override
    public Map<String, String> getParamSettings() {
        return null;
    }

    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {

    }

    @Override
    public Map<String, String> getAllParamSettings() {
        return null;
    }

    @Override
    public Graph getGraph() {
        if (graphList == null || graphList.isEmpty()) {
            return null;
        } else {
            return graphList.get(0);
        }
    }

    @Override
    public List<Graph> getGraphs() {
        return graphList;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setGraphList(List<Graph> graphList) {
        this.graphList = graphList;
    }
}





