package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.NNEstimatorModel;

import javax.swing.*;
import java.awt.*;

/**
 * Tetrad editor for {@link NNEstimatorModel}.
 *
 * <p>Reflection entrypoint: {@code public NNEstimatorEditor(NNEstimatorModel)}.
 * Delegates all display logic to {@link NNEstimatorComparePanel}.
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
