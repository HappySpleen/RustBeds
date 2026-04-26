package me.gabij.multiplebedspawn.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

public final class PluginSerializationUtils {
    private static final String LEGACY_PACKAGE_PREFIX = "me.gabrielfj.";
    private static final String CURRENT_PACKAGE_PREFIX = "me.gabij.";

    private PluginSerializationUtils() {
    }

    public static <T extends Serializable> byte[] serialize(T value) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
            objectOutput.flush();
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize " + value.getClass().getSimpleName(), e);
        }
    }

    public static <T> T deserialize(byte[] bytes, Class<T> type) {
        try (InputStream input = new ByteArrayInputStream(bytes);
             ObjectInputStream objectInput = new LegacyPackageObjectInputStream(input)) {
            return type.cast(objectInput.readObject());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static class LegacyPackageObjectInputStream extends ObjectInputStream {
        private LegacyPackageObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String className = desc.getName();
            if (className.startsWith(LEGACY_PACKAGE_PREFIX)) {
                className = className.replace(LEGACY_PACKAGE_PREFIX, CURRENT_PACKAGE_PREFIX);
            }
            return super.resolveClass(ObjectStreamClass.lookup(Class.forName(className)));
        }
    }
}
