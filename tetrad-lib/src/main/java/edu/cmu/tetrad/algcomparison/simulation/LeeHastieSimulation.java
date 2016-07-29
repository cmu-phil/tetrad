package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.pitt.csb.mgm.MixedUtils;
import org.relaxng.datatype.Datatype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * A version of the Lee & Hastic simulation which is guaranteed ot generate a discrete
 * data set.
 *
 * @author jdramsey
 */
public class LeeHastieSimulation implements Simulation {
    private List<DataSet> dataSets;
    private Graph graph;
    private DataType dataType;

    public LeeHastieSimulation(DataType dataType) {
        this.dataType = dataType;
    }

    @Override
    public void createData(Parameters parameters) {
        this.graph = GraphUtils.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2,
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                parameters.getInt("connected") == 1);

        double percentDiscrete = parameters.getDouble("percentDiscrete");

        if (dataType == DataType.Continuous && percentDiscrete != 0.0) {
            throw new IllegalArgumentException("To simulate continuous data, 'percentDiscrete' must be set to 0.0.");
        } else if (dataType == DataType.Discrete && percentDiscrete != 1000.0) {
            throw new IllegalArgumentException("To simulate discrete data, 'percentDiscrete' must be set to 100.0.");
        }

        this.dataSets = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));
            DataSet dataSet = simulate(graph, parameters);
            dataSets.add(dataSet);
        }
    }

    @Override
    public Graph getTrueGraph() {
        return graph;
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Lee & Hastie simulation";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
        parameters.add("numLatents");
        parameters.add("avgDegree");
        parameters.add("maxDegree");
        parameters.add("maxIndegree");
        parameters.add("maxOutdegree");
        parameters.add("numRuns");
        parameters.add("sampleSize");
        parameters.add("numCategories");
        parameters.add("percentDiscrete");
        return parameters;
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return dataType;
    }

    private DataSet simulate(Graph dag, Parameters parameters) {
        HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = dag.getNodes();

        Collections.shuffle(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            if (i < nodes.size() * parameters.getDouble("percentDiscrete") * 0.01) {
                nd.put(nodes.get(i).getName(), parameters.getInt("numCategories"));
            } else {
                nd.put(nodes.get(i).getName(), 0);
            }
        }

        Graph graph = MixedUtils.makeMixedGraph(dag, nd);

        GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(graph, "Split(-1.5,-.5,.5,1.5)");
        GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);

        DataSet ds = im.simulateDataAvoidInfinity(parameters.getInt("sampleSize"), false);
        return MixedUtils.makeMixedData(ds, nd);
    }

}
