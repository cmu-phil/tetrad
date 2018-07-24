package edu.cmu.tetrad.algcomparison.statistic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A list of statistics and their utility weights.
 *
 * @author jdramsey
 */
public class Statistics {
    private List<Statistic> statistics = new ArrayList<>();
    private Map<Statistic, Double> weights = new HashMap<>();

    public Statistics() {
    }

    /**
     * Adds a statistic.
     *
     * @param statistic The statistic to add.
     */
    public void add(Statistic statistic) {
        this.statistics.add(statistic);
    }

    /**
     * Sets the utility weight of the statistic by the given name.
     *
     * @param abbrebiation The abbreviation set in the statistic.
     * @param weight       The utility weight for that statistic.
     */
    public void setWeight(String abbrebiation, double weight) {
        if (weight < 0 || weight > 1) throw new IllegalArgumentException("Weight must be in [0, 1]: " + weight);

        boolean set = false;

        for (Statistic stat : statistics) {
            if (stat.getAbbreviation().equals(abbrebiation)) {
                this.weights.put(stat, weight);
                set = true;
            }
        }

        if (!set) {
            throw new IllegalArgumentException("No statistic has been added with that abbreviation: "
                    + abbrebiation);
        }
    }

    /**
     * Return the list of statistics.
     *
     * @return A copy of this list, in the order added.
     */
    public List<Statistic> getStatistics() {
        return new ArrayList<>(statistics);
    }

    /**
     * The utility weight for the statistic.
     *
     * @param statistic The statistic.
     * @return The utility weight for it.
     */
    public double getWeight(Statistic statistic) {
        if (weights.keySet().contains(statistic)) {
            return weights.get(statistic);
        } else {
            return 0.0;
        }
    }

    /**
     * The number of statistics.
     *
     * @return This number.
     */
    public int size() {
        return statistics.size();
    }


}
