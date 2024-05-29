package edu.cmu.tetrad.search;

/**
 * The ModelObserver interface is implemented by classes that want to observe changes in a model.
 */
public interface ModelObserver {

    /**
     * This method is called when the model changes.
     */
    void update();
}
