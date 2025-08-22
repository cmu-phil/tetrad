/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ExtraLatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.LatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.cluster.ClusterAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.BlockIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.MSeparationTest;
import edu.cmu.tetrad.algcomparison.independence.TakesGraph;
import edu.cmu.tetrad.algcomparison.score.BlockScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.MSepScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.session.ParamsResettable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Stores an algorithms in the format of the algorithm comparison API.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralAlgorithmRunner implements AlgorithmRunner, ParamsResettable,
        Unmarshallable, IndTestProducer,
        KnowledgeBoxInput {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the model.
     */
    private final Map<String, Object> userAlgoSelections = new HashMap<>();

    /**
     * The data model.
     */
    private DataWrapper dataWrapper;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The wrapped algorithm.
     */
    private Algorithm algorithm;

    /**
     * The params object, so the GUI can remember stuff for logging.
     */
    private Parameters parameters;

    /**
     * The graph source.
     */
    private Graph sourceGraph;

    /**
     * The external graph.
     */
    private Graph externalGraph;

    /**
     * The graph list.
     */
    List<Graph> graphList = new ArrayList<>();

    /**
     * The knowledge.
     */
    private Knowledge knowledge;

    /**
     * The independence tests.
     */
    private transient List<IndependenceTest> independenceTests;

    BlockSpec blockSpec = null;

    /**
     * The elapsed time for the algorithm to run.
     */
    private long elapsedTime = -1L;

    //===========================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param runner     a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GeneralAlgorithmRunner(GeneralAlgorithmRunner runner, Parameters parameters) {
        this(runner.getDataWrapper(), runner, parameters, null, null);
        this.sourceGraph = runner.sourceGraph;
        this.knowledge = runner.knowledge;
        this.algorithm = runner.algorithm;
        this.parameters = parameters;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters) {
        this(dataWrapper, null, parameters, null, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        this(dataWrapper, null, parameters, knowledgeBoxModel, null);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters) {
        this(dataWrapper, graphSource, parameters, null, null);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource       a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource,
                                  KnowledgeBoxModel knowledgeBoxModel,
                                  Parameters parameters) {
        this(dataWrapper, graphSource, parameters, knowledgeBoxModel, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param facts             a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts) {
        this(dataWrapper, null, parameters, knowledgeBoxModel, facts);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param runner      a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters) {
        this(dataWrapper, null, parameters, null, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param runner            a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        this(dataWrapper, null, parameters, knowledgeBoxModel, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param runner      a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, GeneralAlgorithmRunner runner,
                                  Parameters parameters) {
        this(dataWrapper, graphSource, parameters, null, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource       a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param runner            a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, GeneralAlgorithmRunner runner,
                                  Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        this(dataWrapper, graphSource, parameters, knowledgeBoxModel, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * Constucts a wrapper for the given graph.
     *
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param runner      a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, GeneralAlgorithmRunner runner, Parameters parameters) {
        this(null, graphSource, parameters, null, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param graphSource       a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) {
        this(null, graphSource, parameters, knowledgeBoxModel, null);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param model             a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public GeneralAlgorithmRunner(IndependenceFactsModel model,
                                  Parameters parameters, KnowledgeBoxModel knowledgeBoxModel) {
        this(null, null, parameters, knowledgeBoxModel, model);
    }

    /**
     * Constucts a wrapper for the given graph.
     *
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, Parameters parameters) {
        this(null, graphSource, parameters, null, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource       a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param facts             a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
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
            this.knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            this.knowledge = new Knowledge();
        }

        if (facts != null) {
            getParameters().set("independenceFacts", facts.getFacts());
        }
    }

    //============================PUBLIC METHODS==========================//

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() {
        long start = System.currentTimeMillis();

        List<Graph> graphList = new ArrayList<>();

        if (this.independenceTests != null) {
            this.independenceTests.clear();
        }

        Algorithm algo = getAlgorithm();

        if (this.knowledge != null && !knowledge.isEmpty()) {
            if (algo instanceof HasKnowledge) {
                ((HasKnowledge) algo).setKnowledge(this.knowledge.copy());
            } else {
                throw new IllegalArgumentException("Knowledge has been supplied, but this algorithm does not use knowledge.");
            }
        }

        if (getDataModelList().isEmpty() && getSourceGraph() != null) {
            if (algo instanceof TakesScoreWrapper) {
                // We inject the graph to the score to satisfy the tests like MSeparationScore - Zhou
                ScoreWrapper scoreWrapper = ((TakesScoreWrapper) algo).getScoreWrapper();
                if (scoreWrapper instanceof MSepScore) {
                    ((MSepScore) scoreWrapper).setGraph(getSourceGraph());
                }
                if (scoreWrapper instanceof BlockScoreWrapper) {
                    ((BlockScoreWrapper) scoreWrapper).setBlockSpec(blockSpec);
                }
            }

            if (algo instanceof TakesIndependenceWrapper) {
                IndependenceWrapper wrapper = ((TakesIndependenceWrapper) algo).getIndependenceWrapper();
                if (wrapper instanceof MSeparationTest) {
                    ((MSeparationTest) wrapper).setGraph(getSourceGraph());
                }
                if (wrapper instanceof BlockIndependenceWrapper) {
                    ((BlockIndependenceWrapper) wrapper).setBlockSpec(blockSpec);
                }
            }

            if (algo instanceof TakesGraph) {
                ((TakesGraph) algo).setGraph(this.sourceGraph);
            }

            if (this.algorithm instanceof HasKnowledge) {
                Knowledge knowledge1 = TsUtils.getKnowledge(getSourceGraph());

                if (this.knowledge.isEmpty() && !knowledge1.isEmpty()) {
                    ((HasKnowledge) algo).setKnowledge(knowledge1);
                } else {
                    ((HasKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                }
            }


            Graph graph = null;
            try {
                graph = algo.search(null, this.parameters);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            LayoutUtil.defaultLayout(graph);

            graphList.add(graph);
        } else {
            if (getAlgorithm() instanceof MultiDataSetAlgorithm) {
                for (int k = 0; k < this.parameters.getInt("numRuns"); k++) {
                    Knowledge knowledge1 = getDataModelList().getFirst().getKnowledge();
                    List<DataModel> dataSets = new ArrayList<>(getDataModelList());
                    for (DataModel dataSet : dataSets) dataSet.setKnowledge(knowledge1);
                    int randomSelectionSize = this.parameters.getInt("randomSelectionSize");
                    if (randomSelectionSize == 0) {
                        randomSelectionSize = dataSets.size();
                    }
                    if (dataSets.size() < randomSelectionSize) {
                        throw new IllegalArgumentException("Sorry, the 'random selection size' is greater than "
                                                           + "the number of data sets: " + randomSelectionSize + " > " + dataSets.size());
                    }
                    RandomUtil.shuffle(dataSets);

                    List<DataModel> sub = new ArrayList<>();
                    for (int j = 0; j < randomSelectionSize; j++) {
                        sub.add(dataSets.get(j));
                    }

                    if (algo instanceof TakesGraph) {
                        ((TakesGraph) algo).setGraph(this.sourceGraph);
                    }

                    if (this.algorithm instanceof HasKnowledge) {
                        ((HasKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                    }

                    try {
                        graphList.add(((MultiDataSetAlgorithm) algo).search(sub, this.parameters));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else if (getAlgorithm() instanceof ClusterAlgorithm) {
                for (int k = 0; k < this.parameters.getInt("numRuns"); k++) {
                    getDataModelList().forEach(dataModel -> {
                        if (dataModel instanceof ICovarianceMatrix dataSet) {

                            if (algo instanceof TakesGraph) {
                                ((TakesGraph) algo).setGraph(this.sourceGraph);
                            }

                            if (this.algorithm instanceof HasKnowledge) {
                                ((HasKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                            }

                            Graph graph = null;
                            try {
                                graph = this.algorithm.search(dataSet, this.parameters);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }

                            LayoutUtil.defaultLayout(graph);

                            graphList.add(graph);
                        } else if (dataModel instanceof DataSet dataSet) {

                            if (!dataSet.isContinuous()) {
                                throw new IllegalArgumentException("Sorry, you need a continuous dataset for a cluster algorithm.");
                            }

                            if (algo instanceof TakesGraph) {
                                ((TakesGraph) algo).setGraph(this.sourceGraph);
                            }

                            if (this.algorithm instanceof HasKnowledge) {
                                ((HasKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                            }

                            Graph graph = null;
                            try {
                                graph = this.algorithm.search(dataSet, this.parameters);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            LayoutUtil.defaultLayout(graph);

                            graphList.add(graph);
                        }
                    });
                }
            } else {
                if (getDataModelList().size() != 1) {
                    throw new IllegalArgumentException("Expecting a single dataset here.");
                }

                if (algo != null) {
                    getDataModelList().forEach(data -> {
                        Knowledge knowledgeFromData = data.getKnowledge();
                        if (!(knowledgeFromData == null || knowledgeFromData.getVariables().isEmpty())) {
                            this.knowledge = knowledgeFromData;
                        }

                        if (algo instanceof TakesScoreWrapper) {
                            // We inject the graph to the score to satisfy the tests like MSeparationScore - Zhou
                            ScoreWrapper scoreWrapper = ((TakesScoreWrapper) algo).getScoreWrapper();

                            if (scoreWrapper instanceof BlockScoreWrapper) {
                                ((BlockScoreWrapper) scoreWrapper).setBlockSpec(blockSpec);
                            }
                        }

                        if (algo instanceof TakesIndependenceWrapper) {
                            IndependenceWrapper wrapper = ((TakesIndependenceWrapper) algo).getIndependenceWrapper();

                            if (wrapper instanceof BlockIndependenceWrapper) {
                                ((BlockIndependenceWrapper) wrapper).setBlockSpec(blockSpec);
                            }
                        }

                        DataType algDataType = algo.getDataType();

                        if (algo instanceof TakesGraph) {
                            ((TakesGraph) algo).setGraph(this.sourceGraph);
                        }

                        if (this.algorithm instanceof HasKnowledge) {
                            ((HasKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                        }

                        if (data instanceof ICovarianceMatrix && parameters.getInt(Params.NUMBER_RESAMPLING) > 0) {
                            throw new IllegalArgumentException("Sorry, you need a tabular dataset in order to do bootstrapping.");
                        }

                        if (data.isContinuous() && (algDataType == DataType.Continuous || algDataType == DataType.Mixed)) {
                            Graph graph = null;
                            try {
                                graph = algo.search(data, this.parameters);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            LayoutUtil.defaultLayout(graph);
                            graphList.add(graph);
                        } else if (data.isDiscrete() && (algDataType == DataType.Discrete || algDataType == DataType.Mixed)) {
                            Graph graph = null;
                            try {
                                graph = algo.search(data, this.parameters);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            LayoutUtil.defaultLayout(graph);
                            graphList.add(graph);
                        } else if (data.isMixed() && algDataType == DataType.Mixed) {
                            Graph graph = null;
                            try {
                                graph = algo.search(data, this.parameters);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            LayoutUtil.defaultLayout(graph);
                            graphList.add(graph);
                        } else {
                            throw new IllegalArgumentException("The algorithm was not expecting that type of data.");
                        }
                    });
                }
            }

            long stop = System.currentTimeMillis();

            this.elapsedTime = stop - start;
        }

        if (knowledge != null && knowledge.getNumTiers() > 0) {
            for (Graph graph : graphList) {
                GraphSearchUtils.arrangeByKnowledgeTiers(graph, knowledge);
            }
        } else {
            for (Graph graph : graphList) {
                LayoutUtil.defaultLayout(graph);
            }
        }

        this.graphList = graphList;
    }

    /**
     * <p>hasMissingValues.</p>
     *
     * @return a boolean
     */
    public boolean hasMissingValues() {
        DataModelList dataModelList = getDataModelList();
        if (dataModelList.containsEmptyData()) {
            return false;
        } else {
            if (dataModelList.getFirst() instanceof CovarianceMatrix) {
                return false;
            }

            DataSet dataSet = (DataSet) dataModelList.getFirst();

            return dataSet.existsMissingValue();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * By default, algorithm do not support knowledge. Those that do will speak up.
     */
    @Override
    public boolean supportsKnowledge() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MeekRules getMeekRules() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExternalGraph(Graph graph) {
        this.externalGraph = graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlgorithmName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Graph getSourceGraph() {
        return this.sourceGraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getResultGraph() {
        return getGraph();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final DataModel getDataModel() {
        if (this.dataWrapper != null) {
            DataModelList dataModelList = this.dataWrapper.getDataModelList();

            if (dataModelList.size() == 1) {
                return dataModelList.getFirst();
            } else {
                return dataModelList;
            }
        } else {

            // Do not throw an exception here!
            return new BoxDataSet(new VerticalDoubleDataBox(0, 0), new ArrayList<>());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Parameters getParams() {
        return null;
    }

    /**
     * <p>getDataModelList.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModelList} object
     */
    public final DataModelList getDataModelList() {
        if (this.dataWrapper == null) {
            return new DataModelList();
        }
        return this.dataWrapper.getDataModelList();
    }

    /**
     * <p>Getter for the field <code>parameters</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public final Parameters getParameters() {
        return this.parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getResettableParams() {
        return this.getParameters();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetParams(Object params) {
        this.parameters = (Parameters) params;
    }

    //===========================PRIVATE METHODS==========================//
    private void transferVarNamesToParams(List<String> names) {
        getParameters().set("varNames", names);
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getIndependenceTest() {
        if (this.independenceTests == null) {
            this.independenceTests = new ArrayList<>();
        }

        if (this.independenceTests.size() == 1) {
            return this.independenceTests.getFirst();
        }

        Algorithm algo = getAlgorithm();

        if (getDataModelList().isEmpty() && getSourceGraph() != null) {
            // We inject the graph to the test to satisfy the tests like MSeparationTest - Zhou
            IndependenceWrapper test = new MSeparationTest(getSourceGraph());

            if (this.independenceTests == null) {
                this.independenceTests = new ArrayList<>();
            }

            // Grabbing this independence test for the independence tests interface. JR 2020.8.24
//            IndependenceTest test = indTestWrapper.getTest(null, parameters);
            this.independenceTests.add(test.getTest(null, this.parameters));
        } else if (algo instanceof TakesIndependenceWrapper) {
            if (getDataModelList().size() == 1) {
                IndependenceWrapper indTestWrapper = ((TakesIndependenceWrapper) getAlgorithm()).getIndependenceWrapper();

                if (this.independenceTests == null) {
                    this.independenceTests = new ArrayList<>();
                }

                if (indTestWrapper instanceof BlockIndependenceWrapper) {
                    ((BlockIndependenceWrapper) indTestWrapper).setBlockSpec(blockSpec);
                }

                // Grabbing this independence test for the independence tests interface. JR 2020.8.24
                IndependenceTest test = indTestWrapper.getTest(getDataModelList().get(0), this.parameters);
                this.independenceTests.add(test);
            }
        } else if (algo instanceof TakesScoreWrapper) {
            if (getDataModelList().size() == 1) {
                ScoreWrapper wrapper = ((TakesScoreWrapper) getAlgorithm()).getScoreWrapper();

                if (this.independenceTests == null) {
                    this.independenceTests = new ArrayList<>();
                }

                if (wrapper instanceof BlockScoreWrapper) {
                    ((BlockScoreWrapper) wrapper).setBlockSpec(blockSpec);
                }

                // Grabbing this independence score for the independence tests interface. JR 2020.8.24
                Score score = wrapper.getScore(getDataModelList().get(0), this.parameters);
                this.independenceTests.add(new ScoreIndTest(score));
            }
        }

        if (this.independenceTests.isEmpty()) {
            throw new IllegalArgumentException("One or more of the parents was a search that didn't use "
                                               + "a test or a score.");
        }

        return this.independenceTests.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>algorithm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public Algorithm getAlgorithm() {
        if (this.algorithm instanceof ExtraLatentStructureAlgorithm) {
            ((ExtraLatentStructureAlgorithm) this.algorithm).setBlockSpec(blockSpec);
        }

        return this.algorithm;
    }

    /**
     * <p>Setter for the field <code>algorithm</code>.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public void setAlgorithm(Algorithm algorithm) {
        if (algorithm == null) {
            throw new NullPointerException("Algorithm not specified");
        }
        this.algorithm = algorithm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getTriplesClassificationTypes() {
        return Collections.EMPTY_LIST;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        return Collections.EMPTY_LIST;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getParamSettings() {
        return Collections.EMPTY_MAP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getAllParamSettings() {
        return Collections.EMPTY_MAP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getGraph() {
        if (this.graphList == null || this.graphList.isEmpty()) {
            return null;
        } else {
            return this.graphList.getFirst();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Graph> getGraphs() {
        return this.graphList;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Getter for the field <code>dataWrapper</code>.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public DataWrapper getDataWrapper() {
        return this.dataWrapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return Collections.EMPTY_LIST;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getVariableNames() {
        return Collections.EMPTY_LIST;
    }

    /**
     * <p>getCompareGraphs.</p>
     *
     * @param graphs a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public List<Graph> getCompareGraphs(List<Graph> graphs) {
        if (graphs == null) {
            throw new NullPointerException();
        }

        List<Graph> compareGraphs = new ArrayList<>();

        for (Graph graph : graphs) {
            compareGraphs.add(this.algorithm.getComparisonGraph(graph));
        }

        return compareGraphs;
    }

    /**
     * <p>Getter for the field <code>userAlgoSelections</code>.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, Object> getUserAlgoSelections() {
        return this.userAlgoSelections;
    }

    /**
     * Returns the elapsed time for the algorithm to run, in milliseconds.
     *
     * @return the elapsed time for the algorithm to run. If the algorithm does not (or has not) run, this is set to -1.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = blockSpec;
    }
}
