package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.NNEstimatorModel;

import javax.swing.*;
import java.awt.*;

/**
 * Editor for the Compare-box model DagFactorizationCompare.
 * Reflection entrypoint: public Editor(DagFactorizationCompare model).
 */
public final class NNEstimatorEditor extends JPanel {

    public NNEstimatorEditor(NNEstimatorModel model) {
        super(new BorderLayout());
        NNEstimatorComparePanel comp = new NNEstimatorComparePanel(model);
        comp.addPropertyChangeListener(
                evt -> firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()));
        add(comp, BorderLayout.CENTER);
    }
}