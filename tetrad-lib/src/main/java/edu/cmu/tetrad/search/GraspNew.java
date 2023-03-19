package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * Implements the BOSS algorithm.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class GraspNew implements SuborderSearch {
    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private final Map<Node, Double> scores;
    private Map<Node, GrowShrinkTree> gsts;
    private int numStarts;
    private Knowledge knowledge;


    public GraspNew(Score score) {
        this.score = score;
        this.variables = score.getVariables();
        this.parents = new HashMap<>();
        this.scores = new HashMap<>();

        for (Node node : this.variables) {
            this.parents.put(node, new HashSet<>());
        }

        this.numStarts = 1;
    }

    @Override
    public void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts) {

        // TODO

    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    private double update(List<Node> prefix, List<Node> suborder) {
        double score = 0;

        Iterator<Node> itr = suborder.iterator();
        Set<Node> Z = new HashSet<>(prefix);
        while (itr.hasNext()) {
            Node x = itr.next();
            parents.get(x).clear();
            scores.put(x, gsts.get(x).trace(new HashSet<>(Z), parents.get(x)));
            score += scores.get(x);
            Z.add(x);
        }

        return score;
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public Map<Node, Set<Node>> getParents() {
        return parents;
    }

    @Override
    public Score getScore() {
        return score;
    }

}
