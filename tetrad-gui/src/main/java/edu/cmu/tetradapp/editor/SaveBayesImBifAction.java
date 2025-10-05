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

import edu.cmu.tetrad.bayes.BayesBifRenderer;
import edu.cmu.tetradapp.model.EditorUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

class SaveBayesImBifAction extends AbstractAction {
    private final BayesImEditor bayesImEditor;

    /**
     * <p>Constructor for SaveBayesImXmlAction.</p>
     *
     * @param bayesImEditor a {@link BayesImEditor} object
     */
    public SaveBayesImBifAction(BayesImEditor bayesImEditor) {
        super("Save Bayes IM as BIF");
        if (bayesImEditor == null) {
            throw new NullPointerException(
                    "BayesImEditorWizard must not be null.");
        }
        this.bayesImEditor = bayesImEditor;
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {

        File outfile = EditorUtils.getSaveFile("bayesim", "bif",
                this.bayesImEditor, false, "Save Bayes IM as BIF...");

        String content = BayesBifRenderer.render(this.bayesImEditor.getWizard().getBayesIm());

        try {
            // Write the string to the file
            Files.write(outfile.toPath(), content.getBytes());
            System.out.println("File written successfully.");
        } catch (IOException e1) {
            System.err.println("An error occurred while writing the file: " + e1.getMessage());
        }
    }
}






