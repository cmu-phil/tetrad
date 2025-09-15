package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;

import java.util.ArrayList;
import java.util.List;

public final class HybridCgVars {
    private HybridCgVars() {}

    /** Build data variables (ContinuousVariable / DiscreteVariable) from the PM’s schema. */
    public static List<Node> materializeDataVariables(HybridCgPm pm) {
        List<Node> vars = new ArrayList<>();
        Node[] order = pm.getNodes(); // graph nodes (names drive identity)

        for (int i = 0; i < order.length; i++) {
            String name = order[i].getName();
            if (pm.isDiscrete(i)) {
                List<String> cats = pm.getCategories(i); // already in the PM’s order
                vars.add(new DiscreteVariable(name, new ArrayList<>(cats)));
            } else {
                vars.add(new ContinuousVariable(name));
            }
        }
        return vars;
    }
}