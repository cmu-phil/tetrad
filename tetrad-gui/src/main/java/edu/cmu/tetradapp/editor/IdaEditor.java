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

import edu.cmu.tetrad.graph.NodePair;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetradapp.model.IdaModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * A model for the IdaCheck class. The IdaCheck class calculates IDA effects for a CPDAG G for all pairs distinct (x, y)
 * of variables, where the effect is the minimum IDA effect of x on y, obtained by regressing y on x &cup; S and reporting
 * the regression coefficient. Here, S ranges over sets consisting of possible parents of x in G--that is, a set
 * consisting of a subset of the parents of x in G. The IDA effect of x on y is the minimum of these regression
 * coefficients. The IDA effect of x on y is the minimum of these regression coefficients. The IDA effect of x on y is
 * the minimum of these regression coefficients. Here, x and y may be any nodes in G.
 * <p>
 * This editor displays a table of the results of the IDA check for each (x, y) pair of nodes and gives summary statistics
 * for the results. The table has columns for the pair of nodes, the minimum IDA effect, the maximum IDA effect. As a
 * summary, the average of the minimum squared distances of the coefficients in the SEM IM from the interval of IDA
 * is given. The table can be copied and pasted into a text file or into Excel. The table can be restricted to
 * certain variables by typing the names of the variables in the "Restrict to:" text field. The table can be sorted by
 * clicking on the column headers.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IdaEditor extends JPanel {

    public IdaEditor(IdaModel idaModel) {
        IdaCheck model = idaModel.getIdaCheck();

        // Grab the legal node pairs (i.e. all possible pairs of distinct nodes)
        List<NodePair> pairs = model.getNodePairs();

        // Create a table model for the results of the IDA check
        IdaTableModel tableModel = new IdaTableModel(pairs, model);

        // Create a box layout for the panel, with the table at the left and the summary statistics at the right
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        // Add the table to the left
        JTable table = new JTable(tableModel);
        this.add(new JScrollPane(table));

        // For now add a blank panel to the right
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        this.add(rightPanel);

    }


    /**
     * A table model for the results of the IDA check.
     */
    private static class IdaTableModel extends AbstractTableModel {

        /**
         * The column names for the table.
         */
        private final String[] columnNames = {"Pair", "Min IDA Effect", "Max IDA Effect"};

        /**
         * The data for the table.
         */
        private final Object[][] data;

        /**
         * Constructs a new table model for the results of the IDA check.
         */
        public IdaTableModel(List<NodePair> pairs, IdaCheck model) {

            // Create the data for the table
            this.data = new Object[pairs.size()][3];

            // Fill in the data for the table
            for (int i = 0; i < pairs.size(); i++) {
                NodePair pair = pairs.get(i);
                double min = model.getMinEffect(pair.getFirst(), pair.getSecond());
                double max = model.getMaxEffect(pair.getFirst(), pair.getSecond());
                this.data[i][0] = pair;
                this.data[i][1] = min;
                this.data[i][2] = max;
            }
        }

        @Override
        public int getRowCount() {
            return this.data.length;
        }

        @Override
        public int getColumnCount() {
            return this.columnNames.length;
        }

        @Override
        public String getColumnName(int col) {
            return this.columnNames[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            return this.data[row][col];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    }


}

