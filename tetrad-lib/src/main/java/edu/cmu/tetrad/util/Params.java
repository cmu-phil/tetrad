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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * May 7, 2019 2:53:27 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class Params {

    public static final String ADD_ORIGINAL_DATASET = "addOriginalDataset";
    public static final String ALPHA = "alpha";
    public static final String APPLY_R1 = "applyR1";
    public static final String AVG_DEGREE = "avgDegree";
    public static final String BASIS_TYPE = "basisType";
    public static final String CCI_SCORE_ALPHA = "cciScoreAlpha";
    public static final String CG_EXACT = "cgExact";
    public static final String COEF_HIGH = "coefHigh";
    public static final String COEF_LOW = "coefLow";
    public static final String COEF_SYMMETRIC = "coefSymmetric";
    public static final String COLLIDER_DISCOVERY_RULE = "colliderDiscoveryRule";
    public static final String COMPLETE_RULE_SET_USED = "completeRuleSetUsed";
    public static final String CONCURRENT_FAS = "concurrentFAS";
    public static final String CONFLICT_RULE = "conflictRule";
    public static final String CONNECTED = "connected";
    public static final String COV_HIGH = "covHigh";
    public static final String COV_LOW = "covLow";
    public static final String COV_SYMMETRIC = "covSymmetric";
    public static final String CUTOFF_CONSTRAIN_SEARCH = "cutoffConstrainSearch";
    public static final String CUTOFF_DATA_SEARCH = "cutoffDataSearch";
    public static final String CUTOFF_IND_TEST = "cutoffIndTest";
    public static final String DATA_TYPE = "dataType";
    public static final String DEPTH = "depth";
    public static final String DETERMINISM_THRESHOLD = "determinismThreshold";
    public static final String DIFFERENT_GRAPHS = "differentGraphs";
    public static final String DISCRETIZE = "discretize";
    public static final String DO_COLLIDER_ORIENTATION = "doColliderOrientation";
    public static final String ERRORS_NORMAL = "errorsNormal";
    public static final String SKEW_EDGE_THRESHOLD = "skewEdgeThreshold";
    public static final String TWO_CYCLE_SCREENING_THRESHOLD = "twoCycleScreeningThreshold";
    public static final String FASK_DELTA = "faskDelta";
    public static final String FASK_LEFT_RIGHT_RULE = "faskLeftRightRule";
    public static final String FASK_ADJACENCY_METHOD = "faskAdjacencyMethod";
    public static final String FASK_NONEMPIRICAL = "faskNonempirical";
    public static final String FAITHFULNESS_ASSUMED = "faithfulnessAssumed";
    public static final String FAS_HEURISTIC = "fasHeuristic";
    public static final String FAS_RULE = "fasRule";
    public static final String FAST_ICA_A = "fastIcaA";
    public static final String FAST_ICA_MAX_ITER = "fastIcaMaxIter";
    public static final String FAST_ICA_TOLERANCE = "fastIcaTolerance";
    public static final String ICA_ALGORITHM = "icaAlgorithm";
    public static final String ICA_FUNCTION = "icaFunction";
    public static final String ORIENTATION_ALPHA = "orientationAlpha";
    public static final String FISHER_EPSILON = "fisherEpsilon";
    public static final String GENERAL_SEM_ERROR_TEMPLATE = "generalSemErrorTemplate";
    public static final String GENERAL_SEM_FUNCTION_TEMPLATE_LATENT = "generalSemFunctionTemplateLatent";
    public static final String GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED = "generalSemFunctionTemplateMeasured";
    public static final String GENERAL_SEM_PARAMETER_TEMPLATE = "generalSemParameterTemplate";
    public static final String IA = "ia";
    public static final String INCLUDE_NEGATIVE_COEFS = "includeNegativeCoefs";
    public static final String INCLUDE_NEGATIVE_SKEWS_FOR_BETA = "includeNegativeSkewsForBeta";
    public static final String INCLUDE_POSITIVE_COEFS = "includePositiveCoefs";
    public static final String INCLUDE_POSITIVE_SKEWS_FOR_BETA = "includePositiveSkewsForBeta";
    public static final String INCLUDE_STRUCTURE_MODEL = "include_structure_model";
    public static final String INTERVAL_BETWEEN_RECORDINGS = "intervalBetweenRecordings";
    public static final String INTERVAL_BETWEEN_SHOCKS = "intervalBetweenShocks";
    public static final String IPEN = "ipen";
    public static final String IS = "is";
    public static final String ITR = "itr";
    public static final String KCI_ALPHA = "kciAlpha";
    public static final String KCI_CUTOFF = "kciCutoff";
    public static final String KCI_EPSILON = "kciEpsilon";
    public static final String KCI_NUM_BOOTSTRAPS = "kciNumBootstraps";
    public static final String KCI_USE_APPROMATION = "kciUseAppromation";
    public static final String KERNEL_MULTIPLIER = "kernelMultiplier";
    public static final String KERNEL_REGRESSION_SAMPLE_SIZE = "kernelRegressionSampleSize";
    public static final String KERNEL_TYPE = "kernelType";
    public static final String KERNEL_WIDTH = "kernelWidth";
    public static final String LATENT_MEASURED_IMPURE_PARENTS = "latentMeasuredImpureParents";
    public static final String LOWER_BOUND = "lowerBound";
    public static final String MAX_CATEGORIES = "maxCategories";
    public static final String MAX_DEGREE = "maxDegree";
    public static final String MAX_DISTINCT_VALUES_DISCRETE = "maxDistinctValuesDiscrete";
    public static final String MAX_INDEGREE = "maxIndegree";
    public static final String MAX_ITERATIONS = "maxIterations";
    public static final String MAX_OUTDEGREE = "maxOutdegree";
    public static final String MAX_P_ORIENTATION_MAX_PATH_LENGTH = "maxPOrientationMaxPathLength";
    public static final String MAX_PATH_LENGTH = "maxPathLength";
    public static final String MAXIT = "maxit";
    public static final String MEAN_HIGH = "meanHigh";
    public static final String MEAN_LOW = "meanLow";
    public static final String MEASURED_MEASURED_IMPURE_ASSOCIATIONS = "measuredMeasuredImpureAssociations";
    public static final String MEASURED_MEASURED_IMPURE_PARENTS = "measuredMeasuredImpureParents";
    public static final String MEASUREMENT_MODEL_DEGREE = "measurementModelDegree";
    public static final String MEASUREMENT_VARIANCE = "measurementVariance";
    public static final String MGM_PARAM1 = "mgmParam1";
    public static final String MGM_PARAM2 = "mgmParam2";
    public static final String MGM_PARAM3 = "mgmParam3";
    public static final String MIN_CATEGORIES = "minCategories";
    public static final String NO_RANDOMLY_DETERMINED_INDEPENDENCE = "noRandomlyDeterminedIndependence";
    public static final String NUM_BASIS_FUNCTIONS = "numBasisFunctions";
    public static final String NUM_BSC_BOOTSTRAP_SAMPLES = "numBscBootstrapSamples";
    public static final String NUM_CATEGORIES = "numCategories";
    public static final String NUM_CATEGORIES_TO_DISCRETIZE = "numCategoriesToDiscretize";
    public static final String NUM_LAGS = "numLags";
    public static final String NUM_LATENTS = "numLatents";
    public static final String NUM_MEASURES = "numMeasures";
    public static final String PROBABILITY_OF_EDGE = "probabilityOfEdge";
    public static final String NUM_RANDOMIZED_SEARCH_MODELS = "numRandomizedSearchModels";
    public static final String NUM_RUNS = "numRuns";
    public static final String NUM_STRUCTURAL_EDGES = "numStructuralEdges";
    public static final String NUM_STRUCTURAL_NODES = "numStructuralNodes";
    public static final String NUMBER_RESAMPLING = "numberResampling";
    public static final String ORIENT_TOWARD_DCONNECTIONS = "orientTowardDConnections";
    public static final String ORIENT_VISIBLE_FEEDBACK_LOOPS = "orientVisibleFeedbackLoops";
    public static final String OUTPUT_RBD = "outputRBD";
    public static final String PARALLELISM = "parallelism";
    public static final String PENALTY_DISCOUNT = "penaltyDiscount";
    public static final String EBIC_GAMMA = "ebicGamma";
    public static final String PERCENT_DISCRETE = "percentDiscrete";
    public static final String PERCENT_RESAMPLE_SIZE = "percentResampleSize";
    public static final String POSSIBLE_DSEP_DONE = "possibleDsepDone";
    public static final String PROB_CYCLE = "probCycle";
    public static final String PROB_TWO_CYCLE = "probTwoCycle";
    public static final String RANDOM_SELECTION_SIZE = "randomSelectionSize";
    public static final String RANDOMIZE_COLUMNS = "randomizeColumns";
    public static final String RCIT_NUM_FEATURES = "rcitNumFeatures";
    public static final String RESAMPLING_ENSEMBLE = "resamplingEnsemble";
    public static final String RESAMPLING_WITH_REPLACEMENT = "resamplingWithReplacement";
    public static final String PRIOR_EQUIVALENT_SAMPLE_SIZE = "priorEquivalentSampleSize";
    public static final String SAMPLE_SIZE = "sampleSize";
    public static final String SAVE_LATENT_VARS = "saveLatentVars";
    public static final String SCALE_FREE_ALPHA = "scaleFreeAlpha";
    public static final String SCALE_FREE_BETA = "scaleFreeBeta";
    public static final String SCALE_FREE_DELTA_IN = "scaleFreeDeltaIn";
    public static final String SCALE_FREE_DELTA_OUT = "scaleFreeDeltaOut";
    public static final String SELF_LOOP_COEF = "selfLoopCoef";
    public static final String SKIP_NUM_RECORDS = "skipNumRecords";
    public static final String STABLE_FAS = "stableFAS";
    public static final String STANDARDIZE = "standardize";
    public static final String STRUCTURE_PRIOR = "structurePrior";
    public static final String SYMMETRIC_FIRST_STEP = "symmetricFirstStep";
    public static final String TARGET_NAME = "targetName";
    public static final String THR = "thr";
    public static final String THRESHOLD_FOR_NUM_EIGENVALUES = "thresholdForNumEigenvalues";
    public static final String THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH = "thresholdNoRandomConstrainSearch";
    public static final String THRESHOLD_NO_RANDOM_DATA_SEARCH = "thresholdNoRandomDataSearch";
    public static final String TWO_CYCLE_ALPHA = "twoCycleAlpha";
    public static final String UPPER_BOUND = "upperBound";
    public static final String USE_CORR_DIFF_ADJACENCIES = "useCorrDiffAdjacencies";
    public static final String USE_FAS_ADJACENCIES = "useFasAdjacencies";
    public static final String USE_GAP = "useGap";
    public static final String USE_MAX_P_ORIENTATION_HEURISTIC = "useMaxPOrientationHeuristic";
    public static final String USE_SKEW_ADJACENCIES = "useSkewAdjacencies";
    public static final String USE_WISHART = "useWishart";
    public static final String VAR_HIGH = "varHigh";
    public static final String VAR_LOW = "varLow";
    public static final String VERBOSE = "verbose";
    public static final String MEEK_VERBOSE = "meekVerbose";

    // System prameters that are not supposed to put in the HTML manual documentation
    public static final String PRINT_STREAM = "printStream";
    public static final String SEM_BIC_RULE = "semBicRule";
    public static final String SEM_BIC_STRUCTURE_PRIOR = "semBicStructurePrior";
    public static final String NUM_STARTS = "numStarts";
    public static final String CACHE_SCORES = "cacheScores";
    public static final String OTHER_PERM_METHOD = "otherPermMethod";
    public static final String BOSS_SCORE_TYPE = "bossScoreType";
    public static final String BREAK_TIES = "breakTies";
    public static final String OUTPUT_CPDAG = "outputCpdag";
    public static final String ZS_RISK_BOUND = "zSRiskBound";
    public static final String NUM_ROUNDS = "numRounds";

    // GRASP parameters and flags.
    public static final String GRASP_CHECK_COVERING = "graspCheckCovering";
    public static final String GRASP_FORWARD_TUCK_ONLY = "graspForwardTuckOnly";
    public static final String GRASP_BREAK_AFTER_IMPROVEMENT = "graspBreakAFterImprovement";
    public static final String GRASP_ORDERED_ALG = "graspOrderedAlg";
    public static final String GRASP_USE_SCORE = "graspUseScore";
    public static final String GRASP_USE_VERMA_PEARL = "graspUseVermaPearl";
    public static final String GRASP_USE_DATA_ORDER = "graspUseDataOrder";
    public static final String GRASP_ALLOW_RANDOMNESS_INSIDE_ALGORITHM = "graspAllowRandomnessIndideAlgorithm";
    public static final String GRASP_DEPTH = "graspDepth";
    public static final String GRASP_UNCOVERED_DEPTH = "graspUncoveredDepth";
    public static final String GRASP_NONSINGULAR_DEPTH = "graspNonSingularDepth";
    public static final String GRASP_TOLERANCE_DEPTH = "graspToleranceDepth";
    public static final String GRASP_ALG = "graspAlg";
    public static final String TIMEOUT = "timeout";
    public static final String GRASP_USE_VP_SCORING = "graspUseVpScoring";
    public static final String SIMULATION_ERROR_TYPE = "simulationErrorType";
    public static final String SIMULATION_PARAM1 = "simulationParam1";
    public static final String SIMULATION_PARAM2 = "simulationParam2";
    public static final String SELECTION_MIN_EFFECT = "selectionMinEffect";
    public static final String NUM_SUBSAMPLES = "numSubsamples";
    public static final String TARGET_NAMES = "targetNames";
    public static final String CSTAR_Q = "cstarQ";

    // All parameters that are found in HTML manual documentation
    private static final Set<String> ALL_PARAMS_IN_HTML_MANUAL = new HashSet<>(Arrays.asList(
            Params.ADD_ORIGINAL_DATASET, Params.ALPHA, Params.APPLY_R1, Params.AVG_DEGREE, Params.BASIS_TYPE,
            Params.CCI_SCORE_ALPHA, Params.CG_EXACT, Params.COEF_HIGH, Params.COEF_LOW, Params.COEF_SYMMETRIC,
            Params.COLLIDER_DISCOVERY_RULE, Params.COMPLETE_RULE_SET_USED, Params.CONCURRENT_FAS,
            Params.CONFLICT_RULE, Params.CONNECTED, Params.COV_HIGH, Params.COV_LOW, Params.COV_SYMMETRIC,
            Params.CUTOFF_CONSTRAIN_SEARCH, Params.CUTOFF_DATA_SEARCH, Params.CUTOFF_IND_TEST,
            Params.DATA_TYPE, Params.DEPTH, Params.DETERMINISM_THRESHOLD, Params.DIFFERENT_GRAPHS, Params.DISCRETIZE,
            Params.DO_COLLIDER_ORIENTATION, Params.ERRORS_NORMAL, Params.SKEW_EDGE_THRESHOLD,
            Params.FAITHFULNESS_ASSUMED, Params.FAS_RULE, Params.FISHER_EPSILON, Params.GENERAL_SEM_ERROR_TEMPLATE,
            Params.GENERAL_SEM_FUNCTION_TEMPLATE_LATENT, Params.GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED,
            Params.GENERAL_SEM_PARAMETER_TEMPLATE, Params.IA, Params.INCLUDE_NEGATIVE_COEFS,
            Params.INCLUDE_NEGATIVE_SKEWS_FOR_BETA, Params.INCLUDE_POSITIVE_COEFS,
            Params.INCLUDE_POSITIVE_SKEWS_FOR_BETA, Params.INCLUDE_STRUCTURE_MODEL,
            Params.INTERVAL_BETWEEN_RECORDINGS, Params.INTERVAL_BETWEEN_SHOCKS, Params.IPEN, Params.IS, Params.ITR,
            Params.KCI_ALPHA, Params.KCI_CUTOFF, Params.KCI_EPSILON, Params.KCI_NUM_BOOTSTRAPS, Params.KCI_USE_APPROMATION,
            Params.KERNEL_MULTIPLIER, Params.KERNEL_REGRESSION_SAMPLE_SIZE, Params.KERNEL_TYPE, Params.KERNEL_WIDTH,
            Params.LATENT_MEASURED_IMPURE_PARENTS, Params.LOWER_BOUND, Params.MAX_CATEGORIES, Params.MAX_DEGREE,
            Params.MAX_DISTINCT_VALUES_DISCRETE, Params.MAX_INDEGREE, Params.MAX_ITERATIONS, Params.MAX_OUTDEGREE,
            Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH, Params.MAX_PATH_LENGTH, Params.MAXIT, Params.MEAN_HIGH,
            Params.MEAN_LOW, Params.MEASURED_MEASURED_IMPURE_ASSOCIATIONS, Params.MEASURED_MEASURED_IMPURE_PARENTS,
            Params.MEASUREMENT_MODEL_DEGREE, Params.MEASUREMENT_VARIANCE, Params.MGM_PARAM1, Params.MGM_PARAM2, Params.MGM_PARAM3,
            Params.MIN_CATEGORIES, Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE, Params.NUM_BASIS_FUNCTIONS,
            Params.NUM_BSC_BOOTSTRAP_SAMPLES, Params.NUM_CATEGORIES, Params.NUM_CATEGORIES_TO_DISCRETIZE, Params.NUM_LAGS,
            Params.NUM_LATENTS, Params.NUM_MEASURES, Params.NUM_RANDOMIZED_SEARCH_MODELS, Params.NUM_RUNS,
            Params.NUM_STRUCTURAL_EDGES, Params.NUM_STRUCTURAL_NODES, Params.NUMBER_RESAMPLING,
            Params.ORIENT_TOWARD_DCONNECTIONS, Params.ORIENT_VISIBLE_FEEDBACK_LOOPS, Params.OUTPUT_RBD,
            Params.PENALTY_DISCOUNT, Params.PERCENT_DISCRETE, Params.PERCENT_RESAMPLE_SIZE, Params.POSSIBLE_DSEP_DONE,
            Params.PROB_CYCLE, Params.PROB_TWO_CYCLE, Params.RANDOM_SELECTION_SIZE, Params.RANDOMIZE_COLUMNS,
            Params.RCIT_NUM_FEATURES, Params.RESAMPLING_ENSEMBLE, Params.RESAMPLING_WITH_REPLACEMENT, Params.PRIOR_EQUIVALENT_SAMPLE_SIZE,
            Params.SAMPLE_SIZE, Params.SAVE_LATENT_VARS, Params.SCALE_FREE_ALPHA, Params.SCALE_FREE_BETA, Params.SCALE_FREE_DELTA_IN,
            Params.SCALE_FREE_DELTA_OUT, Params.SELF_LOOP_COEF, Params.SKIP_NUM_RECORDS, Params.STABLE_FAS, Params.STANDARDIZE,
            Params.STRUCTURE_PRIOR, Params.SYMMETRIC_FIRST_STEP, Params.TARGET_NAME, Params.THR, Params.THRESHOLD_FOR_NUM_EIGENVALUES,
            Params.THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH, Params.THRESHOLD_NO_RANDOM_DATA_SEARCH, Params.TWO_CYCLE_ALPHA,
            Params.UPPER_BOUND, Params.USE_CORR_DIFF_ADJACENCIES, Params.USE_FAS_ADJACENCIES, Params.USE_GAP,
            Params.USE_MAX_P_ORIENTATION_HEURISTIC, Params.USE_SKEW_ADJACENCIES, Params.USE_WISHART, Params.VAR_HIGH,
            Params.VAR_LOW, Params.VERBOSE
    ));

    private static final Set<String> BOOTSTRAPPING_PARAMS = new HashSet<>(Arrays.asList(
            Params.ADD_ORIGINAL_DATASET,
            Params.NUMBER_RESAMPLING,
            Params.PERCENT_RESAMPLE_SIZE,
            Params.RESAMPLING_ENSEMBLE,
            Params.RESAMPLING_WITH_REPLACEMENT
    ));

    private Params() {
    }

    public static Set<String> getAlgorithmParameters(Algorithm algorithm) {
        return new HashSet<>(algorithm.getParameters());
    }

    public static Set<String> getTestParameters(Algorithm algorithm) {
        return (algorithm instanceof TakesIndependenceWrapper)
                ? new HashSet<>(((TakesIndependenceWrapper) algorithm).getIndependenceWrapper().getParameters())
                : Collections.emptySet();
    }

    public static Set<String> getScoreParameters(Algorithm algorithm) {
        return (algorithm instanceof UsesScoreWrapper)
                ? new HashSet<>(((UsesScoreWrapper) algorithm).getScoreWrapper().getParameters())
                : Collections.emptySet();
    }

    public static Set<String> getBootstrappingParameters(Algorithm algorithm) {
        return (algorithm.getClass().isAnnotationPresent(Bootstrapping.class))
                ? Params.BOOTSTRAPPING_PARAMS
                : Collections.emptySet();
    }

    public static Set<String> getParameters() {
        return Params.ALL_PARAMS_IN_HTML_MANUAL;
    }

}
