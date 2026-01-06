package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Instance‑specific scoring contract used by IS‑FGES / IS‑GFCI and related algorithms.
 * <p>
 * Implementations (e.g., {@code IsBDeuScore2}, future BIC variants) compute local scores that blend population fit with
 * instance‑specific preferences. The interface keeps the historical index‑based API (via {@code int[]} of variable
 * indices) for performance and compatibility with structure search.
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
     * Local score for {@code node} given parent sets and children (population) context. Implementations may include
     * structure priors (instance‑specific component).
     *
     * @param node        index of the child variable
     * @param parentsIS   indices of the IS parent set of {@code node}
     * @param parentsPOP  indices of the population parent set of {@code node}
     * @param childrenPOP indices of the (population) children of {@code node}
     * @return local score value
     */
    double localScore(int node, int[] parentsIS, int[] parentsPOP, int[] childrenPOP);

    /**
     * Difference in local score for {@code y} when modifying the relationship w.r.t. {@code x}. Useful for incremental
     * scoring during add/delete/reorient moves.
     *
     * @param x            index of the source variable
     * @param y            index of the target variable (whose local family changes)
     * @param parentsYIS   indices of {@code y}'s IS parents
     * @param parentsYPOP  indices of {@code y}'s population parents
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
     * Deprecated method for calculating the local score of a node given specific parent
     * sets and child (population) context.
     *
     * @param node        the index of the target node whose score is to be calculated
     * @param parentsIS   an array containing the indices of the IS (instance-specific) parent set
     * @param parentsPOP  an array containing the indices of the population parent set
     * @param childrenPOP an array containing the indices of the population children
     * @return the calculated local score as a double
     */
    @Deprecated
    double localScore1(int node, int[] parentsIS, int[] parentsPOP, int[] childrenPOP);

    // ----------------------- Model / data access -----------------------

    /**
     * Retrieves the list of variables (nodes) associated with the current implementation.
     *
     * @return a list of {@code Node} objects representing the variables.
     */
    List<Node> getVariables();

    /**
     * Resolve a variable by name.
     *
     * @param targetName variable name
     * @return the {@link Node} with that name, or {@code null} if absent
     */
    Node getVariable(String targetName);

    /**
     * Retrieves the sample size used in the context of the implementation.
     * The sample size typically refers to the number of instances or observations
     * involved in scoring or inference processes.
     *
     * @return the sample size as an integer
     */
    int getSampleSize();

    /**
     * Retrieves the dataset associated with the scoring or inference process.
     * The dataset typically represents the underlying data that is being used for
     * computations or model evaluations.
     *
     * @return the associated {@code DataSet} object
     */
    DataSet getDataSet();

    // ----------------------- Structure priors / hyper‑parameters -----------------------

    /**
     * Retrieves the structure prior, which is an implementation-specific component
     * used to influence the local score calculation.
     *
     * @return the current structure prior as a double
     */
    double getStructurePrior();

    /**
     * Sets the structure prior used to influence the scoring calculations.
     *
     * @param structurePrior the structure prior value to be used in the scoring process
     */
    void setStructurePrior(double structurePrior);

    /**
     * Retrieves the sample prior or equivalent sample size used in the scoring process.
     * This value typically influences the balance between data and structural assumptions in the model.
     *
     * @return the current sample prior as a double
     */
    double getSamplePrior();

    /**
     * Sets the sample prior or equivalent sample size used in the scoring process.
     * This value typically balances the influence of data and structural assumptions in the model.
     *
     * @param samplePrior the sample prior value to be used in the scoring process
     */
    void setSamplePrior(double samplePrior);

    // ----------------------- Misc helpers -----------------------

    /**
     * Heuristic indicating whether the net effect on a candidate edge exceeds a threshold. Implementations may
     * interpret {@code bump} as a required score gain.
     *
     * @param bump required effect/gain threshold
     * @return true if the edge effect is considered significant
     */
    boolean isEffectEdge(double bump);

    /**
     * Retrieves the maximum allowable degree for a node in the given context or structure.
     *
     * @return the maximum degree as an integer
     */
    int getMaxDegree();

    /**
     * Determines whether the given set of nodes {@code Z} determines the target node {@code y}. This could involve
     * analyzing relationships or dependencies between nodes.
     *
     * @param Z a list of nodes to be checked as the determining set
     * @param y the target node to verify determination
     * @return {@code true} if the nodes in {@code Z} determine {@code y}, {@code false} otherwise
     */
    boolean determines(List<Node> Z, Node y);
}
