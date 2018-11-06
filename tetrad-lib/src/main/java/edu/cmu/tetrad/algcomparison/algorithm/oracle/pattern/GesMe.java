package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScoreDeterministic;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.lang.Math.sqrt;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
public class GesMe implements Algorithm, TakesInitialGraph/*, HasKnowledge*/ {

    static final long serialVersionUID = 23L;
    private boolean compareToTrue = false;
    private Graph initialGraph = null;
    private ScoreWrapper score = new SemBicScoreDeterministic();

    public GesMe() {
        setCompareToTrue(false);
    }

    public GesMe(boolean compareToTrueGraph) {
        setCompareToTrue(compareToTrueGraph);
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (parameters.getInt("numberResampling") < 1) {
//          dataSet = DataUtils.center((DataSet) dataSet);
            CovarianceMatrix covarianceMatrix = new CovarianceMatrix((DataSet) dataSet);

            edu.cmu.tetrad.search.FactorAnalysis analysis = new edu.cmu.tetrad.search.FactorAnalysis(covarianceMatrix);
            analysis.setThreshold(parameters.getDouble("convergenceThreshold"));
            analysis.setNumFactors(parameters.getInt("numFactors"));
//            analysis.setNumFactors(((DataSet) dataSet).getNumColumns());

            TetradMatrix unrotated = analysis.successiveResidual();
            TetradMatrix rotated = analysis.successiveFactorVarimax(unrotated);

            if (parameters.getBoolean("verbose")) {
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

                String output = "Unrotated Factor Loading Matrix:\n";

                output += tableString(unrotated, nf, Double.POSITIVE_INFINITY);

                if (unrotated.columns() != 1) {
                    output += "\n\nRotated Matrix (using sequential varimax):\n";
                    output += tableString(rotated, nf, parameters.getDouble("fa_threshold"));
                }

                System.out.println(output);
                TetradLogger.getInstance().forceLogMessage(output);
            }

            TetradMatrix L;

            if (parameters.getBoolean("useVarimax")) {
                L = rotated;
            } else {
                L = unrotated;
            }


            TetradMatrix residual = analysis.getResidual();

            ICovarianceMatrix covFa = new CovarianceMatrix(covarianceMatrix.getVariables(), L.times(L.transpose()),
                    covarianceMatrix.getSampleSize());

            System.out.println(covFa);

	        final double[] vars = covarianceMatrix.getMatrix().diag().toArray();
	        List<Integer> indices = new ArrayList<>();
	        for (int i = 0; i < vars.length; i++) {
	            indices.add(i);
	        }

            Collections.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return -Double.compare(vars[o1], vars[o2]);
                }
            });

            NumberFormat nf = new DecimalFormat("0.000");

            for (int i = 0; i < indices.size(); i++) {
                System.out.println(nf.format(vars[indices.get(i)]) + " ");
            }

            System.out.println();

            int n = vars.length;

            int cutoff = (int) (n * ((sqrt(8 * n + 1) - 1) / (2 * n)));

            List<Node> nodes = covarianceMatrix.getVariables();

            List<Node> leaves = new ArrayList<>();

            for (int i = 0; i < cutoff; i++) {
                leaves.add(nodes.get(indices.get(i)));
            }

            IKnowledge knowledge2 = new Knowledge2();

            for (Node v : nodes) {
                if (leaves.contains(v)) {
                    knowledge2.addToTier(2, v.getName());
                } else {
                    knowledge2.addToTier(1, v.getName());
                }
            }

            knowledge2.setTierForbiddenWithin(2, true);

            System.out.println("knowledge2 = " + knowledge2);

            Score score = this.score.getScore(covFa, parameters);

            edu.cmu.tetrad.search.Fges2 search = new edu.cmu.tetrad.search.Fges2(score);
            search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));

            if (parameters.getBoolean("enforceMinimumLeafNodes")) {
                search.setKnowledge(knowledge2);
            }

            search.setVerbose(parameters.getBoolean("verbose"));
            search.setMaxDegree(parameters.getInt("maxDegree"));
            search.setSymmetricFirstStep(parameters.getBoolean("symmetricFirstStep"));

            Object obj = parameters.get("printStream");
            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            if (parameters.getBoolean("verbose")) {
//                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                String output = "Unrotated Factor Loading Matrix:\n";
                double threshold = parameters.getDouble("fa_threshold");

                output += tableString(L, nf, Double.POSITIVE_INFINITY);

                if (L.columns() != 1) {
                    output += "\n\nL:\n";
                    output += tableString(L, nf, threshold);
                }

                System.out.println(output);
                TetradLogger.getInstance().forceLogMessage(output);
            }

            System.out.println("residual = " + residual);

            return search.search();
    	}else{
    		GesMe algorithm = new GesMe(compareToTrue);
    		
			if (initialGraph != null) {
				algorithm.setInitialGraph(initialGraph);
			}
			DataSet data = (DataSet) dataSet;
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

    @Override
    public Graph getComparisonGraph(Graph graph) {
        if (compareToTrue) {
            return new EdgeListGraph(graph);
        } else {
            return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
        }
    }

    @Override
    public String getDescription() {
        return "FGES (Fast Greedy Equivalence Search) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = score.getParameters();
        parameters.add("symmetricFirstStep");
        parameters.add("faithfulnessAssumed");
        parameters.add("maxDegree");
        parameters.add("verbose");
        parameters.add("determinismThreshold");
        parameters.add("convergenceThreshold");
        parameters.add("fa_threshold");
        parameters.add("numFactors");
        parameters.add("useVarimax");
        parameters.add("enforceMinimumLeafNodes");
        // Resampling
        parameters.add("numberResampling");
        parameters.add("percentResampleSize");
        parameters.add("resamplingWithReplacement");
        parameters.add("resamplingEnsemble");
        return parameters;
    }

//    @Override
//    public IKnowledge getKnowledge() {
//        return knowledge;
//    }
//
//    @Override
//    public void setKnowledge(IKnowledge knowledge) {
//        this.knowledge = knowledge;
//    }
    public void setCompareToTrue(boolean compareToTrue) {
        this.compareToTrue = compareToTrue;
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

	@Override
	public Graph getInitialGraph() {
		return initialGraph;
	}

	@Override
	public void setInitialGraph(Graph initialGraph) {
		this.initialGraph = initialGraph;
	}

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
