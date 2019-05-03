package austeretony.groups.common.network.client;

import austeretony.groups.client.GroupsManagerClient;
import austeretony.groups.common.Group;
import austeretony.oxygen.common.network.ProxyPacket;
import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;

public class CPSyncGroup extends ProxyPacket {

    private Group group;

    public CPSyncGroup() {}

    public CPSyncGroup(Group group) {
        this.group = group;
    }

    @Override
    public void write(PacketBuffer buffer, INetHandler netHandler) {
        this.group.write(buffer);
    }

    @Override
    public void read(PacketBuffer buffer, INetHandler netHandler) {
        GroupsManagerClient.instance().readGroup(Group.read(buffer));
    }
}
