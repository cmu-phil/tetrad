///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.TimeSeriesData;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * Displays a DataSet object as a JTable.
 *
 * @author Joseph Ramsey
 */
public class TimeSeriesDataDisplayJTable extends JTable
        implements DataModelContainer {

    /**
     * Constructor. Takes a DataSet as a model.
     */
    public TimeSeriesDataDisplayJTable(TimeSeriesData model) {
        setModel(new TimeSeriesDataDisplayTable(model));
        setDefaultEditor(Number.class, new NumberCellEditor());
        setDefaultRenderer(Number.class, new NumberCellRenderer());
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        int rowCount = dataModel.getRowCount();
        int max = 0;

        while (rowCount > 0) {
            rowCount /= 10;
            max++;
        }

        getColumnModel().getColumn(0).setMaxWidth(10 * max);
        getColumnModel().getColumn(0).setCellRenderer(new RowNumberRenderer2());
    }

    /**
     * @return the underlying DataSet model.
     */
    private TimeSeriesData getDataSet() {
        TimeSeriesDataDisplayTable dataDisplayTableModelDataSet =
                (TimeSeriesDataDisplayTable) getModel();
        return dataDisplayTableModelDataSet.getDataSet();
    }

    public DataModel getDataModel() {
        return getDataSet();
    }
}

/**
 * Wraps a dataSet which is possibly smaller than the display window in a larger
 * AbstractTableModel which will fill the window.
 *
 * @author Joseph Ramsey
 */
class TimeSeriesDataDisplayTable extends AbstractTableModel {

    /**
     * The DataSet being displayed.
     */
    private TimeSeriesData dataSet;

    /**
     * The number of columns in the data set.
     */
    private int colCount;

    /**
     * The number of rows in the data set.
     */
    private int maxRowCount;

    /**
     * Constructs a new DisplayTableModel to wrap the given dataSet.
     *
     * @param dataSet the dataSet.
     */
    public TimeSeriesDataDisplayTable(TimeSeriesData dataSet) {
        this.dataSet = dataSet;
        colCount = dataSet.getNumVars();
        maxRowCount = dataSet.getNumTimePoints();
    }

    /**
     * @return the name of the column at position 'col'.
     *
     * @param col the position of the column whose name is requested.
     */
    public String getColumnName(int col) {

        if (col == 0) {
            return "";    // This column displays the row number.
        }

        if (col < colCount + 1) {
            return (String) dataSet.getVariableNames().get(col - 1);
        }
        else {
            return null;
        }
    }

    /**
     * @return the number of rows in the wrapper table model. Guarantees that
     * this number will be at least 100.
     *
     */
    public int getRowCount() {
        return (maxRowCount < 100) ? 100 : maxRowCount;
    }

    /**
     * @return the number of columns in the wrapper table model. Guarantees that
     * this number will be at least 30.
     */
    public int getColumnCount() {
        return (colCount < 30) ? 30 : colCount + 1;
    }

    /**
     * @return the value at the given (row, column) coordinates of the table
     * as an Object.  If the variable for the column is a DiscreteVariable, the
     * String value (as opposed to the integer index value) is extracted
     * and returned.  If the coordinates are out of range of the wrapped table
     * model, 'null' is returned. Otherwise, the value stored in the wrapped
     * table model at the given coordinates is returned.
     */
    public Object getValueAt(int row, int column) {
        if (column == 0) {
            return row + 1;    // present as 1-indexed.
        }
        else if (column < dataSet.getNumVars() + 1 &&
                row < dataSet.getNumTimePoints()) {
            return this.dataSet.getDatum(row, column - 1);
        }
        else {
            return null;
        }
    }

    /**
     * @return the class of the column.
     */
    public Class getColumnClass(int col) {
        return Number.class;
    }

    /**
     * @return the DataSet being presented.
     */
    public TimeSeriesData getDataSet() {
        return dataSet;
    }
}

/**
 * Displays the row number.
 */
class RowNumberRenderer2 implements TableCellRenderer {

    /**
     * @return a label stylized for presenting row numbers in the 0th column.
     */
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = new JLabel(Integer.toString(row + 1));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }
}





