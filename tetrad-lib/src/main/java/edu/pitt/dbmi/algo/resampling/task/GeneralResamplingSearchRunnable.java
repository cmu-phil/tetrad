package edu.pitt.dbmi.algo.resampling.task;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingSearch;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Mar 19, 2017 9:45:44 PM
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 */
public class GeneralResamplingSearchRunnable implements Callable<Graph> {

    private final Parameters parameters;
    private final GeneralResamplingSearch resamplingAlgorithmSearch;
    private final boolean verbose;
    private DataModel dataModel;
    private List<DataModel> dataModels = new ArrayList<>();
    private Algorithm algorithm;
    private MultiDataSetAlgorithm multiDataSetAlgorithm;
    /**
     * An initial graph to start from.
     */
    private Graph externalGraph;

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

    private PrintStream out = System.out;

    public GeneralResamplingSearchRunnable(DataModel dataModel, Algorithm algorithm, Parameters parameters,
                                           GeneralResamplingSearch resamplingAlgorithmSearch, boolean verbose) {
        if (dataModel == null) throw new NullPointerException("Data model null.");
        if (algorithm == null) throw new NullPointerException("Algorithm null.");
        if (parameters == null) throw new NullPointerException("Parameters null.");
        if (resamplingAlgorithmSearch == null) throw new NullPointerException("Resampling algroithms search null.");

        this.dataModel = dataModel.copy();
        this.algorithm = algorithm;
        this.parameters = parameters;
        this.resamplingAlgorithmSearch = resamplingAlgorithmSearch;
        this.verbose = verbose;
    }

    public GeneralResamplingSearchRunnable(List<DataModel> dataModel, MultiDataSetAlgorithm algorithm, Parameters parameters,
                                           GeneralResamplingSearch resamplingAlgorithmSearch, boolean verbose) {
        if (dataModel == null) throw new NullPointerException("Data model null.");
        if (algorithm == null) throw new NullPointerException("Algorithm null.");
        if (parameters == null) throw new NullPointerException("Parameters null.");
        if (resamplingAlgorithmSearch == null) throw new NullPointerException("Resampling algroithms search null.");

        this.dataModels = dataModel;
        this.multiDataSetAlgorithm = algorithm;
        this.parameters = parameters;
        this.resamplingAlgorithmSearch = resamplingAlgorithmSearch;
        this.verbose = verbose;
    }

    /**
     * @return the background knowledge.
     */

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null)
            throw new NullPointerException();
        this.knowledge = knowledge;
    }

    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    @Override
    public Graph call() {
        long start;
        long stop;
        start = System.currentTimeMillis();

        if (this.verbose) {
            this.out.println("thread started ... ");
        }

        try {
            Graph graph;

            if (this.dataModel != null) {
                if (this.algorithm instanceof HasKnowledge) {
                    ((HasKnowledge) this.algorithm).setKnowledge(this.knowledge);
                    if (this.verbose) {
                        this.out.println("knowledge being set ... ");
                    }
                }

                graph = this.algorithm.search(this.dataModel, this.parameters);
            } else {
                if (this.multiDataSetAlgorithm instanceof HasKnowledge) {
                    ((HasKnowledge) this.multiDataSetAlgorithm).setKnowledge(this.knowledge);
                    if (this.verbose) {
                        this.out.println("knowledge being set ... ");
                    }
                }

                graph = this.multiDataSetAlgorithm.search(this.dataModels, this.parameters);
            }

            stop = System.currentTimeMillis();
            if (this.verbose) {
                this.out.println("processing time of resampling for a thread was: "
                        + (stop - start) / 1000.0 + " sec");
            }

            return graph;
        } catch (Exception e) {
            return null;
        }
    }
}
