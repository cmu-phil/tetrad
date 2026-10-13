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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The "Plot Matrix for Selected" button for editors that show a model's graph beside the data it was estimated
 * from: it opens a plot matrix over the variables selected in the graph. Selected nodes with no column of the same
 * name in the data (latents, error terms) are left out. Does not modify the data or the graph.
 * <p>
 * The graph and the data are asked for when the button is clicked, since an editor may rebuild its graph or change
 * the data set it is estimating from while it is open.
 *
 * @author josephramsey
 */
public final class PlotMatrixForSelected {

    private PlotMatrixForSelected() {
    }

    /**
     * Builds the button.
     *
     * @param workbench gives the workbench whose selected nodes are plotted
     * @param dataSet   gives the data set to plot from first; may give null if there is no tabular data
     * @param choices   gives the data models the plot matrix may switch among (non-tabular ones are ignored); may
     *                  give null
     * @return the button
     */
    public static JButton button(Supplier<GraphWorkbench> workbench, Supplier<DataSet> dataSet,
                                 Supplier<? extends List<? extends DataModel>> choices) {
        return button(workbench, dataSet, choices, null);
    }

    /**
     * Builds the button, for an editor that also keeps a list of selected variables apart from the graph.
     *
     * @param workbench gives the workbench whose selected nodes are plotted
     * @param dataSet   gives the data set to plot from first; may give null if there is no tabular data
     * @param choices   gives the data models the plot matrix may switch among (non-tabular ones are ignored); may
     *                  give null
     * @param fallback  gives the variables to plot when no node is selected in the graph; null for none
     * @return the button
     */
    public static JButton button(Supplier<GraphWorkbench> workbench, Supplier<DataSet> dataSet,
                                 Supplier<? extends List<? extends DataModel>> choices,
                                 Supplier<? extends List<Node>> fallback) {
        JButton plot = new JButton("Plot Matrix for Selected");
        plot.setToolTipText(fallback == null ? "Open a plot matrix over the variables selected in the graph."
                : "Open a plot matrix over the variables selected in the graph, or, if none are selected there,"
                  + " over the selected variables list.");

        plot.addActionListener(e -> {
            GraphWorkbench graphWorkbench = workbench.get();
            DataSet data = dataSet.get();

            if (data == null || data.getNumRows() == 0) {
                JOptionPane.showMessageDialog(plot, "There is no tabular data set to plot from.");
                return;
            }

            List<Node> chosen = new ArrayList<>();

            if (graphWorkbench != null) {
                for (DisplayNode displayNode : graphWorkbench.getSelectedNodes()) {
                    if (displayNode.getModelNode() != null) chosen.add(displayNode.getModelNode());
                }
            }

            if (chosen.isEmpty() && fallback != null && fallback.get() != null) {
                chosen.addAll(fallback.get());
            }

            List<Node> variables = new ArrayList<>();

            for (Node node : chosen) {
                Node variable = data.getVariable(node.getName());
                if (variable != null && !variables.contains(variable)) variables.add(variable);
            }

            if (variables.isEmpty()) {
                JOptionPane.showMessageDialog(plot,
                        "Select one or more variables that are in the data, then click this button.");
                return;
            }

            StringBuilder title = new StringBuilder("Plot Matrix: ");

            for (int i = 0; i < variables.size() && i < 4; i++) {
                if (i > 0) title.append(", ");
                title.append(variables.get(i).getName());
            }

            if (variables.size() > 4) {
                title.append(", ... (").append(variables.size()).append(" variables)");
            }

            PlotMatrix panel = new PlotMatrix(data, variables, variables, variables);

            List<DataSet> dataSets = new ArrayList<>();
            List<? extends DataModel> models = choices == null ? null : choices.get();

            if (models != null) {
                for (DataModel model : models) {
                    if (model instanceof DataSet tabular && tabular.getNumRows() > 0) dataSets.add(tabular);
                }
            }

            panel.setDataSetChoices(dataSets, data);

            EditorWindow window = new EditorWindow(panel, title.toString(), null, false, plot);
            DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
            window.pack();
            window.setVisible(true);
        });

        return plot;
    }
}
