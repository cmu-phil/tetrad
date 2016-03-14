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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.IonJoeModifications;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IonInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the FCI algorithm.
 *
 * @author Joseph Ramsey
 */
public class IonRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource, DoNotAddOldModel, IonInput {
    static final long serialVersionUID = 23L;
    private List<Graph> graphs;
                                                                                         
    //=========================CONSTRUCTORS================================//

    public IonRunner(IonInput pag, IonParams params) {
        super(params, pag.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8,
                     IonInput pag9, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph(),
                pag9.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8,
                     IonInput pag9, IonInput pag10, IonParams params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph(),
                pag9.getGraph(), pag10.getGraph());
    }

    public IonRunner(IonInput pag, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8,
                     IonInput pag9, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph(),
                pag9.getGraph());
    }

    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8,
                     IonInput pag9, IonInput pag10, KnowledgeBoxModel knowledge, IonParams params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph(),
                pag9.getGraph(), pag10.getGraph());
    }
    public IonRunner(FciRunner fci, IonParams params) {
        super(params, fci.getResultGraph());
    }

    public IonRunner(FciRunner fci1, FciRunner fci2, IonParams params) {
        super(params, fci1.getResultGraph(), fci2.getResultGraph());
    }

    public IonRunner(FciRunner fci1, FciRunner fci2, FciRunner fci3, IonParams params) {
        super(params, fci1.getResultGraph(), fci2.getResultGraph(), fci3.getResultGraph());
    }

    public IonRunner(FciRunner fci1, FciRunner fci2, FciRunner fci3, FciRunner fci4, IonParams params) {
        super(params, fci1.getResultGraph(), fci2.getResultGraph(), fci3.getResultGraph(), fci4.getResultGraph());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static IonRunner serializableInstance() {
        return new IonRunner(new GraphWrapper(new EdgeListGraph()), new IonParams());
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        System.out.println("Executing.");

        IonJoeModifications ion = new IonJoeModifications(getGraphs());

        ion.setAdjacencySearch(((IonParams) getParams()).isPruneByAdjacencies());
        ion.setPathLengthSearch(((IonParams) getParams()).isPruneByPathLength());
        ion.setKnowledge(getParams().getKnowledge());
        
        List<Graph> graphs = ion.search();

        Collections.sort(graphs, new Comparator<Graph>() {
            public int compare(Graph graph, Graph graph1) {
                return graph.getNumEdges() - graph1.getNumEdges();
            }
        });

        if (graphs == null) {
            throw new NullPointerException();
        }

        if (!graphs.isEmpty()) {
            setResultGraph(graphs.get(0));
        }

        System.out.println("graphs = " + graphs);

        ((IonParams) getParams()).setGraphIndex(0);

        this.graphs = graphs;
    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        SearchParams params = getParams();
        IndTestType testType;

        if (getParams() instanceof BasicSearchParams) {
            BasicSearchParams _params = (BasicSearchParams) params;
            testType = _params.getIndTestType();
        } else {
            FciSearchParams _params = (FciSearchParams) params;
            testType = _params.getIndTestType();
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
        List<String> names = new ArrayList<String>();
//        names.add("Definite Colliders");
//        names.add("Definite Noncolliders");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
//        triplesList.add(DataGraphUtils.getDefiniteCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getDefiniteNoncollidersFromGraph(node, graph));
        return triplesList;
    }

    public boolean supportsKnowledge() {
        return true;
    }

    @Override
    public String getAlgorithmName() {
        return "ION";
    }

    public List<Graph> getStoredGraphs() {
        if (graphs == null) {
            return new ArrayList<Graph>();
        }

        return new ArrayList<Graph>(graphs);
    }
}


