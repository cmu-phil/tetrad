package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * Implements permutation search.
 *
 * @author bryanandrews
 */

public class PermutationSearch2 {
    private final Score score;
    private final List<Node> order;
    private final Map<String, Node> nodeMap;
    private final Map<Node, Integer> index;
    private final BossNew2 bossNew2;

    private Knowledge knowledge = new Knowledge();

    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private final Map<Node, Double> scores;
    private final Map<Node, GrowShrinkTree> gsts;
    private int numStarts = 1;
    private boolean verbose = true;


    public PermutationSearch2(BossNew2 bossNew2) {
        this.bossNew2 = bossNew2;
        this.variables = bossNew2.getVariables();
        this.parents = bossNew2.getParents();
        this.score = bossNew2.getScore();
        this.nodeMap = new HashMap<>();
        this.order = new ArrayList<>();
        this.scores = new HashMap<>();

        int i = 0;
        this.index = new HashMap<>();
        this.gsts = new HashMap<>();
        for (Node node : this.variables) {
            this.index.put(node, i++);
            this.nodeMap.put(node.getName(), node);
            this.parents.put(node, new HashSet<>());
        }
    }

    public Graph search() {
        this.gsts.clear();
        for (Node node : this.variables) {
            if (this.knowledge.isEmpty()) {
                this.gsts.put(node, new GrowShrinkTree(this.score, this.index, node));
            } else {
                List<Node> required = new ArrayList<>();
                List<Node> forbidden = new ArrayList<>();
                for (Node parent : this.variables) {
                    if (this.knowledge.isRequired(parent.getName(), node.getName())) required.add(parent);
                    if (this.knowledge.isForbidden(parent.getName(), node.getName())) forbidden.add(parent);
                }
                this.gsts.put(node, new GrowShrinkTree(this.score, this.index, node, required, forbidden));
            }
        }

        List<int[]> tasks = new ArrayList<>();
        if (!this.knowledge.isEmpty() && this.knowledge.getVariablesNotInTiers().isEmpty()) {
            int start, end = 0;
            for (int i = 0; i < this.knowledge.getNumTiers(); i++) {
                start = end;
                for (String name : this.knowledge.getTier(i)) {
                    this.order.add(this.nodeMap.get(name));
                    end++;
                }
                if (!this.knowledge.isTierForbiddenWithin(i)) {
                    tasks.add(new int[] {start, end});
                }
            }
        } else {
            this.order.addAll(this.variables);
            tasks.add(new int[] {0, this.variables.size()});
        }

        for (int[] task : tasks) {
            List<Node> prefix = new ArrayList<>(this.order.subList(0, task[0]));
            List<Node> suborder = this.order.subList(task[0], task[1]);
            makeValidKnowledgeOrder(suborder);
            bossNew2.searchSuborder(prefix, suborder, gsts, parents, scores, numStarts);
        }

        return getGraph(this.variables, parents, true);
    }

    private Graph getGraph(List<Node> nodes, Map<Node, Set<Node>> parents, boolean cpDag) {
        Graph graph = new EdgeListGraph(nodes);

        for (Node a : nodes) {
            for (Node b : parents.get(a)) {
                graph.addDirectedEdge(b, a);
            }
        }

        if (cpDag) {
            MeekRules rules = new MeekRules();
            rules.orientImplied(graph);
        }

        return graph;
    }

    private void makeValidKnowledgeOrder(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            order.sort((a, b) -> {
                if (a.getName().equals(b.getName())) return 0;
                else if (this.knowledge.isRequired(a.getName(), b.getName())) return -1;
                else if (this.knowledge.isRequired(b.getName(), a.getName())) return 1;
                else return 0;
            });
        }
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
        this.bossNew2.setKnowledge(knowledge);
    }
}