package me.gabij.multiplebedspawn.models;

import me.gabij.multiplebedspawn.utils.PluginSerializationUtils;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;

public class BedDataType implements PersistentDataType<byte[], BedData> {

    @Override
    public Class<byte[]> getPrimitiveType() {
        return byte[].class;
    }

    @Override
    public Class<BedData> getComplexType() {
        return BedData.class;
    }

    @Override
    public byte[] toPrimitive(BedData complex, PersistentDataAdapterContext context) {
        return PluginSerializationUtils.serialize(complex);
    }

    @Override
    public BedData fromPrimitive(byte[] primitive, PersistentDataAdapterContext context) {
        return PluginSerializationUtils.deserialize(primitive, BedData.class);
    }

}
