/*
 * Copyright (C) 2018 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aug 7, 2018 10:21:59 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class MultidataUtils {

    private MultidataUtils() {
    }

    public static DataModel combineDataset(final List<DataModel> dataModels) {
        if (dataModels == null || dataModels.isEmpty()) {
            return null;
        }

        final DataModel dataModel = dataModels.get(0);
        final DataBox dataBox = ((BoxDataSet) dataModel).getDataBox();

        final int[] rowCounts = MultidataUtils.getRowCounts(dataModels);

        final List<Node> variables = new ArrayList<>(dataModel.getVariables().size());
        MultidataUtils.combineVariables(dataModels, variables);

        final int numOfRows = Arrays.stream(rowCounts).sum();
        final int numOfCols = MultidataUtils.getNumberOfColumns(dataModel);

        if (dataBox instanceof DoubleDataBox || dataBox instanceof VerticalDoubleDataBox) {
            final double[][] continuousData = new double[numOfRows][numOfCols];
            MultidataUtils.combineContinuousData(dataModels, continuousData);

            return new BoxDataSet(new DoubleDataBox(continuousData), variables);
        } else if (dataBox instanceof VerticalIntDataBox) {
            final int[][] discreteData = new int[numOfCols][];
            MultidataUtils.combineDiscreteDataToDiscreteVerticalData(dataModels, variables, discreteData, numOfRows, numOfCols);

            return new BoxDataSet(new VerticalIntDataBox(discreteData), variables);
        } else if (dataBox instanceof MixedDataBox) {
            final double[][] continuousData = new double[numOfCols][];
            MultidataUtils.combineMixedContinuousData(dataModels, variables, continuousData, numOfRows, numOfCols);

            final int[][] discreteData = new int[numOfCols][];
            MultidataUtils.combineMixedDiscreteData(dataModels, variables, discreteData, numOfRows, numOfCols);

            return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
        } else {
            throw new UnsupportedOperationException("This method only supports data with continuous, discrete, or mixed variables.");
        }
    }

    private static void combineSingleMixedDiscreteData(final List<DataModel> dataModels, final int[][] combinedData, final int numOfColumns) {
        final DataModel dataModel = dataModels.get(0);
        final MixedDataBox model = (MixedDataBox) ((BoxDataSet) dataModel).getDataBox();
        final int[][] discreteData = model.getDiscreteData();
        for (int col = 0; col < numOfColumns; col++) {
            final int[] data = discreteData[col];
            if (data == null) {
                continue;
            }

            final int[] rowData = new int[data.length];
            System.arraycopy(data, 0, rowData, 0, data.length);

            combinedData[col] = rowData;
        }
    }

    private static void combineMultipleMixedDiscreteData(final List<DataModel> dataModels, final List<Node> variables, final int[][] combinedData, final int numOfRows, final int numOfColumns) {
        final DiscreteVariable[] discreteVars = variables.stream()
                .map(e -> (e instanceof DiscreteVariable) ? (DiscreteVariable) e : null)
                .toArray(size -> new DiscreteVariable[size]);

        final DiscreteVariable[][] dataVariables = dataModels.stream()
                .map(e -> e.getVariables().stream().map(v -> (v instanceof DiscreteVariable) ? (DiscreteVariable) v : null).toArray(size -> new DiscreteVariable[size]))
                .toArray(size -> new DiscreteVariable[size][]);

        final MixedDataBox[] models = dataModels.stream()
                .map(e -> (MixedDataBox) ((BoxDataSet) e).getDataBox())
                .toArray(size -> new MixedDataBox[size]);

        for (int col = 0; col < numOfColumns; col++) {
            if (discreteVars[col] != null) {
                final int[] rowData = new int[numOfRows];
                int row = 0;
                for (int i = 0; i < models.length; i++) {
                    final DiscreteVariable var = dataVariables[i][col];
                    final MixedDataBox model = models[i];
                    final int[][] data = model.getDiscreteData();
                    final int[] values = data[col];
                    for (int j = 0; j < values.length; j++) {
                        rowData[row++] = discreteVars[col].getIndex(var.getCategory(values[j]));
                    }
                }
                combinedData[col] = rowData;
            }
        }
    }

    public static void combineMixedDiscreteData(final List<DataModel> dataModels, final List<Node> variables, final int[][] combinedData, final int numOfRows, final int numOfColumns) {
        if (dataModels.size() == 1) {
            MultidataUtils.combineSingleMixedDiscreteData(dataModels, combinedData, numOfColumns);
        } else {
            MultidataUtils.combineMultipleMixedDiscreteData(dataModels, variables, combinedData, numOfRows, numOfColumns);
        }
    }

    private static void combineSingleMixedContinuousData(final List<DataModel> dataModels, final double[][] combinedData, final int numOfColumns) {
        final DataModel dataModel = dataModels.get(0);
        final MixedDataBox model = (MixedDataBox) ((BoxDataSet) dataModel).getDataBox();
        final double[][] continuousData = model.getContinuousData();
        for (int col = 0; col < numOfColumns; col++) {
            final double[] data = continuousData[col];
            if (data == null) {
                continue;
            }

            final double[] rowData = new double[data.length];
            System.arraycopy(data, 0, rowData, 0, data.length);

            combinedData[col] = rowData;
        }
    }

    private static void combineMultipleMixedContinuousData(final List<DataModel> dataModels, final List<Node> variables, final double[][] combinedData, final int numOfRows, final int numOfColumns) {
        final List<MixedDataBox> models = dataModels.stream()
                .map(e -> (MixedDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());

        final Node[] continuousVars = variables.stream()
                .map(e -> (e instanceof ContinuousVariable) ? e : null)
                .toArray(size -> new Node[size]);

        for (int col = 0; col < numOfColumns; col++) {
            if (continuousVars[col] != null) {
                final double[] rowData = new double[numOfRows];
                int row = 0;
                for (final MixedDataBox model : models) {
                    final double[][] data = model.getContinuousData();
                    final double[] values = data[col];
                    System.arraycopy(values, 0, rowData, row, values.length);
                    row += values.length;
                }
                combinedData[col] = rowData;
            }
        }
    }

    public static void combineMixedContinuousData(final List<DataModel> dataModels, final List<Node> variables, final double[][] combinedData, final int numOfRows, final int numOfColumns) {
        if (dataModels.size() == 1) {
            MultidataUtils.combineSingleMixedContinuousData(dataModels, combinedData, numOfColumns);
        } else {
            MultidataUtils.combineMultipleMixedContinuousData(dataModels, variables, combinedData, numOfRows, numOfColumns);
        }
    }

    public static void combineDiscreteDataToDiscreteVerticalData(final List<DataModel> dataModels, final List<Node> variables, final int[][] combinedData, final int numOfRows, final int numOfColumns) {
        final DiscreteVariable[] discreteVars = variables.stream()
                .map(e -> (e instanceof DiscreteVariable) ? (DiscreteVariable) e : null)
                .toArray(size -> new DiscreteVariable[size]);

        final DataModel[] models = dataModels.stream()
                .toArray(size -> new DataModel[size]);

        final DiscreteVariable[][] dataVariables = dataModels.stream()
                .map(e -> e.getVariables().stream().map(v -> (DiscreteVariable) v).toArray(size -> new DiscreteVariable[size]))
                .toArray(size -> new DiscreteVariable[size][]);

        for (int col = 0; col < numOfColumns; col++) {
            final int[] rowData = new int[numOfRows];
            int row = 0;
            for (int i = 0; i < models.length; i++) {
                final DiscreteVariable var = dataVariables[i][col];
                final VerticalIntDataBox dataBox = (VerticalIntDataBox) ((BoxDataSet) models[i]).getDataBox();
                final int[][] data = dataBox.getVariableVectors();
                final int[] values = data[col];
                for (int j = 0; j < values.length; j++) {
                    rowData[row++] = discreteVars[col].getIndex(var.getCategory(values[j]));
                }
            }
            combinedData[col] = rowData;
        }
    }

    public static void combineContinuousDataToContinuousVerticalData(final List<DataModel> dataModels, final double[][] combinedData, final int numOfRows, final int numOfColumns) {
        final List<DoubleDataBox> models = dataModels.stream()
                .map(e -> (DoubleDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());
        for (int col = 0; col < numOfColumns; col++) {
            final double[] rowData = new double[numOfRows];
            int row = 0;
            for (final DoubleDataBox dataBox : models) {
                final double[][] data = dataBox.getData();
                for (final double[] rData : data) {
                    rowData[row++] = rData[col];
                }
            }
            combinedData[col] = rowData;
        }
    }

    public static void combineContinuousData(final List<DataModel> dataModels, final double[][] combinedData) {
        final List<DoubleDataBox> models = dataModels.stream()
                .map(e -> {
                    final DataBox dataBox = ((BoxDataSet) e).getDataBox();
                    final DoubleDataBox box2 = new DoubleDataBox(dataBox.numRows(), dataBox.numCols());

                    final double[][] m = new double[dataBox.numRows()][dataBox.numCols()];
                    for (int i = 0; i < dataBox.numRows(); i++) {
                        for (int j = 0; j < dataBox.numCols(); j++) {
                            box2.set(i, j, dataBox.get(i, j));
                        }
                    }

                    return new DoubleDataBox(box2.getData());
                })
                .collect(Collectors.toList());

        int row = 0;
        for (final DoubleDataBox dataBox : models) {
            final double[][] data = dataBox.getData();
            for (final double[] rowData : data) {
                System.arraycopy(rowData, 0, combinedData[row++], 0, rowData.length);
            }
        }
    }

    private static void combineMixedVariables(final List<DataModel> dataModels, final List<Node> variables) {
        final List<Node> dataVars = dataModels.get(0).getVariables();
        if (dataModels.size() == 1) {
            dataVars.stream()
                    .collect(Collectors.toCollection(() -> variables));
        } else {
            final List<Node> nodeList = dataModels.get(0).getVariables();

            final int size = nodeList.size();
            final Set<String>[] varCategories = new Set[size];

            // initialize category set
            int index = 0;
            for (final Node node : nodeList) {
                if (node instanceof DiscreteVariable) {
                    varCategories[index] = new HashSet<>();
                }
                index++;
            }

            dataModels.forEach(dataModel -> {
                final List<Node> nodes = dataModel.getVariables();
                int i = 0;
                for (final Node node : nodes) {
                    if (node instanceof DiscreteVariable) {
                        Set<String> set = varCategories[i];
                        if (set == null) {
                            set = new HashSet<>();
                            varCategories[i] = set;
                        }
                        set.addAll(((DiscreteVariable) node).getCategories());
                    }
                    i++;
                }
            });

            index = 0;
            for (final Node node : nodeList) {
                if (node instanceof DiscreteVariable) {
                    final Set<String> categories = varCategories[index];
                    variables.add(new DiscreteVariable(node.getName(), categories.stream().collect(Collectors.toList())));
                } else {
                    variables.add(node);
                }
                index++;
            }
        }
    }

    private static void combineDiscreteVariables(final List<DataModel> dataModels, final List<Node> variables) {
        final List<Node> dataVars = dataModels.get(0).getVariables();
        if (dataModels.size() == 1) {
            dataVars.stream()
                    .collect(Collectors.toCollection(() -> variables));
        } else {
            final int size = dataVars.size();

            // initialize an array that holds a set of categories for each of the variable
            final Set<String>[] varCategories = new Set[size];
            for (int i = 0; i < size; i++) {
                varCategories[i] = new HashSet<>();
            }

            // collect the categories from each variable into a set
            dataModels.forEach(models -> {
                final List<Node> nodes = models.getVariables();
                int index = 0;
                for (final Node node : nodes) {
                    final DiscreteVariable var = (DiscreteVariable) node;
                    varCategories[index++].addAll(var.getCategories());
                }
            });

            int index = 0;
            for (final Node dataVar : dataVars) {
                final Set<String> categories = varCategories[index++];
                variables.add(new DiscreteVariable(dataVar.getName(), categories.stream().collect(Collectors.toList())));
            }
        }
    }

    private static void combineContinuousVariables(final List<DataModel> dataModels, final List<Node> variables) {
        dataModels.get(0).getVariables().stream()
                .collect(Collectors.toCollection(() -> variables));
    }

    /**
     * Combine the list of variables from each of data model in the list into
     * one variable list.
     *
     * @param dataModels list of data models that has the same list of variables
     * @param variables  list where all the combined variables are stored
     */
    public static void combineVariables(final List<DataModel> dataModels, final List<Node> variables) {
        if (dataModels == null || dataModels.isEmpty()) {
            return;
        }

        final DataModel dataModel = dataModels.get(0);
        final DataBox dataBox = ((BoxDataSet) dataModel).getDataBox();

        if (dataBox instanceof DoubleDataBox || dataBox instanceof VerticalDoubleDataBox) {
            MultidataUtils.combineContinuousVariables(dataModels, variables);
        } else if (dataBox instanceof VerticalIntDataBox) {
            MultidataUtils.combineDiscreteVariables(dataModels, variables);
        } else if (dataBox instanceof MixedDataBox) {
            MultidataUtils.combineMixedVariables(dataModels, variables);
        } else {
            throw new UnsupportedOperationException("This method only supports data with continuous, discrete, or mixed variables.");
        }
    }

    public static int[] getRowCounts(final List<DataModel> dataModels) {
        final int[] counts = new int[dataModels.size()];

        int index = 0;
        for (final DataModel dataModel : dataModels) {
            if (dataModel instanceof BoxDataSet) {
                counts[index++] = ((BoxDataSet) dataModel).getNumRows();
            }
        }

        return counts;
    }

    public static int getNumberOfColumns(final DataModel dataModel) {
        return (dataModel instanceof BoxDataSet)
                ? ((BoxDataSet) dataModel).getDataBox().numCols()
                : 0;
    }

}
