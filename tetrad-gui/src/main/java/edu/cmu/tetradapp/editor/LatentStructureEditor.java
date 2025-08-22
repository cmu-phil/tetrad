package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.LatentStructureRunner;
import edu.cmu.tetradapp.util.FinalizingEditor;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

/** “Block Search” session node editor that reuses GeneralAlgorithmEditor. */
public class LatentStructureEditor extends JPanel implements FinalizingEditor {
    @Serial private static final long serialVersionUID = 1L;

    private final GeneralAlgorithmEditor delegate;

    public LatentStructureEditor(LatentStructureRunner runner) {
        super(new BorderLayout());
        this.delegate = new GeneralAlgorithmEditor(runner);
        add(delegate, BorderLayout.CENTER);
    }

    @Override
    public boolean finalizeEditor() {
        return delegate.finalizeEditor();
    }
}