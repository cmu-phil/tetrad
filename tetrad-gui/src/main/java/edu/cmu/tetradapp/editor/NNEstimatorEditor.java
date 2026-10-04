package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.NNEstimatorModel;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.*;

/**
 * Tetrad editor for {@link NNEstimatorModel}.
 *
 * <p>Reflection entrypoint: {@code public NNEstimatorEditor(NNEstimatorModel)}.
 * Delegates all display logic to {@link NNEstimatorComparePanel}. If the parent data box holds several data sets,
 * a chooser above the panel says which one the model is fitted to and lets another be chosen.
 */
public final class NNEstimatorEditor extends JPanel {

    private final NNEstimatorModel model;
    private JComponent comparePanel;

    public NNEstimatorEditor(NNEstimatorModel model) {
        super(new BorderLayout());
        this.model = model;

        Box chooser = DataSetChooser.create(model.getSourceData(), model.getDataIndex(), this::chooseData);

        if (chooser != null) {
            chooser.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            chooser.add(Box.createHorizontalStrut(12));
            chooser.add(new JLabel("Choosing another refits the model and clears cross-validation, edge"
                                   + " strength and pruning results."));
            chooser.add(Box.createHorizontalGlue());
            add(chooser, BorderLayout.NORTH);
        }

        showComparePanel();
    }

    /**
     * Builds the display afresh from the model, as when the editor is opened.
     */
    private void showComparePanel() {
        if (this.comparePanel != null) remove(this.comparePanel);

        NNEstimatorComparePanel comp = new NNEstimatorComparePanel(this.model);
        comp.addPropertyChangeListener(
                evt -> firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()));
        this.comparePanel = comp;
        add(comp, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void chooseData(int index) {
        new WatchedProcess() {
            @Override
            public void watch() {
                try {
                    NNEstimatorEditor.this.model.setDataIndex(index);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(NNEstimatorEditor.this,
                            "Could not fit the model to that data set:\n" + ex.getMessage()));
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    showComparePanel();
                    firePropertyChange("modelChanged", null, null);
                });
            }
        };
    }
}
