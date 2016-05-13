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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the MB Fan Search
 * algorithm.
 *
 * @author Frank Wimberly after Joe Ramsey's PcRunner
 */
public class FgsMbRunner extends AbstractAlgorithmRunner implements
        IndTestProducer, GraphSource, IFgsRunner, Indexable {
    static final long serialVersionUID = 23L;

    private transient FgsMb2 fgs;
    private int index;
    private ArrayList<ScoredGraph> topGraphs;


    // =========================CONSTRUCTORS===============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public FgsMbRunner(DataWrapper dataWrapper, FgsParams params,
                       KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public FgsMbRunner(DataWrapper dataWrapper, FgsParams params) {
        super(dataWrapper, params, null);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public FgsMbRunner(Graph graph, FgsParams params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public FgsMbRunner(GraphWrapper dagWrapper, FgsParams params) {
        super(dagWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public FgsMbRunner(GraphWrapper dagWrapper, KnowledgeBoxModel knowledgeBoxModel, FgsParams params) {
        super(dagWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public FgsMbRunner(DagWrapper dagWrapper, FgsParams params) {
        super(dagWrapper.getDag(), params);
    }

    /**
     * Constructs a wrapper for the given EdgeListGraph.
     */
    public FgsMbRunner(DagWrapper dagWrapper, KnowledgeBoxModel knowledgeBoxModel, FgsParams params) {
        super(dagWrapper.getDag(), params, knowledgeBoxModel);
    }

    public FgsMbRunner(SemGraphWrapper dagWrapper, FgsParams params) {
        super(dagWrapper.getGraph(), params);
    }

    public FgsMbRunner(SemGraphWrapper dagWrapper, KnowledgeBoxModel knowledgeBoxModel, FgsParams params) {
        super(dagWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public FgsMbRunner(IndependenceFactsModel model, FgsParams params) {
        super(model, params, null);
    }

    public FgsMbRunner(IndependenceFactsModel model, FgsParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static FgsMbRunner serializableInstance() {
        return new FgsMbRunner(DataWrapper.serializableInstance(),
                FgsParams.serializableInstance(), KnowledgeBoxModel
                .serializableInstance());
    }

    // =================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        IKnowledge knowledge = getParams().getKnowledge();
        String targetName = ((FgsParams) getParams()).getTargetName();

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

        FgsParams params = (FgsParams) getParams();
        Node target = null;

        if (model instanceof Graph) {
            GraphScore gesScore = new GraphScore((Graph) model);
            target = gesScore.getVariable(targetName);
            fgs = new FgsMb2(gesScore);
            fgs.setKnowledge(getParams().getKnowledge());
            fgs.setNumPatternsToStore(params.getIndTestParams().getNumPatternsToSave());
            fgs.setVerbose(true);
        } else if (model instanceof DataSet) {
            DataSet dataSet = (DataSet) model;

            if (dataSet.isContinuous()) {
                SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly((DataSet) model));
                target = score.getVariable(targetName);
                score.setPenaltyDiscount(params.getComplexityPenalty());
                fgs = new FgsMb2(score);
            } else if (dataSet.isDiscrete()) {
                double samplePrior = 1;//((FgsParams) getParams()).getSamplePrior();
                double structurePrior = 1;//((FgsParams) getParams()).getStructurePrior();
                BDeuScore score = new BDeuScore(dataSet);
                score.setSamplePrior(samplePrior);
                score.setStructurePrior(structurePrior);
                target = score.getVariable(targetName);
                fgs = new FgsMb2(score);
            } else {
                throw new IllegalStateException("Data set must either be continuous or discrete.");
            }
        } else if (model instanceof ICovarianceMatrix) {
            SemBicScore gesScore = new SemBicScore((ICovarianceMatrix) model);
            gesScore.setPenaltyDiscount(params.getIndTestParams().getAlpha());
            gesScore.setPenaltyDiscount(params.getComplexityPenalty());
            target = gesScore.getVariable(targetName);
            fgs = new FgsMb2(gesScore);
        }
        else if (model instanceof DataModelList) {
            DataModelList list = (DataModelList) model;

            for (DataModel dataModel : list) {
                if (!(dataModel instanceof DataSet || dataModel instanceof ICovarianceMatrix)) {
                    throw new IllegalArgumentException("Need a combination of all continuous data sets or " +
                            "covariance matrices, or else all discrete data sets, or else a single initialGraph.");
                }
            }

//            if (list.size() != 1) {
//                throw new IllegalArgumentException("FGS takes exactly one data set, covariance matrix, or initialGraph " +
//                        "as input. For multiple data sets as input, use IMaGES.");
//            }

            FgsParams FgsParams = (FgsParams) getParams();
            FgsIndTestParams indTestParams = (FgsIndTestParams) FgsParams.getIndTestParams();

            if (allContinuous(list)) {
                double penalty = ((FgsParams) getParams()).getComplexityPenalty();

                if (indTestParams.isFirstNontriangular()) {
                    SemBicScoreImages fgsScore = new SemBicScoreImages(list);
                    fgsScore.setPenaltyDiscount(penalty);
                    target = fgsScore.getVariable(targetName);
                    fgs = new FgsMb2(fgsScore);
                } else {
                    SemBicScoreImages fgsScore = new SemBicScoreImages(list);
                    fgsScore.setPenaltyDiscount(penalty);
                    target = fgsScore.getVariable(targetName);
                    fgs = new FgsMb2(fgsScore);
                }
            } else if (allDiscrete(list)) {
                double structurePrior = ((FgsParams) getParams()).getStructurePrior();
                double samplePrior = ((FgsParams) getParams()).getSamplePrior();

                BdeuScoreImages fgsScore = new BdeuScoreImages(list);
                fgsScore.setSamplePrior(samplePrior);
                fgsScore.setStructurePrior(structurePrior);
                target = fgsScore.getVariable(targetName);

                if (indTestParams.isFirstNontriangular()) {
                    fgs = new FgsMb2(fgsScore);
                } else {
                    fgs = new FgsMb2(fgsScore);
                }
            } else {
                throw new IllegalArgumentException("Data must be either all discrete or all continuous.");
            }
        }        else {
            System.out.println("No viable input.");
        }


//        Graph searchGraph;
//
//        if (true) {
//            DataModel dataModel = getDataModelList().getSelectedModel();
//            ICovarianceMatrix cov;
//            Node target;
//            FgsMb fgs;
//
//            if (dataModel instanceof DataSet) {
//                DataSet dataSet = (DataSet) dataModel;
//                target = dataSet.getVariable(targetName);
//
//                if (dataSet.isContinuous()) {
//                    SemBicScore gesScore = new SemBicScore(new CovarianceMatrixOnTheFly((DataSet) dataModel),
//                            getParams().getIndTestParams().getAlpha());
//                    fgs = new FgsMb(gesScore, target);
//                } else if (dataSet.isDiscrete()) {
//                    double structurePrior = 1;
//                    double samplePrior = getParams().getIndTestParams().getAlpha();
//                    BDeuScore score = new BDeuScore(dataSet);
//                    score.setSamplePrior(samplePrior);
//                    score.setStructurePrior(structurePrior);
//                    fgs = new FgsMb(score, target);
//                } else {
//                    throw new IllegalStateException("Data set must either be continuous or discrete.");
//                }
//            } else if (dataModel instanceof ICovarianceMatrix) {
//                cov = (ICovarianceMatrix) dataModel;
//                SemBicScore score = new SemBicScore(cov,
//                        getParams().getIndTestParams().getAlpha());
//                target = cov.getVariable(targetName);
//                fgs = new FgsMb(score, target);
//            } else {
//                throw new IllegalArgumentException("Expecting a data set or a covariance matrix.");
//            }
//
//            fgs.setVerbose(true);
//            fgs.setHeuristicSpeedup(true);
//            searchGraph = fgs.search();
//        } else {
//            Node target = getIndependenceTest().getVariable(targetName);
//            System.out.println("Target = " + target);
//
//            int depth = getParams().getIndTestParams().getMaxIndegree();
//
//            ScoredIndTest fgsScore = new ScoredIndTest(getIndependenceTest());
//            fgsScore.setParameter1(getParams().getIndTestParams().getAlpha());
//            FgsMb search = new FgsMb(fgsScore, target);
//            search.setKnowledge(knowledge);
//            search.setMaxIndegree(depth);
//            search.setVerbose(true);
//            search.setHeuristicSpeedup(true);
//            searchGraph = search.search();
//        }

//        if (getSourceGraph() != null) {
//            GraphUtils.arrangeBySourceGraph(searchGraph, getSourceGraph());
//        } else if (knowledge.isDefaultToKnowledgeLayout()) {
//            SearchGraphUtils.arrangeByKnowledgeTiers(searchGraph, knowledge);
//        } else {
//            GraphUtils.circleLayout(searchGraph, 200, 200, 150);
//        }

//        fgs.setInitialGraph(initialGraph);
        fgs.setKnowledge(getParams().getKnowledge());
        fgs.setNumPatternsToStore(params.getIndTestParams().getNumPatternsToSave());
        fgs.setVerbose(true);
//        fgs.setFaithfulnessAssumed(((FgsIndTestParams) params.getIndTestParams()).isFaithfulnessAssumed());
        fgs.setMaxIndegree(params.getIndTestParams().getDepth());
        Graph graph = fgs.search(target);

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (getParams().getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, getParams().getKnowledge());
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        this.topGraphs = new ArrayList<>(fgs.getTopGraphs());

        if (topGraphs.isEmpty()) {
            topGraphs.add(new ScoredGraph(getResultGraph(), Double.NaN));
        }

        setIndex(topGraphs.size() - 1);

        setResultGraph(graph);
    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        FgsParams params = (FgsParams) getParams();
        IndTestType testType = params.getIndTestType();
        return new IndTestChooser().getTest(dataModel, params, testType);
    }

    private boolean allContinuous(List<DataModel> dataModels) {
        for (DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                if (!((DataSet) dataModel).isContinuous() || dataModel instanceof ICovarianceMatrix) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean allDiscrete(List<DataModel> dataModels) {
        for (DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                if (!((DataSet) dataModel).isDiscrete()) {
                    return false;
                }
            }
        }

        return true;
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
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s) throws IOException,
            ClassNotFoundException {
        s.defaultReadObject();
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<String>();
        names.add("Colliders");
        names.add("Noncolliders");
        names.add("Ambiguous Triples");
        return names;
    }

    /**
     * @return the list of triples corresponding to
     * <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getCollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getNoncollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }

    public boolean supportsKnowledge() {
        return true;
    }

    @Override
    public String getAlgorithmName() {
        return "MBFS";
    }

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public FgsRunner.Type getType() {
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

        FgsRunner.Type type;

        if (model instanceof Graph) {
            type = FgsRunner.Type.GRAPH;
        } else if (model instanceof DataSet) {
            DataSet dataSet = (DataSet) model;

            if (dataSet.isContinuous()) {
                type = FgsRunner.Type.CONTINUOUS;
            } else if (dataSet.isDiscrete()) {
                type = FgsRunner.Type.DISCRETE;
            } else {
                throw new IllegalStateException("Data set must either be continuous or discrete.");
            }
        } else if (model instanceof ICovarianceMatrix) {
            type = FgsRunner.Type.CONTINUOUS;
        } else if (model instanceof DataModelList) {
            DataModelList list = (DataModelList) model;

            if (allContinuous(list)) {
                type = FgsRunner.Type.CONTINUOUS;
            } else if (allDiscrete(list)) {
                type = FgsRunner.Type.DISCRETE;
            } else {
                throw new IllegalArgumentException("Data must be either all discrete or all continuous.");
            }
        } else {
            throw new IllegalArgumentException("Unrecognized data type.");
        }

        return type;
    }

    @Override
    public List<ScoredGraph> getTopGraphs() {
        return topGraphs;
    }

    public void setIndex(int index) {
        if (index < -1) {
            throw new IllegalArgumentException("Must be in >= -1: " + index);
        }

        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}



