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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sep 7, 2018 1:38:50 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class GeneralResamplingSearch {

    private Algorithm algorithm;

    private MultiDataSetAlgorithm multiDataSetAlgorithm;

    private double percentResampleSize = 100;

    private boolean resamplingWithReplacement = true;

    private final int numberResampling;

    private boolean runParallel;

    private boolean addOriginalDataset;

    private boolean verbose;

    private final List<Graph> PAGs = Collections.synchronizedList(new ArrayList<>());

    private final ExecutorService pool;

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

    public GeneralResamplingSearch(DataSet data, int numberResampling) {
        this.data = data;
        this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.numberResampling = numberResampling;
    }

    public GeneralResamplingSearch(List<DataSet> dataSets, int numberResampling) {
        this.dataSets = dataSets;
        this.pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.numberResampling = numberResampling;
    }

    public void addPAG(Graph pag) {
        this.PAGs.add(pag);
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

    public void setDataSets(List<DataSet> dataSets) {
        this.dataSets = dataSets;
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
     * Sets the output stream that output (except for log output) should be sent to.
     * By detault System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * @return the output stream that output (except for log output) should be sent
     *         to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public List<Graph> search() {

        this.PAGs.clear();
        this.parameters.set("numberResampling", 0); // This needs to be set to zero to not loop indefinitely

        if (!this.runParallel) {
            // Running in the sequential form
            if (this.verbose) {
                this.out.println("Running Resamplings in Sequential Mode, numberResampling = " + this.numberResampling);
            }
            for (int i1 = 0; i1 < this.numberResampling; i1++) {
                GeneralResamplingSearchRunnable task;

                // Bootstrapping
                if (this.resamplingWithReplacement) {
                    if (this.data != null) {
                        DataSet dataSet = DataUtils.getBootstrapSample(this.data, (int) (this.data.getNumRows() * this.percentResampleSize / 100.0));
                        task = new GeneralResamplingSearchRunnable(dataSet, this.algorithm, this.parameters, this, this.verbose);
                    } else {
                        List<DataModel> dataModels = new ArrayList<>();
                        for (DataSet data : this.dataSets) {
                            DataSet dataSet = DataUtils.getBootstrapSample(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                            dataModels.add(dataSet);
                        }
                        task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                                this.verbose);
                    }
                    // Sub-sampling
                } else {
                    if (this.data != null) {
                        DataSet dataSet = DataUtils.getResamplingDataset(this.data, (int) (this.data.getNumRows() * this.percentResampleSize / 100.0));
                        task = new GeneralResamplingSearchRunnable(dataSet, this.algorithm, this.parameters, this, this.verbose);
                    } else {
                        List<DataModel> dataModels = new ArrayList<>();
                        for (DataSet data : this.dataSets) {
                            DataSet dataSet = DataUtils.getResamplingDataset(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                            dataModels.add(dataSet);
                        }
                        task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                                this.verbose);
                    }
                }

                if (this.externalGraph != null) {
                    task.setExternalGraph(this.externalGraph);
                }
                task.setKnowledge(this.knowledge);
                task.run();
            }

            // Search again with original dataset
            if (this.resamplingWithReplacement && this.addOriginalDataset) {
                GeneralResamplingSearchRunnable task;

                if (this.data != null) {
                    task = new GeneralResamplingSearchRunnable(this.data, this.algorithm, this.parameters, this, this.verbose);
                } else {
                    List<DataModel> dataModels = new ArrayList<>(this.dataSets);
                    task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                            this.verbose);
                }

                if (this.externalGraph != null) {
                    task.setExternalGraph(this.externalGraph);
                }
                task.setKnowledge(this.knowledge);
                task.run();
            }

        } else {
            // Running in the parallel multiThread form
            if (this.verbose) {
                this.out.println("Running Resamplings in Parallel Mode, numberResampling = " + this.numberResampling);
            }

            for (int i1 = 0; i1 < this.numberResampling; i1++) {

                GeneralResamplingSearchRunnable task;

                // Bootstrapping
                if (this.resamplingWithReplacement) {
                    if (this.data != null) {
                        DataSet dataSet = DataUtils.getBootstrapSample(this.data, (int) (this.data.getNumRows() * this.percentResampleSize / 100.0));
                        task = new GeneralResamplingSearchRunnable(dataSet, this.algorithm, this.parameters, this, this.verbose);
                    } else {
                        List<DataModel> dataModels = new ArrayList<>();
                        for (DataSet data : this.dataSets) {
                            DataSet dataSet = DataUtils.getBootstrapSample(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                            dataModels.add(dataSet);
                        }
                        task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                                this.verbose);
                    }
                    // Sub-sampling
                } else {
                    if (this.data != null) {
                        DataSet dataSet = DataUtils.getResamplingDataset(this.data, (int) (this.data.getNumRows() * this.percentResampleSize / 100.0));
                        task = new GeneralResamplingSearchRunnable(dataSet, this.algorithm, this.parameters, this, this.verbose);
                    } else {
                        List<DataModel> dataModels = new ArrayList<>();
                        for (DataSet data : this.dataSets) {
                            DataSet dataSet = DataUtils.getResamplingDataset(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                            dataModels.add(dataSet);
                        }
                        task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                                this.verbose);
                    }
                }

                if (this.externalGraph != null) {
                    task.setExternalGraph(this.externalGraph);
                }
                task.setKnowledge(this.knowledge);
                this.pool.submit(task);
            }

            // Search again with original dataset
            if (this.resamplingWithReplacement && this.addOriginalDataset) {
                GeneralResamplingSearchRunnable task;

                if (this.data != null) {
                    task = new GeneralResamplingSearchRunnable(this.data, this.algorithm, this.parameters, this, this.verbose);
                } else {
                    List<DataModel> dataModels = new ArrayList<>(this.dataSets);
                    task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                            this.verbose);
                }

                if (this.externalGraph != null) {
                    task.setExternalGraph(this.externalGraph);
                }
                task.setKnowledge(this.knowledge);
                this.pool.submit(task);
            }

            this.pool.shutdown();

            while (!this.pool.isTerminated()) {
                try {
                    Thread.sleep(1000);
                    // out.println("Waiting...");
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            // out.println("Is terminated: " + pool.isTerminated());
        }

        // If the pool is prematurely terminated, do sequentially
        if (this.PAGs.size() == 0) {
            for (int i1 = 0; i1 < this.numberResampling; i1++) {
                GeneralResamplingSearchRunnable task = null;

                // Bootstrapping
                if (this.resamplingWithReplacement) {
                    if (this.data != null) {
                        DataSet dataSet = DataUtils.getBootstrapSample(this.data, (int) (this.data.getNumRows() * this.percentResampleSize / 100.0));
                        task = new GeneralResamplingSearchRunnable(dataSet, this.algorithm, this.parameters, this, this.verbose);
                    } else {
                        List<DataModel> dataModels = new ArrayList<>();
                        for (DataSet data : this.dataSets) {
                            DataSet dataSet = DataUtils.getBootstrapSample(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                            dataModels.add(dataSet);
                        }
                        task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                                this.verbose);
                    }
                    // Sub-sampling
                } else {
                    if (this.data != null) {
                        DataSet dataSet = DataUtils.getResamplingDataset(this.data, (int) (this.data.getNumRows() * this.percentResampleSize / 100.0));
                        task = new GeneralResamplingSearchRunnable(dataSet, this.algorithm, this.parameters, this, this.verbose);
                    } else {
                        List<DataModel> dataModels = new ArrayList<>();
                        for (DataSet data : this.dataSets) {
                            DataSet dataSet = DataUtils.getResamplingDataset(data, (int) (data.getNumRows() * this.percentResampleSize / 100.0));
                            dataModels.add(dataSet);
                        }
                        task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                                this.verbose);
                    }
                }

                if (this.externalGraph != null) {
                    task.setExternalGraph(this.externalGraph);
                }
                task.setKnowledge(this.knowledge);
                task.run();
            }

            // Search again with original dataset
            if (this.resamplingWithReplacement && this.addOriginalDataset) {
                GeneralResamplingSearchRunnable task;

                if (this.data != null) {
                    task = new GeneralResamplingSearchRunnable(this.data, this.algorithm, this.parameters, this, this.verbose);
                } else {
                    List<DataModel> dataModels = new ArrayList<>(this.dataSets);
                    task = new GeneralResamplingSearchRunnable(dataModels, this.multiDataSetAlgorithm, this.parameters, this,
                            this.verbose);
                }

                if (this.externalGraph != null) {
                    task.setExternalGraph(this.externalGraph);
                }
                task.setKnowledge(this.knowledge);
                task.run();
            }

        }

        this.parameters.set("numberResampling", this.numberResampling); // This needs to be reset back to the previous value

        return this.PAGs;
    }

}
