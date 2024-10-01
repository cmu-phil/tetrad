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
 * @version $Id: $Id
 */
public final class Params {

    /**
     * Constant <code>ADD_ORIGINAL_DATASET="addOriginalDataset"</code>
     */
    public static final String ADD_ORIGINAL_DATASET = "addOriginalDataset";
    /**
     * Constant <code>ALPHA="alpha"</code>
     */
    public static final String ALPHA = "alpha";
    /**
     * Constant <code>APPLY_R1="applyR1"</code>
     */
    public static final String APPLY_R1 = "applyR1";
    /**
     * Constant <code>AVG_DEGREE="avgDegree"</code>
     */
    public static final String AVG_DEGREE = "avgDegree";
    /**
     * Constant <code>BASIS_TYPE="basisType"</code>
     */
    public static final String BASIS_TYPE = "basisType";
    /**
     * Constant <code>CCI_SCORE_ALPHA="cciScoreAlpha"</code>
     */
    public static final String CCI_SCORE_ALPHA = "cciScoreAlpha";
    /**
     * Constant <code>CG_EXACT="cgExact"</code>
     */
    public static final String CG_EXACT = "cgExact";
    /**
     * Constant <code>COEF_HIGH="coefHigh"</code>
     */
    public static final String COEF_HIGH = "coefHigh";
    /**
     * Constant <code>COEF_LOW="coefLow"</code>
     */
    public static final String COEF_LOW = "coefLow";
    /**
     * Constant <code>COEF_SYMMETRIC="coefSymmetric"</code>
     */
    public static final String COEF_SYMMETRIC = "coefSymmetric";
    /**
     * Constant <code>COLLIDER_DISCOVERY_RULE="colliderDiscoveryRule"</code>
     */
    public static final String COLLIDER_DISCOVERY_RULE = "colliderDiscoveryRule";
    /**
     * Constant <code>COMPLETE_RULE_SET_USED="completeRuleSetUsed"</code>
     */
    public static final String COMPLETE_RULE_SET_USED = "completeRuleSetUsed";
    /**
     * Constant <code>SEPSET_FINDER_METHOD="sepsetFinderMethod"</code>
     */
    public static final String SEPSET_FINDER_METHOD = "sepsetFinderMethod";
    /**
     * Constant <code>CONCURRENT_FAS="concurrentFAS"</code>
     */
    public static final String CONCURRENT_FAS = "concurrentFAS";
    /**
     * Constant <code>CONFLICT_RULE="conflictRule"</code>
     */
    public static final String CONFLICT_RULE = "conflictRule";
    /**
     * Constant <code>GUARANTEE_CPDAG="guaranteeCpdag"</code>
     */
    public static final String GUARANTEE_CPDAG = "guaranteeCpdag";
    /**
     * Constant <code>CONNECTED="connected"</code>
     */
    public static final String CONNECTED = "connected";
    /**
     * Constant <code>COV_HIGH="covHigh"</code>
     */
    public static final String COV_HIGH = "covHigh";
    /**
     * Constant <code>COV_LOW="covLow"</code>
     */
    public static final String COV_LOW = "covLow";
    /**
     * Constant <code>COV_SYMMETRIC="covSymmetric"</code>
     */
    public static final String COV_SYMMETRIC = "covSymmetric";
    /**
     * Constant <code>CUTOFF_CONSTRAIN_SEARCH="cutoffConstrainSearch"</code>
     */
    public static final String CUTOFF_CONSTRAIN_SEARCH = "cutoffConstrainSearch";
    /**
     * Constant <code>CUTOFF_DATA_SEARCH="cutoffDataSearch"</code>
     */
    public static final String CUTOFF_DATA_SEARCH = "cutoffDataSearch";
    /**
     * Constant <code>CUTOFF_IND_TEST="cutoffIndTest"</code>
     */
    public static final String CUTOFF_IND_TEST = "cutoffIndTest";
    /**
     * Constant <code>DATA_TYPE="dataType"</code>
     */
    public static final String DATA_TYPE = "dataType";
    /**
     * Constant <code>DEPTH="depth"</code>
     */
    public static final String DEPTH = "depth";
    /**
     * Constant <code>DETERMINISM_THRESHOLD="determinismThreshold"</code>
     */
    public static final String DETERMINISM_THRESHOLD = "determinismThreshold";
    /**
     * Constant <code>DIFFERENT_GRAPHS="differentGraphs"</code>
     */
    public static final String DIFFERENT_GRAPHS = "differentGraphs";
    /**
     * Constant <code>DISCRETIZE="discretize"</code>
     */
    public static final String DISCRETIZE = "discretize";
    /**
     * Constant <code>DO_COLLIDER_ORIENTATION="doColliderOrientation"</code>
     */
    public static final String DO_COLLIDER_ORIENTATION = "doColliderOrientation";
    /**
     * Constant <code>ERRORS_NORMAL="errorsNormal"</code>
     */
    public static final String ERRORS_NORMAL = "errorsNormal";
    /**
     * Constant <code>SKEW_EDGE_THRESHOLD="skewEdgeThreshold"</code>
     */
    public static final String SKEW_EDGE_THRESHOLD = "skewEdgeThreshold";
    /**
     * Constant <code>TWO_CYCLE_SCREENING_THRESHOLD="twoCycleScreeningThreshold"</code>
     */
    public static final String TWO_CYCLE_SCREENING_THRESHOLD = "twoCycleScreeningThreshold";
    /**
     * Constant <code>FASK_DELTA="faskDelta"</code>
     */
    public static final String FASK_DELTA = "faskDelta";
    /**
     * Constant <code>FASK_LEFT_RIGHT_RULE="faskLeftRightRule"</code>
     */
    public static final String FASK_LEFT_RIGHT_RULE = "faskLeftRightRule";
    /**
     * Constant <code>FASK_ADJACENCY_METHOD="faskAdjacencyMethod"</code>
     */
    public static final String FASK_ADJACENCY_METHOD = "faskAdjacencyMethod";
    /**
     * Constant <code>FASK_NONEMPIRICAL="faskNonempirical"</code>
     */
    public static final String FASK_NONEMPIRICAL = "faskNonempirical";
    /**
     * Constant <code>FAITHFULNESS_ASSUMED="faithfulnessAssumed"</code>
     */
    public static final String FAITHFULNESS_ASSUMED = "faithfulnessAssumed";
    /**
     * Constant <code>FAS_RULE="fasRule"</code>
     */
    public static final String FAS_RULE = "fasRule";
    /**
     * Constant <code>FAST_ICA_A="fastIcaA"</code>
     */
    public static final String FAST_ICA_A = "fastIcaA";
    /**
     * Constant <code>FAST_ICA_MAX_ITER="fastIcaMaxIter"</code>
     */
    public static final String FAST_ICA_MAX_ITER = "fastIcaMaxIter";
    /**
     * Constant <code>FAST_ICA_TOLERANCE="fastIcaTolerance"</code>
     */
    public static final String FAST_ICA_TOLERANCE = "fastIcaTolerance";
    /**
     * Constant <code>THRESHOLD_B="thresholdBHat"</code>
     */
    public static final String THRESHOLD_B = "thresholdBHat";
    /**
     * Constant <code>GUARANTEE_ACYCLIC="guaranteeAcyclic"</code>
     */
    public static final String GUARANTEE_ACYCLIC = "guaranteeAcyclic";
    /**
     * Constant <code>THRESHOLD_SPINE="thresholdSpine"</code>
     */
    public static final String THRESHOLD_W = "thresholdW";
    /**
     * Constant <code>ORIENTATION_ALPHA="orientationAlpha"</code>
     */
    public static final String ORIENTATION_ALPHA = "orientationAlpha";
    /**
     * Constant <code>FISHER_EPSILON="fisherEpsilon"</code>
     */
    public static final String FISHER_EPSILON = "fisherEpsilon";
    /**
     * Constant <code>GENERAL_SEM_ERROR_TEMPLATE="generalSemErrorTemplate"</code>
     */
    public static final String GENERAL_SEM_ERROR_TEMPLATE = "generalSemErrorTemplate";
    /**
     * Constant <code>GENERAL_SEM_FUNCTION_TEMPLATE_LATENT="generalSemFunctionTemplateLatent"</code>
     */
    public static final String GENERAL_SEM_FUNCTION_TEMPLATE_LATENT = "generalSemFunctionTemplateLatent";
    /**
     * Constant <code>GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED="generalSemFunctionTemplateMeasured"</code>
     */
    public static final String GENERAL_SEM_FUNCTION_TEMPLATE_MEASURED = "generalSemFunctionTemplateMeasured";
    /**
     * Constant <code>GENERAL_SEM_PARAMETER_TEMPLATE="generalSemParameterTemplate"</code>
     */
    public static final String GENERAL_SEM_PARAMETER_TEMPLATE = "generalSemParameterTemplate";
    /**
     * Constant <code>GUARANTEE_IID="guaranteeIid"</code>
     */
    public static final String GUARANTEE_IID = "guaranteeIid";
    /**
     * Constant <code>IA="ia"</code>
     */
    public static final String IA = "ia";
    /**
     * Constant <code>INCLUDE_NEGATIVE_COEFS="includeNegativeCoefs"</code>
     */
    public static final String INCLUDE_NEGATIVE_COEFS = "includeNegativeCoefs";
    /**
     * Constant <code>INCLUDE_NEGATIVE_SKEWS_FOR_BETA="includeNegativeSkewsForBeta"</code>
     */
    public static final String INCLUDE_NEGATIVE_SKEWS_FOR_BETA = "includeNegativeSkewsForBeta";
    /**
     * Constant <code>INCLUDE_POSITIVE_COEFS="includePositiveCoefs"</code>
     */
    public static final String INCLUDE_POSITIVE_COEFS = "includePositiveCoefs";
    /**
     * Constant <code>INCLUDE_POSITIVE_SKEWS_FOR_BETA="includePositiveSkewsForBeta"</code>
     */
    public static final String INCLUDE_POSITIVE_SKEWS_FOR_BETA = "includePositiveSkewsForBeta";
    /**
     * Constant <code>INCLUDE_STRUCTURE_MODEL="include_structure_model"</code>
     */
    public static final String INCLUDE_STRUCTURE_MODEL = "include_structure_model";
    /**
     * Constant <code>INTERVAL_BETWEEN_RECORDINGS="intervalBetweenRecordings"</code>
     */
    public static final String INTERVAL_BETWEEN_RECORDINGS = "intervalBetweenRecordings";
    /**
     * Constant <code>INTERVAL_BETWEEN_SHOCKS="intervalBetweenShocks"</code>
     */
    public static final String INTERVAL_BETWEEN_SHOCKS = "intervalBetweenShocks";
    /**
     * Constant <code>IPEN="ipen"</code>
     */
    public static final String IPEN = "ipen";
    /**
     * Constant <code>IS="is"</code>
     */
    public static final String IS = "is";
    /**
     * Constant <code>ITR="itr"</code>
     */
    public static final String ITR = "itr";
    /**
     * Constant <code>KCI_ALPHA="kciAlpha"</code>
     */
    public static final String KCI_ALPHA = "kciAlpha";
    /**
     * Constant <code>KCI_CUTOFF="kciCutoff"</code>
     */
    public static final String KCI_CUTOFF = "kciCutoff";
    /**
     * Constant <code>KCI_EPSILON="kciEpsilon"</code>
     */
    public static final String KCI_EPSILON = "kciEpsilon";
    /**
     * Constant <code>KCI_NUM_BOOTSTRAPS="kciNumBootstraps"</code>
     */
    public static final String KCI_NUM_BOOTSTRAPS = "kciNumBootstraps";
    /**
     * Constant <code>KCI_USE_APPROXIMATION="kciUseApproximation"</code>
     */
    public static final String KCI_USE_APPROXIMATION = "kciUseApproximation";
    /**
     * Constant <code>KERNEL_MULTIPLIER="kernelMultiplier"</code>
     */
    public static final String KERNEL_MULTIPLIER = "kernelMultiplier";
    /**
     * Constant <code>KERNEL_REGRESSION_SAMPLE_SIZE="kernelRegressionSampleSize"</code>
     */
    public static final String KERNEL_REGRESSION_SAMPLE_SIZE = "kernelRegressionSampleSize";
    /**
     * Constant <code>KERNEL_TYPE="kernelType"</code>
     */
    public static final String KERNEL_TYPE = "kernelType";
    /**
     * Constant <code>KERNEL_WIDTH="kernelWidth"</code>
     */
    public static final String KERNEL_WIDTH = "kernelWidth";
    /**
     * Constant <code>LATENT_MEASURED_IMPURE_PARENTS="latentMeasuredImpureParents"</code>
     */
    public static final String LATENT_MEASURED_IMPURE_PARENTS = "latentMeasuredImpureParents";
    /**
     * Constant <code>LOWER_BOUND="lowerBound"</code>
     */
    public static final String LOWER_BOUND = "lowerBound";
    /**
     * Constant <code>MAX_CATEGORIES="maxCategories"</code>
     */
    public static final String MAX_CATEGORIES = "maxCategories";
    /**
     * Constant <code>MAX_DEGREE="maxDegree"</code>
     */
    public static final String MAX_DEGREE = "maxDegree";
    /**
     * Constant <code>MAX_DISTINCT_VALUES_DISCRETE="maxDistinctValuesDiscrete"</code>
     */
    public static final String MAX_DISTINCT_VALUES_DISCRETE = "maxDistinctValuesDiscrete";
    /**
     * Constant <code>MAX_INDEGREE="maxIndegree"</code>
     */
    public static final String MAX_INDEGREE = "maxIndegree";
    /**
     * Constant <code>MAX_ITERATIONS="maxIterations"</code>
     */
    public static final String MAX_ITERATIONS = "maxIterations";
    /**
     * Constant <code>MAX_OUTDEGREE="maxOutdegree"</code>
     */
    public static final String MAX_OUTDEGREE = "maxOutdegree";
    /**
     * Constant <code>MAX_PATH_LENGTH="maxPathLength"</code>
     */
    public static final String MAX_DISCRIMINATING_PATH_LENGTH = "maxDiscriminatingPathLength";
    /**
     * Constant <code>MAXIT="maxit"</code>
     */
    public static final String MAXIT = "maxit";
    /**
     * Constant <code>MEAN_HIGH="meanHigh"</code>
     */
    public static final String MEAN_HIGH = "meanHigh";
    /**
     * Constant <code>MEAN_LOW="meanLow"</code>
     */
    public static final String MEAN_LOW = "meanLow";
    /**
     * Constant <code>MEASURED_MEASURED_IMPURE_ASSOCIATIONS="measuredMeasuredImpureAssociations"</code>
     */
    public static final String MEASURED_MEASURED_IMPURE_ASSOCIATIONS = "measuredMeasuredImpureAssociations";
    /**
     * Constant <code>MEASURED_MEASURED_IMPURE_PARENTS="measuredMeasuredImpureParents"</code>
     */
    public static final String MEASURED_MEASURED_IMPURE_PARENTS = "measuredMeasuredImpureParents";
    /**
     * Constant <code>MEASUREMENT_MODEL_DEGREE="measurementModelDegree"</code>
     */
    public static final String MEASUREMENT_MODEL_DEGREE = "measurementModelDegree";
    /**
     * Constant <code>MEASUREMENT_VARIANCE="measurementVariance"</code>
     */
    public static final String MEASUREMENT_VARIANCE = "measurementVariance";
    /**
     * Constant <code>MGM_PARAM1="mgmParam1"</code>
     */
    public static final String MGM_PARAM1 = "mgmParam1";
    /**
     * Constant <code>MGM_PARAM2="mgmParam2"</code>
     */
    public static final String MGM_PARAM2 = "mgmParam2";
    /**
     * Constant <code>MGM_PARAM3="mgmParam3"</code>
     */
    public static final String MGM_PARAM3 = "mgmParam3";
    /**
     * Constant <code>MIN_CATEGORIES="minCategories"</code>
     */
    public static final String MIN_CATEGORIES = "minCategories";
    /**
     * Constant <code>NO_RANDOMLY_DETERMINED_INDEPENDENCE="noRandomlyDeterminedIndependence"</code>
     */
    public static final String NO_RANDOMLY_DETERMINED_INDEPENDENCE = "noRandomlyDeterminedIndependence";
    /**
     * Constant <code>NUM_BASIS_FUNCTIONS="numBasisFunctions"</code>
     */
    public static final String NUM_BASIS_FUNCTIONS = "numBasisFunctions";
    /**
     * Constant <code>NUM_BSC_BOOTSTRAP_SAMPLES="numBscBootstrapSamples"</code>
     */
    public static final String NUM_BSC_BOOTSTRAP_SAMPLES = "numBscBootstrapSamples";
    /**
     * Constant <code>NUM_CATEGORIES="numCategories"</code>
     */
    public static final String NUM_CATEGORIES = "numCategories";
    /**
     * Constant <code>NUM_CATEGORIES_TO_DISCRETIZE="numCategoriesToDiscretize"</code>
     */
    public static final String NUM_CATEGORIES_TO_DISCRETIZE = "numCategoriesToDiscretize";
    /**
     * Constant <code>NUM_LAGS="numLags"</code>
     */
    public static final String NUM_LAGS = "numLags";
    /**
     * Constant <code>NUM_LATENTS="numLatents"</code>
     */
    public static final String NUM_LATENTS = "numLatents";
    /**
     * Constant <code>NUM_MEASURES="numMeasures"</code>
     */
    public static final String NUM_MEASURES = "numMeasures";
    /**
     * Constant <code>PROBABILITY_OF_EDGE="probabilityOfEdge"</code>
     */
    public static final String PROBABILITY_OF_EDGE = "probabilityOfEdge";
    /**
     * Constant <code>NUM_RANDOMIZED_SEARCH_MODELS="numRandomizedSearchModels"</code>
     */
    public static final String NUM_RANDOMIZED_SEARCH_MODELS = "numRandomizedSearchModels";
    /**
     * Constant <code>NUM_RUNS="numRuns"</code>
     */
    public static final String NUM_RUNS = "numRuns";
    /**
     * Constant <code>NUM_STRUCTURAL_EDGES="numStructuralEdges"</code>
     */
    public static final String NUM_STRUCTURAL_EDGES = "numStructuralEdges";
    /**
     * Constant <code>NUM_STRUCTURAL_NODES="numStructuralNodes"</code>
     */
    public static final String NUM_STRUCTURAL_NODES = "numStructuralNodes";
    /**
     * Constant <code>NUMBER_RESAMPLING="numberResampling"</code>
     */
    public static final String NUMBER_RESAMPLING = "numberResampling";
    /**
     * Constant <code>ORIENT_TOWARD_DCONNECTIONS="orientTowardDConnections"</code>
     */
    public static final String ORIENT_TOWARD_DCONNECTIONS = "orientTowardDConnections";
    /**
     * Constant <code>ORIENT_VISIBLE_FEEDBACK_LOOPS="orientVisibleFeedbackLoops"</code>
     */
    public static final String ORIENT_VISIBLE_FEEDBACK_LOOPS = "orientVisibleFeedbackLoops";
    /**
     * Constant <code>OUTPUT_RBD="outputRBD"</code>
     */
    public static final String OUTPUT_RBD = "outputRBD";
    /**
     * Constant <code>PARALLELIZED="parallelized"</code>
     */
    public static final String PARALLELIZED = "parallelized";
    /**
     * Constant <code>PENALTY_DISCOUNT="penaltyDiscount"</code>
     */
    public static final String PENALTY_DISCOUNT = "penaltyDiscount";
    /**
     * Constant <code>PENALTY_DISCOUNT_ZS="penaltyDiscountZs"</code>
     */
    public static final String PENALTY_DISCOUNT_ZS = "penaltyDiscountZs";
    /**
     * Constant <code>EBIC_GAMMA="ebicGamma"</code>
     */
    public static final String EBIC_GAMMA = "ebicGamma";
    /**
     * Constant <code>PERCENT_DISCRETE="percentDiscrete"</code>
     */
    public static final String PERCENT_DISCRETE = "percentDiscrete";
    /**
     * Constant <code>PERCENT_RESAMPLE_SIZE="percentResampleSize"</code>
     */
    public static final String PERCENT_RESAMPLE_SIZE = "percentResampleSize";
    /**
     * Constant <code>POSSIBLE_MSEP_DONE="possibleMsepDone"</code>
     */
    public static final String POSSIBLE_MSEP_DONE = "possibleMsepDone";
    /**
     * Constant <code>PROB_CYCLE="probCycle"</code>
     */
    public static final String PROB_CYCLE = "probCycle";
    /**
     * Constant <code>PROB_TWO_CYCLE="probTwoCycle"</code>
     */
    public static final String PROB_TWO_CYCLE = "probTwoCycle";
    /**
     * Constant <code>RANDOM_SELECTION_SIZE="randomSelectionSize"</code>
     */
    public static final String RANDOM_SELECTION_SIZE = "randomSelectionSize";
    /**
     * Constant <code>RANDOMIZE_COLUMNS="randomizeColumns"</code>
     */
    public static final String RANDOMIZE_COLUMNS = "randomizeColumns";
    /**
     * Constant <code>RCIT_NUM_FEATURES="rcitNumFeatures"</code>
     */
    public static final String RCIT_NUM_FEATURES = "rcitNumFeatures";
    /**
     * Constant <code>RESAMPLING_ENSEMBLE="resamplingEnsemble"</code>
     */
    public static final String RESAMPLING_ENSEMBLE = "resamplingEnsemble";
    /**
     * Constant <code>RESAMPLING_WITH_REPLACEMENT="resamplingWithReplacement"</code>
     */
    public static final String RESAMPLING_WITH_REPLACEMENT = "resamplingWithReplacement";
    /**
     * Constant <code>PRIOR_EQUIVALENT_SAMPLE_SIZE="priorEquivalentSampleSize"</code>
     */
    public static final String PRIOR_EQUIVALENT_SAMPLE_SIZE = "priorEquivalentSampleSize";
    /**
     * Constant <code>SAMPLE_SIZE="sampleSize"</code>
     */
    public static final String SAMPLE_SIZE = "sampleSize";
    /**
     * Constant <code>SAVE_LATENT_VARS="saveLatentVars"</code>
     */
    public static final String SAVE_LATENT_VARS = "saveLatentVars";
    /**
     * Constant <code>SCALE_FREE_ALPHA="scaleFreeAlpha"</code>
     */
    public static final String SCALE_FREE_ALPHA = "scaleFreeAlpha";
    /**
     * Constant <code>SCALE_FREE_BETA="scaleFreeBeta"</code>
     */
    public static final String SCALE_FREE_BETA = "scaleFreeBeta";
    /**
     * Constant <code>SCALE_FREE_DELTA_IN="scaleFreeDeltaIn"</code>
     */
    public static final String SCALE_FREE_DELTA_IN = "scaleFreeDeltaIn";
    /**
     * Constant <code>SCALE_FREE_DELTA_OUT="scaleFreeDeltaOut"</code>
     */
    public static final String SCALE_FREE_DELTA_OUT = "scaleFreeDeltaOut";
    /**
     * Constant <code>SELF_LOOP_COEF="selfLoopCoef"</code>
     */
    public static final String SELF_LOOP_COEF = "selfLoopCoef";
    /**
     * Constant <code>SKIP_NUM_RECORDS="skipNumRecords"</code>
     */
    public static final String SKIP_NUM_RECORDS = "skipNumRecords";
    /**
     * Constant <code>STABLE_FAS="stableFAS"</code>
     */
    public static final String STABLE_FAS = "stableFAS";
    /**
     * Constant <code>STANDARDIZE="standardize"</code>
     */
    public static final String STANDARDIZE = "standardize";
    /**
     * Constant <code>STRUCTURE_PRIOR="structurePrior"</code>
     */
    public static final String STRUCTURE_PRIOR = "structurePrior";
    /**
     * Constant <code>SYMMETRIC_FIRST_STEP="symmetricFirstStep"</code>
     */
    public static final String SYMMETRIC_FIRST_STEP = "symmetricFirstStep";
    /**
     * Constant <code>TARGET_NAME="targetName"</code>
     */
    public static final String TARGET_NAME = "targetName";
    /**
     * Constant <code>THR="thr"</code>
     */
    public static final String THR = "thr";
    /**
     * Constant <code>THRESHOLD_FOR_NUM_EIGENVALUES="thresholdForNumEigenvalues"</code>
     */
    public static final String THRESHOLD_FOR_NUM_EIGENVALUES = "thresholdForNumEigenvalues";
    /**
     * Constant <code>THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH="thresholdNoRandomConstrainSearch"</code>
     */
    public static final String THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH = "thresholdNoRandomConstrainSearch";
    /**
     * Constant <code>THRESHOLD_NO_RANDOM_DATA_SEARCH="thresholdNoRandomDataSearch"</code>
     */
    public static final String THRESHOLD_NO_RANDOM_DATA_SEARCH = "thresholdNoRandomDataSearch";
    /**
     * Constant <code>TWO_CYCLE_ALPHA="twoCycleAlpha"</code>
     */
    public static final String TWO_CYCLE_ALPHA = "twoCycleAlpha";
    /**
     * Constant <code>UPPER_BOUND="upperBound"</code>
     */
    public static final String UPPER_BOUND = "upperBound";
    /**
     * Constant <code>USE_CORR_DIFF_ADJACENCIES="useCorrDiffAdjacencies"</code>
     */
    public static final String USE_CORR_DIFF_ADJACENCIES = "useCorrDiffAdjacencies";
    /**
     * Constant <code>USE_FAS_ADJACENCIES="useFasAdjacencies"</code>
     */
    public static final String USE_FAS_ADJACENCIES = "useFasAdjacencies";
    /**
     * Constant <code>USE_GAP="useGap"</code>
     */
    public static final String USE_GAP = "useGap";
    /**
     * Constant <code>USE_MAX_P_HEURISTIC="useMaxPHeuristic"</code>
     */
    public static final String USE_MAX_P_HEURISTIC = "useMaxPHeuristic";
    /**
     * Constant <code>USE_MAX_P_ORIENTATION_HEURISTIC="useMaxPOrientationHeuristic"</code>
     */
    public static final String USE_MAX_P_ORIENTATION_HEURISTIC = "useMaxPOrientationHeuristic";
    /**
     * Constant <code>USE_SKEW_ADJACENCIES="useSkewAdjacencies"</code>
     */
    public static final String USE_SKEW_ADJACENCIES = "useSkewAdjacencies";
    /**
     * Constant <code>USE_WISHART="useWishart"</code>
     */
    public static final String USE_WISHART = "useWishart";
    /**
     * Constant <code>CHECK_TYPE="checkType"</code>
     */
    public static final String CHECK_TYPE = "checkType";
    /**
     * Constant <code>VAR_HIGH="varHigh"</code>
     */
    public static final String VAR_HIGH = "varHigh";
    /**
     * Constant <code>VAR_LOW="varLow"</code>
     */
    public static final String VAR_LOW = "varLow";
    /**
     * Constant <code>VERBOSE="verbose"</code>
     */
    public static final String VERBOSE = "verbose";
    /**
     * Constant <code>SEM_BIC_RULE="semBicRule"</code>
     */
    public static final String SEM_BIC_RULE = "semBicRule";
    /**
     * Constant <code>SEM_GIC_RULE="semGicRule"</code>
     */
    public static final String SEM_GIC_RULE = "semGicRule";
    /**
     * Constant <code>SEM_BIC_STRUCTURE_PRIOR="semBicStructurePrior"</code>
     */
    public static final String SEM_BIC_STRUCTURE_PRIOR = "semBicStructurePrior";
    /**
     * Constant <code>POISSON_LAMBDA="poissonLambda"</code>
     */
    public static final String POISSON_LAMBDA = "poissonLambda";
    /**
     * Constant <code>USE_BES="useBes"</code>
     */
    public static final String USE_BES = "useBes";
    /**
     * Constant <code>NUM_STARTS="numStarts"</code>
     */
    public static final String NUM_STARTS = "numStarts";
    /**
     * Constant <code>CACHE_SCORES="cacheScores"</code>
     */
    public static final String CACHE_SCORES = "cacheScores";
    /**
     * Constant <code>BOSS_ALG="bossAlg"</code>
     */
    public static final String BOSS_ALG = "bossAlg";
    /**
     * Constant <code>OUTPUT_CPDAG="outputCpdag"</code>
     */
    public static final String OUTPUT_CPDAG = "outputCpdag";
    /**
     * Constant <code>ZS_RISK_BOUND="zSRiskBound"</code>
     */
    public static final String ZS_RISK_BOUND = "zSRiskBound";
    /**
     * Constant <code>NUM_ROUNDS="numRounds"</code>
     */
    public static final String NUM_ROUNDS = "numRounds";

    // GRASP parameters and flags.
    /**
     * Constant <code>GRASP_CHECK_COVERING="graspCheckCovering"</code>
     */
    public static final String GRASP_CHECK_COVERING = "graspCheckCovering";
    /**
     * Constant <code>GRASP_FORWARD_TUCK_ONLY="graspForwardTuckOnly"</code>
     */
    public static final String GRASP_FORWARD_TUCK_ONLY = "graspForwardTuckOnly";
    /**
     * Constant <code>GRASP_BREAK_AFTER_IMPROVEMENT="graspBreakAFterImprovement"</code>
     */
    public static final String GRASP_BREAK_AFTER_IMPROVEMENT = "graspBreakAFterImprovement";
    /**
     * Constant <code>GRASP_ORDERED_ALG="graspOrderedAlg"</code>
     */
    public static final String GRASP_ORDERED_ALG = "graspOrderedAlg";
    /**
     * Constant <code>GRASP_USE_SCORE="graspUseScore"</code>
     */
    public static final String GRASP_USE_SCORE = "graspUseScore";
    /**
     * Constant <code>GRASP_USE_RASKUTTI_UHLER="graspUseRaskuttiUhler"</code>
     */
    public static final String GRASP_USE_RASKUTTI_UHLER = "graspUseRaskuttiUhler";
    /**
     * Constant <code>USE_DATA_ORDER="useDataOrder"</code>
     */
    public static final String USE_DATA_ORDER = "useDataOrder";
    /**
     * Constant <code>ALLOW_INTERNAL_RANDOMNESS="allowInternalRandomness"</code>
     */
    public static final String ALLOW_INTERNAL_RANDOMNESS = "allowInternalRandomness";
    /**
     * Constant <code>GRASP_DEPTH="graspDepth"</code>
     */
    public static final String GRASP_DEPTH = "graspDepth";
    /**
     * Constant <code>GRASP_SINGULAR_DEPTH="graspSingularDepth"</code>
     */
    public static final String GRASP_SINGULAR_DEPTH = "graspSingularDepth";
    /**
     * Constant <code>GRASP_NONSINGULAR_DEPTH="graspNonSingularDepth"</code>
     */
    public static final String GRASP_NONSINGULAR_DEPTH = "graspNonSingularDepth";
    /**
     * Constant <code>GRASP_TOLERANCE_DEPTH="graspToleranceDepth"</code>
     */
    public static final String GRASP_TOLERANCE_DEPTH = "graspToleranceDepth";
    /**
     * Constant <code>GRASP_ALG="graspAlg"</code>
     */
    public static final String GRASP_ALG = "graspAlg";
    /**
     * Constant <code>TIMEOUT="timeout"</code>
     */
    public static final String TIMEOUT = "timeout";    /**
     * Constant <code>TEST_TIMEOUT="testTimeout"</code>
     */
    public static final String TEST_TIMEOUT = "testTimeout";
    /**
     * Constant <code>GRASP_USE_VP_SCORING="graspUseVpScoring"</code>
     */
    public static final String GRASP_USE_VP_SCORING = "graspUseVpScoring";
    /**
     * Constant <code>SIMULATION_ERROR_TYPE="simulationErrorType"</code>
     */
    public static final String SIMULATION_ERROR_TYPE = "simulationErrorType";
    /**
     * Constant <code>SIMULATION_PARAM1="simulationParam1"</code>
     */
    public static final String SIMULATION_PARAM1 = "simulationParam1";
    /**
     * Constant <code>SIMULATION_PARAM2="simulationParam2"</code>
     */
    public static final String SIMULATION_PARAM2 = "simulationParam2";
    /**
     * Constant <code>SELECTION_MIN_EFFECT="selectionMinEffect"</code>
     */
    public static final String SELECTION_MIN_EFFECT = "selectionMinEffect";
    /**
     * Constant <code>NUM_SUBSAMPLES="numSubsamples"</code>
     */
    public static final String NUM_SUBSAMPLES = "numSubsamples";
    /**
     * Constant <code>TARGETS="targets"</code>
     */
    public static final String TARGETS = "targets";
    /**
     * Constant <code>MB="mb"</code>
     */
    public static final String MB = "mb";
    /**
     * Constant <code>TOP_BRACKET="topBracket"</code>
     */
    public static final String TOP_BRACKET = "topBracket";
    /**
     * Constant <code>TIME_LAG="timeLag"</code>
     */
    public static final String TIME_LAG = "timeLag";
    /**
     * Constant <code>PRECOMPUTE_COVARIANCES="precomputeCovariances"</code>
     */
    public static final String PRECOMPUTE_COVARIANCES = "precomputeCovariances";
    /**
     * Constant <code>IMAGES_META_ALG="imagesMetaAlg"</code>
     */
    public static final String IMAGES_META_ALG = "imagesMetaAlg";

    /**
     * Constant <code>SEED="seed"</code>
     */
    public static final String SEED = "seed";
    /**
     * Constant <code>SIGNIFICANCE_CHECKED="significanceChecked"</code>
     */
    public static final String SIGNIFICANCE_CHECKED = "significanceChecked";
    /**
     * Constant <code>PROB_REMOVE_COLUMN="probRemoveColumn"</code>
     */
    public static final String PROB_REMOVE_COLUMN = "probRemoveColumn";
    /**
     * Constant <code>SAVE_BOOTSTRAP_GRAPHS="saveBootstrapGraphs"</code>
     */
    public static final String SAVE_BOOTSTRAP_GRAPHS = "saveBootstrapGraphs";
    /**
     * Constant <code>LAMBDA1="lambda1"</code>
     */
    public static final String LAMBDA1 = "lambda1";
    /**
     * Constant <code>W_THRESHOLD="wThreshold"</code>
     */
    public static final String W_THRESHOLD = "wThreshold";
    /**
     * Constant <code>CPDAG="cpdag"</code>
     */
    public static final String CPDAG = "cpdag";
    /**
     * Constant <code>TRIMMING_STYLE="trimmingStyle"</code>
     */
    public static final String TRIMMING_STYLE = "trimmingStyle";
    /**
     * Constant <code>NUMBER_OF_EXPANSIONS="numberOfExpansions"</code>
     */
    public static final String NUMBER_OF_EXPANSIONS = "numberOfExpansions";
    /**
     * Constant <code>CSTAR_CPDAG_ALGORITHM="cstarCpdagAlgorithm"</code>
     */
    public static final String CSTAR_CPDAG_ALGORITHM = "cstarCpdagAlgorithm";
    /**
     * Constant <code>FILE_OUT_PATH="fileOutPath"</code>
     */
    public static final String FILE_OUT_PATH = "fileOutPath";
    /**
     * Constant <code>REMOVE_EFFECT_NODES="removeEffectNodes"</code>
     */
    public static final String REMOVE_EFFECT_NODES = "removeEffectNodes";
    /**
     * Constant <code>SAMPLE_STYLE="sampleStyle"</code>
     */
    public static final String SAMPLE_STYLE = "sampleStyle";
    /**
     * Constant <code>NUM_THREADS="numThreads"</code>
     */
    public static final String NUM_THREADS = "numThreads";
    /**
     * Constant <code>BOOTSTRAPPING_NUM_THEADS="bootstrappingNumThreads"</code>
     */
    public static final String BOOTSTRAPPING_NUM_THREADS = "bootstrappingNumThreads";

    /**
     * Constant <code>USE_PSEUDOINVERSE="usePseudoinverse"</code>
     */
    public static final String USE_PSEUDOINVERSE = "usePseudoinverse";
    /**
     * Constant <code>USE_PSEUDOINVERSE_FOR_LATENT="usePseudoinverseForLatent"</code>
     */
    public static final String COMPARE_GRAPH_ALGCOMP = "compareGraphAlgcomp";
    /**
     * Constant <code>COMPARE_GRAPH_ALGCOMP="compareGraphAlgcomp"</code>
     */
    public static final String MIN_SAMPLE_SIZE_PER_CELL = "minSampleSizePerCell";
    /**
     * Constant <code>MAX_SCORE_DROP="maxScoreDrop"</code>
     */
    public static final String MAX_SCORE_DROP = "maxScoreDrop";
    /**
     * Constant <code>GUARANTEE_PAG="guaranteePag"</code>
     */
    public static final String GUARANTEE_PAG = "guaranteePag";
    /**
     * Constant <code>REMOVE_ALMOST_CYCLES="removeAlmostCycles"</code>
     */
    public static final String REMOVE_ALMOST_CYCLES = "removeAlmostCycles";
   /**
     * Constant <code>PC_HEURISTIC="pcHeuristic"</code>
     */
    public static String PC_HEURISTIC = "pcHeuristic";
    /**
     * Constant <code>FCI_LITE_STARTS_WITGH="FciLiteStartsWith"</code>
     */
    public static String FCI_LITE_STARTS_WITH = "fciLiteStartsWith";
    /**
     * Constant <code>EXTRA_EDGE_REMOVAL_STEP="extraEdgeRemovalStep"</code>
     */
    public static String EXTRA_EDGE_REMOVAL_STEP = "extraEdgeRemovalStep";
    /**
     * Constant <code>MAX_BLOCKING_PATH_LENGTH="maxBlockingPathLength"</code>
     */
    public static final String MAX_BLOCKING_PATH_LENGTH = "maxBlockingPathLength";
    /**
     * Constant <code>MAX_SEPSET_SIZE="maxSepsetSize"</code>
     */
    public static final String MAX_SEPSET_SIZE = "maxSepsetSize";
    /**
     * Constant <code>MIN_COUNT_PER_CELL="minCountPerCell"</code>
     */
    public static String MIN_COUNT_PER_CELL = "minCountPerCell";
    /**
     * Constant <code>CELL_COUNT_TYPE="cellCountType"</code>
     */
    public static String CELL_TABLE_TYPE = "cellTableType";


    private Params() {
    }


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
            Params.KCI_ALPHA, Params.KCI_CUTOFF, Params.KCI_EPSILON, Params.KCI_NUM_BOOTSTRAPS, Params.KCI_USE_APPROXIMATION,
            Params.KERNEL_MULTIPLIER, Params.KERNEL_REGRESSION_SAMPLE_SIZE, Params.KERNEL_TYPE, Params.KERNEL_WIDTH,
            Params.LATENT_MEASURED_IMPURE_PARENTS, Params.LOWER_BOUND, Params.MAX_CATEGORIES, Params.MAX_DEGREE,
            Params.MAX_DISTINCT_VALUES_DISCRETE, Params.MAX_INDEGREE, Params.MAX_ITERATIONS, Params.MAX_OUTDEGREE,
            Params.MEAN_LOW, Params.MEASURED_MEASURED_IMPURE_ASSOCIATIONS, Params.MEASURED_MEASURED_IMPURE_PARENTS,
            Params.MEASUREMENT_MODEL_DEGREE, Params.MEASUREMENT_VARIANCE, Params.MGM_PARAM1, Params.MGM_PARAM2, Params.MGM_PARAM3,
            Params.MIN_CATEGORIES, Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE, Params.NUM_BASIS_FUNCTIONS,
            Params.NUM_BSC_BOOTSTRAP_SAMPLES, Params.NUM_CATEGORIES, Params.NUM_CATEGORIES_TO_DISCRETIZE, Params.NUM_LAGS,
            Params.NUM_LATENTS, Params.NUM_MEASURES, Params.NUM_RANDOMIZED_SEARCH_MODELS, Params.NUM_RUNS,
            Params.NUM_STRUCTURAL_EDGES, Params.NUM_STRUCTURAL_NODES, Params.NUMBER_RESAMPLING,
            Params.ORIENT_TOWARD_DCONNECTIONS, Params.ORIENT_VISIBLE_FEEDBACK_LOOPS, Params.OUTPUT_RBD,
            Params.PENALTY_DISCOUNT, Params.PERCENT_DISCRETE, Params.PERCENT_RESAMPLE_SIZE, Params.POSSIBLE_MSEP_DONE,
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
//            Params.RESAMPLING_ENSEMBLE,
            Params.RESAMPLING_WITH_REPLACEMENT,
            Params.BOOTSTRAPPING_NUM_THREADS,
            Params.SAVE_BOOTSTRAP_GRAPHS,
            Params.SEED
    ));

    /**
     * <p>getAlgorithmParameters.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     * @return a {@link java.util.Set} object
     */
    public static Set<String> getAlgorithmParameters(Algorithm algorithm) {
        return new HashSet<>(algorithm.getParameters());
    }

    /**
     * <p>getTestParameters.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     * @return a {@link java.util.Set} object
     */
    public static Set<String> getTestParameters(Algorithm algorithm) {
        return (algorithm instanceof TakesIndependenceWrapper)
                ? new HashSet<>(((TakesIndependenceWrapper) algorithm).getIndependenceWrapper().getParameters())
                : Collections.emptySet();
    }

    /**
     * <p>getScoreParameters.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     * @return a {@link java.util.Set} object
     */
    public static Set<String> getScoreParameters(Algorithm algorithm) {
        return (algorithm instanceof UsesScoreWrapper)
                ? new HashSet<>(((UsesScoreWrapper) algorithm).getScoreWrapper().getParameters())
                : Collections.emptySet();
    }

    /**
     * <p>getBootstrappingParameters.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     * @return a {@link java.util.Set} object
     */
    public static Set<String> getBootstrappingParameters(Algorithm algorithm) {
        return (algorithm.getClass().isAnnotationPresent(Bootstrapping.class))
                ? Params.BOOTSTRAPPING_PARAMS
                : Collections.emptySet();
    }

    /**
     * <p>getParameters.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public static Set<String> getParameters() {
        return Params.ALL_PARAMS_IN_HTML_MANUAL;
    }

}
