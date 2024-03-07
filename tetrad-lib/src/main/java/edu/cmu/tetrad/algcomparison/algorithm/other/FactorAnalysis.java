package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Factor analysis.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@Bootstrapping
public class FactorAnalysis extends AbstractBootstrapAlgorithm implements Algorithm {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        edu.cmu.tetrad.search.FactorAnalysis analysis = new edu.cmu.tetrad.search.FactorAnalysis(dataSet);
        analysis.setThreshold(parameters.getDouble("convergenceThreshold"));
        analysis.setNumFactors(parameters.getInt("numFactors"));

        double threshold = parameters.getDouble("fa_threshold");

        Matrix unrotatedSolution = analysis.successiveResidual();
        Matrix rotatedSolution = analysis.successiveFactorVarimax(unrotatedSolution);

        SemGraph graph = new SemGraph();

        Vector<Node> observedVariables = new Vector<>();

        for (Node a : dataModel.getVariables()) {
            graph.addNode(a);
            observedVariables.add(a);
        }

        Vector<Node> factors = new Vector<>();

        if (parameters.getBoolean("useVarimax")) {
            for (int i = 0; i < rotatedSolution.getNumColumns(); i++) {
                ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
                factor.setNodeType(NodeType.LATENT);
                graph.addNode(factor);
                factors.add(factor);
            }

            for (int i = 0; i < rotatedSolution.getNumRows(); i++) {
                for (int j = 0; j < rotatedSolution.getNumColumns(); j++) {
                    if (FastMath.abs(rotatedSolution.get(i, j)) > threshold) {
                        graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                    }
                }
            }
        } else {
            for (int i = 0; i < unrotatedSolution.getNumColumns(); i++) {
                ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
                factor.setNodeType(NodeType.LATENT);
                graph.addNode(factor);
                factors.add(factor);
            }

            for (int i = 0; i < unrotatedSolution.getNumRows(); i++) {
                for (int j = 0; j < unrotatedSolution.getNumColumns(); j++) {
                    if (FastMath.abs(unrotatedSolution.get(i, j)) > threshold) {
                        graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                    }
                }
            }
        }

        if (parameters.getBoolean(Params.VERBOSE)) {
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

            String output = "Unrotated Factor Loading Matrix:\n";

            output += tableString(unrotatedSolution, nf, Double.POSITIVE_INFINITY);

            if (unrotatedSolution.getNumColumns() != 1) {
                output += "\n\nRotated Matrix (using sequential varimax):\n";
                output += tableString(rotatedSolution, nf, threshold);
            }

            System.out.println(output);
            TetradLogger.getInstance().forceLogMessage(output);
        }

        return graph;
    }

    private String tableString(Matrix matrix, NumberFormat nf, double threshold) {
        TextTable table = new TextTable(matrix.getNumRows() + 1, matrix.getNumColumns() + 1);

        for (int i = 0; i < matrix.getNumRows() + 1; i++) {
            for (int j = 0; j < matrix.getNumColumns() + 1; j++) {
                if (i > 0 && j == 0) {
                    table.setToken(i, 0, "X" + i);
                } else if (i == 0 && j > 0) {
                    table.setToken(0, j, "Factor " + j);
                } else if (i > 0) {
                    double coefficient = matrix.get(i - 1, j - 1);
                    String token = !Double.isNaN(coefficient) ? nf.format(coefficient) : "Undefined";
                    token += FastMath.abs(coefficient) > threshold ? "*" : " ";
                    table.setToken(i, j, token);
                }
            }
        }

        return "\n" + table;

    }

    /**
     * {@inheritDoc}
     */
    public Graph getComparisonGraph(Graph graph) {
        return GraphUtils.undirectedGraph(graph);
    }

    /**
     * <p>getDescription.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDescription() {
        return "GLASSO (Graphical LASSO)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("fa_threshold");
        params.add("numFactors");
        params.add("useVarimax");
        params.add("convergenceThreshold");

        params.add(Params.VERBOSE);

        return params;
    }
}
