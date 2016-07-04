package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedSemThenDiscretizeHalfSimulation implements Simulation {
    private Graph graph;
    private DataSet dataSet;
    private int numDataSets;

    public MixedSemThenDiscretizeHalfSimulation(int numDataSets) {
        this.numDataSets = numDataSets;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public DataSet getDataSet(int index, Parameters parameters) {
        this.graph = GraphUtils.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getInt("numEdges"),
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                parameters.getInt("connected") == 1);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet continuousData = im.simulateData(parameters.getInt("sampleSize"), false);

        List<Node> shuffledNodes = new ArrayList<>(continuousData.getVariables());
        Collections.shuffle(shuffledNodes);

        Discretizer discretizer = new Discretizer(continuousData);

        for (int i = 0; i < shuffledNodes.size() * parameters.getDouble("percentDiscreteForMixedSimulation") * 0.01; i++) {
            discretizer.equalIntervals(shuffledNodes.get(i), parameters.getInt("numCategories"));
        }

        this.dataSet = discretizer.discretize();
        return this.dataSet;
    }

    public Graph getDag() {
        return graph;
    }

    public DataSet getData() {
        return dataSet;
    }

    public String toString() {
        return "Simulation SEM data then discretizing some variables";
    }

    public boolean isMixed() {
        return true;
    }

    @Override
    public int getNumDataSets() {
        return numDataSets;
    }

    @Override
    public DataType getDataType(Parameters parameters) {
        double percent = parameters.getDouble("percentDiscreteForMixedSimulation");

        if (percent == 0) {
            return DataType.Continuous;
        } else if (percent == 100) {
            return DataType.Discrete;
        } else {
            return DataType.Mixed;
        }
    }
}
