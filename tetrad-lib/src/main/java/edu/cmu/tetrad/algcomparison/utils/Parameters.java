package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.bayes.Evidence;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.TetradSerializable;
import sun.font.GlyphLayout;

import java.util.*;

/**
 * Stores a list of named parameters with their values. Stores default values for known
 * parameters. Returns a list of parameters with their values, for the parameters whose
 * values have been retrieved, using the toString method.
 *
 * @author jdramsey
 */
public class Parameters implements TetradSerializable {
    private Map<String, Object[]> parameters = new LinkedHashMap<>();
    private Set<String> usedParameters = new LinkedHashSet<>();
    private Map<String, Object> overriddenParameters = new HashMap<>();

    public Parameters() {

        // Defaults
        set("numMeasures", 10);
        set("numLatents", 0);
        set("avgDegree", 2);
        set("maxDegree", 100);
        set("maxIndegree", 100);
        set("maxOutdegree", 100);
        set("connected", 0);
        set("sampleSize", 1000);
        set("numRuns", 1);
        set("alpha", 0.001);
        set("penaltyDiscount", 4);
        set("fgsDepth", -1);
        set("depth", -1);
        set("coefLow", 0.5);
        set("coefHigh", 1.5);
        set("variance", -1);
        set("varianceLow", 1.0);
        set("varianceHigh", 3.0);
        set("printWinners", 0);
        set("printAverages", 0);
        set("printAverageTables", 1);
        set("printGraph", 0);
        set("percentDiscrete", 50);
        set("ofInterestCutoff", 0.05);
        set("printGraphs", 0);
        set("numCategories", 4);
        set("samplePrior", 1);
        set("structurePrior", 1);
        set("mgmParam1", 0.1);
        set("mgmParam2", 0.1);
        set("mgmParam3", 0.1);
        set("scaleFreeAlpha", 0.9);
        set("scaleFreeBeta", 0.05);
        set("scaleFreeDeltaIn", 3);
        set("scaleFreeDeltaOut", 3);
        set("generalSemFunctionTemplateMeasured", "TSUM(NEW(B)*$)");
        set("generalSemFunctionTemplateLatent", "TSUM(NEW(B)*$)");
        set("generalSemErrorTemplate", "Beta(2, 5)");
        set("varLow", 1);
        set("varHigh", 3);
    }

    public Parameters(Parameters parameters) {
        this.parameters = new LinkedHashMap<>(parameters.parameters);
        this.usedParameters = new LinkedHashSet<>(parameters.usedParameters);
        this.overriddenParameters = new HashMap<>(parameters.overriddenParameters);
    }

    /**
     * Returns a list of the parameters whoese values were actually used in the course of
     * the simulatoin.
     *
     * @return This list, in String form.
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (String param : usedParameters) {
            builder.append("\n").append(param).append(" = ").append(parameters.get(param)[0]);
        }

        return builder.toString();
    }

    /**
     * Returns the integer values of the given parameter.
     *
     * @param name The name of the parameter.
     * @param defaultValue
     * @return The integer value of this parameter.
     */
    public int getInt(String name, int defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return ((Number) o).intValue();
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            Object o = objects[0];
            return ((Number) o).intValue();
        }
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return (Boolean) o;
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            Object o = objects[0];
            return (Boolean) o;
        }
    }

    /**
     * Returns the double values of the given parameter.
     *
     * @param name The name of the parameter.
     * @param defaultValue
     * @return The double value of this parameter.
     */
    public double getDouble(String name, double defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return ((Number) o).doubleValue();
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }

        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            Object o = objects[0];
            return ((Number) o).intValue();
        }
    }

    /**
     * Returns the string values of the given parameter.
     *
     * @param name The name of the parameter.
     * @param defaultValue
     * @return The double value of this parameter.
     */
    public String getString(String name, String defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            Object o = overriddenParameters.get(name);
            return (String) o;
        }

        if (getNumValues(name) != 1) {
            throw new IllegalArgumentException("Parameter '" + name + "' has more than one value.");
        }
        usedParameters.add(name);
        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            Object o = objects[0];
            return (String) o;
        }
    }

    /**
     * Returns the object for the given parameter.
     *
     * @param name The name of the parameter.
     * @param defaultValue
     * @return the object value.
     */
    public Object get(String name, Object defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            return overriddenParameters.get(name);
        }

        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            return objects[0];
        }
    }

    /**
     * Returns the values set for the given parameter. Usually of length 1.
     *
     * @param name The name of the parameter.
     * @param defaultValue
     * @return The array of values.
     */
    public Object[] getValues(String name, Object[] defaultValue) {
        if (overriddenParameters.containsKey(name)) {
            return (Object[]) overriddenParameters.get(name);
        }

        Object[] objects = parameters.get(name);

        if (objects == null) {
            return defaultValue;
        } else {
            return objects;
        }
    }

    /**
     * Sets the value(s) of the given parameter to a list of strings.
     *
     * @param name The name of the parameter.
     * @param n    A list of values for the parameter.
     */
    public void set(String name, Object... n) {
        parameters.put(name, n);
    }

    /**
     * Sets the value(s) of the given parameter to a list of values.
     *
     * @param name The name of the parameter.
     * @param s    A list of strings for the parameter.
     */
    public void set(String name, String... s) {
        parameters.put(name, s);
    }

    /**
     * Returns the number of values for the parameter.
     *
     * @param parameter The parameter of the parameter.
     * @return The number of values set for that parameter.
     */
    public int getNumValues(String parameter) {
        Object[] objects = parameters.get(parameter);
        if (objects == null) {
            throw new IllegalArgumentException("Expecting a value for parameter '" + parameter + "'");
        }
        return objects.length;
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param parameter The name of the parameter.
     * @param value     The value of the parameter (a single value).
     */
    public void set(String parameter, Object value) {
        parameters.put(parameter, new Object[]{value});
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param parameter The name of the parameter.
     * @param value     The value of the parameter (a single value).
     */
    public void set(String parameter, String value) {
        parameters.put(parameter, new String[]{value});
    }

    /**
     * Sets a map of parameters to override the current ones.
     *
     * @param parameters A map from parameter names to values.
     */
    public void setOverriddenParameters(Map<String, Object> parameters) {
        this.overriddenParameters = parameters;
    }

    public Map<String, Object[]> getParameters() {
        return parameters;
    }

    public Set<String> getUsedParameters() {
        return usedParameters;
    }


    //===

    private boolean retainPreviousValues;
    private boolean coefSymmetric;
    private double covLow = 0.05;
    private double covHigh = 0.1;
    private double varLow = 1;
    private double varHigh = 3;
    private List<String> equations = new ArrayList<>();
    private boolean cyclicAllowed = false;
    private String newGraphInitializationMode;
    private boolean covSymmetric = true;
    private int numRestarts = 1;
    private SemIm.ScoreType scoreType;
    private String semOptimizerType;
    private IKnowledge knowledge;
    private IndependenceFacts independenceFacts;
    private List<String> varNames;
    private Class indClass;
    private int numTimePoints;
    private double alpha;
    private int numLags;
    private int depth;
    private Graph sourceGraph;
    private IndTestType indTestType;
    private boolean randomGraphAddCycles;
    private int newGraphNumMeasuredNodes;
    private int newGraphNumEdges;
    private Parameters indTestParams;
    private double samplePrior;
    private double structurePrior;
    private double penaltyDiscount;
    private int numPatternsToSave;
    private boolean faithfulnessAssumed;
    private String[] regressorNames;
    private String targetName;
    private int sampleSize;
    private boolean latentDataSaved;
    private int numDataSets;
    private Clusters clusters;
    private TestType tetradTestType;
    private TestType purifyTestType;
    private Evidence evidence;
    private Node variable;
    private String initializationMode;
    private int lowerBoundNumVals;
    private int upperBoundNumVals;
    private BpcAlgorithmType bpcAlgorithmType;
    private double symmetricAlpha;
    private boolean aggressivelyPreventCycles;
    private FindOneFactorClusters.Algorithm fofcAlgorithms;
    private boolean include3Clusters;
    private ArrayList<String> latentVarNames;
    private boolean showMaxP;
    private double maxP;
    private Graph maxStructureGraph;
    private Clusters maxClusters;
    private Graph maxFullGraph;
    private double maxAlpha;
    private boolean firstNontriangular;
    private Lofs2.Rule rule;
    private boolean orientStrongerDirection;
    private boolean meanCenterResiduals;
    private boolean r2Orient2Cycles;
    private Lofs.Score score;
    private double epsilon;
    private double zeta;
    private double selfLoopStrength;
    private boolean pruneByAdjacencies;
    private boolean pruneByPathLength;
    private int graphIndex;
    private double threshold;
    private boolean RFCI_Used;
    private boolean completeRuleSetUsed;
    private int maxReachablePathLength;
    private boolean possibleDsepDone;
    private int numOfTimeLags;
    private int numTimeLags;
    private double lambda;
    private boolean includeLatents;
    private boolean resetTableOnExecute;
    private boolean keepLatents;
    private String referenceGraphName;
    private String targetGraphName;
    private  List<GraphUtils.GraphComparison> records;
    private DataSet dataSet;
    private Graph graph;
    private List<Node> highlightInEditor;
    private List<Node> selectedVariables;
    private Graph selectionGraph;
    private String graphSelectionType;
    private String nType;
    private String dialogText;
    private String name;
    private int n;
    private boolean forwardSearch;
    private int[] shifts;
    private double tolerance;
    private boolean sampleSizeSet;
    private double bias;
    private double timeLimit;
    private GlyphLayout specs;
    private int maxit;
    private double thr;
    private boolean ia;
    private boolean is;
    private boolean ipen;
    private boolean itr;
    private int beamWidth;
    private double zeroEdgeP;
    private int numSplits;
    private SplitCasesSpec spec;
    private boolean dataShuffled;
    private double prob;
    private FindTwoFactorClusters.Algorithm ftfcAlgorithm;

    public boolean isRetainPreviousValues() {
        return retainPreviousValues;
    }

    public double getCoefLow() {
        return getDouble("coefLow", 0.5);
    }

    public double getCoefHigh() {
        return getDouble("coefHigh", 1.5);
    }

    public boolean isCoefSymmetric() {
        return coefSymmetric;
    }

    public double getCovLow() {
        return covLow;
    }

    public double getCovHigh() {
        return covHigh;
    }

    public void setEquations(List<String> equations) {
        this.equations = equations;
    }

    public List<String> getEquations() {
        return equations;
    }

    public boolean isCyclicAllowed() {
        return cyclicAllowed;
    }

    public String getNewGraphInitializationMode() {
        return newGraphInitializationMode;
    }

    public void setNewGraphInitializationMode(String newGraphInitializationMode) {
        this.newGraphInitializationMode = newGraphInitializationMode;
    }

    public void setCovRange(double low, double high) {
        set("covLow", low);
        set("high", high);
    }

    public void setVarRange(double value, double varHigh) {

    }

    public boolean isCovSymmetric() {
        return covSymmetric;
    }

    public void setCoefSymmetric(boolean coefSymmetric) {

        this.coefSymmetric = coefSymmetric;
    }

    public void setCovSymmetric(boolean covSymmetric) {
        this.covSymmetric = covSymmetric;
    }

    public void setRetainPreviousValues(boolean retainPreviousValues) {
        this.retainPreviousValues = retainPreviousValues;
    }

    public int getNumRestarts() {
        return numRestarts;
    }

    public SemIm.ScoreType getScoreType() {
        return scoreType;
    }

    public String getSemOptimizerType() {
        return semOptimizerType;
    }

    public void setSemOptimizerType(String semOptimizerType) {
        this.semOptimizerType = semOptimizerType;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setIndependenceFacts(IndependenceFacts independenceFacts) {
        this.independenceFacts = independenceFacts;
    }

    public List<String> getVarNames() {
        return varNames;
    }

    public void setVarNames(List<String> varNames) {
        this.varNames = varNames;
    }

    public Class getIndClass() {
        return indClass;
    }

    public int getNumTimePoints() {
        return numTimePoints;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public int getNumLags() {
        return numLags;
    }

    public void setNumLags(int numLags) {
        this.numLags = numLags;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public Graph getSourceGraph() {
        return sourceGraph;
    }

    public void setSourceGraph(Graph sourceGraph) {
        this.sourceGraph = sourceGraph;
    }

    public void setIndTestType(IndTestType indTestType) {
        this.indTestType = indTestType;
    }

    public IndTestType getIndTestType() {
        return indTestType;
    }

    public boolean isRandomGraphAddCycles() {
        return randomGraphAddCycles;
    }

    public int getNewGraphNumMeasuredNodes() {
        return newGraphNumMeasuredNodes;
    }

    public int getNewGraphNumEdges() {
        return newGraphNumEdges;
    }

    public double getSamplePrior() {
        return samplePrior;
    }

    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    public double getStructurePrior() {
        return 0;
    }

    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public int getNumPatternsToSave() {
        return numPatternsToSave;
    }

    public void setNumPatternsToSave(int numPatternsToSave) {
        this.numPatternsToSave = numPatternsToSave;
    }

    public boolean isFaithfulnessAssumed() {
        return faithfulnessAssumed;
    }

    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    public String[] getRegressorNames() {
        return regressorNames;
    }

    public void setRegressorNames(String[] regressorNames) {
        this.regressorNames = regressorNames;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public boolean isLatentDataSaved() {
        return latentDataSaved;
    }

    public void setLatentDataSaved(boolean latentDataSaved) {
        this.latentDataSaved = latentDataSaved;
    }

    public int getNumDataSets() {
        return numDataSets;
    }

    public void setNumDataSets(int numDataSets) {
        this.numDataSets = numDataSets;
    }

    public Clusters getClusters() {
        return clusters;
    }

    public void setClusters(Clusters clusters) {
        this.clusters = clusters;
    }

    public TestType getTetradTestType() {
        return (TestType) tetradTestType;
    }

    public void setTetradTestType(TestType tetradTestType) {
        this.tetradTestType = tetradTestType;
    }

    public Object getPurifyTestType() {
        return purifyTestType;
    }

    public void setPurifyTestType(TestType purifyTestType) {
        this.purifyTestType = purifyTestType;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public void setEvidence(Evidence evidence) {
        this.evidence = evidence;
    }

    public Node getVariable() {
        return variable;
    }

    public void setVariable(Node variable) {
        this.variable = variable;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public String getInitializationMode() {
        return initializationMode;
    }

    public void setInitializationMode(String value) {
        this.initializationMode = value;
    }

    public int getLowerBoundNumVals() {
        return lowerBoundNumVals;
    }

    public void setLowerBoundNumVals(int lowerBoundNumVals) {
        this.lowerBoundNumVals = lowerBoundNumVals;
    }

    public int getUpperBoundNumVals() {
        return upperBoundNumVals;
    }

    public void setUpperBoundNumVals(int upperBoundNumVals) {
        this.upperBoundNumVals = upperBoundNumVals;
    }

    public BpcAlgorithmType getBpcAlgorithmType() {
        return bpcAlgorithmType;
    }

    public void setBpcAlgorithmType(BpcAlgorithmType bpcAlgorithmType) {
        this.bpcAlgorithmType = bpcAlgorithmType;
    }

    public double getSymmetricAlpha() {
        return symmetricAlpha;
    }

    public void setSymmetricAlpha(double symmetricAlpha) {
        this.symmetricAlpha = symmetricAlpha;
    }

    public boolean isAggressivelyPreventCycles() {
        return aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public FindOneFactorClusters.Algorithm getFofcAlgorithms() {
        return fofcAlgorithms;
    }

    public void setFofcAlgorithms(FindOneFactorClusters.Algorithm fofcAlgorithms) {
        this.fofcAlgorithms = fofcAlgorithms;
    }

    public boolean isInclude3Clusters() {
        return include3Clusters;
    }

    public void setInclude3Clusters(boolean include3Clusters) {
        this.include3Clusters = include3Clusters;
    }

    public void setLatentVarNames(ArrayList<String> latentVarNames) {
        this.latentVarNames = latentVarNames;
    }

    public boolean isShowMaxP() {
        return showMaxP;
    }

    public void setShowMaxP(boolean showMaxP) {
        this.showMaxP = showMaxP;
    }

    public double getMaxP() {
        return maxP;
    }

    public void setMaxP(double maxP) {
        this.maxP = maxP;
    }

    public void setMaxStructureGraph(Graph maxStructureGraph) {
        this.maxStructureGraph = maxStructureGraph;
    }

    public Graph getMaxStructureGraph() {
        return maxStructureGraph;
    }

    public void setMaxClusters(Clusters maxClusters) {
        this.maxClusters = maxClusters;
    }

    public Clusters getMaxClusters() {
        return maxClusters;
    }

    public void setMaxFullGraph(Graph maxFullGraph) {
        this.maxFullGraph = maxFullGraph;
    }

    public Graph getMaxFullGraph() {
        return maxFullGraph;
    }

    public void setMaxAlpha(double maxAlpha) {
        this.maxAlpha = maxAlpha;
    }

    public boolean isFirstNontriangular() {
        return firstNontriangular;
    }

    public double getPruneFactor() {
        return 0;
    }

    public void setPruneFactor(double value) {
    }

    public Lofs2.Rule getRule() {
        return rule;
    }

    public void setRule(Lofs2.Rule rule) {
        this.rule = rule;
    }

    public boolean isOrientStrongerDirection() {
        return orientStrongerDirection;
    }

    public void setOrientStrongerDirection(boolean orientStrongerDirection) {
        this.orientStrongerDirection = orientStrongerDirection;
    }

    public boolean isMeanCenterResiduals() {
        return meanCenterResiduals;
    }

    public void setMeanCenterResiduals(boolean meanCenterResiduals) {
        this.meanCenterResiduals = meanCenterResiduals;
    }

    public boolean isR2Orient2Cycles() {
        return r2Orient2Cycles;
    }

    public void setR2Orient2Cycles(boolean r2Orient2Cycles) {
        this.r2Orient2Cycles = r2Orient2Cycles;
    }

    public Lofs.Score getScore() {
        return score;
    }

    public void setScore(Lofs.Score score) {
        this.score = score;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    public double getZeta() {
        return zeta;
    }

    public void setZeta(double zeta) {
        this.zeta = zeta;
    }

    public double getSelfLoopStrength() {
        return selfLoopStrength;
    }

    public void setSelfLoopStrength(double selfLoopStrength) {
        this.selfLoopStrength = selfLoopStrength;
    }

    public boolean isPruneByAdjacencies() {
        return pruneByAdjacencies;
    }

    public void setPruneByAdjacencies(boolean pruneByAdjacencies) {
        this.pruneByAdjacencies = pruneByAdjacencies;
    }

    public boolean isPruneByPathLength() {
        return pruneByPathLength;
    }

    public void setPruneByPathLength(boolean pruneByPathLength) {
        this.pruneByPathLength = pruneByPathLength;
    }

    public void setGraphIndex(int graphIndex) {
        this.graphIndex = graphIndex;
    }

    public int getGraphIndex() {
        return graphIndex;
    }


    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public boolean isRFCI_Used() {
        return RFCI_Used;
    }

    public void setRFCI_Used(boolean RFCI_Used) {
        this.RFCI_Used = RFCI_Used;
    }

    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public int getMaxReachablePathLength() {
        return maxReachablePathLength;
    }

    public void setMaxReachablePathLength(int maxReachablePathLength) {
        this.maxReachablePathLength = maxReachablePathLength;
    }

    public boolean isPossibleDsepDone() {
        return possibleDsepDone;
    }

    public void setPossibleDsepDone(boolean possibleDsepDone) {
        this.possibleDsepDone = possibleDsepDone;
    }

    public int getNumOfTimeLags() {
        return numOfTimeLags;
    }

    public void setNumOfTimeLags(int numOfTimeLags) {
        this.numOfTimeLags = numOfTimeLags;
    }

    public int getNumTimeLags() {
        return numTimeLags;
    }

    public double getLambda() {
        return lambda;
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    public void setIncludeLatents(boolean includeLatents) {
        this.includeLatents = includeLatents;
    }

    public void setResetTableOnExecute(boolean resetTableOnExecute) {
        this.resetTableOnExecute = resetTableOnExecute;
    }

    public boolean isResetTableOnExecute() {
        return resetTableOnExecute;
    }

    public void setKeepLatents(boolean keepLatents) {
        this.keepLatents = keepLatents;
    }

    public boolean isKeepLatents() {
        return keepLatents;
    }

    public void setReferenceGraphName(String referenceGraphName) {
        this.referenceGraphName = referenceGraphName;
    }

    public String getReferenceGraphName() {
        return referenceGraphName;
    }

    public void setTargetGraphName(String targetGraphName) {
        this.targetGraphName = targetGraphName;
    }

    public String getTargetGraphName() {
        return targetGraphName;
    }

    public List<GraphUtils.GraphComparison> getRecords() {
        return records;
    }

    public void setRecords(List<GraphUtils.GraphComparison> records) {
        this.records = records;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public void setDataSet(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public List<Node> getHighlightInEditor() {
        return highlightInEditor;
    }

    public void setHighlightInEditor(List<Node> highlightInEditor) {
        this.highlightInEditor = highlightInEditor;
    }

    public List<Node> getSelectedVariables() {
        return selectedVariables;
    }

    public void setSelectedVariables(List<Node> selectedVariables) {
        this.selectedVariables = selectedVariables;
    }

    public void setSelectionGraph(Graph selectionGraph) {
        this.selectionGraph = selectionGraph;
    }

    public Graph getSelectionGraph() {
        return selectionGraph;
    }

    public String getGraphSelectionType() {
        return graphSelectionType;
    }

    public void setGraphSelectionType(String graphSelectionType) {
        this.graphSelectionType = graphSelectionType;
    }

    public String getnType() {
        return nType;
    }

    public void setnType(String nType) {
        this.nType = nType;
    }

    public void setDialogText(String dialogText) {
        this.dialogText = dialogText;
    }

    public String getDialogText() {
        return dialogText;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setN(int n) {
        this.n = n;
    }

    public int getN() {
        return n;
    }

    public boolean isForwardSearch() {
        return forwardSearch;
    }

    public void setForwardSearch(boolean forwardSearch) {
        this.forwardSearch = forwardSearch;
    }

    public int[] getShifts() {
        return shifts;
    }

    public void setShifts(int[] shifts) {
        this.shifts = shifts;
    }

    public double getTolerance() {
        return tolerance;
    }

    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    public boolean isSampleSizeSet() {
        return sampleSizeSet;
    }

    public double getBias() {
        return bias;
    }

    public void setBias(double bias) {
        this.bias = bias;
    }

    public double getTimeLimit() {
        return timeLimit;
    }

    public void setTimeLimit(double timeLimit) {
        this.timeLimit = timeLimit;
    }

    public int getMaxit() {
        return maxit;
    }

    public void setMaxit(int maxit) {
        this.maxit = maxit;
    }

    public double getThr() {
        return thr;
    }

    public void setThr(double thr) {
        this.thr = thr;
    }

    public boolean isIa() {
        return ia;
    }

    public void setIa(boolean ia) {
        this.ia = ia;
    }

    public void setIs(boolean is) {
        this.is = is;
    }

    public boolean isIs() {
        return is;
    }

    public void setIpen(boolean ipen) {
        this.ipen = ipen;
    }

    public boolean isIpen() {
        return ipen;
    }

    public void setItr(boolean itr) {
        this.itr = itr;
    }

    public boolean isItr() {
        return itr;
    }

    public int getBeamWidth() {
        return beamWidth;
    }

    public void setBeamWidth(int beamWidth) {
        this.beamWidth = beamWidth;
    }

    public double getZeroEdgeP() {
        return zeroEdgeP;
    }

    public void setZeroEdgeP(double zeroEdgeP) {
        this.zeroEdgeP = zeroEdgeP;
    }

    public void setNumSplits(int numSplits) {
        this.numSplits = numSplits;
    }

    public int getNumSplits() {
        return numSplits;
    }

    public void setSpec(SplitCasesSpec spec) {
        this.spec = spec;
    }

    public SplitCasesSpec getSpec() {
        return spec;
    }

    public void setDataShuffled(boolean dataShuffled) {
        this.dataShuffled = dataShuffled;
    }

    public boolean isDataShuffled() {
        return dataShuffled;
    }

    public void addRecord(GraphUtils.GraphComparison comparison) {

    }

    public void setProb(double prob) {
        this.prob = prob;
    }

    public double getProb() {
        return prob;
    }

    public FindTwoFactorClusters.Algorithm getFtfcAlgorithm() {
        return ftfcAlgorithm;
    }

    public void setFtfcAlgorithm(FindTwoFactorClusters.Algorithm ftfcAlgorithm) {
        this.ftfcAlgorithm = ftfcAlgorithm;
    }


    public void setScoreType(SemIm.ScoreType scoreType) {
        this.scoreType = scoreType;
    }

    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

}
