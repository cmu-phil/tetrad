package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.DagFactorizationCompare;

import javax.swing.*;
import java.awt.*;

/**
 * Editor for the Compare-box model DagFactorizationCompare.
 * Reflection entrypoint: public Editor(DagFactorizationCompare model).
 */
public final class DagFactorizationCompareEditor extends JPanel {

    public DagFactorizationCompareEditor(DagFactorizationCompare model) {
        super(new BorderLayout());
        add(new DagFactorizationComparePanel(model), BorderLayout.CENTER);
    }
}