///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

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
 * @version $Id: $Id
 */
public class MixedTabularDatasetFileReader extends DatasetFileReader implements MixedTabularDatasetReader {

    private final int numberOfDiscreteCategories;
    private boolean hasHeader;
    private char quoteChar;

    /**
     * Constructor.
     *
     * @param dataFile                   The data file.
     * @param delimiter                  The delimiter.
     * @param numberOfDiscreteCategories The number of discrete categories.
     */
    public MixedTabularDatasetFileReader(Path dataFile, Delimiter delimiter, int numberOfDiscreteCategories) {
        super(dataFile, delimiter);
        this.numberOfDiscreteCategories = numberOfDiscreteCategories;
        this.hasHeader = true;
        this.quoteChar = '"';
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reads in the data.
     */
    @Override
    public Data readInData() throws IOException {
        return readInData(Collections.EMPTY_SET);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Data readInData(Set<String> namesOfColumnsToExclude) throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(this.dataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteChar);

        final boolean isDiscrete = false;
        DataColumn[] dataColumns = this.hasHeader
                ? columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete)
                : columnReader.generateColumns(new int[0], isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(this.dataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteChar);
        dataReader.setMissingDataMarker(this.missingDataMarker);

        dataReader.determineDiscreteDataColumns(dataColumns, this.numberOfDiscreteCategories, this.hasHeader);

        return toMixedData(dataReader.read(dataColumns, this.hasHeader));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reads in the data.
     */
    @Override
    public Data readInData(int[] columnsToExclude) throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(this.dataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteChar);

        final boolean isDiscrete = false;
        DataColumn[] dataColumns = this.hasHeader
                ? columnReader.readInDataColumns(columnsToExclude, isDiscrete)
                : columnReader.generateColumns(columnsToExclude, isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(this.dataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteChar);
        dataReader.setMissingDataMarker(this.missingDataMarker);

        dataReader.determineDiscreteDataColumns(dataColumns, this.numberOfDiscreteCategories, this.hasHeader);

        return toMixedData(dataReader.read(dataColumns, this.hasHeader));
    }

    private Data toMixedData(Data data) {
        if (data instanceof ContinuousData continuousData) {
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
        } else if (data instanceof DiscreteData verticalDiscreteData) {
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
        } else if (data instanceof MixedTabularData mixedTabularData) {
            DiscreteDataColumn[] columns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();
            int numOfRows = mixedTabularData.getNumOfRows();

            return new MixedTabularData(numOfRows, columns, continuousData, discreteData);
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets whether the data file has a header.
     */
    @Override
    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the quote character.
     */
    @Override
    public void setQuoteCharacter(char quoteCharacter) {
        this.quoteChar = quoteCharacter;
    }

}

