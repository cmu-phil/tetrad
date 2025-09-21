package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.utils.Embedding;

import java.util.*;

/**
 * CAM-style additive BIC scorer for a single node given a candidate parent set. Implements: Y ~ sum_k Phi(X_k)  (main
 * effects only; no interactions).
 * <p>
 * Internally, we build one global embedding via Embedding, then use BlocksBicScore restricted to the blocks of the
 * chosen parents.
 */
public final class CamAdditiveBic implements AdditiveLocalScorer {

    private final DataSet raw;
    private final List<Node> variables;
    private final Embedding.EmbeddedData emb;
    private final List<List<Integer>> blocks; // one block per original variable, main-effects basis only
    private final DataSet embedded;
    private final BlocksBicScore blocksBic;

    // knobs (CAM defaults)
    private final int degree;
    private int includeIntercept = 2;
    private int standardize = 1;
    private double ridge = 0.0;
    private double penaltyDiscount = 1.0;

    public CamAdditiveBic(DataSet raw, int degree) {
        if (degree < 0) throw new IllegalArgumentException("degree >= 0 required");
        this.raw = Objects.requireNonNull(raw);
        this.variables = new ArrayList<>(raw.getVariables());
        this.degree = degree;

        // Build embedding once
        this.emb = Objects.requireNonNull(
                Embedding.getEmbeddedData(raw, degree, includeIntercept, standardize),
                "Embedding.getEmbeddedData returned null");
        this.embedded = emb.embeddedData();

        this.blocks = new ArrayList<>(raw.getNumColumns());
        for (int j = 0; j < raw.getNumColumns(); j++) {
            this.blocks.add(emb.embedding().get(j));
        }
        this.blocksBic = new BlocksBicScore(new BlockSpec(embedded, blocks, variables));
        this.blocksBic.setRidge(ridge);
        this.blocksBic.setPenaltyDiscount(penaltyDiscount);
    }

    public void setIncludeIntercept(int v) {
        this.includeIntercept = v;
    }

    public void setStandardize(int v) {
        this.standardize = v;
    }

    public AdditiveLocalScorer setRidge(double v) {
        this.ridge = v;
        blocksBic.setRidge(v);
        return this;
    }

    public AdditiveLocalScorer setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
        blocksBic.setPenaltyDiscount(c);
        return this;
    }

    /**
     * Return BIC(Y | Pa) where Pa ⊆ variables.
     */
    public double localScore(Node y, Collection<Node> parents) {
        return blocksBic.localScore(y, List.copyOf(parents));
    }

    /**
     * Convenience by index.
     */
    public double localScore(int yIndex, int... parentIdxs) {
        return blocksBic.localScore(yIndex, parentIdxs);
    }

    /**
     * Total CAM score for a permutation π: sum_y BIC( y | {π-predecessors} ).
     */
    public double permutationScore(List<Node> order) {
        Map<Node, Integer> pos = new HashMap<>();
        for (int i = 0; i < order.size(); i++) pos.put(order.get(i), i);

        double sum = 0.0;
        for (int i = 0; i < order.size(); i++) {
            Node y = order.get(i);
            List<Node> preds = new ArrayList<>();
            for (int j = 0; j < i; j++) preds.add(order.get(j));
            sum += localScore(y, preds);
        }
        return sum;
    }

    public List<Node> getVariables() {
        return variables;
    }

    public int getDegree() {
        return degree;
    }
}