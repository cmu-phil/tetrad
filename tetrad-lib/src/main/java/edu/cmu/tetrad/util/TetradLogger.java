///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.util;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a logging utility used throughout tetrad.  Unlike a typical logger, this does not work on levels; instead
 * one can set events need to be logged. This is done by giving the logger a <code>TetradLoggerConfig</code> which will
 * be used to determine whether some event should be logged.
 * <p>
 * Although one can explicitly construct instance of <code>TetradLoggerConfig</code> and set them in the logger, the
 * configuration detail for most models is defined in the
 * <code>configuration.xml</code> file and added to the logger at startup.  A pre-configured
 * <code>TetradLoggerConfig</code> for some model can be found by calling
 * <code>getTetradLoggerConfigForModel(Class)</code>
 * <p>
 * Furthermore, the logger supports logging to a sequence of files in some directory. To start logging to a new file in
 * the logging directory (assuming it has been set) call <code>setNextOutputStream</code> to remove this stream from the
 * logger call <code>removeNextOutputStream</code>. In adding to the feature arbitrary streams can be added and removed
 * from the logger by calling <code>addOutputStream</code> and
 * <code>removeOutputStream</code>.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class TetradLogger {

    /**
     * The singleton instance of the logger.
     */
    private static final TetradLogger INSTANCE = new TetradLogger();
    /**
     * A mapping between output streams and writers used to wrap them.
     */
    private final transient Map<OutputStream, Writer> writers = new LinkedHashMap<>();
    /**
     * A mapping from model classes to their configured loggers.
     */
    private final transient Map<Class<?>, TetradLoggerConfig> classConfigMap = new ConcurrentHashMap<>();
    /**
     * The listeners.
     */
    private final transient List<TetradLoggerListener> listeners = new ArrayList<>();
    /**
     * States whether events should be logged; this allows one to turn off all loggers at once. (Note, a field is used,
     * since fast lookups are important)
     */
    private transient boolean logging = Preferences.userRoot().getBoolean("loggingActivated", true);
    /**
     * The configuration to use to determine which events to log.
     */
    private transient TetradLoggerConfig config;
    /**
     * The getModel file stream that is being written to, this is set in "setNextOutputStream()".s
     */
    private transient OutputStream stream;
    /**
     * The latest file path being written to.
     */
    private transient String latestFilePath;

    /**
     * Private constructor, this is a singleton.
     */
    private TetradLogger() {

    }

    //=============================== Public methods ===================================//


    /**
     * Returns an instance of TetradLogger.
     *
     * @return an instance of TetradLogger
     */
    public static TetradLogger getInstance() {
        return TetradLogger.INSTANCE;
    }

    /**
     * Adds a TetradLoggerListener to the TetradLogger. The listener will be notified whenever a logger configuration is
     * set or reset.
     *
     * @param l the TetradLoggerListener to add
     */
    public void addTetradLoggerListener(TetradLoggerListener l) {
        this.listeners.add(l);
    }

    /**
     * Removes a TetradLoggerListener from the TetradLogger.
     *
     * @param l the TetradLoggerListener to remove
     */
    @SuppressWarnings("UnusedDeclaration")
    public void removeTetradLoggerListener(TetradLoggerListener l) {
        this.listeners.remove(l);
    }

    /**
     * Sets what configuration should be used to determine which events to log. Null can be given to remove a previously
     * set configuration from the logger.
     *
     * @param config a {@link edu.cmu.tetrad.util.TetradLoggerConfig} object
     */
    public void setTetradLoggerConfig(TetradLoggerConfig config) {
        TetradLoggerConfig previous = this.config;
        if (config == null) {
            this.config = null;
            if (previous != null) {
                this.fireDeactivated();
            }
        } else {
            this.config = config;
            this.fireActivated(this.config);

        }
    }

    /**
     * If there is a pre-defined configuration for the given model it is set, otherwise an exception is thrown.
     *
     * @param model a {@link java.lang.Class} object
     */
    public void setConfigForClass(Class<?> model) {
        TetradLoggerConfig config = this.classConfigMap.get(model);
        setTetradLoggerConfig(config);
    }

    /**
     * Adds the given <code>TetradLoggerConfig</code> to the logger, so that it can be used throughout the life of the
     * application.
     *
     * @param model  a {@link java.lang.Class} object
     * @param config a {@link edu.cmu.tetrad.util.TetradLoggerConfig} object
     */
    public void addTetradLoggerConfig(Class<?> model, TetradLoggerConfig config) {
        this.classConfigMap.put(model, config);
    }

    /**
     * <p>getLoggerForClass.</p>
     *
     * @param clazz a {@link java.lang.Class} object
     * @return a {@link edu.cmu.tetrad.util.TetradLoggerConfig} object
     */
    public TetradLoggerConfig getLoggerForClass(Class<?> clazz) {
        TetradLoggerConfig config = this.classConfigMap.get(clazz);

        if (config == null) {
            return null;
        }

        return config.copy();
    }


    /**
     * Resets the logger by removing any configuration info set with <code>setTetradLoggerConfig</code>.
     */
    public void reset() {
        this.config = null;
        flush();
    }

    /**
     * States whether the logger is turned on or not.
     *
     * @return true iff the logger is logging.
     */
    public boolean isLogging() {
        return this.logging;
    }

    /**
     * Sets whether the logger is on or not.
     *
     * @param logging a boolean
     */
    public void setLogging(boolean logging) {
        Preferences.userRoot().putBoolean("loggingActivated", logging);
        this.logging = logging;
    }

    /**
     * Flushes the writers.
     */
    public void flush() {
        if (this.logging) {
            try {
                for (Writer writer : this.writers.values()) {
                    writer.flush();
                }
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
            }
        }
        for (OutputStream stream : this.writers.keySet()) {
            if (stream instanceof LogDisplayOutputStream logStream) {
                logStream.moveToEnd();
            }
        }
    }

    /**
     * Logs an error, this will log the message regardless of any configuration information. Although it won't be logged
     * if the logger is off and if there are no streams attached.
     *
     * @param message a {@link java.lang.String} object
     */
    public void error(String message) {
        if (this.logging) {
            try {
                for (Writer writer : this.writers.values()) {
                    writer.write(message);
                    writer.write("\n");
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Logs the given message regardless of the logger's getModel settings. Although nothing will be logged if the
     * logger has been turned off.
     *
     * @param message a {@link java.lang.String} object
     */
    public void log(String message) {
        if (this.logging) {
            if (!this.writers.containsKey(System.out)) {
                System.out.println(message);
            }

            if (this.config == null) {
                this.fireActivated(new EmptyConfig(true));
            }
            try {
                for (Writer writer : this.writers.values()) {
                    writer.write(message);
                    writer.write("\n");
                    writer.flush();
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Sets the <code>OutputStream</code> that is used to log matters out to.
     *
     * @param stream a {@link java.io.OutputStream} object
     */
    public void addOutputStream(OutputStream stream) {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
        this.writers.put(stream, writer);
    }

    /**
     * Removes the given stream from the logger.
     *
     * @param stream a {@link java.io.OutputStream} object
     */
    public void removeOutputStream(OutputStream stream) {
        this.writers.remove(stream);
    }

    /**
     * Removes all streams from the logger.
     */
    public void clear() {
        for (OutputStream stream : this.writers.keySet()) {
            if (stream != System.out) {
                try {
                    stream.close();
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
        this.writers.clear();
        this.stream = null;
    }

    /**
     * Sets the next output stream to use it for logging, call <code>removeNextOutputStream</code> to remove it. This
     * will create the next output file in the output directory and form a stream from it and add it to the logger.
     *
     * @throws java.lang.IllegalStateException - Thrown if there is an error setting the stream, the message will state
     *                                         the nature of the error.
     */
    public void setNextOutputStream() {
        if (this.logging && this.isFileLoggingEnabled()) {
            File dir = new File(getLoggingDirectory());
            String latestFilePath = getString(dir);
            File logFile = new File(latestFilePath);

            OutputStream old = this.stream;
            try {
                this.stream = new FileOutputStream(logFile);
            } catch (FileNotFoundException e) {
                this.stream = old;
                throw new IllegalStateException("Could not create file in output directory ("
                                                + dir.getAbsolutePath() + ").");
            }
            if (old != null) {
                removeOutputStream(old);
            }

            addOutputStream(this.stream);

            this.latestFilePath = latestFilePath;
        }
    }

    @NotNull
    private String getString(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                throw new IllegalStateException("Could not create the output directory "
                                                + dir.getAbsolutePath() + ".");
            }
        }
        if (!dir.canWrite()) {
            throw new IllegalStateException("Cannot write to the directory chosen for saving output " +
                                            " logs (" + dir.getAbsolutePath() + "). Please pick another directory.");
        }
        // get the next file name to use.
        String prefix = getLoggingFilePrefix();
        String[] list = dir.list();
        if (list == null) list = new String[0];
        List<String> files = Arrays.asList(list);
        int index = 1;
        String name = prefix + (index++) + ".txt";
        while (files.contains(name)) {
            name = prefix + (index++) + ".txt";
        }
        // finally, create a log file and add a stream to the logger
        return dir.getAbsolutePath() + "/" + name;
    }


    /**
     * <p>removeNextOutputStream.</p>
     */
    public void removeNextOutputStream() {
        flush();
        if (this.stream != null) {
            removeOutputStream(this.stream);
            try {
                this.stream.close();
            } catch (IOException e) {
                // do nothing.
            }
            this.stream = null;
        }
    }

    /**
     * <p>getLoggingFilePrefix.</p>
     *
     * @return - prefix
     */
    public String getLoggingFilePrefix() {
        return Preferences.userRoot().get("loggingPrefix", "output");
    }

    /**
     * Sets the logging prefix.
     *
     * @param loggingFilePrefix a {@link java.lang.String} object
     */
    public void setLoggingFilePrefix(String loggingFilePrefix) {
        if (loggingFilePrefix == null) {
            throw new NullPointerException();
        }

        if (loggingFilePrefix.isEmpty()) {
            throw new IllegalArgumentException("Empty prefix name; ignored.");
        }

        Preferences.userRoot().put("loggingPrefix", normalize(loggingFilePrefix));
    }

    /**
     * States whether to display the log display.
     *
     * @return a boolean
     */
    public boolean isDisplayLogEnabled() {
        return Preferences.userRoot().getBoolean("enableDisplayLogging", true);
    }

    /**
     * Sets whether the display log should be used or not.
     *
     * @param enabled a boolean
     */
    public void setDisplayLogEnabled(boolean enabled) {
        Preferences.userRoot().putBoolean("enableDisplayLogging", enabled);
    }

    /**
     * States whether file logging is enabled or not.
     *
     * @return a boolean
     */
    public boolean isFileLoggingEnabled() {
        return Preferences.userRoot().getBoolean("enableFileLogging", false);
    }

    /**
     * Sets whether "file logging" is enabled or not; that is whether calls to <code>setNextOutputStream</code> will be
     * respected.
     *
     * @param enabled a boolean
     */
    public void setFileLoggingEnabled(boolean enabled) {
        Preferences.userRoot().putBoolean("enableFileLogging", enabled);
    }

    /**
     * States whether log displays should be automatically displayed or not.
     *
     * @param enable a boolean
     */
    public void setAutomaticLogDisplayEnabled(boolean enable) {
        Preferences.userRoot().put("allowAutomaticLogDisplay", enable ? "allow" : "disallow");
    }

    /**
     * <p>getLoggingDirectory.</p>
     *
     * @return - logging directory.
     */
    public String getLoggingDirectory() {
        return Preferences.userRoot().get("loggingDirectory",
                Preferences.userRoot().absolutePath());
    }

    /**
     * Sets the logging directory, but first checks whether we can write to it, etc.
     *
     * @param directory - The directory to set.
     * @throws java.lang.IllegalStateException if there is a problem with the directory.
     */
    public void setLoggingDirectory(String directory) {
        File selectedFile = new File(directory);

        if (selectedFile.exists() && !selectedFile.isDirectory()) {
            throw new IllegalStateException("That 'output directory' is actually a file, not a directory");
        }

        if (selectedFile.exists() && selectedFile.isDirectory() & !selectedFile.canWrite()) {
            throw new IllegalStateException("The output directory cannot be written to.");
        }

        if (!selectedFile.exists()) {
            boolean created = selectedFile.mkdir();

            if (!created) {
                throw new IllegalStateException("The output directory cannot be created. ");
            }

            if (!selectedFile.canWrite()) {
                throw new IllegalStateException("That output directory cannot be written to. " +
                                                "Keeping the old one.");
            }

            if (!selectedFile.delete()) {
                throw new IllegalStateException("Couldn't delete this file; " + selectedFile);
            }
        }

        Preferences.userRoot().put("loggingDirectory", selectedFile.getAbsolutePath());
    }

    //========================================= Private Method ============================//


    /**
     * Normalizes the prefix.
     */
    private String normalize(String prefix) {
        StringBuilder buf = new StringBuilder();
        Pattern pattern = Pattern.compile("[a-zA-Z_]");
        for (int i = 0; i < prefix.length(); i++) {
            String s = prefix.substring(i, i + 1);
            Matcher matcher = pattern.matcher(s);
            if (matcher.matches()) {
                buf.append(s);
            }
        }
        return buf.toString();
    }


    private void fireActivated(TetradLoggerConfig config) {
        if (this.logging && !this.listeners.isEmpty()) {
            TetradLoggerEvent evt = new TetradLoggerEvent(this, config);
            for (TetradLoggerListener l : this.listeners) {
                l.configurationActivated(evt);
            }
        }
    }


    private void fireDeactivated() {
        if (this.logging && !this.listeners.isEmpty() && this.config == null) {
            TetradLoggerEvent evt = new TetradLoggerEvent(this, null);
            for (TetradLoggerListener l : this.listeners) {
                l.configurationDeactivated(evt);
            }
        }
    }

    /**
     * <p>Getter for the field <code>latestFilePath</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getLatestFilePath() {
        return this.latestFilePath;
    }

    //================================ Inner classes ====================================//

    /**
     * Represents an output stream that can get its own length.
     */
    public interface LogDisplayOutputStream {


        /**
         * The total string length written to the text area.
         *
         * @return The total string length written to the text area.
         */
        @SuppressWarnings("UnusedDeclaration")
        int getLengthWritten();


        /**
         * Should move the log to the end of the stream.
         */
        void moveToEnd();

    }

    /**
     * Represents an empty configuration for the logger. It implements the TetradLoggerConfig interface.
     */
    public static class EmptyConfig implements TetradLoggerConfig {

        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * Represents the activation status of an event in the logger configuration.
         */
        private final boolean active;

        /**
         * <p>Constructor for EmptyConfig.</p>
         *
         * @param active a boolean
         */
        @SuppressWarnings("SameParameterValue")
        public EmptyConfig(boolean active) {
            this.active = active;
        }

        /**
         * Generates a simple exemplar of this class to test serialization.
         *
         * @return a simple exemplar of this class to test serialization.
         */
        public static EmptyConfig serializableInstance() {
            return new EmptyConfig(true);
        }

        /**
         * <p>isEventActive.</p>
         *
         * @param id a {@link String} object
         * @return a boolean
         */
        public boolean isEventActive(String id) {
            return this.active;
        }

        /**
         * <p>isActive.</p>
         *
         * @return a boolean
         */
        @Override
        public boolean active() {
            return this.active;
        }

        /**
         * <p>copy.</p>
         *
         * @return a {@link TetradLoggerConfig} object
         */
        public TetradLoggerConfig copy() {
            return new EmptyConfig(this.active);
        }

        /**
         * <p>getSupportedEvents.</p>
         *
         * @return a {@link List} object
         */
        public List<Event> getSupportedEvents() {
            if (!this.active) {
                return Collections.emptyList();
            }
            throw new UnsupportedOperationException("Not supported if active is true");
        }

        /**
         * <p>setEventActive.</p>
         *
         * @param id     a {@link String} object
         * @param active a boolean
         */
        public void setEventActive(String id, boolean active) {
            throw new UnsupportedOperationException("Can't modify the logger config");
        }
    }


}



