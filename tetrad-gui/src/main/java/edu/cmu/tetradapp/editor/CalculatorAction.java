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

import edu.cmu.tetrad.calculator.Transformation;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.HasCalculatorParams;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.event.ActionEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Action that lauches the calculator editor.
 *
 * @author Tyler Gibson
 */
class CalculatorAction extends AbstractAction {


    /**
     * The data the calculator is working on.
     */
    private final DataWrapper wrapper;


    /**
     * The data editor, may be null.
     */
    private DataEditor dataEditor;

    /**
     * Constructs the calculator action given the data wrapper to operate on.
     */
    private CalculatorAction(DataWrapper wrapper) {
        super("Calculator ...");
        if (wrapper == null) {
            throw new NullPointerException("DataWrapper was null.");
        }
        this.wrapper = wrapper;
    }


    /**
     * Constructs the calculator given the data editor its attached to.
     *
     * @param editor a {@link edu.cmu.tetradapp.editor.DataEditor} object
     */
    public CalculatorAction(DataEditor editor) {
        this(editor.getDataWrapper());
        this.dataEditor = editor;

    }

    /**
     * {@inheritDoc}
     * <p>
     * Launches the calculator editoir.
     */
    public void actionPerformed(ActionEvent e) {
        CalculatorEditor editor = new CalculatorEditor();

        Parameters params = this.wrapper.getParams();

        if (params instanceof HasCalculatorParams) {
            params = ((HasCalculatorParams) params).getCalculatorParams();
        }

        editor.setParams(params);
        editor.setParentModels(new Object[]{this.wrapper});
        editor.setup();

        EditorWindow editorWindow =
                new EditorWindow(editor, editor.getName(), "Save", true, this.dataEditor);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);


        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosed(InternalFrameEvent e) {
                EditorWindow window = (EditorWindow) e.getSource();

                if (window.isCanceled()) {
                    return;
                }

                if (editor.finalizeEdit()) {
                    List<String> equations = new ArrayList<>();
                    String _displayEquations = Preferences.userRoot().get("calculator_equations", "");
                    String[] displayEquations = _displayEquations.split("///");

                    for (String equation : displayEquations) {
                        if (equation.contains("$")) {
                            for (Node node : editor.getDataSet().getVariables()) {
                                equations.add(equation.replace("$", node.getName()));
                            }
                        } else {
                            equations.add(equation);
                        }
                    }

                    String[] eqs = equations.toArray(new String[0]);
                    try {
                        Transformation.transform(editor.getDataSet(), eqs);
                    } catch (ParseException e1) {
                        throw new IllegalStateException("Parse error while applying equations to dataset.");
                    }
                }
            }
        });
    }
}





