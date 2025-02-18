package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.util.*;

public class OrderedLocalMarkovProperty {

    private OrderedLocalMarkovProperty() {}

    public static Set<IndependenceFact> getModel(Graph mag) {
        Paths paths = new Paths(mag);
        if (!paths.isLegalMag()) {
            throw new IllegalArgumentException("Input is not a legal MAG");
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
            model.add(new IndependenceFact(sink, node, mb));
        }

        Set<Node> dis_ = new HashSet<>(dis);
        for (Node node : dis) {
            dis_ = new HashSet<>(dis_);
            dis_.remove((node));

            EdgeListGraph mag_ = new EdgeListGraph(mag);
            mag_.removeNodes(new ArrayList<>(de.get(node)));
            processSink(model, de, sink, dis_, mag_);
        }
    }
}