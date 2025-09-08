package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.*;

/**
 * Wrapper for a Hybrid Conditional Gaussian Parameter Model (HybridCgPm). Mirrors BayesPmWrapper responsibilities: -
 * Holds the underlying PM (structure + variable typing/domains). - Exposes a Graph view for editors. - Provides simple
 * metadata (name/notes).
 * <p>
 * NOTE: This wrapper does not modify the core HybridCgModel classes.
 */
public class HybridCgPmWrapper implements SessionModel, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private HybridCgModel.HybridCgPm hybridCgPm;
    private String name = "Hybrid CG PM";
    private String notes = "";

//    /** No-arg ctor for reflection/persistence; set fields later as needed. */
//    public HybridCgPmWrapper() {
//        this.hybridCgPm = null;  // caller must set or use a ctor that builds the PM
//    }
//
//    /** Convenience: build a new PM from a graph with default parameters. */
//    public HybridCgPmWrapper(Graph graph) {
//        this(graph, new Parameters());
//    }

    public HybridCgPmWrapper(GraphWrapper graph, Parameters parameters) {
        this(graph.getGraph(), parameters);
    }

    /**
     * Build a new PM from a graph, honoring variable types inferred from DiscreteVariable instances, and initializing
     * simple placeholder cutpoints for discrete children with continuous parents.
     */
    public HybridCgPmWrapper(Graph graph, Parameters params) {
        Objects.requireNonNull(params, "params");           // keep signature aligned with BayesPmWrapper
        Graph g = Objects.requireNonNull(graph, "graph");

        final List<Node> nodeOrder = new ArrayList<>(g.getNodes());

        // Tell the PM which variables are discrete and their categories (order matters!)
        Map<Node, Boolean> isDisc = new LinkedHashMap<>();
        Map<Node, List<String>> cats = new LinkedHashMap<>();

        for (Node v : nodeOrder) {
            boolean discrete = v instanceof DiscreteVariable;
            isDisc.put(v, discrete);
            if (discrete) cats.put(v, ((DiscreteVariable) v).getCategories());
        }

        // Construct PM
        HybridCgModel.HybridCgPm pm = new HybridCgModel.HybridCgPm(g, nodeOrder, isDisc, cats);

        // Initialize placeholder cutpoints for discrete children that have continuous parents.
        // (Editor can later override via setCutpointsForDiscreteChild.)
        for (Node child : nodeOrder) {
            int y = pm.indexOf(child);
            if (!pm.isDiscrete(y)) continue;
            int[] cParents = pm.getContinuousParents(y);
            if (cParents.length == 0) continue;

            Map<Node, double[]> cutpoints = new LinkedHashMap<>();
            for (int idx : cParents) {
                Node cp = pm.getNodes()[idx];
                // Placeholder: 3 bins using two cutpoints [-0.5, 0.5]
                // Replace/override in the editor as needed.
                cutpoints.put(cp, new double[]{-0.5, 0.5});
            }
            pm.setContParentCutpointsForDiscreteChild(child, cutpoints);
        }

        // IMPORTANT: keep the same PM instance we configured (don't reconstruct and lose cutpoints)
        this.hybridCgPm = pm;

        // Optional display name
        String label = params.getString("modelName", null);
        if (label != null && !label.isBlank()) {
            this.name = label;
        }
    }

//    /** Construct from an existing PM. */
//    public HybridCgPmWrapper(HybridCgModel.HybridCgPm hybridCgPm) {
//        this.hybridCgPm = Objects.requireNonNull(hybridCgPm, "hybridCgPm");
//    }

    /**
     * Underlying parameter model.
     */
    public HybridCgModel.HybridCgPm getHybridCgPm() {
        return hybridCgPm;
    }

    /**
     * Optional: setter for reflection cases that build, then inject.
     */
    public void setHybridCgPm(HybridCgModel.HybridCgPm pm) {
        this.hybridCgPm = Objects.requireNonNull(pm, "pm");
    }

    /**
     * Structural graph associated with the parameter model.
     */
    public Graph getGraph() {
        return hybridCgPm != null ? hybridCgPm.getGraph() : null;
    }

    /**
     * Display name for the editor panes / tabs.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Hybrid CG PM" : name;
    }

    /**
     * Free-form notes for the project/session.
     */
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = (notes == null ? "" : notes);
    }

    /**
     * Convenience passthrough to set cutpoints for a discrete child that has continuous parents.
     */
    public void setCutpointsForDiscreteChild(Node child, Map<Node, double[]> cutpointsByContParent) {
        Objects.requireNonNull(hybridCgPm, "PM not set");
        hybridCgPm.setContParentCutpointsForDiscreteChild(child, cutpointsByContParent);
    }

    @Override
    public HybridCgPmWrapper clone() {
        try {
            return (HybridCgPmWrapper) super.clone(); // shallow clone of wrapper; PM kept as-is
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return "HybridCgPmWrapper{" + "name='" + name + '\'' + ", graph=" + getGraph() + '}';
    }
}