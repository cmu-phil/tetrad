package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.session.SessionNode;


/**
 * Represents the configuration details for a session node.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface SessionNodeConfig {


    /**
     * <p>getModelConfig.</p>
     *
     * @param model a {@link java.lang.Class<?>} object
     * @return the model config for the model with the given class or null if there isn't one.
     */
    SessionNodeModelConfig getModelConfig(Class<?> model);


    /**
     * <p>getModels.</p>
     *
     * @return all the models for this node.
     */
    Class<?>[] getModels();


    /**
     * <p>getNodeSpecificMessage.</p>
     *
     * @return text to use as a nodeSpecificMessage for the node.
     */
    String getNodeSpecificMessage();


    /**
     * <p>getModelChooserInstance.</p>
     *
     * @param sessionNode - The CessionNode for the getModel node.
     * @return a newly created <code>ModelChooser</code> that should be utilized to select a model. If no chooser was
     * specified then the default chooser will be returned.
     */
    ModelChooser getModelChooserInstance(SessionNode sessionNode);


    /**
     * <p>getSessionDisplayCompInstance.</p>
     *
     * @return a newly created <code>SessionDisplayComp</code> that is used to display the node on the session
     * workbench. If no display component class was specified then a default instance will be used.
     */
    SessionDisplayComp getSessionDisplayCompInstance();


}




