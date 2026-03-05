package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.DagFactorizationCompare;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Editor for the Compare-box model DagFactorizationCompare.
 * Reflection entrypoint: public Editor(DagFactorizationCompare model).
 */
public final class DagFactorizationCompareEditor extends JPanel {

    public DagFactorizationCompareEditor(DagFactorizationCompare model) {
        super(new BorderLayout());
        DagFactorizationComparePanel comp = new DagFactorizationComparePanel(model);
        comp.addPropertyChangeListener(
                evt -> firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()));
        add(comp, BorderLayout.CENTER);
    }
}