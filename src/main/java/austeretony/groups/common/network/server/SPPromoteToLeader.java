package austeretony.groups.common.network.server;

import austeretony.groups.common.GroupsManagerServer;
import austeretony.oxygen.common.network.ProxyPacket;
import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;

public class SPPromoteToLeader extends ProxyPacket {

    private int index;

    public SPPromoteToLeader() {}

    public SPPromoteToLeader(int index) {
        this.index = index;
    }

    @Override
    public void write(PacketBuffer buffer, INetHandler netHandler) {
        buffer.writeShort(this.index);
    }

    @Override
    public void read(PacketBuffer buffer, INetHandler netHandler) {
        GroupsManagerServer.instance().promoteToLeader(getEntityPlayerMP(netHandler), buffer.readShort());
    }
}
