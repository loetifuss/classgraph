/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison, with significant contributions from Davy De Durpel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package nonapi.io.github.classgraph.classloaderhandler;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import nonapi.io.github.classgraph.classpath.ClassLoaderOrder;
import nonapi.io.github.classgraph.classpath.ClasspathOrder;
import nonapi.io.github.classgraph.scanspec.ScanSpec;
import nonapi.io.github.classgraph.utils.FileUtils;
import nonapi.io.github.classgraph.utils.LogNode;

/**
 * Extract classpath entries from the JBoss ClassLoader. See:
 *
 * <p>
 * https://github.com/jboss-modules/jboss-modules/blob/master/src/main/java/org/jboss/modules/ModuleClassLoader.java
 */
class JBossClassLoaderHandler implements ClassLoaderHandler {
    /** Class cannot be constructed. */
    private JBossClassLoaderHandler() {
    }

    /**
     * Check whether this {@link ClassLoaderHandler} can handle a given {@link ClassLoader}.
     *
     * @param classLoaderClass
     *            the {@link ClassLoader} class or one of its superclasses.
     * @param log
     *            the log
     * @return true if this {@link ClassLoaderHandler} can handle the {@link ClassLoader}.
     */
    public static boolean canHandle(final Class<?> classLoaderClass, final LogNode log) {
        return "org.jboss.modules.ModuleClassLoader".equals(classLoaderClass.getName());
    }

    /**
     * Find the {@link ClassLoader} delegation order for a {@link ClassLoader}.
     *
     * @param classLoader
     *            the {@link ClassLoader} to find the order for.
     * @param classLoaderOrder
     *            a {@link ClassLoaderOrder} object to update.
     * @param log
     *            the log
     */
    public static void findClassLoaderOrder(final ClassLoader classLoader, final ClassLoaderOrder classLoaderOrder,
                                            final LogNode log) {
        classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
        classLoaderOrder.add(classLoader, log);
    }

    /**
     * Handle a resource loader.
     *
     * @param resourceLoader
     *            the resource loader
     * @param classLoader
     *            the classloader
     * @param classpathOrderOut
     *            the classpath order
     * @param scanSpec
     *            the scan spec
     * @param log
     *            the log
     */
    private static void handleResourceLoader(final Object resourceLoader, final ClassLoader classLoader,
                                             final ClasspathOrder classpathOrderOut, final ScanSpec scanSpec, final LogNode log) {
        if (resourceLoader == null) {
            return;
        }
        // PathResourceLoader has root field, which is a Path object
        final Object root = classpathOrderOut.reflectionUtils.getFieldVal(false, resourceLoader, "root");

        String path = loadJarPathFromClassicVFS(root, classpathOrderOut);
        if(!isPathExisting(path)) {
            path = loadJarPathFromNewVFS(root, classpathOrderOut, classLoader);
        }

        if (path == null) {
            final File file = (File) classpathOrderOut.reflectionUtils.getFieldVal(false, resourceLoader,
                    "fileOfJar");
            if (file != null) {
                path = file.getAbsolutePath();
            }
        }
        if (path != null) {
            classpathOrderOut.addClasspathEntry(path, classLoader, scanSpec, log);
        } else {
            if (log != null) {
                log.log("Could not determine classpath for ResourceLoader: " + resourceLoader);
            }
        }
    }

    /**
     * Checks if the given path exists and is a regular file.
     *
     * @param path the path to check
     * @return true if the path exists and is a regular file, false otherwise
     */
    private static boolean isPathExisting(String path) {
        if(path != null && !path.isEmpty()) {
            Path possibleExistingPath = Paths.get(path);
            return Files.exists(possibleExistingPath) && Files.isRegularFile(possibleExistingPath);
        }
        return false;
    }

    /**
     * Returns the absolute path of a JAR file from a given root object using the JBoss VFS mechanism.
     * This works for Versions of JBoss/Wildfly that contain the following change:
     *  <a href="https://issues.redhat.com/browse/WFLY-18544">WFLY-18544</a>
     *  <a href="https://issues.redhat.com/browse/JBEAP-25879">JBEAP-25879</a>
     *  <a href="https://issues.redhat.com/browse/JBEAP-25677">JBEAP-25677</a>
     * @param root The root object to get the JAR path from.
     * @param classpathOrderOut The ClasspathOrder object for updating the classpath order.
     * @param classLoader The ClassLoader to use for loading the VFS class. Used as fallback if the current threads classloader
     *                    does not have any dependency on jboss.vfs
     * @return The absolute path of the JAR file, or null if the path couldn't be found.
     */
    private static String loadJarPathFromNewVFS(final Object root, final ClasspathOrder classpathOrderOut, final ClassLoader classLoader) {
        Class<?> jbossVFS = getJBossVFSAccess(classLoader);
        if (root == null || jbossVFS == null) return null;

        // try to find the mount of the root. Type is org.jboss.vfs.VFS.Mount
        Object mount = classpathOrderOut.reflectionUtils.invokeStaticMethod(false, jbossVFS, "getMount",
                root.getClass(), root);
        if (mount == null) return null;

        // try to access the fileSystem of the mount. Type is org.jboss.vfs.spi.FileSystem
        Object fileSystem = classpathOrderOut.reflectionUtils.invokeMethod(false, mount, "getFileSystem");
        if (fileSystem == null)  return null;

        // now access the mount source, which is the file that is used to create the mount.
        File mountSource = (File) classpathOrderOut.reflectionUtils.invokeMethod(false, fileSystem,
                "getMountSource");
        if (mountSource == null)  return null;

        // absolute path of the mountSource should be the 'physical' .jar
        return mountSource.getAbsolutePath();
    }

    /**
     * Get the access to the JBoss VFS class. Tries to load VFS first from the current threads classloader.
     * If VFS can not be found in there, the provided classloader will be used to load VFS from.
     *
     * @param classLoader The ClassLoader to use for loading the JBoss VFS class.
     * @return The Class object representing the JBoss VFS class, or null if it couldn't be found.
     */
    private static Class<?> getJBossVFSAccess(ClassLoader classLoader) {
        Class<?> jbossVFS = null;
        // we need access to the class 'VFS' of org.jboss.vfs
        try {
            // try to load JBoss VFS access from the current threads classloader
            ClassLoader currentThreadClassLoarder = Thread.currentThread().getContextClassLoader();
            jbossVFS = Class.forName("org.jboss.vfs.VFS", true, currentThreadClassLoarder);
        } catch (ClassNotFoundException ex) {
            // if the classloader before could not provide the VFS, try it in the provided class loader
            // NOTE: maybe we can skip this, org.jboss.vfs is less likely to be in every class loader present.
            // It's easier to ensure that it is in the current threads classloader where classgraph is called from?
            try {
                jbossVFS = Class.forName("org.jboss.vfs.VFS", true, classLoader);
            } catch (ClassNotFoundException e) {
                // swallow the exception. If there is no VFS present, we can't do anything...
                e.printStackTrace();
            }
        }
        return jbossVFS;
    }

    /**
     * Returns the absolute path of a JAR file from a given root object using the 'classic' VFS read mechanism.
     * This works for Versions of JBoss/Wildfly prior to this change:
     * <a href="https://issues.redhat.com/browse/WFLY-18544">WFLY-18544</a>
     * <a href="https://issues.redhat.com/browse/JBEAP-25879">JBEAP-25879</a>
     * <a href="https://issues.redhat.com/browse/JBEAP-25677">JBEAP-25677</a>
     * @param root The root object to get the JAR path from.
     * @param classpathOrderOut The ClasspathOrder object for updating the classpath order.
     * @return The absolute path of the JAR file, or null if the path couldn't be found.
     */
    private static String loadJarPathFromClassicVFS(final Object root, final ClasspathOrder classpathOrderOut) {

        // type VirtualFile
        final File physicalFile = (File) classpathOrderOut.reflectionUtils.invokeMethod(false, root,
                "getPhysicalFile");
        String path = null;
        if (physicalFile != null) {
            final String name = (String) classpathOrderOut.reflectionUtils.invokeMethod(false, root, "getName");
            if (name != null) {
                // getParentFile() removes "contents" directory
                final File file = new File(physicalFile.getParentFile(), name);
                if (FileUtils.canRead(file)) {
                    path = file.getAbsolutePath();
                } else {
                    // This is an exploded jar or classpath directory
                    path = physicalFile.getAbsolutePath();
                }
            } else {
                path = physicalFile.getAbsolutePath();
            }
        } else {
            path = (String) classpathOrderOut.reflectionUtils.invokeMethod(false, root, "getPathName");
            if (path == null) {
                // Try Path or File
                final File file = root instanceof Path ? ((Path) root).toFile()
                        : root instanceof File ? (File) root : null;
                if (file != null) {
                    path = file.getAbsolutePath();
                }
            }
        }
        return path;
    }

    /**
     * Handle a module.
     *
     * @param module
     *            the module
     * @param visitedModules
     *            visited modules
     * @param classLoader
     *            the classloader
     * @param classpathOrderOut
     *            the classpath order
     * @param scanSpec
     *            the scan spec
     * @param log
     *            the log
     */
    private static void handleRealModule(final Object module, final Set<Object> visitedModules,
                                         final ClassLoader classLoader, final ClasspathOrder classpathOrderOut, final ScanSpec scanSpec,
                                         final LogNode log) {
        if (!visitedModules.add(module)) {
            // Avoid extracting paths from the same module more than once
            return;
        }
        ClassLoader moduleLoader = (ClassLoader) classpathOrderOut.reflectionUtils.invokeMethod(false, module,
                "getClassLoader");
        if (moduleLoader == null) {
            moduleLoader = classLoader;
        }
        // type VFSResourceLoader[]
        final Object vfsResourceLoaders = classpathOrderOut.reflectionUtils.invokeMethod(false, moduleLoader,
                "getResourceLoaders");
        if (vfsResourceLoaders != null) {
            for (int i = 0, n = Array.getLength(vfsResourceLoaders); i < n; i++) {
                // type JarFileResourceLoader for jars, VFSResourceLoader for exploded jars, PathResourceLoader
                // for resource directories, or NativeLibraryResourceLoader for (usually non-existent) native
                // library "lib/" dirs adjacent to the jarfiles that they were presumably extracted from.
                final Object resourceLoader = Array.get(vfsResourceLoaders, i);
                // Could skip NativeLibraryResourceLoader instances altogether, but testing for their existence
                // only seems to add about 3% to the total scan time.
                // if (!resourceLoader.getClass().getSimpleName().equals("NativeLibraryResourceLoader")) {
                handleResourceLoader(resourceLoader, moduleLoader, classpathOrderOut, scanSpec, log);
                //}
            }
        }
    }

    /**
     * Find the classpath entries for the associated {@link ClassLoader}.
     *
     * @param classLoader
     *            the {@link ClassLoader} to find the classpath entries order for.
     * @param classpathOrder
     *            a {@link ClasspathOrder} object to update.
     * @param scanSpec
     *            the {@link ScanSpec}.
     * @param log
     *            the log.
     */
    public static void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                          final ScanSpec scanSpec, final LogNode log) {
        final Object module = classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getModule");
        final Object callerModuleLoader = classpathOrder.reflectionUtils.invokeMethod(false, module,
                "getCallerModuleLoader");
        final Set<Object> visitedModules = new HashSet<>();
        @SuppressWarnings("unchecked")
        final Map<Object, Object> moduleMap = (Map<Object, Object>) classpathOrder.reflectionUtils
                .getFieldVal(false, callerModuleLoader, "moduleMap");
        final Set<Entry<Object, Object>> moduleMapEntries = moduleMap != null ? moduleMap.entrySet()
                : Collections.<Entry<Object, Object>> emptySet();
        for (final Entry<Object, Object> ent : moduleMapEntries) {
            // type FutureModule
            final Object val = ent.getValue();
            // type Module
            final Object realModule = classpathOrder.reflectionUtils.invokeMethod(false, val, "getModule");
            handleRealModule(realModule, visitedModules, classLoader, classpathOrder, scanSpec, log);
        }
        // type Map<String, List<LocalLoader>>
        @SuppressWarnings("unchecked")
        final Map<String, List<?>> pathsMap = (Map<String, List<?>>) classpathOrder.reflectionUtils
                .invokeMethod(false, module, "getPaths");
        for (final Entry<String, List<?>> ent : pathsMap.entrySet()) {
            for (final Object /* ModuleClassLoader$1 */ localLoader : ent.getValue()) {
                // type ModuleClassLoader (outer class)
                final Object moduleClassLoader = classpathOrder.reflectionUtils.getFieldVal(false, localLoader,
                        "this$0");
                // type Module
                final Object realModule = classpathOrder.reflectionUtils.getFieldVal(false, moduleClassLoader,
                        "module");
                handleRealModule(realModule, visitedModules, classLoader, classpathOrder, scanSpec, log);
            }
        }
    }
}
