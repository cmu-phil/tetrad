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

import edu.cmu.tetradapp.session.Session;

/**
 * Interface for desktop controller methods, to allow app components to control the desktop without a package cycle. See
 * TetradDesktop for meaning of methods.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetradapp.app.TetradDesktop
 */
public interface DesktopControllable {
    /**
     * <p>newSessionEditor.</p>
     */
    void newSessionEditor();

    /**
     * <p>getFrontmostSessionEditor.</p>
     *
     * @return a {@link edu.cmu.tetradapp.util.SessionEditorIndirectRef} object
     */
    SessionEditorIndirectRef getFrontmostSessionEditor();

    /**
     * <p>exitProgram.</p>
     */
    void exitProgram();

    /**
     * <p>existsSessionByName.</p>
     *
     * @param name a {@link java.lang.String} object
     * @return a boolean
     */
    boolean existsSessionByName(String name);

    /**
     * <p>getSessionByName.</p>
     *
     * @param name a {@link java.lang.String} object
     * @return a {@link Session} object
     */
    Session getSessionByName(String name);

    /**
     * <p>addSessionEditor.</p>
     *
     * @param editor a {@link edu.cmu.tetradapp.util.SessionEditorIndirectRef} object
     */
    void addSessionEditor(SessionEditorIndirectRef editor);

    /**
     * <p>closeEmptySessions.</p>
     */
    void closeEmptySessions();

    /**
     * <p>putMetadata.</p>
     *
     * @param sessionWrapper a {@link edu.cmu.tetradapp.util.SessionWrapperIndirectRef} object
     * @param metadata       a {@link edu.cmu.tetradapp.util.TetradMetadataIndirectRef} object
     */
    void putMetadata(SessionWrapperIndirectRef sessionWrapper,
                     TetradMetadataIndirectRef metadata);

    /**
     * <p>getTetradMetadata.</p>
     *
     * @param sessionWrapper a {@link edu.cmu.tetradapp.util.SessionWrapperIndirectRef} object
     * @return a {@link edu.cmu.tetradapp.util.TetradMetadataIndirectRef} object
     */
    TetradMetadataIndirectRef getTetradMetadata(
            SessionWrapperIndirectRef sessionWrapper);

    /**
     * <p>addEditorWindow.</p>
     *
     * @param editorWindow a {@link edu.cmu.tetradapp.util.EditorWindowIndirectRef} object
     * @param layer        a int
     */
    void addEditorWindow(EditorWindowIndirectRef editorWindow, int layer);


    /**
     * <p>closeFrontmostSession.</p>
     */
    void closeFrontmostSession();

    /**
     * <p>closeSessionByName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    void closeSessionByName(String name);

    /**
     * <p>closeAllSessions.</p>
     *
     * @return a boolean
     */
    boolean closeAllSessions();
}



