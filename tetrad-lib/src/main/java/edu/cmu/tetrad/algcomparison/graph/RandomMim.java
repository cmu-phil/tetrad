package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.graph.DataGraphUtilsFlexMim;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Creates a random graph by adding forward edges.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RandomMim implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the RandomTwoFactorMim.
     */
    public RandomMim() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        String latentGroupSpecs = parameters.getString("latentGroupSpecs", "5:5(1)");
//        int numStructuralNodes = parameters.getInt("numStructuralNodes", 3);
        int maxStructuralEdges = parameters.getInt("numStructuralEdges", 3);
//        int measurementModelDegree = parameters.getInt("measurementModelDegree", 3);
        int numLatentMeasuredImpureParents = parameters.getInt("latentMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureParents = parameters.getInt("measuredMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureAssociations = parameters.getInt("measuredMeasuredImpureAssociations", 0);

        List<DataGraphUtilsFlexMim.LatentGroupSpec> specs = DataGraphUtilsFlexMim.parseLatentGroupSpecs(latentGroupSpecs);
//        DataGraphUtilsFlexMim.LatentGroupSpec spec = new DataGraphUtilsFlexMim.LatentGroupSpec(
//                numStructuralNodes, 3, measurementModelDegree);
        return DataGraphUtilsFlexMim.randomMimGeneral(specs, maxStructuralEdges,
                numLatentMeasuredImpureParents,
                numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations,
                DataGraphUtilsFlexMim.LatentLinkMode.CARTESIAN_PRODUCT,
                new Random());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Random two-factor MIM (Multiple Indicator Model)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("latentGroupSpecs");
//        parameters.add("numStructuralNodes");
        parameters.add("numStructuralEdges");
        parameters.add("measurementModelDegree");
        parameters.add("latentMeasuredImpureParents");
        parameters.add("measuredMeasuredImpureParents");
        parameters.add("measuredMeasuredImpureAssociations");
        return parameters;
    }
}
