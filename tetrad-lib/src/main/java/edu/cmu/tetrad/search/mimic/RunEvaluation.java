package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * The RunEvaluation class is responsible for configuring parameters, executing
 * mimic benchmark trials, and generating a report based on the results. This
 * entry-point class utilizes the MimicBenchmark framework for evaluating model
 * performance under specified conditions.
 */
public class RunEvaluation {

    /**
     * Default constructor for the RunEvaluation class.
     *
     * This constructor initializes an instance of the RunEvaluation class, which
     * is responsible for configuring parameters, executing mimic benchmark trials,
     * and generating reports. The class leverages the MimicBenchmark framework to
     * evaluate model performance under specified conditions.
     */
    public RunEvaluation() {}

    /**
     * The main method serves as the entry point for the application. It
     * initializes the benchmark parameters, executes the trials, and generates
     * a report based on the benchmark results.
     *
     * @param args command-line arguments passed to the program. These arguments
     *             are unused in this implementation.
     */
    public static void main(String[] args) {
        Parameters parameters = new Parameters();

        parameters.set("mimicNumInputs", 12);
        parameters.set("mimicNumLatents", 4);
        parameters.set("mimicNumOutputs", 12);
        parameters.set("mimicSinglyConnected", true);
        parameters.set("mimicLatentEdgeProb", 0.3);
        parameters.set("mimicInputAttachProb", 0.35);
        parameters.set("mimicOutputAttachProb", 0.35);

        parameters.set("maxLatentSubsetSize", 4);

        parameters.set(Params.ALPHA, 0.01);
        parameters.set(Params.PENALTY_DISCOUNT, 1.0);
        parameters.set(Params.SAMPLE_SIZE, 5000);
        parameters.set(Params.COEF_LOW, 0.1);
        parameters.set(Params.COEF_HIGH, 1.0);
        parameters.set(Params.COEF_SYMMETRIC, false);
        parameters.set(Params.DEPTH, -1);
        parameters.set(Params.EFFECTIVE_SAMPLE_SIZE, -1);
        parameters.set(Params.VERBOSE, false);

        MimicBenchmark benchmark = new MimicBenchmark();
        MimicBenchmarkResult result = benchmark.runTrials(parameters, 200);

        MimicBenchmarkReport report = new MimicBenchmarkReport();
        System.out.println(report.createReport(result, parameters));

//        MimicBenchmarkReportTdf report = new MimicBenchmarkReportTdf();
//        System.out.println(report.createReport(result, parameters));
    }
}
