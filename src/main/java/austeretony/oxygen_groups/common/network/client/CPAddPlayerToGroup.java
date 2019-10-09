package austeretony.oxygen_groups.common.network.client;

import austeretony.oxygen_core.client.api.OxygenHelperClient;
import austeretony.oxygen_core.common.PlayerSharedData;
import austeretony.oxygen_core.common.network.Packet;
import austeretony.oxygen_groups.client.GroupsManagerClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.INetHandler;

public class CPAddPlayerToGroup extends Packet {

    private PlayerSharedData sharedData;

    public CPAddPlayerToGroup() {}

    public CPAddPlayerToGroup(PlayerSharedData sharedData) {
        this.sharedData = sharedData;
    }

    @Override
    public void write(ByteBuf buffer, INetHandler netHandler) {
        this.sharedData.write(buffer);
    }

    @Override
    public void read(ByteBuf buffer, INetHandler netHandler) {
        final PlayerSharedData sharedData = PlayerSharedData.read(buffer);
        OxygenHelperClient.addRoutineTask(()->GroupsManagerClient.instance().getGroupDataManager().addToGroup(sharedData));
    }
}
