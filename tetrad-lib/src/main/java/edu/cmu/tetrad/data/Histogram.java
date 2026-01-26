package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;

import java.util.*;

/**
 * Model for a conditional histogram for mixed continuous and discrete variables.
 *
 * Drop-in replacement with optional x-axis bounds for continuous targets.
 *
 * @author josephramsey
 */
public class Histogram {

    private final DataSet dataSet;
    private final boolean removeZeroPointsPerPlot;

    private Node target;
    private int numBins = 10;

    private Map<Node, double[]> continuousIntervals;
    private Map<Node, Integer> discreteValues;

    // NEW: optional bounds for continuous targets
    private Double continuousBoundLow = null;
    private Double continuousBoundHigh = null;
    private boolean ignoreOutsideBounds = true;

    // Cache of last "display" min/max used for continuous histograms
    private double lastDisplayMin = Double.NaN;
    private double lastDisplayMax = Double.NaN;

    /**
     * Constructs a Histogram object for the given data set. This constructor ensures that the data set is not null
     * and contains variables. It optionally removes zero data points per plot.
     *
     * @param dataSet the data set used to compute the histogram; must not be null and must contain variables
     * @param target the target variable for which the histogram will be created
     * @param removeZeroPointsPerPlot a flag indicating whether zero data points should be removed per plot
     * @throws NullPointerException if the provided dataSet is null
     * @throws IllegalArgumentException if the provided dataSet contains no variables
     */
    public Histogram(DataSet dataSet, String target, boolean removeZeroPointsPerPlot) {
        if (dataSet == null) throw new NullPointerException("Given dataSet must not be null.");
        if (dataSet.getVariables().isEmpty()) {
            throw new IllegalArgumentException("Can't do histograms for an empty data set.");
        }

        this.dataSet = dataSet;
        this.removeZeroPointsPerPlot = removeZeroPointsPerPlot;
        setTarget(target);
    }

    /**
     * Adds a continuous conditioning variable with a specified range.
     * The conditioning variable is constrained to the interval [low, high].
     * If the variable is already conditioned, an exception is thrown.
     *
     * @param variable the name of the variable to be conditioned; must correspond to a continuous variable in the data set
     * @param low the lower bound of the conditioning interval (exclusive)
     * @param high the upper bound of the conditioning interval (exclusive); must be greater than the {@code low} value
     * @throws IllegalArgumentException if {@code low} is not less than {@code high}, or if the specified variable
     * is not continuous, or if the variable is already a conditioning variable
     */
    public void addConditioningVariable(String variable, double low, double high) {
        if (!(low < high)) throw new IllegalArgumentException("Low must be less than high: " + low + " >= " + high);

        Node node = this.dataSet.getVariable(variable);
        if (!(node instanceof ContinuousVariable)) throw new IllegalArgumentException("Variable must be continuous.");
        if (this.continuousIntervals.containsKey(node)) {
            throw new IllegalArgumentException("Please remove conditioning variable first.");
        }

        this.continuousIntervals.put(node, new double[]{low, high});
    }

    /**
     * Adds a discrete conditioning variable with a specified value.
     * The variable must be discrete. If the variable is not discrete, an {@code IllegalArgumentException} is thrown.
     *
     * @param variable the name of the variable to be conditioned; must correspond to a discrete variable in the data set
     * @param value the integer value associated with the discrete conditioning variable
     * @throws IllegalArgumentException if the specified variable is not discrete
     */
    public void addConditioningVariable(String variable, int value) {
        Node node = this.dataSet.getVariable(variable);
        if (!(node instanceof DiscreteVariable)) throw new IllegalArgumentException("Variable must be discrete.");
        this.discreteValues.put(node, value);
    }

    /**
     * Removes a conditioning variable from the histogram.
     * This method deletes any existing conditioning rules for the specified variable,
     * whether they are continuous intervals or discrete values. If the variable is not a
     * conditioning variable, an {@code IllegalArgumentException} is thrown.
     *
     * @param variable the name of the variable to be removed from the conditioning set; must exist in the data set
     * @throws IllegalArgumentException if the specified variable is not a conditioning variable
     */
    public void removeConditioningVariable(String variable) {
        Node node = this.dataSet.getVariable(variable);
        if (!(this.continuousIntervals.containsKey(node) || this.discreteValues.containsKey(node))) {
            throw new IllegalArgumentException("Not a conditioning node: " + variable);
        }
        this.continuousIntervals.remove(node);
        this.discreteValues.remove(node);
    }

    /**
     * Sets the number of bins to be used in the histogram. This determines how the data is divided
     * into intervals for counting. The number of bins must be a positive integer greater than or
     * equal to 1. If the target variable is discrete, this operation is not allowed.
     *
     * @param numBins the number of bins to divide the histogram into; must be a positive integer
     *                and greater than or equal to 1
     * @throws IllegalArgumentException if {@code numBins} is less than 1
     * @throws IllegalArgumentException if the target variable is discrete
     */
    public void setNumBins(int numBins) {
        if (numBins < 1) throw new IllegalArgumentException("numBins must be >= 1.");
        if (this.target instanceof DiscreteVariable) {
            throw new IllegalArgumentException("Can't set number of bins for a discrete target.");
        }
        this.numBins = numBins;
    }

    /**
     * Sets the continuous bounds for the target variable along with an option to ignore data outside these bounds.
     * The bounds apply only if the target variable is continuous and valid.
     *
     * @param low the lower bound of the range (inclusive); must be less than {@code high}
     * @param high the upper bound of the range (inclusive); must be greater than {@code low}
     * @param ignoreOutside a boolean specifying whether to ignore data points outside the specified bounds
     * @throws IllegalArgumentException if {@code low} is not less than {@code high}
     * @throws IllegalArgumentException if the target variable is not continuous
     */
    public void setContinuousBounds(double low, double high, boolean ignoreOutside) {
        if (!(low < high)) throw new IllegalArgumentException("low must be < high.");
        if (this.target instanceof DiscreteVariable) {
            throw new IllegalArgumentException("Continuous bounds apply only to continuous targets.");
        }
        this.continuousBoundLow = low;
        this.continuousBoundHigh = high;
        this.ignoreOutsideBounds = ignoreOutside;
    }

    /**
     * Clears the continuous bounds applied to the histogram's target variable.
     *
     * This method removes any previously set continuous bounds by resetting
     * the lower bound, upper bound, and the "ignore outside bounds" flag
     * to their default values. Specifically:
     * - The lower bound (`continuousBoundLow`) is set to null.
     * - The upper bound (`continuousBoundHigh`) is set to null.
     * - The flag indicating whether to ignore data outside the bounds
     *   (`ignoreOutsideBounds`) is set to true.
     *
     * After calling this method, the histogram no longer uses any continuous
     * bounds for filtering or processing the target variable's data.
     */
    public void clearContinuousBounds() {
        this.continuousBoundLow = null;
        this.continuousBoundHigh = null;
        this.ignoreOutsideBounds = true;
    }

    /**
     * Checks if both the lower and upper bounds for a continuous interval are set.
     *
     * This method evaluates whether the continuous bounds, defined by
     * {@code continuousBoundLow} and {@code continuousBoundHigh}, are
     * both non-null. It returns {@code true} if both bounds are set,
     * indicating that the histogram is operating with a defined range
     * for continuous values.
     *
     * @return {@code true} if the lower and upper bounds for a continuous
     *         interval are both non-null, {@code false} otherwise.
     */
    public boolean hasContinuousBounds() {
        return continuousBoundLow != null && continuousBoundHigh != null;
    }

    /**
     * Retrieves the minimum value used for display purposes in a continuous histogram.
     * This method first checks if continuous bounds are defined and returns the lower bound
     * if they are present. If no continuous bounds are set and a previously calculated display
     * minimum exists, it returns that value. Otherwise, it defaults to the unconditioned
     * minimum value of the data.
     *
     * @return the minimum value for display, determined based on continuous bounds or
     *         unconditioned data, as a double.
     */
    public double getDisplayMin() {
        if (hasContinuousBounds()) return continuousBoundLow;
        // If we've computed once, lastDisplayMin is helpful; otherwise fall back to unconditioned min.
        if (!Double.isNaN(lastDisplayMin)) return lastDisplayMin;
        return getMin();
    }

    /**
     * Retrieves the maximum value used for display purposes in a continuous histogram.
     * This method determines the display maximum by evaluating the following, in order:
     * - If continuous bounds are defined, the upper bound is returned.
     * - If a previously calculated display maximum exists, it is returned.
     * - Otherwise, the maximum value from unconditioned data is returned.
     *
     * @return the maximum value for display, based on continuous bounds, previously
     *         calculated display value, or unconditioned data, as a double.
     */
    public double getDisplayMax() {
        if (hasContinuousBounds()) return continuousBoundHigh;
        if (!Double.isNaN(lastDisplayMax)) return lastDisplayMax;
        return getMax();
    }

    /**
     * Calculates and returns frequency counts for the data associated with the target variable.
     * The method handles both continuous and discrete variable types.
     *
     * For continuous variables:
     * - The data is divided into bins based on the specified number of bins and a computed range.
     * - Special handling is applied for degenerate ranges (where min and max are identical).
     * - Values outside the bounds may be ignored based on configuration.
     *
     * For discrete variables:
     * - Counts are returned for each category based on the variable's number of categories.
     * - Only valid category indices are counted.
     *
     * @return An array of integers representing the frequency counts. For continuous variables,
     *         the size of the array is equal to the number of bins. For discrete variables,
     *         the size of the array is equal to the number of categories of the target variable.
     *         If no valid data is available, an array of zeros is returned.
     * @throws IllegalArgumentException if the target variable type is unrecognized.
     */
    public int[] getFrequencies() {
        if (this.target instanceof ContinuousVariable) {
            List<Double> rawData = getConditionedDataContinuous();
            rawData = removeZeroPointsPerPlot(rawData);

            if (rawData.isEmpty()) {
                lastDisplayMin = Double.NaN;
                lastDisplayMax = Double.NaN;
                return new int[this.numBins];
            }

            // Decide binning range
            final double min;
            final double max;

            if (hasContinuousBounds()) {
                min = continuousBoundLow;
                max = continuousBoundHigh;
            } else {
                double[] d = asDoubleArray(rawData);
                min = StatUtils.min(d);
                max = StatUtils.max(d);
            }

            lastDisplayMin = min;
            lastDisplayMax = max;

            // Degenerate range: all values identical (or bounds extremely tight).
            if (!(min < max)) {
                int[] counts = new int[this.numBins];
                // Put everything into last bin (or first—either is fine; last tends to look better)
                int bin = this.numBins - 1;
                for (double v : rawData.stream().mapToDouble(Double::doubleValue).toArray()) {
                    if (hasContinuousBounds() && ignoreOutsideBounds && (v < min || v > max)) continue;
                    counts[bin]++;
                }
                return counts;
            }

            final int[] counts = new int[this.numBins];
            final double width = (max - min) / this.numBins;

            // Fast binning with correct edge handling:
            // - v < min or v > max ignored if ignoreOutsideBounds
            // - v == max goes to last bin
            for (double v : rawData.stream().mapToDouble(Double::doubleValue).toArray()) {
                if (Double.isNaN(v)) continue;

                if (hasContinuousBounds() && ignoreOutsideBounds) {
                    if (v < min || v > max) continue;
                }

                // If bounds not set, we still should ignore extreme NaNs; otherwise include all.
                // Compute bin index
                int bin;
                if (v == max) {
                    bin = this.numBins - 1;
                } else {
                    bin = (int) ((v - min) / width);
                    if (bin < 0) {
                        if (hasContinuousBounds() && ignoreOutsideBounds) continue;
                        bin = 0;
                    } else if (bin >= this.numBins) {
                        if (hasContinuousBounds() && ignoreOutsideBounds) continue;
                        bin = this.numBins - 1;
                    }
                }

                counts[bin]++;
            }

            return counts;
        }

        if (this.target instanceof DiscreteVariable var) {
            List<Integer> rawData = getConditionedDataDiscrete();
            int[] counts = new int[var.getNumCategories()];

            int[] data = rawData.stream().mapToInt(Integer::intValue).toArray();
            for (int value : data) {
                if (value >= 0 && value < counts.length) counts[value]++;
            }

            return counts;
        }

        throw new IllegalArgumentException("Unrecognized variable type.");
    }

    private List<Double> removeZeroPointsPerPlot(List<Double> data) {
        if (!removeZeroPointsPerPlot) return data;

        List<Double> out = new ArrayList<>(data.size());
        for (double d : data) {
            if (d != 0.0) out.add(d);
        }
        return out;
    }

    /**
     * Computes and returns the maximum value from a list of unconditioned
     * continuous data points. The method retrieves the data, converts
     * it into an array of doubles, and calculates the maximum value
     * contained within the array.
     *
     * @return the maximum value in the unconditioned continuous data
     */
    public double getMax() {
        List<Double> uncond = getUnconditionedDataContinuous();
        double[] d = asDoubleArray(uncond);
        return StatUtils.max(d);
    }

    /**
     * Calculates and returns the minimum value from a list of unconditioned continuous data.
     *
     * @return The minimum value from the unconditioned continuous data as a double.
     */
    public double getMin() {
        List<Double> uncond = getUnconditionedDataContinuous();
        double[] d = asDoubleArray(uncond);
        return StatUtils.min(d);
    }

    /**
     * Returns the size of the conditioned data list.
     *
     * @return the number of elements in the conditioned data list
     */
    public int getN() {
        List<Double> conditioned = getConditionedDataContinuous();
        return conditioned.size();
    }

    /**
     * Retrieves the continuous data for a specified variable from the dataset.
     *
     * @param variable the name of the variable for which continuous data is to be retrieved
     * @return an array of double values representing the continuous data for the specified variable
     */
    public double[] getContinuousData(String variable) {
        int index = this.dataSet.getColumn(this.dataSet.getVariable(variable));
        List<Double> data = new ArrayList<>(this.dataSet.getNumRows());
        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            data.add(this.dataSet.getDouble(i, index));
        }
        return asDoubleArray(data);
    }

    /**
     * Retrieves the current DataSet instance associated with this object.
     *
     * @return the current DataSet instance
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * Retrieves the name of the target associated with this instance.
     *
     * @return the name of the target as a String.
     */
    public String getTarget() {
        return this.target.getName();
    }

    private void setTarget(String target) {
        Node _target = (target == null) ? this.dataSet.getVariable(0) : this.dataSet.getVariable(target);
        this.target = _target;
        this.continuousIntervals = new HashMap<>();
        this.discreteValues = new HashMap<>();
        // Do NOT clear bounds here—callers may want bounds to persist across target changes;
        // if you prefer the old behavior, uncomment the next line.
        // clearContinuousBounds();
    }

    /**
     * Retrieves the target node.
     *
     * @return the target node of type Node.
     */
    public Node getTargetNode() {
        return this.target;
    }

    private double[] asDoubleArray(List<Double> data) {
        double[] out = new double[data.size()];
        for (int i = 0; i < data.size(); i++) out[i] = data.get(i);
        return out;
    }

    private List<Double> getUnconditionedDataContinuous() {
        int index = this.dataSet.getColumn(this.target);
        List<Double> data = new ArrayList<>(this.dataSet.getNumRows());
        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            data.add(this.dataSet.getDouble(i, index));
        }
        return data;
    }

    private List<Double> getConditionedDataContinuous() {
        List<Integer> rows = getConditionedRows();
        int index = this.dataSet.getColumn(this.target);

        List<Double> data = new ArrayList<>(rows.size());
        for (Integer row : rows) {
            data.add(this.dataSet.getDouble(row, index));
        }
        return data;
    }

    private List<Integer> getConditionedDataDiscrete() {
        List<Integer> rows = getConditionedRows();
        int index = this.dataSet.getColumn(this.target);

        List<Integer> data = new ArrayList<>(rows.size());
        for (Integer row : rows) {
            data.add(this.dataSet.getInt(row, index));
        }
        return data;
    }

    // Returns the rows in the data that satisfy the conditioning constraints.
    private List<Integer> getConditionedRows() {
        List<Integer> rows = new ArrayList<>();

        I:
        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            for (Node node : this.continuousIntervals.keySet()) {
                double[] range = this.continuousIntervals.get(node);
                int index = this.dataSet.getColumn(node);
                double value = this.dataSet.getDouble(i, index);
                if (!(value >= range[0] && value <= range[1])) {
                    continue I;
                }
            }

            for (Node node : this.discreteValues.keySet()) {
                int value = this.discreteValues.get(node);
                int index = this.dataSet.getColumn(node);
                int _value = this.dataSet.getInt(i, index);
                if (value != _value) {
                    continue I;
                }
            }

            rows.add(i);
        }

        return rows;
    }
}