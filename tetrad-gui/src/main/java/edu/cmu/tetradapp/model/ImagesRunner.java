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
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the GES algorithm.
 *
 * @author Ricardo Silva
 */

public class ImagesRunner extends AbstractAlgorithmRunner implements IFgsRunner, GraphSource,
        PropertyChangeListener, IGesRunner, Indexable {
    static final long serialVersionUID = 23L;

    public FgsRunner.Type getType() {
        return type;
    }

    private transient List<PropertyChangeListener> listeners;
    private List<ScoredGraph> topGraphs;
    private int index;
    private transient Fgs fgs;
    private Graph graph;
    private FgsRunner.Type type;

    //============================CONSTRUCTORS============================//

    public ImagesRunner(DataWrapper dataWrapper, FgsParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(new MergeDatasetsWrapper(dataWrapper), params, knowledgeBoxModel);
        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper, FgsParams params) {
        super(new MergeDatasetsWrapper(dataWrapper), params, null);
        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper, GraphWrapper graph, FgsParams params) {
        super(new MergeDatasetsWrapper(dataWrapper), params, null);
        this.graph = graph.getGraph();
        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper, GraphWrapper graph, FgsParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(new MergeDatasetsWrapper(dataWrapper), params, knowledgeBoxModel);
        this.graph = graph.getGraph();
        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2
                ),
                params, null);
        type = computeType();

    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     DataWrapper dataWrapper3,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2,
                        dataWrapper3
                ),
                params, null);

        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     DataWrapper dataWrapper3,
                     DataWrapper dataWrapper4,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2,
                        dataWrapper3,
                        dataWrapper4
                ),
                params, null);

        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     DataWrapper dataWrapper3,
                     DataWrapper dataWrapper4,
                     DataWrapper dataWrapper5,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2,
                        dataWrapper3,
                        dataWrapper4,
                        dataWrapper5
                ),
                params, null);

        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     DataWrapper dataWrapper3,
                     DataWrapper dataWrapper4,
                     DataWrapper dataWrapper5,
                     DataWrapper dataWrapper6,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2,
                        dataWrapper3,
                        dataWrapper4,
                        dataWrapper5,
                        dataWrapper6
                ),
                params, null);

        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     DataWrapper dataWrapper3,
                     DataWrapper dataWrapper4,
                     DataWrapper dataWrapper5,
                     DataWrapper dataWrapper6,
                     DataWrapper dataWrapper7,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2,
                        dataWrapper3,
                        dataWrapper4,
                        dataWrapper5,
                        dataWrapper6,
                        dataWrapper7
                ),
                params, null);

        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     DataWrapper dataWrapper3,
                     DataWrapper dataWrapper4,
                     DataWrapper dataWrapper5,
                     DataWrapper dataWrapper6,
                     DataWrapper dataWrapper7,
                     DataWrapper dataWrapper8,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2,
                        dataWrapper3,
                        dataWrapper4,
                        dataWrapper5,
                        dataWrapper6,
                        dataWrapper7,
                        dataWrapper8
                ),
                params, null);

        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     DataWrapper dataWrapper3,
                     DataWrapper dataWrapper4,
                     DataWrapper dataWrapper5,
                     DataWrapper dataWrapper6,
                     DataWrapper dataWrapper7,
                     DataWrapper dataWrapper8,
                     DataWrapper dataWrapper9,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2,
                        dataWrapper3,
                        dataWrapper4,
                        dataWrapper5,
                        dataWrapper6,
                        dataWrapper7,
                        dataWrapper8,
                        dataWrapper9
                ),
                params, null);

        type = computeType();
    }

    public ImagesRunner(DataWrapper dataWrapper1,
                     DataWrapper dataWrapper2,
                     DataWrapper dataWrapper3,
                     DataWrapper dataWrapper4,
                     DataWrapper dataWrapper5,
                     DataWrapper dataWrapper6,
                     DataWrapper dataWrapper7,
                     DataWrapper dataWrapper8,
                     DataWrapper dataWrapper9,
                     DataWrapper dataWrapper10,
                     FgsParams params) {

        super(new MergeDatasetsWrapper(
                        dataWrapper1,
                        dataWrapper2,
                        dataWrapper3,
                        dataWrapper4,
                        dataWrapper5,
                        dataWrapper6,
                        dataWrapper7,
                        dataWrapper8,
                        dataWrapper9,
                        dataWrapper10
                ),
                params, null);

        type = computeType();
    }

    public ImagesRunner(GraphWrapper graphWrapper, FgsParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
        type = computeType();
    }

    public ImagesRunner(GraphWrapper graphWrapper, FgsParams params) {
        super(graphWrapper.getGraph(), params, null);
        type = computeType();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static ImagesRunner serializableInstance() {
        return new ImagesRunner(DataWrapper.serializableInstance(),
                FgsParams.serializableInstance(), KnowledgeBoxModel.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

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

        FgsParams params = (FgsParams) getParams();

        if (model instanceof Graph) {
            GraphScore gesScore = new GraphScore((Graph) model);
            fgs = new Fgs(gesScore);
            fgs.setKnowledge(getParams().getKnowledge());
            fgs.setNumPatternsToStore(params.getIndTestParams().getNumPatternsToSave());
            fgs.setVerbose(true);
        } else if (model instanceof DataSet) {
            DataSet dataSet = (DataSet) model;

            if (dataSet.isContinuous()) {
                SemBicScore gesScore = new SemBicScore(new CovarianceMatrixOnTheFly((DataSet) model),
                        params.getComplexityPenalty());
                fgs = new Fgs(gesScore);
                fgs.setKnowledge(getParams().getKnowledge());
                fgs.setNumPatternsToStore(params.getIndTestParams().getNumPatternsToSave());
                fgs.setFaithfulnessAssumed(((FgsIndTestParams) params.getIndTestParams()).isFaithfulnessAssumed());
                fgs.setVerbose(true);
            } else if (dataSet.isDiscrete()) {
                double samplePrior = ((FgsParams) getParams()).getSamplePrior();
                double structurePrior = ((FgsParams) getParams()).getStructurePrior();
                BDeuScore score = new BDeuScore(dataSet);
                score.setSamplePrior(samplePrior);
                score.setStructurePrior(structurePrior);
                fgs = new Fgs(score);
                fgs.setVerbose(true);
                fgs.setKnowledge(getParams().getKnowledge());
                fgs.setNumPatternsToStore(params.getIndTestParams().getNumPatternsToSave());
                fgs.setFaithfulnessAssumed(((FgsIndTestParams) params.getIndTestParams()).isFaithfulnessAssumed());
            } else {
                throw new IllegalStateException("Data set must either be continuous or discrete.");
            }
        } else if (model instanceof ICovarianceMatrix) {
            SemBicScore gesScore = new SemBicScore((ICovarianceMatrix) model, params.getComplexityPenalty());
            fgs = new Fgs(gesScore);
            fgs.setKnowledge(getParams().getKnowledge());
            fgs.setNumPatternsToStore(params.getIndTestParams().getNumPatternsToSave());
            fgs.setFaithfulnessAssumed(((FgsIndTestParams) params.getIndTestParams()).isFaithfulnessAssumed());
            fgs.setVerbose(true);
        } else if (model instanceof DataModelList) {
            DataModelList list = (DataModelList) model;

            for (DataModel dataModel : list) {
                if (!(dataModel instanceof DataSet || dataModel instanceof ICovarianceMatrix)) {
                    throw new IllegalArgumentException("Need a combination of all continuous data sets or " +
                            "covariance matrices, or else all discrete data sets, or else a single graph.");
                }
            }

//            if (list.size() != 1) {
//                throw new IllegalArgumentException("FGS takes exactly one data set, covariance matrix, or graph " +
//                        "as input. For multiple data sets as input, use IMaGES.");
//            }

            FgsParams FgsParams = (FgsParams) getParams();
            FgsIndTestParams indTestParams = (FgsIndTestParams) FgsParams.getIndTestParams();

            if (allContinuous(list)) {
                double penalty = ((FgsParams) getParams()).getComplexityPenalty();

                if (indTestParams.isFirstNontriangular()) {
                    fgs = new Fgs(new SemBicScoreImages(list));
                    fgs.setPenaltyDiscount(penalty);
                } else {
                    fgs = new Fgs(new SemBicScoreImages(list));
                    fgs.setPenaltyDiscount(penalty);
                }
            } else if (allDiscrete(list)) {
                double structurePrior = ((FgsParams) getParams()).getStructurePrior();
                double samplePrior = ((FgsParams) getParams()).getSamplePrior();

                if (indTestParams.isFirstNontriangular()) {
                    fgs = new Fgs(new BdeuScoreImages(list));
                    fgs.setSamplePrior(samplePrior);
                    fgs.setStructurePrior(structurePrior);
                } else {
                    fgs = new Fgs(new BdeuScoreImages(list));
                    fgs.setSamplePrior(samplePrior);
                    fgs.setStructurePrior(structurePrior);
                }
            } else {
                throw new IllegalArgumentException("Data must be either all discrete or all continuous.");
            }
        } else {
            System.out.println("No viable input.");
        }

        Graph graph = fgs.search();

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (getParams().getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, getParams().getKnowledge());
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);

        this.topGraphs = new ArrayList<>(fgs.getTopGraphs());

        if (topGraphs.isEmpty()) {
            topGraphs.add(new ScoredGraph(getResultGraph(), Double.NaN));
        }

        this.topGraphs = new ArrayList<>(fgs.getTopGraphs());

        if (this.topGraphs.isEmpty()) {
            this.topGraphs.add(new ScoredGraph(getResultGraph(), Double.NaN));
        }

        setIndex(topGraphs.size() - 1);
    }

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public FgsRunner.Type computeType() {
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
        }

        return type;
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

    public void setIndex(int index) {
        if (index < -1) {
            throw new IllegalArgumentException("Must be in >= -1: " + index);
        }

        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public Graph getGraph() {
        return getTopGraphs().get(getIndex()).getGraph();
    }


    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new ArrayList<String>();
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new ArrayList<List<Triple>>();
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getParams().getKnowledge());
        return rules;
    }

    @Override
    public String getAlgorithmName() {
        return "IMaGES";
    }

    public void propertyChange(PropertyChangeEvent evt) {
        firePropertyChange(evt);
    }

    private void firePropertyChange(PropertyChangeEvent evt) {
        for (PropertyChangeListener l : getListeners()) {
            l.propertyChange(evt);
        }
    }

    private List<PropertyChangeListener> getListeners() {
        if (listeners == null) {
            listeners = new ArrayList<PropertyChangeListener>();
        }
        return listeners;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        if (!getListeners().contains(l)) getListeners().add(l);
    }

    public List<ScoredGraph> getTopGraphs() {
        return this.topGraphs;
    }

    public String getBayesFactorsReport(Graph dag) {
        if (fgs == null) {
            return "Please re-run IMaGES.";
        } else {
            return fgs.logEdgeBayesFactorsString(dag);
        }
    }

    public GraphScorer getGraphScorer() {
        return fgs;
    }

}





