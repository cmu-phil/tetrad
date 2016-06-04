package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.Simulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.pitt.csb.mgm.MixedUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class LeeHastieSimulation implements Simulation {
    private Graph dag;
    private DataSet dataSet;

    public LeeHastieSimulation() {
    }

    public void simulate(Map<String, Number> parameters) {
        this.dag = GraphUtils.randomGraphRandomForwardEdges(
                parameters.get("numMeasures").intValue(), parameters.get("numLatents").intValue(),
                parameters.get("numEdges").intValue(),
                parameters.get("maxDegree").intValue(),
                parameters.get("maxIndegree").intValue(),
                parameters.get("maxOutdegree").intValue(),
                parameters.get("connected").intValue() == 1);

        HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = dag.getNodes();

        Collections.shuffle(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            if (i < nodes.size() / 2) {
                nd.put(nodes.get(i).getName(), parameters.get("numCategories").intValue());
            } else {
                nd.put(nodes.get(i).getName(), 0);
            }
        }

        Graph graph = MixedUtils.makeMixedGraph(dag, nd);

        GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(graph, "Split(-1.5,-.5,.5,1.5)");
        GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);

        DataSet ds = im.simulateDataAvoidInfinity(parameters.get("sampleSize").intValue(), false);
        this.dataSet = MixedUtils.makeMixedData(ds, nd);
    }

    public Graph getDag() {
        return dag;
    }

    public DataSet getData() {
        return dataSet;
    }

    public String toString() {
        return "Lee & Hastie simulation";
    }
}
