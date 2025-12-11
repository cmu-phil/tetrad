package edu.cmu.tetrad.search.rlcd;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Parameters for RLCD, modeled after the Python implementation.
 */
public final class RLCDParams implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * alpha_k for rank tests at cardinality k (k=0..maxk).
     */
    private double[] alphaByK = {0.01, 0.01, 0.01, 0.01};
    /**
     * Maximum cluster cardinality k considered.
     */
    private int maxK = 3;
    /**
     * Indicates whether non-leaf nodes are allowed in the context where this variable is used. When set to
     * {@code true}, non-leaf nodes are permitted. When set to {@code false}, only leaf nodes are permitted.
     */
    private boolean allowNonLeafX = true;
    /**
     * A flag indicating whether the covers should be unfolded.
     * <p>
     * When set to {@code true}, covers will be unfolded as per the logic implementation. When set to {@code false},
     * covers will remain in their folded state.
     */
    private boolean unfoldCovers = true;
    /**
     * A flag indicating whether the verification of V-structures is enabled or not.
     * <p>
     * When set to {@code true}, the process will check for V-structures (a specific pattern or structure in the data or
     * algorithm being used). If set to {@code false}, this check will be skipped.
     */
    private boolean checkVStructures = true;
    /**
     * Number of stages to run (1=skeleton only, 2/3=with latent discovery).
     */
    private int stages = 2;
    /**
     * Represents the method used in stage 1 processing, initialized with a default value. The stage1Method variable
     * determines the algorithm or approach to be employed during the first stage of execution or analysis.
     * <p>
     * Possible values are defined in the {@link Stage1Method} enumeration.
     */
    private Stage1Method stage1Method = Stage1Method.FGES;
    /**
     * Sparsity/penalty parameter for score-based Stage 1 (e.g., FGES/ GES).
     */
    private double stage1GesSparsity = 2.0;
    /**
     * Alpha for CI test if PC/FCI is used in Stage 1.
     */
    private double stage1CiAlpha = 0.01;
    /**
     * Threshold for grouping maximal cliques into overlapping partitions.
     */
    private int stage1PartitionThreshold = 3;
    /**
     * A factory instance for creating and managing rank test-related objects or processes. This variable is used to
     * provide a centralized way to instantiate or obtain rank testing components, ensuring consistency and
     * encapsulation of the creation logic.
     */
    private RankTestFactory rankTestFactory;
    /**
     * Represents a factory for creating instances of independence tests. This variable is used to generate or provide
     * access to specific implementations of independence tests that are utilized in statistical or algorithmic analyses
     * to determine the independence between variables.
     */
    private IndependenceTestFactory ciTestFactory;

    /**
     * Constructs an instance of the RLCDParams class with default configuration values. This constructor initializes
     * the RLCDParams object, which serves as a container for configuration parameters required by the Rank-based Latent
     * Causal Discovery (RLCD) algorithm.
     */
    public RLCDParams() {
    }

    /**
     * Retrieves a copy of the alphaByK array, which represents the alpha values associated with each value of K in the
     * RLCD algorithm's configuration.
     *
     * @return a new array containing the alpha values for each K. Modifications to the returned array will not affect
     * the internal state of the RLCDParams object.
     */
    public double[] getAlphaByK() {
        return alphaByK.clone();
    }

    /**
     * Sets the alpha values for different values of K in the RLCD algorithm's configuration. Ensures that the provided
     * array is non-null and non-empty, and clones the array to protect the internal state.
     *
     * @param alphaByK an array of double values representing the alpha values for corresponding values of K. Must not
     *                 be null or empty.
     * @throws IllegalArgumentException if the provided array is null or empty.
     */
    public void setAlphaByK(double[] alphaByK) {
        if (alphaByK == null || alphaByK.length == 0) {
            throw new IllegalArgumentException("alphaByK must be non-empty.");
        }
        this.alphaByK = alphaByK.clone();
    }

    /**
     * Retrieves the maximum value of K, a parameter used in the RLCD algorithm's configuration.
     *
     * @return the maximum value of K as an integer.
     */
    public int getMaxK() {
        return maxK;
    }

    /**
     * Sets the maximum value of K, a parameter used in the RLCD algorithm's configuration. The value must be greater
     * than or equal to 1.
     *
     * @param maxK the maximum value of K. Must be an integer greater than or equal to 1.
     * @throws IllegalArgumentException if the provided value of maxK is less than 1.
     */
    public void setMaxK(int maxK) {
        if (maxK < 1) {
            throw new IllegalArgumentException("maxK must be >=1.");
        }
        this.maxK = maxK;
    }

    /**
     * Determines whether non-leaf structures (X) are allowed in the RLCD algorithm's configuration.
     *
     * @return true if non-leaf structures are permitted; false otherwise.
     */
    public boolean isAllowNonLeafX() {
        return allowNonLeafX;
    }

    /**
     * Sets whether non-leaf structures (X) are allowed in the RLCD algorithm's configuration.
     *
     * @param allowNonLeafX a boolean value indicating whether non-leaf structures are permitted (true) or not (false).
     */
    public void setAllowNonLeafX(boolean allowNonLeafX) {
        this.allowNonLeafX = allowNonLeafX;
    }

    /**
     * Determines whether the "unfold covers" configuration is enabled in the RLCD algorithm's settings.
     *
     * @return true if the "unfold covers" configuration is enabled; false otherwise.
     */
    public boolean isUnfoldCovers() {
        return unfoldCovers;
    }

    /**
     * Sets whether the "unfold covers" configuration is enabled in the RLCD algorithm's settings.
     *
     * @param unfoldCovers a boolean value indicating whether the "unfold covers" configuration is enabled (true) or
     *                     disabled (false).
     */
    public void setUnfoldCovers(boolean unfoldCovers) {
        this.unfoldCovers = unfoldCovers;
    }

    /**
     * Determines whether V-structures are checked in the configuration of the RLCD algorithm.
     *
     * @return true if V-structures are checked; false otherwise.
     */
    public boolean isCheckVStructures() {
        return checkVStructures;
    }

    /**
     * Sets whether V-structures are checked within the configuration of the RLCD algorithm.
     *
     * @param checkVStructures a boolean value indicating whether V-structures are checked (true) or not (false).
     */
    public void setCheckVStructures(boolean checkVStructures) {
        this.checkVStructures = checkVStructures;
    }

    /**
     * Retrieves the number of stages configured for the RLCD algorithm.
     *
     * @return the number of stages as an integer.
     */
    public int getStages() {
        return stages;
    }

    /**
     * Sets the number of stages for the RLCD algorithm. The value must be between 1 and 3, inclusive. This method
     * updates the configuration parameter that specifies the number of stages the algorithm will follow. If the value
     * is outside the allowed range, an exception is thrown to enforce validity.
     *
     * @param stages the number of stages as an integer. Must be 1, 2, or 3.
     * @throws IllegalArgumentException if the provided value of stages is less than 1 or greater than 3.
     */
    public void setStages(int stages) {
        if (stages < 1 || stages > 3) {
            throw new IllegalArgumentException("stages must be 1, 2, or 3.");
        }
        this.stages = stages;
    }

    /**
     * Retrieves the Stage1Method configuration used in Phase 1 of the RLCD algorithm. The Stage1Method specifies the
     * method to be employed for constructing the conditional independence skeleton or score-based skeleton.
     *
     * @return the current Stage1Method configuration.
     */
    public Stage1Method getStage1Method() {
        return stage1Method;
    }

    /**
     * Sets the Stage1Method configuration for Phase 1 of the RLCD algorithm. The Stage1Method determines the method
     * used for constructing the skeleton in the causal discovery process.
     *
     * @param stage1Method the Stage1Method to be applied in Phase 1. This can be one of the predefined methods such as
     *                     ALL, PC_RANK, FGES, GES, or FCI.
     */
    public void setStage1Method(Stage1Method stage1Method) {
        this.stage1Method = stage1Method;
    }

    /**
     * Retrieves the sparsity parameter for the GES (Greedy Equivalence Search) algorithm used in Stage 1 of the RLCD
     * algorithm's configuration.
     *
     * @return the value of the stage1GesSparsity parameter as a double. This parameter represents the sparsity level
     * utilized during the GES-based skeleton construction.
     */
    public double getStage1GesSparsity() {
        return stage1GesSparsity;
    }

    /**
     * Sets the sparsity parameter for the GES (Greedy Equivalence Search) algorithm used in Stage 1 of the RLCD
     * algorithm's configuration. The sparsity parameter influences the structure of the skeleton constructed during the
     * GES-based process.
     *
     * @param stage1GesSparsity the sparsity level for the GES algorithm. Must be a non-negative double value.
     */
    public void setStage1GesSparsity(double stage1GesSparsity) {
        this.stage1GesSparsity = stage1GesSparsity;
    }

    /**
     * Retrieves the alpha parameter used for conditional independence (CI) tests in Stage 1 of the RLCD algorithm. This
     * parameter represents the significance level for the statistical tests performed during the causal discovery
     * process.
     *
     * @return the value of the stage1CiAlpha parameter as a double, indicating the significance level for Stage 1 CI
     * tests.
     */
    public double getStage1CiAlpha() {
        return stage1CiAlpha;
    }

    /**
     * Sets the alpha parameter used for conditional independence (CI) tests in Stage 1 of the RLCD algorithm. The alpha
     * parameter represents the significance level for the statistical tests performed during causal discovery.
     *
     * @param stage1CiAlpha the significance level for Stage 1 CI tests. Must be a double value between 0 and 1
     *                      (exclusive).
     */
    public void setStage1CiAlpha(double stage1CiAlpha) {
        this.stage1CiAlpha = stage1CiAlpha;
    }

    /**
     * Retrieves the partition threshold used in Stage 1 of the RLCD algorithm's configuration.
     *
     * @return the value of the stage1PartitionThreshold parameter as an integer.
     */
    public int getStage1PartitionThreshold() {
        return stage1PartitionThreshold;
    }

    /**
     * Sets the partition threshold parameter for Stage 1 of the RLCD algorithm. This parameter determines the threshold
     * value used for partitioning data or resolving specific configurations during the first stage of the algorithm's
     * execution.
     *
     * @param stage1PartitionThreshold the partition threshold as an integer. A valid numeric threshold required for
     *                                 Stage 1 configuration.
     */
    public void setStage1PartitionThreshold(int stage1PartitionThreshold) {
        this.stage1PartitionThreshold = stage1PartitionThreshold;
    }

    /**
     * Retrieves the instance of RankTestFactory.
     *
     * @return the RankTestFactory instance.
     */
    public RankTestFactory getRankTestFactory() {
        return rankTestFactory;
    }

    /**
     * Sets the factory instance used to create rank test objects.
     *
     * @param rankTestFactory The RankTestFactory instance to be used for creating rank test objects.
     */
    public void setRankTestFactory(RankTestFactory rankTestFactory) {
        this.rankTestFactory = rankTestFactory;
    }

    /**
     * Retrieves the instance of IndependenceTestFactory associated with this object.
     *
     * @return the IndependenceTestFactory instance used for conducting independence tests
     */
    public IndependenceTestFactory getCiTestFactory() {
        return ciTestFactory;
    }

    /**
     * Sets the IndependenceTestFactory instance to be used.
     *
     * @param ciTestFactory the IndependenceTestFactory instance to set
     */
    public void setCiTestFactory(IndependenceTestFactory ciTestFactory) {
        this.ciTestFactory = ciTestFactory;
    }

    /**
     * Returns a string representation of the RLCDParams object. The string includes values of the fields: alphaByK,
     * maxK, stages, stage1Method, stage1GesSparsity, stage1CiAlpha, and stage1PartitionThreshold.
     *
     * @return a string representation of the RLCDParams object.
     */
    @Override
    public String toString() {
        return "RLCDParams{" +
               "alphaByK=" + Arrays.toString(alphaByK) +
               ", maxK=" + maxK +
               ", stages=" + stages +
               ", stage1Method=" + stage1Method +
               ", stage1GesSparsity=" + stage1GesSparsity +
               ", stage1CiAlpha=" + stage1CiAlpha +
               ", stage1PartitionThreshold=" + stage1PartitionThreshold +
               '}';
    }

    /**
     * Enum representing the available methods for Phase 1 of the RLCD causal discovery process. Phase 1 involves
     * constructing the conditional independence skeleton or score-based skeleton.
     */
    public enum Stage1Method {
        /**
         * Fully connected skeleton
         */
        ALL,  // fully connected skeleton
        /**
         * Represents the PC algorithm with rank-based modification for skeleton discovery. PC_RANK is a method used in
         * Phase 1 of the RLCD causal discovery process to construct the conditional independence skeleton, applying a
         * rank-based approach to improve the robustness of the standard PC algorithm under specific conditions, such as
         * non-normally distributed or ordinal data.
         */
        PC_RANK,
        /**
         * Represents the FGES (Fast Greedy Equivalence Search) algorithm used in Phase 1 of the RLCD causal discovery
         * process. FGES is a score-based method for constructing a skeleton graph by iteratively adding, removing, or
         * reversing edges to optimize a predefined scoring function.
         */
        FGES,
        /**
         * Represents the GES (Greedy Equivalence Search) algorithm as a method in Phase 1 of the RLCD causal discovery
         * process. GES is a score-based algorithm for constructing a causal graph by iteratively adding, removing, or
         * reversing edges to optimize a predefined scoring function. It assumes a set of candidate structures and
         * searches for the one that best fits the data based on the scoring function.
         */
        GES,
        /**
         * Represents the FCI (Fast Causal Inference) algorithm as a method in Phase 1 of the RLCD causal discovery
         * process. FCI is a constraint-based algorithm designed for causal discovery in the presence of latent (hidden)
         * variables and selection bias. It extends the PC algorithm to account for such cases by iteratively refining
         * the skeleton while considering conditional independence relations to infer edges and edges' orientations in a
         * PAG (Partial Ancestral Graph).
         */
        FCI
    }
}