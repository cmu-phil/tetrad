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
 * @version $Id: $Id
 */
public final class MultidataUtils {

    private MultidataUtils() {
    }

    /**
     * <p>combineDataset.</p>
     *
     * @param dataModels a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public static DataModel combineDataset(List<DataModel> dataModels) {
        if (dataModels == null || dataModels.isEmpty()) {
            return null;
        }

        DataModel dataModel = dataModels.get(0);
        DataBox dataBox = ((BoxDataSet) dataModel).getDataBox();

        int[] rowCounts = MultidataUtils.getRowCounts(dataModels);

        List<Node> variables = new ArrayList<>(dataModel.getVariables().size());
        MultidataUtils.combineVariables(dataModels, variables);

        int numOfRows = Arrays.stream(rowCounts).sum();
        int numOfCols = MultidataUtils.getNumberOfColumns(dataModel);

        if (dataBox instanceof DoubleDataBox || dataBox instanceof VerticalDoubleDataBox) {
            double[][] continuousData = new double[numOfRows][numOfCols];
            MultidataUtils.combineContinuousData(dataModels, continuousData);

            return new BoxDataSet(new DoubleDataBox(continuousData), variables);
        } else if (dataBox instanceof VerticalIntDataBox) {
            int[][] discreteData = new int[numOfCols][];
            MultidataUtils.combineDiscreteDataToDiscreteVerticalData(dataModels, variables, discreteData, numOfRows, numOfCols);

            return new BoxDataSet(new VerticalIntDataBox(discreteData), variables);
        } else if (dataBox instanceof MixedDataBox) {
            double[][] continuousData = new double[numOfCols][];
            MultidataUtils.combineMixedContinuousData(dataModels, variables, continuousData, numOfRows, numOfCols);

            int[][] discreteData = new int[numOfCols][];
            MultidataUtils.combineMixedDiscreteData(dataModels, variables, discreteData, numOfRows, numOfCols);

            return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
        } else {
            throw new UnsupportedOperationException("This method only supports data with continuous, discrete, or mixed variables.");
        }
    }

    private static void combineSingleMixedDiscreteData(List<DataModel> dataModels, int[][] combinedData, int numOfColumns) {
        DataModel dataModel = dataModels.get(0);
        MixedDataBox model = (MixedDataBox) ((BoxDataSet) dataModel).getDataBox();
        int[][] discreteData = model.getDiscreteData();
        for (int col = 0; col < numOfColumns; col++) {
            int[] data = discreteData[col];
            if (data == null) {
                continue;
            }

            int[] rowData = new int[data.length];
            System.arraycopy(data, 0, rowData, 0, data.length);

            combinedData[col] = rowData;
        }
    }

    private static void combineMultipleMixedDiscreteData(List<DataModel> dataModels, List<Node> variables, int[][] combinedData, int numOfRows, int numOfColumns) {
        DiscreteVariable[] discreteVars = variables.stream()
                .map(e -> (e instanceof DiscreteVariable) ? (DiscreteVariable) e : null)
                .toArray(DiscreteVariable[]::new);

        DiscreteVariable[][] dataVariables = dataModels.stream()
                .map(e -> e.getVariables().stream().map(v -> (v instanceof DiscreteVariable) ? (DiscreteVariable) v : null).toArray(DiscreteVariable[]::new))
                .toArray(DiscreteVariable[][]::new);

        MixedDataBox[] models = dataModels.stream()
                .map(e -> (MixedDataBox) ((BoxDataSet) e).getDataBox())
                .toArray(MixedDataBox[]::new);

        for (int col = 0; col < numOfColumns; col++) {
            if (discreteVars[col] != null) {
                int[] rowData = new int[numOfRows];
                int row = 0;
                for (int i = 0; i < models.length; i++) {
                    DiscreteVariable var = dataVariables[i][col];
                    MixedDataBox model = models[i];
                    int[][] data = model.getDiscreteData();
                    int[] values = data[col];
                    for (int value : values) {
                        rowData[row++] = discreteVars[col].getIndex(var.getCategory(value));
                    }
                }
                combinedData[col] = rowData;
            }
        }
    }

    /**
     * <p>combineMixedDiscreteData.</p>
     *
     * @param dataModels   a {@link java.util.List} object
     * @param variables    a {@link java.util.List} object
     * @param combinedData an array of {@link int} objects
     * @param numOfRows    a int
     * @param numOfColumns a int
     */
    public static void combineMixedDiscreteData(List<DataModel> dataModels, List<Node> variables, int[][] combinedData, int numOfRows, int numOfColumns) {
        if (dataModels.size() == 1) {
            MultidataUtils.combineSingleMixedDiscreteData(dataModels, combinedData, numOfColumns);
        } else {
            MultidataUtils.combineMultipleMixedDiscreteData(dataModels, variables, combinedData, numOfRows, numOfColumns);
        }
    }

    private static void combineSingleMixedContinuousData(List<DataModel> dataModels, double[][] combinedData, int numOfColumns) {
        DataModel dataModel = dataModels.get(0);
        MixedDataBox model = (MixedDataBox) ((BoxDataSet) dataModel).getDataBox();
        double[][] continuousData = model.getContinuousData();
        for (int col = 0; col < numOfColumns; col++) {
            double[] data = continuousData[col];
            if (data == null) {
                continue;
            }

            double[] rowData = new double[data.length];
            System.arraycopy(data, 0, rowData, 0, data.length);

            combinedData[col] = rowData;
        }
    }

    private static void combineMultipleMixedContinuousData(List<DataModel> dataModels, List<Node> variables, double[][] combinedData, int numOfRows, int numOfColumns) {
        List<MixedDataBox> models = dataModels.stream()
                .map(e -> (MixedDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());

        Node[] continuousVars = variables.stream()
                .map(e -> (e instanceof ContinuousVariable) ? e : null)
                .toArray(Node[]::new);

        for (int col = 0; col < numOfColumns; col++) {
            if (continuousVars[col] != null) {
                double[] rowData = new double[numOfRows];
                int row = 0;
                for (MixedDataBox model : models) {
                    double[][] data = model.getContinuousData();
                    double[] values = data[col];
                    System.arraycopy(values, 0, rowData, row, values.length);
                    row += values.length;
                }
                combinedData[col] = rowData;
            }
        }
    }

    /**
     * <p>combineMixedContinuousData.</p>
     *
     * @param dataModels   a {@link java.util.List} object
     * @param variables    a {@link java.util.List} object
     * @param combinedData an array of {@link double} objects
     * @param numOfRows    a int
     * @param numOfColumns a int
     */
    public static void combineMixedContinuousData(List<DataModel> dataModels, List<Node> variables, double[][] combinedData, int numOfRows, int numOfColumns) {
        if (dataModels.size() == 1) {
            MultidataUtils.combineSingleMixedContinuousData(dataModels, combinedData, numOfColumns);
        } else {
            MultidataUtils.combineMultipleMixedContinuousData(dataModels, variables, combinedData, numOfRows, numOfColumns);
        }
    }

    /**
     * <p>combineDiscreteDataToDiscreteVerticalData.</p>
     *
     * @param dataModels   a {@link java.util.List} object
     * @param variables    a {@link java.util.List} object
     * @param combinedData an array of {@link int} objects
     * @param numOfRows    a int
     * @param numOfColumns a int
     */
    public static void combineDiscreteDataToDiscreteVerticalData(List<DataModel> dataModels, List<Node> variables, int[][] combinedData, int numOfRows, int numOfColumns) {
        DiscreteVariable[] discreteVars = variables.stream()
                .map(e -> (e instanceof DiscreteVariable) ? (DiscreteVariable) e : null)
                .toArray(DiscreteVariable[]::new);

        DataModel[] models = dataModels.toArray(new DataModel[0]);

        DiscreteVariable[][] dataVariables = dataModels.stream()
                .map(e -> e.getVariables().stream().map(v -> (DiscreteVariable) v).toArray(DiscreteVariable[]::new))
                .toArray(DiscreteVariable[][]::new);

        for (int col = 0; col < numOfColumns; col++) {
            int[] rowData = new int[numOfRows];
            int row = 0;
            for (int i = 0; i < models.length; i++) {
                DiscreteVariable var = dataVariables[i][col];
                VerticalIntDataBox dataBox = (VerticalIntDataBox) ((BoxDataSet) models[i]).getDataBox();
                int[][] data = dataBox.getVariableVectors();
                int[] values = data[col];
                for (int value : values) {
                    rowData[row++] = discreteVars[col].getIndex(var.getCategory(value));
                }
            }
            combinedData[col] = rowData;
        }
    }

    /**
     * <p>combineContinuousData.</p>
     *
     * @param dataModels   a {@link java.util.List} object
     * @param combinedData an array of {@link double} objects
     */
    public static void combineContinuousData(List<DataModel> dataModels, double[][] combinedData) {
        List<DoubleDataBox> models = dataModels.stream()
                .map(e -> {
                    DataBox dataBox = ((BoxDataSet) e).getDataBox();
                    DoubleDataBox box2 = new DoubleDataBox(dataBox.numRows(), dataBox.numCols());

                    for (int i = 0; i < dataBox.numRows(); i++) {
                        for (int j = 0; j < dataBox.numCols(); j++) {
                            box2.set(i, j, dataBox.get(i, j));
                        }
                    }

                    return new DoubleDataBox(box2.getData());
                })
                .collect(Collectors.toList());

        int row = 0;
        for (DoubleDataBox dataBox : models) {
            double[][] data = dataBox.getData();
            for (double[] rowData : data) {
                System.arraycopy(rowData, 0, combinedData[row++], 0, rowData.length);
            }
        }
    }

    private static void combineMixedVariables(List<DataModel> dataModels, List<Node> variables) {
        List<Node> dataVars = dataModels.get(0).getVariables();
        if (dataModels.size() == 1) {
            dataVars.stream()
                    .collect(Collectors.toCollection(() -> variables));
        } else {
            List<Node> nodeList = dataModels.get(0).getVariables();

            int size = nodeList.size();
            Set<String>[] varCategories = new Set[size];

            // initialize category set
            int index = 0;
            for (Node node : nodeList) {
                if (node instanceof DiscreteVariable) {
                    varCategories[index] = new HashSet<>();
                }
                index++;
            }

            dataModels.forEach(dataModel -> {
                List<Node> nodes = dataModel.getVariables();
                int i = 0;
                for (Node node : nodes) {
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
            for (Node node : nodeList) {
                if (node instanceof DiscreteVariable) {
                    Set<String> categories = varCategories[index];
                    variables.add(new DiscreteVariable(node.getName(), new ArrayList<>(categories)));
                } else {
                    variables.add(node);
                }
                index++;
            }
        }
    }

    private static void combineDiscreteVariables(List<DataModel> dataModels, List<Node> variables) {
        List<Node> dataVars = dataModels.get(0).getVariables();
        if (dataModels.size() == 1) {
            dataVars.stream()
                    .collect(Collectors.toCollection(() -> variables));
        } else {
            int size = dataVars.size();

            // initialize an array that holds a set of categories for each of the variable
            Set<String>[] varCategories = new Set[size];
            for (int i = 0; i < size; i++) {
                varCategories[i] = new HashSet<>();
            }

            // collect the categories from each variable into a set
            dataModels.forEach(models -> {
                List<Node> nodes = models.getVariables();
                int index = 0;
                for (Node node : nodes) {
                    DiscreteVariable var = (DiscreteVariable) node;
                    varCategories[index++].addAll(var.getCategories());
                }
            });

            int index = 0;
            for (Node dataVar : dataVars) {
                Set<String> categories = varCategories[index++];
                variables.add(new DiscreteVariable(dataVar.getName(), new ArrayList<>(categories)));
            }
        }
    }

    private static void combineContinuousVariables(List<DataModel> dataModels, List<Node> variables) {
        dataModels.get(0).getVariables().stream()
                .collect(Collectors.toCollection(() -> variables));
    }

    /**
     * Combine the list of variables from each of data model in the list into one variable list.
     *
     * @param dataModels list of data models that has the same list of variables
     * @param variables  list where all the combined variables are stored
     */
    public static void combineVariables(List<DataModel> dataModels, List<Node> variables) {
        if (dataModels == null || dataModels.isEmpty()) {
            return;
        }

        DataModel dataModel = dataModels.get(0);
        DataBox dataBox = ((BoxDataSet) dataModel).getDataBox();

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

    /**
     * <p>getRowCounts.</p>
     *
     * @param dataModels a {@link java.util.List} object
     * @return an array of {@link int} objects
     */
    public static int[] getRowCounts(List<DataModel> dataModels) {
        int[] counts = new int[dataModels.size()];

        int index = 0;
        for (DataModel dataModel : dataModels) {
            if (dataModel instanceof BoxDataSet) {
                counts[index++] = ((BoxDataSet) dataModel).getNumRows();
            }
        }

        return counts;
    }

    /**
     * <p>getNumberOfColumns.</p>
     *
     * @param dataModel a {@link edu.cmu.tetrad.data.DataModel} object
     * @return a int
     */
    public static int getNumberOfColumns(DataModel dataModel) {
        return (dataModel instanceof BoxDataSet)
                ? ((BoxDataSet) dataModel).getDataBox().numCols()
                : 0;
    }

}
