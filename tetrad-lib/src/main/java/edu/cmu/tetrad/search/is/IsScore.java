package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Instance‑specific scoring contract used by IS‑FGES / IS‑GFCI and related algorithms.
 * <p>
 * Implementations (e.g., {@code IsBDeuScore2}, future BIC variants) compute local scores that
 * blend population fit with instance‑specific preferences. The interface keeps the historical
 * index‑based API (via {@code int[]} of variable indices) for performance and compatibility
 * with structure search.
 * </p>
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li>All arrays of indices refer to {@link #getVariables()} order.</li>
 *   <li>"IS" refers to the instance‑specific model; "POP" to the population model.</li>
 *   <li>Implementations may use structure priors to reward/penalize local changes.</li>
 * </ul>
 */
public interface IsScore {

    // ----------------------- Core local scores -----------------------

    /**
     * Local score for {@code node} given parent sets and children (population) context.
     * Implementations may include structure priors (instance‑specific component).
     *
     * @param node        index of the child variable
     * @param parentsIS   indices of the IS parent set of {@code node}
     * @param parentsPOP  indices of the population parent set of {@code node}
     * @param childrenPOP indices of the (population) children of {@code node}
     * @return local score value
     */
    double localScore(int node, int[] parentsIS, int[] parentsPOP, int[] childrenPOP);

    /**
     * Difference in local score for {@code y} when modifying the relationship w.r.t. {@code x}.
     * Useful for incremental scoring during add/delete/reorient moves.
     *
     * @param x           index of the source variable
     * @param y           index of the target variable (whose local family changes)
     * @param parentsYIS  indices of {@code y}'s IS parents
     * @param parentsYPOP indices of {@code y}'s population parents
     * @param childrenYPOP indices of {@code y}'s population children
     * @return {@code localScore_after - localScore_before}
     */
    double localScoreDiff(int x, int y, int[] parentsYIS, int[] parentsYPOP, int[] childrenYPOP);

    /**
     * Local score for {@code node} <em>without</em> using structure priors (pure likelihood/penalty).
     *
     * @param node        index of the child variable
     * @param parentsIS   indices of the IS parent set
     * @param parentsPOP  indices of the POP parent set
     * @param childrenPOP indices of the POP children set
     * @return local score value without structure prior
     */
    default double localScoreWithoutStructurePrior(int node, int[] parentsIS, int[] parentsPOP, int[] childrenPOP) {
        // Backwards compatibility with older implementations that expose {@code localScore1}.
        return localScore1(node, parentsIS, parentsPOP, childrenPOP);
    }

    /**
     * Legacy alias for {@link #localScoreWithoutStructurePrior(int, int[], int[], int[])}.
     *
     * @deprecated Use {@link #localScoreWithoutStructurePrior(int, int[], int[], int[])}.
     */
    @Deprecated
    double localScore1(int node, int[] parentsIS, int[] parentsPOP, int[] childrenPOP);

    // ----------------------- Model / data access -----------------------

    /** @return variables in the consistent index order used by the score. */
    List<Node> getVariables();

    /**
     * Resolve a variable by name.
     * @param targetName variable name
     * @return the {@link Node} with that name, or {@code null} if absent
     */
    Node getVariable(String targetName);

    /** @return sample size used internally by the score. */
    int getSampleSize();

    /** @return the dataset backing this score (for inspection/compatibility). */
    DataSet getDataSet();

    // ----------------------- Structure priors / hyper‑parameters -----------------------

    /** @return the current structure prior (implementation‑specific semantics). */
    double getStructurePrior();

    /** Set the structure prior (implementation‑specific semantics). */
    void setStructurePrior(double structurePrior);

    /** @return the current sample prior or equivalent sample size (if applicable). */
    double getSamplePrior();

    /** Set the sample prior or equivalent sample size (if applicable). */
    void setSamplePrior(double samplePrior);

    // ----------------------- Misc helpers -----------------------

    /**
     * Heuristic indicating whether the net effect on a candidate edge exceeds a threshold.
     * Implementations may interpret {@code bump} as a required score gain.
     *
     * @param bump required effect/gain threshold
     * @return true if the edge effect is considered significant
     */
    boolean isEffectEdge(double bump);

    /** @return maximum allowed degree (if constrained), else a non‑negative value or {@code Integer.MAX_VALUE}. */
    int getMaxDegree();

    /**
     * Return whether set {@code Z} deterministically determines {@code y} (e.g., due to functional constraints or fixed bins).
     * Implementations should document their criterion precisely.
     */
    boolean determines(List<Node> Z, Node y);
}
