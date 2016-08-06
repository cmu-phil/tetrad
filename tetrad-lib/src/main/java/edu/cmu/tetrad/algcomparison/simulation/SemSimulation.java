package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class SemSimulation implements Simulation {
    static final long serialVersionUID = 23L;
    private RandomGraph randomGraph;
    private SemPm pm;
    private SemIm im;
    private StandardizedSemIm standardizedIm;
    private List<DataSet> dataSets;
    private Graph graph;

    public SemSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    public SemSimulation(SemPm pm) {
        this.randomGraph = new SingleGraph(pm.getGraph());
        this.pm = pm;
    }

    public SemSimulation(SemIm im) {
        this.randomGraph = new SingleGraph(im.getSemPm().getGraph());
        this.im = im;
        this.pm = im.getSemPm();
    }

    public SemSimulation(StandardizedSemIm im) {
        this.randomGraph = new SingleGraph(im.getSemPm().getGraph());
        this.standardizedIm = im;
        this.pm = im.getSemPm();
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = randomGraph.createGraph(parameters);

        dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));
            DataSet dataSet = simulate(graph, parameters);
            dataSet.setName("" + (i + 1));
            dataSets.add(dataSet);
        }
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
    public Graph getTrueGraph() {
        return graph;
    }

    @Override
    public String getDescription() {
        return "Linear, Gaussian SEM simulation using " + randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(randomGraph instanceof SingleGraph)) {
            parameters.addAll(randomGraph.getParameters());
        }

//        if (pm == null) {
//            parameters.addAll(SemPm.getParameterNames());
//        }

        if (im == null && standardizedIm == null) {
            parameters.addAll(SemIm.getParameterNames());
        }

        parameters.add("numRuns");
        parameters.add("sampleSize");
        return parameters;
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        SemIm im = this.im;

        if (standardizedIm != null) {
            return standardizedIm.simulateData(parameters.getInt("sampleSize"), false);
        } else {
            if (im == null) {
                SemPm pm = this.pm;

                if (pm == null) {
                    pm = new SemPm(graph);
                }

                im = new SemIm(pm, parameters);
            }

            return im.simulateData(parameters.getInt("sampleSize"), false);
        }
    }
}
