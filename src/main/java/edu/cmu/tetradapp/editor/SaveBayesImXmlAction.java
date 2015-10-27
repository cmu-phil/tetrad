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
    private BayesImEditor bayesImEditor;

    public SaveBayesImXmlAction(BayesImEditor bayesImEditor) {
        super("Save Bayes IM as XML");
        if (bayesImEditor == null) {
            throw new NullPointerException(
                    "BayesImEditorWizard must not be null.");
        }
        this.bayesImEditor = bayesImEditor;
    }

    public void actionPerformed(ActionEvent e) {
        try {

            File outfile = EditorUtils.getSaveFile("bayesim", "xml",
                    this.bayesImEditor, false, "Save Bayes IM as XML...");

            BayesIm bayesIm = bayesImEditor.getWizard().getBayesIm();
            FileOutputStream out = new FileOutputStream(outfile);

            Element element = BayesXmlRenderer.getElement(bayesIm);
            Document document = new Document(element);
            Serializer serializer = new Serializer(out);
            serializer.setLineSeparator("\n");
            serializer.setIndent(2);
            serializer.write(document);
            out.close();
        }
        catch (IOException e1) {
            throw new RuntimeException(e1);
        }
    }
}





