///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Benchmark harness for MIMIC-style algorithms.
 *
 * <p>This class generates random MIMIC models, simulates measured data from them using
 * a SEM parameterization, runs a list of MIMIC-style algorithms, and evaluates their
 * estimated graphs against the true latent structure.</p>
 *
 * @author josephramsey
 */
public final class MimicBenchmark {

    /**
     * Generator for random MIMIC models.
     */
    private final RandomMimicGraphGenerator generator;

    /**
     * Evaluator for estimated graphs.
     */
    private final MimicEvaluator evaluator;

    /**
     * Evaluator for latent-latent edge adequacy.
     */
    private final LatentLatentEvaluator latentLatentEvaluator;

    /**
     * Search runners to benchmark.
     */
    private final List<MimicSearchRunner> runners;

    /**
     * Constructs a benchmark with the default generator, evaluator, and the three current
     * MIMIC-style runners.
     */
    public MimicBenchmark() {
        this.generator = new RandomMimicGraphGenerator();
        this.evaluator = new MimicEvaluator();
        this.latentLatentEvaluator = new LatentLatentEvaluator();
        this.runners = new ArrayList<>();

//        this.runners.add(new DmMergeRunner());
//        this.runners.add(new DmMgRunner());
//        this.runners.add(new DmMgBossRunner());
//        this.runners.add(new DmBossRobustRunner());
        this.runners.add(new TrekMimicRunner());
        this.runners.add(new BossTrekMimicRunner());
//        this.runners.add(new BossTrekMimic2Runner());
    }

    /**
     * Constructs a benchmark with the supplied generator, evaluator, and runners.
     *
     * @param generator the generator
     * @param evaluator the evaluator
     * @param runners the search runners
     */
    public MimicBenchmark(RandomMimicGraphGenerator generator,
                          MimicEvaluator evaluator,
                          List<MimicSearchRunner> runners) {
        if (generator == null || evaluator == null || runners == null) {
            throw new NullPointerException("Generator, evaluator, and runners must not be null.");
        }

        this.generator = generator;
        this.evaluator = evaluator;
        this.latentLatentEvaluator = new LatentLatentEvaluator();
        this.runners = new ArrayList<>(runners);
    }

    /**
     * Runs one benchmark trial.
     *
     * @param parameters the benchmark parameters
     * @return the trial result
     */
    public MimicTrialResult runTrial(Parameters parameters) {
        MimicModel trueModel = this.generator.generate(parameters);
        DataSet measuredData = simulateMeasuredData(trueModel, parameters);
        Knowledge knowledge = trueModel.getTierKnowledge();

        Map<String, Graph> estimatedGraphs = new LinkedHashMap<>();
        Map<String, MimicEvaluation> evaluations = new LinkedHashMap<>();
        Map<String, LatentLatentEvaluator.Report> latentLatentReports = new LinkedHashMap<>();

        for (MimicSearchRunner runner : this.runners) {
            List<Node> inputs = trueModel.getInputs();
            List<Node> outputs = trueModel.getOutputs();

            Graph estimated = runner.run(measuredData, knowledge, inputs, outputs, parameters);
            MimicEvaluation evaluation = this.evaluator.evaluate(trueModel, estimated);
            LatentLatentEvaluator.Report latentLatentReport =
                    this.latentLatentEvaluator.evaluate(trueModel.getGraph(), estimated);

            estimatedGraphs.put(runner.getName(), estimated);
            evaluations.put(runner.getName(), evaluation);
            latentLatentReports.put(runner.getName(), latentLatentReport);
        }

        return new MimicTrialResult(
                trueModel,
                measuredData,
                knowledge,
                estimatedGraphs,
                evaluations,
                latentLatentReports
        );
    }

    /**
     * Runs multiple benchmark trials.
     *
     * @param parameters the benchmark parameters
     * @param numTrials the number of trials
     * @return the benchmark result
     */
    public MimicBenchmarkResult runTrials(Parameters parameters, int numTrials) {
        if (numTrials < 1) {
            throw new IllegalArgumentException("Number of trials must be at least 1.");
        }

        List<MimicTrialResult> trials = new ArrayList<>();

        for (int i = 0; i < numTrials; i++) {
            trials.add(runTrial(parameters));
        }

        return new MimicBenchmarkResult(trials);
    }

    /**
     * Simulates measured data from the supplied true model using a SEM parameterization.
     *
     * <p>The model graph may contain latents, but the returned data set is restricted to the
     * measured input and output variables only.</p>
     *
     * @param trueModel the true MIMIC model
     * @param parameters the simulation parameters
     * @return the measured data
     */
    private DataSet simulateMeasuredData(MimicModel trueModel, Parameters parameters) {
        Graph graph = trueModel.getGraph();
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm, parameters);

        int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);
        return im.simulateData(sampleSize, false);
    }
}