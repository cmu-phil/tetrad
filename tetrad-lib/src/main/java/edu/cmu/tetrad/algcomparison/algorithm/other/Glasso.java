package edu.cmu.tetrad.algcomparison.algorithm.other;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.pitt.csb.mgm.MGM;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class Glasso implements Algorithm {
    static final long serialVersionUID = 23L;

    public Graph search(DataSet ds, Parameters parameters) {

        DoubleMatrix2D cov = new DenseDoubleMatrix2D(ds.getCovarianceMatrix().toArray());

        edu.cmu.tetrad.search.Glasso glasso = new edu.cmu.tetrad.search.Glasso(cov);
        glasso.setMaxit((int) parameters.getInt("maxit"));
        glasso.setIa(parameters.getBoolean("ia"));
        glasso.setIs(parameters.getBoolean("is"));
        glasso.setItr(parameters.getBoolean("itr"));
        glasso.setIpen(parameters.getBoolean("ipen"));
        glasso.setThr(parameters.getDouble("thr"));
        glasso.setRhoAllEqual(1.0);

        edu.cmu.tetrad.search.Glasso.Result result = glasso.search();
        TetradMatrix wwi = new TetradMatrix(result.getWwi().toArray());

        List<Node> variables = ds.getVariables();
        Graph resultGraph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                if (wwi.get(i, j) != 0.0 && wwi.get(i, j) != 0.0) {
                    resultGraph.addUndirectedEdge(variables.get(i), variables.get(j));
                }
            }
        }

        return resultGraph;
    }

    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    public String getDescription() {
        return "GLASSO (Graphical LASSO)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("maxit");
        params.add("ia");
        params.add("is");
        params.add("itr");
        params.add("ipen");
        params.add("thr");
        return params;
    }
}