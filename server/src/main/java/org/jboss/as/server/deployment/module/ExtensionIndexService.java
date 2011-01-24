/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server.deployment.module;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.VirtualFile;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ExtensionIndexService implements Service<ExtensionIndex>, ExtensionIndex {
    private final File[] extensionRoots;
    private Map<String, List<ExtensionJar>> extensions = new HashMap<String, List<ExtensionJar>>();

    private static final Logger log = Logger.getLogger("org.jboss.as.server.deployment.module.extension-index");

    public ExtensionIndexService(final File... roots) {
        extensionRoots = roots;
    }

    public synchronized void start(final StartContext context) throws StartException {
        // No point in throwing away the index once it is created.
        context.getController().compareAndSetMode(ServiceController.Mode.ON_DEMAND, ServiceController.Mode.ACTIVE);
        extensions.clear();
        boolean startOk = false;
        try {
            for (File root : extensionRoots) {
                final File[] jars = root.listFiles(new FileFilter() {
                    public boolean accept(final File file) {
                        return file.getName().endsWith(".jar") && !file.isDirectory();
                    }
                });
                if (jars != null) for (File jar : jars) try {
                    final JarFile jarFile = new JarFile(jar);
                    boolean ok = false;
                    try {
                        final Manifest manifest = jarFile.getManifest();
                        final Attributes mainAttributes = manifest.getMainAttributes();
                        final String extensionName = mainAttributes.getValue(Attributes.Name.EXTENSION_NAME);
                        if (extensionName == null) {
                            // not an extension
                            continue;
                        }
                        final String implVersion = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                        final String specVersion = mainAttributes.getValue(Attributes.Name.SPECIFICATION_VERSION);
                        final String implVendorId = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR_ID);
                        ok = true;
                        jarFile.close();
                        final VirtualFile virtualFile = VFS.getChild(jar.getAbsolutePath());
                        final Closeable handle = VFS.mountZip(jar, virtualFile, TempFileProviderService.provider());
                        List<ExtensionJar> extensionJarList = extensions.get(extensionName);
                        if (extensionJarList == null) extensions.put(extensionName, extensionJarList = new ArrayList<ExtensionJar>());
                        extensionJarList.add(new ExtensionJar(virtualFile, implVersion, implVendorId, specVersion, handle));
                    } finally {
                        if (! ok) {
                            VFSUtils.safeClose(jarFile);
                        }
                    }
                } catch (IOException e) {
                    log.debugf("Failed to process JAR manifest for %s: %s", jar, e);
                    continue;
                }
            }
            startOk = true;
        } finally {
            if (! startOk) {
                for (List<ExtensionJar> jars : extensions.values()) {
                    for (ExtensionJar jar : jars) {
                        VFSUtils.safeClose(jar.handle);
                    }
                }
                extensions.clear();
            }
        }
    }

    public synchronized void stop(final StopContext context) {
        for (List<ExtensionJar> jars : extensions.values()) {
            for (ExtensionJar jar : jars) {
                VFSUtils.safeClose(jar.handle);
            }
        }
        extensions.clear();
    }

    public synchronized ResourceRoot findExtension(final String name, final String minSpecVersion, final String minImplVersion, final String requiredVendorId) {
        final List<ExtensionJar> jars = extensions.get(name);
        if (jars != null) for (ExtensionJar extensionJar : jars) {

            // Get the root
            final VirtualFile root = extensionJar.root;

            // Check the parameters
            final String implVendorId = extensionJar.implVendorId;
            if (requiredVendorId != null && ! requiredVendorId.equals(implVendorId)) {
                log.debugf("Skipping extension JAR %s because vendor ID %s does not match required vendor ID %s", root.getName(), requiredVendorId, implVendorId);
                continue;
            }
            if (minSpecVersion != null) {
                final String specVersion = extensionJar.specVersion;
                if (specVersion == null) {
                    log.debugf("Skipping extension JAR %s because spec version is missing but %s is required", root.getName(), minSpecVersion);
                    continue;
                }
                try {
                    if (compareVersion(minSpecVersion, specVersion) > 0) {
                        log.debugf("Skipping extension JAR %s because spec version %s is less than required version %s", root.getName(), specVersion, minSpecVersion);
                        continue;
                    }
                } catch (NumberFormatException e) {
                    log.debugf("Skipping extension JAR %s because version compare of spec version failed");
                    continue;
                }
            }
            if (minImplVersion != null) {
                final String implVersion = extensionJar.implVersion;
                if (implVersion == null) {
                    log.debugf("Skipping extension JAR %s because impl version is missing but %s is required", root.getName(), minImplVersion);
                    continue;
                }
                try {
                    if (compareVersion(minImplVersion, implVersion) > 0) {
                        log.debugf("Skipping extension JAR %s because impl version %s is less than required version %s", root.getName(), implVersion, minImplVersion);
                        continue;
                    }
                } catch (NumberFormatException e) {
                    log.debugf("Skipping extension JAR %s because version compare of impl version failed");
                    continue;
                }
            }

            // Extension matches!
            log.debugf("Matched extension JAR %s", root);
            return new ResourceRoot(name, root, new MountHandle(extensionJar.handle));
        }
        return null;
    }

    public ExtensionIndex getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    private static int compareVersion(String v1, String v2) {
        if (v1.isEmpty() && v2.isEmpty()) {
            return 0;
        } else if (v1.isEmpty()) {
            return -1;
        } else if (v2.isEmpty()) {
            return 1;
        }
        int s1 = 0, e1;
        int s2 = 0, e2;
        for (;;) {
            e1 = v1.indexOf('.', s1);
            e2 = v2.indexOf('.', s2);
            String seg1 = e1 == -1 ? v1.substring(s1) : v1.substring(s1, e1);
            String seg2 = e2 == -1 ? v2.substring(s2) : v2.substring(s2, e2);
            int i1 = Integer.parseInt(seg1);
            int i2 = Integer.parseInt(seg2);
            if (i1 > i2) return 1;
            if (i1 < i2) return -1;
            if (e1 == -1 && e2 == -1) return 0;
            if (e1 == -1) return 1;
            if (e2 == -1) return -1;
            s1 = e1 + 1;
            s2 = e2 + 1;
        }
    }

    static class ExtensionJar {

        private final VirtualFile root;
        private final String implVersion;
        private final String implVendorId;
        private final String specVersion;
        private final Closeable handle;

        ExtensionJar(final VirtualFile root, final String implVersion, final String implVendorId, final String specVersion, final Closeable handle) {
            this.root = root;
            this.implVersion = implVersion;
            this.implVendorId = implVendorId;
            this.specVersion = specVersion;
            this.handle = handle;
        }
    }
}
