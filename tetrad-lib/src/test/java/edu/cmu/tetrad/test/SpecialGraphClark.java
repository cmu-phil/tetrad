package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * A special graph for testing a model of Clark's.
 *
 * @author jdramsey
 */
public class SpecialGraphClark implements RandomGraph {
    static final long serialVersionUID = 23L;

    @Override
    public Graph createGraph(Parameters parameters) {

        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");

        Graph g = new EdgeListGraph();
        g.addNode(x);
        g.addNode(y);
        g.addNode(z);

//        g.addDirectedEdge(x, y);
//        g.addDirectedEdge(z, x);
//        g.addDirectedEdge(z, y);

        g.addDirectedEdge(x, y);
        g.addDirectedEdge(x, z);
        g.addDirectedEdge(y, z);
//
        return g;
    }

    @Override
    public String getDescription() {
        return "Graph constructed by adding random forward edges";
    }

    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }
}
