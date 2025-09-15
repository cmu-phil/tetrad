package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.Graph;

import java.util.ArrayList;
import java.util.List;

public final class GraphResultList {
    private final List<Graph> graphs = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    public void add(Graph g, String name) {
        graphs.add(g);
        names.add(name);
    }

    public List<Graph> getGraphs() {
        return graphs;
    }

    public List<String> getNames() {
        return names;
    }

    public int size() {
        return graphs.size();
    }

    public Graph get(int i) {
        return graphs.get(i);
    }

    public String getName(int i) {
        return names.get(i);
    }
}