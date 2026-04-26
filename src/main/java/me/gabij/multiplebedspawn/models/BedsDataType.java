package me.gabij.multiplebedspawn.models;

import me.gabij.multiplebedspawn.utils.PluginSerializationUtils;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;

public class BedsDataType implements PersistentDataType<byte[], PlayerBedsData> {

    @Override
    public Class<byte[]> getPrimitiveType() {
        return byte[].class;
    }

    @Override
    public Class<PlayerBedsData> getComplexType() {
        return PlayerBedsData.class;
    }

    @Override
    public byte[] toPrimitive(PlayerBedsData complex, PersistentDataAdapterContext context) {
        return PluginSerializationUtils.serialize(complex);
    }

    @Override
    public PlayerBedsData fromPrimitive(byte[] primitive, PersistentDataAdapterContext context) {
        return PluginSerializationUtils.deserialize(primitive, PlayerBedsData.class);
    }

}
