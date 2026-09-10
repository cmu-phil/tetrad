///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.LongToWideWrapper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Parameter editor for {@link LongToWideWrapper}: a modal wizard dialog. The session editor recognizes a
 * {@link FinalizingParameterEditor} that is also a {@link JDialog} and lets it run its own dialog from
 * {@link #setup()}, taking the answer from {@link #finalizeEdit()}; so the wizard controls its own Back, Next,
 * Finish, and Cancel buttons, and the transform runs only if the user clicked Finish on the last page. The pages
 * themselves live in {@link LongToWideWizardPanel}.
 *
 * @author josephramsey
 */
public class LongToWideParamsEditor extends JDialog implements FinalizingParameterEditor {

    private Parameters params;
    private DataSet sourceDataSet;
    private boolean finished = false;

    /**
     * Constructs the editor.
     */
    public LongToWideParamsEditor() {
        super(ownerOf(JOptionUtils.centeringComp()), "Long to Wide", ModalityType.APPLICATION_MODAL);
    }

    private static Window ownerOf(Component comp) {
        return comp == null ? null : SwingUtilities.getWindowAncestor(comp);
    }

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * {@inheritDoc}
     */
    public void setParentModels(Object[] parentModels) {
        if (parentModels == null) return;

        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper data) {
                DataModel model = data.getSelectedDataModel();
                if (model instanceof DataSet dataSet) {
                    this.sourceDataSet = dataSet;
                }
            }
        }
    }

    /**
     * Builds the wizard and shows it; returns when the user has finished or cancelled.
     */
    public void setup() {
        this.finished = false;

        LongToWideWizardPanel wizard = new LongToWideWizardPanel(this.params, this.sourceDataSet,
                () -> {
                    this.finished = true;
                    dispose();
                },
                () -> {
                    this.finished = false;
                    dispose();
                });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                LongToWideParamsEditor.this.finished = false;
            }
        });

        setContentPane(wizard);
        pack();
        setLocationRelativeTo(JOptionUtils.centeringComp());
        setVisible(true); // modal: blocks until disposed
    }

    /**
     * {@inheritDoc}
     */
    public boolean mustBeShown() {
        return true;
    }

    /**
     * Returns true if the user clicked Finish on the last page; the parameters were written as the user went.
     *
     * @return Whether to run the transform.
     */
    public boolean finalizeEdit() {
        return this.finished;
    }
}
