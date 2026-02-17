package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;

public class IndependenceFactsDslModel implements SessionModel {


    private final DataModel dataModel;
    private final Graph graph;
    private final Parameters parameters;
    private final CachedIndependenceQueries cachedQueriesOrNull;
    private String name = "";

    /**
     * Minimal: data-only. This supports statistical tests only.
     */
    public IndependenceFactsDslModel(DataWrapper dataModel, Parameters parameters) {
        this(dataModel, null, parameters, null);
    }

    /**
     * Data + graph: enables both statistical tests and m-separation.
     */
    public IndependenceFactsDslModel(DataWrapper dataModel, GraphSource graph, Parameters parameters) {
        this(dataModel, graph, parameters, null);
    }

    public IndependenceFactsDslModel(DataWrapper dataModel,
                                     GraphSource graph,
                                     Parameters parameters,
                                     CachedIndependenceQueries cachedQueriesOrNull) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.graph = graph == null ? null : graph.getGraph();
        this.parameters = parameters;
        this.cachedQueriesOrNull = cachedQueriesOrNull;
    }

    public DataModel getDataModel() {
        return dataModel;
    }

    public Graph getGraph() {
        return graph;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public CachedIndependenceQueries getCachedQueriesOrNull() {
        return cachedQueriesOrNull;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        if (name == null) throw new IllegalArgumentException("name cannot be null");
        this.name = name;
    }
}
