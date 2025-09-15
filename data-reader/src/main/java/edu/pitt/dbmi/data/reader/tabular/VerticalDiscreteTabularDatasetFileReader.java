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
 * @version $Id: $Id
 */
public class VerticalDiscreteTabularDatasetFileReader extends DatasetFileReader implements VerticalDiscreteTabularDatasetReader {

    private boolean hasHeader;
    private char quoteChar;

    /**
     * Constructor.
     *
     * @param dataFile  The data file.
     * @param delimiter The delimiter.
     */
    public VerticalDiscreteTabularDatasetFileReader(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);
        this.hasHeader = this.hasHeader = true;
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
     * <p>
     * Reads in the data.
     */
    @Override
    public Data readInData(Set<String> namesOfColumnsToExclude) throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(this.dataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteChar);

        final boolean isDiscrete = true;
        DataColumn[] dataColumns = this.hasHeader
                ? columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete)
                : columnReader.generateColumns(new int[0], isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(this.dataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteChar);
        dataReader.setMissingDataMarker(this.missingDataMarker);

        return dataReader.read(dataColumns, this.hasHeader);
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

        final boolean isDiscrete = true;
        DataColumn[] dataColumns = this.hasHeader
                ? columnReader.readInDataColumns(columnsToExclude, isDiscrete)
                : columnReader.generateColumns(columnsToExclude, isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(this.dataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteChar);
        dataReader.setMissingDataMarker(this.missingDataMarker);

        return dataReader.read(dataColumns, this.hasHeader);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Set whether the data has a header.
     */
    @Override
    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Set the quote character.
     */
    @Override
    public void setQuoteCharacter(char quoteCharacter) {
        this.quoteChar = quoteCharacter;
    }

}

