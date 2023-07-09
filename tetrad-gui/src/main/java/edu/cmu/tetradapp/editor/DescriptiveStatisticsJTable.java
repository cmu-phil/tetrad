///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Displays a DataSet object as a JTable.
 *
 * @author josephramsey
 */
public class DescriptiveStatisticsJTable extends JTable implements DataModelContainer,
        PropertyChangeListener {

    /**
     * Constructor. Takes a DataSet as a model.
     */
    public DescriptiveStatisticsJTable(DataSet model) {
        DescriptiveStatsModel dataModel = new DescriptiveStatsModel(model);
        setModel(dataModel);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
//		System.out.println("dataModel: "+model.getColumnToTooltip());
        int rowCount = this.dataModel.getRowCount();
        int max = 0;

        while (rowCount > 0) {
            rowCount /= 10;
            max++;
        }

        FontMetrics metrics = getFontMetrics(getFont());

//        getColumnModel().getColumn(0).setMaxWidth(60);
        setRowHeight(metrics.getHeight() + 3);

        setRowSelectionAllowed(true);
        getColumnModel().setColumnSelectionAllowed(true);

        for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
            final TableColumnModel columnModel1 = getColumnModel();

            if (i == 0) {
                columnModel1.getColumn(i).setCellRenderer(new LeftAlignRenderer());
            } else {
                columnModel1.getColumn(i).setCellRenderer(new RightAlignRenderer());
            }

            setRowSelectionAllowed(true);
//            addColumnSelectionInterval(i + 1, i + 1);
        }

        getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            public void columnAdded(TableColumnModelEvent e) {
            }

            public void columnRemoved(TableColumnModelEvent e) {
            }

            public void columnMoved(TableColumnModelEvent e) {
            }

            public void columnMarginChanged(ChangeEvent e) {
            }

            /**
             * Sets the selection of columns in the model to what's in the
             * display.
             */
            public void columnSelectionChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }

                ListSelectionModel selectionModel = (ListSelectionModel) e
                        .getSource();
                DataSet dataSet = getDataSet();
                dataSet.clearSelection();

                if (!getRowSelectionAllowed()) {
                    for (int i = 0; i < dataSet.getNumColumns(); i++) {
                        if (selectionModel.isSelectedIndex(i + 1)) {
                            dataSet.setSelected(dataSet.getVariable(i), true);
                        }
                    }
                }
            }
        });

        setTransferHandler(new TabularDataTransferHandler());

        addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                JTable table = (JTable) e.getSource();
                TableCellEditor editor = table.getCellEditor();
                if (editor != null) {
                    editor.stopCellEditing();
                }
            }
        });
    }

    public void setValueAt(Object aValue, int row, int column) {
        try {
            super.setValueAt(aValue, row, column);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e
                    .getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * @return the underlying DataSet model.
     */
    public DataSet getDataSet() {
        DescriptiveStatsModel tableModelTabularData = (DescriptiveStatsModel) getModel();
        return tableModelTabularData.getDataSet();
    }

    public void setDataSet(DataSet data) {
        TabularDataTable tableModelTabularData = (TabularDataTable) getModel();
        tableModelTabularData.setDataSet(data);
    }

    public DataModel getDataModel() {
        return getDataSet();
    }

    public void propertyChange(PropertyChangeEvent evt) {
        firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
    }

    public class RightAlignRenderer extends DefaultTableCellRenderer {
        public RightAlignRenderer() {
            setHorizontalAlignment(JLabel.RIGHT);
        }
    }

    public class LeftAlignRenderer extends DefaultTableCellRenderer {
        public LeftAlignRenderer() {
            setHorizontalAlignment(JLabel.LEFT);
        }
    }
}

