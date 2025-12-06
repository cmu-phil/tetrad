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

    public enum Stage1Method {
        ALL,  // fully connected skeleton
        PC_RANK,
        FGES,
        GES,
        FCI
    }

    /** alpha_k for rank tests at cardinality k (k=0..maxk). */
    private double[] alphaByK = {0.01, 0.01, 0.01, 0.01};

    /** Maximum cluster cardinality k considered. */
    private int maxK = 3;

    private boolean allowNonLeafX = true;
    private boolean unfoldCovers = true;
    private boolean checkVStructures = true;

    /** Number of stages to run (1=skeleton only, 2/3=with latent discovery). */
    private int stages = 2;

    private Stage1Method stage1Method = Stage1Method.FGES;

    /** Sparsity/penalty parameter for score-based Stage 1 (e.g., FGES/ GES). */
    private double stage1GesSparsity = 2.0;

    /** Alpha for CI test if PC/FCI is used in Stage 1. */
    private double stage1CiAlpha = 0.01;

    /** Threshold for grouping maximal cliques into overlapping partitions. */
    private int stage1PartitionThreshold = 3;

    // For future use once rank and CI tests are implemented in Java:
    private RankTestFactory rankTestFactory;
    private IndependenceTestFactory ciTestFactory;

    public RLCDParams() {
    }

    public double[] getAlphaByK() {
        return alphaByK.clone();
    }

    public void setAlphaByK(double[] alphaByK) {
        if (alphaByK == null || alphaByK.length == 0) {
            throw new IllegalArgumentException("alphaByK must be non-empty.");
        }
        this.alphaByK = alphaByK.clone();
    }

    public int getMaxK() {
        return maxK;
    }

    public void setMaxK(int maxK) {
        if (maxK < 1) {
            throw new IllegalArgumentException("maxK must be >=1.");
        }
        this.maxK = maxK;
    }

    public boolean isAllowNonLeafX() {
        return allowNonLeafX;
    }

    public void setAllowNonLeafX(boolean allowNonLeafX) {
        this.allowNonLeafX = allowNonLeafX;
    }

    public boolean isUnfoldCovers() {
        return unfoldCovers;
    }

    public void setUnfoldCovers(boolean unfoldCovers) {
        this.unfoldCovers = unfoldCovers;
    }

    public boolean isCheckVStructures() {
        return checkVStructures;
    }

    public void setCheckVStructures(boolean checkVStructures) {
        this.checkVStructures = checkVStructures;
    }

    public int getStages() {
        return stages;
    }

    public void setStages(int stages) {
        if (stages < 1 || stages > 3) {
            throw new IllegalArgumentException("stages must be 1, 2, or 3.");
        }
        this.stages = stages;
    }

    public Stage1Method getStage1Method() {
        return stage1Method;
    }

    public void setStage1Method(Stage1Method stage1Method) {
        this.stage1Method = stage1Method;
    }

    public double getStage1GesSparsity() {
        return stage1GesSparsity;
    }

    public void setStage1GesSparsity(double stage1GesSparsity) {
        this.stage1GesSparsity = stage1GesSparsity;
    }

    public double getStage1CiAlpha() {
        return stage1CiAlpha;
    }

    public void setStage1CiAlpha(double stage1CiAlpha) {
        this.stage1CiAlpha = stage1CiAlpha;
    }

    public int getStage1PartitionThreshold() {
        return stage1PartitionThreshold;
    }

    public void setStage1PartitionThreshold(int stage1PartitionThreshold) {
        this.stage1PartitionThreshold = stage1PartitionThreshold;
    }

    public RankTestFactory getRankTestFactory() {
        return rankTestFactory;
    }

    public void setRankTestFactory(RankTestFactory rankTestFactory) {
        this.rankTestFactory = rankTestFactory;
    }

    public IndependenceTestFactory getCiTestFactory() {
        return ciTestFactory;
    }

    public void setCiTestFactory(IndependenceTestFactory ciTestFactory) {
        this.ciTestFactory = ciTestFactory;
    }

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
}