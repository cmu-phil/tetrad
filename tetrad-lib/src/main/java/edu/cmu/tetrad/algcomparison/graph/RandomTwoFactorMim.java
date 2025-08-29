package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.graph.DataGraphUtilsFlexMim;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

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
public class RandomTwoFactorMim implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the RandomTwoFactorMim.
     */
    public RandomTwoFactorMim() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        int numStructuralNodes = parameters.getInt("mimNumStructuralNodes", 3);
        int maxStructuralEdges = parameters.getInt("mimNumStructuralEdges", 3);
        int numChildrenPerGroup = parameters.getInt("mimNumChildrenPerGroup", 3);
        int numLatentMeasuredImpureParents = parameters.getInt("mimLatentMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureParents = parameters.getInt("mimMeasuredMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureAssociations = parameters.getInt("mimMeasuredMeasuredImpureAssociations", 0);

        DataGraphUtilsFlexMim.LatentGroupSpec spec = new DataGraphUtilsFlexMim.LatentGroupSpec(
                numStructuralNodes, 3, numChildrenPerGroup);
        return DataGraphUtilsFlexMim.randomMimGeneral(List.of(spec), maxStructuralEdges,
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
        parameters.add("mimNumStructuralNodes");
        parameters.add("mimNumStructuralEdges");
        parameters.add("mimNumChildrenPerGroup");
        parameters.add("mimLatentMeasuredImpureParents");
        parameters.add("mimMeasuredMeasuredImpureParents");
        parameters.add("mimMeasuredMeasuredImpureAssociations");
        return parameters;
    }
}
