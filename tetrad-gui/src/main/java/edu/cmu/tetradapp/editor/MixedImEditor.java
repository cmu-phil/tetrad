package edu.cmu.tetradapp.editor;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

public class MixedImEditor {

    private JPanel editorPanel;
    private JTable table;
    private MixedImNodeEditingTable model; // TableModel
    private String name = "Mixed IM Editor";

    public MixedImEditor() {
        setupEditor();
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        this.name = (n == null ? "Mixed IM Editor" : n);
    }

    public JPanel getEditorPanel() {
        return editorPanel;
    }

    /** Hook a wizard (see MixedImEditorWizard) */
    public JDialog getWizard(Window owner) {
        return MixedImEditorWizard.create(owner, this);
    }

    /** Returns whatever parameter container you adopt later (MLE/SEM results etc.). */
    public Object retrieveMixedIm() {
        // TODO: return the current parameterization object (e.g., MixedIm / CgIm).
        return null;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        // If you expose meaningful bean properties, wire them here.
    }

    private void setupEditor() {
        editorPanel = new JPanel(new BorderLayout());
        model = new MixedImNodeEditingTable();
        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(true);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(true);

        // Optional: row/col headers; for now keep it minimal:
        editorPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Top strip with a tiny toolbar/stub:
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton addCfg = new JButton("Add Config");
        addCfg.addActionListener(e -> model.addDiscreteConfig());
        JButton delCfg = new JButton("Delete Config");
        delCfg.addActionListener(e -> model.deleteSelectedConfig(table.getSelectedRow()));
        JButton validate = new JButton("Validate");
        validate.addActionListener(e -> model.validateAndNormalize());
        bar.add(addCfg);
        bar.add(delCfg);
        bar.add(validate);

        editorPanel.add(bar, BorderLayout.NORTH);
    }
}