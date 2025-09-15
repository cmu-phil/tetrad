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

import javax.swing.*;
import java.awt.*;

public final class HybridCgImEditorWizard {

    private HybridCgImEditorWizard() {
    }

    public static JDialog create(Window owner, HybridCgImEditor editor) {
        JDialog dlg = new JDialog(owner, "Mixed IM Wizard", Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout());
        content.add(editor, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton load = new JButton("Load from Model");
        JButton apply = new JButton("Apply/Save");
        JButton close = new JButton("Close");

        load.addActionListener(e -> {
            // TODO: load parameters from an existing fitted Mixed/Cg model into the table
            JOptionPane.showMessageDialog(dlg, "Load not yet implemented.");
        });
        apply.addActionListener(e -> {
            // TODO: push back to your data structure or registry
            JOptionPane.showMessageDialog(dlg, "Apply not yet implemented.");
        });
        close.addActionListener(e -> dlg.dispose());

        south.add(load);
        south.add(apply);
        south.add(close);

        content.add(south, BorderLayout.SOUTH);
        dlg.setContentPane(content);
        dlg.setSize(800, 500);
        dlg.setLocationRelativeTo(owner);
        return dlg;
    }
}
