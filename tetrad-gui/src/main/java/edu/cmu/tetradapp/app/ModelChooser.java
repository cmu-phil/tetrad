package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.session.SessionNode;

import java.util.List;

/**
 * Represents a device that allows one to select between available models.  A chooser must have an empty constructor,
 * after construction the chooser's set methods will called in the following order: setId(), setTitle(), setNodeName(),
 * setModelConfigs(). After all set methods have been called the setup() method should be called.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface ModelChooser {


    /**
     * <p>getTitle.</p>
     *
     * @return the title of the chooser.
     */
    String getTitle();

    /**
     * <p>setTitle.</p>
     *
     * @param title The title to use for the chooser.
     */
    void setTitle(String title);

    /**
     * <p>getSelectedModel.</p>
     *
     * @return the model class that was selected or null if nothing was selected.
     */
    Class<?> getSelectedModel();

    /**
     * <p>setModelConfigs.</p>
     *
     * @param configs the models that this chooser should display.
     */
    void setModelConfigs(List<SessionNodeModelConfig> configs);


    /**
     * <p>setNodeId.</p>
     *
     * @param id the id for the node.
     */
    void setNodeId(String id);


    /**
     * Call after the set methods are called so that the component can build itself.
     */
    void setup();

    /**
     * <p>setSessionNode.</p>
     *
     * @param sessionNode the SessionNode for the getModel node.
     */
    void setSessionNode(SessionNode sessionNode);
}




