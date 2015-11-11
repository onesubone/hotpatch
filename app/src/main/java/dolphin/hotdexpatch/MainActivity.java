package dolphin.hotdexpatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dalvik.system.BaseDexClassLoader;

public class MainActivity extends Activity {
    private static final String TAG = "ClassLoaderPlugin";
    private static final String TMP_DEX_FOLDER_NAME = "tmp_dex";

    public void loadPluginDex(View view) {
        File optimizedDirectory = getApplication().getFilesDir();
        BaseDexClassLoader classLoader = (BaseDexClassLoader) this.getClassLoader();
        Log.d("ddd", "current class load = " + classLoader);
        try {
            File file = new File("/data/data/dolphin.hotdexpatch/classes.dex");
            ArrayList<File> additionalClassPathEntries = new ArrayList<File>();
            additionalClassPathEntries.add(file);
//            install(classLoader, additionalClassPathEntries, optimizedDirectory);

            if (Build.VERSION.SDK_INT >= 19) {
                DexHotPatchHelper.V19.install(classLoader, additionalClassPathEntries, optimizedDirectory);
            } else if (Build.VERSION.SDK_INT >= 14) {
                DexHotPatchHelper.V14.install(classLoader, additionalClassPathEntries, optimizedDirectory);
            } else {
                // Not support
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void nextActivity(View view){
        startActivity(new Intent(this, Main2Activity.class));
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                File optimizedDirectory)
            throws IllegalArgumentException, IllegalAccessException,
            NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
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
