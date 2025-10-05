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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesXmlRenderer;
import edu.cmu.tetradapp.model.EditorUtils;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Serializer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

class SaveBayesImXmlAction extends AbstractAction {
    private final BayesImEditor bayesImEditor;

    /**
     * <p>Constructor for SaveBayesImXmlAction.</p>
     *
     * @param bayesImEditor a {@link edu.cmu.tetradapp.editor.BayesImEditor} object
     */
    public SaveBayesImXmlAction(BayesImEditor bayesImEditor) {
        super("Save Bayes IM as XML");
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
        try {

            File outfile = EditorUtils.getSaveFile("bayesim", "xml",
                    this.bayesImEditor, false, "Save Bayes IM as XML...");

            BayesIm bayesIm = this.bayesImEditor.getWizard().getBayesIm();
            FileOutputStream out = new FileOutputStream(outfile);

            Element element = BayesXmlRenderer.getElement(bayesIm);
            Document document = new Document(element);
            Serializer serializer = new Serializer(out);
            serializer.setLineSeparator("\n");
            serializer.setIndent(2);
            serializer.write(document);
            out.close();
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
    }
}






