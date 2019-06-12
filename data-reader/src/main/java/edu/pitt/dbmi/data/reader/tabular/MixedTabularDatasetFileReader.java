/*
 * Copyright (C) 2019 University of Pittsburgh.
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
package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.ContinuousData;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DatasetFileReader;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.DiscreteData;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 *
 * Dec 14, 2018 1:54:31 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MixedTabularDatasetFileReader extends DatasetFileReader implements MixedTabularDatasetReader {

    private final int numberOfDiscreteCategories;
    private boolean hasHeader;
    private char quoteChar;

    public MixedTabularDatasetFileReader(Path dataFile, Delimiter delimiter, int numberOfDiscreteCategories) {
        super(dataFile, delimiter);
        this.numberOfDiscreteCategories = numberOfDiscreteCategories;
        this.hasHeader = true;
        this.quoteChar = '"';
    }

    @Override
    public Data readInData() throws IOException {
        return readInData(Collections.EMPTY_SET);
    }

    @Override
    public Data readInData(Set<String> namesOfColumnsToExclude) throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteChar);

        boolean isDiscrete = false;
        DataColumn[] dataColumns = hasHeader
                ? columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete)
                : columnReader.generateColumns(new int[0], isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(dataFile, delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteChar);
        dataReader.setMissingDataMarker(missingDataMarker);

        dataReader.determineDiscreteDataColumns(dataColumns, numberOfDiscreteCategories, hasHeader);

        return toMixedData(dataReader.read(dataColumns, hasHeader));
    }

    @Override
    public Data readInData(int[] columnsToExclude) throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteChar);

        boolean isDiscrete = false;
        DataColumn[] dataColumns = hasHeader
                ? columnReader.readInDataColumns(columnsToExclude, isDiscrete)
                : columnReader.generateColumns(columnsToExclude, isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(dataFile, delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteChar);
        dataReader.setMissingDataMarker(missingDataMarker);

        dataReader.determineDiscreteDataColumns(dataColumns, numberOfDiscreteCategories, hasHeader);

        return toMixedData(dataReader.read(dataColumns, hasHeader));
    }

    private Data toMixedData(Data data) {
        if (data instanceof ContinuousData) {
            ContinuousData continuousData = (ContinuousData) data;
            double[][] contData = continuousData.getData();
            int numOfRows = contData.length;
            int numOfCols = contData[0].length;

            // convert to mixed variables
            DiscreteDataColumn[] columns = Arrays.stream(continuousData.getDataColumns())
                    .map(MixedTabularDataColumn::new)
                    .toArray(DiscreteDataColumn[]::new);

            // transpose the data
            double[][] vertContData = new double[numOfCols][numOfRows];
            for (int row = 0; row < numOfRows; row++) {
                for (int col = 0; col < numOfCols; col++) {
                    vertContData[col][row] = contData[row][col];
                }
            }

            return new MixedTabularData(numOfRows, columns, vertContData, new int[0][0]);
        } else if (data instanceof DiscreteData) {
            DiscreteData verticalDiscreteData = (DiscreteData) data;
            int[][] discreteData = verticalDiscreteData.getData();
            int numOfRows = discreteData[0].length;

            // convert to mixed variables
            DiscreteDataColumn[] columns = Arrays.stream(verticalDiscreteData.getDataColumns())
                    .map(e -> {
                        DiscreteDataColumn column = new MixedTabularDataColumn(e.getDataColumn());
                        e.getCategories().forEach(v -> column.setValue(v));
                        e.recategorize();

                        return column;
                    }).toArray(DiscreteDataColumn[]::new);

            return new MixedTabularData(numOfRows, columns, new double[0][0], discreteData);
        } else if (data instanceof MixedTabularData) {
            MixedTabularData mixedTabularData = (MixedTabularData) data;
            DiscreteDataColumn[] columns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();
            int numOfRows = mixedTabularData.getNumOfRows();

            return new MixedTabularData(numOfRows, columns, continuousData, discreteData);
        } else {
            return null;
        }
    }

    @Override
    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    @Override
    public void setQuoteCharacter(char quoteCharacter) {
        this.quoteChar = quoteCharacter;
    }

}
