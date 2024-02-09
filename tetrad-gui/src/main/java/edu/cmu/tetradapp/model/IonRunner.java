///////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.work_in_progress.Ion;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IonInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the FCI algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IonRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, DoNotAddOldModel, IonInput {
    private static final long serialVersionUID = 23L;
    private List<Graph> graphs;

    //=========================CONSTRUCTORS================================//

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag, Parameters params) {
        super(params, pag.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag7 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag7 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag8 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag7 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag8 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag9 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8,
                     IonInput pag9, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph(),
                pag9.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag7 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag8 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag9 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag10 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8,
                     IonInput pag9, IonInput pag10, Parameters params) {
        super(params, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph(),
                pag9.getGraph(), pag10.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag7 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag7 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag8 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag7 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag8 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag9 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8,
                     IonInput pag9, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph(),
                pag9.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param pag1 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag2 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag3 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag4 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag5 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag6 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag7 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag8 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag9 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param pag10 a {@link edu.cmu.tetradapp.util.IonInput} object
     * @param knowledge a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(IonInput pag1, IonInput pag2, IonInput pag3, IonInput pag4,
                     IonInput pag5, IonInput pag6, IonInput pag7, IonInput pag8,
                     IonInput pag9, IonInput pag10, KnowledgeBoxModel knowledge, Parameters params) {
        super(params, knowledge, pag1.getGraph(), pag2.getGraph(), pag3.getGraph(), pag4.getGraph(),
                pag5.getGraph(), pag6.getGraph(), pag7.getGraph(), pag8.getGraph(),
                pag9.getGraph(), pag10.getGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param fci a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(FciRunner fci, Parameters params) {
        super(params, fci.getResultGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param fci1 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param fci2 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(FciRunner fci1, FciRunner fci2, Parameters params) {
        super(params, fci1.getResultGraph(), fci2.getResultGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param fci1 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param fci2 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param fci3 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(FciRunner fci1, FciRunner fci2, FciRunner fci3, Parameters params) {
        super(params, fci1.getResultGraph(), fci2.getResultGraph(), fci3.getResultGraph());
    }

    /**
     * <p>Constructor for IonRunner.</p>
     *
     * @param fci1 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param fci2 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param fci3 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param fci4 a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IonRunner(FciRunner fci1, FciRunner fci2, FciRunner fci3, FciRunner fci4, Parameters params) {
        super(params, fci1.getResultGraph(), fci2.getResultGraph(), fci3.getResultGraph(), fci4.getResultGraph());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     * @return a {@link edu.cmu.tetradapp.model.IonRunner} object
     */
    public static IonRunner serializableInstance() {
        return new IonRunner(new GraphWrapper(new EdgeListGraph()), new Parameters());
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */
    public void execute() {
        System.out.println("Executing.");

        Ion ion = new Ion(getGraphs());

        ion.setDoAdjacencySearch(getParams().getBoolean("pruneByAdjacencies", true));
        ion.setDoPathLengthSearch(getParams().getBoolean("pruneByPathLength", true));
        ion.setKnowledge((Knowledge) getParams().get("knowledge", new Knowledge()));

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

        getParams().set("graphIndex", 0);

        this.graphs = graphs;
    }

    /**
     * <p>getIndependenceTest.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        return new IndTestChooser().getTest(dataModel, getParams());
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getResultGraph();
    }


    /**
     * <p>getTriplesClassificationTypes.</p>
     *
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new ArrayList<>();
    }

    /** {@inheritDoc} */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new ArrayList<>();
    }

    /**
     * <p>supportsKnowledge.</p>
     *
     * @return a boolean
     */
    public boolean supportsKnowledge() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String getAlgorithmName() {
        return "ION";
    }

    /**
     * <p>getStoredGraphs.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Graph> getStoredGraphs() {
        if (this.graphs == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(this.graphs);
    }
}


