package edu.cmu.tetrad.algcomparison.comparegraphs;

import edu.cmu.tetrad.algcomparison.statistic.Statistic;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

public class CompareTwoGraphs {


    private final Graph target;
    private final Graph reference;
    private List<Statistic> statistics;
    private CompareToType compareToType = CompareToType.DAG;
    private ComparisonType comparisontype = ComparisonType.STATS_LIST;

    public CompareTwoGraphs(Graph target, Graph reference) {
        this.target = target;
        this.reference = reference;
    }

    public void setCompareToType(CompareToType compareTo) {
        this.compareToType = compareTo;
    }

    public void setComparisontype(ComparisonType comparisontype) {
        this.comparisontype = comparisontype;
    }

    public void addStatistic(Statistic statistic) {
        this.statistics.add(statistic);
    }

    /**
     * Returns a string comparing 'target' to 'reference' using the given comparison method. If type comparison method
     * is 'stats list', a list of comparison statistics will be printed out using the stats added via the 'addStatistic'
     * method. These stats will not be used for the other comparison methods. The type of graph compared to (DAG, CPDAG,
     * PAG) can be set using the 'setCompareTotype' method.
     *
     * @return This string, which can be printed.
     * @see #setCompareToType(CompareToType)
     * @see #setComparisontype(ComparisonType)
     * @see #addStatistic(Statistic)
     */
    public String getComparisonString() {
        switch (comparisontype) {
            case STATS_LIST:
                return statsListString();
            case EDGEWISE:
                return edgewiseString();
            case MISCLASSIFICATTONS:
                return misclassificationsString();
            default:
                throw new IllegalStateException("Unsupported comparison type: " + comparisontype);
        }
    }

    private String statsListString() {
        return "foo";
    }

    private String edgewiseString() {
        return "foo";
    }

    private String misclassificationsString() {
        return "foo";
    }

    public enum CompareToType {DAG, CPDAG, PAG}

    public enum ComparisonType {STATS_LIST, EDGEWISE, MISCLASSIFICATTONS}
}
