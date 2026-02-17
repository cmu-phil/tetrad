package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

public class IndependenceFactsDslModel implements SessionModel {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DataModel dataModel;
    private final Graph graph;
    private final Parameters parameters;

    // ✅ do NOT try to persist caches across sessions
    private transient CachedIndependenceQueries cachedQueriesOrNull;

    private String name = "";

    // ✅ these *are* serializable; good
    private final Map<String, String> editorStateString = new HashMap<>();
    private final Map<String, Integer> editorStateInt = new HashMap<>();

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

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        if (name == null) throw new IllegalArgumentException("name cannot be null");
        this.name = name;
    }

    public CachedIndependenceQueries getCachedQueriesOrNull() {
        return cachedQueriesOrNull;
    }

    // Optional: rebuild cache lazily if you want
    public void setCachedQueriesOrNull(CachedIndependenceQueries q) {
        this.cachedQueriesOrNull = q;
    }

    public String getEditorStateString(String key) {
        return editorStateString.get(key);
    }

    public int getEditorStateInt(String key, int defaultValue) {
        Integer v = editorStateInt.get(key);
        return (v == null) ? defaultValue : v;
    }

    public void setEditorStateString(String key, String value) {
            editorStateString.put(key, value);
    }

    public void setEditorStateInt(String key, int value) {
        editorStateInt.put(key, value);
    }
}
