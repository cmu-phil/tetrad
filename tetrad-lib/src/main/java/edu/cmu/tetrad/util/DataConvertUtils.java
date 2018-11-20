/*
 * Copyright (C) 2017 University of Pittsburgh.
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
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.covariance.CovarianceData;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnFileReader.TabularDataColumn;
import edu.pitt.dbmi.data.reader.tabular.TabularDataFileReader.ContinuousTabularDataset;
import edu.pitt.dbmi.data.reader.tabular.TabularDataFileReader.DiscreteColumn;
import edu.pitt.dbmi.data.reader.tabular.TabularDataFileReader.MixedTabularDataset;
import edu.pitt.dbmi.data.reader.tabular.TabularDataFileReader.VerticalDiscreteTabularDataset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Jun 20, 2017 11:10:30 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DataConvertUtils {

    private DataConvertUtils() {
    }

    public static DataModel toDataModel(Data data) {
        if (data instanceof ContinuousTabularDataset) {
            return toContinuousDataModel((ContinuousTabularDataset) data);
        } else if (data instanceof VerticalDiscreteTabularDataset) {
            return toVerticalDiscreteDataModel((VerticalDiscreteTabularDataset) data);
        } else if (data instanceof MixedTabularDataset) {
            return toMixedDataBox((MixedTabularDataset) data);
        } else if (data instanceof CovarianceData) {
            return toCovarianceMatrix((CovarianceData) data);
        } else {
            return null;
        }
    }

    public static DataModel toCovarianceMatrix(CovarianceData dataset) {
        List<Node> variables = toNodes(dataset.getVariables());
        TetradMatrix matrix = new TetradMatrix(dataset.getData());
        int sampleSize = dataset.getNumberOfCases();

        return new CovarianceMatrix(variables, matrix, sampleSize);
    }

    public static DataModel toMixedDataBox(MixedTabularDataset dataset) {
        int numOfRows = dataset.getNumOfRows();
        DiscreteColumn[] columns = dataset.getColumns();
        double[][] continuousData = dataset.getContinuousData();
        int[][] discreteData = dataset.getDiscreteData();

        List<Node> nodes = Arrays.stream(columns)
                .map(e -> e.isDiscrete()
                ? new DiscreteVariable(e.getName(), e.getCategories())
                : new ContinuousVariable(e.getName()))
                .collect(Collectors.toList());

        return new BoxDataSet(new MixedDataBox(nodes, numOfRows, continuousData, discreteData), nodes);
    }

    public static DataModel toVerticalDiscreteDataModel(VerticalDiscreteTabularDataset dataset) {
        DataBox dataBox = new VerticalIntDataBox(dataset.getData());
        List<Node> variables = toNodes(dataset.getColumns());

        return new BoxDataSet(dataBox, variables);
    }

    public static DataModel toContinuousDataModel(ContinuousTabularDataset dataset) {
        DataBox dataBox = new DoubleDataBox(dataset.getData());
        List<Node> variables = toNodes(dataset.getColumns());

        return new BoxDataSet(dataBox, variables);
    }

    public static List<Node> toNodes(List<String> variables) {
        return variables.stream()
                .map(ContinuousVariable::new)
                .collect(Collectors.toList());
    }

    public static List<Node> toNodes(DiscreteColumn[] columns) {
        return Arrays.stream(columns)
                .map(e -> new DiscreteVariable(e.getName(), e.getCategories()))
                .collect(Collectors.toList());
    }

    public static List<Node> toNodes(TabularDataColumn[] columns) {
        return Arrays.stream(columns)
                .map(e -> new ContinuousVariable(e.getName()))
                .collect(Collectors.toList());
    }

}
