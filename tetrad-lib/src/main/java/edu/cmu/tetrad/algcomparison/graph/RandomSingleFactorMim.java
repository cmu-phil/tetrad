package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a random graph by adding forward edges.
 *
 * @author jdramsey
 */
public class RandomSingleFactorMim implements RandomGraph {
    static final long serialVersionUID = 23L;

    @Override
    public Graph createGraph(final Parameters parameters) {
        final int numStructuralNodes = parameters.getInt("numStructuralNodes", 3);
        final int maxStructuralEdges = parameters.getInt("numStructuralEdges", 3);
        final int measurementModelDegree = parameters.getInt("measurementModelDegree", 3);
        final int numLatentMeasuredImpureParents = parameters.getInt("latentMeasuredImpureParents", 0);
        final int numMeasuredMeasuredImpureParents = parameters.getInt("measuredMeasuredImpureParents", 0);
        final int numMeasuredMeasuredImpureAssociations = parameters.getInt("measuredMeasuredImpureAssociations", 0);

        return DataGraphUtils.randomSingleFactorModel(numStructuralNodes, maxStructuralEdges, measurementModelDegree,
                numLatentMeasuredImpureParents, numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations);
    }

    @Override
    public String getDescription() {
        return "Random single-factor MIM (Multiple Indicator Model)";
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>();
        parameters.add("numStructuralNodes");
        parameters.add("numStructuralEdges");
        parameters.add("measurementModelDegree");
        parameters.add("latentMeasuredImpureParents");
        parameters.add("measuredMeasuredImpureParents");
        parameters.add("measuredMeasuredImpureAssociations");
        return parameters;
    }
}
