/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Static final string constants for use in parameter maps.
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class Params {

    private Params() {
    }
    /**
     * Constant <code>ADAPTIVE_BASIS_SELECTION="adaptiveBasisSelection"</code>
     */
    public static final String ADAPTIVE_BASIS_SELECTION = "adaptiveBasisSelection";
    /**
     * Constant <code>ADD_ORIGINAL_DATASET="addOriginalDataset"</code>
     */
    public static final String ADD_ORIGINAL_DATASET = "addOriginalDataset";
    /**
     * Constant <code>ALLOW_BIDIRECTED="allowBidirected"</code>
     */
    public static final String ALLOW_BIDIRECTED = "allowBidirected";
    /**
     * Constant <code>ALLOW_INTERNAL_RANDOMNESS="allowInternalRandomness"</code>
     */
    public static final String ALLOW_INTERNAL_RANDOMNESS = "allowInternalRandomness";
    /**
     * Constant <code>ALPHA="alpha"</code>
     */
    public static final String ALPHA = "alpha";
    /**
     * Constant <code>ALPHA_DEFAULT_0_05="alphaDefault0.05"</code>
     */
    public static final String ALPHA_DEFAULT_0_05 = "alphaDefault0.05";
    /**
     * Constant <code>AM_BETA_ALPHA="amBetaAlpha"</code>
     */
    public static final String AM_BETA_ALPHA = "amBetaAlpha";
    /**
     * Constant <code>AM_BETA_BETA="amBetaBeta"</code>
     */
    public static final String AM_BETA_BETA = "amBetaBeta";
    /**
     * Constant <code>AM_COEF_HIGH="amCoefHigh"</code>
     */
    public static final String AM_COEF_HIGH = "amCoefHigh";
    /**
     * Constant <code>AM_COEF_LOW="amCoefLow"</code>
     */
    public static final String AM_COEF_LOW = "amCoefLow";
    /**
     * Constant <code>AM_COEF_SYMMETRIC="amCoefSymmetric"</code>
     */
    public static final String AM_COEF_SYMMETRIC = "amCoefSymmetric";
    /**
     * Constant <code>AM_DERIVATIVE_MAX="amDerivativeMax"</code>
     */
    public static final String AM_DERIVATIVE_MAX = "amDerivativeMax";
    /**
     * Constant <code>AM_DERIVATIVE_MIN="amDerivativeMin"</code>
     */
    public static final String AM_DERIVATIVE_MIN = "amDerivativeMin";
    /**
     * Constant <code>AM_DISTORTION_TYPE="amDistortionType"</code>
     */
    public static final String AM_DISTORTION_TYPE = "amDistortionType";
    /**
     * Constant <code>AM_ENSURE_INVERTIBILITY="amEnsureInvertibility"</code>
     */
    public static final String AM_ENSURE_INVERTIBILITY = "amEnsureInvertibility";
    /**
     * Constant <code>AM_FIRST_DERIVATIVE_MAX="amFirstDerivMax"</code>
     */
    public static final String AM_FIRST_DERIVATIVE_MAX = "amFirstDerivMax";
    /**
     * Constant <code>AM_FIRST_DERIVATIVE_MIN="amFirstDerivMin"</code>
     */
    public static final String AM_FIRST_DERIVATIVE_MIN = "amFirstDerivMin";
    /**
     * Constant <code>AM_RESCALE_MAX="amRescaleMax"</code>
     */
    public static final String AM_RESCALE_MAX = "amRescaleMax";
    /**
     * Constant <code>AM_RESCALE_MIN="amRescaleMin"</code>
     */
    public static final String AM_RESCALE_MIN = "amRescaleMin";
    /**
     * Constant <code>AM_TAYLOR_SERIES_DEGREE="amTaylorSeriesDegree"</code>
     */
    public static final String AM_TAYLOR_SERIES_DEGREE = "amTaylorSeriesDegree";
    /**
     * Constant <code>ANM_NOISE_KIND="anmNoiseKind"</code>
     */
    public static final String ANM_NOISE_KIND = "anmNoiseKind";
    /**
     * Constant <code>ANM_NOISE_STRENGTH="anmNoiseStrength"</code>
     */
    public static final String ANM_NOISE_STRENGTH = "anmNoiseStrength";
    /**
     * Constant <code>ANM_NONLINEARITY="anmNonlinearity"</code>
     */
    public static final String ANM_NONLINEARITY = "anmNonlinearity";
    /**
     * Constant <code>ANM_PRESET="anmPreset"</code>
     */
    public static final String ANM_PRESET = "anmPreset";
    /**
     * Constant <code>APPLY_R1="applyR1"</code>
     */
    public static final String APPLY_R1 = "applyR1";
    /**
     * Constant <code>AVG_DEGREE="avgDegree"</code>
     */
    public static final String AVG_DEGREE = "avgDegree";
    /**
     * Constant <code>BASIS_RANK_TRANSFORM="basisRankTransform"</code>
     */
    public static final String BASIS_RANK_TRANSFORM = "basisRankTransform";
    /**
     * Constant <code>BASIS_SCALE="basisScale"</code>
     */
    public static final String BASIS_SCALE = "basisScale";
    /**
     * Constant <code>BASIS_TYPE="basisType"</code>
     */
    public static final String BASIS_TYPE = "basisType";
    /**
     * Constant <code>BETA_HIGH="betaHigh"</code>
     */
    public static final String BETA_HIGH = "betaHigh";
    /**
     * Constant <code>BETA_LOW="betaLow"</code>
     */
    public static final String BETA_LOW = "betaLow";
    /**
     * Constant <code>BGE_ALPHA_MU="bgeAlphaMu"</code>
     */
    public static final String BGE_ALPHA_MU = "bgeAlphaMu";
    /**
     * Constant <code>BGE_ALPHA_W_OFFSET="bgeAlphaWOffset"</code>
     */
    public static final String BGE_ALPHA_W_OFFSET = "bgeAlphaWOffset";
    /**
     * Constant <code>BINS_PER_CONT_XY="binsPerContXY"</code>
     */
    public static final String BINS_PER_CONT_XY = "binsPerContXY";
    /**
     * Constant <code>BINS_PER_CONT_Z="binsPerContZ"</code>
     */
    public static final String BINS_PER_CONT_Z = "binsPerContZ";
    /**
     * Constant <code>BOOTSTRAPPING_NUM_THREADS="bootstrappingNumThreads"</code>
     */
    public static final String BOOTSTRAPPING_NUM_THREADS = "bootstrappingNumThreads";
    /**
     * Constant <code>BOSS_ALG="bossAlg"</code>
     */
    public static final String BOSS_ALG = "bossAlg";
    /**
     * Constant <code>BW_MAX_ROWS="bwMaxRows"</code>
     */
    public static final String BW_MAX_ROWS = "bwMaxRows";
    /**
     * Constant <code>CACHE_SCORES="cacheScores"</code>
     */
    public static final String CACHE_SCORES = "cacheScores";
    /**
     * Constant <code>CAT_RHO="catRho"</code>
     */
    public static final String CAT_RHO = "catRho";
    /**
     * Constant <code>CCI_SCORE_ALPHA="cciScoreAlpha"</code>
     */
    public static final String CCI_SCORE_ALPHA = "cciScoreAlpha";
    /**
     * Constant <code>CDNOD_COLLIDER_STYLE="cdnodColliderStyle"</code>
     */
    public static final String CDNOD_COLLIDER_STYLE = "cdnodColliderStyle";
    /**
     * Constant <code>CDNOD_CONTEXT_MARGIN="cdnodContextMargin"</code>
     */
    public static final String CDNOD_CONTEXT_MARGIN = "cdnodContextMargin";
    /**
     * Constant <code>CDNOD_EXCLUDE_CONTEXTS_FROM_S="cdnodExcludeContextsFromS"</code>
     */
    public static final String CDNOD_EXCLUDE_CONTEXTS_FROM_S = "cdnodExcludeContextsFromS";
    /**
     * Constant <code>CDNOD_GREEDY_CONTEXTS="cdnodGreedyContexts"</code>
     */
    public static final String CDNOD_GREEDY_CONTEXTS = "cdnodGreedyContexts";
    /**
     * Constant <code>CDNOD_MAX_CONTEXTS_PER_NODE="cdnodMaxContextsPerNode"</code>
     */
    public static final String CDNOD_MAX_CONTEXTS_PER_NODE = "cdnodMaxContextsPerNode";
    /**
     * Constant <code>CDNOD_MAX_P_MARGIN="cdnodMaxPMargin"</code>
     */
    public static final String CDNOD_MAX_P_MARGIN = "cdnodMaxPMargin";
    /**
     * Constant <code>CDNOD_SCORE_CONTEXTS="cdnodScoreContexts"</code>
     */
    public static final String CDNOD_SCORE_CONTEXTS = "cdnodScoreContexts";
    /**
     * Constant <code>CELL_TABLE_TYPE="cellTableType"</code>
     */
    public static final String CELL_TABLE_TYPE = "cellTableType";
    /**
     * Constant <code>CG_EXACT="cgExact"</code>
     */
    public static final String CG_EXACT = "cgExact";
    /**
     * Constant <code>CHECK_ADJACENCY_SEPSETS="checkAdjacencySepsets"</code>
     */
    public static final String CHECK_ADJACENCY_SEPSETS = "checkAdjacencySepsets";
    /**
     * Constant <code>CHECK_TYPE="checkType"</code>
     */
    public static final String CHECK_TYPE = "checkType";
    /**
     * Constant <code>CLUSTER_SIZES="clusterSizes"</code>
     */
    public static final String CLUSTER_SIZES = "clusterSizes";
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
     * Constant <code>COLLIDER_ORIENTATION_STYLE="colliderOrientationStyle"</code>
     */
    public static final String COLLIDER_ORIENTATION_STYLE = "colliderOrientationStyle";
    /**
     * Constant <code>COMPARE_GRAPH_ALGCOMP="compareGraphAlgcomp"</code>
     */
    public static final String COMPARE_GRAPH_ALGCOMP = "compareGraphAlgcomp";
    /**
     * Constant <code>COMPLETE_RULE_SET_USED="completeRuleSetUsed"</code>
     */
    public static final String COMPLETE_RULE_SET_USED = "completeRuleSetUsed";
    /**
     * Constant <code>CONCURRENT_FAS="concurrentFAS"</code>
     */
    public static final String CONCURRENT_FAS = "concurrentFAS";
    /**
     * Constant <code>CONDITIONING_THRESHOLD="conditioningThreshold"</code>
     */
    public static final String CONDITIONING_THRESHOLD = "conditioningThreshold";
    /**
     * Constant <code>CONFLICT_RULE="conflictRule"</code>
     */
    public static final String CONFLICT_RULE = "conflictRule";
    /**
     * Constant <code>CONNECTED="connected"</code>
     */
    public static final String CONNECTED = "connected";
    /**
     * Constant <code>CONVERGENCE_THRESHOLD="convergenceThreshold"</code>
     */
    public static final String CONVERGENCE_THRESHOLD = "convergenceThreshold";
    /**
     * Constant <code>CORD_ENGINE="cordEngine"</code>
     */
    public static final String CORD_ENGINE = "cordEngine";
    /**
     * Constant <code>CORD_LEARNING_RATE="cordLearningRate"</code>
     */
    public static final String CORD_LEARNING_RATE = "cordLearningRate";
    /**
     * Constant <code>CORD_MAX_LEAF_NODES="cordMaxLeafNodes"</code>
     */
    public static final String CORD_MAX_LEAF_NODES = "cordMaxLeafNodes";
    /**
     * Constant <code>CORD_NUM_ESTIMATORS="cordNumEstimators"</code>
     */
    public static final String CORD_NUM_ESTIMATORS = "cordNumEstimators";
    /**
     * Constant <code>CORD_NUM_THRESHOLDS="cordNumThresholds"</code>
     */
    public static final String CORD_NUM_THRESHOLDS = "cordNumThresholds";
    /**
     * Constant <code>CORD_SEED="cordSeed"</code>
     */
    public static final String CORD_SEED = "cordSeed";
    /**
     * Constant <code>CORD_SYMMETRIC="cordSymmetric"</code>
     */
    public static final String CORD_SYMMETRIC = "cordSymmetric";
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
     * Constant <code>CPDAG="cpdag"</code>
     */
    public static final String CPDAG = "cpdag";
    /**
     * Constant <code>CSTAR_CPDAG_ALGORITHM="cstarCpdagAlgorithm"</code>
     */
    public static final String CSTAR_CPDAG_ALGORITHM = "cstarCpdagAlgorithm";
    /**
     * Constant <code>CUSTOM_NOISE_EXPRESSION="customNoiseExpression"</code>
     */
    public static final String CUSTOM_NOISE_EXPRESSION = "customNoiseExpression";
    /**
     * Constant <code>CUSTOM_NOISE_OPTION="customNoiseOption"</code>
     */
    public static final String CUSTOM_NOISE_OPTION = "customNoiseOption";
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
     * Constant <code>CYCLIC_COEF_HIGH="cyclicCoefHigh"</code>
     */
    public static final String CYCLIC_COEF_HIGH = "cyclicCoefHigh";
    /**
     * Constant <code>CYCLIC_COEF_LOW="cyclicCoefLow"</code>
     */
    public static final String CYCLIC_COEF_LOW = "cyclicCoefLow";
    /**
     * Constant <code>CYCLIC_COEF_STYLE="cyclicCoefStyle"</code>
     */
    public static final String CYCLIC_COEF_STYLE = "cyclicCoefStyle";
    /**
     * Constant <code>CYCLIC_MAX_PROD="cyclicMaxProd"</code>
     */
    public static final String CYCLIC_MAX_PROD = "cyclicMaxProd";
    /**
     * Constant <code>CYCLIC_RADIUS="cyclicRadius"</code>
     */
    public static final String CYCLIC_RADIUS = "cyclicRadius";
    /**
     * Constant <code>DAO_RANDOMIZE_ORDER="daoRandomizeOrder"</code>
     */
    public static final String DAO_RANDOMIZE_ORDER = "daoRandomizeOrder";
    /**
     * Constant <code>DAO_SF_IN="daoSfIn"</code>
     */
    public static final String DAO_SF_IN = "daoSfIn";
    /**
     * Constant <code>DAO_SF_OUT="daoSfOut"</code>
     */
    public static final String DAO_SF_OUT = "daoSfOut";
    /**
     * Constant <code>DATA_TYPE="dataType"</code>
     */
    public static final String DATA_TYPE = "dataType";
    /**
     * Constant <code>DEDUP_BY_CPDAG="dedupByCpdag"</code>
     */
    public static final String DEDUP_BY_CPDAG = "dedupByCpdag";
    /**
     * Constant <code>DEPTH="depth"</code>
     */
    public static final String DEPTH = "depth";
    /**
     * Constant <code>DETERMINISM_THRESHOLD="determinismThreshold"</code>
     */
    public static final String DETERMINISM_THRESHOLD = "determinismThreshold";
    /**
     * Constant <code>DE_COUPLING="deCoupling"</code>
     */
    public static final String DE_COUPLING = "deCoupling";
    /**
     * Constant <code>DE_DERIVED_NOISE="deDerivedNoise"</code>
     */
    public static final String DE_DERIVED_NOISE = "deDerivedNoise";
    /**
     * Constant <code>DE_EMIT_CONFIG_COLUMN="deEmitConfigColumn"</code>
     */
    public static final String DE_EMIT_CONFIG_COLUMN = "deEmitConfigColumn";
    /**
     * Constant <code>DE_INTERACTION="deInteraction"</code>
     */
    public static final String DE_INTERACTION = "deInteraction";
    /**
     * Constant <code>DE_MAX_LEVELS="deMaxLevels"</code>
     */
    public static final String DE_MAX_LEVELS = "deMaxLevels";
    /**
     * Constant <code>DE_MIN_LEVELS="deMinLevels"</code>
     */
    public static final String DE_MIN_LEVELS = "deMinLevels";
    /**
     * Constant <code>DE_NUM_DERIVED="deNumDerived"</code>
     */
    public static final String DE_NUM_DERIVED = "deNumDerived";
    /**
     * Constant <code>DE_NUM_FACTORS="deNumFactors"</code>
     */
    public static final String DE_NUM_FACTORS = "deNumFactors";
    /**
     * Constant <code>DE_NUM_RESPONSES="deNumResponses"</code>
     */
    public static final String DE_NUM_RESPONSES = "deNumResponses";
    /**
     * Constant <code>DE_RESPONSE_NOISE="deResponseNoise"</code>
     */
    public static final String DE_RESPONSE_NOISE = "deResponseNoise";
    /**
     * Constant <code>DE_SELECTION="deSelection"</code>
     */
    public static final String DE_SELECTION = "deSelection";
    /**
     * Constant <code>DE_SORT_BY_CONFIGURATION="deSortByConfiguration"</code>
     */
    public static final String DE_SORT_BY_CONFIGURATION = "deSortByConfiguration";
    /**
     * Constant <code>DIFFERENT_GRAPHS="differentGraphs"</code>
     */
    public static final String DIFFERENT_GRAPHS = "differentGraphs";
    /**
     * Constant <code>DISCRETE_INTERACTION_ORDER="discreteInteractionOrder"</code>
     */
    public static final String DISCRETE_INTERACTION_ORDER = "discreteInteractionOrder";
    /**
     * Constant <code>DISCRETIZE="discretize"</code>
     */
    public static final String DISCRETIZE = "discretize";
    /**
     * Constant <code>DISTORT_POST_NONLINEAR="distortPostNonlinear"</code>
     */
    public static final String DISTORT_POST_NONLINEAR = "distortPostNonlinear";
    /**
     * /** Constant <code>AM_DISTORT_PRE_ERROR="amDistortPreError"</code>
     */
    public static final String DISTORT_PRE_NOISE = "distortPreNoise";
    /**
     * Constant <code>DO_COLLIDER_ORIENTATION="doColliderOrientation"</code>
     */
    public static final String DO_COLLIDER_ORIENTATION = "doColliderOrientation";
    /**
     * Constant <code>DO_HIGHER_RANK_EXPANSION="doHigherRankExpansion"</code>
     */
    public static final String DO_HIGHER_RANK_EXPANSION = "doHigherRankExpansion";
    /**
     * Constant <code>DO_LEGALITY_GATING="doLegalityGating"</code>
     */
    public static final String DO_LEGALITY_GATING = "doLegalityGating";
    /**
     * Constant <code>DO_ONE_EQUATION_ONLY="doOneEquationOnly"</code>
     */
    public static final String DO_ONE_EQUATION_ONLY = "doOneEquationOnly";
    /**
     * Constant <code>DO_POSSIBLE_DSEP="doPossibleDsep"</code>
     */
    public static final String DO_POSSIBLE_DSEP = "doPossibleDsep";
    /**
     * Constant <code>EBIC_GAMMA="ebicGamma"</code>
     */
    public static final String EBIC_GAMMA = "ebicGamma";
    /**
     * Constant <code>EFFECTIVE_SAMPLE_SIZE="effectiveSampleSize"</code>
     */
    public static final String EFFECTIVE_SAMPLE_SIZE = "effectiveSampleSize";
    /**
     * Constant <code>ERRORS_NORMAL="errorsNormal"</code>
     */
    public static final String ERRORS_NORMAL = "errorsNormal";
    /**
     * Constant <code>EXCLUDE_SELECTION_BIAS="excludeSelectionBias"</code>
     */
    public static final String EXCLUDE_SELECTION_BIAS = "excludeSelectionBias";
    /**
     * Constant <code>EXTRA_EDGE_REMOVAL_STEP="extraEdgeRemovalStep"</code>
     */
    public static final String EXTRA_EDGE_REMOVAL_STEP = "extraEdgeRemovalStep";
    /**
     * Constant <code>FAITHFULNESS_ASSUMED="faithfulnessAssumed"</code>
     */
    public static final String FAITHFULNESS_ASSUMED = "faithfulnessAssumed";
    /**
     * Constant <code>FASK_ADJACENCY_METHOD="faskAdjacencyMethod"</code>
     */
    public static final String FASK_ADJACENCY_METHOD = "faskAdjacencyMethod";
    /**
     * Constant <code>FASK_DELTA="faskDelta"</code>
     */
    public static final String FASK_DELTA = "faskDelta";
    /**
     * Constant <code>FASK_LEFT_RIGHT_RULE="faskLeftRightRule"</code>
     */
    public static final String FASK_LEFT_RIGHT_RULE = "faskLeftRightRule";
    /**
     * Constant <code>FASK_NONEMPIRICAL="faskNonempirical"</code>
     */
    public static final String FASK_NONEMPIRICAL = "faskNonempirical";
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
     * Constant <code>FAS_RULE="fasRule"</code>
     */
    public static final String FAS_RULE = "fasRule";
    /**
     * Constant <code>FA_THRESHOLD="fa_threshold"</code>
     */
    public static final String FA_THRESHOLD = "fa_threshold";
    /**
     * Constant <code>FCITSL_ALLOW_CLASS_ESCAPE="fcitSlAllowClassEscape"</code>
     */
    public static final String FCITSL_ALLOW_CLASS_ESCAPE = "fcitSlAllowClassEscape";
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
     * Constant <code>FCIT_STARTS_WITH="fcitStartsWith"</code>
     */
    public static final String FCIT_STARTS_WITH = "fcitStartsWith";
    /**
     * Constant <code>FDR_Q="fdrQ"</code>
     */
    public static final String FDR_Q = "fdrQ";
    /**
     * Constant <code>FFML_FF_FEATURES="ffmlFfFeatures"</code>
     */
    public static final String FFML_FF_FEATURES = "ffmlFfFeatures";
    /**
     * Constant <code>FFML_INTERACTION_LAMBDA="ffmlInteractionLambda"</code> Weight of the
     * discrete-continuous interaction (product-kernel) term in the FFML score; 0 = additive
     * (BF/DG-style) discrete handling.
     */
    public static final String FFML_INTERACTION_LAMBDA = "ffmlInteractionLambda";
    /**
     * Constant <code>FFML_RIDGE="ffmlRidge"</code>
     */
    public static final String FFML_RIDGE = "ffmlRidge";
    /**
     * Constant <code>FILE_OUT_PATH="fileOutPath"</code>
     */
    public static final String FILE_OUT_PATH = "fileOutPath";
    /**
     * Constant <code>FISHER_EPSILON="fisherEpsilon"</code>
     */
    public static final String FISHER_EPSILON = "fisherEpsilon";
    /**
     * Constant <code>FLOP_NUM_RESTARTS="flopNumRestarts"</code>
     */
    public static final String FLOP_NUM_RESTARTS = "flopNumRestarts";
    /**
     * Constant <code>FOFC_ALPHA="fofcAlpha"</code>
     */
    public static final String FOFC_ALPHA = "fofcAlpha";
    /**
     * Constant <code>F_DEGREE="fDegree"</code>
     */
    public static final String F_DEGREE = "fDegree";
    /**
     * Constant <code>GAMMA_HIGH="gammaHigh"</code>
     */
    public static final String GAMMA_HIGH = "gammaHigh";
    /**
     * Constant <code>GAMMA_LOW="gammaLow"</code>
     */
    public static final String GAMMA_LOW = "gammaLow";
    /**
     * Constant <code>GCM_CONTROL_FUNCTION="gcmControlFunction"</code>
     */
    public static final String GCM_CONTROL_FUNCTION = "gcmControlFunction";
    /**
     * Constant <code>GCM_MULTIPLIER_SAMPLES="gcmMultiplierSamples"</code>
     */
    public static final String GCM_MULTIPLIER_SAMPLES = "gcmMultiplierSamples";
    /**
     * Constant <code>GCM_REGRESSOR_TYPE="gcmRegressorType"</code>
     */
    public static final String GCM_REGRESSOR_TYPE = "gcmRegressorType";
    /**
     * Constant <code>GCM_RFF_FEATURES="gcmRffFeatures"</code>
     */
    public static final String GCM_RFF_FEATURES = "gcmRffFeatures";
    /**
     * Constant <code>GCM_RFF_SIGMA="gcmRffSigma"</code>
     */
    public static final String GCM_RFF_SIGMA = "gcmRffSigma";
    /**
     * Constant <code>GCM_RIDGE="gcmRidge"</code>
     */
    public static final String GCM_RIDGE = "gcmRidge";
    /**
     * Constant <code>GCM_Z_TRUNCATION_LIMIT="gcmZTruncationLimit"</code>
     */
    public static final String GCM_Z_TRUNCATION_LIMIT = "gcmZTruncationLimit";
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
     * Constant <code>GIN_BACKEND="ginBackend"</code>
     */
    public static final String GIN_BACKEND = "ginBackend";
    /**
     * Constant <code>GIN_PERMUTATIONS="ginPermutations"</code>
     */
    public static final String GIN_PERMUTATIONS = "ginPermutations";
    /**
     * Constant <code>GIN_RIDGE="ginRidge"</code>
     */
    public static final String GIN_RIDGE = "ginRidge";
    /**
     * Constant <code>GMAS_ALLOW_ADDITIONS="gmasAllowAdditions"</code>
     */
    public static final String GMAS_ALLOW_ADDITIONS = "gmasAllowAdditions";
    /**
     * Constant <code>GMAS_LOOKAHEAD_DEPTH="gmasLookaheadDepth"</code>
     */
    public static final String GMAS_LOOKAHEAD_DEPTH = "gmasLookaheadDepth";
    /**
     * Constant <code>GMAS_TRIANGLE_ESCAPE_ONLY="gmasTriangleEscapeOnly"</code>
     */
    public static final String GMAS_TRIANGLE_ESCAPE_ONLY = "gmasTriangleEscapeOnly";
    /**
     * Constant <code>GRASP_ALG="graspAlg"</code>
     */
    public static final String GRASP_ALG = "graspAlg";
    /**
     * Constant <code>GRASP_BREAK_AFTER_IMPROVEMENT="graspBreakAFterImprovement"</code>
     */
    public static final String GRASP_BREAK_AFTER_IMPROVEMENT = "graspBreakAFterImprovement";
    // GRASP parameters and flags.
    /**
     * Constant <code>GRASP_CHECK_COVERING="graspCheckCovering"</code>
     */
    public static final String GRASP_CHECK_COVERING = "graspCheckCovering";
    /**
     * Constant <code>GRASP_DEPTH="graspDepth"</code>
     */
    public static final String GRASP_DEPTH = "graspDepth";
    /**
     * Constant <code>GRASP_FORWARD_TUCK_ONLY="graspForwardTuckOnly"</code>
     */
    public static final String GRASP_FORWARD_TUCK_ONLY = "graspForwardTuckOnly";
    /**
     * Constant <code>GRASP_NONSINGULAR_DEPTH="graspNonSingularDepth"</code>
     */
    public static final String GRASP_NONSINGULAR_DEPTH = "graspNonSingularDepth";
    /**
     * Constant <code>GRASP_ORDERED_ALG="graspOrderedAlg"</code>
     */
    public static final String GRASP_ORDERED_ALG = "graspOrderedAlg";
    /**
     * Constant <code>GRASP_SINGULAR_DEPTH="graspSingularDepth"</code>
     */
    public static final String GRASP_SINGULAR_DEPTH = "graspSingularDepth";
    /**
     * Constant <code>GRASP_TOLERANCE_DEPTH="graspToleranceDepth"</code>
     */
    public static final String GRASP_TOLERANCE_DEPTH = "graspToleranceDepth";
    /**
     * Constant <code>GRASP_USE_RASKUTTI_UHLER="graspUseRaskuttiUhler"</code>
     */
    public static final String GRASP_USE_RASKUTTI_UHLER = "graspUseRaskuttiUhler";
    /**
     * Constant <code>GRASP_USE_SCORE="graspUseScore"</code>
     */
    public static final String GRASP_USE_SCORE = "graspUseScore";
    /**
     * Constant <code>GRASP_USE_VP_SCORING="graspUseVpScoring"</code>
     */
    public static final String GRASP_USE_VP_SCORING = "graspUseVpScoring";
    /**
     * Constant <code>GUARANTEE_ACYCLIC="guaranteeAcyclic"</code>
     */
    public static final String GUARANTEE_ACYCLIC = "guaranteeAcyclic";
    /**
     * Constant <code>GUARANTEE_CPDAG="guaranteeCpdag"</code>
     */
    public static final String GUARANTEE_CPDAG = "guaranteeCpdag";
    /**
     * Constant <code>GUARANTEE_IID="guaranteeIid"</code>
     */
    public static final String GUARANTEE_IID = "guaranteeIid";
    /**
     * Constant <code>GUARANTEE_PAG="guaranteePag"</code>
     */
    public static final String GUARANTEE_PAG = "guaranteePag";
    /**
     * Constant <code>HIDDEN_DIMENSION="hiddenDimension"</code>
     */
    public static final String HIDDEN_DIMENSION = "hiddenDimension";
    /**
     * Constant <code>HIDDEN_DIMENSIONS="hiddenDimensions"</code>
     */
    public static final String HIDDEN_DIMENSIONS = "hiddenDimensions";
    /**
     * Constant <code>IA="ia"</code>
     */
    public static final String IA = "ia";
    /**
     * Constant <code>IMAGES_META_ALG="imagesMetaAlg"</code>
     */
    public static final String IMAGES_META_ALG = "imagesMetaAlg";
    /**
     * Constant <code>INCLUDE_ALL_NODES="includeAllNodes"</code>
     */
    public static final String INCLUDE_ALL_NODES = "includeAllNodes";
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
     * Constant <code>INPUT_SCALE="inputScale"</code>
     */
    public static final String INPUT_SCALE = "inputScale";
    /**
     * Constant <code>INSTANCE_ROW="instanceRow"</code>
     */
    public static final String INSTANCE_ROW = "instanceRow";
    /**
     * Constant <code>INSTANCE_SPECIFIC_ALPHA="instanceSpecificAlpha"</code>
     */
    public static final String INSTANCE_SPECIFIC_ALPHA = "instanceSpecificAlpha";
    /**
     * Constant <code>INTERCEPT_HIGH="interceptHigh"</code>
     */
    public static final String INTERCEPT_HIGH = "interceptHigh";
    /**
     * Constant <code>INTERCEPT_LOW="interceptLow"</code>
     */
    public static final String INTERCEPT_LOW = "interceptLow";
    /**
     * Constant <code>INTERVAL_BETWEEN_RECORDINGS="intervalBetweenRecordings"</code>
     */
    public static final String INTERVAL_BETWEEN_RECORDINGS = "intervalBetweenRecordings";
    /**
     * Constant <code>INTERVAL_BETWEEN_SHOCKS="intervalBetweenShocks"</code>
     */
    public static final String INTERVAL_BETWEEN_SHOCKS = "intervalBetweenShocks";
    /**
     * Constant <code>ION_ADJACENCY_SEARCH="ionAdjacencySearch"</code>
     */
    public static final String ION_ADJACENCY_SEARCH = "ionAdjacencySearch";
    /**
     * Constant <code>ION_PATH_LENGTH_SEARCH="ionPathLengthSearch"</code>
     */
    public static final String ION_PATH_LENGTH_SEARCH = "ionPathLengthSearch";
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
     * Constant <code>KML_BANDWIDTH_MULTIPLIER="kmlBandwidthMultiplier"</code>
     */
    public static final String KML_BANDWIDTH_MULTIPLIER = "kmlBandwidthMultiplier";
    /**
     * Constant <code>KML_FEATURE_TYPE="kmlFeatureType"</code>
     */
    public static final String KML_FEATURE_TYPE = "kmlFeatureType";
    /**
     * Constant <code>KML_JITTER="kmlJitter"</code>
     */
    public static final String KML_JITTER = "kmlJitter";
    /**
     * Constant <code>KML_LAMBDA="kmlLambda"</code>
     */
    public static final String KML_LAMBDA = "kmlLambda";
    /**
     * Constant <code>KML_NUM_FEATURES="kmlNumFeatures"</code>
     */
    public static final String KML_NUM_FEATURES = "kmlNumFeatures";
    /**
     * Constant <code>LAMBDA1="lambda1"</code>
     */
    public static final String LAMBDA1 = "lambda1";
    /**
     * Constant <code>LATENT_GROUP_SPECS="latentGroupSpecs"</code>
     */
    public static final String LATENT_GROUP_SPECS = "latentGroupSpecs";
    /**
     * Constant <code>LATENT_MEASURED_IMPURE_PARENTS="latentMeasuredImpureParents"</code>
     */
    public static final String LATENT_MEASURED_IMPURE_PARENTS = "latentMeasuredImpureParents";
    /**
     * Constant <code>LINEAR_HIGH="linearHigh"</code>
     */
    public static final String LINEAR_HIGH = "linearHigh";
    /**
     * Constant <code>LINEAR_LOW="linearLow"</code>
     */
    public static final String LINEAR_LOW = "linearLow";
    /**
     * Constant <code>LOG_FINAL_ORIENTATIONS="logFinalOrientations"</code>
     */
    public static final String LOG_FINAL_ORIENTATIONS = "logFinalOrientations";
    /**
     * Constant <code>LOWER_BOUND="lowerBound"</code>
     */
    public static final String LOWER_BOUND = "lowerBound";
    /**
     * Constant <code>LV_HEURISTIC_ONLY="lvHeuristicOnly"</code>
     */
    public static final String LV_HEURISTIC_ONLY = "lvHeuristicOnly";
    /**
     * Constant <code>MAXIT="maxit"</code>
     */
    public static final String MAXIT = "maxit";
    /**
     * Constant <code>MAX_BLOCKING_PATH_LENGTH="maxBlockingPathLength"</code>
     */
    public static final String MAX_BLOCKING_PATH_LENGTH = "maxBlockingPathLength";
    /**
     * Constant <code>MAX_CATEGORIES="maxCategories"</code>
     */
    public static final String MAX_CATEGORIES = "maxCategories";
    /**
     * Constant <code>MAX_CELLS_PER_STRATUM="maxCellsPerStratum"</code>
     */
    public static final String MAX_CELLS_PER_STRATUM = "maxCellsPerStratum";
    /**
     * Constant <code>MAX_DEGREE="maxDegree"</code>
     */
    public static final String MAX_DEGREE = "maxDegree";
    /**
     * Constant <code>MAX_DISCRIMINATING_PATH_LENGTH="maxDiscriminatingPathLength"</code>
     */
    public static final String MAX_DISCRIMINATING_PATH_LENGTH = "maxDiscriminatingPathLength";
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
     * Constant <code>MAX_LATENT_SUBSET_SIZE="maxLatentSubsetSize"</code>
     */
    public static final String MAX_LATENT_SUBSET_SIZE = "maxLatentSubsetSize";
    /**
     * Constant <code>MAX_OBSERVED_LEVELS_PER_VAR="maxObservedLevelsPerVar"</code>
     */
    public static final String MAX_OBSERVED_LEVELS_PER_VAR = "maxObservedLevelsPerVar";
    /**
     * Constant <code>MAX_OUTDEGREE="maxOutdegree"</code>
     */
    public static final String MAX_OUTDEGREE = "maxOutdegree";
    /**
     * Constant <code>MAX_POSSIBLE_SEP_PATH_LENGTH="maxPossibleSepPathLength"</code>
     */
    public static final String MAX_POSSIBLE_SEP_PATH_LENGTH = "maxPossibleSepPathLength";
    /**
     * Constant <code>MAX_RANK="maxRank"</code>
     */
    public static final String MAX_RANK = "maxRank";
    /**
     * Constant <code>MAX_RUNS="maxRuns"</code>
     */
    public static final String MAX_RUNS = "maxRuns";
    /**
     * Constant <code>MAX_SCORE_DROP="maxScoreDrop"</code>
     */
    public static final String MAX_SCORE_DROP = "maxScoreDrop";
    /**
     * Constant <code>MB="mb"</code>
     */
    public static final String MB = "mb";
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
     * Constant <code>MERGE_THRESHOLD="dmMergeThreshold"</code>
     */
    public static final String MERGE_THRESHOLD = "dmMergeThreshold";
    /**
     * Constant <code>META_EDGE_CONNECTION_TYPE="mimMetaEdgeConnectionType"</code>
     */
    public static final String META_EDGE_CONNECTION_TYPE = "mimMetaEdgeConnectionType";
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
     * Constant <code>MIMBUILD_TYPE="mimbuildType"</code>
     */
    public static final String MIMBUILD_TYPE = "mimbuildType";
    /**
     * Constant <code>MIMIC_GROUP_SPECS="mimicGroupSpecs"</code>
     */
    public static final String MIMIC_GROUP_SPECS = "mimicGroupSpecs";
    /**
     * Constant <code>MIMIC_NUM_SHARED_PARENTS="mimicNumSharedParents"</code>
     */
    public static final String MIMIC_NUM_SHARED_PARENTS = "mimicNumSharedParents";
    /**
     * Constant <code>MIM_LATENT_GROUP_SPECS="mimLatentGroupSpecs"</code>
     */
    public static final String MIM_LATENT_GROUP_SPECS = "mimLatentGroupSpecs";
    /**
     * Constant <code>MIM_LATENT_MEASURED_IMPURE_PARENTS="mimLatentMeasuredImpureParents"</code>
     */
    public static final String MIM_LATENT_MEASURED_IMPURE_PARENTS = "mimLatentMeasuredImpureParents";
    /**
     * Constant <code>MIM_MEASURED_MEASURED_IMPURE_ASSOCIATIONS="mimMeasuredMeasuredImpureAssociations"</code>
     */
    public static final String MIM_MEASURED_MEASURED_IMPURE_ASSOCIATIONS = "mimMeasuredMeasuredImpureAssociations";
    /**
     * Constant <code>MIM_MEASURED_MEASURED_IMPURE_PARENTS="mimMeasuredMeasuredImpureParents"</code>
     */
    public static final String MIM_MEASURED_MEASURED_IMPURE_PARENTS = "mimMeasuredMeasuredImpureParents";
    /**
     * Constant <code>MIM_NUM_CHILDREN_PER_GROUP="mimNumChildrenPerGroup"</code>
     */
    public static final String MIM_NUM_CHILDREN_PER_GROUP = "mimNumChildrenPerGroup";
    /**
     * Constant <code>MINIMAX_FF_FEATURES="minimaxFfFeatures"</code>
     */
    public static final String MINIMAX_FF_FEATURES = "minimaxFfFeatures";
    /**
     * Constant <code>MINIMAX_FF_SIGMA="minimaxFfSigma"</code>
     */
    public static final String MINIMAX_FF_SIGMA = "minimaxFfSigma";
    /**
     * Constant <code>MINIMAX_IRLS_ITERS="minimaxIrlsIters"</code>
     */
    public static final String MINIMAX_IRLS_ITERS = "minimaxIrlsIters";
    /**
     * Constant <code>MINIMAX_NU="minimaxMaxNu"</code>
     */
    public static final String MINIMAX_NU = "minimaxMaxNu";
    /**
     * Constant <code>MINIMAX_PERMUTATIONS="minimaxPermutations"</code>
     */
    public static final String MINIMAX_PERMUTATIONS = "minimaxPermutations";
    /**
     * Constant <code>MINIMAX_RIDGE="minimaxRidge"</code>
     */
    public static final String MINIMAX_RIDGE = "minimaxRidge";
    /**
     * Constant <code>MINIMAX_SCALE="minimaxScale"</code>
     */
    public static final String MINIMAX_SCALE = "minimaxScale";
    /**
     * Constant <code>MIN_CATEGORIES="minCategories"</code>
     */
    public static final String MIN_CATEGORIES = "minCategories";
    /**
     * Constant <code>MIN_COUNT_PER_CELL="minCountPerCell"</code>
     */
    public static final String MIN_COUNT_PER_CELL = "minCountPerCell";
    /**
     * Constant <code>MIN_PARAM_SAMPLE_SIZE="minParamSampleSize"</code>
     */
    public static final String MIN_PARAM_SAMPLE_SIZE = "minParamSampleSize";
    /**
     * Constant <code>MIN_SAMPLE_SIZE_PER_CELL="minSampleSizePerCell"</code>
     */
    public static final String MIN_SAMPLE_SIZE_PER_CELL = "minSampleSizePerCell";
    /**
     * Constant <code>MIN_STRATUM_SIZE="minStratumSize"</code>
     */
    public static final String MIN_STRATUM_SIZE = "minStratumSize";
    /**
     * Constant <code>MISSING_DATA_POLICY="missingDataPolicy"</code>. One of "default" (legacy behavior per
     * component), "fail", "listwise", "testwise", "em" (EM-estimated covariance; continuous data), or "mi"
     * (multiple imputation; handled by search wrappers). See edu.cmu.tetrad.data.missing.MissingDataPolicy.
     */
    public static final String MISSING_DATA_POLICY = "missingDataPolicy";
    /**
     * Constant <code>MISSING_EM_MAX_ITERATIONS="missingEmMaxIterations"</code>. Maximum EM iterations.
     */
    public static final String MISSING_EM_MAX_ITERATIONS = "missingEmMaxIterations";
    /**
     * Constant <code>MISSING_EM_RIDGE="missingEmRidge"</code>. Ridge for the EM covariance estimator.
     */
    public static final String MISSING_EM_RIDGE = "missingEmRidge";
    /**
     * Constant <code>MISSING_EM_TOLERANCE="missingEmTolerance"</code>. Convergence tolerance for the EM covariance
     * estimator.
     */
    public static final String MISSING_EM_TOLERANCE = "missingEmTolerance";
    /**
     * Constant <code>MISSING_ESS_MODE="missingEssMode"</code>. Effective-sample-size mode for analyses run on a
     * covariance matrix estimated from incomplete data: "fullN", "minPairwise", or "meanPairwise".
     */
    public static final String MISSING_ESS_MODE = "missingEssMode";
    /**
     * Constant <code>MISSING_NUM_IMPUTATIONS="missingNumImputations"</code>. Number of imputed datasets for
     * multiple imputation.
     */
    public static final String MISSING_NUM_IMPUTATIONS = "missingNumImputations";
    /**
     * Constant <code>MNAR_NUM_EXTRA_INFLUENCES="mnarNumExtraInfluences"</code>
     */
    public static final String MNAR_NUM_EXTRA_INFLUENCES = "mnarNumExtraInfluences";
    /**
     * Constant <code>MNAR_NUM_VARIABLES_WITH_MISSING="mnarNumVariablesWithMissing"</code>
     */
    public static final String MNAR_NUM_VARIABLES_WITH_MISSING = "mnarNumVariablesWithMissing";
    /**
     * Constant <code>MNAR_THRESHOLD="mnarThreshold"</code>
     */
    public static final String MNAR_THRESHOLD = "mnarThreshold";
    /**
     * Constant <code>MaX_PAX_P_ORIENTATION_HEURISTIC_MAX_LENGTH="maxPaxPOrientationHeuristicMaxLength"</code>
     */
    public static final String MaX_PAX_P_ORIENTATION_HEURISTIC_MAX_LENGTH = "maxPaxPOrientationHeuristicMaxLength";
    /**
     * Constant <code>NOISE_EXPRESSION="noiseExpression"</code>
     */
    public static final String NOISE_EXPRESSION = "noiseExpression";
    /**
     * Constant <code>NO_RANDOMLY_DETERMINED_INDEPENDENCE="noRandomlyDeterminedIndependence"</code>
     */
    public static final String NO_RANDOMLY_DETERMINED_INDEPENDENCE = "noRandomlyDeterminedIndependence";
    /**
     * Constant <code>NUMBER_OF_EXPANSIONS="numberOfExpansions"</code>
     */
    public static final String NUMBER_OF_EXPANSIONS = "numberOfExpansions";
    /**
     * Constant <code>NUMBER_RESAMPLING="numberResampling"</code>
     */
    public static final String NUMBER_RESAMPLING = "numberResampling";
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
     * Constant <code>NUM_FACTORS="numFactors"</code>
     */
    public static final String NUM_FACTORS = "numFactors";
    /**
     * Constant <code>NUM_FF_FEATURES="numFfFeatures"</code>
     */
    public static final String NUM_FF_FEATURES = "numFfFeatures";
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
     * Constant <code>NUM_RANDOMIZED_SEARCH_MODELS="numRandomizedSearchModels"</code>
     */
    public static final String NUM_RANDOMIZED_SEARCH_MODELS = "numRandomizedSearchModels";
    /**
     * Constant <code>NUM_ROUNDS="numRounds"</code>
     */
    public static final String NUM_ROUNDS = "numRounds";
    /**
     * Constant <code>NUM_RUNS="numRuns"</code>
     */
    public static final String NUM_RUNS = "numRuns";
    /**
     * Constant <code>NUM_STARTS="numStarts"</code>
     */
    public static final String NUM_STARTS = "numStarts";
    /**
     * Constant <code>NUM_STRUCTURAL_EDGES="mimNumStructuralEdges"</code>
     */
    public static final String NUM_STRUCTURAL_EDGES = "mimNumStructuralEdges";
    /**
     * Constant <code>NUM_STRUCTURAL_NODES="mimNumStructuralNodes"</code>
     */
    public static final String NUM_STRUCTURAL_NODES = "mimNumStructuralNodes";
    /**
     * Constant <code>NUM_SUBSAMPLES="numSubsamples"</code>
     */
    public static final String NUM_SUBSAMPLES = "numSubsamples";
    /**
     * Constant <code>NUM_THREADS="numThreads"</code>
     */
    public static final String NUM_THREADS = "numThreads";
    /**
     * Constant <code>OPTIMAL_ACROSS_BRANCHES="optimalAcrossBranches"</code>
     */
    public static final String OPTIMAL_ACROSS_BRANCHES = "optimalAcrossBranches";
    /**
     * Constant <code>ORIENTATION_ALPHA="orientationAlpha"</code>
     */
    public static final String ORIENTATION_ALPHA = "orientationAlpha";
    /**
     * Constant <code>ORIENT_AND_PRUNE="orientAndPrune"</code>
     */
    public static final String ORIENT_AND_PRUNE = "orientAndPrune";
    /**
     * Constant <code>ORIENT_TOWARD_DCONNECTIONS="orientTowardDConnections"</code>
     */
    public static final String ORIENT_TOWARD_DCONNECTIONS = "orientTowardDConnections";
    /**
     * Constant <code>ORIENT_VISIBLE_FEEDBACK_LOOPS="orientVisibleFeedbackLoops"</code>
     */
    public static final String ORIENT_VISIBLE_FEEDBACK_LOOPS = "orientVisibleFeedbackLoops";
    /**
     * Constant <code>OS_DEGRADE_CENSOR_PROP="osDegradeCensorProp"</code>
     */
    public static final String OS_DEGRADE_CENSOR_PROP = "osDegradeCensorProp";
    /**
     * Constant <code>OS_DEGRADE_CENSOR_QUANTILE="osDegradeCensorQuantile"</code>
     */
    public static final String OS_DEGRADE_CENSOR_QUANTILE = "osDegradeCensorQuantile";
    /**
     * Constant <code>OS_DEGRADE_MISSING_MECHANISM="osDegradeMissingMechanism"</code>
     */
    public static final String OS_DEGRADE_MISSING_MECHANISM = "osDegradeMissingMechanism";
    /**
     * Constant <code>OS_DEGRADE_MISSING_PROP="osDegradeMissingProp"</code>
     */
    public static final String OS_DEGRADE_MISSING_PROP = "osDegradeMissingProp";
    /**
     * Constant <code>OS_DEGRADE_ORDINALIZE_PROP="osDegradeOrdinalizeProp"</code>
     */
    public static final String OS_DEGRADE_ORDINALIZE_PROP = "osDegradeOrdinalizeProp";
    /**
     * Constant <code>OS_FORM_INDEX_NOISE="osFormIndexNoise"</code>
     */
    public static final String OS_FORM_INDEX_NOISE = "osFormIndexNoise";
    /**
     * Constant <code>OS_FORM_INTERACTION="osFormInteraction"</code>
     */
    public static final String OS_FORM_INTERACTION = "osFormInteraction";
    /**
     * Constant <code>OS_FORM_NONLINEARITY="osFormNonlinearity"</code>
     */
    public static final String OS_FORM_NONLINEARITY = "osFormNonlinearity";
    /**
     * Constant <code>OS_GRAPH_AVG_SYSTEM_DEGREE="osGraphAvgSystemDegree"</code>
     */
    public static final String OS_GRAPH_AVG_SYSTEM_DEGREE = "osGraphAvgSystemDegree";
    /**
     * Constant <code>OS_GRAPH_EDGE_DENSITY="osGraphEdgeDensity"</code>
     */
    public static final String OS_GRAPH_EDGE_DENSITY = "osGraphEdgeDensity";
    /**
     * Constant <code>OS_GRAPH_NUM_CONTEXT="osGraphNumContext"</code>
     */
    public static final String OS_GRAPH_NUM_CONTEXT = "osGraphNumContext";
    /**
     * Constant <code>OS_GRAPH_NUM_HIDDEN_CONTEXT="osGraphNumHiddenContext"</code>
     */
    public static final String OS_GRAPH_NUM_HIDDEN_CONTEXT = "osGraphNumHiddenContext";
    /**
     * Constant <code>OS_GRAPH_NUM_INDICES="osGraphNumIndices"</code>
     */
    public static final String OS_GRAPH_NUM_INDICES = "osGraphNumIndices";
    /**
     * Constant <code>OS_GRAPH_NUM_OUTCOMES="osGraphNumOutcomes"</code>
     */
    public static final String OS_GRAPH_NUM_OUTCOMES = "osGraphNumOutcomes";
    /**
     * Constant <code>OS_GRAPH_NUM_SYSTEM="osGraphNumSystem"</code>
     */
    public static final String OS_GRAPH_NUM_SYSTEM = "osGraphNumSystem";
    /**
     * Constant <code>OS_PANEL_EMIT_SUBJECTS_AS_DATA_SETS="osPanelEmitSubjectsAsDataSets"</code>
     */
    public static final String OS_PANEL_EMIT_SUBJECTS_AS_DATA_SETS = "osPanelEmitSubjectsAsDataSets";
    /**
     * Constant <code>OS_PANEL_EMIT_SUBJECT_COLUMN="osPanelEmitSubjectColumn"</code>
     */
    public static final String OS_PANEL_EMIT_SUBJECT_COLUMN = "osPanelEmitSubjectColumn";
    /**
     * Constant <code>OS_PANEL_NUM_SUBJECTS="osPanelNumSubjects"</code>
     */
    public static final String OS_PANEL_NUM_SUBJECTS = "osPanelNumSubjects";
    /**
     * Constant <code>OS_PANEL_SUBJECT_SHIFT_SD="osPanelSubjectShiftSd"</code>
     */
    public static final String OS_PANEL_SUBJECT_SHIFT_SD = "osPanelSubjectShiftSd";
    /**
     * Constant <code>OS_SERIAL_AR_COEF="osSerialArCoef"</code>
     */
    public static final String OS_SERIAL_AR_COEF = "osSerialArCoef";
    /**
     * Constant <code>OS_SERIAL_INDEX_MEMORY_HIGH="osSerialIndexMemoryHigh"</code>
     */
    public static final String OS_SERIAL_INDEX_MEMORY_HIGH = "osSerialIndexMemoryHigh";
    /**
     * Constant <code>OS_SERIAL_INDEX_MEMORY_LOW="osSerialIndexMemoryLow"</code>
     */
    public static final String OS_SERIAL_INDEX_MEMORY_LOW = "osSerialIndexMemoryLow";
    /**
     * Constant <code>OS_SERIAL_MAX_LAG="osSerialMaxLag"</code>
     */
    public static final String OS_SERIAL_MAX_LAG = "osSerialMaxLag";
    /**
     * Constant <code>OS_SERIAL_PROP_CROSS_LAG="osSerialPropCrossLag"</code>
     */
    public static final String OS_SERIAL_PROP_CROSS_LAG = "osSerialPropCrossLag";
    /**
     * Constant <code>OS_TYPE_DISCRETE_OUTCOME="osTypeDiscreteOutcome"</code>
     */
    public static final String OS_TYPE_DISCRETE_OUTCOME = "osTypeDiscreteOutcome";
    /**
     * Constant <code>OS_TYPE_NUM_CATEGORIES="osTypeNumCategories"</code>
     */
    public static final String OS_TYPE_NUM_CATEGORIES = "osTypeNumCategories";
    /**
     * Constant <code>OS_TYPE_PROP_CONTEXT_DISCRETE="osTypePropContextDiscrete"</code>
     */
    public static final String OS_TYPE_PROP_CONTEXT_DISCRETE = "osTypePropContextDiscrete";
    /**
     * Constant <code>OS_TYPE_PROP_SYSTEM_DISCRETE="osTypePropSystemDiscrete"</code>
     */
    public static final String OS_TYPE_PROP_SYSTEM_DISCRETE = "osTypePropSystemDiscrete";
    /**
     * Constant <code>OUTPUT_CPDAG="outputCpdag"</code>
     */
    public static final String OUTPUT_CPDAG = "outputCpdag";
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
     * Constant <code>PENALTY_DISCOUNT_DEFAULT_1="penaltyDiscountDefault1"</code>
     */
    public static final String PENALTY_DISCOUNT_DEFAULT_1 = "penaltyDiscountDefault1";
    /**
     * Constant <code>PENALTY_DISCOUNT_ZS="penaltyDiscountZs"</code>
     */
    public static final String PENALTY_DISCOUNT_ZS = "penaltyDiscountZs";
    /**
     * Constant <code>PERCENT_DISCRETE="percentDiscrete"</code>
     */
    public static final String PERCENT_DISCRETE = "percentDiscrete";
    /**
     * Constant <code>PERCENT_RESAMPLE_SIZE="percentResampleSize"</code>
     */
    public static final String PERCENT_RESAMPLE_SIZE = "percentResampleSize";
    /**
     * Constant <code>PERCENT_STABILITY="percentStability"</code>
     */
    public static final String PERCENT_STABILITY = "percentStability";
    /**
     * Constant <code>PERCENT_SUBSAMPLE_SIZE="percentSubsampleSize"</code>
     */
    public static final String PERCENT_SUBSAMPLE_SIZE = "percentSubsampleSize";
    /**
     * Constant <code>POISSON_LAMBDA="poissonLambda"</code>
     */
    public static final String POISSON_LAMBDA = "poissonLambda";
    /**
     * Constant <code>POLYNOMIAL_CONSTANT="polynomialConstant"</code>
     */
    public static final String POLYNOMIAL_CONSTANT = "polynomialConstant";
    /**
     * Constant <code>POLYNOMIAL_DEGREE="polynomialDegree"</code>
     */
    public static final String POLYNOMIAL_DEGREE = "polynomialDegree";
    /**
     * Constant <code>POOLED_TEST_METHOD="pooledTestMethod"</code> How per-data-set p-values are combined when data
     * sets are pooled for a test-based search: "fisher" (default; most powerful when the dependence is shared by
     * all data sets) or "tippett" (min-p, Sidak-adjusted; more powerful when the dependence is present in only some
     * of the data sets).
     */
    public static final String POOLED_TEST_METHOD = "pooledTestMethod";
    /**
     * Constant <code>POOL_DATA_SETS="poolDataSets"</code> Whether, given several data sets, to pool them into ONE
     * search (IMaGES-style: the score is summed across data sets, the test combines p-values by Fisher's method)
     * instead of running the algorithm once per data set. Honored by the GUI runner; in the API, passing a
     * DataModelList to an AbstractBootstrapAlgorithm's search method requests pooling directly.
     */
    public static final String POOL_DATA_SETS = "poolDataSets";
    /**
     * Constant <code>PRECOMPUTE_COVARIANCES="precomputeCovariances"</code>
     */
    public static final String PRECOMPUTE_COVARIANCES = "precomputeCovariances";
    /**
     * Constant <code>PRESERVE_MARKOV="preserveMarkov"</code>
     */
    public static final String PRESERVE_MARKOV = "preserveMarkov";
    /**
     * Constant <code>PRIOR_EQUIVALENT_SAMPLE_SIZE="priorEquivalentSampleSize"</code>
     */
    public static final String PRIOR_EQUIVALENT_SAMPLE_SIZE = "priorEquivalentSampleSize";
    /**
     * Constant <code>PROBABILITY_OF_EDGE="probabilityOfEdge"</code>
     */
    public static final String PROBABILITY_OF_EDGE = "probabilityOfEdge";
    /**
     * Constant <code>PROB_CYCLE="probCycle"</code>
     */
    public static final String PROB_CYCLE = "probCycle";
    /**
     * Constant <code>PROB_REMOVE_COLUMN="probRemoveColumn"</code>
     */
    public static final String PROB_REMOVE_COLUMN = "probRemoveColumn";
    /**
     * Constant <code>PROB_TWO_CYCLE="probTwoCycle"</code>
     */
    public static final String PROB_TWO_CYCLE = "probTwoCycle";
    /**
     * Constant <code>PYTHON_CI_SERVER="pythonCiServer"</code>
     */
    public static final String PYTHON_CI_SERVER = "pythonCiServer";
    /**
     * Constant <code>PYTHON_EXE="pythonExe"</code>
     */
    public static final String PYTHON_EXE = "pythonExe";
    /**
     * Constant <code>RANDOMIZE_COLUMNS="randomizeColumns"</code>
     */
    public static final String RANDOMIZE_COLUMNS = "randomizeColumns";
    /**
     * Constant <code>RANDOM_SELECTION_SIZE="randomSelectionSize"</code>
     */
    public static final String RANDOM_SELECTION_SIZE = "randomSelectionSize";
    /**
     * Constant <code>RA_RADIUS="raRadius"</code>
     */
    public static final String RA_RADIUS = "raRadius";
    /**
     * Constant <code>RB_RADIUS="rbRadius"</code>
     */
    public static final String RB_RADIUS = "rbRadius";
    /**
     * Constant <code>RCIT_APPROX="rcit.approx"</code> Approximation method for the null distribution: one of {"lpd4",
     * "hbe", "gamma", "chi2", "perm"}.
     */
    public static final String RCIT_APPROX = "rcit.approx";
    /**
     * Constant <code>RCIT_CENTER_FEATURES="rcit.centerFeatures"</code> Whether to center feature matrices before
     * regression and HSIC.
     */
    public static final String RCIT_CENTER_FEATURES = "rcit.centerFeatures";
    /**
     * Constant <code>RCIT_LAMBDA="rcit.lambda"</code> Ridge regularization parameter (λ) used during residualization.
     */
    public static final String RCIT_LAMBDA = "rcit.lambda";
    /**
     * Constant <code>RCIT_MODE="rcit.rcitMode"</code> Whether to use RCIT (true) or RCoT (false).
     */
    public static final String RCIT_MODE = "rcit.rcitMode";
    /**
     * Constant <code>RCIT_NUM_F="rcitNumF"</code>
     */
    public static final String RCIT_NUM_F = "rcitNumF";
    /**
     * Constant <code>RCIT_NUM_F2="rcitNumF2"</code>
     */
    public static final String RCIT_NUM_F2 = "rcitNumF2";
    /**
     * Constant <code>RCIT_NUM_FEATURES="rcitNumFeatures"</code>
     */
    public static final String RCIT_NUM_FEATURES = "rcitNumFeatures";
    /**
     * Constant <code>RCIT_NUM_FEATURES_XY="rcit.numFeaturesXY"</code> Number of random Fourier features for the test
     * variables X and Y (num_f2).
     */
    public static final String RCIT_NUM_FEATURES_XY = "rcit.numFeaturesXY";
    /**
     * Constant <code>RCIT_NUM_FEATURES_Z="rcit.numFeaturesZ"</code> Number of random Fourier features for the
     * conditioning set Z (num_f).
     */
    public static final String RCIT_NUM_FEATURES_Z = "rcit.numFeaturesZ";
    /**
     * Constant <code>RCIT_PERMUTATIONS="rcit.permutations"</code> Number of permutations used when RCIT_APPROX is
     * "perm".
     */
    public static final String RCIT_PERMUTATIONS = "rcit.permutations";
    /**
     * Constant <code>RECOVERY_ODDS_THRESHOLD="recoveryOddsThreshold"</code>
     */
    public static final String RECOVERY_ODDS_THRESHOLD = "recoveryOddsThreshold";
    /**
     * Constant <code>RECURSIVE_DEPTH="recursiveDepth"</code>
     */
    public static final String RECURSIVE_DEPTH = "recursiveDepth";
    /**
     * Constant <code>REGULARIZATION_LAMBDA="regularizationLambda"</code>
     */
    public static final String REGULARIZATION_LAMBDA = "regularizationLambda";
    /**
     * Constant <code>REMOVE_ALMOST_CYCLES="removeAlmostCycles"</code>
     */
    public static final String REMOVE_ALMOST_CYCLES = "removeAlmostCycles";
    /**
     * Constant <code>REMOVE_EFFECT_NODES="removeEffectNodes"</code>
     */
    public static final String REMOVE_EFFECT_NODES = "removeEffectNodes";
    /**
     * Constant <code>RESAMPLING_ENSEMBLE="resamplingEnsemble"</code>
     */
    public static final String RESAMPLING_ENSEMBLE = "resamplingEnsemble";
    /**
     * Constant <code>RESAMPLING_WITH_REPLACEMENT="resamplingWithReplacement"</code>
     */
    public static final String RESAMPLING_WITH_REPLACEMENT = "resamplingWithReplacement";
    /**
     * Constant <code>RESCUE_ACTION="rescueAction"</code>
     */
    public static final String RESCUE_ACTION = "rescueAction";
    /**
     * Constant <code>SAMPLE_SIZE="sampleSize"</code>
     */
    public static final String SAMPLE_SIZE = "sampleSize";
    /**
     * Constant <code>SAMPLE_STYLE="sampleStyle"</code>
     */
    public static final String SAMPLE_STYLE = "sampleStyle";
    /**
     * Constant <code>SAVE_BOOTSTRAP_GRAPHS="saveBootstrapGraphs"</code>
     */
    public static final String SAVE_BOOTSTRAP_GRAPHS = "saveBootstrapGraphs";
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
     * Constant <code>SCALING_FACTOR="scalingFactor"</code>
     */
    public static final String SCALING_FACTOR = "scalingFactor";
    /**
     * Constant <code>SCORE_CHECK_PENALTY_DISCOUNT="scoreCheckPenaltyDiscount"</code>
     */
    public static final String SCORE_CHECK_PENALTY_DISCOUNT = "scoreCheckPenaltyDiscount";
    /**
     * Constant <code>SCORE_TEST_CALIBRATED_P_VALUES="scoreTestCalibratedPValues"</code>
     */
    public static final String SCORE_TEST_CALIBRATED_P_VALUES = "scoreTestCalibratedPValues";
    /**
     * Constant <code>SEED="seed"</code>
     */
    public static final String SEED = "seed";
//    /**
//     * Constant <code>SIMULATION_ERROR_TYPE="simulationErrorType"</code>
//     */
//    public static final String SIMULATION_ERROR_TYPE = "simulationErrorType";
//    /**
//     * Constant <code>SIMULATION_PARAM1="simulationParam1"</code>
//     */
//    public static final String SIMULATION_PARAM1 = "simulationParam1";
//    /**
//     * Constant <code>SIMULATION_PARAM2="simulationParam2"</code>
//     */
//    public static final String SIMULATION_PARAM2 = "simulationParam2";
    /**
     * Constant <code>SELECTION_MIN_EFFECT="selectionMinEffect"</code>
     */
    public static final String SELECTION_MIN_EFFECT = "selectionMinEffect";
    /**
     * Constant <code>SELF_LOOP_COEF="selfLoopCoef"</code>
     */
    public static final String SELF_LOOP_COEF = "selfLoopCoef";
    /**
     * Constant <code>SEM_BIC_RULE="semBicRule"</code>
     */
    public static final String SEM_BIC_RULE = "semBicRule";
    /**
     * Constant <code>SEM_BIC_STRUCTURE_PRIOR="semBicStructurePrior"</code>
     */
    public static final String SEM_BIC_STRUCTURE_PRIOR = "semBicStructurePrior";
    /**
     * Constant <code>SEM_GIC_RULE="semGicRule"</code>
     */
    public static final String SEM_GIC_RULE = "semGicRule";
    /**
     * Constant <code>SEPSET_FINDER_METHOD="sepsetFinderMethod"</code>
     */
    public static final String SEPSET_FINDER_METHOD = "sepsetFinderMethod";
    /**
     * Constant <code>SHRINKAGE_MODE="shrinkageMode"</code>
     */
    public static final String SHRINKAGE_MODE = "shrinkageMode";
    /**
     * Constant <code>SIGNIFICANCE_CHECKED="significanceChecked"</code>
     */
    public static final String SIGNIFICANCE_CHECKED = "significanceChecked";
    /**
     * Constant <code>SINGULARITY_LAMBDA="singularityLambda"</code>
     */
    public static final String SINGULARITY_LAMBDA = "singularityLambda";
    /**
     * Constant <code>SKEW_EDGE_THRESHOLD="skewEdgeThreshold"</code>
     */
    public static final String SKEW_EDGE_THRESHOLD = "skewEdgeThreshold";
    /**
     * Constant <code>SKIP_NUM_RECORDS="skipNumRecords"</code>
     */
    public static final String SKIP_NUM_RECORDS = "skipNumRecords";
    /**
     * Constant <code>SPLIT_DELTA="splitDelta"</code>
     */
    public static final String SPLIT_DELTA = "splitDelta";
    /**
     * Constant <code>STABLE_FAS="stableFAS"</code>
     */
    public static final String STABLE_FAS = "stableFAS";
    /**
     * Constant <code>STANDARDIZE="standardize"</code>
     */
    public static final String STANDARDIZE = "standardize";
    /**
     * Constant <code>STARS_CUTOFF="StARS.cutoff"</code>
     */
    public static final String STARS_CUTOFF = "StARS.cutoff";
    /**
     * Constant <code>START_FROM_COMPLETE_GRAPH="startFromCompleteGraph"</code>
     */
    public static final String START_FROM_COMPLETE_GRAPH = "startFromCompleteGraph";
    /**
     * Constant <code>STRUCTURE_PRIOR="structurePrior"</code>
     */
    public static final String STRUCTURE_PRIOR = "structurePrior";
    /**
     * Constant <code>SYMMETRIC_FIRST_STEP="symmetricFirstStep"</code>
     */
    public static final String SYMMETRIC_FIRST_STEP = "symmetricFirstStep";
    /**
     * Constant <code>TARGETS="targets"</code>
     */
    public static final String TARGETS = "targets";
    /**
     * Constant <code>TARGET_NAME="targetName"</code>
     */
    public static final String TARGET_NAME = "targetName";
    /**
     * Constant <code>TEST_TIMEOUT="testTimeout"</code>
     */
    public static final String TEST_TIMEOUT = "testTimeout";
    /**
     * Constant <code>TETRAD_TEST_BPC="tetrad_test_bpc"</code>
     */
    public static final String TETRAD_TEST_BPC = "tetrad_test_bpc";
    /**
     * Constant <code>TETRAD_TEST_FOFC="tetrad_test_fofc"</code>
     */
    public static final String TETRAD_TEST_FOFC = "tetrad_test_fofc";
    /**
     * Constant <code>THR="thr"</code>
     */
    public static final String THR = "thr";
    /**
     * Constant <code>THRESHOLD_B="thresholdBHat"</code>
     */
    public static final String THRESHOLD_B = "thresholdBHat";
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
     * Constant <code>THRESHOLD_W="thresholdW"</code>
     */
    public static final String THRESHOLD_W = "thresholdW";
    /**
     * Constant <code>TIMEOUT="timeout"</code>
     */
    public static final String TIMEOUT = "timeout";
    /**
     * Constant <code>TIME_LAG="timeLag"</code>
     */
    public static final String TIME_LAG = "timeLag";
    /**
     * Constant <code>REPEATING_GRAPH="repeatingGraph"</code> Whether to use a repeating graph for time lag search.
     */
    public static final String TIME_LAG_REPLICATING_GRAPH = "timeLagReplicatingGraph";
    /**
     * Constant <code>TOP_BRACKET="topBracket"</code>
     */
    public static final String TOP_BRACKET = "topBracket";
    /**
     * Constant <code>TOP_K="topK"</code>
     */
    public static final String TOP_K = "topK";
    /**
     * Constant <code>TRFF_FF_FEATURES="trffFfFeatures"</code>
     */
    public static final String TRFF_FF_FEATURES = "trffFfFeatures";
    /**
     * Constant <code>TRFF_NU="trffNu"</code>
     */
    public static final String TRFF_NU = "trffNu";
    /**
     * Constant <code>TRFF_RIDGE="trffRidge"</code>
     */
    public static final String TRFF_RIDGE = "trffRidge";
    /**
     * Constant <code>TRFF_SYMMETRIZED="trffSymmetrized"</code>
     */
    public static final String TRFF_SYMMETRIZED = "trffSymmetrized";
    /**
     * Constant <code>TRIMMING_STYLE="trimmingStyle"</code>
     */
    public static final String TRIMMING_STYLE = "trimmingStyle";
    /**
     * Constant <code>TRUNCATION_LIMIT="truncationLimit"</code>
     */
    public static final String TRUNCATION_LIMIT = "truncationLimit";
    /**
     * Constant <code>TSC_CLUSTER_RANK="tscClusterRank"</code>
     */
    public static final String TSC_CLUSTER_RANK = "tscClusterRank";
    /**
     * Constant <code>TSC_CLUSTER_SIZE="tscClusterSize"</code>
     */
    public static final String TSC_CLUSTER_SIZE = "tscClusterSize";
    /**
     * Constant <code>TSC_ENABLE_HIERARCHY="tscEnableHierarchy"</code>
     */
    public static final String TSC_ENABLE_HIERARCHY = "tscEnableHierarchy";
    /**
     * Constant <code>TSC_MIN_RANK_DROP="tscMinRankDrop"</code>
     */
    public static final String TSC_MIN_RANK_DROP = "tscMinRankDrop";
    /**
     * Constant <code>TSC_MIN_REDUNDANCY="tscMinRedundancy"</code>
     */
    public static final String TSC_MIN_REDUNDANCY = "tscMinRedundancy";
    /**
     * Constant <code>TSC_MODE="tscMode"</code>
     */
    public static final String TSC_MODE = "tscMode";
    /**
     * Constant <code>TSC_PC_USE_BOSS="tscPcUseBoss"</code>
     */
    public static final String TSC_PC_USE_BOSS = "tscPcUseBoss";
    /**
     * Constant <code>TSC_SINGLETON_POLICY="tscSingletonPolicy"</code>
     */
    public static final String TSC_SINGLETON_POLICY = "tscSingletonPolicy";
    /**
     * Constant <code>TWO_CYCLE_ALPHA="twoCycleAlpha"</code>
     */
    public static final String TWO_CYCLE_ALPHA = "twoCycleAlpha";
    /**
     * Constant <code>TWO_CYCLE_SCREENING_THRESHOLD="twoCycleScreeningThreshold"</code>
     */
    public static final String TWO_CYCLE_SCREENING_THRESHOLD = "twoCycleScreeningThreshold";
    /**
     * Constant <code>UPPER_BOUND="upperBound"</code>
     */
    public static final String UPPER_BOUND = "upperBound";
    /**
     * Constant <code>USE_ADAPTIVE_Z_BINS="useAdaptiveZBins"</code>
     */
    public static final String USE_ADAPTIVE_Z_BINS = "useAdaptiveZBins";
    /**
     * Constant <code>USE_BES="useBes"</code>
     */
    public static final String USE_BES = "useBes";
    /**
     * Constant <code>USE_BOSS_ADJACENCIES="useBossAdjacencies"</code>
     */
    public static final String USE_BOSS_ADJACENCIES = "useBossAdjacencies";
    /**
     * Constant <code>USE_CORR_DIFF_ADJACENCIES="useCorrDiffAdjacencies"</code>
     */
    public static final String USE_CORR_DIFF_ADJACENCIES = "useCorrDiffAdjacencies";
    /**
     * Constant <code>USE_DATA_ORDER="useDataOrder"</code>
     */
    public static final String USE_DATA_ORDER = "useDataOrder";
    /**
     * Constant <code>USE_FAS_ADJACENCIES="useFasAdjacencies"</code>
     */
    public static final String USE_FAS_ADJACENCIES = "useFasAdjacencies";
    /**
     * Constant <code>USE_GAP="useGap"</code>
     */
    public static final String USE_GAP = "useGap";
    /**
     * Constant <code>USE_ILS_RESTARTS="useIlsRestarts"</code>
     */
    public static final String USE_ILS_RESTARTS = "useIlsRestarts";
    /**
     * Constant <code>USE_MAX_ACROSS_STRATA="useMaxAcrossStrata"</code>
     */
    public static final String USE_MAX_ACROSS_STRATA = "useMaxAcrossStrata";
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
     * Constant <code>USE_VARIMAX="useVarimax"</code>
     */
    public static final String USE_VARIMAX = "useVarimax";
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
     * Constant <code>W_THRESHOLD="wThreshold"</code>
     */
    public static final String W_THRESHOLD = "wThreshold";
    /**
     * Constant <code>ZS_RISK_BOUND="zSRiskBound"</code>
     */
    public static final String ZS_RISK_BOUND = "zSRiskBound";

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
        return (algorithm instanceof TakesScoreWrapper)
                ? new HashSet<>(((TakesScoreWrapper) algorithm).getScoreWrapper().getParameters())
                : Collections.emptySet();
    }

    /**
     * <p>getBootstrappingParameters.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     * @return a {@link java.util.Set} object
     */
    public static Set<String> getBootstrappingParameters(Algorithm algorithm) {
        // Changed 2026-8-13: the ability to bootstrap is determined by extending
        // AbstractBootstrapAlgorithm (which is what actually implements the resampling at run time), not by the
        // presence of the Bootstrapping annotation. Keeping capability and annotation as two separate sources of
        // truth caused algorithms that could bootstrap to silently lose their bootstrapping parameters in the
        // interface whenever the annotation was forgotten (RfciBsc, PagSamplingRfci, ImagesFges), and algorithms
        // that cannot bootstrap to be offered parameters that did nothing (FgesConcatenated, FaskConcatenated,
        // FaskVote). The annotation is retained as documentation and is now @Inherited from the abstract bases.
        return (algorithm instanceof edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm)
                ? Params.BOOTSTRAPPING_PARAMS
                : Collections.emptySet();
    }

}

