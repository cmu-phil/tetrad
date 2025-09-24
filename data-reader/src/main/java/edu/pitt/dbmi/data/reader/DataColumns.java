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

package edu.pitt.dbmi.data.reader;

import edu.pitt.dbmi.data.reader.metadata.ColumnMetadata;
import edu.pitt.dbmi.data.reader.metadata.InterventionalColumn;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.tabular.TabularDataColumn;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Jan 2, 2019 10:46:41 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class DataColumns {

    private DataColumns() {
    }

    /**
     * Update data columns with metadata.
     *
     * @param dataColumns data columns
     * @param metadata    metadata
     * @return updated data columns
     */
    public static DataColumn[] update(DataColumn[] dataColumns, Metadata metadata) {
        Map<String, ColumnMetadata> columnMetadataMap = DataColumns.getColumnMetadataMap(metadata);

        // update data column's data type and metadata column's column number
        for (DataColumn dataColumn : dataColumns) {
            ColumnMetadata column = columnMetadataMap.get(dataColumn.getName());
            if (column != null) {
                dataColumn.setDiscrete(column.isDiscrete());
                column.setColumnNumber(dataColumn.getColumnNumber());
            }
        }

        // add missing interventional status metadata
        List<DataColumn> additionalColumns = new LinkedList<>();
        int numOfCols = dataColumns.length;
        for (InterventionalColumn column : metadata.getInterventionalColumns()) {
            if (column.getStatusColumn() == null) {
                String name = column.getValueColumn().getName() + "_s";
                final boolean discrete = true;
                int columnNumber = ++numOfCols;

                column.setStatusColumn(new ColumnMetadata(name, columnNumber, discrete));
                additionalColumns.add(new TabularDataColumn(name, columnNumber, true, discrete));
            }
        }

        // add additional columns
        if (!additionalColumns.isEmpty()) {
            DataColumn[] expandedDataColumns = new DataColumn[numOfCols];
            System.arraycopy(dataColumns, 0, expandedDataColumns, 0, dataColumns.length);

            int index = dataColumns.length;
            for (DataColumn dataColumn : additionalColumns) {
                expandedDataColumns[index++] = dataColumn;
            }

            dataColumns = expandedDataColumns;
        }

        return dataColumns;
    }

    private static Map<String, ColumnMetadata> getColumnMetadataMap(Metadata metadata) {
        Map<String, ColumnMetadata> columnMetadataMap = new HashMap<>();

        // get metadata for domain
        metadata.getDomainColumnns()
                .forEach(column -> columnMetadataMap.put(column.getName(), column));

        // get metadata for intervention
        metadata.getInterventionalColumns()
                .forEach(column -> {
                    ColumnMetadata col = column.getValueColumn();
                    if (col != null) {
                        columnMetadataMap.put(col.getName(), col);
                    }

                    col = column.getStatusColumn();
                    if (col != null) {
                        columnMetadataMap.put(col.getName(), col);
                    }
                });

        return columnMetadataMap;
    }

}

