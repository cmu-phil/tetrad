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

import edu.cmu.tetrad.session.ModificationRegistery;
import edu.cmu.tetradapp.util.EditorWindowIndirectRef;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
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
    private boolean canceled = false;

    /**
     * The name of the main button; normally "Save."
     */
    private String buttonName;

    /**
     * The bounds of the source component.
     */
    private Component centeringComp;

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

//        if (buttonName == null) {
//            throw new NullPointerException("Button name must not be null.");
//        }

        this.buttonName = buttonName;
        doSetup(editor, cancellable);

        this.centeringComp = centeringComp;
    }

    @Override
    public int compareTo(Object o) {
        EditorWindow to = (EditorWindow) o;
        return ((EditorWindow) o).getName().compareTo(to.getName());

    }

    /**
     * Constructs the dialog.
     */
    private void doSetup(JPanel editor, boolean cancellable) {
        this.editor = editor;

        addInternalFrameListener(new InternalFrameAdapter() {
            public void InternalFrameClosing(InternalFrameEvent evt) {
                canceled = true;
                closeDialog();
            }
        });

        okButton = null;

        if (buttonName != null) {
            okButton = new JButton(buttonName);
        }

        JButton cancelButton = new JButton("Cancel");

        if (okButton != null) {
            okButton.setPreferredSize(new Dimension(70, 50));
            okButton.addActionListener(new OkListener());
        }

        cancelButton.setPreferredSize(new Dimension(80, 50));
        cancelButton.addActionListener(new CancelListener());

        Box b0 = Box.createVerticalBox();
        Box b = Box.createHorizontalBox();

        b.add(Box.createHorizontalGlue());
        if (okButton != null) {
            b.add(okButton);
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
        }
        else {
            getContentPane().add(b0);
        }

        // Set the ok button so that pressing enter activates it.
        // jdramsey 5/5/02
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) {
            root.setDefaultButton(okButton);
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
        return canceled;
    }

    public JComponent getEditor() {
        return editor;
    }

    public Component getCenteringComp() {
        return centeringComp;
    }

    class OkListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            closeDialog();
        }
    }

    class CancelListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            canceled = true;
            closeDialog();
        }
    }

    /**
     * Adds the action listener to the OK button if it's not null.
     */
    public void addActionListener(ActionListener l) {
        if (okButton != null) {
            okButton.addActionListener(l);
        }
    }
}





