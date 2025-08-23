package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.LatentStructureRunner;
import edu.cmu.tetradapp.util.FinalizingEditor;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

/**
 * “Block Search” session node editor that reuses GeneralAlgorithmEditor.
 */
public class LatentStructureEditor extends JPanel implements FinalizingEditor {
    @Serial
    private static final long serialVersionUID = 1L;

    private final GeneralAlgorithmEditor delegate;

    /**
     * Constructs a LatentStructureEditor instance, which requires a LatentStructureRunner.
     *
     * @param runner the instance of LatentStructureRunner; must not be null
     */
    public LatentStructureEditor(LatentStructureRunner runner) {
        super(new BorderLayout());
        this.delegate = new GeneralAlgorithmEditor(runner);
        add(delegate, BorderLayout.CENTER);
    }

    /**
     * Finalizes the editor by delegating to the GeneralAlgorithmEditor.
     *
     * @return true if the editor was finalized successfully, false otherwise
     */
    @Override
    public boolean finalizeEditor() {
        return delegate.finalizeEditor();
    }
}