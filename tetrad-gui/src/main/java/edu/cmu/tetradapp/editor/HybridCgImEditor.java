package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.HybridCgImWrapper;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Method;

public class HybridCgImEditor extends JPanel {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private JPanel editorPanel;
    private JTable table;
    private HybridCgImNodeEditingTable model; // TableModel
    private String name = "Hybrid CG IM Editor";
    private HybridCgImWrapper imWrapper; // backing model wrapper (nullable until set)

    public HybridCgImEditor() {
        setupEditor();
    }

    /**
     * Convenience ctor that binds an existing IM wrapper.
     */
    public HybridCgImEditor(HybridCgImWrapper wrapper) {
        this();
        setImWrapper(wrapper);
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        this.name = (n == null ? "Hybrid CG IM Editor" : n);
    }

    public JPanel getEditorPanel() {
        return editorPanel;
    }

    /**
     * Current wrapper (may be null).
     */
    public HybridCgImWrapper getImWrapper() {
        return imWrapper;
    }

    /**
     * Bind/replace the current IM wrapper used by this editor.
     */
    public void setImWrapper(HybridCgImWrapper wrapper) {
        HybridCgImWrapper old = this.imWrapper;
        this.imWrapper = wrapper;
        configureModelBinding();
        pcs.firePropertyChange("imWrapper", old, this.imWrapper);
    }

    /**
     * Hook a wizard (see MixedImEditorWizard)
     */
    public JDialog getWizard(Window owner) {
        return HybridCgImEditorWizard.create(owner, this);
    }

    /**
     * Returns whatever parameter container you adopt later (MLE/SEM results etc.).
     */
    public Object retrieveHybridCgIm() {
        if (imWrapper == null) return null;
        // Prefer the concrete type for callers that know about it.
        try {
            return imWrapper.getHybridCgIm();
        } catch (Throwable t) {
            return null;
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    private void setupEditor() {
        editorPanel = new JPanel(new BorderLayout());
        model = new HybridCgImNodeEditingTable();
        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(true);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(true);

        // Optional: row/col headers; for now keep it minimal:
        editorPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Try initial binding (no-op if wrapper is null at construction time)
        configureModelBinding();

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton addCfg = new JButton("Add Config");
        addCfg.addActionListener(e -> safeInvoke(model, "addDiscreteConfig"));

        JButton delCfg = new JButton("Delete Config");
        delCfg.addActionListener(e -> safeInvoke(model, "deleteSelectedConfig", int.class, table.getSelectedRow()));

        JButton validate = new JButton("Validate");
        validate.addActionListener(e -> safeInvoke(model, "validateAndNormalize"));

        JButton normalize = new JButton("Normalize Rows");
        normalize.addActionListener(e -> safeInvoke(model, "normalizeAllRows"));

        JButton applyToIm = new JButton("Apply to IM");
        applyToIm.addActionListener(e -> {
            if (imWrapper == null) {
                JOptionPane.showMessageDialog(editorPanel, "No IM bound to editor.", "Apply", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Let the table model push its state into the IM, if it supports it.
            boolean ok = safeInvoke(model, "commitToIm", HybridCgImWrapper.class, imWrapper);
            if (!ok) {
                // Fallback: try a generic commit with the raw IM object
                Object im = retrieveHybridCgIm();
                if (im != null) ok = safeInvoke(model, "commitToIm", im.getClass(), im);
            }
            if (ok) pcs.firePropertyChange("imUpdated", false, true);
        });

        bar.add(addCfg);
        bar.add(delCfg);
        bar.add(validate);
        bar.add(normalize);
        bar.addSeparator();
        bar.add(applyToIm);

        editorPanel.add(bar, BorderLayout.NORTH);

        setLayout(new BorderLayout());
        add(editorPanel, BorderLayout.CENTER);
    }

    /**
     * Bind the current wrapper into the table model (via well-known or reflective hooks).
     */
    private void configureModelBinding() {
        if (model == null) return;
        if (imWrapper == null) return;
        // Preferred: a strongly-typed setter on the table model
        if (!safeInvoke(model, "setHybridCgImWrapper", HybridCgImWrapper.class, imWrapper)) {
            // Fallback: pass the concrete IM directly, if available
            Object im = retrieveHybridCgIm();
            if (im != null) safeInvoke(model, "setHybridCgIm", im.getClass(), im);
        }
    }

    /**
     * Reflectively invoke a no-arg or single-arg method if present. Returns true if invoked.
     */
    private boolean safeInvoke(Object target, String methodName, Class<?> paramType, Object arg) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.setAccessible(true);
            m.invoke(target, arg);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private boolean safeInvoke(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            m.invoke(target);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }
}