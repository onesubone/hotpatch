package dolphin.hotdexpatch;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import dalvik.system.BaseDexClassLoader;

/**
 * Created by hanyanan on 2015/11/11.
 */
public class DexHotPatchHelper {
    private static final String TAG = "DexHotPatchHelper";

    public static class V19 {
        /**
         * 适用于4.4，5.0， 5.1，但是6.0未测
         */
        public synchronized static void install(ClassLoader loader, ArrayList<File> additionalClassPathEntries,
                                                File optimizedDirectory)throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException{
            Field pathListField = findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
            expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
                    suppressedExceptions));
            if (suppressedExceptions.size() > 0) {
                for (IOException e : suppressedExceptions) {
                    Log.w(TAG, "Exception in makeDexElement", e);
                }
                Field suppressedExceptionsField =
                        findField(loader, "dexElementsSuppressedExceptions");
                IOException[] dexElementsSuppressedExceptions =
                        (IOException[]) suppressedExceptionsField.get(loader);
                if (dexElementsSuppressedExceptions == null) {
                    dexElementsSuppressedExceptions =
                            suppressedExceptions.toArray(
                                    new IOException[suppressedExceptions.size()]);
                } else {
                    IOException[] combined =
                            new IOException[suppressedExceptions.size() +
                                    dexElementsSuppressedExceptions.length];
                    suppressedExceptions.toArray(combined);
                    System.arraycopy(dexElementsSuppressedExceptions, 0, combined,
                            suppressedExceptions.size(), dexElementsSuppressedExceptions.length);
                    dexElementsSuppressedExceptions = combined;
                }
                suppressedExceptionsField.set(loader, dexElementsSuppressedExceptions);
            }
        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makeDexElements}.
         */
        private static Object[] makeDexElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory,
                ArrayList<IOException> suppressedExceptions)
                throws IllegalAccessException, InvocationTargetException,
                NoSuchMethodException {
            Method makeDexElements =
                    findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class,
                            ArrayList.class);
            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory,
                    suppressedExceptions);
        }
    }


    public static class V14 {
        /**
         * Installer for platform versions 14, 15, 16, 17 and 18.
         * 从4.0-4.3
         */
        public synchronized static void install(ClassLoader loader, ArrayList<File> additionalClassPathEntries,
                                                   File optimizedDirectory) throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Field pathListField = findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory));
        }


        public static class V4 {
//            /**
//             * Installer for platform versions 4 to 13.
//             */
//                private static void install(ClassLoader loader, List<File> additionalClassPathEntries)
//                        throws IllegalArgumentException, IllegalAccessException,
//                        NoSuchFieldException, IOException {
//            /* The patched class loader is expected to be a descendant of
//             * dalvik.system.DexClassLoader. We modify its
//             * fields mPaths, mFiles, mZips and mDexs to append additional DEX
//             * file entries.
//             */
//                    int extraSize = additionalClassPathEntries.size();
//                    Field pathField = findField(loader, "path");
//                    StringBuilder path = new StringBuilder((String) pathField.get(loader));
//                    String[] extraPaths = new String[extraSize];
//                    File[] extraFiles = new File[extraSize];
//                    ZipFile[] extraZips = new ZipFile[extraSize];
//                    DexFile[] extraDexs = new DexFile[extraSize];
//                    for (ListIterator<File> iterator = additionalClassPathEntries.listIterator();
//                         iterator.hasNext();) {
//                        File additionalEntry = iterator.next();
//                        String entryPath = additionalEntry.getAbsolutePath();
//                        path.append(':').append(entryPath);
//                        int index = iterator.previousIndex();
//                        extraPaths[index] = entryPath;
//                        extraFiles[index] = additionalEntry;
//                        extraZips[index] = new ZipFile(additionalEntry);
//                        extraDexs[index] = DexFile.loadDex(entryPath, entryPath + ".dex", 0);
//                    }
//                    pathField.set(loader, path.toString());
//                    expandFieldArray(loader, "mPaths", extraPaths);
//                    expandFieldArray(loader, "mFiles", extraFiles);
//                    expandFieldArray(loader, "mZips", extraZips);
//                    expandFieldArray(loader, "mDexs", extraDexs);
//                }
//

        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makeDexElements}.
         */
        private static Object[] makeDexElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory)
                throws IllegalAccessException, InvocationTargetException,
                NoSuchMethodException {
            Method makeDexElements =
                    findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class);
            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory);
        }
    }

    /**
     * Replace the value of a field containing a non null array, by a new array containing the
     * elements of the original array plus the elements of extraElements.
     *
     * @param instance      the instance whose field is to be modified.
     * @param fieldName     the field to modify.
     * @param extraElements elements to append at the end of the array.
     */
    private static void expandFieldArray(Object instance, String fieldName,
                                         Object[] extraElements) throws NoSuchFieldException, IllegalArgumentException,
            IllegalAccessException {
        Field jlrField = findField(instance, fieldName);
        Object[] original = (Object[]) jlrField.get(instance);
        Object[] combined = (Object[]) Array.newInstance(original.getClass().getComponentType(), original.length + extraElements.length);
        System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
        System.arraycopy(original, 0, combined, extraElements.length, original.length);

        jlrField.set(instance, combined);
    }

    /**
     * Locates a given field anywhere in the class inheritance hierarchy.
     *
     * @param instance an object to search the field into.
     * @param name     field name
     * @return a field object
     * @throws NoSuchFieldException if the field cannot be located
     */
    private static Field findField(Object instance, String name) throws NoSuchFieldException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(name);
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                return field;
            } catch (NoSuchFieldException e) {
                // ignore and search next
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    /**
     * Locates a given method anywhere in the class inheritance hierarchy.
     *
     * @param instance       an object to search the method into.
     * @param name           method name
     * @param parameterTypes method parameter types
     * @return a method object
     * @throws NoSuchMethodException if the method cannot be located
     */
    private static Method findMethod(Object instance, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Method method = clazz.getDeclaredMethod(name, parameterTypes);
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                return method;
            } catch (NoSuchMethodException e) {
                // ignore and search next
            }
        }
        throw new NoSuchMethodException("Method " + name + " with parameters " +
                Arrays.asList(parameterTypes) + " not found in " + instance.getClass());
    }
}
