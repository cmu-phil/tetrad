/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.BlocksIndTestTs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Tsc;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.search.blocks.SingleClusterPolicy;
import edu.cmu.tetrad.search.test.IndTestBlocksTs;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.*;
import org.ejml.simple.SimpleMatrix;

import java.io.Serial;
import java.util.*;

/**
 * The TrekMimic class implements methods for performing advanced graph-based search algorithms
 * using statistical and structural approaches. This class is an extension of various abstract
 * and utility classes, combining functionalities to manipulate, recover, and analyze latent
 * structures in a given data model. It includes methodologies to discover latent variables,
 * assess relationships, and estimate statistical properties from data.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Trek-MIMIC",
        command = "trek-mimic",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class TrekMimic extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private final IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Optional observed variables known to be inputs to the latent structure.
     * These are stored by name so they remain stable across node replacement.
     */
    private final Set<String> inputNames = new LinkedHashSet<>();

    /**
     * Optional observed variables known to be outputs from the latent structure.
     * These are stored by name so they remain stable across node replacement.
     */
    private final Set<String> outputNames = new LinkedHashSet<>();

    /**
     * Constructs a new instance of the TrekMimic class. This constructor initializes the
     * critical independence test mechanism required for the algorithm's operation.
     * Specifically, it instantiates a BlocksIndTestTs object and assigns it to the internal
     * test field, which is used for performing independence tests based on "Blocks-Test-TS".
     */
    public TrekMimic() {
        this.test = new BlocksIndTestTs();
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;
        Tsc tsc = new Tsc(dataModel.getVariables(), new CovarianceMatrix(data));
        tsc.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        tsc.setRmax(3);
        tsc.setMinRedundancy(0);
        tsc.setAlpha(parameters.getDouble(Params.ALPHA));
        Map<Set<Integer>, Integer> clusters = tsc.findClusters();
        List<List<Integer>> blocks = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();

        for (Set<Integer> block : clusters.keySet()) {
            List<Integer> blockList = new ArrayList<>(block);
            Collections.sort(blockList);
            blocks.add(blockList);
            ranks.add(clusters.get(block));
        }

        if (knowledge != null) {
            List<String> tier0 = knowledge.getTier(0);
            List<String> tier1 = knowledge.getTier(1);

            List<Node> inputs = new ArrayList<>();
            List<Node> outputs = new ArrayList<>();

            for (String var : tier0) {
                Node node = data.getVariable(var);
                inputs.add(node);
            }

            for (String var : tier1) {
                Node node = data.getVariable(var);
                outputs.add(node);
            }

            setInputs(inputs);
            setOutputs(outputs);
        }

        BlocksUtil.validateBlocks(blocks, data);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        BlockSpec spec = BlocksUtil.toSpec(blocks, ranks, data);

        spec = BlocksUtil.applySingleClusterPolicy(spec, SingleClusterPolicy.EXCLUDE, parameters.getDouble(Params.ALPHA));

        ((BlocksIndTestTs) this.test).setBlockSpec(spec);

//        edu.cmu.tetrad.search.Pc.ColliderOrientationStyle colliderOrientationStyle = edu.cmu.tetrad.search.Pc.ColliderOrientationStyle.MAX_P;

        IndependenceTest test = this.test.getTest(dataModel, parameters);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        ((IndTestBlocksTs) test).setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));

        edu.cmu.tetrad.search.Pc search = new edu.cmu.tetrad.search.Pc(test);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);
        search.setFasStable(false);
//        search.setColliderOrientationStyle(colliderOrientationStyle);
        search.setVerbose(false);
        Graph graph = search.search();

        for (int i = 0; i < spec.blocks().size(); i++) {
            Node var = spec.blockVariables().get(i);

            for (int j : spec.blocks().get(i)) {
                Node node2 = spec.dataSet().getVariables().get(j);

                if (!mayBeLatentChild(node2)) {
                    continue;
                }

                graph.addNode(node2);

                if (!graph.isParentOf(var, node2)) {
                    graph.addDirectedEdge(var, node2);
                }
            }
        }

        graph = GraphUtils.replaceNodes(graph, data.getVariables());

        for (Node node : data.getVariables()) {
            if (graph.getNode(node.getName()) == null) {
                graph.addNode(node);
            }
        }

        List<Node> allLatents = new ArrayList<>(spec.blockVariables());
        List<Node> allChildren = determineObservedChildren(graph, allLatents, data.getVariables());
        List<Node> initialPool = determineParentPool(data.getVariables(), allChildren);

        List<Node> variables = data.getVariables();
        SimpleMatrix s = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();

        int sampleSize = data.getNumRows();
        double alpha = parameters.getDouble(Params.ALPHA);

        edu.cmu.tetrad.search.TrekMimic tm = new edu.cmu.tetrad.search.TrekMimic();
        tm.setDoHigherRankExpansion(true);
        tm.setMaxLatentSubsetSize(parameters.getInt("maxLatentSubsetSize", 4));

        tm.recoverMeasuredParentsHybrid(
                graph,
                initialPool,
                allLatents,
                variables,
                s,
                sampleSize,
                alpha
        );

        orientLatentEdgesByCorrelationOfParentsAndChildren(graph, variables, s, sampleSize, alpha);
        return graph;
    }

    private void orientLatentEdgesByCorrelationOfParentsAndChildren(Graph graph,
                                                                    List<Node> variables,
                                                                    SimpleMatrix s,
                                                                    int sampleSize,
                                                                    double alpha) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());

        for (Edge edge : edges) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (x.getNodeType() != NodeType.LATENT || y.getNodeType() != NodeType.LATENT) {
                continue;
            }

            List<Node> parentsx = new ArrayList<>(graph.getParents(x));
            List<Node> parentsy = new ArrayList<>(graph.getParents(y));

            List<Node> childrenx = new ArrayList<>(graph.getChildren(x));
            List<Node> childreny = new ArrayList<>(graph.getChildren(y));

            parentsx.removeIf(n -> n.getNodeType() == NodeType.LATENT);
            parentsy.removeIf(n -> n.getNodeType() == NodeType.LATENT);

            childrenx.removeIf(n -> n.getNodeType() == NodeType.LATENT);
            childreny.removeIf(n -> n.getNodeType() == NodeType.LATENT);

            boolean allCorrelatedxy = true;
            boolean pairTestedxy = false;

            for (Node parentx : parentsx) {
                for (Node childy : childreny) {
                    pairTestedxy = true;

                    if (!correlated(parentx, childy, variables, s, sampleSize, alpha)) {
                        allCorrelatedxy = false;
                        break;
                    }
                }

                if (!allCorrelatedxy) {
                    break;
                }
            }

            boolean orientXtoY = allCorrelatedxy && pairTestedxy;

            boolean allCorrelatedyx = true;
            boolean pairTestedyx = false;

            for (Node parenty : parentsy) {
                for (Node childx : childrenx) {
                    pairTestedyx = true;

                    if (!correlated(parenty, childx, variables, s, sampleSize, alpha)) {
                        allCorrelatedyx = false;
                        break;
                    }
                }

                if (!allCorrelatedyx) {
                    break;
                }
            }

            boolean orientYtoX = allCorrelatedyx && pairTestedyx;

            // If both directions are supported or neither is supported, leave unoriented.
            if (orientXtoY == orientYtoX) {
                continue;
            }

            graph.removeEdge(edge);

            if (orientXtoY) {
                graph.addDirectedEdge(x, y);
            } else {
                graph.addDirectedEdge(y, x);
            }
        }
    }

    private boolean correlated(Node a, Node b, List<Node> variables, SimpleMatrix s, int sampleSize, double alpha) {
        int i = variables.indexOf(a);
        int j = variables.indexOf(b);

        double r = s.get(i, j);

        if (Math.abs(r) >= 1.0) {
            return true;
        }

        double z = 0.5 * Math.log((1.0 + r) / (1.0 - r)) * Math.sqrt(sampleSize - 3.0);
        double cutoff = StatUtils.getZForAlpha(alpha);

        return Math.abs(z) > cutoff;
    }

    private List<Node> getObservedParentsUnion(Graph graph, Collection<Node> latents) {
        LinkedHashSet<Node> parents = new LinkedHashSet<>();

        for (Node latent : latents) {
            for (Node parent : graph.getParents(latent)) {
                if (parent.getNodeType() != NodeType.LATENT) {
                    parents.add(parent);
                }
            }
        }

        return new ArrayList<>(parents);
    }

    private List<Node> getObservedParents(Graph graph, Node latent) {
        return getObservedParentsUnion(graph, Collections.singletonList(latent));
    }

    private List<Node> getObservedChildrenUnion(Graph graph, Collection<Node> latents) {
        LinkedHashSet<Node> children = new LinkedHashSet<>();

        for (Node latent : latents) {
            for (Node child : graph.getChildren(latent)) {
                if (child.getNodeType() != NodeType.LATENT) {
                    children.add(child);
                }
            }
        }

        return new ArrayList<>(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Trek-MIMIC using " + this.test.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.DEPTH);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the observed variables known to be inputs to the latent structure.
     *
     * @param inputs the input variables
     */
    public void setInputs(Collection<Node> inputs) {
        this.inputNames.clear();

        if (inputs != null) {
            for (Node node : inputs) {
                if (node != null) {
                    this.inputNames.add(node.getName());
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Sets the observed variables known to be outputs from the latent structure.
     *
     * @param outputs the output variables
     */
    public void setOutputs(Collection<Node> outputs) {
        this.outputNames.clear();

        if (outputs != null) {
            for (Node node : outputs) {
                if (node != null) {
                    this.outputNames.add(node.getName());
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Ensures that no observed variable is simultaneously declared as both an input and an output.
     */
    private void validateInputOutputKnowledge() {
        Set<String> intersection = new LinkedHashSet<>(this.inputNames);
        intersection.retainAll(this.outputNames);

        if (!intersection.isEmpty()) {
            throw new IllegalArgumentException(
                    "The same variables cannot be declared as both inputs and outputs: " + intersection
            );
        }
    }

    /**
     * Returns true if the given node is known to be an input.
     *
     * @param node the node
     * @return true if the node is known to be an input
     */
    private boolean isKnownInput(Node node) {
        return node != null && this.inputNames.contains(node.getName());
    }

    /**
     * Returns true if the given node is known to be an output.
     *
     * @param node the node
     * @return true if the node is known to be an output
     */
    private boolean isKnownOutput(Node node) {
        return node != null && this.outputNames.contains(node.getName());
    }

    /**
     * Returns true if the given observed node may be treated as a child of a latent.
     * <p>
     * If the user has declared the node to be an input, it is not treated as a child.
     * If the user has declared the node to be an output, it is treated as a child.
     * Otherwise, the previous behavior is retained.
     *
     * @param node the node
     * @return true if the node may be treated as a child of a latent
     */
    private boolean mayBeLatentChild(Node node) {
        if (isKnownInput(node)) {
            return false;
        }

        if (isKnownOutput(node)) {
            return true;
        }

        return true;
    }

    /**
     * Returns the observed variables to treat as children of the supplied latents, incorporating
     * any optional input/output role knowledge.
     *
     * @param graph the graph
     * @param latents the latent nodes
     * @param observedVariables the observed variables
     * @return the observed child set
     */
    private List<Node> determineObservedChildren(Graph graph,
                                                 Collection<Node> latents,
                                                 List<Node> observedVariables) {
        LinkedHashMap<String, Node> byName = new LinkedHashMap<>();

        for (Node node : observedVariables) {
            byName.put(node.getName(), node);
        }

        LinkedHashSet<Node> children = new LinkedHashSet<>();

        // Start from the previous behavior.
        for (Node child : getObservedChildrenUnion(graph, latents)) {
            Node resolved = byName.get(child.getName());

            if (resolved != null && !isKnownInput(resolved)) {
                children.add(resolved);
            }
        }

        // If outputs were explicitly supplied, make sure they are treated as children.
        for (String name : this.outputNames) {
            Node resolved = byName.get(name);

            if (resolved != null) {
                children.add(resolved);
            }
        }

        return new ArrayList<>(children);
    }

    /**
     * Returns the observed variables to treat as the parent pool, incorporating any optional
     * input/output role knowledge.
     *
     * @param observedVariables the observed variables
     * @param observedChildren the observed child variables
     * @return the parent pool
     */
    private List<Node> determineParentPool(List<Node> observedVariables,
                                           Collection<Node> observedChildren) {
        LinkedHashSet<Node> pool = new LinkedHashSet<>(observedVariables);
        pool.removeAll(observedChildren);

        // Inputs should always be eligible for the parent pool if present in the data.
        for (Node node : observedVariables) {
            if (isKnownInput(node)) {
                pool.add(node);
            }
        }

        // Outputs should not appear in the parent pool.
        pool.removeIf(this::isKnownOutput);

        return new ArrayList<>(pool);
    }

    /**
     * Returns the currently specified input variable names.
     *
     * @return the input variable names
     */
    public List<String> getInputNames() {
        return new ArrayList<>(this.inputNames);
    }

    /**
     * Returns the currently specified output variable names.
     *
     * @return the output variable names
     */
    public List<String> getOutputNames() {
        return new ArrayList<>(this.outputNames);
    }

    /**
     * Clears any supplied input/output role knowledge.
     */
    public void clearInputOutputKnowledge() {
        this.inputNames.clear();
        this.outputNames.clear();
    }
}

