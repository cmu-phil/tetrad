package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScore2;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.csb.mgm.MixedUtils;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedMGMFgs implements Algorithm {
    public Graph search(DataSet ds, Map<String, Number> parameters) {
        MGM m = new MGM(ds, new double[]{
                parameters.get("mgmParam1").doubleValue(),
                parameters.get("mgmParam2").doubleValue(),
                parameters.get("mgmParam3").doubleValue()
        });
        Graph gm = m.search();
        DataSet dataSet = MixedUtils.makeContinuousData(ds);
        SemBicScore2 score = new SemBicScore2(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        Fgs fg = new Fgs(score);
        fg.setBoundGraph(gm);
        fg.setVerbose(false);
        Graph p = fg.search();
        return convertBack(ds, p);
    }

    public String getName() {
        return "m-MGMFGS";
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
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

    public String getDescription() {
        return "MGM-FGS, assuming the data are mixed. Uses the output of MGM as an intial graph " +
                "for FGS.";
    }
}
