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

import edu.pitt.dbmi.data.reader.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Dec 14, 2018 1:54:31 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MixedTabularDatasetFileReader extends DatasetFileReader implements MixedTabularDatasetReader {

    private final int numberOfDiscreteCategories;
    private boolean hasHeader;
    private char quoteChar;

    public MixedTabularDatasetFileReader(final Path dataFile, final Delimiter delimiter, final int numberOfDiscreteCategories) {
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
    public Data readInData(final Set<String> namesOfColumnsToExclude) throws IOException {
        final TabularColumnReader columnReader = new TabularColumnFileReader(this.dataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteChar);

        final boolean isDiscrete = false;
        final DataColumn[] dataColumns = this.hasHeader
                ? columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete)
                : columnReader.generateColumns(new int[0], isDiscrete);

        final TabularDataReader dataReader = new TabularDataFileReader(this.dataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteChar);
        dataReader.setMissingDataMarker(this.missingDataMarker);

        dataReader.determineDiscreteDataColumns(dataColumns, this.numberOfDiscreteCategories, this.hasHeader);

        return toMixedData(dataReader.read(dataColumns, this.hasHeader));
    }

    @Override
    public Data readInData(final int[] columnsToExclude) throws IOException {
        final TabularColumnReader columnReader = new TabularColumnFileReader(this.dataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteChar);

        final boolean isDiscrete = false;
        final DataColumn[] dataColumns = this.hasHeader
                ? columnReader.readInDataColumns(columnsToExclude, isDiscrete)
                : columnReader.generateColumns(columnsToExclude, isDiscrete);

        final TabularDataReader dataReader = new TabularDataFileReader(this.dataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteChar);
        dataReader.setMissingDataMarker(this.missingDataMarker);

        dataReader.determineDiscreteDataColumns(dataColumns, this.numberOfDiscreteCategories, this.hasHeader);

        return toMixedData(dataReader.read(dataColumns, this.hasHeader));
    }

    private Data toMixedData(final Data data) {
        if (data instanceof ContinuousData) {
            final ContinuousData continuousData = (ContinuousData) data;
            final double[][] contData = continuousData.getData();
            final int numOfRows = contData.length;
            final int numOfCols = contData[0].length;

            // convert to mixed variables
            final DiscreteDataColumn[] columns = Arrays.stream(continuousData.getDataColumns())
                    .map(MixedTabularDataColumn::new)
                    .toArray(DiscreteDataColumn[]::new);

            // transpose the data
            final double[][] vertContData = new double[numOfCols][numOfRows];
            for (int row = 0; row < numOfRows; row++) {
                for (int col = 0; col < numOfCols; col++) {
                    vertContData[col][row] = contData[row][col];
                }
            }

            return new MixedTabularData(numOfRows, columns, vertContData, new int[0][0]);
        } else if (data instanceof DiscreteData) {
            final DiscreteData verticalDiscreteData = (DiscreteData) data;
            final int[][] discreteData = verticalDiscreteData.getData();
            final int numOfRows = discreteData[0].length;

            // convert to mixed variables
            final DiscreteDataColumn[] columns = Arrays.stream(verticalDiscreteData.getDataColumns())
                    .map(e -> {
                        final DiscreteDataColumn column = new MixedTabularDataColumn(e.getDataColumn());
                        e.getCategories().forEach(v -> column.setValue(v));
                        e.recategorize();

                        return column;
                    }).toArray(DiscreteDataColumn[]::new);

            return new MixedTabularData(numOfRows, columns, new double[0][0], discreteData);
        } else if (data instanceof MixedTabularData) {
            final MixedTabularData mixedTabularData = (MixedTabularData) data;
            final DiscreteDataColumn[] columns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();
            final int numOfRows = mixedTabularData.getNumOfRows();

            return new MixedTabularData(numOfRows, columns, continuousData, discreteData);
        } else {
            return null;
        }
    }

    @Override
    public void setHasHeader(final boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    @Override
    public void setQuoteCharacter(final char quoteCharacter) {
        this.quoteChar = quoteCharacter;
    }

}
