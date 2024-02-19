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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.utils.DagScorer;
import edu.cmu.tetradapp.session.DoNotAddOldModel;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.Unmarshallable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Holds a list of graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ScoredGraphsWrapper implements SessionModel, GraphSource, Unmarshallable, DoNotAddOldModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graphs and their scores.
     */
    private final Map<Graph, Double> graphsToScores;

    /**
     * Transient graph scorer, null if non exists (or needs to be refreshed).
     */
    private final transient DagScorer graphScorer;
    /**
     * The name of the set of graphs.
     */
    private String name;
    /**
     * deprecated;
     */
    private Graph selectedGraph;

    //=============================CONSTRUCTORS==========================//

    private ScoredGraphsWrapper() {
        this.graphsToScores = null;
        this.graphScorer = null;
    }

    /**
     * <p>Constructor for ScoredGraphsWrapper.</p>
     *
     * @param graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param scorer a {@link edu.cmu.tetrad.search.utils.DagScorer} object
     */
    public ScoredGraphsWrapper(Graph graph, DagScorer scorer) {
        List<Graph> dags = GraphTransforms.generateCpdagDags(graph, true);
        this.graphsToScores = new HashMap<>();
        this.graphScorer = scorer;

        for (Graph _graph : dags) {
            double score = Double.NaN;

            if (scorer != null) {
                score = scorer.scoreDag(_graph);
            }

            this.graphsToScores.put(_graph, score);
        }

        if (!this.graphsToScores.keySet().isEmpty()) {
            /*
      The index of the selected graph.
     */
            final int index = 0;
            this.selectedGraph = this.graphsToScores.keySet().iterator().next();
        }

        log();
    }

    /**
     * <p>Constructor for ScoredGraphsWrapper.</p>
     *
     * @param wrapper    a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ScoredGraphsWrapper(DagWrapper wrapper, Parameters parameters) {
        this(wrapper.getGraph(), null);
    }

    /**
     * <p>Constructor for ScoredGraphsWrapper.</p>
     *
     * @param wrapper    a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ScoredGraphsWrapper(GraphWrapper wrapper, Parameters parameters) {
        this(wrapper.getGraph(), null);
    }

    /**
     * <p>Constructor for ScoredGraphsWrapper.</p>
     *
     * @param wrapper    a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ScoredGraphsWrapper(SemGraphWrapper wrapper, Parameters parameters) {
        this(wrapper.getGraph(), null);
    }

    /**
     * <p>Constructor for ScoredGraphsWrapper.</p>
     *
     * @param wrapper    a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ScoredGraphsWrapper(PcRunner wrapper, Parameters parameters) {
        this(wrapper.getGraph(), null);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.ScoredGraphsWrapper} object
     * @see TetradSerializableUtils
     */
    public static ScoredGraphsWrapper serializableInstance() {
        return new ScoredGraphsWrapper(PcRunner.serializableInstance(), new Parameters());
    }

    //==============================PUBLIC METHODS======================//

    /**
     * <p>Getter for the field <code>graphsToScores</code>.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<Graph, Double> getGraphsToScores() {
        Map<Graph, Double> _graphsToScores = new LinkedHashMap<>();

        for (Graph graph : this.graphsToScores.keySet()) {
            _graphsToScores.put(new EdgeListGraph(graph), this.graphsToScores.get(graph));
        }

        return _graphsToScores;
    }


    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    //==========================PRIVATE METHODS===========================//

    private void log() {
        TetradLogger.getInstance().log("info", "DAGs in forbid_latent_common_causes");
        TetradLogger.getInstance().log("selected_graph", "\nSelected Graph\n");
        TetradLogger.getInstance().log("selected_graph", getGraph() + "");

        TetradLogger.getInstance().log("all_graphs", "\nAll Graphs:\n");
        int index = 0;

        for (Graph graph : this.graphsToScores.keySet()) {
            TetradLogger.getInstance().log("all_graphs", "\nGraph #" + (++index));
            TetradLogger.getInstance().log("all_graphs", graph + "");
        }
    }


    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * May be null; a selected graph must be set.
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.selectedGraph;
    }

    /**
     * May be null; a selected graph must be set.
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSelectedGraph() {
        return this.selectedGraph;
    }

    /**
     * Sets a selected graph. Must be one of the graphs in <code>getGraphToScore().keySet</code>.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setSelectedGraph(Graph graph) {
        if (!this.graphsToScores.containsKey(graph)) {
            throw new IllegalArgumentException("Not a graph in this set.");
        }

        this.selectedGraph = graph;
    }

    /**
     * <p>Getter for the field <code>graphScorer</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.utils.DagScorer} object
     */
    public DagScorer getGraphScorer() {
        return this.graphScorer;
    }
}


