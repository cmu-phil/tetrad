package edu.pitt.dbmi.algo.resampling.task;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingSearch;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Mar 19, 2017 9:45:44 PM
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * @version $Id: $Id
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
    private Knowledge knowledge = new Knowledge();

    private PrintStream out = System.out;
    private ScoreWrapper scoreWrapper = null;
    private IndependenceWrapper independenceWrapper = null;

    /**
     * <p>Constructor for GeneralResamplingSearchRunnable.</p>
     *
     * @param dataModel                 a {@link edu.cmu.tetrad.data.DataModel} object
     * @param algorithm                 a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     * @param parameters                a {@link edu.cmu.tetrad.util.Parameters} object
     * @param resamplingAlgorithmSearch a {@link edu.pitt.dbmi.algo.resampling.GeneralResamplingSearch} object
     * @param verbose                   a boolean
     */
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

    /**
     * <p>Constructor for GeneralResamplingSearchRunnable.</p>
     *
     * @param dataModel                 a {@link java.util.List} object
     * @param algorithm                 a {@link edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm} object
     * @param parameters                a {@link edu.cmu.tetrad.util.Parameters} object
     * @param resamplingAlgorithmSearch a {@link edu.pitt.dbmi.algo.resampling.GeneralResamplingSearch} object
     * @param verbose                   a boolean
     */
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
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return the background knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    /**
     * <p>Getter for the field <code>externalGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    /**
     * <p>Setter for the field <code>externalGraph</code>.</p>
     *
     * @param externalGraph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * <p>Getter for the field <code>out</code>.</p>
     *
     * @return the output stream that output (except for log output) should be sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent to. By detault System.out.
     *
     * @param out a {@link java.io.PrintStream} object
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph call() {
        long start;
        long stop;
        start = MillisecondTimes.timeMillis();

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

                if (scoreWrapper != null) {
                    this.multiDataSetAlgorithm.setScoreWrapper(this.scoreWrapper);
                }

                if (independenceWrapper != null) {
                    this.multiDataSetAlgorithm.setIndTestWrapper(this.independenceWrapper);
                }

                graph = this.multiDataSetAlgorithm.search(this.dataModels, this.parameters);
            }

            stop = MillisecondTimes.timeMillis();

            if (this.verbose) {
                this.out.println("processing time of resampling for a thread was: "
                        + (stop - start) / 1000.0 + " sec");
            }

            return graph;
        } catch (Exception e) {
            TetradLogger.getInstance().forceLogMessage("Exception in bootstrapping runnable: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * <p>Setter for the field <code>scoreWrapper</code>.</p>
     *
     * @param scoreWrapper a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public void setScoreWrapper(ScoreWrapper scoreWrapper) {
        this.scoreWrapper = scoreWrapper;
    }
}
