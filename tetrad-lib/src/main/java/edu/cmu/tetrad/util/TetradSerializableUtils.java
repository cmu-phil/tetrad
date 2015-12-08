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

package edu.cmu.tetrad.util;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.jet.random.Beta;
import cern.jet.random.BreitWigner;
import cern.jet.random.Normal;
import cern.jet.random.Uniform;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import javax.swing.text.Document;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.NumberFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Contains methods used by TestSerialization to ensure that previous "stable"
 * versions of Tetrad will by loadable by later "stable" versions of Tetrad.
 *
 * @author Joseph Ramsey
 * @see #safelySerializableTypes
 */
public class TetradSerializableUtils {

    /**
     * <p>This is a list of types in the Java API that have been designated as
     * "safely serializable". The collections classes are included here (even
     * though they currently are way not safely serializable) since in JDK 1.5
     * the types of objects they contain will be syntactically checkable. (The
     * collections classes are included by designating the interfaces Collection
     * and Map as safely serializable.) String and Class are clearly safely
     * serializable, since are both immutable and do not have constructors that
     * allow non-TetradSerializable objects to be passed to them, and therefore
     * are in no danger of storing any objects in fields whose serializability
     * can't be vouched for. When adding classes to this list, please use
     * similar reasoning to vouch for their safety. Unfortunately, such safety
     * cannot be automatically checked. Class, for instance, </p> <p>We will
     * move to JDK 1.5 as soon as it becomes available for Macs.</p>
     */
    private static final Class[] safelySerializableTypes = new Class[]{
            String.class, Class.class, Date.class, Collection.class, Map.class,
            TetradMatrix.class, Document.class, Normal.class, Uniform.class,
            BreitWigner.class, Beta.class, TetradVector.class, Number.class,
            DoubleMatrix2D.class, DoubleMatrix1D.class, RealMatrix.class,
            NumberFormat.class, RealVector.class
    };

    /**
     * The highest directory inside build/tetrad/classes that contains all of
     * the TetradSerializable classes.
     */
    private final String serializableScope;

    /**
     * The directory to which serialized class instances from the
     * currentDirectory build should be saved.
     */
    private final String currentDirectory;

    /**
     * The directory to which serialized classes from previous versions should
     * be stored.
     */
    private final String archiveDirectory;

    /**
     * Blank constructor. Please set the directory undirectedPaths that you will need
     * using the relevant set methods before calling test methods.
     */
    public TetradSerializableUtils(String serializableScope,
                                   String currentDirectory, String archiveDirectory) {
        if (serializableScope == null) {
            throw new NullPointerException();
        }

        if (currentDirectory == null) {
            throw new NullPointerException();
        }

        if (archiveDirectory == null) {
            throw new NullPointerException();
        }

        this.serializableScope = serializableScope;
        this.currentDirectory = currentDirectory;
        this.archiveDirectory = archiveDirectory;
    }

    /**
     * Checks all of the classes in the serialization scope that implement
     * TetradSerializable to make sure all of their fields are either themselves
     * (a) primitive, (b) TetradSerializable, or (c) assignable from types
     * designated as safely serializable by virtue of being included in the
     * safelySerializableTypes array (see), or are arrays whose lowest order
     * component types satisfy either (a), (b), or (c). Safely serializable
     * classes in the Java API currently include collections classes, plus
     * String and Class. Collections classes are included, since their types
     * will be syntactically checkable in JDK 1.5. String and Class are members
     * of a broader type of Class whose safely can by checked by making sure
     * there is no way to pass into them via constructor or method argument any
     * object that is not TetradSerializable or safely serializable. But it's
     * easy enough now to just make a list.
     *
     * @see #safelySerializableTypes
     */
    public void checkNestingOfFields() {
        List classes = getAssignableClasses(new File(getSerializableScope()),
                TetradSerializable.class);

        boolean foundUnsafeField = false;

        for (Object aClass : classes) {
            Class clazz = (Class) aClass;

            if (TetradSerializableExcluded.class.isAssignableFrom(clazz)) {
                continue;
            }

            Field[] fields = clazz.getDeclaredFields();

            FIELDS:
            for (Field field : fields) {
//                System.out.println(field);

                if (Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                Class type = field.getType();

                while (type.isArray()) {
                    type = type.getComponentType();
                }

                if (type.isPrimitive()) {
                    continue;
                }

                if (type.isEnum()) {
                    continue;
                }

                //                // Printing out Collections fields temporarily.
                //                if (Collection.class.isAssignableFrom(type)) {
                //                    System.out.println("COLLECTION FIELD: " + field);
                //                }
                //
                //                if (Map.class.isAssignableFrom(type)) {
                //                    System.out.println("MAP FIELD: " + field);
                //                }

                if (TetradSerializable.class.isAssignableFrom(type) &&
                        !TetradSerializableExcluded.class.isAssignableFrom(
                                clazz)) {
                    continue;
                }

                for (Class safelySerializableClass : safelySerializableTypes) {
                    if (safelySerializableClass.isAssignableFrom(type)) {
                        continue FIELDS;
                    }
                }

                // A reference in an inner class to the outer class.
                if (field.getName().equals("this$0")) {
                    continue;
                }

                System.out.println("UNSAFE FIELD:" + field);
                foundUnsafeField = true;
            }
        }

        if (foundUnsafeField) {
            throw new RuntimeException(
                    "Unsafe serializable fields found. Please " +
                            "fix immediately.");
        }
    }

    /**
     * Finds all classes inside the stated scope that implement
     * TetradSerializable and serializes them out to the getCurrentDirectory()
     * directory. Abstract methods and interfaces are skipped over. For all
     * other classes C, it is assumed that C has a static constructor of the
     * following form:
     * <pre>
     *     public static C serializableInstance() {
     *         // Returns an instance of C. May be a mind-numbingly simple
     *         // instance, no need to get fancy.
     *     }
     * </pre>
     * The instance returned may be mind-numbingly simple; there is no need to
     * get fancy. It may change over time. The point is to make sure that
     * instances serialized out with earlier versions load with the
     * currentDirectory version.
     *
     * @throws RuntimeException if clazz cannot be serialized. This exception
     *                          has an informative message and wraps the
     *                          originally thrown exception as root cause.
     */
    public void serializeCurrentDirectory() throws RuntimeException {
        clearCurrentDirectory();
        @SuppressWarnings("Convert2Diamond") Map<String, List<String>> classFields =
                new TreeMap<>();

        // Get the classes that implement SerializationCanonicalizer.
        List classes = getAssignableClasses(new File(getSerializableScope()),
                TetradSerializable.class);

        System.out.println(
                "Serializing exemplars of instantiable TetradSerializable " +
                        "in " + getSerializableScope() + ".");
        System.out.println(
                "Writing serialized examplars to " + getCurrentDirectory());

        int index = -1;

        for (Object aClass : classes) {
            Class clazz = (Class) aClass;

            if (TetradSerializableExcluded.class.isAssignableFrom(clazz)) {
                continue;
            }

            if (Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }

            if (Modifier.isInterface(clazz.getModifiers())) {
                continue;
            }

            int numFields = getNumNonSerialVersionUIDFields(clazz);

            if (numFields > 0 && serializableInstanceMethod(clazz) == null) {
                throw new RuntimeException("Class " + clazz + " does not " +
                        "\nhave a public static serializableInstance constructor.");
            }

            if (++index % 50 == 0) {
                System.out.println(index);
            }

            System.out.print(".");

            serializeClass(clazz, classFields);
        }

        try {
            File file = new File(getCurrentDirectory(), "class_fields.ser");
            FileOutputStream out = new FileOutputStream(file);
            ObjectOutputStream objOut = new ObjectOutputStream(out);
            objOut.writeObject(classFields);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("\nFinished serializing exemplars.");
    }

    private int getNumNonSerialVersionUIDFields(Class clazz) {
        Field[] declaredFields = clazz.getDeclaredFields();
        int numFields = declaredFields.length;
        List<Field> fieldList = Arrays.asList(declaredFields);

//        System.out.println(clazz);
//
//        for (Field field : fieldList) {
//            System.out.println(field.getName());
//        }

        for (Field field : fieldList) {
            if (field.getName().equals("serialVersionUID")) {
                numFields--;
            }

            if (field.getName().equals("this$0")) {
                numFields--;
            }
        }

//        System.out.println(numFields);

        return numFields;
    }


    /**
     * Clears the archive directory.
     */
    private void clearCurrentDirectory() {
        File directory = new File(getCurrentDirectory());

        if (directory.exists() && directory.isDirectory()) {
            String[] listing = directory.list();

            for (String aListing : listing) {
                File file = new File(getCurrentDirectory(), aListing);
                boolean deleted = file.delete();
            }
        }

        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * Clears the archive directory.
     */
    public void clearArchiveDirectory() {
        File directory = new File(getArchiveDirectory());

        if (directory.exists() && directory.isDirectory()) {
            String[] listing = directory.list();

            for (String aListing : listing) {
                File file = new File(getArchiveDirectory(), aListing);
                file.delete();
            }
        }

        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * Serializes the given class to the getCurrentDirectory() directory. The
     * static serializedInstance() method of clazz will be called to get an
     * examplar of clazz. This examplar will then be serialized out to a file
     * stored in getCurrentDirectory().
     *
     * @param clazz the class to serialize.
     * @throws RuntimeException if clazz cannot be serialized. This exception
     *                          has an informative message and wraps the
     *                          originally thrown exception as root cause.
     * @see #getCurrentDirectory()
     */
    private void serializeClass(Class clazz,
                                Map<String, List<String>> classFields) throws RuntimeException {
        File current = new File(getCurrentDirectory());

        if (!current.exists() || !current.isDirectory()) {
            throw new IllegalStateException("There is no " +
                    current.getAbsolutePath() + " directory. " +
                    "\nThis is where the serialized classes should be. " +
                    "Please run serializeCurrentDirectory() first.");
        }

        try {
            Field field = clazz.getDeclaredField("serialVersionUID");

            int modifiers = field.getModifiers();
            boolean _static = Modifier.isStatic(modifiers);
            boolean _final = Modifier.isFinal(modifiers);
            field.setAccessible(true);

            if (!_static || !_final || !(23L == field.getLong(null))) {
                throw new RuntimeException(
                        "Class " + clazz + " does not define static final " +
                                "long serialVersionUID = 23L");
            }

            int numFields = getNumNonSerialVersionUIDFields(clazz);

            if (numFields > 0) {
                Method method =
                        clazz.getMethod("serializableInstance");
                Object object = method.invoke(null);

                File file = new File(current, clazz.getName() + ".ser");
                boolean created = file.createNewFile();

                FileOutputStream out = new FileOutputStream(file);
                ObjectOutputStream objOut = new ObjectOutputStream(out);
                objOut.writeObject(object);
                out.close();
            }

            // Make entry in list of class fields.
            ObjectStreamClass objectStreamClass =
                    ObjectStreamClass.lookup(clazz);
            String className = objectStreamClass.getName();
            ObjectStreamField[] fields = objectStreamClass.getFields();
            @SuppressWarnings("Convert2Diamond") List<String> fieldList = new ArrayList<>();

            for (ObjectStreamField objectStreamField : fields) {
                String fieldName = objectStreamField.getName();
                fieldList.add(fieldName);
            }

            classFields.put(className, fieldList);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(("There is no static final long field " +
                    "'serialVersionUID' in " + clazz +
                    ". Please make one and set it " + "to 23L."));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Class " + clazz + "does not " +
                    "have a public static serializableInstance constructor.",
                    e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("The method serializableInstance() of " +
                    "class " + clazz + " is not public.", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Unable to statically call the " +
                    "serializableInstance() method of class " + clazz + ".", e);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not create a new, writeable file " + "in " +
                            getCurrentDirectory() +
                            " when trying to serialize " + clazz + ".", e);
        }
    }

    /**
     * Deserializes all files in the given directory, as a test to make sure
     * they can all be deserialized.
     *
     * @throws RuntimeException if clazz cannot be serialized. This exception
     *                          has an informative message and wraps the
     *                          originally thrown exception as root cause.
     */
    public void deserializeCurrentDirectory() throws RuntimeException {
        System.out.println("Deserializing files in " + getCurrentDirectory());

        File directory = new File(getCurrentDirectory());

        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException(
                    "There is no " + directory + " directory.");
        }

        String[] listing = directory.list();

        for (String aListing : listing) {
            if (!aListing.endsWith(".ser")) {
                continue;
            }

            File file = new File(getCurrentDirectory(), aListing);
            deserializeClass(file);
        }

        System.out.println("Finished deserializing classes in " +
                getCurrentDirectory() + ".");
    }

    /**
     * Deserializes the information in the given file, returning the object
     * represented.
     *
     * @throws RuntimeException if clazz cannot be serialized. This exception
     *                          has an informative message and wraps the
     *                          originally thrown exception as root cause.
     */
    private void deserializeClass(File file) throws RuntimeException {
        try {
            FileInputStream in = new FileInputStream(file);
            ObjectInputStream objIn = new ObjectInputStream(in);
            Object o = objIn.readObject();
            in.close();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("There is no class in the model API " +
                    "to deserialize the object in " + file + ". Perhaps the " +
                    "class was renamed, moved to another package, or removed. " +
                    "In any case, please put it back where it was.", e);
        } catch (IOException e) {
            throw new RuntimeException(
                    "There was an I/O error associated with " +
                            "the process of deserializing the file " + file +
                            ".", e);
        }
    }

    /**
     * Creates a zip archive of the currently serialized files in
     * getCurrentDirectory(), placing the archive in getArchiveDirectory().
     *
     * @throws RuntimeException if clazz cannot be serialized. This exception
     *                          has an informative message and wraps the
     *                          originally thrown exception as root cause.
     * @see #getCurrentDirectory()
     * @see #getArchiveDirectory()
     */
    public void archiveCurrentDirectory() throws RuntimeException {
        System.out.println("Making zip archive of files in " +
                getCurrentDirectory() + ", putting it in " +
                getArchiveDirectory() + ".");

        File current = new File(getCurrentDirectory());

        if (!current.exists() || !current.isDirectory()) {
            throw new IllegalArgumentException("There is no " +
                    current.getAbsolutePath() + " directory. " +
                    "\nThis is where the serialized classes should be. " +
                    "Please run serializeCurrentDirectory() first.");
        }

        File archive = new File(getArchiveDirectory());
        if (archive.exists() && !archive.isDirectory()) {
            throw new IllegalArgumentException("Output directory " +
                    archive.getAbsolutePath() + " is not a directory.");
        }

        if (!archive.exists()) {
            boolean success = archive.mkdirs();
        }

        String[] filenames = current.list();

        // Create a buffer for reading the files
        byte[] buf = new byte[1024];

        try {
            String version = Version.currentRepositoryVersion().toString();

            // Create the ZIP file
            String outFilename = "serializedclasses-" + version + ".zip";
            File _file = new File(getArchiveDirectory(), outFilename);
            FileOutputStream fileOut = new FileOutputStream(_file);
            ZipOutputStream out = new ZipOutputStream(fileOut);

            // Compress the files
            for (String filename : filenames) {
                File file = new File(current, filename);

                FileInputStream in = new FileInputStream(file);

                // Add ZIP entry to output stream.
                ZipEntry entry = new ZipEntry(filename);
                entry.setSize(file.length());
                entry.setTime(file.lastModified());

                out.putNextEntry(entry);

                // Transfer bytes from the file to the ZIP file
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                // Complete the entry
                out.closeEntry();
                in.close();
            }

            // Complete the ZIP file
            out.close();

            System.out.println(
                    "Finished writing zip file " + outFilename + ".");
        } catch (IOException e) {
            throw new RuntimeException(
                    "There was an I/O error associated with " +
                            "the process of zipping up files in " +
                            getCurrentDirectory() + ".", e);
        }
    }

    /**
     * Deserializes examplars stored in archives in getArchiveDirectory().
     *
     * @throws RuntimeException if clazz cannot be serialized. This exception
     *                          has an informative message and wraps the
     *                          originally thrown exception as root cause.
     * @see #getArchiveDirectory()
     */
    public void deserializeArchivedVersions() throws RuntimeException {
        System.out.println("Deserializing archived instances in " +
                getArchiveDirectory() + ".");

        File archive = new File(getArchiveDirectory());

        if (!archive.exists() || !archive.isDirectory()) {
            return;
        }

        String[] listing = archive.list();

        for (String archiveName : listing) {
            if (!(archiveName.endsWith(".zip"))) {
                continue;
            }

            try {
                File file = new File(getArchiveDirectory(), archiveName);
                ZipFile zipFile = new ZipFile(file);
                ZipEntry entry = zipFile.getEntry("class_fields.ser");
                InputStream inputStream = zipFile.getInputStream(entry);
                ObjectInputStream objectIn = new ObjectInputStream(inputStream);
                Map<String, List<String>> classFields =
                        (Map<String, List<String>>) objectIn.readObject();
                zipFile.close();

                for (String className : classFields.keySet()) {

//                    if (classFields.equals("HypotheticalGraph")) continue;

                    List<String> fieldNames = classFields.get(className);
                    Class<?> clazz = Class.forName(className);
                    ObjectStreamClass streamClass =
                            ObjectStreamClass.lookup(clazz);

                    if (streamClass == null) {
                        System.out.println();
                    }

                    for (String fieldName : fieldNames) {
                        assert streamClass != null;
                        ObjectStreamField field =
                                streamClass.getField(fieldName);

                        if (field == null) {
                            throw new RuntimeException("Field '" + fieldName +
                                    "' was dropped from class '" + className +
                                    "' as a serializable field! Please " +
                                    "put it back!!!" + "\nIt used to be in " +
                                    className + " in this archive: " +
                                    archiveName + ".");
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                        "Could not read class_fields.ser in archive + " + archiveName + " .", e);
            } catch (IOException e) {
                throw new RuntimeException("Problem reading archive" +
                        archiveName + "; see cause.", e);
            }

            System.out.println(
                    "...Deserializing instances in " + archiveName + "...");
            ZipEntry zipEntry = null;

            try {
                File file = new File(getArchiveDirectory(), archiveName);
                FileInputStream in = new FileInputStream(file);
                ZipInputStream zipinputstream = new ZipInputStream(in);

                while ((zipEntry = zipinputstream.getNextEntry()) != null) {
                    if (!zipEntry.getName().endsWith(".ser")) {
                        continue;
                    }

                    ObjectInputStream objectIn =
                            new ObjectInputStream(zipinputstream);
                    objectIn.readObject();
                    zipinputstream.closeEntry();
                }

                zipinputstream.close();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                        "Could not read object zipped file " +
                                zipEntry.getName() + " in archive " +
                                archiveName + ". " +
                                "Perhaps the class was renamed, moved to another package, or " +
                                "removed. In any case, please put it back where it was.",
                        e);
            } catch (IOException e) {
                throw new RuntimeException("Problem reading archive" +
                        archiveName + "; see cause.", e);
            }
        }

        System.out.println("Finished deserializing archived instances.");
    }


    /**
     * @return a reference to the public static serializableInstance() method of
     * clazz, if there is one; otherwise, returns null.
     */
    private Method serializableInstanceMethod(Class clazz) {
        Method[] methods = clazz.getMethods();

        for (Method method : methods) {
            if ("serializableInstance".equals(method.getName())) {
                Class[] parameterTypes = method.getParameterTypes();

                if (!(parameterTypes.length == 0)) {
                    continue;
                }

                if (!(Modifier.isStatic(method.getModifiers()))) {
                    continue;
                }

                if (Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }

                return method;
            }
        }

        return null;
    }

    /**
     * @return all of the classes x in the given directory (recursively) such
     * that clazz.isAssignableFrom(x).
     */
    private List<Class> getAssignableClasses(File path,
                                             Class<TetradSerializable> clazz) {
        if (!path.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }

        @SuppressWarnings("Convert2Diamond") List<Class> classes = new LinkedList<>();
        File[] files = path.listFiles();

        if (files == null) {
            throw new NullPointerException();
        }

        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(getAssignableClasses(file, clazz));
            } else {
                String packagePath = file.getPath();
                packagePath = packagePath.replace('\\', '.');
                packagePath = packagePath.replace('/', '.');
                packagePath = packagePath.substring(
                        packagePath.indexOf("edu.cmu"), packagePath.length());
                int index = packagePath.indexOf(".class");

                if (index == -1) {
                    continue;
                }

                packagePath = packagePath.substring(0, index);

                try {
                    Class _clazz =
                            getClass().getClassLoader().loadClass(packagePath);

                    if (clazz.isAssignableFrom(_clazz) && !_clazz.isInterface()) {
                        classes.add(_clazz);
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        return classes;
    }

    private String getSerializableScope() {
        return serializableScope;
    }

    private String getCurrentDirectory() {
        return currentDirectory;
    }

    private String getArchiveDirectory() {
        return archiveDirectory;
    }
}





