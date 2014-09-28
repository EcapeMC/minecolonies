package com.minecolonies.network.messages;

import com.minecolonies.colony.ColonyManager;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * Add or Update a ColonyView on the client
 */
public class ColonyViewMessage implements IMessage
{
    private UUID colonyId;
    private NBTTagCompound colonyView;
    private boolean newSubscription;

    public ColonyViewMessage(){}

    public ColonyViewMessage(UUID colonyId, NBTTagCompound colonyView, boolean newSubscription)
    {
        this.colonyId = colonyId;
        this.colonyView = colonyView;
        this.newSubscription = newSubscription;
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeLong(colonyId.getMostSignificantBits());
        buf.writeLong(colonyId.getLeastSignificantBits());
        buf.writeBoolean(newSubscription);
        ByteBufUtils.writeTag(buf, colonyView);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        colonyId = new UUID(buf.readLong(), buf.readLong());
        newSubscription = buf.readBoolean();
        colonyView = ByteBufUtils.readTag(buf);
    }

    public static class Handler implements IMessageHandler<ColonyViewMessage, IMessage>
    {
        @Override
        public IMessage onMessage(ColonyViewMessage message, MessageContext ctx)
        {
            return ColonyManager.handleColonyViewPacket(message.colonyId, message.colonyView, message.newSubscription);
        }
    }
}