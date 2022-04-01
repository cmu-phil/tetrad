///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.session.ModificationRegistery;
import edu.cmu.tetradapp.util.EditorWindowIndirectRef;
import edu.cmu.tetradapp.util.FinalizingEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Launches a dialog to display an editor component.
 *
 * @author Gregory Li, Joseph Ramsey
 */
public class EditorWindow extends JInternalFrame
        implements EditorWindowIndirectRef, Comparable {

    private JPanel editor;

    /**
     * Set to true if the dialog was canceled.
     */
    private boolean canceled;

    /**
     * The name of the main button; normally "Save."
     */
    private final String buttonName;

    /**
     * The bounds of the source component.
     */
    private final Component centeringComp;

    /**
     * The button the user clicks to dismiss the dialog.
     */
    private JButton okButton;

    /**
     * Pops a new editor window up from a dialog.
     */
    public EditorWindow(JPanel editor, String title, String buttonName,
                        boolean cancellable, Component centeringComp) {
        super(title, true, true, true, false);

        if (editor == null) {
            throw new NullPointerException("Editor must not be null.");
        }

        this.buttonName = buttonName;
        doSetup(editor, cancellable);

        this.centeringComp = centeringComp;

        setClosable(false);
    }

    @Override
    public int compareTo(@NotNull Object o) {
        EditorWindow to = (EditorWindow) o;
        return ((EditorWindow) o).getName().compareTo(to.getName());

    }

    /**
     * Constructs the dialog.
     */
    private void doSetup(JPanel editor, boolean cancellable) {
        this.editor = editor;
        this.okButton = null;

        if (this.buttonName != null) {
            this.okButton = new JButton(this.buttonName);
        }

        JButton cancelButton = new JButton("Cancel");

        if (this.okButton != null) {
            this.okButton.setPreferredSize(new Dimension(100, 50));
            this.okButton.addActionListener(new OkListener());
        }

        cancelButton.setPreferredSize(new Dimension(100, 50));
        cancelButton.addActionListener(new CancelListener());

        Box b0 = Box.createVerticalBox();
        Box b = Box.createHorizontalBox();

        b.add(Box.createHorizontalGlue());
        if (this.okButton != null) {
            b.add(this.okButton);
        }
        b.add(Box.createHorizontalStrut(5));

        if (cancellable) {
            b.add(cancelButton);
        }

        b.add(Box.createHorizontalGlue());

        b0.add(editor);
        b0.add(b);

        Dimension screensize = Toolkit.getDefaultToolkit().getScreenSize();

        int width = Math.min(b0.getPreferredSize().width + 50, screensize.width);
        int height = Math.min(b0.getPreferredSize().height + 50, screensize.height - 100);

        if (!(editor instanceof DoNotScroll) && (b0.getPreferredSize().width > width || b0.getPreferredSize().height > height)) {
            JScrollPane scroll = new JScrollPane(b0);
            scroll.setPreferredSize(new Dimension(width, height));
            getContentPane().add(scroll);
        } else {
            getContentPane().add(b0);
        }

        // Set the ok button so that pressing enter activates it.
        // jdramsey 5/5/02
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) {
            root.setDefaultButton(this.okButton);
        }

        pack();
    }

    /**
     * Closes the dialog.
     */
    public void closeDialog() {
        setVisible(false);
        ModificationRegistery.unregisterEditor(getEditor());
        doDefaultCloseAction();
    }

    public boolean isCanceled() {
        return this.canceled;
    }

    private JComponent getEditor() {
        return this.editor;
    }

    public Component getCenteringComp() {
        return this.centeringComp;
    }

    private class OkListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            if (EditorWindow.this.editor instanceof FinalizingEditor) {
                boolean ok = ((FinalizingEditor) EditorWindow.this.editor).finalizeEditor();
                if (ok) {
                    closeDialog();
                }
            } else {
                closeDialog();
            }
        }
    }

    private class CancelListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            EditorWindow.this.canceled = true;
            closeDialog();
        }
    }

    /**
     * Adds the action listener to the OK button if it's not null.
     */
    public void addActionListener(ActionListener l) {
        if (this.okButton != null) {
            this.okButton.addActionListener(l);
        }
    }
}
