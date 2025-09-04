// TODO: adjust the package to match where BayesImWrapper lives (often: edu.cmu.tetradapp.model)
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Wrapper for a Mixed Instantiated Model (MixedIm).
 * Mirrors BayesImWrapper responsibilities:
 *  - Holds the instantiated parameters for the MixedPm (CPDs / CG params, etc.).
 *  - Provides cloning & light metadata for GUI editors.
 *
 * Drop-in notes:
 *  - Replace the MixedIm type with your actual class.
 *  - Add extra interfaces (e.g., SessionModel) if BayesImWrapper does so in your GUI.
 *  - Expose additional accessors that your table editors need (e.g., getters/setters for
 *    cell values, parameter blocks, constraints, etc.).
 */
public class MixedImWrapper implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

//    // TODO: replace with your actual MixedIm & MixedPm types/imports.
//    public static class MixedPm implements Serializable, Cloneable {
//        @Serial private static final long serialVersionUID = 1L;
//        private final Graph graph;
//        public MixedPm(Graph graph) { this.graph = Objects.requireNonNull(graph, "graph"); }
//        public Graph getGraph() { return graph; }
//        @Override public MixedPm clone() { try { return (MixedPm) super.clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); } }
//    }

    public static class MixedIm implements Serializable, Cloneable {
        @Serial private static final long serialVersionUID = 1L;
        private final MixedPmWrapper.MixedPm mixedPm;

        public MixedIm(MixedPmWrapper.MixedPm mixedPm) {
            this.mixedPm = Objects.requireNonNull(mixedPm, "mixedPm");
        }

        /** Parameter model backing this instantiation. */
        public MixedPmWrapper.MixedPm getMixedPm() { return mixedPm; }

        @Override
        public MixedIm clone() {
            try {
                MixedIm copy = (MixedIm) super.clone();
                // If MixedPm is mutable, deep copy it; otherwise keep reference.
                // Replace as needed:
                return new MixedIm(mixedPm.clone());
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private final MixedIm mixedIm;
    private String name = "Mixed IM";
    private String notes = "";

    /** Construct from an existing MixedIm. */
    public MixedImWrapper(MixedIm mixedIm) {
        this.mixedIm = Objects.requireNonNull(mixedIm, "mixedIm");
    }

    /** Convenience: build a new MixedIm from a MixedPm. */
    public MixedImWrapper(MixedPmWrapper mixedPmWrapper) {
        this(new MixedIm(mixedPmWrapper.getMixedPm())); // Replace with your real MixedIm ctor
    }

    /** Underlying instantiated model. */
    public MixedIm getMixedIm() { return mixedIm; }

    /** Access the parameter model behind this instantiation. */
    public MixedPmWrapper.MixedPm getMixedPm() { return mixedIm.getMixedPm(); }

    /** Graph for editors. */
    public Graph getGraph() { return getMixedPm().getGraph(); }

    /** Display name for the editor panes / tabs. */
    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNullElse(name, "Mixed IM"); }

    /** Free-form notes for the project/session. */
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = (notes == null ? "" : notes); }

    /** Deep copy wrapper + model (adjust if your MixedIm has a dedicated copy ctor). */
    public MixedImWrapper deepCopy() {
        MixedIm imCopy = mixedIm.clone(); // replace if you have MixedIm(MixedIm other)
        MixedImWrapper w = new MixedImWrapper(imCopy);
        w.name = this.name;
        w.notes = this.notes;
        return w;
    }

    @Override
    public MixedImWrapper clone() {
        return deepCopy();
    }

    @Override
    public String toString() {
        return "MixedImWrapper{" + "name='" + name + '\'' + ", graph=" + getGraph() + '}';
    }

    // ------------------- Editor-facing helpers (add as needed) -------------------
    // Examples of methods your MixedImEditor table might call:
    //
    // public int getBlockCount() { ... }
    // public String getBlockName(int b) { ... }
    // public int getRowCount(int b) { ... }
    // public int getColCount(int b) { ... }
    // public Object getValue(int b, int r, int c) { ... }
    // public void   setValue(int b, int r, int c, Object value) { ... }
    //
    // Keep these wrappers thin; all nontrivial logic should live in MixedIm/MixedPm.
}