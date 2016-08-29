package edu.cmu.tetradapp.util;

/**
 * Actions an editor needs to perform before closing. If it's OK to close the
 * finalalizeEditor method will return true; otherwise, false.
 *
 * @author jdramsey
 */
public interface FinalizingEditor {
    boolean finalizeEditor();
}
