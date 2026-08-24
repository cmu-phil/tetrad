/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads images from the docs/images (light) or docs/darkmode/images (dark) resource directories.
 * <p>
 * Images are read synchronously with {@link ImageIO} and cached per resource path. This matters for the session
 * editor: previously images came from {@link Toolkit#createImage(URL)}, which loads asynchronously, so a subsequent
 * {@code new ImageIcon(image)} had to wait on a {@code MediaTracker}. If the calling thread's interrupt flag was set -
 * which is exactly the state of a {@code WatchedProcess} worker after a stop or a failure, and session nodes are laid
 * out on that worker during propagation - the wait aborted immediately and the icon came back with width -1, so the
 * node displayed with no icon. A fully decoded {@link BufferedImage} has no such loading phase.
 */
public final class ImageUtils {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private ImageUtils() {
    }

    /**
     * Returns the image at the given path, in the light- or dark-mode image directory depending on the current look
     * and feel. Never returns null; if the resource is missing, a blank placeholder is returned and a message printed.
     *
     * @param anchor an object whose class loader is used to locate the resource.
     * @param path   the file name, e.g. "searchIcon.gif".
     * @return the image.
     */
    public static Image getImage(Object anchor, String path) {
        if (anchor == null) {
            throw new NullPointerException("Anchor must not be null.");
        }
        if (path == null) {
            throw new NullPointerException("Path must not be null.");
        }

        String fullPath;
        if (isDarkMode()) {
            fullPath = "/docs/darkmode/images/" + path;
        } else {
            fullPath = "/docs/images/" + path;
        }

        Image cached = CACHE.get(fullPath);
        if (cached != null) {
            return cached;
        }

        URL url = anchor.getClass().getResource(fullPath);

        if (url == null) {
            System.out.println("Couldn't find image at " + fullPath);
            return new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        }

        Image image = null;

        try {
            image = ImageIO.read(url);
        } catch (IOException e) {
            System.out.println("Couldn't decode image at " + fullPath + ": " + e.getMessage());
        }

        if (image == null) {
            // Format not handled by ImageIO; fall back to the asynchronous toolkit loader, uncached.
            return Toolkit.getDefaultToolkit().createImage(url);
        }

        CACHE.put(fullPath, image);
        return image;
    }

    private static boolean isDarkMode() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
    }
}
