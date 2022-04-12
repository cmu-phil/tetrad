package edu.pitt.dbmi.algo.resampling;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.task.GeneralResamplingSearchRunnable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * Sep 7, 2018 1:38:50 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 */
public class GeneralResamplingSearch {

    private final int numberResampling;
    private final List<Graph> graphs = Collections.synchronizedList(new ArrayList<>());
    private final ForkJoinPool pool;
    private Algorithm algorithm;
    private MultiDataSetAlgorithm multiDataSetAlgorithm;
    private double percentResampleSize = 100;
    private boolean resamplingWithReplacement = true;
    private boolean runParallel;
    private boolean addOriginalDataset;
    private boolean verbose;
    private DataSet data;

    private List<DataSet> dataSets;

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

    private PrintStream out = System.out;

    private Parameters parameters;

    /**
     * An initial graph to start from.
     */
    private Graph externalGraph;
    private int numNograph = 0;

    public GeneralResamplingSearch(DataSet data, int numberResampling) {
        this.data = data;
        this.pool = ForkJoinPool.commonPool();
        this.numberResampling = numberResampling;
    }

    public GeneralResamplingSearch(List<DataSet> dataSets, int numberResampling) {
        this.dataSets = dataSets;
        this.pool = ForkJoinPool.commonPool();
        this.numberResampling = numberResampling;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
        this.multiDataSetAlgorithm = null;
    }

    public void setMultiDataSetAlgorithm(MultiDataSetAlgorithm multiDataSetAlgorithm) {
        this.multiDataSetAlgorithm = multiDataSetAlgorithm;
        this.algorithm = null;
    }

    public void setPercentResampleSize(double percentResampleSize) {
        this.percentResampleSize = percentResampleSize;
    }

    public void setResamplingWithReplacement(boolean resamplingWithReplacement) {
        this.resamplingWithReplacement = resamplingWithReplacement;
    }

    public void setRunParallel(boolean runParallel) {
        this.runParallel = runParallel;
    }

    public void setAddOriginalDataset(boolean addOriginalDataset) {
        this.addOriginalDataset = addOriginalDataset;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setData(DataSet data) {
        this.data = data;
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

    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * @return the output stream that output (except for log output) should be sent
     * to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent to.
     * By default System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public List<Graph> search() {

        this.graphs.clear();
        this.parameters.set("numberResampling", 0); // This needs to be set to zero to not loop indefinitely

        List<Callable<Graph>> tasks = new ArrayList<>();

        // Running in the sequential form
        if (this.verbose) {
            this.out.println("Running Resamplings in Sequential Mode, numberResampling = " + this.numberResampling);
        }

        if (this.data != null) {
            for (int i1 = 0; i1 < this.numberResampling; i1++) {
                DataSet dataSet;

                if (this.resamplingWithReplacement) {
                    dataSet = DataUtils.getBootstrapSample(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                } else {
                    dataSet = DataUtils.getResamplingDataset(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                }

                Callable<Graph> task = new GeneralResamplingSearchRunnable(dataSet, this.algorithm, this.parameters, this, this.verbose);
                tasks.add(task);
            }

            if (addOriginalDataset) {
                GeneralResamplingSearchRunnable task = new GeneralResamplingSearchRunnable(data,
                        this.algorithm, this.parameters, this,
                        this.verbose);
                task.setExternalGraph(this.externalGraph);
                task.setKnowledge(this.knowledge);
                tasks.add(task);
            }
        } else {
            for (DataSet data : this.dataSets) {
                for (int i1 = 0; i1 < this.numberResampling; i1++) {
                    DataModel dataModel;

                    if (this.resamplingWithReplacement) {
                        dataModel = DataUtils.getBootstrapSample(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                    } else {
                        dataModel = DataUtils.getResamplingDataset(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                    }

                    GeneralResamplingSearchRunnable task = new GeneralResamplingSearchRunnable(dataModel,
                            this.multiDataSetAlgorithm, this.parameters, this,
                            this.verbose);
                    task.setExternalGraph(this.externalGraph);
                    task.setKnowledge(this.knowledge);

                    tasks.add(task);
                }
            }
        }

        int numNoGraph = 0;

        if (this.runParallel) {
            List<Future<Graph>> futures = this.pool.invokeAll(tasks);
            for (Future<Graph> future : futures) {
                Graph graph = null;
                try {
                    graph = future.get();

                    if (graph == null) {
                        numNograph++;
                    } else {
                        this.graphs.add(graph);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        } else {
            for (Callable<Graph> callable : tasks) {
                try {
                    Graph graph = callable.call();

                    if (graph == null) {
                        numNoGraph++;
                    } else {
                        this.graphs.add(graph);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        this.parameters.set("numberResampling", this.numberResampling);
        this.numNograph = numNoGraph;

        return this.graphs;
    }

    public int getNumNograph() {
        return numNograph;
    }
}
