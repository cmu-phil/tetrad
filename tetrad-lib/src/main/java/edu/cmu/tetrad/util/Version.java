///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.util;

import java.io.*;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the getModel Tetrad version. This needs to be modified manually each time a new version is released. The
 * form of the version is a.b.c-d, where "a" is the major version, "b" is the minor version, "c" is the minor
 * subversion, and "d" is the incremental release number for subversions.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@SuppressWarnings("RedundantIfStatement")
public class Version implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The major version number. In release a.b.c-d, a. At time of creating this class, it's 4, and the minor version is
     * 3. This should increase only for truly substantial and essentially complete new releases of Tetrad.
     *
     * @serial Range greater than or equal to 0.
     */
    private final int majorVersion;

    /**
     * The minor version number. In release a.b.c-d, b. This number increases without bound until the next major
     * release, at which point it goes back to zero.
     *
     * @serial Range greater than or equal to 0.
     */
    private final int minorVersion;

    /**
     * The minor release number. In release a.b.c-d, c. This number increases without bound until the next major or
     * minor release, at which point it goes back to zero.
     *
     * @serial Range greater than or equal to 0.
     */
    private final int minorSubversion;

    /**
     * The incremental release number. In release a.b.c-d, d. This number increases without bound until the next major
     * version, minor version, or minor subversion release, at which point is goes back to zero. If it is zero, release
     * a.b.c.dx may be referred to as a.b.c.
     * <p>
     * With maven snapshots the incremental release number refers to the snapshot build date.  If this field is SNAPSHOT
     * it is set to max int
     *
     * @serial Range greater than or equal to 0.
     */
    private final int incrementalRelease;

    //===========================CONSTRUCTORS============================//

    /**
     * Parses string version specs into Versions.
     *
     * @param spec A string of the form a.b.c-d.
     */
    public Version(String spec) {
        if (spec == null) {
            throw new NullPointerException();
        }

        Pattern pattern2 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)");
        Matcher matcher2 = pattern2.matcher(spec);

        Pattern pattern3 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)-(\\d*)");
        Matcher matcher3 = pattern3.matcher(spec);

        Pattern pattern4 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)(.*)-SNAPSHOT");
        Matcher matcher4 = pattern4.matcher(spec);

        Pattern pattern5 = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)-(\\d*)(.*)-SNAPSHOT");
        Matcher matcher5 = pattern5.matcher(spec);

        if (matcher2.matches()) {
            this.majorVersion = Integer.parseInt(matcher2.group(1));
            this.minorVersion = Integer.parseInt(matcher2.group(2));
            this.minorSubversion = Integer.parseInt(matcher2.group(3));
            this.incrementalRelease = 0;
        } else if (matcher3.matches()) {
            this.majorVersion = Integer.parseInt(matcher3.group(1));
            this.minorVersion = Integer.parseInt(matcher3.group(2));
            this.minorSubversion = Integer.parseInt(matcher3.group(3));
            this.incrementalRelease = Integer.parseInt(matcher3.group(4));
        } else if (matcher4.matches()) {
            this.majorVersion = Integer.parseInt(matcher4.group(1));
            this.minorVersion = Integer.parseInt(matcher4.group(2));
            this.minorSubversion = Integer.parseInt(matcher4.group(3));
            this.incrementalRelease = 0;
        } else if (matcher5.matches()) {
            this.majorVersion = Integer.parseInt(matcher5.group(1));
            this.minorVersion = Integer.parseInt(matcher5.group(2));
            this.minorSubversion = Integer.parseInt(matcher5.group(3));
            this.incrementalRelease = Integer.parseInt(matcher5.group(4));
        } else {
            this.majorVersion = 0;//Integer.parseInt(matcher5.group(1));
            this.minorVersion = 0;//Integer.parseInt(matcher5.group(2));
            this.minorSubversion = 0;//Integer.parseInt(matcher5.group(3));
            this.incrementalRelease = 0;//Integer.parseInt(matcher5.group(4));
        }

    }

    private Version(int majorVersion, int minorVersion, int minorSubversion,
                    int incrementalRelease) {
        if (majorVersion < 0) {
            throw new IllegalArgumentException();
        }

        if (minorVersion < 0) {
            throw new IllegalArgumentException();
        }

        if (minorSubversion < 0) {
            throw new IllegalArgumentException();
        }

        if (incrementalRelease < 0) {
            throw new IllegalArgumentException();
        }

        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.minorSubversion = minorSubversion;
        this.incrementalRelease = incrementalRelease;
    }

    /**
     * <p>currentRepositoryVersion.</p>
     *
     * @return the model version as stored in project/resources/version. This will be the same as the getModel viewable
     * version when the ejar and task is run. (The file is copied over.)
     */
    public static Version currentRepositoryVersion() {
        try {
            File file = new File("project_tetrad/resources/version");
            FileReader fileReader = new FileReader(file);
            BufferedReader bufReader = new BufferedReader(fileReader);
            String spec = bufReader.readLine();
            bufReader.close();
            return new Version(spec);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Please correct the file project/resources/version " +
                    "\nso that it contains a version number of the form " +
                    "\na.b.c or a.b.c-d or a.b.c-SNAPSHOT or a.b.c-d.e. " +
                    "\nNormally this is set automatically by the Maven build " +
                    "\nsystem.");
        }
    }

    /**
     * <p>currentViewableVersion.</p>
     *
     * @return the model version as stored in the ejar. To sync this with the version at project/resources/version, run
     * the ejar ant task.
     */
    public static Version currentViewableVersion() {
        try {
            final String path = "/resources/version";
            URL url = Version.class.getResource(path);

            if (url == null) {
                throw new RuntimeException(
                        "Please correct the file project/resources/version " +
                        "\nso that it contains a version number of the form " +
                        "\na.b.c or a.b.c-d or a.b.c-SNAPSHOT or a.b.c-d.e. " +
                        "\nNormally this is set automatically by the Maven build " +
                        "\nsystem.");
            }

            InputStream inStream = url.openStream();
            Reader reader = new InputStreamReader(inStream);
            BufferedReader bufReader = new BufferedReader(reader);
            String spec = bufReader.readLine();

            bufReader.close();

            return new Version(spec);
        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new RuntimeException(
                "Please correct the file project/resources/version " +
                "\nso that it contains a version number of the form " +
                "\na.b.c or a.b.c-d or a.b.c-SNAPSHOT or a.b.c-d.e. " +
                "\nNormally this is set automatically by the Maven build " +
                "\nsystem.");
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.util.Version} object
     */
    public static Version serializableInstance() {
        return new Version("1.2.3");
    }

    //==========================PUBLIC METHODS===========================//

    private int majorVersion() {
        return this.majorVersion;
    }

    private int minorVersion() {
        return this.minorVersion;
    }

    private int minorSubversion() {
        return this.minorSubversion;
    }

    private int incrementalRelease() {
        return this.incrementalRelease;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hashCode = 61;
        hashCode += 61 * this.majorVersion;
        hashCode += 61 * this.minorVersion;
        hashCode += 61 * this.minorSubversion;
        hashCode += this.incrementalRelease;
        return hashCode;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Version other)) {
            return false;
        }

        if (!(this.majorVersion == other.majorVersion)) {
            return false;
        }

        if (!(this.minorVersion == other.minorVersion)) {
            return false;
        }

        if (!(this.minorSubversion == other.minorSubversion)) {
            return false;
        }

        if (!(this.incrementalRelease == other.incrementalRelease)) {
            return false;
        }

        return true;
    }


    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return majorVersion() + "." + minorVersion() + "." + minorSubversion()
               + "-" + incrementalRelease();
    }

    //===========================PRIVATE METHODS=========================//

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * <p>nextMajorVersion.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Version} object
     */
    public Version nextMajorVersion() {
        int majorVersion = this.majorVersion + 1;
        final int minorVersion = 0;
        final int minorSubversion = 0;
        final int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    /**
     * <p>nextMinorVersion.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Version} object
     */
    public Version nextMinorVersion() {
        int majorVersion = this.majorVersion;
        int minorVersion = this.minorVersion + 1;
        final int minorSubversion = 0;
        final int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    /**
     * <p>nextMinorSubversion.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Version} object
     */
    public Version nextMinorSubversion() {
        int majorVersion = this.majorVersion;
        int minorVersion = this.minorVersion;
        int minorSubversion = this.minorSubversion + 1;
        final int incrementalRelease = 0;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }

    /**
     * <p>nextIncrementalRelease.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Version} object
     */
    public Version nextIncrementalRelease() {
        int majorVersion = this.majorVersion;
        int minorVersion = this.minorVersion;
        int minorSubversion = this.minorSubversion;
        int incrementalRelease = this.incrementalRelease + 1;

        return new Version(majorVersion, minorVersion, minorSubversion,
                incrementalRelease);
    }


}






