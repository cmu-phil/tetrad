package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.MixedBicScore;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.List;
import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedFgsMixedScore implements Algorithm {
    public Graph search(DataSet Dk, Map<String, Number> parameters) {
        MixedBicScore score = new MixedBicScore(Dk);
        Fgs fgs = new Fgs(score);
        fgs.setDepth(parameters.get("fgsDepth").intValue());
        return fgs.search();
    }


    @Override
    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    @Override
    public String getDescription() {
        return "FGS using a mixed BIC score";
    }
}
