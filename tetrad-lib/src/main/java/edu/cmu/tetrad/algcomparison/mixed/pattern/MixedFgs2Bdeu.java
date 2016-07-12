package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.Fgs2;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedFgs2Bdeu implements Algorithm {
    public Graph search(DataSet Dk, Parameters parameters) {
        Discretizer discretizer = new Discretizer(Dk);
        List<Node> nodes = Dk.getVariables();

        for (Node node : nodes) {
            if (node instanceof ContinuousVariable) {
                discretizer.equalIntervals(node, parameters.getInt("numCategories"));
            }
        }

        Dk = discretizer.discretize();

        BDeuScore score = new BDeuScore(Dk);
        score.setSamplePrior(parameters.getInt("samplePrior"));
        score.setStructurePrior(parameters.getInt("structurePrior"));
        Fgs2 fgs = new Fgs2(score);
        Graph p = fgs.search();
        return convertBack(Dk, p);
    }


    @Override
    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    @Override
    public String getDescription() {
        return "FGS with BDeu after discretizing the continuous variables in the data set";
    }

    private Graph convertBack(DataSet Dk, Graph p) {
        Graph p2 = new EdgeListGraph(Dk.getVariables());

        for (int i = 0; i < p.getNodes().size(); i++) {
            for (int j = i + 1; j < p.getNodes().size(); j++) {
                Node v1 = p.getNodes().get(i);
                Node v2 = p.getNodes().get(j);

                Edge e = p.getEdge(v1, v2);

                if (e != null) {
                    Node w1 = Dk.getVariable(e.getNode1().getName());
                    Node w2 = Dk.getVariable(e.getNode2().getName());

                    Edge e2 = new Edge(w1, w2, e.getEndpoint1(), e.getEndpoint2());

                    p2.addEdge(e2);
                }
            }
        }
        return p2;
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> usesParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numCategories");
        parameters.add("samplePrior");
        parameters.add("structurePrior");
        return parameters;
    }
}
