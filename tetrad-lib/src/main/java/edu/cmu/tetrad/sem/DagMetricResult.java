package edu.cmu.tetrad.sem;

/**
 * Represents the result of a metric related to a Directed Acyclic Graph (DAG),
 * including its name, value, an associated note, and an indication of whether
 * a higher or lower value is considered better.
 *
 * This record provides a structured way to represent and encapsulate metrics
 * with additional context about their evaluation criteria.
 *
 * @param name The name of the metric being evaluated.
 * @param value The numerical value of the metric.
 * @param note Additional descriptive information or observations about the metric.
 * @param better Indicates whether higher, lower, or neither is considered better for the metric.
 */
public record DagMetricResult(String name, double value, String note, Better better) {

    /**
     * Represents the evaluation criteria for a metric, describing whether a higher
     * value, a lower value, or neither is considered "better" for the given context.
     *
     * This enum facilitates the standardization of metric evaluation, ensuring clear
     * communication of optimization goals.
     */
    public enum Better {
        /**
         * Indicates that a higher value is considered preferable in the context of metric evaluation.
         *
         * This constant is used to specify that an increase in the metric value corresponds
         * to a more favorable outcome, aligning optimization practices with the intended goal.
         */
        HIGHER,
        /**
         * Indicates that a lower value is considered preferable in the context of metric evaluation.
         *
         * This constant is used to specify that a decrease in the metric value corresponds
         * to a more favorable outcome, supporting optimization practices where minimizing
         * the metric is the intended objective.
         */
        LOWER,
        /**
         * Indicates that the metric's value is not applicable or does not influence
         * the evaluation criteria in any meaningful way.
         *
         * This constant is used to represent cases where the concept of "better" is
         * irrelevant or cannot be defined, such as when the metric is purely informational
         * or outside the scope of optimization goals.
         */
        NA }

    /**
     * Constructs an instance of DagMetricResult with the specified name, value, and note.
     * Sets the evaluation criteria to {@code Better.NA} by default.
     *
     * @param name The name of the metric being evaluated.
     * @param value The numerical value of the metric.
     * @param note Additional descriptive information or observations about the metric.
     */
    public DagMetricResult(String name, double value, String note) {
        this(name, value, note, Better.NA);
    }
}