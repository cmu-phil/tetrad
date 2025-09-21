package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.Collection;
import java.util.Objects;

/**
 * Adapter: use BasisFunctionBlocksBicScore as a CAM local scorer.
 * This lets CAM and your BF-based searches share the *same* BIC score.
 */
public final class CamBasisFunctionBicScorer implements AdditiveLocalScorer {

    private final BasisFunctionBlocksBicScore bf;
    private final DataSet data;

    public CamBasisFunctionBicScorer(DataSet data, int degree) {
        this.data = Objects.requireNonNull(data, "data");
        this.bf = new BasisFunctionBlocksBicScore(data, degree);
    }

    @Override
    public double localScore(Node y, Collection<Node> parents) {
        return bf.localScore(y, parents.stream().toList());
    }

    @Override
    public double localScore(int yIndex, int... parentIdxs) {
        return bf.localScore(yIndex, parentIdxs);
    }

    @Override
    public AdditiveLocalScorer setPenaltyDiscount(double c) {
        bf.setPenaltyDiscount(c);
        return this;
    }

    @Override
    public AdditiveLocalScorer setRidge(double r) {
        bf.setRidge(r);
        return this;
    }

    /** Optional accessors if you want to inspect embedding/blocks. */
    public BasisFunctionBlocksBicScore getInner() { return bf; }
    public DataSet getData() { return data; }
}