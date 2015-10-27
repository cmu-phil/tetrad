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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.knowledge_editor.KnowledgeEditor;
import edu.cmu.tetradapp.model.KnowledgeEditable;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Builds a menu to edit, copy, paste, save, and load Knowledge. All that's
 * required is that the editor it's attached to implement the KnowledgeEditable
 * class.
 *
 * @author Joseph Ramsey
 */
public class KnowledgeMenu extends JMenu {
    private KnowledgeEditable knowledgeEditable;


    public KnowledgeMenu(final KnowledgeEditable knowledgeEditable) {
        super("Knowledge");
        this.knowledgeEditable = knowledgeEditable;

        JMenuItem editKnowledge = new JMenuItem("Edit Knowledge...");
        add(editKnowledge);
        addSeparator();

        editKnowledge.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	
                IKnowledge knowledge = getKnowledgeEditable().getKnowledge();
                List<String> varList = getKnowledgeEditable().getVarNames();

                Graph sourceGraph = getKnowledgeEditable().getSourceGraph();

                final KnowledgeEditor knowledgeEditor =
                        new KnowledgeEditor(knowledge, varList, sourceGraph);

                EditorWindow window = new EditorWindow(knowledgeEditor,
                        "Knowledge Editor", "Save", true, (Component) knowledgeEditable);
                DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
                window.setVisible(true);

                window.addInternalFrameListener(new InternalFrameAdapter() {
                    public void internalFrameClosed(InternalFrameEvent e) {
                        EditorWindow win = (EditorWindow) e.getSource();

                        if (!win.isCanceled()) {
                            KnowledgeEditor editor =
                                    (KnowledgeEditor) win.getEditor();
                            IKnowledge knowledge = editor.getKnowledge();
                            getKnowledgeEditable().setKnowledge(knowledge);
                        }
                    }
                });
            }
        });

        add(new LoadKnowledgeAction(this.knowledgeEditable));
        add(new SaveKnowledgeAction(this.knowledgeEditable));
        add(new CopyKnowledgeAction(this.knowledgeEditable));
        add(new PasteKnowledgeAction(this.knowledgeEditable));
    }

    private KnowledgeEditable getKnowledgeEditable() {
        return knowledgeEditable;
    }
}





