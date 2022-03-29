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
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.Unmarshallable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the GES algorithm.
 *
 * @author Ricardo Silva
 * @author Daniel Malinsky
 */

public class TsFgesRunner extends AbstractAlgorithmRunner implements IFgesRunner, GraphSource,
        PropertyChangeListener, IGesRunner, Indexable, DoNotAddOldModel, Unmarshallable {
    static final long serialVersionUID = 23L;
    private LinkedHashMap<String, String> allParamSettings;

    public enum Type {CONTINUOUS, DISCRETE, MIXED, GRAPH}

    private transient List<PropertyChangeListener> listeners;
    private List<ScoredGraph> topGraphs;
    private int index;
    private transient TsFges2 fges;
    private transient Graph externalGraph;

    //============================CONSTRUCTORS============================//

    public TsFgesRunner(final DataWrapper[] dataWrappers, final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(new MergeDatasetsWrapper(dataWrappers, params), params, knowledgeBoxModel);
    }

    public TsFgesRunner(final DataWrapper[] dataWrappers, final Parameters params) {
        super(new MergeDatasetsWrapper(dataWrappers, params), params, null);
    }

    public TsFgesRunner(final DataWrapper[] dataWrappers, final GraphSource graph, final Parameters params) {
        super(new MergeDatasetsWrapper(dataWrappers, params), params, null);
        if (graph == this) throw new IllegalArgumentException();
        this.externalGraph = graph.getGraph();
    }

    public TsFgesRunner(final DataWrapper[] dataWrappers, final GraphSource graph, final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(new MergeDatasetsWrapper(dataWrappers, params), params, knowledgeBoxModel);
        if (graph == this) throw new IllegalArgumentException();
        this.externalGraph = graph.getGraph();
    }

    public TsFgesRunner(final GraphWrapper graphWrapper, final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public TsFgesRunner(final GraphWrapper graphWrapper, final Parameters params) {
        super(graphWrapper.getGraph(), params, null);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new DataWrapper(new Parameters());
    }

    //============================PUBLIC METHODS==========================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        System.out.println("A");

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

        if (model instanceof Graph) {
            final GraphScore gesScore = new GraphScore((Graph) model);
            this.fges = new TsFges2(gesScore);
            this.fges.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
            this.fges.setVerbose(true);
        } else {
            final double penaltyDiscount = params.getDouble("penaltyDiscount", 4);

            if (model instanceof DataSet) {
                final DataSet dataSet = (DataSet) model;

                if (dataSet.isContinuous()) {
                    final SemBicScore gesScore = new SemBicScore(new CovarianceMatrix((DataSet) model));
//                    SemBicScore2 gesScore = new SemBicScore2(new CovarianceMatrix((DataSet) model));
//                    SemGpScore gesScore = new SemGpScore(new CovarianceMatrix((DataSet) model));
//                    SvrScore gesScore = new SvrScore((DataSet) model);
                    gesScore.setPenaltyDiscount(penaltyDiscount);
                    System.out.println("Score done");
                    this.fges = new TsFges2(gesScore);
                } else if (dataSet.isDiscrete()) {
                    final double samplePrior = getParams().getDouble("samplePrior", 1);
                    final double structurePrior = getParams().getDouble("structurePrior", 1);
                    final BDeuScore score = new BDeuScore(dataSet);
                    score.setSamplePrior(samplePrior);
                    score.setStructurePrior(structurePrior);
                    this.fges = new TsFges2(score);
                } else {
                    final MixedBicScore gesScore = new MixedBicScore(dataSet);
                    gesScore.setPenaltyDiscount(penaltyDiscount);
                    this.fges = new TsFges2(gesScore);
                }
            } else if (model instanceof ICovarianceMatrix) {
                final SemBicScore gesScore = new SemBicScore((ICovarianceMatrix) model);
                gesScore.setPenaltyDiscount(penaltyDiscount);
                gesScore.setPenaltyDiscount(penaltyDiscount);
                this.fges = new TsFges2(gesScore);
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

                if (allContinuous(list)) {
                    final double penalty = getParams().getDouble("penaltyDiscount", 4);

                    if (params.getBoolean("firstNontriangular", false)) {
                        final SemBicScoreImages fgesScore = new SemBicScoreImages(list);
                        fgesScore.setPenaltyDiscount(penalty);
                        this.fges = new TsFges2(fgesScore);
                    } else {
                        final SemBicScoreImages fgesScore = new SemBicScoreImages(list);
                        fgesScore.setPenaltyDiscount(penalty);
                        this.fges = new TsFges2(fgesScore);
                    }
                } else if (allDiscrete(list)) {
                    final double structurePrior = getParams().getDouble("structurePrior", 1);
                    final double samplePrior = getParams().getDouble("samplePrior", 1);

                    final BdeuScoreImages fgesScore = new BdeuScoreImages(list);
                    fgesScore.setSamplePrior(samplePrior);
                    fgesScore.setStructurePrior(structurePrior);

                    if (params.getBoolean("firstNontriangular", false)) {
                        this.fges = new TsFges2(fgesScore);
                    } else {
                        this.fges = new TsFges2(fgesScore);
                    }
                } else {
                    throw new IllegalArgumentException("Data must be either all discrete or all continuous.");
                }
            } else {
                System.out.println("No viable input.");
            }
        }

        this.fges.setExternalGraph(this.externalGraph);
        this.fges.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
        this.fges.setNumCPDAGsToStore(params.getInt("numCPDAGsToSave", 1));
        this.fges.setVerbose(true);
//        fges.setHeuristicSpeedup(((Parameters) params.getIndTestParams()).isFaithfulnessAssumed());
//        * there is no setHeuristicSpeedup option in Fges2 and so likewise TsFges2. *
        final Graph graph = this.fges.search();

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (((IKnowledge) getParams().get("knowledge", new Knowledge2())).isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, (IKnowledge) getParams().get("knowledge", new Knowledge2()));
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);

        this.topGraphs = new ArrayList<>(this.fges.getTopGraphs());

        if (this.topGraphs.isEmpty()) {

            this.topGraphs.add(new ScoredGraph(getResultGraph(), Double.NaN));
        }

        setIndex(this.topGraphs.size() - 1);
    }

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
//    public FgesRunner.Type getType() { return FgesRunner.getType(); }
    public FgesRunner.Type getType() {
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

        final FgesRunner.Type type;

        if (model instanceof Graph) {
            type = FgesRunner.Type.GRAPH;
        } else if (model instanceof DataSet) {
            final DataSet dataSet = (DataSet) model;

            if (dataSet.isContinuous()) {
                type = FgesRunner.Type.CONTINUOUS;
            } else if (dataSet.isDiscrete()) {
                type = FgesRunner.Type.DISCRETE;
            } else {
                type = FgesRunner.Type.MIXED;
//                throw new IllegalStateException("Data set must either be continuous or discrete.");
            }
        } else if (model instanceof ICovarianceMatrix) {
            type = FgesRunner.Type.CONTINUOUS;
        } else if (model instanceof DataModelList) {
            final DataModelList list = (DataModelList) model;

            if (allContinuous(list)) {
                type = FgesRunner.Type.CONTINUOUS;
            } else if (allDiscrete(list)) {
                type = FgesRunner.Type.DISCRETE;
            } else {
                type = FgesRunner.Type.MIXED;
//                throw new IllegalArgumentException("Data must be either all discrete or all continuous.");
            }
        } else {
            throw new IllegalArgumentException("Unrecognized data type.");
        }

        return type;
    }

    private boolean allContinuous(final List<DataModel> dataModels) {
        for (final DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                if (!((DataSet) dataModel).isContinuous() || dataModel instanceof ICovarianceMatrix) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean allDiscrete(final List<DataModel> dataModels) {
        for (final DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                if (!((DataSet) dataModel).isDiscrete()) {
                    return false;
                }
            }
        }

        return true;
    }

    public void setIndex(final int index) {
        if (index < -1) {
            throw new IllegalArgumentException("Must be in >= -1: " + index);
        }

        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }

    public Graph getGraph() {
        if (getIndex() >= 0) {
            return getTopGraphs().get(getIndex()).getGraph();
        } else {
            return getResultGraph();
        }
    }


    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new ArrayList<>();
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(final Node node) {
        return new ArrayList<>();
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        final MeekRules rules = new MeekRules();
        rules.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
        return rules;
    }

    @Override
    public Map<String, String> getParamSettings() {
        super.getParamSettings();
        final Parameters params = getParams();
        this.paramSettings.put("Penalty Discount", new DecimalFormat("0.0").format(params.getDouble("penaltyDiscount", 4)));
        return this.paramSettings;
    }

    @Override
    public String getAlgorithmName() {
        return "FGES";
    }

    public void propertyChange(final PropertyChangeEvent evt) {
        firePropertyChange(evt);
    }

    private void firePropertyChange(final PropertyChangeEvent evt) {
        for (final PropertyChangeListener l : getListeners()) {
            l.propertyChange(evt);
        }
    }

    private List<PropertyChangeListener> getListeners() {
        if (this.listeners == null) {
            this.listeners = new ArrayList<>();
        }
        return this.listeners;
    }

    public void addPropertyChangeListener(final PropertyChangeListener l) {
        if (!getListeners().contains(l)) getListeners().add(l);
    }

    public List<ScoredGraph> getTopGraphs() {
        return this.topGraphs;
    }

    public String getBayesFactorsReport(final Graph dag) {
        if (this.fges == null) {
            return "Please re-run IMaGES.";
        } else {
            return this.fges.logEdgeBayesFactorsString(dag);
        }
    }

    public GraphScorer getGraphScorer() {
        return this.fges;
    }
}





