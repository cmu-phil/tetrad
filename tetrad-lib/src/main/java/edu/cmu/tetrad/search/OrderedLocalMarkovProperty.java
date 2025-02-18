package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.util.*;

public class OrderedLocalMarkovProperty {

    private OrderedLocalMarkovProperty() {}

    public static Set<IndependenceFact> getModel(Graph mag) {
        // there should probably be a check to ensure the input is a mag
        Set<IndependenceFact> model = new HashSet<>();

        Paths paths = new Paths(mag);
        Map<Node, Set<Node>> de = paths.getDescendantsMap();

        List<Node> unprocessed = new ArrayList<>(mag.getNodes());
        EdgeListGraph mag_ = new EdgeListGraph((mag));

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
            model.add(new IndependenceFact(sink, node, mb));
        }

        // verify moving this outside the loop is correct
        Set<Node> dis_ = new HashSet<>(dis);
        for (Node node : dis) {
            // Set<Node> dis_ = new HashSet<>(dis);
            dis_.remove((node));

            EdgeListGraph mag_ = new EdgeListGraph(mag);
            mag_.removeNodes(new ArrayList<>(de.get(node)));

            processSink(model, de, sink, dis_, mag_);
        }
    }
}