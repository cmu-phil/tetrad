package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.util.LinkedList;
import java.util.List;

/**
 * R3.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Fask Orientation",
        command = "fask_orientation",
        algoType = AlgType.orient_pairwise
//        description = "These are algorithms that orient edges X—Y for continuous variables pairwise based on non-Gaussian information. (If the variables are all Gaussian, one cannot orient these edges. That is, these rules will orient left or right randomly.) For EB, RSkew, RSkewE, Skew and SkewE, see Hyvarinen and Smith (2013). For R1, R2, R3 and R4, see Ramsey et al., 2014.\n" +
//                "\n" +
//                "The principles governing these rules vary. R1 and R2 appeal directly to the Central Limit Theorem to judge which of various conditioning sets yields the greatest non-Gaussianity measure. (The measure for non-Gaussianity measure used is Anderson-Darling.) R4 does as well, but allows coefficients for relevant parameters to be adjusted to achieve maximum non-Gaussianity. EB works by appealing to entropy for the orientation. R3 uses the same rule as EB except using the Anderson-Darling score for a measure of non-Gaussianity. RSkew and Skew appeal to measures of skew for the variables and assume positive skewness for all variables. The rules for the two are different; please see Hyvarinen and Smith for details. SkewE and RSkewE adjust the signs of variables by the signs of their skewnesses to ensure that the assumption of positive skewness holds. FaskOrientation uses the left-right rule from FASK\n" +
//                "\n" +
//                "A comparison of all of these methods is given in Ramsey et al., 2014. In general, for fMRI data, we find that the RSkew method works the best, followed by the R3 method. Cycles can be oriented using these methods, since each edge is oriented independently of the others.\n" +
//                "\n" +
//                "Input Assumptions: Continuous data in which the variables are non-Gaussian. Non-Gaussianity can be assessed using the Anderson-Darling score, which is available in the Data box.\n" +
//                "\n" +
//                "Output Format: Orients all of the edges in the input graph using the selected score. \n" +
//                "\n" +
//                "Parameters:\n" +
//                "- Cutoff for p-values (alpha). Conditional independence tests with p-values greater than this will be judged to be independent (H0).\n" +
//                "- Maximum size of conditioning set (depth). PC in the adjacency phase will consider conditioning sets for conditional independences of increasing size, up to this value. For instance, for depth 3, the maximum size of a conditioning set considered will be 3."
)
public class FaskOrientation implements Algorithm, TakesInitialGraph {

    static final long serialVersionUID = 23L;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge;

    public FaskOrientation() {
    }

    public FaskOrientation(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt("bootstrapSampleSize") < 1) {
            initialGraph = algorithm.search(dataSet, parameters);

//            if (initialGraph != null) {
//                initialGraph = algorithm.search(dataSet, parameters);
//            } else {
//                throw new IllegalArgumentException("This R3 algorithm needs both data and a graph source as inputs; it \n"
//                        + "will orient the edges in the input graph using the data");
//            }

            List<String> nodes = initialGraph.getNodeNames();

            initialGraph = GraphUtils.replaceNodes(initialGraph, dataSet.getVariables());

            for (Node node : initialGraph.getNodes()){
                if (!nodes.contains(node.getName())) {
                    initialGraph.removeNode(node);
                }
            }

            dataSet = ((DataSet) dataSet).subsetColumns(initialGraph.getNodes());

            edu.cmu.tetrad.search.SemBicScore score = new edu.cmu.tetrad.search.SemBicScore(new CovarianceMatrixOnTheFly((DataSet) dataSet, false));
            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            edu.cmu.tetrad.search.Fask search = new edu.cmu.tetrad.search.Fask((DataSet) dataSet, score);
            search.setInitialGraph(initialGraph);
            search.setExtraEdgeThreshold(parameters.getDouble("extraEdgeThreshold"));
            return search.search();
        } else {
            FaskOrientation r3 = new FaskOrientation(algorithm);
            if (initialGraph != null) {
                r3.setInitialGraph(initialGraph);
            }

            DataSet data = (DataSet) dataSet;
            GeneralBootstrapTest search = new GeneralBootstrapTest(data, r3,
                    parameters.getInt("bootstrapSampleSize"));

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "R3, entropy based pairwise orientation" + (algorithm != null ? " with initial graph from "
                + algorithm.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();

        if (algorithm != null && !algorithm.getParameters().isEmpty()) {
            parameters.addAll(algorithm.getParameters());
        }

        // Bootstrapping
        parameters.add("twoCycleAlpha");
        parameters.add("extraEdgeThreshold");
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        parameters.add("verbose");

        return parameters;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
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
        if (algorithm == null) {
            throw new IllegalArgumentException("This R3 algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}
