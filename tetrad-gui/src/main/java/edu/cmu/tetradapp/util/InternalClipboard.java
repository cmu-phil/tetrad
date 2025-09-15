package edu.cmu.tetradapp.util;

import java.awt.datatransfer.Clipboard;

/**
 * Stores cut or copied objects in a way that does not allow them to be accidentally pasted to other applications. Data
 * that should be pastable to other applications should use the System clipboard,
 * Toolkit.getDefaultToolkit().getSystemClipboard().
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class InternalClipboard extends Clipboard {
    private static final InternalClipboard ourInstance = new InternalClipboard();
    private static final InternalClipboard layoutInstance = new InternalClipboard();

    private InternalClipboard() {
        super("Internal Clipboard");
    }

    /**
     * <p>getInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.util.InternalClipboard} object
     */
    public static InternalClipboard getInstance() {
        return InternalClipboard.ourInstance;
    }

    /**
     * <p>Getter for the field <code>layoutInstance</code>.</p>
     *
     * @return a {@link edu.cmu.tetradapp.util.InternalClipboard} object
     */
    public static InternalClipboard getLayoutInstance() {
        return InternalClipboard.layoutInstance;
    }
}





