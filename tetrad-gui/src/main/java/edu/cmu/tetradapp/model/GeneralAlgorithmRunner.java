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
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.cluster.ClusterAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.ImpliedOrientation;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.session.ParamsResettable;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Unmarshallable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores an algorithms in the format of the algorithm comparison API.
 *
 * @author jdramsey
 */
public class GeneralAlgorithmRunner implements AlgorithmRunner, ParamsResettable,
        MultipleGraphSource, Unmarshallable, SessionModel, IndTestProducer,
        KnowledgeBoxInput {

    static final long serialVersionUID = 23L;

    private DataWrapper dataWrapper;
    private String name;
    private Algorithm algorithm = new Fges(new BdeuScore(), false);
    private Parameters parameters;
    private Graph sourceGraph;
    private Graph initialGraph;
    private List<Graph> graphList = new ArrayList<>();
    private IKnowledge knowledge = new Knowledge2();
    private final Map<String, Object> models = new HashMap<>();
    private transient List<IndependenceTest> independenceTests = null;

    //===========================CONSTRUCTORS===========================//
    public GeneralAlgorithmRunner(GeneralAlgorithmRunner runner, Parameters parameters) {
        this(runner.getDataWrapper(), runner, parameters, null, null);
        this.sourceGraph = runner.sourceGraph;
        this.knowledge = runner.knowledge;
        this.algorithm = runner.algorithm;
        this.parameters = parameters;
    }

    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters) {
        this(dataWrapper, null, parameters, null, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters,
            KnowledgeBoxModel knowledgeBoxModel) {
        this(dataWrapper, null, parameters, knowledgeBoxModel, null);
    }

    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters) {
        this(dataWrapper, graphSource, parameters, null, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters,
            KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts) {
        this(dataWrapper, null, parameters, knowledgeBoxModel, facts);
    }

    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters) {
        this(dataWrapper, null, parameters, null, null);
        this.algorithm = runner.algorithm;
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters,
            KnowledgeBoxModel knowledgeBoxModel) {
        this(dataWrapper, null, parameters, knowledgeBoxModel, null);
        this.algorithm = runner.algorithm;
    }

    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, GeneralAlgorithmRunner runner,
            Parameters parameters) {
        this(dataWrapper, graphSource, parameters, null, null);
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
        this(dataWrapper, graphSource, parameters, knowledgeBoxModel, null);
        this.algorithm = runner.algorithm;
    }

    /**
     * Constucts a wrapper for the given graph.
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, GeneralAlgorithmRunner runner, Parameters parameters) {
        this(null, graphSource, parameters, null, null);
        this.algorithm = runner.algorithm;
    }

    public GeneralAlgorithmRunner(GraphSource graphSource, Parameters parameters,
            KnowledgeBoxModel knowledgeBoxModel) {
        this(null, graphSource, parameters, knowledgeBoxModel, null);
    }

    public GeneralAlgorithmRunner(IndependenceFactsModel model,
            Parameters parameters, KnowledgeBoxModel knowledgeBoxModel) {
        this(null, null, parameters, knowledgeBoxModel, model);
    }

    /**
     * Constucts a wrapper for the given graph.
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, Parameters parameters) {
        this(null, graphSource, parameters, null, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters,
            KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts) {
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;

        if (graphSource instanceof GeneralAlgorithmRunner) {
            this.algorithm = ((GeneralAlgorithmRunner) graphSource).getAlgorithm();
        }

        if (dataWrapper != null) {
            this.dataWrapper = dataWrapper;

            if (dataWrapper.getDataModelList().isEmpty() && dataWrapper instanceof Simulation) {
                ((Simulation) dataWrapper).createSimulation();
            }
        }

        if (graphSource != null) {
            if (dataWrapper == null && graphSource instanceof DataWrapper) {
                this.dataWrapper = (DataWrapper) graphSource;
            } else {
                this.sourceGraph = graphSource.getGraph();
            }
        }

        if (dataWrapper != null) {
            List<String> names = this.dataWrapper.getVariableNames();
            transferVarNamesToParams(names);
        }

        if (knowledgeBoxModel != null) {
            knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            knowledge = new Knowledge2();
        }

        if (facts != null) {
            getParameters().set("independenceFacts", facts.getFacts());
        }
    }

    //============================PUBLIC METHODS==========================//
    @Override
    public void execute() {
        List<Graph> graphList = new ArrayList<>();
        int i = 0;

        if (getDataModelList().isEmpty()) {
            if (getSourceGraph() != null) {
                Algorithm algo = getAlgorithm();

                if (algo instanceof HasKnowledge) {
                    ((HasKnowledge) algo).setKnowledge(getKnowledge());
                }

                graphList.add(algo.search(null, parameters));
            } else {
                throw new IllegalArgumentException("The parent boxes did not include any datasets or graphs. Try opening\n"
                        + "the editors for those boxes and loading or simulating them.");
            }
        } else {
            if (getAlgorithm() instanceof MultiDataSetAlgorithm) {
                for (int k = 0; k < parameters.getInt("numRuns"); k++) {
                    List<DataSet> dataSets = getDataModelList().stream()
                            .map(e -> (DataSet) e)
                            .collect(Collectors.toCollection(ArrayList::new));
                    if (dataSets.size() < parameters.getInt("randomSelectionSize")) {
                        throw new IllegalArgumentException("Sorry, the 'random selection size' is greater than "
                                + "the number of data sets.");
                    }
                    Collections.shuffle(dataSets);

                    List<DataModel> sub = new ArrayList<>();
                    for (int j = 0; j < parameters.getInt("randomSelectionSize"); j++) {
                        sub.add(dataSets.get(j));
                    }

                    Algorithm algo = getAlgorithm();
                    if (algo instanceof HasKnowledge) {
                        ((HasKnowledge) algo).setKnowledge(getKnowledge());
                    }
                    graphList.add(((MultiDataSetAlgorithm) algo).search(sub, parameters));
                }
            } else if (getAlgorithm() instanceof ClusterAlgorithm) {
                for (int k = 0; k < parameters.getInt("numRuns"); k++) {
                    getDataModelList().forEach(dataModel -> {
                        if (dataModel instanceof ICovarianceMatrix) {
                            ICovarianceMatrix dataSet = (ICovarianceMatrix) dataModel;
                            graphList.add(algorithm.search(dataSet, parameters));
                        } else if (dataModel instanceof DataSet) {
                            DataSet dataSet = (DataSet) dataModel;

                            if (!dataSet.isContinuous()) {
                                throw new IllegalArgumentException("Sorry, you need a continuous dataset for a cluster algorithm.");
                            }

                            graphList.add(algorithm.search(dataSet, parameters));
                        }
                    });
                }
            } else {
                getDataModelList().forEach(data -> {
                    IKnowledge knowledgeFromData = data.getKnowledge();
                    if (!(knowledgeFromData == null || knowledgeFromData.getVariables().isEmpty())) {
                        this.knowledge = knowledgeFromData;
                    }

                    Algorithm algo = getAlgorithm();
                    if (algo instanceof HasKnowledge) {
                        ((HasKnowledge) algo).setKnowledge(getKnowledge());
                    }

                    DataType algDataType = algo.getDataType();

                    if (data.isContinuous() && (algDataType == DataType.Continuous || algDataType == DataType.Mixed)) {
                        graphList.add(algo.search(data, parameters));
                    } else if (data.isDiscrete() && (algDataType == DataType.Discrete || algDataType == DataType.Mixed)) {
                        graphList.add(algo.search(data, parameters));
                    } else if (data.isMixed() && algDataType == DataType.Mixed) {
                        graphList.add(algo.search(data, parameters));
                    } else {
                        throw new IllegalArgumentException("The type of data changed; try opening up the search editor and "
                                + "running the algorithm there.");
                    }
                });
            }
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
     * By default, algorithm do not support knowledge. Those that do will speak
     * up.
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
    public Graph getResultGraph() {
        return getGraph();
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
        if (dataWrapper == null) {
            return new DataModelList();
        }
        return dataWrapper.getDataModelList();
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
    public IndependenceTest getIndependenceTest() {
        return independenceTests.get(0);
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
        if (algorithm == null) {
            return;
        }
        this.algorithm = algorithm;
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Map<String, String> getParamSettings() {
        return Collections.EMPTY_MAP;
    }

    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {

    }

    @Override
    public Map<String, String> getAllParamSettings() {
        return Collections.EMPTY_MAP;
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

    public DataWrapper getDataWrapper() {
        return dataWrapper;
    }

    public void setIndependenceTests(List<IndependenceTest> independenceTests) {
        this.independenceTests = independenceTests;
    }

    @Override
    public List<Node> getVariables() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<String> getVariableNames() {
        return Collections.EMPTY_LIST;
    }

    public List<Graph> getCompareGraphs(List<Graph> graphs) {
        if (graphs == null) {
            throw new NullPointerException();
        }

        List<Graph> compareGraphs = new ArrayList<>();

        for (Graph graph : graphs) {
            compareGraphs.add(algorithm.getComparisonGraph(graph));
        }

        return compareGraphs;
    }

    public Map<String, Object> getModels() {
        return models;
    }

}
