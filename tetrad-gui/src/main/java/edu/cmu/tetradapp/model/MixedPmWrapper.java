// TODO: adjust the package to match where BayesPmWrapper lives (often: edu.cmu.tetradapp.model)
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Wrapper for a Mixed Parameter Model (MixedPm).
 * Mirrors BayesPmWrapper responsibilities:
 *  - Holds the underlying MixedPm (structure + variable typing/domains).
 *  - Exposes a Graph view for editors.
 *  - Provides deep copy & simple metadata (name/notes).
 *
 * Drop-in notes:
 *  - Replace the import/type for MixedPm with your actual class.
 *  - If BayesPmWrapper implements extra interfaces in your GUI (e.g., SessionModel),
 *    add them here as well.
 */
public class MixedPmWrapper implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    // TODO: replace with the actual MixedPm type import
    // e.g., import edu.cmu.tetrad.sem.MixedPm; or edu.cmu.tetrad.mixed.MixedPm;
    public static class MixedPm implements Serializable, Cloneable {
        @Serial private static final long serialVersionUID = 1L;
        private final Graph graph;

        public MixedPm(Graph graph) {
            this.graph = Objects.requireNonNull(graph, "graph");
        }

        public Graph getGraph() { return graph; }

        @Override
        public MixedPm clone() {
            try { return (MixedPm) super.clone(); }
            catch (CloneNotSupportedException e) { throw new AssertionError(e); }
        }
    }

    private final MixedPm mixedPm;
    private String name = "Mixed PM";
    private String notes = "";

    /** Construct from an existing MixedPm. */
    public MixedPmWrapper(MixedPm mixedPm) {
        this.mixedPm = Objects.requireNonNull(mixedPm, "mixedPm");
    }

    /** Convenience: build a new MixedPm from a graph. */
    public MixedPmWrapper(Graph graph) {
        this(new MixedPm(Objects.requireNonNull(graph, "graph")));
    }

    /** Underlying parameter model. */
    public MixedPm getMixedPm() { return mixedPm; }

    /** The structural graph associated with the parameter model. */
    public Graph getGraph() { return mixedPm.getGraph(); }

    /** Display name for the editor panes / tabs. */
    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNullElse(name, "Mixed PM"); }

    /** Free-form notes for the project/session. */
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = (notes == null ? "" : notes); }

    /** Deep copy wrapper + model (adjust if your MixedPm has a dedicated copy ctor). */
    public MixedPmWrapper deepCopy() {
        MixedPm pmCopy = mixedPm.clone(); // replace if you have MixedPm(MixedPm other)
        MixedPmWrapper w = new MixedPmWrapper(pmCopy);
        w.name = this.name;
        w.notes = this.notes;
        return w;
    }

    @Override
    public MixedPmWrapper clone() {
        return deepCopy();
    }

    @Override
    public String toString() {
        return "MixedPmWrapper{" + "name='" + name + '\'' + ", graph=" + getGraph() + '}';
    }
}