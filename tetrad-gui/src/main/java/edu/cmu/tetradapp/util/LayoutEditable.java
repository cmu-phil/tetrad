package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.awt.*;
import java.util.Map;


/**
 * Interface to indicate a class that has a graph in it that can be laid out.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface LayoutEditable {

    /**
     * <p>getGraph.</p>
     *
     * @return the getModel graph. (Not necessarily a copy.)
     */
    Graph getGraph();

    /**
     * The display nodes.
     *
     * @return a {@link java.util.Map} object
     */
    Map<Edge, Object> getModelEdgesToDisplay();

    /**
     * <p>getModelNodesToDisplay.</p>
     *
     * @return a {@link java.util.Map} object
     */
    Map<Node, Object> getModelNodesToDisplay();

    /**
     * <p>getKnowledge.</p>
     *
     * @return the getModel knowledge.
     */
    Knowledge getKnowledge();

    /**
     * <p>getSourceGraph.</p>
     *
     * @return the source graph.
     */
    Graph getSourceGraph();

    /**
     * Sets the graph according to which the given graph should be laid out.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    void layoutByGraph(Graph graph);

    /**
     * f Lays out the graph in tiers according to knowledge.
     */
    void layoutByKnowledge();

    /**
     * <p>getVisibleRect.</p>
     *
     * @return the preferred size of the layout.
     */
    Rectangle getVisibleRect();
}





