package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.editor.ParameterEditor;

import javax.swing.*;

/**
 * Represents the configuration details for a particular model
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface SessionNodeModelConfig {

    /**
     * <p>getHelpIdentifier.</p>
     *
     * @return the identifier to use for help.
     */
    String getHelpIdentifier();


    /**
     * <p>getCategory.</p>
     *
     * @return the category that this model config belongs to or null if there isn't one. This allows you to organize
     * models into various groupings.
     */
    String category();


    /**
     * Returns the model class associated with the configuration.
     *
     * @return the model class
     */
    Class<?> model();


    /**
     * <p>getName.</p>
     *
     * @return a descriptive name for the model.
     */
    String name();


    /**
     * <p>getAcronym.</p>
     *
     * @return the acronym for the model.
     */
    String acronym();


    /**
     * <p>getEditorInstance.</p>
     *
     * @param arguments an array of {@link java.lang.Object} objects
     * @return an instance of the editor to use for the model.
     * @throws java.lang.IllegalArgumentException - Throws an exception of the arguments aren't of the right sort.
     */
    JPanel getEditorInstance(Object[] arguments);


    /**
     * <p>getParameterEditorInstance.</p>
     *
     * @return a newly created instance of the parameter editor for the params returned by
     * <code>getParametersInstance()</code> or null if there is no such
     * editor.
     */
    ParameterEditor getParameterEditorInstance();


}



