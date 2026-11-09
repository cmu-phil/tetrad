package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * A session model for the independence facts DSL editor. It holds the selected data model, an optional graph
 * (enabling m-separation queries), the parameters, an optional transient cache of independence queries, and
 * persisted editor state.
 */
public class IndependenceFactsDslModel implements SessionModel {

    /**
     * The serial version UID.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The data model on which statistical tests are run.
     */
    private final DataModel dataModel;
    /**
     * The graph used for m-separation queries; may be null.
     */
    private final Graph graph;
    /**
     * The parameters for the model.
     */
    private final Parameters parameters;

    // ✅ do NOT try to persist caches across sessions
    /**
     * An optional cache of independence queries; transient, so not persisted across sessions. May be null.
     */
    private transient CachedIndependenceQueries cachedQueriesOrNull;

    /**
     * The name of this model.
     */
    private String name = "";

    // ✅ these *are* serializable; good
    /**
     * Persisted string-valued editor state, keyed by name.
     */
    private final Map<String, String> editorStateString = new HashMap<>();
    /**
     * Persisted integer-valued editor state, keyed by name.
     */
    private final Map<String, Integer> editorStateInt = new HashMap<>();

    /**
     * Minimal: data-only. This supports statistical tests only.
     *
     * @param dataModel  the data wrapper; its selected data model is used.
     * @param parameters the parameters.
     */
    public IndependenceFactsDslModel(DataWrapper dataModel, Parameters parameters) {
        this(dataModel, null, parameters, null);
    }

    /**
     * Data + graph: enables both statistical tests and m-separation.
     *
     * @param dataModel  the data wrapper; its selected data model is used.
     * @param graph      the graph source used for m-separation.
     * @param parameters the parameters.
     */
    public IndependenceFactsDslModel(DataWrapper dataModel, GraphSource graph, Parameters parameters) {
        this(dataModel, graph, parameters, null);
    }

    /**
     * Constructs a new model from data, an optional graph, parameters, and an optional cache of independence
     * queries.
     *
     * @param dataModel           the data wrapper; its selected data model is used.
     * @param graph               the graph source, or null if m-separation is not needed.
     * @param parameters          the parameters.
     * @param cachedQueriesOrNull a cache of independence queries, or null.
     */
    public IndependenceFactsDslModel(DataWrapper dataModel,
                                     GraphSource graph,
                                     Parameters parameters,
                                     CachedIndependenceQueries cachedQueriesOrNull) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.graph = graph == null ? null : graph.getGraph();
        this.parameters = parameters;
        this.cachedQueriesOrNull = cachedQueriesOrNull;
    }

    /**
     * Returns the data model.
     *
     * @return the data model.
     */
    public DataModel getDataModel() {
        return dataModel;
    }

    /**
     * Returns the graph used for m-separation queries.
     *
     * @return the graph, or null if none was supplied.
     */
    public Graph getGraph() {
        return graph;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters.
     */
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

    /**
     * Returns the cache of independence queries.
     *
     * @return the cache, or null if none is set.
     */
    public CachedIndependenceQueries getCachedQueriesOrNull() {
        return cachedQueriesOrNull;
    }

    // Optional: rebuild cache lazily if you want
    /**
     * Sets the cache of independence queries.
     *
     * @param q the cache, or null.
     */
    public void setCachedQueriesOrNull(CachedIndependenceQueries q) {
        this.cachedQueriesOrNull = q;
    }

    /**
     * Returns the persisted string-valued editor state for the given key.
     *
     * @param key the key.
     * @return the stored value, or null if there is none.
     */
    public String getEditorStateString(String key) {
        return editorStateString.get(key);
    }

    /**
     * Returns the persisted integer-valued editor state for the given key.
     *
     * @param key          the key.
     * @param defaultValue the value to return if nothing is stored for the key.
     * @return the stored value, or the default value if there is none.
     */
    public int getEditorStateInt(String key, int defaultValue) {
        Integer v = editorStateInt.get(key);
        return (v == null) ? defaultValue : v;
    }

    /**
     * Stores a string-valued editor state value under the given key.
     *
     * @param key   the key.
     * @param value the value.
     */
    public void setEditorStateString(String key, String value) {
            editorStateString.put(key, value);
    }

    /**
     * Stores an integer-valued editor state value under the given key.
     *
     * @param key   the key.
     * @param value the value.
     */
    public void setEditorStateInt(String key, int value) {
        editorStateInt.put(key, value);
    }
}
