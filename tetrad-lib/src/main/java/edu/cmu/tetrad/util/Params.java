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
import java.util.stream.Collectors;

/**
 *
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
    public static final String TWO_CYCLE_TESTING_ALPHA = "twoCycleTestingAlpha";
    public static final String ACCEPTANCE_PROPORTION = "acceptanceProportion";
    public static final String FASK_DELTA = "faskDelta";
    public static final String FASK_LEFT_RIGHT_RULE = "faskLeftRightRule";
    public static final String FASK_LINEARITY_ASSUMED = "faskAssumeLinearity";
    public static final String FASK_ADJACENCY_METHOD = "faskAdjacencyMethod";
    public static final String FASK_NONEMPIRICAL = "faskNonempirical";
    public static final String FAITHFULNESS_ASSUMED = "faithfulnessAssumed";
    public static final String FAS_RULE = "fasRule";
    public static final String FAST_ICA_A = "fastIcaA";
    public static final String FAST_ICA_MAX_ITER = "fastIcaMaxIter";
    public static final String FAST_ICA_TOLERANCE = "fastIcaTolerance";
    public static final String FDR_Q = "fdrQ";
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
    public static final String NUM_RANDOMIZED_SEARCH_MODELS = "numRandomizedSearchModels";
    public static final String NUM_RUNS = "numRuns";
    public static final String NUM_STRUCTURAL_EDGES = "numStructuralEdges";
    public static final String NUM_STRUCTURAL_NODES = "numStructuralNodes";
    public static final String NUMBER_RESAMPLING = "numberResampling";
    public static final String ORIENT_TOWARD_DCONNECTIONS = "orientTowardDConnections";
    public static final String ORIENT_VISIBLE_FEEDBACK_LOOPS = "orientVisibleFeedbackLoops";
    public static final String OUTPUT_RBD = "outputRBD";
    public static final String PENALTY_DISCOUNT = "penaltyDiscount";
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
    public static final String TDEPTH = "tDepth";
    public static final String TESTWISE_DELETION = "testwiseDeletion";
    public static final String THR = "thr";
    public static final String THRESHOLD_FOR_NUM_EIGENVALUES = "thresholdForNumEigenvalues";
    public static final String THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH = "thresholdNoRandomConstrainSearch";
    public static final String THRESHOLD_NO_RANDOM_DATA_SEARCH = "thresholdNoRandomDataSearch";
    public static final String TURNING = "turning";
    public static final String TWO_CYCLE_ALPHA = "twoCycleAlpha";
    public static final String UPPER_BOUND = "upperBound";
    public static final String USE_CORR_DIFF_ADJACENCIES = "useCorrDiffAdjacencies";
    public static final String USE_FAS_ADJACENCIES = "useFasAdjacencies";
    public static final String USE_FDR_FOR_INDEPENDENCE = "useFdrForIndependence";
    public static final String USE_GAP = "useGap";
    public static final String USE_MAX_P_ORIENTATION_HEURISTIC = "useMaxPOrientationHeuristic";
    public static final String USE_SELLKE_ADJUSTMENT = "useSellkeAdjustment";
    public static final String USE_SKEW_ADJACENCIES = "useSkewAdjacencies";
    public static final String USE_WISHART = "useWishart";
    public static final String VAR_HIGH = "varHigh";
    public static final String VAR_LOW = "varLow";
    public static final String VERBOSE = "verbose";

    // System prameters that are not supposed to put in the HTML manual documentation
    public static final String PRINT_STREAM = "printStream";
    public static final String SEM_BIC_RULE = "semBicRule";
    public static final String SEM_BIC_STRUCTURE_PRIOR = "semBicStructurePrior";

    // All parameters that are found in HTML manual documentation
    private static final Set<String> ALL_PARAMS_IN_HTML_MANUAL = new HashSet<>(Arrays.asList(
            ADD_ORIGINAL_DATASET, ALPHA, APPLY_R1, AVG_DEGREE, BASIS_TYPE,
            CCI_SCORE_ALPHA, CG_EXACT, COEF_HIGH, COEF_LOW, COEF_SYMMETRIC,
            COLLIDER_DISCOVERY_RULE, COMPLETE_RULE_SET_USED, CONCURRENT_FAS,
            CONFLICT_RULE, CONNECTED, COV_HIGH, COV_LOW, COV_SYMMETRIC,
            CUTOFF_CONSTRAIN_SEARCH, CUTOFF_DATA_SEARCH, CUTOFF_IND_TEST,
            DATA_TYPE, DEPTH, DETERMINISM_THRESHOLD, DIFFERENT_GRAPHS, DISCRETIZE,
            DO_COLLIDER_ORIENTATION, ERRORS_NORMAL, SKEW_EDGE_THRESHOLD,
            FAITHFULNESS_ASSUMED, FAS_RULE, FISHER_EPSILON, GENERAL_SEM_ERROR_TEMPLATE,
            GENERAL_SEM_FUNCTION_TEMPLATE_LATENT, GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED,
            GENERAL_SEM_PARAMETER_TEMPLATE, IA, INCLUDE_NEGATIVE_COEFS,
            INCLUDE_NEGATIVE_SKEWS_FOR_BETA, INCLUDE_POSITIVE_COEFS,
            INCLUDE_POSITIVE_SKEWS_FOR_BETA, INCLUDE_STRUCTURE_MODEL,
            INTERVAL_BETWEEN_RECORDINGS, INTERVAL_BETWEEN_SHOCKS, IPEN, IS, ITR,
            KCI_ALPHA, KCI_CUTOFF, KCI_EPSILON, KCI_NUM_BOOTSTRAPS, KCI_USE_APPROMATION,
            KERNEL_MULTIPLIER, KERNEL_REGRESSION_SAMPLE_SIZE, KERNEL_TYPE, KERNEL_WIDTH,
            LATENT_MEASURED_IMPURE_PARENTS, LOWER_BOUND, MAX_CATEGORIES, MAX_DEGREE,
            MAX_DISTINCT_VALUES_DISCRETE, MAX_INDEGREE, MAX_ITERATIONS, MAX_OUTDEGREE,
            MAX_P_ORIENTATION_MAX_PATH_LENGTH, MAX_PATH_LENGTH, MAXIT, MEAN_HIGH,
            MEAN_LOW, MEASURED_MEASURED_IMPURE_ASSOCIATIONS, MEASURED_MEASURED_IMPURE_PARENTS,
            MEASUREMENT_MODEL_DEGREE, MEASUREMENT_VARIANCE, MGM_PARAM1, MGM_PARAM2, MGM_PARAM3,
            MIN_CATEGORIES, NO_RANDOMLY_DETERMINED_INDEPENDENCE, NUM_BASIS_FUNCTIONS,
            NUM_BSC_BOOTSTRAP_SAMPLES, NUM_CATEGORIES, NUM_CATEGORIES_TO_DISCRETIZE, NUM_LAGS,
            NUM_LATENTS, NUM_MEASURES, NUM_RANDOMIZED_SEARCH_MODELS, NUM_RUNS,
            NUM_STRUCTURAL_EDGES, NUM_STRUCTURAL_NODES, NUMBER_RESAMPLING,
            ORIENT_TOWARD_DCONNECTIONS, ORIENT_VISIBLE_FEEDBACK_LOOPS, OUTPUT_RBD,
            PENALTY_DISCOUNT, PERCENT_DISCRETE, PERCENT_RESAMPLE_SIZE, POSSIBLE_DSEP_DONE,
            PROB_CYCLE, PROB_TWO_CYCLE, RANDOM_SELECTION_SIZE, RANDOMIZE_COLUMNS,
            RCIT_NUM_FEATURES, RESAMPLING_ENSEMBLE, RESAMPLING_WITH_REPLACEMENT, PRIOR_EQUIVALENT_SAMPLE_SIZE,
            SAMPLE_SIZE, SAVE_LATENT_VARS, SCALE_FREE_ALPHA, SCALE_FREE_BETA, SCALE_FREE_DELTA_IN,
            SCALE_FREE_DELTA_OUT, SELF_LOOP_COEF, SKIP_NUM_RECORDS, STABLE_FAS, STANDARDIZE,
            STRUCTURE_PRIOR, SYMMETRIC_FIRST_STEP, TARGET_NAME, THR, THRESHOLD_FOR_NUM_EIGENVALUES,
            THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH, THRESHOLD_NO_RANDOM_DATA_SEARCH, TWO_CYCLE_ALPHA,
            UPPER_BOUND, USE_CORR_DIFF_ADJACENCIES, USE_FAS_ADJACENCIES, USE_GAP,
            USE_MAX_P_ORIENTATION_HEURISTIC, USE_SKEW_ADJACENCIES, USE_WISHART, VAR_HIGH,
            VAR_LOW, VERBOSE
    ));

    private static final Set<String> BOOTSTRAPPING_PARAMS = new HashSet<>(Arrays.asList(
            ADD_ORIGINAL_DATASET,
            NUMBER_RESAMPLING,
            PERCENT_RESAMPLE_SIZE,
            RESAMPLING_ENSEMBLE,
            RESAMPLING_WITH_REPLACEMENT
    ));

    private Params() {
    }

    public static Set<String> getAlgorithmParameters(Algorithm algorithm) {
        return algorithm.getParameters()
                .stream().collect(Collectors.toSet());
    }

    public static Set<String> getTestParameters(Algorithm algorithm) {
        return (algorithm instanceof TakesIndependenceWrapper)
                ? ((TakesIndependenceWrapper) algorithm).getIndependenceWrapper().getParameters()
                        .stream().collect(Collectors.toSet())
                : Collections.emptySet();
    }

    public static Set<String> getScoreParameters(Algorithm algorithm) {
        return (algorithm instanceof UsesScoreWrapper)
                ? ((UsesScoreWrapper) algorithm).getScoreWrapper().getParameters()
                        .stream().collect(Collectors.toSet())
                : Collections.emptySet();
    }

    public static Set<String> getBootstrappingParameters(Algorithm algorithm) {
        return (algorithm.getClass().isAnnotationPresent(Bootstrapping.class))
                ? BOOTSTRAPPING_PARAMS
                : Collections.emptySet();
    }

    public static final Set<String> getParameters() {
        return ALL_PARAMS_IN_HTML_MANUAL;
    }

}
