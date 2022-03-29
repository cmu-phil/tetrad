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
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.util.Parameters;
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

public class LofsRunner extends AbstractAlgorithmRunner implements
        GraphSource, PropertyChangeListener, KnowledgeBoxInput,
        DoNotAddOldModel {
    static final long serialVersionUID = 23L;
    private transient List<PropertyChangeListener> listeners;
    private Graph CPDAG;
    private Graph trueGraph = null;

// ============================CONSTRUCTORS============================//

    // public LingamCPDAGRunner(DataWrapper dataWrapper, Parameters
    // params) {
    // super(dataWrapper, params);
    // }

    public LofsRunner(final GraphWrapper graphWrapper,
                      final DataWrapper dataWrapper, final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = graphWrapper.getGraph();
    }

    public LofsRunner(final GraphWrapper graphWrapper,
                      final DataWrapper dataWrapper, final Parameters params,
                      final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.CPDAG = graphWrapper.getGraph();
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public LofsRunner(final GraphSource graphWrapper, final Parameters params,
                      final KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public LofsRunner(final GraphSource graphWrapper, final Parameters params) {
        super(graphWrapper.getGraph(), params, null);
    }

    public LofsRunner(final PcRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final PcStableRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final PcStableRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final PcRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final FasRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final FasRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final CpcRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final CpcRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final PcLocalRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final PcLocalRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final FciRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final FciRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params, final GraphWrapper graph) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
        this.trueGraph = graph.getGraph();
    }

    public LofsRunner(final CcdRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final CcdRunner2 wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final IGesRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final IGesRunner wrapper, final DataWrapper dataWrapper,
                      final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
    }

    public LofsRunner(final IGesRunner wrapper, final DataWrapper dataWrapper,
                      final GraphWrapper graphWrapper, final Parameters params) {
        super(dataWrapper, params, null);
        this.CPDAG = wrapper.getGraph();
        this.trueGraph = graphWrapper.getGraph();
    }

    public List<Node> getVariables() {
        return this.CPDAG.getNodes();
    }

    public List<String> getVariableNames() {
        return this.CPDAG.getNodeNames();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    // ============================PUBLIC METHODS==========================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */

    public void execute() {
        final DataModel source = getDataModel();
        Graph graph = null;


        if (source instanceof DataModelList) {
//            graph = lingamCPDAGEdgeVote((DataModelList) source, CPDAG);
            graph = applyLofs((DataModelList) source, this.CPDAG);
        } else {
            final DataModelList list = new DataModelList();
            list.add(source);

            if (this.CPDAG == null) {
                throw new IllegalArgumentException("Data must be specified.");
            }

            graph = applyLofs(list, this.CPDAG);
        }

        setResultGraph(graph);

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (((IKnowledge) getParams().get("knowledge", new Knowledge2())).isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, (IKnowledge) getParams().get("knowledge", new Knowledge2()));
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }


        // for (int i = 0; i < result.getDags().size(); i++) {
        // System.out.println("\n\nModel # " + (i + 1) + " # votes = " +
        // result.getCounts().get(i));
        // System.out.println(result.getDags().get(i));
        // }
    }

    private Graph lingamCPDAGEdgeVote(final DataModelList dataSets, final Graph CPDAG) {
        final List<Graph> lingamCPDAGGraphs = new ArrayList<>();

        // Images plus lingam orientation on multiple subjects.
        for (final DataModel dataModel : dataSets) {
            final DataSet dataSet = (DataSet) dataModel;
            final LingamCPDAG lingamCPDAG = new LingamCPDAG(CPDAG, dataSet);
            lingamCPDAG.setAlpha(getParams().getDouble("alpha", 0.001));
            final Graph _graph = lingamCPDAG.search();

            System.out.println(_graph);

            lingamCPDAGGraphs.add(_graph);
        }

        final Graph lingamizedGraph = new EdgeListGraph(CPDAG.getNodes());

        for (final Edge edge : CPDAG.getEdges()) {
            int numRight = 0, numLeft = 0;

            for (final Graph graph : lingamCPDAGGraphs) {
                if (graph.containsEdge(Edges.directedEdge(edge.getNode1(), edge.getNode2()))) {
                    numRight++;
                } else if (graph.containsEdge(Edges.directedEdge(edge.getNode2(), edge.getNode1()))) {
                    numLeft++;
                }
            }

            final int margin = 0;

            if (numRight > numLeft + margin) {
                lingamizedGraph.addDirectedEdge(edge.getNode1(), edge.getNode2());
            } else if (numLeft > numRight + margin) {
                lingamizedGraph.addDirectedEdge(edge.getNode2(), edge.getNode1());
            } else {
                lingamizedGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        System.out.println("lingamized graph = " + lingamizedGraph);

        return lingamizedGraph;
    }

    private Graph applyLofs(final DataModelList dataSets, final Graph CPDAG) {
        final Parameters params = getParams();
        final List<DataSet> _dataSets = new ArrayList<>();

        for (final DataModel dataModel : dataSets) {
            _dataSets.add((DataSet) dataModel);
        }

        final Lofs2 lofs = new Lofs2(CPDAG, _dataSets);
        lofs.setAlpha(getParams().getDouble("alpha", 0.001));
        lofs.setRule((Lofs2.Rule) params.get("rule", Lofs2.Rule.R3));
        lofs.setOrientStrongerDirection(params.getBoolean("orientStrongerDirection", true));
        lofs.setEdgeCorrected(params.getBoolean("meanCenterResiduals", false));
        lofs.setR2Orient2Cycles(params.getBoolean("r2Orient2Cycles", false));
        lofs.setScore((Lofs.Score) params.get("score", Lofs.Score.andersonDarling));
        lofs.setEpsilon(params.getDouble("epsilon", .1));
        lofs.setZeta(params.getDouble("zeta", 1));
        lofs.setSelfLoopStrength(params.getDouble("selfLoopStrength", 0.0));
        lofs.setKnowledge((IKnowledge) params.get("knowledge", new Knowledge2()));

        return lofs.orient();
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        final List<String> names = new ArrayList<>();
        names.add("ColliderDiscovery");
        names.add("Noncolliders");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code> for the given node.
     */
    public List<List<Triple>> getTriplesLists(final Node node) {
        final List<List<Triple>> triplesList = new ArrayList<>();
        final Graph graph = getGraph();
        triplesList.add(GraphUtils.getCollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getNoncollidersFromGraph(node, graph));
        return triplesList;
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
    public String getAlgorithmName() {
        return "LOFS";
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
        if (!getListeners().contains(l))
            getListeners().add(l);
    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        final IndTestType testType = (IndTestType) (getParams()).get("indTestType", IndTestType.FISHER_Z);
        return new IndTestChooser().getTest(dataModel, getParams(), testType);
    }


}


