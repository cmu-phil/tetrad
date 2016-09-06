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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.ModificationRegistery;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetradapp.app.SessionEditorNode;
import edu.cmu.tetradapp.app.SessionNodeConfig;
import edu.cmu.tetradapp.app.SessionNodeModelConfig;
import edu.cmu.tetradapp.model.MultiModelSessionNode;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays editors in a tabbed pane for a list of models.
 *
 * @author Joseph Ramsey
 */
public class MultiModelEditor extends JPanel {

    /**
     * The model for the note.
     */
    private MultiModelSessionNode model;
    private SessionNodeConfig config;
    private List<JPanel> editors;

    /**
     * Constructs the editor given the model
     */
    public MultiModelEditor(MultiModelSessionNode model, SessionNodeConfig config) {
        this.model = model;
        this.config = config;
        setup();
    }

    //============================ Private Methods =========================//

    private void setup() {
        editors = new ArrayList<>();

        if (model.size() == 0) {
            throw new IllegalArgumentException("Is seems there is not model for this box. That's not good.");
        } else if (model.size() == 1) {
            SessionModel _model = model.get(0);

            if (_model instanceof HasSpecialEditor) {
                Class<?> modelClass = model.getClass();
                SessionNodeModelConfig modelConfig = this.config.getModelConfig(modelClass);

                Object[] arguments = new Object[]{model};
                JPanel editor = modelConfig.getEditorInstance(arguments);

                editors.add(editor);

                setLayout(new BorderLayout());
                add(editor, BorderLayout.CENTER);
            } else {
                SessionNodeModelConfig modelConfig = this.config.getModelConfig(model.getClass());

                Object[] arguments = new Object[]{model};
                JPanel editor = modelConfig.getEditorInstance(arguments);

                editors.add(editor);

                setLayout(new BorderLayout());
                add(editor, BorderLayout.CENTER);
            }
        } else {
            JTabbedPane pane = new JTabbedPane(JTabbedPane.LEFT);

            for (int i = 0; i < model.size(); i++) {
                Object _model = model.get(i);

                SessionNodeModelConfig modelConfig = this.config.getModelConfig(model.get(i).getClass());

                Object[] arguments = new Object[]{model};
                JPanel editor = modelConfig.getEditorInstance(arguments);

                editors.add(editor);
                pane.add("" + (i + 1), editor);

                setLayout(new BorderLayout());
                add(pane, BorderLayout.CENTER);
            }
        }
    }

    public MultiModelSessionNode getModels() {
        return model;
    }
}



