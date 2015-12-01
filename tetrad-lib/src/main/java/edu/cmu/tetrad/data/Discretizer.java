///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
 * Discretizes individual columns of discrete or continuous data. Continuous
 * data is discretized by specifying a list of n - 1 cutoffs for n values in the
 * discretized data, with optional string labels for these values. Discrete data
 * is discretized by specifying a mapping from old value names to new
 * value names, the idea being that old values may be merged.
 *
 * @author Joseph Ramsey
 * @author Tyler Gibson
 */
public class Discretizer {
    private DataSet sourceDataSet;
    Map<Node, DiscretizationSpec> specs;
    private boolean variablesCopied = true;

    /**
     * Constructs a new discretizer that discretizes every variable as binary,
     * using evenly distributed values.
     */
    public Discretizer(DataSet dataSet) {
        this.sourceDataSet = dataSet;

        specs = new HashMap<Node, DiscretizationSpec>();
    }

    public Discretizer(DataSet dataSet, Map<Node, DiscretizationSpec> specs) {
        this.sourceDataSet = dataSet;
        this.specs = specs;
    }

    /**
     * Sets the given node to discretized using evenly distributed values using the
     * given number of categories.
     */
    public void equalCounts(Node node, int numCategories) {
        if (node instanceof DiscreteVariable) return;

        String name = node.getName();
        int i = sourceDataSet.getVariables().indexOf(node);
        double[] data = sourceDataSet.getDoubleData().getColumn(i).toArray();
        double[] breakpoints = Discretizer.getEqualFrequencyBreakPoints(data, numCategories);
        List<String> categories = new DiscreteVariable(name, numCategories).getCategories();

        ContinuousDiscretizationSpec spec
                = new ContinuousDiscretizationSpec(breakpoints, categories);
        spec.setMethod(ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES);

        specs.put(node, spec);
    }

    /**
     * Sets the given node to discretized using evenly spaced intervals using the
     * given number of categories.
     */
    public void equalIntervals(Node node, int numCategories) {
        if (node instanceof DiscreteVariable) return;

        String name = node.getName();
        int i = sourceDataSet.getVariables().indexOf(node);
        double[] data = sourceDataSet.getDoubleData().getColumn(i).toArray();
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

        specs.put(node, spec);
    }

    /**
     * Indicates that the given variable should not be discretized.
     */
    public void notDiscretized(Node node) {
        specs.remove(node);
    }

    public void setVariablesCopied(boolean unselectedVariabledCopied) {
        this.variablesCopied = unselectedVariabledCopied;
    }

    public boolean isVariablesCopied() {
        return this.variablesCopied;
    }

    /**
     * @return - Discretized dataset.
     */
    public DataSet discretize() {
        // build list of variables
        List<Node> variables = new LinkedList<Node>();
        Map<Node, Node> replacementMapping = new HashMap<>();
        for (int i = 0; i < sourceDataSet.getNumColumns(); i++) {
            Node variable = sourceDataSet.getVariable(i);
            if (variable instanceof ContinuousVariable) {
                ContinuousDiscretizationSpec spec = null;
                Node _node = null;

                for (Node node : specs.keySet()) {
                    if (node.getName().equals(variable.getName())) {
                        DiscretizationSpec _spec = specs.get(node);
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

                for (Node node : specs.keySet()) {
                    if (node.getName().equals(variable.getName())) {
                        DiscretizationSpec _spec = specs.get(node);
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
                }
            } else if (isVariablesCopied()) {
                variables.add(variable);
            }
        }

        // build new dataset.
        ColtDataSet newDataSet = new ColtDataSet(sourceDataSet.getNumRows(), variables);
        for (int i = 0; i < newDataSet.getNumColumns(); i++) {
            Node variable = newDataSet.getVariable(i);
            Node sourceVar = replacementMapping.get(variable);
            if (sourceVar != null && specs.containsKey(sourceVar)) {
                if (sourceVar instanceof ContinuousVariable) {
                    ContinuousDiscretizationSpec spec = (ContinuousDiscretizationSpec) specs.get(sourceVar);
                    double[] breakpoints = spec.getBreakpoints();
                    List<String> categories = spec.getCategories();
                    String name = variable.getName();

                    double[] trimmedData = new double[newDataSet.getNumRows()];
                    int col = newDataSet.getColumn(variable);

                    for (int j = 0; j < sourceDataSet.getNumRows(); j++) {
                        trimmedData[j] = sourceDataSet.getDouble(j, col);
                    }
                    Discretization discretization = discretize(trimmedData,
                            breakpoints, name, categories);

                    int _col = newDataSet.getColumn(variable);
                    int[] _data = discretization.getData();
                    for (int j = 0; j < _data.length; j++) {
                        newDataSet.setInt(j, _col, _data[j]);
                    }
                } else if (sourceVar instanceof DiscreteVariable) {
                    DiscreteDiscretizationSpec spec = (DiscreteDiscretizationSpec) specs.get(sourceVar);

                    int[] remap = spec.getRemap();

                    int[] trimmedData = new int[newDataSet.getNumRows()];
                    int col = newDataSet.getColumn(variable);

                    for (int j = 0; j < sourceDataSet.getNumRows(); j++) {
                        trimmedData[j] = sourceDataSet.getInt(j, col);
                    }

                    int _col = newDataSet.getColumn(variable);

                    for (int j = 0; j < trimmedData.length; j++) {
                        newDataSet.setInt(j, _col, remap[trimmedData[j]]);
                    }
                }

            } else {
                DataUtils.copyColumn(variable, sourceDataSet, newDataSet);
            }
        }
        return newDataSet;
    }

    /**
     * @param _data
     * @param numberOfCategories
     * @return
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

//        List<Chunk> chunks = new ArrayList<Chunk>(data.length);
//        int startChunkCount = 0;
//        double lastValue = data[0];
//        for (int i = 0; i < data.length; i++) {
//            double value = data[i];
//            if (value != lastValue) {

//                chunks.add(new Chunk(startChunkCount, i, value));
//                startChunkCount = i;
//            }
//            lastValue = value;
//        }
//        chunks.add(new Chunk(startChunkCount, data.length, data[data.length - 1]));
//
//        // now find the breakpoints.
////        double interval = data.length / (double) numberOfCategories;
//        double interval = data.length / numberOfCategories;
//        double[] breakpoints = new double[numberOfCategories - 1];
//        int current = 0;
//        int freq = 0;
//        for (Chunk chunk : chunks) {
//            int valuesInChunk = chunk.getNumberOfValuesInChunk();
//            int halfChunk = (int) (valuesInChunk * .5);
//            // if more than half the values in the chunk fit this bucket then put here,
//            // otherwise the chunk should be added to the next bucket.
//            if (freq + halfChunk <= interval) {
//                freq += valuesInChunk;
//            } else {
//                freq = valuesInChunk;
//            }
//
//            if (interval <= freq) {
//                freq = 0;
//                if (current < breakpoints.length) {
//                    breakpoints[current++] = chunk.value;
//                }
//            }
//        }
//
//        for (int i = current; i < breakpoints.length; i++) {
//            breakpoints[i] = Double.POSITIVE_INFINITY;
//        }
//
//        double[] _breakpoints = new double[current];
//        System.arraycopy(breakpoints, 0, _breakpoints, 0, current);

        return breakpoints;
    }


    /**
     * Discretizes the continuous data in the given column using the specified
     * cutoffs and category names. The following scheme is used. If cutoffs[i -
     * 1] < v <= cutoffs[i] (where cutoffs[-1] = negative infinity), then v is
     * mapped to category i. If category names are supplied, the discrete column
     * returned will use these category names.
     *
     * @param cutoffs      The cutoffs used to discretize the data. Should have
     *                     length c - 1, where c is the number of categories in
     *                     the discretized data.
     * @param variableName the name of the returned variable.
     * @param categories   An optional list of category names; may be null. If
     *                     this is supplied, the discrete column returned will
     *                     use these category names. If this is non-null, it
     *                     must have length c, where c is the number of
     *                     categories for the discretized data. If any category
     *                     names are null, default category names will be used
     *                     for those.
     * @return The discretized column.
     */
    public static Discretization discretize(double[] _data, double[] cutoffs,
                                            String variableName, List<String> categories) {

        if (cutoffs == null) {
            throw new NullPointerException();
        }

        for (int i = 0; i < cutoffs.length - 1; i++) {
            if (!(cutoffs[i] <= cutoffs[i + 1])) {
                throw new NullPointerException(
                        "Cutoffs must be in nondecreasing order.");
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

    //======================== Classes ================================//

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
         * Constructs the discretization given the variable and data.
         *
         * @param variable
         * @param data
         */
        private Discretization(DiscreteVariable variable, int[] data) {
            this.variable = variable;
            this.data = data;
        }

        //============================ Public Methods =================================//

        /**
         * @return - discretized variable.
         */
        public final DiscreteVariable getVariable() {
            return variable;
        }

        /**
         * @return - discretized data.
         */
        public final int[] getData() {
            return data;
        }

        public final String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("\n\nDiscretization:");

            for (int aData : data) {
                buf.append("\n").append(variable.getCategory(aData));
            }

            buf.append("\n");
            return buf.toString();
        }
    }

    /**
     * Represents a chunk of data in a sorted array of data.  If low == high then
     * then the chunk only contains one member.
     */
    private static class Chunk {

        private int valuesInChunk;
        private double value;

        public Chunk(int low, int high, double value) {
            this.valuesInChunk = (high - low);
            this.value = value;
        }

        public int getNumberOfValuesInChunk() {
            return this.valuesInChunk;
        }

    }

}



