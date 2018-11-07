package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * @author jdramsey
 */
public class FactorAnalysis implements Algorithm {
    static final long serialVersionUID = 23L;

    public Graph search(DataModel ds, Parameters parameters) {
    	if (parameters.getInt("numberResampling") < 1) {

            DataSet selectedModel = (DataSet) ds;

            if (selectedModel == null) {
                throw new NullPointerException("Data not specified.");
            }

            edu.cmu.tetrad.search.FactorAnalysis analysis = new edu.cmu.tetrad.search.FactorAnalysis(selectedModel);
            analysis.setThreshold(parameters.getDouble("convergenceThreshold"));
            analysis.setNumFactors(parameters.getInt("numFactors"));

            double threshold = parameters.getDouble("fa_threshold");

            TetradMatrix unrotatedSolution = analysis.successiveResidual();
            TetradMatrix rotatedSolution = analysis.successiveFactorVarimax(unrotatedSolution);

            SemGraph graph = new SemGraph();

            Vector<Node> observedVariables = new Vector<>();

            for (Node a : selectedModel.getVariables()) {
                graph.addNode(a);
                observedVariables.add(a);
            }

            Vector<Node> factors = new Vector<>();

            if (parameters.getBoolean("useVarimax")) {
                for (int i = 0; i < rotatedSolution.columns(); i++) {
                    ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
                    factor.setNodeType(NodeType.LATENT);
                    graph.addNode(factor);
                    factors.add(factor);
                }

                for (int i = 0; i < rotatedSolution.rows(); i++) {
                    for (int j = 0; j < rotatedSolution.columns(); j++) {
                        if (Math.abs(rotatedSolution.get(i, j)) > threshold) {
                            graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                        }
                    }
                }
            } else {
                for (int i = 0; i < unrotatedSolution.columns(); i++) {
                    ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
                    factor.setNodeType(NodeType.LATENT);
                    graph.addNode(factor);
                    factors.add(factor);
                }

                for (int i = 0; i < unrotatedSolution.rows(); i++) {
                    for (int j = 0; j < unrotatedSolution.columns(); j++) {
                        if (Math.abs(unrotatedSolution.get(i, j)) > threshold) {
                            graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                        }
                    }
                }
            }

            if (parameters.getBoolean("verbose")) {
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

                String output = "Unrotated Factor Loading Matrix:\n";

                output += tableString(unrotatedSolution, nf, Double.POSITIVE_INFINITY);

                if (unrotatedSolution.columns() != 1) {
                    output += "\n\nRotated Matrix (using sequential varimax):\n";
                    output += tableString(rotatedSolution, nf, threshold);
                }

                System.out.println(output);
                TetradLogger.getInstance().forceLogMessage(output);
            }

            return graph;
    	}else{
    		FactorAnalysis algorithm = new FactorAnalysis();
    		
			DataSet data = (DataSet) ds;
			GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt("numberResampling"));

			search.setPercentResampleSize(parameters.getDouble("percentResampleSize"));
            search.setResamplingWithReplacement(parameters.getBoolean("resamplingWithReplacement"));
            
            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("resamplingEnsemble", 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
			search.setEdgeEnsemble(edgeEnsemble);
			search.setParameters(parameters);
			search.setVerbose(parameters.getBoolean("verbose"));
			return search.search();
    	}
    }

    private String tableString(TetradMatrix matrix, NumberFormat nf, double threshold) {
        TextTable table = new TextTable(matrix.rows() + 1, matrix.columns() + 1);

        for (int i = 0; i < matrix.rows() + 1; i++) {
            for (int j = 0; j < matrix.columns() + 1; j++) {
                if (i > 0 && j == 0) {
                    table.setToken(i, j, "X" + i);
                } else if (i == 0 && j > 0) {
                    table.setToken(i, j, "Factor " + j);
                } else if (i > 0 && j > 0) {
                    double coefficient = matrix.get(i - 1, j - 1);
                    String token = !Double.isNaN(coefficient) ? nf.format(coefficient) : "Undefined";
                    token += Math.abs(coefficient) > threshold ? "*" : " ";
                    table.setToken(i, j, token);
                }
            }
        }

        return "\n" + table.toString();

    }

    public Graph getComparisonGraph(Graph graph) {
        return GraphUtils.undirectedGraph(graph);
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
        params.add("fa_threshold");
        params.add("numFactors");
        params.add("useVarimax");
        params.add("convergenceThreshold");
        params.add("verbose");
        // Resampling
        params.add("numberResampling");
        params.add("percentResampleSize");
        params.add("resamplingWithReplacement");
        params.add("resamplingEnsemble");
        return params;
    }
}