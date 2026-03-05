package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetradapp.util.DesktopController;
import org.apache.commons.text.similarity.JaccardDistance;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Opens the Nonlinearity Checks tool for the selected DataSet.
 */
class NonlinearityChecksAction extends AbstractAction {

    private final ISelectedModel dataEditor;

    /**
     * Constructor for NonlinearityChecksAction.
     * @param editor The DataEditor instance to associate with this action.
     */
    public NonlinearityChecksAction(ISelectedModel editor) {
        super("Nonlinearity Checks...");
        this.dataEditor = editor;
    }

    /**
     * Invoked when an action occurs, opening the Nonlinearity Checks tool for the selected data set.
     *
     * @param e The ActionEvent triggered by the user interaction.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();
        if (dataSet == null || dataSet.getNumColumns() == 0) {
            JOptionPane.showMessageDialog(findOwner(), "Cannot run nonlinearity checks for an empty data set.");
            return;
        }

        JPanel panel = new NonlinearityChecks(dataSet);
        EditorWindow editorWindow = new EditorWindow(panel, "Nonlinearity Checks", null,
                false, (JComponent) this.dataEditor);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);
    }

    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class,
                (JComponent) this.dataEditor);
    }
}