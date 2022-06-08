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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.session.Session;

/**
 * Indirect control for the desktop to avoid package cycles. The reference to
 * the desktop is set using the <code>activate</code> method, as a
 * DesktopControllable. Once set, the method calls in the DesktopControllable
 * interface are passed on to it.
 *
 * <p>Note that all argument types are interface-tagged as well to avoid further
 * package cycles.
 *
 * <p>Not pretty, but easier and cleaner by far than passing the reference to the
 * desktop down through all of the relevant classes in tetradapp.
 *
 * @author Joseph Ramsey
 */
public class DesktopController implements DesktopControllable {
    private static DesktopControllable INSTANCE;


    public static DesktopControllable getInstance() {
        return DesktopController.INSTANCE;
    }

    /**
     * Sets the reference to the desktop that will be used throughout the
     * application when needed. Done once when the Tetrad application is
     * launched.
     */
    public static void setReference(DesktopControllable component) {
        DesktopController.INSTANCE = component;
    }

    public void newSessionEditor() {
        DesktopController.getInstance().newSessionEditor();
    }

    public SessionEditorIndirectRef getFrontmostSessionEditor() {
        return DesktopController.getInstance().getFrontmostSessionEditor();
    }

    public void exitProgram() {
        DesktopController.getInstance().exitProgram();
    }

    public boolean existsSessionByName(String name) {
        return DesktopController.getInstance().existsSessionByName(name);
    }

    public Session getSessionByName(String name) {
        return DesktopController.getInstance().getSessionByName(name);
    }

    public void addSessionEditor(SessionEditorIndirectRef editor) {
        DesktopController.getInstance().addSessionEditor(editor);
    }

    public void closeEmptySessions() {
        DesktopController.getInstance().closeAllSessions();
    }

    public void putMetadata(SessionWrapperIndirectRef sessionWrapper,
                            TetradMetadataIndirectRef metadata) {
        DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
    }

    public TetradMetadataIndirectRef getTetradMetadata(
            SessionWrapperIndirectRef sessionWrapper) {
        return DesktopController.getInstance().getTetradMetadata(sessionWrapper);
    }

    public void addEditorWindow(EditorWindowIndirectRef editorWindow, int layer) {
        DesktopController.getInstance().addEditorWindow(editorWindow, layer);
    }


    public void closeFrontmostSession() {
        DesktopController.getInstance().closeFrontmostSession();
    }

    @Override
    public void closeSessionByName(String name) {
        DesktopController.getInstance().closeSessionByName(name);
    }

    public boolean closeAllSessions() {
        return DesktopController.getInstance().closeAllSessions();
    }
}





