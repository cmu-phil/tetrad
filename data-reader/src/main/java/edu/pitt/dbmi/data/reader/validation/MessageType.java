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
package edu.pitt.dbmi.data.reader.validation;

/**
 * Feb 17, 2017 1:49:26 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public enum MessageType {

    /**
     * Error in file input/output operation.
     */
    FILE_IO_ERROR,

    /**
     * Missing value in file.
     */
    FILE_MISSING_VALUE,

    /**
     * Invalid number in file.
     */
    FILE_INVALID_NUMBER,

    /**
     * Excess or insufficient data in file.
     */
    FILE_EXCESS_DATA,

    /**
     * Excess or insufficient data in file.
     */
    FILE_INSUFFICIENT_DATA,

    /**
     * File summary.
     */
    FILE_SUMMARY

}
