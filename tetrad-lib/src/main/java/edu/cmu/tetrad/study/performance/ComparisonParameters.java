package edu.cmu.tetrad.study.performance;

import edu.cmu.tetrad.sem.ScoreType;

/**
 * Created by jdramsey on 3/24/16. Edited by dmalinsky 5/20/16.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ComparisonParameters {

    /**
     * The data type.
     */
    private DataType dataType;

    /**
     * The result type.
     */
    private ResultType resultType;

    /**
     * The num vars.
     */
    private int numVars = 100;

    /**
     * The num edges.
     */
    private int numEdges = 100;

    /**
     * The sample size.
     */
    private int sampleSize = 1000;

    /**
     * The independence test.
     */
    private IndependenceTestType independenceTest;

    /**
     * The alpha.
     */
    private double alpha = 0.001;

    /**
     * The penalty discount.
     */
    private double penaltyDiscount = 4;

    /**
     * The score.
     */
    private ScoreType score;

    /**
     * The sample prior.
     */
    private double samplePrior = 1;

    /**
     * The structure prior.
     */
    private double structurePrior = 1;

    /**
     * The algorithm.
     */
    private Algorithm algorithm;

    /**
     * The data file.
     */
    private String dataFile;

    /**
     * The graph file.
     */
    private String graphFile;

    /**
     * The one edge faithfulness assumed.
     */
    private boolean oneEdgeFaithfulnessAssumed;

    /**
     * The no data.
     */
    private boolean noData;

    /**
     * The data from file.
     */
    private boolean dataFromFile;

    /**
     * The graph num.
     */
    private int graphNum;

    /**
     * The trial.
     */
    private int trial;

    /**
     * <p>Constructor for ComparisonParameters.</p>
     */
    public ComparisonParameters() {

    }

    /**
     * <p>Constructor for ComparisonParameters.</p>
     *
     * @param params a {@link edu.cmu.tetrad.study.performance.ComparisonParameters} object
     */
    public ComparisonParameters(ComparisonParameters params) {
        this.dataType = params.dataType;
        this.resultType = params.resultType;
        this.numVars = params.numVars;
        this.numEdges = params.numEdges;
        this.sampleSize = params.sampleSize;
        this.independenceTest = params.independenceTest;
        this.alpha = params.alpha;
        this.score = params.score;
        this.algorithm = params.algorithm;
        this.dataFile = params.dataFile;
        this.graphFile = params.graphFile;
        this.oneEdgeFaithfulnessAssumed = params.oneEdgeFaithfulnessAssumed;
        this.noData = params.noData;
        this.dataFromFile = params.dataFromFile;
    }

    /**
     * <p>Getter for the field <code>dataType</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.study.performance.ComparisonParameters.DataType} object
     */
    public DataType getDataType() {
        return this.dataType;
    }

    /**
     * <p>Setter for the field <code>dataType</code>.</p>
     *
     * @param dataType a {@link edu.cmu.tetrad.study.performance.ComparisonParameters.DataType} object
     */
    public void setDataType(DataType dataType) {
        if (this.dataType != null && this.dataType != dataType) {
            throw new IllegalArgumentException("Data type conflicts with previous data type.");
        }

        this.dataType = dataType;
    }

    /**
     * <p>Getter for the field <code>resultType</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.study.performance.ComparisonParameters.ResultType} object
     */
    public ResultType getResultType() {
        return this.resultType;
    }

    /**
     * <p>Setter for the field <code>resultType</code>.</p>
     *
     * @param resultType a {@link edu.cmu.tetrad.study.performance.ComparisonParameters.ResultType} object
     */
    public void setResultType(ResultType resultType) {
        if (this.resultType != null && this.resultType != resultType) {
            throw new IllegalArgumentException("Result type conflicts with previous result type.");
        }

        this.resultType = resultType;
    }

    /**
     * <p>Getter for the field <code>numVars</code>.</p>
     *
     * @return a int
     */
    public int getNumVars() {
        return this.numVars;
    }

    /**
     * <p>Setter for the field <code>numVars</code>.</p>
     *
     * @param numVars a int
     */
    public void setNumVars(int numVars) {
        if (numVars < 1) {
            throw new IllegalArgumentException("Number of variables must be >= 1.");
        }

        this.numVars = numVars;
    }

    /**
     * <p>Getter for the field <code>numEdges</code>.</p>
     *
     * @return a int
     */
    public int getNumEdges() {
        return this.numEdges;
    }

    /**
     * <p>Setter for the field <code>numEdges</code>.</p>
     *
     * @param numEdges a int
     */
    public void setNumEdges(int numEdges) {
        if (numEdges < 1) {
            throw new IllegalArgumentException("Number of edges must be >= 1.");
        }

        this.numEdges = numEdges;
    }

    /**
     * <p>Getter for the field <code>sampleSize</code>.</p>
     *
     * @return a int
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * <p>Setter for the field <code>sampleSize</code>.</p>
     *
     * @param sampleSize a int
     */
    public void setSampleSize(int sampleSize) {
        if (sampleSize < 1) {
            throw new IllegalArgumentException("Sample size must be >= 1.");
        }

        this.sampleSize = sampleSize;
    }

    /**
     * <p>Getter for the field <code>independenceTest</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.study.performance.ComparisonParameters.IndependenceTestType} object
     */
    public IndependenceTestType getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * <p>Setter for the field <code>independenceTest</code>.</p>
     *
     * @param independenceTest a {@link edu.cmu.tetrad.study.performance.ComparisonParameters.IndependenceTestType}
     *                         object
     */
    public void setIndependenceTest(IndependenceTestType independenceTest) {
        this.independenceTest = independenceTest;
    }

    /**
     * <p>Getter for the field <code>penaltyDiscount</code>.</p>
     *
     * @return a double
     */
    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    /**
     * <p>Setter for the field <code>penaltyDiscount</code>.</p>
     *
     * @param penaltyDiscount a double
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * <p>Getter for the field <code>alpha</code>.</p>
     *
     * @return a double
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * <p>Setter for the field <code>alpha</code>.</p>
     *
     * @param alpha a double
     */
    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("Alpha must be in [0, 1]");
        }

        this.alpha = alpha;
    }

    /**
     * <p>Getter for the field <code>score</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.ScoreType} object
     */
    public ScoreType getScore() {
        return this.score;
    }

    /**
     * <p>Setter for the field <code>score</code>.</p>
     *
     * @param score a {@link edu.cmu.tetrad.sem.ScoreType} object
     */
    public void setScore(ScoreType score) {
        if (score == null) {
            throw new NullPointerException("Score not provided.");
        }

        DataType dataType = null;

        if (score == ScoreType.SemBic) {
            dataType = DataType.Continuous;
        } else if (score == ScoreType.BDeu) {
            dataType = DataType.Discrete;
        }

        if (this.dataType != null && this.dataType != dataType) {
            throw new IllegalArgumentException("Data type of score conflicts with previous data type.");
        }

        this.score = score;
    }

    /**
     * <p>Getter for the field <code>algorithm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.study.performance.ComparisonParameters.Algorithm} object
     */
    public Algorithm getAlgorithm() {
        return this.algorithm;
    }

    /**
     * <p>Setter for the field <code>algorithm</code>.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.study.performance.ComparisonParameters.Algorithm} object
     */
    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;

        ResultType resultType = null;

        if (algorithm == Algorithm.PC) {
            resultType = ResultType.CPDAG;
        } else if (algorithm == Algorithm.CPC) {
            resultType = ResultType.CPDAG;
        } else if (algorithm == Algorithm.FGES) {
            resultType = ResultType.CPDAG;
        } else if (algorithm == Algorithm.FCI) {
            resultType = ResultType.PAG;
        } else if (algorithm == Algorithm.GFCI) {
            resultType = ResultType.PAG;
        } else if (algorithm == Algorithm.SVARFCI) {
            resultType = ResultType.PAG;
        } else {
            throw new IllegalArgumentException("Result type of algorithm not set.");
        }

        if (this.resultType != null && this.resultType != resultType) {
            throw new IllegalArgumentException("Result type of algorithm conflicts with previous result type.");
        }
    }

    /**
     * <p>Getter for the field <code>dataFile</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDataFile() {
        return this.dataFile;
    }

    /**
     * <p>Setter for the field <code>dataFile</code>.</p>
     *
     * @param dataFile a {@link java.lang.String} object
     */
    public void setDataFile(String dataFile) {
        this.dataFile = dataFile;
    }

    /**
     * <p>Getter for the field <code>graphFile</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getGraphFile() {
        return this.graphFile;
    }

    /**
     * <p>Setter for the field <code>graphFile</code>.</p>
     *
     * @param graphFile a {@link java.lang.String} object
     */
    public void setGraphFile(String graphFile) {
        this.graphFile = graphFile;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder b = new StringBuilder();

        if (this.dataType != null) {
            b.append("\nData Type = ").append(this.dataType);
        }

        if (this.resultType != null) {
            b.append("\nResult Type = ").append(this.resultType);
        }

        if (this.numVars != -1) {
            b.append("\nNum Vars = ").append(this.numVars);
        }

        if (this.numEdges != -1) {
            b.append("\nNum Edges = ").append(this.numEdges);
        }

        if (this.numEdges != -1) {
            b.append("\nSample Size = ").append(this.sampleSize);
        }

        if (this.independenceTest != null) {
            b.append("\nIndependence Test = ").append(this.independenceTest);
        }

        if (!Double.isNaN(this.alpha)) {
            b.append("\nAlpha = ").append(this.alpha);
        }

        if (this.score != null) {
            b.append("\nScore = ").append(this.score);
        }

        if (this.algorithm != null) {
            b.append("\nAlgorithm = ").append(this.algorithm);
        }


        if (this.oneEdgeFaithfulnessAssumed) {
            b.append("\nOne Edge Faithfulnes = true");
        }

        return b.toString();
    }

    /**
     * <p>Getter for the field <code>samplePrior</code>.</p>
     *
     * @return a double
     */
    public double getSamplePrior() {
        return this.samplePrior;
    }

    /**
     * <p>Setter for the field <code>samplePrior</code>.</p>
     *
     * @param samplePrior a double
     */
    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    /**
     * <p>Getter for the field <code>structurePrior</code>.</p>
     *
     * @return a double
     */
    public double getStructurePrior() {
        return this.structurePrior;
    }

    /**
     * <p>Setter for the field <code>structurePrior</code>.</p>
     *
     * @param structurePrior a double
     */
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * <p>isOneEdgeFaithfulnessAssumed.</p>
     *
     * @return a boolean
     */
    public boolean isOneEdgeFaithfulnessAssumed() {
        return this.oneEdgeFaithfulnessAssumed;
    }

    /**
     * <p>Setter for the field <code>oneEdgeFaithfulnessAssumed</code>.</p>
     *
     * @param oneEdgeFaithfulnessAssumed a boolean
     */
    public void setOneEdgeFaithfulnessAssumed(boolean oneEdgeFaithfulnessAssumed) {
        this.oneEdgeFaithfulnessAssumed = oneEdgeFaithfulnessAssumed;
    }

    /**
     * <p>isNoData.</p>
     *
     * @return a boolean
     */
    public boolean isNoData() {
        return this.noData;
    }

    /**
     * <p>Setter for the field <code>noData</code>.</p>
     *
     * @param noData a boolean
     */
    public void setNoData(boolean noData) {
        this.noData = noData;
    }

    /**
     * <p>isDataFromFile.</p>
     *
     * @return a boolean
     */
    public boolean isDataFromFile() {
        return this.dataFromFile;
    }

    /**
     * <p>Setter for the field <code>dataFromFile</code>.</p>
     *
     * @param dataFromFile a boolean
     */
    public void setDataFromFile(boolean dataFromFile) {
        this.dataFromFile = dataFromFile;
    }

    /**
     * <p>Getter for the field <code>graphNum</code>.</p>
     *
     * @return a int
     */
    public int getGraphNum() {
        return this.graphNum;
    }

    /**
     * <p>Setter for the field <code>graphNum</code>.</p>
     *
     * @param graphNum a int
     */
    public void setGraphNum(int graphNum) {
        this.graphNum = graphNum;
    }

    /**
     * <p>Getter for the field <code>trial</code>.</p>
     *
     * @return a int
     */
    public int getTrial() {
        return this.trial;
    }

    /**
     * <p>Setter for the field <code>trial</code>.</p>
     *
     * @param trial a int
     */
    public void setTrial(int trial) {
        this.trial = trial;
    }

    /**
     * An enumeration of the data types that can be used for structure learning.
     */
    public enum DataType {
        /**
         * Constant for continuous data.
         */
        Continuous,
        /**
         * Constant for discrete data.
         */
        Discrete
    }

    /**
     * An enumeration of the result types that can be used for structure learning.
     */
    public enum ResultType {
        /**
         * Constant for CPDAG result type.
         */
        CPDAG,
        /**
         * Constant for PAG result type.
         */
        PAG
    }

    /**
     * An enumeration of the independence test types that can be used for structure learning.
     */
    public enum IndependenceTestType {
        /**
         * Constant for the FisherZ independence test.
         */
        FisherZ,
        /**
         * Constant for the ChiSquare independence test.
         */
        ChiSquare
    }

    /**
     * An enumeration of the algorithms that can be used for structure learning.
     */
    public enum Algorithm {

        /**
         * Constant for the PC algorithm.
         */
        PC,

        /**
         * Constant for the CPC algorithm.
         */
        CPC,

        /**
         * Constant for the FGES algorithm.
         */
        FGES,

        /**
         * Constant for the FCI algorithm.
         */
        FCI,

        /**
         * Constant for the GFCI algorithm.
         */
        GFCI,

        /**
         * Constant for the SVARFCI algorithm.
         */
        SVARFCI
    }
}
