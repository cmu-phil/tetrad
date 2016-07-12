package edu.cmu.tetrad.algcomparison;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jdramsey on 7/12/16.
 */
public class Statistics {
    private List<Statistic> statistics = new ArrayList<>();
    private Map<Statistic, Double> weights = new HashMap<>();

    public Statistics(){}

    public void add(Statistic statistic) {
        this.statistics.add(statistic);
    }

    public void setWeight(String abbrebiation, double weight) {
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

    public List<Statistic> getStatistics() {
        return new ArrayList<>(statistics);
    }

    public double getWeight(Statistic statistic) {
        if (weights.keySet().contains(statistic)) {
            return weights.get(statistic);
        } else {
            return 0.0;
        }
    }

    public int size() {
        return statistics.size();
    }
}
