/*
 * Copyright (C) 2019 University of Pittsburgh.
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
package edu.pitt.dbmi.data.reader.preview;

import java.nio.file.Path;

/**
 *
 * Feb 20, 2017 2:09:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class AbstractDataPreviewer {

    protected static final byte LINE_FEED = '\n';
    protected static final byte CARRIAGE_RETURN = '\r';

    protected static final String ELLIPSIS = "...";

    protected final Path dataFile;

    public AbstractDataPreviewer(Path dataFile) {
        this.dataFile = dataFile;
    }

    protected void checkCharacterNumberParameter(int numOfCharacters) {
        if (numOfCharacters < 0) {
            throw new IllegalArgumentException("Parameter numOfCharacters must be positive integer.");
        }
    }

    protected void checkLineNumberParameter(int fromLine, int toLine) {
        if (fromLine < 0) {
            throw new IllegalArgumentException("Parameter fromLine must be positive integer.");
        }
        if (toLine < 0) {
            throw new IllegalArgumentException("Parameter toLine must be positive integer.");
        }
        if (toLine < fromLine) {
            throw new IllegalArgumentException("Parameter toLine must be greater than or equal to fromLine.");
        }
    }

}
