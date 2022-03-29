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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the FCI algorithm.
 *
 * @author Joseph Ramsey
 * @author Daniel Malinsky
 */
public class SvarGFciRunner extends AbstractAlgorithmRunner
        implements IndTestProducer {
    static final long serialVersionUID = 23L;

    private transient List<PropertyChangeListener> listeners;
    //    private List<ScoredGraph> topGraphs;
//    private int index;
    private transient SvarGFci gfci;
//    private transient Graph externalGraph;


    //=========================CONSTRUCTORS================================//

//    public GFciRunner(DataWrapper dataWrapper, Parameters params) {
//        super(dataWrapper, params, null);
//    }
//
//    /**
//     * Constucts a wrapper for the given EdgeListGraph.
//     */
//    public GFciRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
//        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
//    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public SvarGFciRunner(final DataWrapper dataWrapper, final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public SvarGFciRunner(final Graph graph, final Parameters params) {
        super(graph, params);
    }


    public SvarGFciRunner(final GraphWrapper graphWrapper, final Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    public SvarGFciRunner(final DagWrapper dagWrapper, final Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    public SvarGFciRunner(final SemGraphWrapper dagWrapper, final Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    public SvarGFciRunner(final IndependenceFactsModel model, final Parameters params) {
        super(model, params, null);
    }

    public SvarGFciRunner(final IndependenceFactsModel model, final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    public SvarGFciRunner(final DataWrapper[] dataWrappers, final Parameters params) {
        super(new MergeDatasetsWrapper(dataWrappers, params), params, null);
    }

    public SvarGFciRunner(final GraphWrapper graphWrapper, final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SvarGFciRunner serializableInstance() {
        return new SvarGFciRunner(Dag.serializableInstance(), new Parameters());
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
//    public void execute() {
//        IKnowledge knowledge = getParameters().getKnowledge();
//        Parameters searchParams = getParameters();
//
//        Parameters params = (Parameters) searchParams;
//
//        Graph graph;
//
//        if (getIndependenceTest() instanceof IndTestDSep) {
//            GFci gfci = new GFci(getIndependenceTest());
//            graph = gfci.search();
//        } else {
//            GFci fci = new GFci(getIndependenceTest());
//            fci.setKnowledge(knowledge);
//            fci.setCompleteRuleSetUsed(params.isCompleteRuleSetUsed());
//            fci.setMaxPathLength(params.getMaxReachablePathLength());
//            fci.setMaxIndegree(params.getMaxIndegree());
//            double penaltyDiscount = params.getPenaltyDiscount();
//
//            fci.setCorrErrorsAlpha(penaltyDiscount);
//            fci.setSamplePrior(params.getSamplePrior());
//            fci.setStructurePrior(params.getStructurePrior());
//            fci.setCompleteRuleSetUsed(false);
//            fci.setFaithfulnessAssumed(params.isFaithfulnessAssumed());
//            graph = fci.search();
//        }
//
//        if (getSourceGraph() != null) {
//            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
//        } else if (knowledge.isDefaultToKnowledgeLayout()) {
//            SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
//        } else {
//            GraphUtils.circleLayout(graph, 200, 200, 150);
//        }
//
//        setResultGraph(graph);
//    }

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        Object model = getDataModel();

        if (model == null && getSourceGraph() != null) {
            model = getSourceGraph();
        }

        if (model == null) {
            throw new RuntimeException("Data source is unspecified. You may need to double click all your data boxes, \n" +
                    "then click Save, and then right click on them and select Propagate Downstream. \n" +
                    "The issue is that we use a seed to simulate from IM's, so your data is not saved to \n" +
                    "file when you save the session. It can, however, be recreated from the saved seed.");
        }

        final Parameters params = getParams();
        final double penaltyDiscount = params.getDouble("penaltyDiscount", 4);

        if (model instanceof Graph) {
            final GraphScore gesScore = new GraphScore((Graph) model);
            final IndependenceTest test = new IndTestDSep((Graph) model);
            this.gfci = new SvarGFci(test, gesScore);
            this.gfci.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
            this.gfci.setVerbose(true);
        } else {

            if (model instanceof DataSet) {
                final DataSet dataSet = (DataSet) model;

                if (dataSet.isContinuous()) {
                    final SemBicScore gesScore = new SemBicScore(new CovarianceMatrix((DataSet) model));
//                    SemBicScore2 gesScore = new SemBicScore2(new CovarianceMatrix((DataSet) model));
//                    SemGpScore gesScore = new SemGpScore(new CovarianceMatrix((DataSet) model));
//                    SvrScore gesScore = new SvrScore((DataSet) model);
                    gesScore.setPenaltyDiscount(penaltyDiscount);
                    System.out.println("Score done");
                    final IndependenceTest test = new IndTestDSep((Graph) model);
                    this.gfci = new SvarGFci(test, gesScore);
                    this.gfci.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
                }
//                else if (dataSet.isDiscrete()) {
//                    double samplePrior = ((Parameters) getParameters()).getSamplePrior();
//                    double structurePrior = ((Parameters) getParameters()).getStructurePrior();
//                    BDeuScore score = new BDeuScore(dataSet);
//                    score.setSamplePrior(samplePrior);
//                    score.setStructurePrior(structurePrior);
//                    gfci = new GFci(score);
//                }
                else {
                    throw new IllegalStateException("Data set must either be continuous or discrete.");
                }
            } else if (model instanceof ICovarianceMatrix) {
                final SemBicScore gesScore = new SemBicScore((ICovarianceMatrix) model);
                gesScore.setPenaltyDiscount(penaltyDiscount);
                gesScore.setPenaltyDiscount(penaltyDiscount);
                final IndependenceTest test = new IndTestDSep((Graph) model);
                this.gfci = new SvarGFci(test, gesScore);
                this.gfci.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
            } else if (model instanceof DataModelList) {
                final DataModelList list = (DataModelList) model;

                for (final DataModel dataModel : list) {
                    if (!(dataModel instanceof DataSet || dataModel instanceof ICovarianceMatrix)) {
                        throw new IllegalArgumentException("Need a combination of all continuous data sets or " +
                                "covariance matrices, or else all discrete data sets, or else a single externalGraph.");
                    }
                }

                if (list.size() != 1) {
                    throw new IllegalArgumentException("FGES takes exactly one data set, covariance matrix, or externalGraph " +
                            "as input. For multiple data sets as input, use IMaGES.");
                }

//                Parameters Parameters = (Parameters) getParameters();
//                Parameters params = (Parameters) Parameters;

                if (allContinuous(list)) {
                    final double penalty = params.getDouble("penaltyDiscount", 4);

                    final SemBicScoreImages fgesScore = new SemBicScoreImages(list);
                    fgesScore.setPenaltyDiscount(penalty);
                    final IndependenceTest test = new IndTestDSep((Graph) model);
                    this.gfci = new SvarGFci(test, fgesScore);
                    this.gfci.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
                }
//                else if (allDiscrete(list)) {
//                    double structurePrior = ((Parameters) getParameters()).getStructurePrior();
//                    double samplePrior = ((Parameters) getParameters()).getSamplePrior();
//
//                    BdeuScoreImages fgesScore = new BdeuScoreImages(list);
//                    fgesScore.setSamplePrior(samplePrior);
//                    fgesScore.setStructurePrior(structurePrior);
//
//                    gfci = new GFci(fgesScore);
//                }
                else {
                    throw new IllegalArgumentException("Data must be either all discrete or all continuous.");
                }
            } else {
                System.out.println("No viable input.");
            }
        }

//        gfci.setExternalGraph(externalGraph);
//        gfci.setKnowledge(getParameters().getKnowledge());
//        gfci.setnumCPDAGsToStore(params.getnumCPDAGsToSave());
        this.gfci.setVerbose(true);
//        gfci.setHeuristicSpeedup(true);
//        gfci.setMaxIndegree(3);
        this.gfci.setFaithfulnessAssumed(params.getBoolean("faithfulnessAssumed", true));
        final Graph graph = this.gfci.search();

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (((IKnowledge) getParams().get("knowledge", new Knowledge2())).isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, (IKnowledge) getParams().get("knowledge", new Knowledge2()));
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);

//        this.topGraphs = new ArrayList<>(gfci.getTopGraphs());
//
//        if (topGraphs.isEmpty()) {
//
//            topGraphs.add(new ScoredGraph(getResultGraph(), Double.NaN));
//        }
//
//        setIndex(topGraphs.size() - 1);
    }

    private boolean allContinuous(final List<DataModel> dataModels) {
        for (final DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                if (!dataModel.isContinuous() || dataModel instanceof ICovarianceMatrix) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean allDiscrete(final List<DataModel> dataModels) {
        for (final DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                if (!dataModel.isDiscrete()) {
                    return false;
                }
            }
        }

        return true;
    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        final Parameters params = getParams();
        final IndTestType testType;

        if (getParams() instanceof Parameters) {
            final Parameters _params = params;
            testType = (IndTestType) _params.get("indTestType", IndTestType.FISHER_Z);
        } else {
            final Parameters _params = params;
            testType = (IndTestType) _params.get("indTestType", IndTestType.FISHER_Z);
        }

        return new IndTestChooser().getTest(dataModel, params, testType);
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        final List<String> names = new ArrayList<>();
//        names.add("Definite ColliderDiscovery");
//        names.add("Definite Noncolliders");
        names.add("Ambiguous Triples");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(final Node node) {
        final List<List<Triple>> triplesList = new ArrayList<>();
        final Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getDefiniteCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getDefiniteNoncollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }


    @Override
    public String getAlgorithmName() {
        return "GFCI";
    }
}



