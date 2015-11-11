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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.session.Session;
import edu.cmu.tetradapp.app.SessionEditor;

/**
 * Interface for desktop controller methods, to allow app components to control
 * the desktop without a package cycle. See TetradDesktop for meaning of
 * methods.
 *
 * @author Joseph Ramsey
 * @see edu.cmu.tetradapp.app.TetradDesktop
 */
public interface DesktopControllable {
    void newSessionEditor();

    SessionEditorIndirectRef getFrontmostSessionEditor();

    void exitProgram();

    boolean existsSessionByName(String name);

    Session getSessionByName(String name);

    void addSessionEditor(SessionEditorIndirectRef editor);

    void closeEmptySessions();

    void putMetadata(SessionWrapperIndirectRef sessionWrapper,
            TetradMetadataIndirectRef metadata);

    TetradMetadataIndirectRef getTetradMetadata(
            SessionWrapperIndirectRef sessionWrapper);

    void addEditorWindow(EditorWindowIndirectRef editorWindow, int layer);


    void closeFrontmostSession();

    void closeSessionByName(String name);

    boolean closeAllSessions();
}



