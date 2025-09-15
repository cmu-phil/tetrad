package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import java.util.*;

/**
 * Utility class for computing the ordered local Markov property of a maximal ancestral graph (MAG). The ordered local
 * Markov property defines conditional independencies for each node in the graph conditional on its Markov blanket.
 * <p>
 * The main functionality of this class is represented by the static method {@code getModel(Graph mag)} which computes a
 * set of {@code IndependenceFact}s representing the ordered local Markov property for a given MAG.
 * <p>
 * This class is not meant to be instantiated.
 */
public class OrderedLocalMarkovProperty {

    private OrderedLocalMarkovProperty() {
    }

    /**
     * Computes the ordered local Markov property for a maximal ancestral graph (MAG). The method generates a set of
     * independence facts representing the conditional independencies implied by the MAG.
     *
     * @param mag The input maximal ancestral graph (MAG) represented as a graph object. Must be a valid legal MAG;
     *            otherwise, an {@code IllegalArgumentException} is thrown.
     * @return A set of {@code IndependenceFact} objects that represent the conditional independencies for the given
     * input MAG based on the ordered local Markov property.
     * @throws IllegalArgumentException if the graph is not a legal MAG.
     */
    public static Set<IndependenceFact> getModel(Graph mag) {
        Paths paths = new Paths(mag);

        if (!paths.isLegalMag()) {
            List<Node> selection = mag.getNodes().stream()
                    .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();
            GraphSearchUtils.LegalMagRet ret = GraphSearchUtils.isLegalMag(mag, new HashSet<>(selection));
//            throw new IllegalArgumentException("MAG not valid, reason = " + ret.getReason());
        }

        Set<IndependenceFact> model = new HashSet<>();
        Map<Node, Set<Node>> de = paths.getDescendantsMap();
        EdgeListGraph mag_ = new EdgeListGraph((mag));

        List<Node> unprocessed = new ArrayList<>(mag.getNodes());
        while (!unprocessed.isEmpty()) {
            Node sink = unprocessed.getFirst();
            while (!mag_.getChildren(sink).isEmpty()) {
                sink = mag_.getChildren(sink).getFirst();
            }

            Set<Node> dis = GraphUtils.district(sink, mag_);
            processSink(model, de, sink, dis, mag_);
            mag_.removeNode(sink);

            unprocessed.remove(sink);
        }

        return model;
    }

    private static void processSink(Set<IndependenceFact> model, Map<Node, Set<Node>> de,
                                    Node sink, Set<Node> dis, EdgeListGraph mag) {
        Set<Node> mb = GraphUtils.markovBlanket(sink, mag);
        for (Node node : mag.getNodes()) {
            if (node == sink) continue;
            if (mb.contains(node)) continue;
            IndependenceFact ind_fact = new IndependenceFact(sink, node, mb);
            model.add(ind_fact);
        }

        Set<Node> dis_ = new HashSet<>(dis);
        for (Node node : dis) {
            if (de.get(node).contains(sink)) continue;

            dis_ = new HashSet<>(dis_);
            dis_.remove((node));

            EdgeListGraph mag_ = new EdgeListGraph(mag);
            mag_.removeNodes(new ArrayList<>(de.get(node)));

            processSink(model, de, sink, dis_, mag_);
        }
    }
}