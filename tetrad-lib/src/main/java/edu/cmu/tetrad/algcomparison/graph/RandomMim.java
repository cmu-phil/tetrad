package edu.cmu.tetrad.algcomparison.graph;

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
        String latentGroupSpecs = parameters.getString("mimLatentGroupSpecs", "5:5(1)");
        int numStructuralEdges = parameters.getInt("mimNumStructuralEdges", 3);
        int numLatentMeasuredImpureParents = parameters.getInt("mimLatentMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureParents = parameters.getInt("mimMeasuredMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureAssociations = parameters.getInt("mimMeasuredMeasuredImpureAssociations", 0);

        List<edu.cmu.tetrad.graph.RandomMim.LatentGroupSpec> specs = edu.cmu.tetrad.graph.RandomMim.parseLatentGroupSpecs(latentGroupSpecs);
        return edu.cmu.tetrad.graph.RandomMim.constructRandomMim(specs, numStructuralEdges,
                numLatentMeasuredImpureParents,
                numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations,
                edu.cmu.tetrad.graph.RandomMim.LatentLinkMode.CARTESIAN_PRODUCT,
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
        parameters.add("mimLatentGroupSpecs");
//        parameters.add("mimNumStructuralNodes");
        parameters.add("mimNumStructuralEdges");
//        parameters.add("mimMeasurementModelDegree");
        parameters.add("mimLatentMeasuredImpureParents");
        parameters.add("mimMeasuredMeasuredImpureParents");
        parameters.add("mimMeasuredMeasuredImpureAssociations");
        return parameters;
    }
}
