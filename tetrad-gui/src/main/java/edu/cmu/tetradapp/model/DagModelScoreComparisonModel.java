package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Editor model for comparing DAG model scores across multiple graphs (columns),
 * given exactly one dataset (rows = metrics).
 *
 * Reflection constructor pattern: (SessionModel[] inputs, Parameters params).
 */
public final class DagModelScoreComparisonModel implements SessionModel {

    private final DataSet data;
    private final List<NamedGraph> graphs;
    private final Parameters parameters;
    private String name;

    public DagModelScoreComparisonModel(SessionModel[] inputs, Parameters parameters) {
        if (inputs == null || inputs.length == 0) {
            throw new IllegalArgumentException("DagModelScoreComparisonModel: no inputs were provided.");
        }
        this.parameters = (parameters == null) ? new Parameters() : parameters;

        DataWrapper dw = null;
        List<GraphWrapper> gws = new ArrayList<>();

        for (SessionModel sm : inputs) {
            if (sm == null) continue;
            if (sm instanceof DataWrapper) {
                if (dw != null) {
                    throw new IllegalArgumentException(
                            "Dag Model Score Comparison requires exactly ONE dataset input, but found more than one DataWrapper.");
                }
                dw = (DataWrapper) sm;
            } else if (sm instanceof GraphWrapper) {
                gws.add((GraphWrapper) sm);
            }
        }

        if (dw == null) {
            throw new IllegalArgumentException(
                    "Dag Model Score Comparison requires a dataset input (DataWrapper), but none was provided.");
        }
        if (gws.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dag Model Score Comparison requires at least one graph input (GraphWrapper), but none was provided.");
        }

        DataSet ds = (DataSet) dw.getSelectedDataModel();
        if (ds == null) {
            throw new IllegalArgumentException("Provided DataWrapper does not contain a DataSet.");
        }
        this.data = ds;

        List<NamedGraph> named = new ArrayList<>();
        for (GraphWrapper gw : gws) {
            Graph g = gw.getGraph();
            if (g == null) continue;

            // Name for table header.
            String name = gw.getName();
            if (name == null || name.trim().isEmpty()) {
                // Fallback: use model class name or default label.
                name = "Graph";
            }

            named.add(new NamedGraph(name, g));
        }

        if (named.isEmpty()) {
            throw new IllegalArgumentException("No usable graphs found in provided GraphWrapper inputs.");
        }

        this.graphs = Collections.unmodifiableList(named);
    }

    public DataSet getData() {
        return data;
    }

    public List<NamedGraph> getGraphs() {
        return graphs;
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
        if (name == null) name = "";
        this.name = name;
    }

    /** Simple name+graph pair for column headers. */
    public static final class NamedGraph implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        private final String name;
        private final Graph graph;

        public NamedGraph(String name, Graph graph) {
            this.name = name;
            this.graph = graph;
        }

        public String getName() {
            return name;
        }

        public Graph getGraph() {
            return graph;
        }
    }

    // SessionModel plumbing (if your SessionModel requires name, etc., add it here).
}