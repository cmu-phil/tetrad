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



