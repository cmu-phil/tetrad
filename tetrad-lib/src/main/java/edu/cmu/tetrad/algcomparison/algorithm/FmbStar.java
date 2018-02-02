package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.FgesMb;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.ConcurrencyUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "FmbStar",
        command = "cstar2",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Uses stability selection (Meinhausen et al.) with FGES-MB (Ramsey et al.) to estimate the " +
                "nodes in the Markov blanket of a target. May be used to estimate nodes with influence " +
                "on a target, in the style of CStar (Stekhoven et al.)" +
                "\nMeinshausen, Nicolai, and Peter Bühlmann. \"Stability selection.\" Journal of the Royal Statistical " +
                "Society: Series B (Statistical Methodology) 72.4 (2010): 417-473." +
                "\nStekhoven, Daniel J., et al. \"Causal stability ranking.\" Bioinformatics 28.21 (2012): 2819-2823." +
                "\nRamsey, Joseph, et al. \"A million variables and more: the Fast Greedy Equivalence Search algorithm for " +
                "learning high-dimensional graphical causal models, with an application to functional magnetic " +
                "resonance images.\" International journal of data science and analytics 3.2 (2017): 121-129."
)
public class FmbStar implements Algorithm {
    static final long serialVersionUID = 23L;
    private Algorithm algorithm;
    private int parallelism = Runtime.getRuntime().availableProcessors();

    public FmbStar() {
        this.algorithm = new Fges();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        DataSet _dataSet = (DataSet) dataSet;
        List<Node> variables = _dataSet.getVariables();

        double percentageB = parameters.getDouble("percentSubsampleSize");
        int numSubsamples = parameters.getInt("numSubsamples");
        int q = parameters.getInt("topQ");
        double pithreshold = parameters.getDouble("piThreshold");
        Node y = dataSet.getVariable(parameters.getString("targetName"));
        double penaltyDiscount = parameters.getDouble("penatyDiscount");
        variables.remove(y);

//        PcAll pc = new PcAll(new IndTestFisherZ(new CovarianceMatrixOnTheFly(_dataSet), 0.05), null);
//        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
//        pc.setDepth(1);
//        pc.setConflictRule(PcAll.ConflictRule.PRIORITY);
//        pc.setColliderDiscovery(PcAll.ColliderDiscovery.FAS_SEPSETS);
//        variables = pc.search().getAdjacentNodes(y);


        List<Node> nodes = getNodes(parameters, _dataSet, variables, percentageB, numSubsamples,
                pithreshold, y, penaltyDiscount);
        Set<Node> allNodes = new HashSet<>(nodes);

//        for (Node n : nodes) {
//            List<Node> _nodes = getNodes(parameters, _dataSet, variables, percentageB, numSubsamples,
//                    pithreshold, n, penaltyDiscount);
//            allNodes.addAll(_nodes);
//        }

        Graph graph = new EdgeListGraph(new ArrayList<>(allNodes));
        graph.addNode(y);

        for (Node w : allNodes) {
            graph.addDirectedEdge(w, y);
        }

        return graph;
    }

    private List<Node> getNodes(Parameters parameters, DataSet _dataSet, List<Node> variables, double percentageB,
                                int numSubsamples, double pithreshold, Node y, double penaltyDiscount) {
        Map<Node, Integer> counts = new ConcurrentHashMap<>();
        for (Node node : variables) counts.put(node, 0);

        class Task implements Callable<Boolean> {
            private int i;
            private Map<Node, Integer> counts;

            private Task(int i, Map<Node, Integer> counts) {
                this.i = i;
                this.counts = counts;
            }

            @Override
            public Boolean call() {
                BootstrapSampler sampler = new BootstrapSampler();
                sampler.setWithoutReplacements(true);
                DataSet sample = sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows()));
//                sample = sample.subsetColumns(variables);

                ICovarianceMatrix covariances = new CovarianceMatrixOnTheFly(sample);
                final SemBicScore score = new SemBicScore(covariances);
                score.setPenaltyDiscount(penaltyDiscount);

                FgesMb fgesMb = new FgesMb(score);
                fgesMb.setParallelism(1);//getParallelism());
                Graph g = fgesMb.search(y);

                for (final Node key : g.getNodes()) {
                    if (!g.isAdjacentTo(key, y)) continue;
                    if (g.containsNode(key)) counts.put(key, counts.get(key) + 1);
                }

                if (parameters.getBoolean("verbose")) {
                    System.out.println("Bootstrap #" + (i + 1) + " of " + numSubsamples);
                    System.out.flush();
                }

                return true;
            }
        }


        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            tasks.add(new Task(i, counts));
        }

        ConcurrencyUtils.runCallables(tasks, parallelism);

        List<Node> sortedVariables = new ArrayList<>(variables);

        sortedVariables.sort((o1, o2) -> Integer.compare(counts.get(o2), counts.get(o1)));

        double[] pi = new double[counts.keySet().size()];

        for (int i = 0; i < sortedVariables.size(); i++) {
            pi[i] = counts.get(sortedVariables.get(i)) / (double) numSubsamples;
        }

        List<Node> outNodes = new ArrayList<>();

        for (int i = 0; i < sortedVariables.size(); i++) {
            if (sortedVariables.get(i) == y) continue;

            int q = parameters.getInt("topQ");
            int p = i + 1;
            double e = pcer(pithreshold, p, q);
            double e1 = pcer(pi[i], p, q);

//            System.out.println("pi[" + i + "] = " + pi[i] + " pithreshold = " + pithreshold);

//            if (pi[i] > pithreshold) {
//                outNodes.add(variables.get(i));
//            }

            if (e1 > e) {
                outNodes.add(variables.get(i));
            }

        }

        return outNodes;
    }

    private double pcer(double pithreshold, int p, int q) {
        double pcer = (1.0 / (2 * pithreshold - 1)) * (((q * q) / ((double) (p))));
//        if (pcer < 0) pcer = 1;
        return pcer;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return algorithm.getComparisonGraph(graph);
    }

    @Override
    public String getDescription() {
        return "CStar";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("numSubsamples");
        parameters.add("percentSubsampleSize");
        parameters.add("piThreshold");
        parameters.add("targetName");
        return parameters;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }
}
