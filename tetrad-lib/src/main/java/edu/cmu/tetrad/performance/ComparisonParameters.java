package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.sem.ScoreType;

/**
 * Created by jdramsey on 3/24/16. Edited by dmalinsky 5/20/16.
 */
public class ComparisonParameters {
    private DataType dataType;
    private ResultType resultType;
    private int numVars = 100;
    private int numEdges = 100;
    private int sampleSize = 1000;
    private IndependenceTestType independenceTest;
    private double alpha = 0.001;
    private double penaltyDiscount = 4;
    private ScoreType score;
    private double samplePrior = 1;
    private double structurePrior = 1;
    private Algorithm algorithm;
    private String dataFile;
    private String graphFile;
    private boolean oneEdgeFaithfulnessAssumed;
    private boolean noData;
    private boolean dataFromFile;
    private int graphNum;
    private int trial;

    public ComparisonParameters() {

    }

    public ComparisonParameters(final ComparisonParameters params) {
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

    public void setDataType(final DataType dataType) {
        if (this.dataType != null && this.dataType != dataType) {
            throw new IllegalArgumentException("Data type conflicts with previous data type.");
        }

        this.dataType = dataType;
    }

    public void setResultType(final ResultType resultType) {
        if (this.resultType != null && this.resultType != resultType) {
            throw new IllegalArgumentException("Result type conflicts with previous result type.");
        }

        this.resultType = resultType;
    }

    public void setNumVars(final int numVars) {
        if (numVars < 1) {
            throw new IllegalArgumentException("Number of variables must be >= 1.");
        }

        this.numVars = numVars;
    }

    public void setNumEdges(final int numEdges) {
        if (numEdges < 1) {
            throw new IllegalArgumentException("Number of edges must be >= 1.");
        }

        this.numEdges = numEdges;
    }

    public void setSampleSize(final int sampleSize) {
        if (sampleSize < 1) {
            throw new IllegalArgumentException("Sample size must be >= 1.");
        }

        this.sampleSize = sampleSize;
    }

    public void setIndependenceTest(final IndependenceTestType independenceTest) {
        this.independenceTest = independenceTest;
    }

    public void setAlpha(final double alpha) {
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("Alpha must be in [0, 1]");
        }

        this.alpha = alpha;
    }


    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setScore(final ScoreType score) {
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

    public void setAlgorithm(final Algorithm algorithm) {
        this.algorithm = algorithm;

        ResultType resultType = null;

        if (algorithm == Algorithm.PC) {
            resultType = ResultType.CPDAG;
        } else if (algorithm == Algorithm.CPC) {
            resultType = ResultType.CPDAG;
        } else if (algorithm == Algorithm.FGES) {
            resultType = ResultType.CPDAG;
        } else if (algorithm == Algorithm.FGES2) {
            resultType = ResultType.CPDAG;
        } else if (algorithm == Algorithm.PCLocal) {
            resultType = ResultType.CPDAG;
        } else if (algorithm == Algorithm.PCStableMax) {
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

    public DataType getDataType() {
        return this.dataType;
    }

    public ResultType getResultType() {
        return this.resultType;
    }

    public int getNumVars() {
        return this.numVars;
    }

    public int getNumEdges() {
        return this.numEdges;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    public IndependenceTestType getIndependenceTest() {
        return this.independenceTest;
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public double getAlpha() {
        return this.alpha;
    }

    public ScoreType getScore() {
        return this.score;
    }

    public Algorithm getAlgorithm() {
        return this.algorithm;
    }

    public String getDataFile() {
        return this.dataFile;
    }

    public void setDataFile(final String dataFile) {
        this.dataFile = dataFile;
    }

    public String getGraphFile() {
        return this.graphFile;
    }

    public String toString() {
        final StringBuilder b = new StringBuilder();

        if (this.dataType != null) {
            b.append("\nData Type = " + this.dataType);
        }

        if (this.resultType != null) {
            b.append("\nResult Type = " + this.resultType);
        }

        if (this.numVars != -1) {
            b.append("\nNum Vars = " + this.numVars);
        }

        if (this.numEdges != -1) {
            b.append("\nNum Edges = " + this.numEdges);
        }

        if (this.numEdges != -1) {
            b.append("\nSample Size = " + this.sampleSize);
        }

        if (this.independenceTest != null) {
            b.append("\nIndependence Test = " + this.independenceTest);
        }

        if (!Double.isNaN(this.alpha)) {
            b.append("\nAlpha = " + this.alpha);
        }

        if (this.score != null) {
            b.append("\nScore = " + this.score);
        }

        if (this.algorithm != null) {
            b.append("\nAlgorithm = " + this.algorithm);
        }


//        if (dataFile != null) {
//            b.append("\nData File = " + dataFile);
//        }
//
//
//        if (graphFile != null) {
//            b.append("\nGraph File = " + graphFile);
//        }

        if (this.oneEdgeFaithfulnessAssumed) {
            b.append("\nOne Edge Faithfulnes = true");
        }

        return b.toString();
    }

    public double getSamplePrior() {
        return this.samplePrior;
    }

    public void setSamplePrior(final double samplePrior) {
        this.samplePrior = samplePrior;
    }

    public double getStructurePrior() {
        return this.structurePrior;
    }

    public void setStructurePrior(final double structurePrior) {
        this.structurePrior = structurePrior;
    }

    public void setOneEdgeFaithfulnessAssumed(final boolean oneEdgeFaithfulnessAssumed) {
        this.oneEdgeFaithfulnessAssumed = oneEdgeFaithfulnessAssumed;
    }

    public boolean isOneEdgeFaithfulnessAssumed() {
        return this.oneEdgeFaithfulnessAssumed;
    }


    public void setNoData(final boolean noData) {
        this.noData = noData;
    }

    public boolean isNoData() {
        return this.noData;
    }

    public boolean isDataFromFile() {
        return this.dataFromFile;
    }

    public void setDataFromFile(final boolean dataFromFile) {
        this.dataFromFile = dataFromFile;
    }

    public void setGraphFile(final String graphFile) {
        this.graphFile = graphFile;
    }

    public void setGraphNum(final int graphNum) {
        this.graphNum = graphNum;
    }

    public int getGraphNum() {
        return this.graphNum;
    }

    public void setTrial(final int trial) {
        this.trial = trial;
    }

    public int getTrial() {
        return this.trial;
    }

    public enum DataType {Continuous, Discrete}

    public enum ResultType {CPDAG, PAG}

    public enum IndependenceTestType {FisherZ, ChiSquare}

    public enum Algorithm {PC, CPC, FGES, FGES2, PCLocal, PCStableMax, FCI, GFCI, SVARFCI}
}
