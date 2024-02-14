package edu.pitt.dbmi.algo.resampling;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.resampling.task.GeneralResamplingSearchRunnable;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * A class for performing a general resampling search.
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @author josephramsey Cleanup.
 * @version $Id: $Id
 */
public class GeneralResamplingSearch {
    /**
     * The number of resamples to take.
     */
    private int numberOfResamples = 1;
    /**
     * The percentage of the resample size.
     */
    private double percentResampleSize = 100.;
    /**
     * The number of threads to use for bootstrapping.
     */
    private int numBootstrapThreads = 1;
    /**
     * The resampling with replacement.
     */
    private boolean resamplingWithReplacement = true;
    /**
     * Whether to add the original dataset to the list of sampled datasets as an additional sample.
     */
    private boolean addOriginalDataset;
    /**
     * The parameters for the search.
     */
    private Parameters parameters;
    /**
     * The list of returns graphs.
     */
    private final List<Graph> graphs = Collections.synchronizedList(new ArrayList<>());
    /**
     * The pool of threads to use for bootstrapping.
     */
    private final ForkJoinPool pool;
    /**
     * The algorithm to use for the search, for single-data set algorithms.
     */
    private Algorithm algorithm;
    /**
     * The data set, for single-data set algorithms.
     */
    private DataSet data;
    /**
     * The data sets, for multi-data set algorithms.
     */
    private List<DataSet> dataSets;
    /**
     * The multi data set algorithm to use for the search, for multi-data set algorithms.
     */
    private MultiDataSetAlgorithm multiDataSetAlgorithm;
    /**
     * Specification of forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Whether to print out verbose output.
     */
    private boolean verbose;
    /**
     * The output stream that output (except for log output) should be sent to.
     */
    private PrintStream out = System.out;
    /**
     * The number of algorithms runs that did not return a graph.
     */
    private int numRunsReturningNullGraph = 0;
    /**
     * The score wrapper to pass to multi-data set algorithms.
     */
    private ScoreWrapper scoreWrapper;
    /**
     * The independence test wrapper to pass to multi-data set algorithms.
     */
    private IndependenceWrapper independenceWrapper;

    /**
     * Constructor for a single data set algorithm.
     *
     * @param data the data set.
     */
    public GeneralResamplingSearch(DataSet data, Algorithm algorithm) {
        this.data = data;
        this.algorithm = algorithm;
        this.pool = new ForkJoinPool(numBootstrapThreads);
    }

    /**
     * Constructor for a multi data set algorithm.
     *
     * @param dataSets the data sets.
     */
    public GeneralResamplingSearch(List<DataSet> dataSets, MultiDataSetAlgorithm algorithm) {
        this.dataSets = dataSets;
        this.multiDataSetAlgorithm = algorithm;
        this.pool = new ForkJoinPool(numBootstrapThreads);
    }

    /**
     * Sets the algorithm, for single-data set algorithms.
     *
     * @param algorithm the algorithm.
     */
    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
        this.multiDataSetAlgorithm = null;
    }

    /**
     * Sets the multi data set algorithm, for multi-data set algorithms.
     *
     * @param multiDataSetAlgorithm the multi data set algorithm.
     */
    public void setMultiDataSetAlgorithm(MultiDataSetAlgorithm multiDataSetAlgorithm) {
        this.multiDataSetAlgorithm = multiDataSetAlgorithm;
        this.algorithm = null;
    }

    /**
     * Sets the percentage of the resample size for each resampling.
     *
     * @param percentResampleSize the resampling size, from 0 to 100.
     */
    public void setPercentResampleSize(double percentResampleSize) {
        this.percentResampleSize = percentResampleSize;
    }

    /**
     * Sets whether to resample with replacement.
     *
     * @param resamplingWithReplacement whether to resample with replacement.
     */
    public void setResamplingWithReplacement(boolean resamplingWithReplacement) {
        this.resamplingWithReplacement = resamplingWithReplacement;
    }

    /**
     * Sets whether to add the original dataset as an additional sample.
     *
     * @param addOriginalDataset whether to add the original dataset as an additional sample.
     */
    public void setAddOriginalDataset(boolean addOriginalDataset) {
        this.addOriginalDataset = addOriginalDataset;
    }

    /**
     * Sets whether to print out verbose output.
     *
     * @param verbose whether to print out verbose output.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the data set, for single-data set algorithms.
     *
     * @param data the data set.
     */
    public void setData(DataSet data) {
        this.data = data;
    }

    /**
     * Sets the background knowledge to be used for the search.
     *
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the output stream that output (except for log output) should be sent to. By default, System.out.
     *
     * @param out the output stream.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets the parameters for the search.
     *
     * @param parameters the parameters.
     */
    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Performs the search and returns the list of graphs.
     *
     * @return the list of graphs. Some of these may be null if the search algorithm did not return a graph.
     */
    public List<Graph> search() {
        this.graphs.clear();

        // We temporarily set NUMBER_RESAMPLING to 0 to avoid the algorithm from running the resampling
        // when called from the bootstrap search. We will set it back to the original value after the
        // bootstrap search is done. Note that using this method only one bootstrap search can be run
        // at a time, though this bootstrapping search can be parallelized.
        parameters.set(Params.NUMBER_RESAMPLING, 0);

        List<Callable<Graph>> tasks = new ArrayList<>();

        // Running in the sequential form
        if (this.verbose) {
            this.out.println("Running Resamplings in Sequential Mode, numberResampling = " + this.numberOfResamples);
        }

        if (this.data != null) {
            Long seed = (parameters == null || parameters.get(Params.SEED) == null) ? null : (Long) parameters.get(Params.SEED);
            RandomGenerator randomGenerator = (seed == null || seed < 0) ? null : new SynchronizedRandomGenerator(new Well44497b(seed));

            for (int i = 0; i < this.numberOfResamples; i++) {
                DataSet dataSet;
                int sampleSize = (int) (data.getNumRows() * this.percentResampleSize / 100.0);

                if (this.resamplingWithReplacement) {
                    if ((randomGenerator == null)) {
                        dataSet = DataTransforms.getBootstrapSample(data, sampleSize);
                    } else {
                        dataSet = DataTransforms.getBootstrapSample(data, sampleSize, randomGenerator);
                    }
                } else {
                    if ((randomGenerator == null)) {
                        dataSet = DataTransforms.getResamplingDataset(data, sampleSize);
                    } else {
                        dataSet = DataTransforms.getResamplingDataset(data, sampleSize, randomGenerator);
                    }
                }

                dataSet.setKnowledge(data.getKnowledge());

                GeneralResamplingSearchRunnable task = new GeneralResamplingSearchRunnable(dataSet, this.algorithm, this.parameters);
                task.setKnowledge(this.knowledge);
                task.setScoreWrapper(scoreWrapper);
                task.setVerbose(verbose);

                tasks.add(task);
            }

            if (addOriginalDataset) {
                GeneralResamplingSearchRunnable task = new GeneralResamplingSearchRunnable(data.copy(),
                        this.algorithm, this.parameters
                );
                task.setKnowledge(this.knowledge);
                tasks.add(task);
                task.setScoreWrapper(scoreWrapper);
                task.setIndependenceWrapper(independenceWrapper);
                task.setVerbose(verbose);
            }
        } else {

            // We use the stored value for the number of resamples, since its value in the parameter object
            // has been temporarily set to 0 to avoid the algorithm from running the resampling when called
            // from the bootstrap search.
            for (int i = 0; i < this.numberOfResamples; i++) {
                List<DataModel> dataModels = new ArrayList<>();

                for (DataSet data : this.dataSets) {

                    if (this.resamplingWithReplacement) {
                        int sampleSize = (int) (data.getNumRows() * this.percentResampleSize / 100.0);
                        DataSet bootstrapSample = DataTransforms.getBootstrapSample(data, sampleSize);
                        bootstrapSample.setKnowledge(data.getKnowledge());
                        dataModels.add(bootstrapSample);
                    } else {
                        int sampleSize = (int) (data.getNumRows() * this.percentResampleSize / 100.0);
                        DataSet resamplingDataset = DataTransforms.getResamplingDataset(data, sampleSize);
                        resamplingDataset.setKnowledge(data.getKnowledge());
                        dataModels.add(resamplingDataset);
                    }
                }

                GeneralResamplingSearchRunnable task = new GeneralResamplingSearchRunnable(dataModels,
                        this.multiDataSetAlgorithm, this.parameters
                );
                task.setKnowledge(dataModels.get(0).getKnowledge());
                task.setScoreWrapper(scoreWrapper);
                task.setVerbose(verbose);

                tasks.add(task);
            }
        }

        int numNoGraph = 0;
        List<Future<Graph>> futures = this.pool.invokeAll(tasks);

        for (Future<Graph> future : futures) {
            try {
                Graph graph = future.get();

                if (graph == null) {
                    numRunsReturningNullGraph++;
                } else {
                    this.graphs.add(graph);
                }
            } catch (InterruptedException | ExecutionException e) {
                TetradLogger.getInstance().forceLogMessage("Skipping a result because of an error in GeneralResamplingSearch: "
                        + e.getMessage());
            }
        }

        this.numRunsReturningNullGraph = numNoGraph;

        // Set the NUMBER_RESAMPLING back to the original value, since the bootstrap search is now done.
        parameters.set(Params.NUMBER_RESAMPLING, numberOfResamples);

        return this.graphs;
    }

    /**
     * Returns the number of algorithm runs that did not return a graph.
     *
     * @return the number of algorithms runs that did not return a graph.
     */
    public int getNumRunsReturningNullGraph() {
        return numRunsReturningNullGraph;
    }

    /**
     * Sets the score wrapper to pass to multi-data set algorithms.
     *
     * @param scoreWrapper the score wrapper.
     */
    public void setScoreWrapper(ScoreWrapper scoreWrapper) {
        this.scoreWrapper = scoreWrapper;
    }

    /**
     * Sets the independence test wrapper to pass to multi-data set algorithms.
     *
     * @param independenceWrapper the independence test wrapper.
     */
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.independenceWrapper = independenceWrapper;
    }

    /**
     * Sets the number of threads to use for bootstrapping. Must be at least one. Note that this is the number of
     * threads used for the bootstrapping itself, not the number of threads used for each search; the latter is
     * determined by the individual search algorithm.
     *
     * @param bootstrappingNumThreads the number of threads to use for bootstrapping.
     */
    public void setBootstrappingNumThreads(int bootstrappingNumThreads) {
        if (bootstrappingNumThreads < 1)
            throw new IllegalArgumentException("Number of bootstrap threads must be at least 1");
        this.numBootstrapThreads = bootstrappingNumThreads;
    }

    /**
     * Sets the number of resamples to take. Must be at least one. Note that in the interface, the number of resamples
     * can be zero; this zero value indicates that bootstrapping should not be performed, so a GeneralResamplingSearch
     * should not be constructed in this case.
     *
     * @param numberOfResamples the number of resamples to take.
     */
    public void setNumberOfResamples(int numberOfResamples) {
        if (numberOfResamples < 1)
            throw new IllegalArgumentException("Number of resamples must be at least 1");
        this.numberOfResamples = numberOfResamples;
    }
}
