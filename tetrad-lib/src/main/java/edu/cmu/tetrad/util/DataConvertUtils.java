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
import edu.pitt.dbmi.data.reader.ContinuousData;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import edu.pitt.dbmi.data.reader.MixedTabularData;
import edu.pitt.dbmi.data.reader.VerticalDiscreteData;
import edu.pitt.dbmi.data.reader.covariance.CovarianceData;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Dec 15, 2018 11:10:30 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DataConvertUtils {

    private DataConvertUtils() {
    }

    public static DataModel toDataModel(Data data) {
        if (data instanceof ContinuousData) {
            return toContinuousDataModel((ContinuousData) data);
        } else if (data instanceof VerticalDiscreteData) {
            return toVerticalDiscreteDataModel((VerticalDiscreteData) data);
        } else if (data instanceof MixedTabularData) {
            return toMixedDataBox((MixedTabularData) data);
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

    public static DataModel toMixedDataBox(MixedTabularData dataset) {
        int numOfRows = dataset.getNumOfRows();
        DiscreteDataColumn[] columns = dataset.getDataColumns();
        double[][] continuousData = dataset.getContinuousData();
        int[][] discreteData = dataset.getDiscreteData();

        List<Node> nodes = Arrays.stream(columns)
                .map(e -> e.getDataColumn().isDiscrete()
                ? new DiscreteVariable(e.getDataColumn().getName(), e.getCategories())
                : new ContinuousVariable(e.getDataColumn().getName()))
                .collect(Collectors.toList());

        return new BoxDataSet(new MixedDataBox(nodes, numOfRows, continuousData, discreteData), nodes);
    }

    public static DataModel toVerticalDiscreteDataModel(VerticalDiscreteData dataset) {
        DataBox dataBox = new VerticalIntDataBox(dataset.getData());
        List<Node> variables = toNodes(dataset.getDataColumns());

        return new BoxDataSet(dataBox, variables);
    }

    public static DataModel toContinuousDataModel(ContinuousData dataset) {
        DataBox dataBox = new DoubleDataBox(dataset.getData());
        List<Node> variables = toNodes(dataset.getDataColumns());

        return new BoxDataSet(dataBox, variables);
    }

    public static List<Node> toNodes(List<String> variables) {
        return variables.stream()
                .map(ContinuousVariable::new)
                .collect(Collectors.toList());
    }

    public static List<Node> toNodes(DiscreteDataColumn[] columns) {
        return Arrays.stream(columns)
                .map(e -> new DiscreteVariable(e.getDataColumn().getName(), e.getCategories()))
                .collect(Collectors.toList());
    }

    public static List<Node> toNodes(DataColumn[] columns) {
        return Arrays.stream(columns)
                .map(e -> new ContinuousVariable(e.getName()))
                .collect(Collectors.toList());
    }

}
