package com.antixray.api;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ApiStabilityTest {

    private static final List<String> EXPECTED_CLASSES = List.of(
        "com.antixray.api.AntiXrayAPI",
        "com.antixray.api.BlockStateProxy",
        "com.antixray.api.BlockVisibilityEvent",
        "com.antixray.api.DefaultObfuscationProvider",
        "com.antixray.api.ObfuscationProvider",
        "com.antixray.api.PlayerXraySuspicionEvent",
        "com.antixray.api.AlertLevel"
    );

    @Test
    void testExpectedClassesExistAndAreValid() throws Exception {
        for (String className : EXPECTED_CLASSES) {
            Class<?> clazz = Class.forName(className);
            assertNotNull(clazz, "Class " + className + " should exist.");
            validateClassStability(clazz);
        }
    }

    @Test
    void testDynamicPackageScan() throws Exception {
        Set<Class<?>> scannedClasses = new HashSet<>();
        
        // Find the package directory on the classpath
        String packagePath = "com/antixray/api";
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(packagePath);
        
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (resource.getProtocol().equals("file")) {
                File dir = new File(resource.getFile().replace("%20", " "));
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.getName().endsWith(".class") && !file.getName().contains("$") && !file.getName().equals("package-info.class") && !file.getName().endsWith("Test.class")) {
                                String className = "com.antixray.api." + file.getName().substring(0, file.getName().length() - 6);
                                scannedClasses.add(Class.forName(className));
                            }
                        }
                    }
                }
            }
        }

        assertFalse(scannedClasses.isEmpty(), "Dynamic scan should find at least the expected API classes.");

        // Check that all scanned classes are within our expected API set to prevent unauthorized additions
        for (Class<?> clazz : scannedClasses) {
            assertTrue(EXPECTED_CLASSES.contains(clazz.getName()), 
                "Found unexpected class in API package: " + clazz.getName() + ". All new API classes must be added to the stability test set.");
            validateClassStability(clazz);
        }
    }

    private void validateClassStability(Class<?> clazz) {
        // Only enforce public/protected classes/interfaces/enums
        if (!isPublicOrProtected(clazz.getModifiers())) {
            return;
        }

        // Validate superclass
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class && superclass != Enum.class) {
            assertTrue(isTypeAllowed(superclass), 
                "Class " + clazz.getName() + " extends disallowed internal class: " + superclass.getName());
        }

        // Validate interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            assertTrue(isTypeAllowed(iface), 
                "Class " + clazz.getName() + " implements disallowed internal interface: " + iface.getName());
        }

        // Validate fields
        for (Field field : clazz.getDeclaredFields()) {
            if (isPublicOrProtected(field.getModifiers())) {
                assertTrue(isTypeAllowed(field.getType()), 
                    "Public/protected field " + field.getName() + " in class " + clazz.getName() + " has disallowed type: " + field.getType().getName());
            }
        }

        // Validate constructors
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (isPublicOrProtected(constructor.getModifiers())) {
                for (Class<?> paramType : constructor.getParameterTypes()) {
                    assertTrue(isTypeAllowed(paramType), 
                        "Public/protected constructor in class " + clazz.getName() + " has parameter with disallowed type: " + paramType.getName());
                }
                for (Class<?> exceptionType : constructor.getExceptionTypes()) {
                    assertTrue(isTypeAllowed(exceptionType), 
                        "Public/protected constructor in class " + clazz.getName() + " declares disallowed exception type: " + exceptionType.getName());
                }
            }
        }

        // Validate methods
        for (Method method : clazz.getDeclaredMethods()) {
            if (isPublicOrProtected(method.getModifiers())) {
                // Return type
                assertTrue(isTypeAllowed(method.getReturnType()), 
                    "Public/protected method " + method.getName() + " in class " + clazz.getName() + " has disallowed return type: " + method.getReturnType().getName());
                
                // Parameters
                for (Class<?> paramType : method.getParameterTypes()) {
                    assertTrue(isTypeAllowed(paramType), 
                        "Public/protected method " + method.getName() + " in class " + clazz.getName() + " has parameter with disallowed type: " + paramType.getName());
                }

                // Exceptions
                for (Class<?> exceptionType : method.getExceptionTypes()) {
                    assertTrue(isTypeAllowed(exceptionType), 
                        "Public/protected method " + method.getName() + " in class " + clazz.getName() + " declares disallowed exception type: " + exceptionType.getName());
                }
            }
        }
    }

    private boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private boolean isTypeAllowed(Class<?> type) {
        if (type == null) {
            return true;
        }
        if (type.isArray()) {
            return isTypeAllowed(type.getComponentType());
        }
        if (type.isPrimitive()) {
            return true;
        }
        String name = type.getName();
        return name.startsWith("java.") ||
               name.startsWith("javax.") ||
               name.startsWith("org.bukkit.") ||
               name.startsWith("com.antixray.api.");
    }
}
