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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * Aug 7, 2018 10:21:59 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class MultidataUtils {

    private MultidataUtils() {
    }

    public static DataModel combineDataset(List<DataModel> dataModels) {
        if (dataModels == null || dataModels.isEmpty()) {
            return null;
        }

        DataModel dataModel = dataModels.get(0);
        DataBox dataBox = ((BoxDataSet) dataModel).getDataBox();

        int[] rowCounts = getRowCounts(dataModels);

        List<Node> variables = new ArrayList<>(dataModel.getVariables().size());
        combineVariables(dataModels, variables);

        int numOfRows = Arrays.stream(rowCounts).sum();
        int numOfCols = getNumberOfColumns(dataModel);

        if (dataBox instanceof DoubleDataBox) {
            double[][] continuousData = new double[numOfRows][numOfCols];
            combineContinuousData(dataModels, continuousData);

            return new BoxDataSet(new DoubleDataBox(continuousData), variables);
        } else if (dataBox instanceof VerticalIntDataBox) {
            int[][] discreteData = new int[numOfCols][];
            combineDiscreteDataToDiscreteVerticalData(dataModels, variables, discreteData, numOfRows, numOfCols);

            return new BoxDataSet(new VerticalIntDataBox(discreteData), variables);
        } else if (dataBox instanceof MixedDataBox) {
            double[][] continuousData = new double[numOfCols][];
            combineMixedContinuousData(dataModels, variables, continuousData, numOfRows, numOfCols);

            int[][] discreteData = new int[numOfCols][];
            combineMixedDiscreteData(dataModels, variables, discreteData, numOfRows, numOfCols);

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
                .toArray(size -> new DiscreteVariable[size]);

        DiscreteVariable[][] dataVariables = dataModels.stream()
                .map(e -> e.getVariables().stream().map(v -> (v instanceof DiscreteVariable) ? (DiscreteVariable) v : null).toArray(size -> new DiscreteVariable[size]))
                .toArray(size -> new DiscreteVariable[size][]);

        MixedDataBox[] models = dataModels.stream()
                .map(e -> (MixedDataBox) ((BoxDataSet) e).getDataBox())
                .toArray(size -> new MixedDataBox[size]);

        for (int col = 0; col < numOfColumns; col++) {
            if (discreteVars[col] != null) {
                int[] rowData = new int[numOfRows];
                int row = 0;
                for (int i = 0; i < models.length; i++) {
                    DiscreteVariable var = dataVariables[i][col];
                    MixedDataBox model = models[i];
                    int[][] data = model.getDiscreteData();
                    int[] values = data[col];
                    for (int j = 0; j < values.length; j++) {
                        rowData[row++] = discreteVars[col].getIndex(var.getCategory(values[j]));
                    }
                }
                combinedData[col] = rowData;
            }
        }
    }

    public static void combineMixedDiscreteData(List<DataModel> dataModels, List<Node> variables, int[][] combinedData, int numOfRows, int numOfColumns) {
        if (dataModels.size() == 1) {
            combineSingleMixedDiscreteData(dataModels, combinedData, numOfColumns);
        } else {
            combineMultipleMixedDiscreteData(dataModels, variables, combinedData, numOfRows, numOfColumns);
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
                .toArray(size -> new Node[size]);

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

    public static void combineMixedContinuousData(List<DataModel> dataModels, List<Node> variables, double[][] combinedData, int numOfRows, int numOfColumns) {
        if (dataModels.size() == 1) {
            combineSingleMixedContinuousData(dataModels, combinedData, numOfColumns);
        } else {
            combineMultipleMixedContinuousData(dataModels, variables, combinedData, numOfRows, numOfColumns);
        }
    }

    public static void combineDiscreteDataToDiscreteVerticalData(List<DataModel> dataModels, List<Node> variables, int[][] combinedData, int numOfRows, int numOfColumns) {
        DiscreteVariable[] discreteVars = variables.stream()
                .map(e -> (e instanceof DiscreteVariable) ? (DiscreteVariable) e : null)
                .toArray(size -> new DiscreteVariable[size]);

        DataModel[] models = dataModels.stream()
                .toArray(size -> new DataModel[size]);

        DiscreteVariable[][] dataVariables = dataModels.stream()
                .map(e -> e.getVariables().stream().map(v -> (DiscreteVariable) v).toArray(size -> new DiscreteVariable[size]))
                .toArray(size -> new DiscreteVariable[size][]);

        for (int col = 0; col < numOfColumns; col++) {
            int[] rowData = new int[numOfRows];
            int row = 0;
            for (int i = 0; i < models.length; i++) {
                DiscreteVariable var = dataVariables[i][col];
                VerticalIntDataBox dataBox = (VerticalIntDataBox) ((BoxDataSet) models[i]).getDataBox();
                int[][] data = dataBox.getVariableVectors();
                int[] values = data[col];
                for (int j = 0; j < values.length; j++) {
                    rowData[row++] = discreteVars[col].getIndex(var.getCategory(values[j]));
                }
            }
            combinedData[col] = rowData;
        }
    }

    public static void combineContinuousDataToContinuousVerticalData(List<DataModel> dataModels, double[][] combinedData, int numOfRows, int numOfColumns) {
        List<DoubleDataBox> models = dataModels.stream()
                .map(e -> (DoubleDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());
        for (int col = 0; col < numOfColumns; col++) {
            double[] rowData = new double[numOfRows];
            int row = 0;
            for (DoubleDataBox dataBox : models) {
                double[][] data = dataBox.getData();
                for (double[] rData : data) {
                    rowData[row++] = rData[col];
                }
            }
            combinedData[col] = rowData;
        }
    }

    public static void combineContinuousData(List<DataModel> dataModels, double[][] combinedData) {
        List<DoubleDataBox> models = dataModels.stream()
                .map(e -> (DoubleDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());

        int row = 0;
        for (DoubleDataBox dataBox : models) {
            double[][] data = ((DoubleDataBox) dataBox).getData();
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
                    variables.add(new DiscreteVariable(node.getName(), categories.stream().collect(Collectors.toList())));
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
                variables.add(new DiscreteVariable(dataVar.getName(), categories.stream().collect(Collectors.toList())));
            }
        }
    }

    private static void combineContinuousVariables(List<DataModel> dataModels, List<Node> variables) {
        dataModels.get(0).getVariables().stream()
                .collect(Collectors.toCollection(() -> variables));
    }

    /**
     * Combine the list of variables from each of data model in the list into
     * one variable list.
     *
     * @param dataModels list of data models that has the same list of variables
     * @param variables list where all the combined variables are stored
     */
    public static void combineVariables(List<DataModel> dataModels, List<Node> variables) {
        if (dataModels == null || dataModels.isEmpty()) {
            return;
        }

        DataModel dataModel = dataModels.get(0);
        DataBox dataBox = ((BoxDataSet) dataModel).getDataBox();

        if (dataBox instanceof DoubleDataBox) {
            combineContinuousVariables(dataModels, variables);
        } else if (dataBox instanceof VerticalIntDataBox) {
            combineDiscreteVariables(dataModels, variables);
        } else if (dataBox instanceof MixedDataBox) {
            combineMixedVariables(dataModels, variables);
        } else {
            throw new UnsupportedOperationException("This method only supports data with continuous, discrete, or mixed variables.");
        }
    }

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

    public static int getNumberOfColumns(DataModel dataModel) {
        return (dataModel instanceof BoxDataSet)
                ? ((BoxDataSet) dataModel).getDataBox().numCols()
                : 0;
    }

}
