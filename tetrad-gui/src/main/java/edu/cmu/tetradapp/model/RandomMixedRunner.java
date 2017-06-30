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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class RandomMixedRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;
    private Graph initialGraph = null;
    Set<Edge> pcAdjacent;
    Set<Edge> pcNonadjacent;
    List<Node> pcNodes;


    //============================CONSTRUCTORS============================//


    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public RandomMixedRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    public RandomMixedRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    // Starts PC from the given graph.
    public RandomMixedRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params) {
        super(dataWrapper, params, null);
        this.initialGraph = graphWrapper.getGraph();
    }

    public RandomMixedRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.initialGraph = graphWrapper.getGraph();
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public RandomMixedRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public RandomMixedRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    public RandomMixedRunner(GraphWrapper graphWrapper, KnowledgeBoxModel knowledgeBoxModel, Parameters params) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public RandomMixedRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    public RandomMixedRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    public RandomMixedRunner(GraphWrapper graphModel, IndependenceFactsModel facts, Parameters params) {
        super(graphModel.getGraph(), params, null, facts.getFacts());
    }

    public RandomMixedRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static RandomMixedRunner serializableInstance() {
        return new RandomMixedRunner(Dag.serializableInstance(), new Parameters());
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(this.isAggressivelyPreventCycles());
        rules.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
        return rules;
    }

    @Override
    public String getAlgorithmName() {
        return "PC";
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {

        DataSet ds = (DataSet) getDataModelList().get(0);

//        WGfci fges = new WGfci(ds);
//        fges.setCorrErrorsAlpha(4);
//        Graph graph = fges.search();

        WFges fges = new WFges(ds);
        fges.setPenaltyDiscount(12);
        Graph graph = fges.search();

//        WFges fges = new WFges(ds);
//        fges.setCorrErrorsAlpha(4);
//        Graph g = fges.search();
//        IndependenceTest test = new IndTestMixedLrt(ds, .001);
//        Cpc pc = new Cpc(test);
//        pc.setInitialGraph(g);
//        Graph graph = pc.search();

//        MGM m = new MGM(ds, new double[]{.1, .1, .1});
//        Graph gm = m.search();
//        IndependenceTest indTest = new IndTestMixedLrt(ds, .001);
//        Cpc pcs = new Cpc(indTest);
//        pcs.setMaxIndegree(-1);
//        pcs.setInitialGraph(gm);
//        pcs.setVerbose(false);
//        Graph graph = pcs.search();

//        WFges fges = new WFges(ds);
//        fges.setMaxIndegree(-1);
//        fges.setCorrErrorsAlpha(4);
//        Graph graph = fges.search();

//        WFges fges = new WFges(ds);
//        fges.setMaxIndegree(5);
//        fges.setCorrErrorsAlpha(8);
//        Graph g =  fges.search();
//        IndependenceTest test = new IndTestMixedLrt(ds, .001);
//        Cpc pc = new Cpc(test);
//        pc.setInitialGraph(g);
//        Graph graph = pc.search();

//        ConditionalGaussianScore score = new ConditionalGaussianScore(ds);
//        Fges fges = new Fges(score);
//        fges.setMaxIndegree(-1);
//        Graph graph = fges.search();

        GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());

        setResultGraph(graph);
    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        IndTestType testType = (IndTestType) (getParams()).get("indTestType", IndTestType.FISHER_Z);
        return new IndTestChooser().getTest(dataModel, getParams(), testType);
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
//        names.add("ColliderDiscovery");
//        names.add("Noncolliders");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
//        Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getNoncollidersFromGraph(node, graph));
        return triplesList;
    }

    public boolean supportsKnowledge() {
        return true;
    }

    @Override
    public Map<String, String> getParamSettings() {
        super.getParamSettings();
        paramSettings.put("Test", getIndependenceTest().toString());
        return paramSettings;
    }

    //========================== Private Methods ===============================//

    private boolean isAggressivelyPreventCycles() {
        Parameters params = getParams();
        if (params instanceof Parameters) {
            return params.getBoolean("aggressivelyPreventCycles", false);
        }
        return false;
    }
}






