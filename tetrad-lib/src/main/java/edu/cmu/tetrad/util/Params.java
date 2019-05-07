/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.util;

/**
 *
 * May 7, 2019 11:24:06 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public enum Params {

    ALPHA("alpha"),
    APPLY_R1("applyR1"),
    AVG_DEGREE("avgDegree"),
    BASIS_TYPE("basisType"),
    CCI_SCORE_ALPHA("cciScoreAlpha"),
    CG_EXACT("cgExact"),
    COEF_HIGH("coefHigh"),
    COEF_LOW("coefLow"),
    COEF_SYMMETRIC("coefSymmetric"),
    COLLIDER_DISCOVERY_RULE("colliderDiscoveryRule"),
    COMPLETE_RULE_SET_USED("completeRuleSetUsed"),
    CONCURRENT_FAS("concurrentFAS"),
    CONFLICT_RULE("conflictRule"),
    CONNECTED("connected"),
    COV_HIGH("covHigh"),
    COV_LOW("covLow"),
    COV_SYMMETRIC("covSymmetric"),
    CUTOFF_CONSTRAIN_SEARCH("cutoffConstrainSearch"),
    CUTOFF_DATA_SEARCH("cutoffDataSearch"),
    CUTOFF_IND_TEST("cutoffIndTest"),
    DATA_TYPE("dataType"),
    DEPTH("depth"),
    DETERMINISM_THRESHOLD("determinismThreshold"),
    DIFFERENT_GRAPHS("differentGraphs"),
    DISCRETIZE("discretize"),
    DO_COLLIDER_ORIENTATION("doColliderOrientation"),
    ERRORS_NORMAL("errorsNormal"),
    EXTRA_EDGE_THRESHOLD("extraEdgeThreshold"),
    FAITHFULNESS_ASSUMED("faithfulnessAssumed"),
    FAS_RULE("fasRule"),
    FISHER_EPSILON("fisherEpsilon"),
    GENERAL_SEM_ERROR_TEMPLATE("generalSemErrorTemplate"),
    GENERAL_SEM_FUNCTION_TEMPLATE_LATENT("generalSemFunctionTemplateLatent"),
    GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED("generalSemFunctionTemplateMeasured"),
    GENERAL_SEM_PARAMETER_TEMPLATE("generalSemParameterTemplate"),
    IA("ia"),
    INCLUDE_NEGATIVE_COEFS("includeNegativeCoefs"),
    INCLUDE_NEGATIVE_SKEWS_FOR_BETA("includeNegativeSkewsForBeta"),
    INCLUDE_POSITIVE_COEFS("includePositiveCoefs"),
    INCLUDE_POSITIVE_SKEWS_FOR_BETA("includePositiveSkewsForBeta"),
    INCLUDE_STRUCTURE_MODEL("include_structure_model"),
    INTERVAL_BETWEEN_RECORDINGS("intervalBetweenRecordings"),
    INTERVAL_BETWEEN_SHOCKS("intervalBetweenShocks"),
    IPEN("ipen"),
    IS("is"),
    ITR("itr"),
    KCI_ALPHA("kciAlpha"),
    KCI_CUTOFF("kciCutoff"),
    KCI_EPSILON("kciEpsilon"),
    KCI_NUM_BOOTSTRAPS("kciNumBootstraps"),
    KCI_USE_APPROMATION("kciUseAppromation"),
    KERNEL_MULTIPLIER("kernelMultiplier"),
    KERNEL_REGRESSION_SAMPLE_SIZE("kernelRegressionSampleSize"),
    KERNEL_TYPE("kernelType"),
    KERNEL_WIDTH("kernelWidth"),
    LATENT_MEASURED_IMPURE_PARENTS("latentMeasuredImpureParents"),
    LOWER_BOUND("lowerBound"),
    MAX_CATEGORIES("maxCategories"),
    MAX_DEGREE("maxDegree"),
    MAX_DISTINCT_VALUES_DISCRETE("maxDistinctValuesDiscrete"),
    MAX_INDEGREE("maxIndegree"),
    MAX_ITERATIONS("maxIterations"),
    MAX_OUTDEGREE("maxOutdegree"),
    MAX_P_ORIENTATION_MAX_PATH_LENGTH("maxPOrientationMaxPathLength"),
    MAX_PATH_LENGTH("maxPathLength"),
    MAXIT("maxit"),
    MEAN_HIGH("meanHigh"),
    MEAN_LOW("meanLow"),
    MEASURED_MEASURED_IMPURE_ASSOCIATIONS("measuredMeasuredImpureAssociations"),
    MEASURED_MEASURED_IMPURE_PARENTS("measuredMeasuredImpureParents"),
    MEASUREMENT_MODEL_DEGREE("measurementModelDegree"),
    MEASUREMENT_VARIANCE("measurementVariance"),
    MGM_PARAM1("mgmParam1"),
    MGM_PARAM2("mgmParam2"),
    MGM_PARAM3("mgmParam3"),
    MIN_CATEGORIES("minCategories"),
    NO_RANDOMLY_DETERMINED_INDEPENDENCE("noRandomlyDeterminedIndependence"),
    NUM_BASIS_FUNCTIONS("numBasisFunctions"),
    NUM_BSC_BOOTSTRAP_SAMPLES("numBscBootstrapSamples"),
    NUM_CATEGORIES("numCategories"),
    NUM_CATEGORIES_TO_DISCRETIZE("numCategoriesToDiscretize"),
    NUM_LAGS("numLags"),
    NUM_LATENTS("numLatents"),
    NUM_MEASURES("numMeasures"),
    NUM_RANDOMIZED_SEARCH_MODELS("numRandomizedSearchModels"),
    NUM_RUNS("numRuns"),
    NUM_STRUCTURAL_EDGES("numStructuralEdges"),
    NUM_STRUCTURAL_NODES("numStructuralNodes"),
    ORIENT_TOWARD_DCONNECTIONS("orientTowardDConnections"),
    ORIENT_VISIBLE_FEEDBACK_LOOPS("orientVisibleFeedbackLoops"),
    OUTPUT_RBD("outputRBD"),
    PENALTY_DISCOUNT("penaltyDiscount"),
    PERCENT_DISCRETE("percentDiscrete"),
    POSSIBLE_DSEP_DONE("possibleDsepDone"),
    PROB_CYCLE("probCycle"),
    PROB_TWO_CYCLE("probTwoCycle"),
    RANDOM_SELECTION_SIZE("randomSelectionSize"),
    RANDOMIZE_COLUMNS("randomizeColumns"),
    RCIT_NUM_FEATURES("rcitNumFeatures"),
    SAMPLE_PRIOR("samplePrior"),
    SAMPLE_SIZE("sampleSize"),
    SAVE_LATENT_VARS("saveLatentVars"),
    SCALE_FREE_ALPHA("scaleFreeAlpha"),
    SCALE_FREE_BETA("scaleFreeBeta"),
    SCALE_FREE_DELTA_IN("scaleFreeDeltaIn"),
    SCALE_FREE_DELTA_OUT("scaleFreeDeltaOut"),
    SELF_LOOP_COEF("selfLoopCoef"),
    SKIP_NUM_RECORDS("skipNumRecords"),
    STABLE_FAS("stableFAS"),
    STANDARDIZE("standardize"),
    STRUCTURE_PRIOR("structurePrior"),
    SYMMETRIC_FIRST_STEP("symmetricFirstStep"),
    TARGET_NAME("targetName"),
    THR("thr"),
    THRESHOLD_FOR_NUM_EIGENVALUES("thresholdForNumEigenvalues"),
    THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH("thresholdNoRandomConstrainSearch"),
    THRESHOLD_NO_RANDOM_DATA_SEARCH("thresholdNoRandomDataSearch"),
    TWO_CYCLE_ALPHA("twoCycleAlpha"),
    UPPER_BOUND("upperBound"),
    USE_CORR_DIFF_ADJACENCIES("useCorrDiffAdjacencies"),
    USE_FAS_ADJACENCIES("useFasAdjacencies"),
    USE_GAP("useGap"),
    USE_MAX_P_ORIENTATION_HEURISTIC("useMaxPOrientationHeuristic"),
    USE_SKEW_ADJACENCIES("useSkewAdjacencies"),
    USE_WISHART("useWishart"),
    VAR_HIGH("varHigh"),
    VAR_LOW("varLow"),
    VERBOSE("verbose");

    private final String name;

    private Params(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
