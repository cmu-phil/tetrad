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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * Created by IntelliJ IDEA.
 * <p>
 * Borrows from the histogram stuff yet again
 *
 * @author Michael Freenor
 */
class NormalityTestEditorPanel extends JPanel {


    /**
     * Combo box of all the variables.
     */
    private final JComboBox variableBox;

    /**
     * The dataset being viewed.
     */
    private final DataSet dataSet;


    /**
     * Constructs the editor panel given the initial histogram and the dataset.
     *
     * @param qqPlot  a {@link edu.cmu.tetradapp.editor.QQPlot} object
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public NormalityTestEditorPanel(QQPlot qqPlot, DataSet dataSet) {
        //   construct components
        this.setLayout(new BorderLayout());
        // first build histogram and components used in the editor.
        Node selected = qqPlot.getSelectedVariable();
        this.dataSet = dataSet;
        this.variableBox = new JComboBox();
        ListCellRenderer renderer = new VariableBoxRenderer();
        this.variableBox.setRenderer(renderer);
        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.variableBox.addItem(node);
                if (node == selected) {
                    this.variableBox.setSelectedItem(node);
                }
            }
        }
        this.variableBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                Node node = (Node) e.getItem();
                new QQPlot(NormalityTestEditorPanel.this.dataSet, node);
                changeNormalityTest(NormalityTests.runNormalityTests(NormalityTestEditorPanel.this.dataSet, (ContinuousVariable) node));
            }
        });

        // build the gui.
        this.add(buildEditArea(), BorderLayout.CENTER);
    }

    //========================== Private Methods ================================//

    private static void setPreferredAsMax(JComponent component) {
        component.setMaximumSize(component.getPreferredSize());

    }

    private void changeNormalityTest(String test) {
        // fire event
        this.firePropertyChange("histogramChange", null, test);
    }

    private Box buildEditArea() {
        NormalityTestEditorPanel.setPreferredAsMax(this.variableBox);

        Box main = Box.createVerticalBox();
        Box hBox = Box.createHorizontalBox();
        hBox.add(Box.createHorizontalStrut(10));
        hBox.add(new JLabel("Select Variable: "));
        hBox.add(Box.createHorizontalStrut(10));
        hBox.add(this.variableBox);
        hBox.add(Box.createHorizontalGlue());
        main.add(hBox);
        main.add(Box.createVerticalStrut(5));
        Box hBox2 = Box.createHorizontalBox();
        hBox2.add(Box.createHorizontalStrut(10));
        //hBox2.add(this.categoryField);
        hBox2.add(Box.createHorizontalGlue());
        main.add(hBox2);
        main.add(Box.createVerticalStrut(5));

        main.add(Box.createVerticalStrut(10));
        main.add(Box.createVerticalGlue());

        return main;
    }

    //========================== Inner classes ===========================//


    private static class VariableBoxRenderer extends DefaultListCellRenderer {

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Node node = (Node) value;
            if (node == null) {
                this.setText("");
            } else {
                this.setText(node.getName());
            }
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            return this;
        }
    }


}





