/*
 * Copyright (C) 2018 University of Pittsburgh.
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
package edu.pitt.dbmi.data.reader;

/**
 *
 * Dec 12, 2018 11:16:53 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public interface DataReader {

    /**
     * Set the character that is used to group multiple words as one.
     *
     * @param quoteCharacter
     */
    public void setQuoteCharacter(char quoteCharacter);

    /**
     * Set the value to indicate a line is a comment to be ignored.
     *
     * @param commentMarker
     */
    public void setCommentMarker(String commentMarker);

}
