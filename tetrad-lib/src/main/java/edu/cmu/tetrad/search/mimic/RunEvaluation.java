package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

public class RunEvaluation {

    public static void main(String[] args) {
        Parameters parameters = new Parameters();

        parameters.set("mimicNumInputs", 20);
        parameters.set("mimicNumLatents", 6);
        parameters.set("mimicNumOutputs", 20);
        parameters.set("mimicSinglyConnected", true);

        parameters.set("mimicLatentEdgeProb", 0.25);
        parameters.set("mimicInputAttachProb", 0.35);
        parameters.set("mimicOutputAttachProb", 0.35);

        parameters.set("maxLatentSubsetSize", 3);

        parameters.set(Params.ALPHA, 0.01);
        parameters.set(Params.SAMPLE_SIZE, 5000);
        parameters.set(Params.COEF_LOW, 0.1);
        parameters.set(Params.COEF_HIGH, 1.2);
        parameters.set(Params.DEPTH, -1);
        parameters.set(Params.EFFECTIVE_SAMPLE_SIZE, -1);
        parameters.set(Params.VERBOSE, true);

        MimicBenchmark benchmark = new MimicBenchmark();
        MimicBenchmarkResult result = benchmark.runTrials(parameters, 50);

        MimicBenchmarkReport report = new MimicBenchmarkReport();
        System.out.println(report.createReport(result));
    }
}
