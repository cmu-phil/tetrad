package edu.cmu.tetrad.performance;

/**
 * Created by jdramsey on 3/24/16.
 */
public class ComparisonParameters {
    private DataType dataType = null;
    private ResultType resultType = null;
    private int numVars = -1;
    private int numEdges = -1;
    private int sampleSize = -1;
    private IndependenceTestType independenceTest = null;
    private double alpha = Double.NaN;
    private double penaltyDiscount = Double.NaN;
    private ScoreType score = null;
    private Algorithm algorithm = null;
    private String dataFile = null;
    private String graphFile;

    public ComparisonParameters() {

    }

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
    }

    public void setDataType(DataType dataType) {
        if (this.dataType != null && this.dataType != dataType) {
            throw new IllegalArgumentException("Data type conflicts with previous data type.");
        }

        this.dataType = dataType;
    }

    public void setResultType(ResultType resultType) {
        if (this.resultType != null && this.resultType != resultType) {
            throw new IllegalArgumentException("Result type conflicts with previous result type.");
        }

        this.resultType = resultType;
    }

    public void setNumVars(int numVars) {
        if (numVars < 1) {
            throw new IllegalArgumentException("Number of variables must be >= 1.");
        }

        this.numVars = numVars;
    }

    public void setNumEdges(int numEdges) {
        if (numEdges < 1) {
            throw new IllegalArgumentException("Number of edges must be >= 1.");
        }

        this.numEdges = numEdges;
    }

    public void setSampleSize(int sampleSize) {
        if (sampleSize < 1) {
            throw new IllegalArgumentException("Sample size must be >= 1.");
        }

        this.sampleSize = sampleSize;
    }

    public void setIndependenceTest(IndependenceTestType independenceTest) {
        this.independenceTest = independenceTest;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("Alpha must be in [0, 1]");
        }

        this.alpha = alpha;
    }


    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setScore(ScoreType score) {
        if (score == null) {
            throw new NullPointerException("Score not provided.");
        }

        DataType dataType = null;

        if (score == ScoreType.SemBic) {
            dataType = DataType.Continuous;
        } else if (score == ScoreType.Bdeu) {
            dataType = DataType.Discrete;
        }

        if (this.dataType != null && this.dataType != dataType) {
            throw new IllegalArgumentException("Data type of score conflicts with previous data type.");
        }

        this.score = score;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;

        ResultType resultType = null;

        if (algorithm == Algorithm.Pc) {
            resultType = ResultType.Pattern;
        } else if (algorithm == Algorithm.Cpc) {
            resultType = ResultType.Pattern;
        } else if (algorithm == Algorithm.Fgs) {
            resultType = ResultType.Pattern;
        } else if (algorithm == Algorithm.PcLocal) {
            resultType = ResultType.Pattern;
        } else if (algorithm == Algorithm.PcMax) {
            resultType = ResultType.Pattern;
        } else if (algorithm == Algorithm.Fci) {
            resultType = ResultType.Pag;
        } else if (algorithm == Algorithm.Gfci) {
            resultType = ResultType.Pag;
        } else {
            throw new IllegalArgumentException("Result type of algorithm not set.");
        }

        if (this.resultType != null && this.resultType != resultType) {
            throw new IllegalArgumentException("Result type of algorithm conflicts with previous result type.");
        }
    }

    public DataType getDataType() {
        return dataType;
    }

    public ResultType getResultType() {
        return resultType;
    }

    public int getNumVars() {
        return numVars;
    }

    public int getNumEdges() {
        return numEdges;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public IndependenceTestType getIndependenceTest() {
        return independenceTest;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public double getAlpha() {
        return alpha;
    }

    public ScoreType getScore() {
        return score;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public String getDataFile() {
        return dataFile;
    }

    public void setDataFile(String dataFile) {
        this.dataFile = dataFile;
    }

    public String getGraphFile() {
        return graphFile;
    }

    public String toString() {
        StringBuilder b = new StringBuilder();

        if (dataType != null) {
            b.append("\nData Type = " + dataType);
        }

        if (resultType != null) {
            b.append("\nResult Type = " + resultType);
        }

        if (numVars != -1) {
            b.append("\nNum Vars = " + numVars);
        }

        if (numEdges != -1) {
            b.append("\nNum Edges = " + numEdges);
        }

        if (numEdges != -1) {
            b.append("\nSample Size = " + sampleSize);
        }

        if (independenceTest != null) {
            b.append("\nIndependence Test = " + independenceTest);
        }

        if (!Double.isNaN(alpha)) {
            b.append("\nAlpha = " + alpha);
        }

        if (score != null) {
            b.append("\nScore = " + score);
        }

        if (algorithm != null) {
            b.append("\nAlgorithm = " + algorithm);
        }


        if (dataFile != null) {
            b.append("\nData File = " + dataFile);
        }


        if (graphFile != null) {
            b.append("\nGraph File = " + graphFile);
        }

        return b.toString();
    }


    public enum DataType {Continuous, Discrete}
    public enum ResultType {Pattern, Pag}
    public enum IndependenceTestType {FisherZ, ChiSquare, }
    public enum ScoreType {SemBic, Bdeu}
    public enum Algorithm {Pc, Cpc, Fgs, PcLocal, PcMax, Fci, Gfci}
}
