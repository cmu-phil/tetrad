package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.session.SessionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * v1: Editor model for estimating adjustment effects given:
 *  - exactly one DataWrapper (continuous outcome + mixed covariates allowed)
 *  - one or more GraphWrappers (CPDAG/PAG/DAG, used for node lists + optional heuristics)
 *
 * v1: Does not (yet) compute adjustment sets from CPDAG/PAG automatically.
 */
public final class DoublyRobustEstModelV1 implements SessionModel {

    private final DataSet dataSet;
    private final List<Graph> graphs;
    private final List<String> graphNames;
    private final Parameters parameters;
    private String name = "Adjustment Effect (v1)";

    public DoublyRobustEstModelV1(SessionModel[] inputs, Parameters parameters) {
        Objects.requireNonNull(inputs, "v1: inputs");
        this.parameters = Objects.requireNonNull(parameters, "v1: parameters");

        DataSet ds = null;
        List<Graph> gs = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (SessionModel m : inputs) {
            if (m == null) continue;

            // v1: DataWrapper detection
            if (m instanceof edu.cmu.tetradapp.model.DataWrapper dw) {
                DataModel dm = dw.getSelectedDataModel();
                if (!(dm instanceof DataSet d)) {
                    throw new IllegalArgumentException("v1: DataWrapper must contain a DataSet.");
                }
                if (ds != null) {
                    throw new IllegalArgumentException("v1: Please provide exactly one DataWrapper.");
                }
                ds = d;
            }

            // v1: GraphWrapper detection
            if (m instanceof edu.cmu.tetradapp.model.GraphWrapper gw) {
                Graph g = gw.getGraph();
                if (g != null) {
                    gs.add(g);
                    names.add(gw.getName() == null ? ("Graph " + gs.size()) : gw.getName());
                }
            }
        }

        if (ds == null) {
            throw new IllegalArgumentException("v1: Adjustment Effect requires one DataWrapper input.");
        }
        if (gs.isEmpty()) {
            throw new IllegalArgumentException("v1: Adjustment Effect requires one or more GraphWrapper inputs.");
        }

        this.dataSet = ds;
        this.graphs = List.copyOf(gs);
        this.graphNames = List.copyOf(names);
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public List<Graph> getGraphs() {
        return graphs;
    }

    public List<String> getGraphNames() {
        return graphNames;
    }

    public Parameters getParameters() {
        return parameters;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}