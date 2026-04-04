package edu.cmu.tetrad.sem;

/**
 * Parameters for model adequacy assessment.
 */
public final class AdequacyParams {

    /**
     * The fraction of the dataset to be held out for testing or validation purposes.
     * This value represents a proportion (e.g., 0.2 corresponds to 20%).
     */
    public double holdoutFrac = 0.2;
    /**
     * The minimum number of rows required to be held out from the dataset
     * for testing or validation purposes, regardless of the specified holdout fraction.
     */
    public int minHoldoutRows = 20;
    /**
     * The number of features to be used for the Maximum Mean Discrepancy (MMD) calculation.
     * This value determines the dimensionality of the feature space over which
     * MMD is computed to assess the adequacy of the model in terms of distributional similarity.
     */
    public int mmdFeatures = 512;
    /**
     * Specifies the maximum number of rows to be used for the Maximum Mean Discrepancy (MMD) calculation.
     * This value limits the size of the dataset when computing MMD to ensure efficiency
     * during model adequacy assessment and prevents excessive memory or computational overhead.
     */
    public int mmdMaxRows = 1000;
    /**
     * Seed value used in random number generation during the Maximum Mean
     * Discrepancy (MMD) calculation. This ensures reproducibility of the
     * results when assessing the distributional similarity for model adequacy.
     */
    public long mmdSeed = 253213L;

    /**
     * Default constructor for AdequacyParams.
     */
    public AdequacyParams() {}
}