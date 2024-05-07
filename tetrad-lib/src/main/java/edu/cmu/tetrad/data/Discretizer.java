///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;

import java.util.*;


/**
 * Discretizes individual columns of discrete or continuous data. Continuous data is discretized by specifying a list of
 * n - 1 cutoffs for n values in the discretized data, with optional string labels for these values. Discrete data is
 * discretized by specifying a mapping from old value names to new value names, the idea being that old values may be
 * merged.
 *
 * @author josephramsey
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class Discretizer {

    /**
     * The dataset to be discretized.
     */
    private final DataSet sourceDataSet;

    /**
     * The discretization specifications for each variable.
     */
    private final Map<Node, DiscretizationSpec> specs;

    /**
     * Whether to copy the variables that are not discretized.
     */
    private boolean variablesCopied = true;

    /**
     * Constructs a new discretizer that discretizes every variable as binary, using evenly distributed values.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public Discretizer(DataSet dataSet) {
        this.sourceDataSet = dataSet;

        this.specs = new HashMap<>();
    }

    /**
     * <p>Constructor for Discretizer.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @param specs   a {@link java.util.Map} object
     */
    public Discretizer(DataSet dataSet, Map<Node, DiscretizationSpec> specs) {
        this.sourceDataSet = dataSet;
        this.specs = specs;
    }

    /**
     * <p>getEqualFrequencyBreakPoints.</p>
     *
     * @param _data              an array of {@link double} objects
     * @param numberOfCategories a int
     * @return an array of {@link double} objects
     */
    public static double[] getEqualFrequencyBreakPoints(double[] _data, int numberOfCategories) {
        double[] data = new double[_data.length];
        System.arraycopy(_data, 0, data, 0, data.length);

        // first sort the data.
        Arrays.sort(data);

        int n = data.length / numberOfCategories;
        double[] breakpoints = new double[numberOfCategories - 1];

        for (int i = 0; i < breakpoints.length; i++) {
            breakpoints[i] = data[n * (i + 1)];
        }

        return breakpoints;
    }

    /**
     * Discretizes the continuous data in the given column using the specified cutoffs and category names. The following
     * scheme is used. If cutoffs[i - 1] &lt; v &lt;= cutoffs[i] (where cutoffs[-1] = negative infinity), then v is
     * mapped to category i. If category names are supplied, the discrete column returned will use these category
     * names.
     *
     * @param cutoffs      The cutoffs used to discretize the data. Should have length c - 1, where c is the number of
     *                     categories in the discretized data.
     * @param variableName the name of the returned variable.
     * @param categories   An optional list of category names; may be null. If this is supplied, the discrete column
     *                     returned will use these category names. If this is non-null, it must have length c, where c
     *                     is the number of categories for the discretized data. If any category names are null, default
     *                     category names will be used for those.
     * @param _data        an array of {@link double} objects
     * @return The discretized column.
     */
    public static Discretization discretize(double[] _data, double[] cutoffs,
                                            String variableName, List<String> categories) {

        if (cutoffs == null) {
            throw new NullPointerException();
        }

        for (int i = 0; i < cutoffs.length - 1; i++) {
            if (!(cutoffs[i] <= cutoffs[i + 1])) {
                System.out.println(
                        "Cutoffs should be in nondecreasing order: "
                        + Arrays.toString(cutoffs)
                );
            }
        }

        if (variableName == null) {
            throw new NullPointerException();
        }

        int numCategories = cutoffs.length + 1;

        if (categories != null && categories.size() != numCategories) {
            throw new IllegalArgumentException("If specified, the list of " +
                                               "categories names must be one longer than the length of " +
                                               "the cutoffs array.");
        }

        DiscreteVariable variable;

        if (categories == null) {
            variable = new DiscreteVariable(variableName, numCategories);
        } else {
            variable = new DiscreteVariable(variableName, categories);
        }

        int[] discreteData = new int[_data.length];

        loop:
        for (int i = 0; i < _data.length; i++) {
            if (Double.isNaN(_data[i])) {
                discreteData[i] = DiscreteVariable.MISSING_VALUE;
                continue;
            }

            for (int j = 0; j < cutoffs.length; j++) {
                if (_data[i] > Double.NEGATIVE_INFINITY
                    && _data[i] < Double.POSITIVE_INFINITY
                    && _data[i] < cutoffs[j]) {
                    discreteData[i] = j;
                    continue loop;
                }
            }

            discreteData[i] = cutoffs.length;
        }

        return new Discretization(variable, discreteData);
    }

    /**
     * Sets the given node to discretized using evenly distributed values using the given number of categories.
     *
     * @param node          a {@link edu.cmu.tetrad.graph.Node} object
     * @param numCategories a int
     */
    public void equalCounts(Node node, int numCategories) {
        if (node instanceof DiscreteVariable) return;

        String name = node.getName();
        int i = this.sourceDataSet.getVariables().indexOf(node);
        double[] data = this.sourceDataSet.getDoubleData().getColumn(i).toArray();
        double[] breakpoints = Discretizer.getEqualFrequencyBreakPoints(data, numCategories);
        List<String> categories = new DiscreteVariable(name, numCategories).getCategories();

        ContinuousDiscretizationSpec spec
                = new ContinuousDiscretizationSpec(breakpoints, categories);
        spec.setMethod(ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES);

        this.specs.put(node, spec);
    }

    /**
     * Sets the given node to discretized using evenly spaced intervals using the given number of categories.
     *
     * @param node          a {@link edu.cmu.tetrad.graph.Node} object
     * @param numCategories a int
     */
    public void equalIntervals(Node node, int numCategories) {
        if (node instanceof DiscreteVariable) return;

        String name = node.getName();
        int i = this.sourceDataSet.getVariables().indexOf(node);
        double[] data = this.sourceDataSet.getDoubleData().getColumn(i).toArray();
//        double[] breakpoints = Discretizer.getEqualFrequencyBreakPoints(data, numCategories);

        double max = StatUtils.max(data);
        double min = StatUtils.min(data);

        double interval = (max - min) / numCategories;

        double[] breakpoints = new double[numCategories - 1];

        for (int g = 0; g < numCategories - 1; g++) {
            breakpoints[g] = min + (g + 1) * interval;
        }

        List<String> categories = new DiscreteVariable(name, numCategories).getCategories();

        ContinuousDiscretizationSpec spec
                = new ContinuousDiscretizationSpec(breakpoints, categories);
        spec.setMethod(ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS);

        this.specs.put(node, spec);
    }

    private boolean isVariablesCopied() {
        return this.variablesCopied;
    }

    /**
     * <p>Setter for the field <code>variablesCopied</code>.</p>
     *
     * @param unselectedVariabledCopied a boolean
     */
    public void setVariablesCopied(boolean unselectedVariabledCopied) {
        this.variablesCopied = unselectedVariabledCopied;
    }

    /**
     * <p>discretize.</p>
     *
     * @return - Discretized dataset.
     */
    public DataSet discretize() {
        // build list of variables
        List<Node> variables = new LinkedList<>();
        Map<Node, Node> replacementMapping = new HashMap<>();
        for (int i = 0; i < this.sourceDataSet.getNumColumns(); i++) {
            Node variable = this.sourceDataSet.getVariable(i);
            if (variable instanceof ContinuousVariable) {
                ContinuousDiscretizationSpec spec = null;
                Node _node = null;

                for (Node node : this.specs.keySet()) {
                    if (node.getName().equals(variable.getName())) {
                        DiscretizationSpec _spec = this.specs.get(node);
                        spec = (ContinuousDiscretizationSpec) _spec;
                        _node = node;
                        break;
                    }
                }

                if (spec != null) {
                    if (spec.getMethod() == ContinuousDiscretizationSpec.NONE) {
                        variables.add(variable);
                    } else {
                        List<String> cats = spec.getCategories();
                        DiscreteVariable var = new DiscreteVariable(variable.getName(), cats);
                        replacementMapping.put(var, _node);
                        variables.add(var);
                    }
                } else if (isVariablesCopied()) {
                    variables.add(variable);
                }
            } else if (variable instanceof DiscreteVariable) {
                DiscreteDiscretizationSpec spec = null;
                Node _node = null;

                for (Node node : this.specs.keySet()) {
                    if (node.getName().equals(variable.getName())) {
                        DiscretizationSpec _spec = this.specs.get(node);
                        spec = (DiscreteDiscretizationSpec) _spec;
                        _node = node;
                        break;
                    }
                }

//                DiscreteDiscretizationSpec spec = (DiscreteDiscretizationSpec) specs.get(variable);
                if (spec != null) {
                    List<String> cats = spec.getCategories();
                    DiscreteVariable var = new DiscreteVariable(_node.getName(), cats);
                    replacementMapping.put(var, _node);
                    variables.add(var);
                } else if (isVariablesCopied()) {
                    variables.add(variable);
                }
            } else if (isVariablesCopied()) {
                variables.add(variable);
            }
        }

        // build new dataset.
        DataSet newDataSet = new BoxDataSet(new VerticalDoubleDataBox(this.sourceDataSet.getNumRows(), variables.size()), variables);
        for (int i = 0; i < newDataSet.getNumColumns(); i++) {
            Node variable = newDataSet.getVariable(i);
            Node sourceVar = replacementMapping.get(variable);
            if (sourceVar != null && this.specs.containsKey(sourceVar)) {
                if (sourceVar instanceof ContinuousVariable) {
                    ContinuousDiscretizationSpec spec = (ContinuousDiscretizationSpec) this.specs.get(sourceVar);
                    double[] breakpoints = spec.getBreakpoints();
                    List<String> categories = spec.getCategories();
                    String name = variable.getName();

                    double[] trimmedData = new double[newDataSet.getNumRows()];
                    int col = newDataSet.getColumn(variable);

                    for (int j = 0; j < this.sourceDataSet.getNumRows(); j++) {
                        trimmedData[j] = this.sourceDataSet.getDouble(j, col);
                    }
                    Discretization discretization = Discretizer.discretize(trimmedData,
                            breakpoints, name, categories);

                    int _col = newDataSet.getColumn(variable);
                    int[] _data = discretization.getData();
                    for (int j = 0; j < _data.length; j++) {
                        newDataSet.setInt(j, _col, _data[j]);
                    }
                } else if (sourceVar instanceof DiscreteVariable) {
                    DiscreteDiscretizationSpec spec = (DiscreteDiscretizationSpec) this.specs.get(sourceVar);

                    int[] remap = spec.getRemap();

                    int[] trimmedData = new int[newDataSet.getNumRows()];
                    int col = newDataSet.getColumn(variable);

                    for (int j = 0; j < this.sourceDataSet.getNumRows(); j++) {
                        trimmedData[j] = this.sourceDataSet.getInt(j, col);
                    }

                    int _col = newDataSet.getColumn(variable);

                    for (int j = 0; j < trimmedData.length; j++) {
                        newDataSet.setInt(j, _col, remap[trimmedData[j]]);
                    }
                }

            } else {
                DataTransforms.copyColumn(variable, this.sourceDataSet, newDataSet);
            }
        }
        return newDataSet;
    }

    /**
     * A discretization specification for a continuous variable.
     */
    public static class Discretization {
        /**
         * The variable that was discretized.
         */
        private final DiscreteVariable variable;

        /**
         * The discretized data.
         */
        private final int[] data;

        /**
         * A discretization specification for a continuous variable.
         */
        private Discretization(DiscreteVariable variable, int[] data) {
            this.variable = variable;
            this.data = data;
        }

        /**
         * Retrieves the variable associated with the object.
         *
         * @return the variable associated with the object
         */
        public final DiscreteVariable getVariable() {
            return this.variable;
        }

        /**
         * Retrieves the data associated with the discretization.
         *
         * @return the discretized data as an array of integers
         */
        public final int[] getData() {
            return this.data;
        }

        /**
         * Returns a string representation of the Discretization object. The string contains the discretization
         * information for the associated variable.
         *
         * @return a string representation of the Discretization object
         */
        public final String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("\n\nDiscretization:");

            for (int aData : this.data) {
                buf.append("\n").append(this.variable.getCategory(aData));
            }

            buf.append("\n");
            return buf.toString();
        }
    }
}



