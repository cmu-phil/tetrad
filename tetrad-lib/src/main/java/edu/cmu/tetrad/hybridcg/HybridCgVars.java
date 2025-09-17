package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;

import java.util.ArrayList;
import java.util.List;

public final class HybridCgVars {
    private HybridCgVars() {
    }

    /**
     * Build data variables (ContinuousVariable / DiscreteVariable) from the PM’s schema.
     *
     * @param pm the HybridCgPm
     * @return the list of data variables
     */
    public static List<Node> materializeDataVariables(HybridCgPm pm) {
        List<Node> vars = new ArrayList<>();
        Node[] order = pm.getNodes(); // graph nodes (names drive identity)

        for (int i = 0; i < order.length; i++) {
            String name = order[i].getName();
            if (pm.isDiscrete(i)) {
                List<String> cats = pm.getCategories(i);
                if (cats == null || cats.isEmpty()) {
                    throw new IllegalStateException("Discrete variable has no categories: " + name);
                }
                vars.add(new DiscreteVariable(name, new ArrayList<>(cats)));
            } else {
                vars.add(new ContinuousVariable(name));
            }
        }
        return vars;
    }
}