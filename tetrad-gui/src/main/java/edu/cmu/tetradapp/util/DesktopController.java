package edu.cmu.tetradapp.util;

import edu.cmu.tetradapp.session.Session;

/**
 * Indirect control for the desktop to avoid package cycles. The reference to the desktop is set using the
 * <code>activate</code> method, as a DesktopControllable. Once set, the method calls in the DesktopControllable
 * interface are passed on to it.
 *
 * <p>Note that all argument types are interface-tagged as well to avoid further
 * package cycles.
 *
 * <p>Not pretty, but easier and cleaner by far than passing the reference to the
 * desktop down through all of the relevant classes in tetradapp.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DesktopController implements DesktopControllable {
    private static DesktopControllable INSTANCE;


    /**
     * <p>getInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.util.DesktopControllable} object
     */
    public static DesktopControllable getInstance() {
        return DesktopController.INSTANCE;
    }

    /**
     * Sets the reference to the desktop that will be used throughout the application when needed. Done once when the
     * Tetrad application is launched.
     *
     * @param component a {@link edu.cmu.tetradapp.util.DesktopControllable} object
     */
    public static void setReference(DesktopControllable component) {
        DesktopController.INSTANCE = component;
    }

    /**
     * <p>newSessionEditor.</p>
     */
    public void newSessionEditor() {
        DesktopController.getInstance().newSessionEditor();
    }

    /**
     * <p>getFrontmostSessionEditor.</p>
     *
     * @return a {@link edu.cmu.tetradapp.util.SessionEditorIndirectRef} object
     */
    public SessionEditorIndirectRef getFrontmostSessionEditor() {
        return DesktopController.getInstance().getFrontmostSessionEditor();
    }

    /**
     * <p>exitProgram.</p>
     */
    public void exitProgram() {
        DesktopController.getInstance().exitProgram();
    }

    /**
     * {@inheritDoc}
     */
    public boolean existsSessionByName(String name) {
        return DesktopController.getInstance().existsSessionByName(name);
    }

    /**
     * {@inheritDoc}
     */
    public Session getSessionByName(String name) {
        return DesktopController.getInstance().getSessionByName(name);
    }

    /**
     * {@inheritDoc}
     */
    public void addSessionEditor(SessionEditorIndirectRef editor) {
        DesktopController.getInstance().addSessionEditor(editor);
    }

    /**
     * <p>closeEmptySessions.</p>
     */
    public void closeEmptySessions() {
        DesktopController.getInstance().closeAllSessions();
    }

    /**
     * {@inheritDoc}
     */
    public void putMetadata(SessionWrapperIndirectRef sessionWrapper,
                            TetradMetadataIndirectRef metadata) {
        DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
    }

    /**
     * {@inheritDoc}
     */
    public TetradMetadataIndirectRef getTetradMetadata(
            SessionWrapperIndirectRef sessionWrapper) {
        return DesktopController.getInstance().getTetradMetadata(sessionWrapper);
    }

    /**
     * {@inheritDoc}
     */
    public void addEditorWindow(EditorWindowIndirectRef editorWindow, int layer) {
        DesktopController.getInstance().addEditorWindow(editorWindow, layer);
    }


    /**
     * <p>closeFrontmostSession.</p>
     */
    public void closeFrontmostSession() {
        DesktopController.getInstance().closeFrontmostSession();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeSessionByName(String name) {
        DesktopController.getInstance().closeSessionByName(name);
    }

    /**
     * <p>closeAllSessions.</p>
     *
     * @return a boolean
     */
    public boolean closeAllSessions() {
        return DesktopController.getInstance().closeAllSessions();
    }
}





