/**
 * Copyright (C) 2013 Guestful (info@guestful.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.guestful.client.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 */
public class MemoryStats {

    private static final Set<String> defaultExcludes = new TreeSet<String>(Arrays.asList(
        ClassLoader.class.getName(),
        ThreadGroup.class.getName(),
        Thread.class.getName(),
        Object.class.getName(),
        Class.class.getName(),
        "sun.misc.Launcher$AppClassLoader",
        "java.lang.ClassLoader$NativeLibrary"
    ));

    public static boolean isEnabled() {
        return JavaAgent.isEnabled();
    }

    public static long sizeOf(Object o) {
        return JavaAgent.getInstrumentation().getObjectSize(o);
    }

    public static long fullSizeOf(Object o, Set<String> excludes) {
        Map<Object, Object> visited = new IdentityHashMap<>();
        Map<Class, Object> classes = new LinkedHashMap<>();
        Stack<Object> stack = new Stack<>();
        long size = internalfullSizeOf(o, stack, visited, classes, excludes);
        while (!stack.isEmpty()) {
            size += internalfullSizeOf(stack.pop(), stack, visited, classes, excludes);
        }
        //System.out.println(o.getClass().getName() + " " + classes.keySet());
        visited.clear();
        classes.clear();
        return size;
    }

    private static long internalfullSizeOf(Object obj, Stack<Object> stack, Map<Object, Object> visited, Map<Class, Object> classes, Set<String> excludes) {
        if (obj == null || obj instanceof String && obj == ((String) obj).intern() || visited.containsKey(obj)) {
            return 0;
        }

        Class clazz = obj.getClass();
        String name = clazz.getName();

        if (name.startsWith("org.codehaus.groovy.") ||
            name.startsWith("groovy.") ||
            excludes.contains(name) ||
            defaultExcludes.contains(name)) {
            return 0;
        }

        visited.put(obj, null);
        classes.put(obj.getClass(), null);

        long size = 0;

        // get size of object + primitive variables + member pointers
        size += sizeOf(obj);

        // process all array elements
        if (clazz.isArray()) {
            if (clazz.getName().length() != 2) {
                // skip primitive type array
                int length = Array.getLength(obj);
                for (int i = 0; i < length; i++) {
                    stack.add(Array.get(obj, i));
                }
            }
            return size;
        }

        // process all fields of the object
        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                    field.setAccessible(true);
                    // objects to be estimated are put to stack
                    try {
                        Object objectToAdd = field.get(obj);
                        if (objectToAdd != null) {
                            stack.add(objectToAdd);
                        }
                    } catch (IllegalAccessException e) {
                        // ignore field
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return size;
    }
}
