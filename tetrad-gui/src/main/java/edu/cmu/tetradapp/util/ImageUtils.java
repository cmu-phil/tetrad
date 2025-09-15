package edu.cmu.tetradapp.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

/**
 * Static utility methods of general use in the application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ImageUtils {

    /**
     * Loads images for the toolbar using the resource method.  It is assumed that all images are in a directory named
     * "/resources/images/" in the jar.
     *
     * @param anchor the object in which this method is being called. This is needed so that images will load correctly
     *               in the security model of Java Web Start.
     * @param path   the pathname of the image beyond "/resources/images/". It is assumed that all images will be in
     *               this directory in the jar.
     * @return the image.
     * @throws java.lang.RuntimeException if the image can't be loaded. The text of the exception contains the path of
     *                                    the image that could not be loaded.
     */
    public static Image getImage(Object anchor, String path) {
        if (anchor == null) {
            throw new NullPointerException("Anchor must not be null.");
        }

        if (path == null) {
            throw new NullPointerException("Path must not be null.");
        }

        String fullPath = "/docs/images/" + path;
        URL url = anchor.getClass().getResource(fullPath);

        if (url == null) {
            System.out.println("Couldn't find image at " + fullPath);
            return new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        }

        return Toolkit.getDefaultToolkit().createImage(url);
    }
}





