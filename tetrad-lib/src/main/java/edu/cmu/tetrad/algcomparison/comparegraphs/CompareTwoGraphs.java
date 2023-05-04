package edu.cmu.tetrad.algcomparison.comparegraphs;

import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

public class CompareTwoGraphs {
    public enum CompareTo {DAG, CPDAG, PAG}
//    public enum ComparisonType {STATS_LIST, }

    private final Graph target;
    private final Graph reference;
    private List<Statistic> statistics;

    private CompareTo compareTo = CompareTo.DAG;

    public CompareTwoGraphs(Graph target, Graph reference) {
        this.target = target;
        this.reference = reference;
    }

    public void addStatistic(Statistic statistic) {
        this.statistics.add(statistic);
    }

    public void setGraphComparedTo(CompareTo compareTo) {
        this.compareTo = compareTo;
    }


}
