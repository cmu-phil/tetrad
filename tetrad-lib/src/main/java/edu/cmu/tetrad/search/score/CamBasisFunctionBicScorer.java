package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Adapter: use BasisFunctionBlocksBicScore as a CAM local scorer.
 * This lets CAM and your BF-based searches share the *same* BIC score.
 */
@Deprecated
public final class CamBasisFunctionBicScorer implements AdditiveLocalScorer {

    private final BasisFunctionBicScore bf;
    private final DataSet data;
    private final List<Node> variables;

    public CamBasisFunctionBicScorer(DataSet data, int degree) {
        this.data = Objects.requireNonNull(data, "data");
        int basisType = 1;
        this.bf = new BasisFunctionBicScore(data, degree, basisType);
        this.variables = data.getVariables();
    }

    @Override
    public double localScore(Node y, Collection<Node> parents) {
        int i = variables.indexOf(y);

        int[] parentIndices = parents.stream()
                .mapToInt(variables::indexOf)
                .toArray();

        return bf.localScore(i, parentIndices);
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
//        bf.setRidge(r);
        return this;
    }

    /** Optional accessors if you want to inspect embedding/blocks. */
//    public BasisFunctionBlocksBicScore getInner() { return bf; }
    public DataSet getData() { return data; }
}