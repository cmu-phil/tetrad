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

import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DatasetFileReader;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * Jan 2, 2019 2:40:26 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class VerticalDiscreteTabularDatasetFileReader extends DatasetFileReader implements VerticalDiscreteTabularDatasetReader {

    private boolean hasHeader;
    private char quoteChar;

    public VerticalDiscreteTabularDatasetFileReader(final Path dataFile, final Delimiter delimiter) {
        super(dataFile, delimiter);
        this.hasHeader = this.hasHeader = true;
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

        final boolean isDiscrete = true;
        final DataColumn[] dataColumns = this.hasHeader
                ? columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete)
                : columnReader.generateColumns(new int[0], isDiscrete);

        final TabularDataReader dataReader = new TabularDataFileReader(this.dataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteChar);
        dataReader.setMissingDataMarker(this.missingDataMarker);

        return dataReader.read(dataColumns, this.hasHeader);
    }

    @Override
    public Data readInData(final int[] columnsToExclude) throws IOException {
        final TabularColumnReader columnReader = new TabularColumnFileReader(this.dataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteChar);

        final boolean isDiscrete = true;
        final DataColumn[] dataColumns = this.hasHeader
                ? columnReader.readInDataColumns(columnsToExclude, isDiscrete)
                : columnReader.generateColumns(columnsToExclude, isDiscrete);

        final TabularDataReader dataReader = new TabularDataFileReader(this.dataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteChar);
        dataReader.setMissingDataMarker(this.missingDataMarker);

        return dataReader.read(dataColumns, this.hasHeader);
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
