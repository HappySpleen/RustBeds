package me.happy.rustbeds.models;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.List;

final class LegacySerializationUtils {
    private static final String CURRENT_PACKAGE_PREFIX = "me.happy.rustbeds.";
    private static final String PREVIOUS_PACKAGE_PREFIX = "me.gabij.multiplebedspawn.";
    private static final int[] OLDEST_AUTHOR_PACKAGE_ASCII = {109, 101, 46, 103, 97, 98, 114, 105, 101, 108, 102, 106, 46};

    private LegacySerializationUtils() {
    }

    static byte[] serialize(Serializable value) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
            objectOutput.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize bed data", exception);
        }
    }

    static <T> T deserialize(byte[] value, Class<T> type) {
        try (InputStream input = new ByteArrayInputStream(value);
                ObjectInputStream objectInput = new LegacyObjectInputStream(input)) {
            return type.cast(objectInput.readObject());
        } catch (IOException | ClassNotFoundException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private static String getOldAuthorPackagePrefix() {
        StringBuilder builder = new StringBuilder();
        for (int asciiValue : OLDEST_AUTHOR_PACKAGE_ASCII) {
            builder.append((char) asciiValue);
        }
        return builder.toString();
    }

    private static List<String> getLegacyPackagePrefixes() {
        return List.of(
                PREVIOUS_PACKAGE_PREFIX,
                getOldAuthorPackagePrefix() + "multiplebedspawn.");
    }

    private static class LegacyObjectInputStream extends ObjectInputStream {
        private static final List<String> LEGACY_PACKAGE_PREFIXES = getLegacyPackagePrefixes();

        private LegacyObjectInputStream(InputStream input) throws IOException {
            super(input);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass description) throws IOException, ClassNotFoundException {
            String className = description.getName();
            for (String legacyPackagePrefix : LEGACY_PACKAGE_PREFIXES) {
                if (className.startsWith(legacyPackagePrefix)) {
                    String relocatedName = CURRENT_PACKAGE_PREFIX + className.substring(legacyPackagePrefix.length());
                    return Class.forName(relocatedName, false, LegacySerializationUtils.class.getClassLoader());
                }
            }
            return super.resolveClass(description);
        }
    }
}
