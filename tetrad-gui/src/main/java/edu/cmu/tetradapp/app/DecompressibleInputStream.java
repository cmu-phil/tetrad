/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.app;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

/**
 * This class was embedded in the LoadSessionAction class. Had to make it its own class for reusability.
 * <p>
 * Dec 6, 2017 11:10:24 AM
 *
 * @author josephramsey
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class DecompressibleInputStream extends ObjectInputStream {

//    private static final Logger LOGGER = LoggerFactory.getLogger(DecompressibleInputStream.class);

    /**
     * <p>Constructor for DecompressibleInputStream.</p>
     *
     * @param in a {@link java.io.InputStream} object
     * @throws java.io.IOException if any.
     */
    public DecompressibleInputStream(InputStream in) throws IOException {
        super(in);
    }

    /**
     * Reads and returns the class descriptor when deserializing an object.
     *
     * @return the class descriptor for the deserialized object.
     * @throws IOException            if an I/O error occurs while reading the class descriptor.
     * @throws ClassNotFoundException if the class for the class descriptor is not found.
     */
    @Override
    protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
        ObjectStreamClass resultClassDescriptor = super.readClassDescriptor(); // initially streams descriptor
        Class<?> localClass; // the class in the local JVM that this descriptor represents.
        try {
            localClass = Class.forName(resultClassDescriptor.getName());
        } catch (ClassNotFoundException e) {
//            DecompressibleInputStream.LOGGER.error("No local class for " + resultClassDescriptor.getName(), e);
            return resultClassDescriptor;
        }
        ObjectStreamClass localClassDescriptor = ObjectStreamClass.lookup(localClass);
        if (localClassDescriptor != null) { // only if class implements serializable
            long localSUID = localClassDescriptor.getSerialVersionUID();
            long streamSUID = resultClassDescriptor.getSerialVersionUID();
            if (streamSUID != localSUID) { // check for serialVersionUID mismatch.
                resultClassDescriptor = localClassDescriptor; // Use local class descriptor for deserialization
            }
        }
        return resultClassDescriptor;
    }

}
