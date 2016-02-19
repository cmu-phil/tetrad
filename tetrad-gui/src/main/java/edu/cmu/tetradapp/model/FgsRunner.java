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

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the GES algorithm.
 *
 * @author Ricardo Silva
 */

public class FgsRunner extends AbstractAlgorithmRunner implements GraphSource,
        PropertyChangeListener, Indexable, IGesRunner, DoNotAddOldModel {
    static final long serialVersionUID = 23L;
    private transient List<PropertyChangeListener> listeners;
    private Map<Graph, Double> dagsToScores;
    private List<ScoredGraph> topGraphs = new ArrayList<ScoredGraph>();
    private int index;
    private List<Map<Graph, Double>> allDagsToScores;
    private Graph initialGraph;
    private Graph trueGraph;// deprecated.

    //============================CONSTRUCTORS============================//

    public FgsRunner(GraphWrapper graphWrapper, FgsParams params) {
        super(graphWrapper.getGraph(), params, null);
    }

    public FgsRunner(DataWrapper dataWrapper, FgsParams params) {
        super(dataWrapper, params, null);
    }

    public FgsRunner(DataWrapper dataWrapper, GraphSource trueGraph, FgsParams params) {
        this(dataWrapper, params, null);
        this.initialGraph = trueGraph.getGraph();
    }

    public FgsRunner(DataWrapper dataWrapper, FgsParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public FgsRunner(GraphSource graphWrapper, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     *
     * @see TetradSerializableUtils
     */
    public static FgsRunner serializableInstance() {
        return new FgsRunner(DataWrapper.serializableInstance(),
                FgsParams.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */

    public void execute() {
        Object source = getDataModel();

        if (source == null && getSourceGraph() != null) {
            source = getSourceGraph();
        }

        if (source == null) {
            throw new RuntimeException("Data source is unspecified. You may need to double click all your data boxes, \n" +
                    "then click Save, and then right click on them and select Propagate Downstream. \n" +
                    "The issue is that we use a seed to simulate from IM's, so your data is not saved to \n" +
                    "file when you save the session. It can, however, be recreated from the saved seed.");
        }

        FgsParams fgsParams = (FgsParams) getParams();
        FgsIndTestParams indTestParams = (FgsIndTestParams) fgsParams.getIndTestParams();
        double penalty = fgsParams.getComplexityPenalty();
        Fgs ges;
        boolean faithfulnessAssumed = fgsParams.isFaithfulnessAssumed();

        if (source instanceof ICovarianceMatrix) {
            SemBicScore gesScore = new SemBicScore((ICovarianceMatrix) source);
            gesScore.setPenaltyDiscount(penalty);
            ges = new Fgs(gesScore);
            ges.setKnowledge(getParams().getKnowledge());
            ges.setPenaltyDiscount(penalty);
            ges.setDepth(2);
            ges.setNumPatternsToStore(indTestParams.getNumPatternsToSave());
            ges.setFaithfulnessAssumed(faithfulnessAssumed);
            ges.setVerbose(true);

        } else if (source instanceof DataSet) {
            DataSet dataSet = (DataSet) source;

            if (dataSet.isContinuous()) {

                SemBicScore gesScore = new SemBicScore(new CovarianceMatrixOnTheFly((DataSet) source));
                gesScore.setPenaltyDiscount(penalty);
                ges = new Fgs(gesScore);
                ges.setKnowledge(getParams().getKnowledge());
                ges.setPenaltyDiscount(penalty);
//                ges.setDepth(2);
                ges.setNumPatternsToStore(indTestParams.getNumPatternsToSave());
//                ges.setFaithfulnessAssumed(faithfulnessAssumed);
                ges.setVerbose(true);
            }
            else if (dataSet.isDiscrete()) {
                double samplePrior = ((FgsParams) getParams()).getSamplePrior();
                double structurePrior = ((FgsParams) getParams()).getStructurePrior();
                BDeuScore score = new BDeuScore(dataSet);
                score.setSamplePrior(samplePrior);
                score.setStructurePrior(structurePrior);
//                BDeuScore score = new BDeuScore(dataSet);
                ges = new Fgs(score);
                ges.setVerbose(true);
                ges.setKnowledge(getParams().getKnowledge());
//                ges.setDepth(2);
                ges.setNumPatternsToStore(indTestParams.getNumPatternsToSave());
                ges.setFaithfulnessAssumed(faithfulnessAssumed);
            }
            else {
                throw new IllegalStateException("Data set must either be continuous or discrete.");
            }
        } else if (source instanceof Graph) {
            GraphScore gesScore = new GraphScore((Graph) source);
            ges = new Fgs(gesScore);
            ges.setKnowledge(getParams().getKnowledge());
            ges.setPenaltyDiscount(penalty);
//                ges.setDepth(2);
            ges.setNumPatternsToStore(50);
//                ges.setFaithfulnessAssumed(faithfulnessAssumed);
            ges.setVerbose(true);
        } else {
            throw new RuntimeException(
                    "GES does not accept this type of data input.");
        }

        ges.setInitialGraph(initialGraph);

        Graph graph = ges.search();

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (getParams().getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, getParams().getKnowledge());
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);

        if (topGraphs.isEmpty()) {
            topGraphs.add(new ScoredGraph(getResultGraph(), Double.NaN));
        }

        this.topGraphs = new ArrayList<>(ges.getTopGraphs());

        if (this.topGraphs.isEmpty()) {
            this.topGraphs.add(new ScoredGraph(getResultGraph(), Double.NaN));
        }

        this.allDagsToScores = new ArrayList<>();

        for (ScoredGraph scoredGraph : topGraphs) {
            Map<Graph, Double> dagsToScores = scoreGraphs(ges, scoredGraph.getGraph());
            this.allDagsToScores.add(dagsToScores);
        }

        setIndex(topGraphs.size() - 1);
    }

    public void setIndex(int index) {
        if (getTopGraphs().size() == 0) {
            return;
        } else {
            if (index < -1) {
                throw new IllegalArgumentException("Must be greater than or equal to -1: " + index);
            }
        }

        this.dagsToScores = this.allDagsToScores.get(index);
        this.index = index;
        firePropertyChange(new PropertyChangeEvent(this, "modelChanged", null, null));
    }

    public int getIndex() {
        return index;
    }

    private Map<Graph, Double> scoreGraphs(Fgs ges, Graph graph) {
        Map<Graph, Double> dagsToScores = new HashMap<Graph, Double>();

        if (false) {
            final List<Graph> dags = SearchGraphUtils.generatePatternDags(graph, true);

            for (Graph _graph : dags) {
                double score = ges.scoreDag(_graph);
                dagsToScores.put(_graph, score);
            }
        }

        return dagsToScores;
    }

    public Graph getGraph() {
        return getResultGraph();
//        return getTopGraphs().get(getIndex()).getGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<String>();
        names.add("Colliders");
        names.add("Noncolliders");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getCollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getNoncollidersFromGraph(node, graph));
        return triplesList;
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getParams().getKnowledge());
        return rules;
    }

    private boolean isAggressivelyPreventCycles() {
        SearchParams params = getParams();
        if (params instanceof MeekSearchParams) {
            return ((MeekSearchParams) params).isAggressivelyPreventCycles();
        }
        return false;
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

    public GraphScorer getGraphScorer() {
        return null;
    }
}





