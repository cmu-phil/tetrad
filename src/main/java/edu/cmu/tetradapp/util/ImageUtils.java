///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

/**
 * Static utility methods of general use in the application.
 *
 * @author Joseph Ramsey
 */
public final class ImageUtils {

    /**
     * Loads images for the toolbar using the resource method.  It is assumed
     * that all images are in a directory named "/resources/images/" in the
     * jar.
     *
     * @param anchor the object in which this method is being called. This is
     *               needed so that images will load correctly in the security
     *               model of Java Web Start.
     * @param path   the pathname of the image beyond "/resources/images/". It
     *               is assumed that all images will be in this directory in the
     *               jar.
     * @return the image.
     * @throws RuntimeException if the image can't be loaded. The text of the
     *                          exception contains the path of the image that
     *                          could not be loaded.
     */
    public static Image getImage(Object anchor, String path) {
        if (anchor == null) {
            throw new NullPointerException("Anchor must not be null.");
        }

        if (path == null) {
            throw new NullPointerException("Path must not be null.");
        }

        String fullPath = "/resources/images/" + path;
        URL url = anchor.getClass().getResource(fullPath);

        if (url == null) {
            System.out.println("Couldn't find image at " + fullPath);
            return new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        }

        return Toolkit.getDefaultToolkit().createImage(url);
    }
}





