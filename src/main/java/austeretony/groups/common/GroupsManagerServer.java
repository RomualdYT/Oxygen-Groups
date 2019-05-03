package austeretony.groups.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import austeretony.groups.common.config.GroupsConfig;
import austeretony.groups.common.main.EnumGroupsChatMessages;
import austeretony.groups.common.main.GroupInviteRequest;
import austeretony.groups.common.main.GroupReadinessCheckProcess;
import austeretony.groups.common.main.GroupsMain;
import austeretony.groups.common.main.KickPlayerRequest;
import austeretony.groups.common.main.KickPlayerVotingProcess;
import austeretony.groups.common.main.ReadinessCheckRequest;
import austeretony.groups.common.network.client.CPAddPlayerToGroup;
import austeretony.groups.common.network.client.CPGroupsCommand;
import austeretony.groups.common.network.client.CPRemovePlayerFromGroup;
import austeretony.groups.common.network.client.CPSyncGroup;
import austeretony.groups.common.network.client.CPSyncPlayersHealth;
import austeretony.groups.common.network.client.CPUpdateLeader;
import austeretony.oxygen.common.api.IPersistentData;
import austeretony.oxygen.common.api.OxygenHelperServer;
import austeretony.oxygen.common.core.api.CommonReference;
import austeretony.oxygen.common.main.EnumOxygenChatMessages;
import austeretony.oxygen.common.main.OxygenMain;
import austeretony.oxygen.common.main.SharedPlayerData;
import austeretony.oxygen.common.util.StreamUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class GroupsManagerServer implements IPersistentData {

    private static GroupsManagerServer instance;

    private final Map<Long, Group> groups = new ConcurrentHashMap<Long, Group>();

    private final Map<UUID, Long> groupAccess = new ConcurrentHashMap<UUID, Long>();

    private int syncCounter;

    private GroupsManagerServer() {}

    public static void create() {
        if (instance == null) 
            instance = new GroupsManagerServer();
    }

    public static GroupsManagerServer instance() {
        return instance;
    }

    public boolean groupExist(long groupId) {
        return this.groups.containsKey(groupId);
    }

    public Group getGroup(long groupId) {
        return this.groups.get(groupId);
    }

    public Group getGroup(UUID playerUUID) {
        return this.groups.get(this.groupAccess.get(playerUUID));
    }

    public boolean haveGroup(UUID playerUUID) {
        return this.groupAccess.containsKey(playerUUID);
    }

    public void inviteToGroup(EntityPlayerMP playerMP, UUID targetUUID) {
        UUID senderUUID = CommonReference.uuid(playerMP);
        if (!senderUUID.equals(targetUUID) 
                && OxygenHelperServer.isOnline(targetUUID)
                && this.canInvite(senderUUID) 
                && this.canBeInvited(targetUUID)) {
            OxygenHelperServer.sendRequest(playerMP, CommonReference.playerByUUID(targetUUID), 
                    new GroupInviteRequest(GroupsMain.GROUP_REQUEST_ID, senderUUID, CommonReference.username(playerMP)), true);
        } else
            OxygenHelperServer.sendMessage(playerMP, OxygenMain.OXYGEN_MOD_INDEX, EnumOxygenChatMessages.REQUEST_RESET.ordinal());
    }

    public void processAcceptedGroupRequest(EntityPlayer player, UUID leaderUUID) {
        if (this.haveGroup(leaderUUID))
            this.addToGroup(player, leaderUUID);
        else
            this.createGroup(player, leaderUUID);
        OxygenHelperServer.saveWorldDataDelegated(this);
    }

    private void createGroup(EntityPlayer player, UUID leaderUUID) {
        UUID invitedUUID = CommonReference.uuid(player);
        Group group = new Group();
        group.createId();
        group.setLeader(leaderUUID);
        group.addPlayer(leaderUUID);
        group.addPlayer(invitedUUID);
        this.groups.put(group.getId(), group);
        this.groupAccess.put(leaderUUID, group.getId());
        this.groupAccess.put(invitedUUID, group.getId());

        OxygenHelperServer.addObservedPlayer(leaderUUID, invitedUUID, false);
        OxygenHelperServer.addObservedPlayer(invitedUUID, leaderUUID, true);

        GroupsMain.network().sendTo(new CPSyncGroup(group), CommonReference.playerByUUID(leaderUUID));
        GroupsMain.network().sendTo(new CPSyncGroup(group), (EntityPlayerMP) player);
    }

    private void addToGroup(EntityPlayer player, UUID leaderUUID) {   
        UUID invitedUUID = CommonReference.uuid(player);
        Group group = this.getGroup(leaderUUID);

        for (UUID uuid : group.getPlayers()) {
            OxygenHelperServer.addObservedPlayer(invitedUUID, uuid, false);
            OxygenHelperServer.addObservedPlayer(uuid, invitedUUID, false);
        }
        OxygenHelperServer.saveObservedPlayersData();

        group.addPlayer(invitedUUID);
        this.groupAccess.put(invitedUUID, group.getId());

        OxygenHelperServer.syncObservedPlayersData((EntityPlayerMP) player);       
        GroupsMain.network().sendTo(new CPSyncGroup(group), (EntityPlayerMP) player);

        for (UUID playerUUID : group.getPlayers())
            if (!playerUUID.equals(invitedUUID) 
                    && OxygenHelperServer.isOnline(playerUUID))
                GroupsMain.network().sendTo(new CPAddPlayerToGroup(OxygenHelperServer.getPlayerIndex(invitedUUID)), CommonReference.playerByUUID(playerUUID));
    }

    public void leaveGroup(UUID playerUUID) {
        if (this.haveGroup(playerUUID)) {
            Group group = this.getGroup(playerUUID);

            if (group.getSize() == 2) {
                this.dismissGroup(group);
                return;
            }
            if (group.isLeader(playerUUID)) {
                UUID uuid = group.getRandomOnlinePlayer();
                if (uuid == null) {
                    this.dismissGroup(group);
                    return;
                }
                group.setLeader(uuid);
            }

            group.removePlayer(playerUUID);
            this.groupAccess.remove(playerUUID);

            for (UUID uuid : group.getPlayers()) {
                OxygenHelperServer.removeObservedPlayer(playerUUID, uuid, false);
                OxygenHelperServer.removeObservedPlayer(uuid, playerUUID, false);
            }
            OxygenHelperServer.saveObservedPlayersData();

            if (OxygenHelperServer.isOnline(playerUUID)) 
                GroupsMain.network().sendTo(new CPGroupsCommand(CPGroupsCommand.EnumCommand.LEAVE_GROUP), CommonReference.playerByUUID(playerUUID));

            for (UUID uuid : group.getPlayers())
                if (OxygenHelperServer.isOnline(uuid))
                    GroupsMain.network().sendTo(new CPRemovePlayerFromGroup(playerUUID), CommonReference.playerByUUID(uuid));

            OxygenHelperServer.saveWorldDataDelegated(this);
        }
    }

    public void dismissGroup(Group group) {
        for (UUID playerUUID : group.getPlayers()) {
            this.groupAccess.remove(playerUUID);

            for (UUID uuid : group.getPlayers())
                OxygenHelperServer.removeObservedPlayer(playerUUID, uuid, false);

            if (OxygenHelperServer.isOnline(playerUUID))
                GroupsMain.network().sendTo(new CPGroupsCommand(CPGroupsCommand.EnumCommand.LEAVE_GROUP), CommonReference.playerByUUID(playerUUID));
        }
        OxygenHelperServer.saveObservedPlayersData();
        this.groups.remove(group.getId());

        OxygenHelperServer.saveWorldDataDelegated(this);
    }

    public void startReadinessCheck(EntityPlayerMP playerMP) {
        UUID playerUUID = CommonReference.uuid(playerMP);
        Group group;
        if (this.haveGroup(playerUUID) && (group = this.getGroup(playerUUID)).isLeader(playerUUID)) {
            if (!group.isVoting()) {
                group.startVote();

                EntityPlayerMP player;
                for (UUID uuid : group.getPlayers()) {
                    if (OxygenHelperServer.isOnline(uuid)) {
                        player = CommonReference.playerByUUID(uuid);
                        OxygenHelperServer.sendMessage(player, GroupsMain.GROUPS_MOD_INDEX, EnumGroupsChatMessages.GROUP_READINESS_CHECK_STARTED.ordinal());
                        OxygenHelperServer.addNotification(player, new ReadinessCheckRequest(GroupsMain.READINESS_CHECK_REQUEST_ID));
                    }
                }

                OxygenHelperServer.addWorldProcess(new GroupReadinessCheckProcess(group.getId()));
            }
        }
    }

    public void processVoteFor(EntityPlayer player) {
        UUID playerUUID = CommonReference.uuid(player);
        if (this.haveGroup(playerUUID))
            this.getGroup(playerUUID).voteFor();
    }

    public void processVoteAgainst(EntityPlayer player) {}//empty

    public void stopReadinessCheck(long groupId) {
        if (this.groupExist(groupId)) {
            Group group = GroupsManagerServer.instance().getGroup(groupId);
            group.stopVote();
            EnumGroupsChatMessages msg = group.getVoteResult() ? EnumGroupsChatMessages.GROUP_READY : EnumGroupsChatMessages.GROUP_NOT_READY;
            for (UUID uuid : group.getPlayers())
                if (OxygenHelperServer.isOnline(uuid))
                    OxygenHelperServer.sendMessage(CommonReference.playerByUUID(uuid), GroupsMain.GROUPS_MOD_INDEX, msg.ordinal());
        }
    }

    public void promoteToLeader(EntityPlayerMP playerMP, int index) {
        UUID leaderUUID = CommonReference.uuid(playerMP);
        Group group;
        if (this.haveGroup(leaderUUID) && (group = this.getGroup(leaderUUID)).isLeader(leaderUUID)) {
            if (OxygenHelperServer.isOnline(index)) {
                UUID playerUUID = OxygenHelperServer.getSharedPlayerData(index).getPlayerUUID();
                group.setLeader(playerUUID);

                for (UUID uuid : group.getPlayers())
                    if (OxygenHelperServer.isOnline(uuid))
                        GroupsMain.network().sendTo(new CPUpdateLeader(index), CommonReference.playerByUUID(uuid));

                OxygenHelperServer.saveWorldDataDelegated(this);
            }
        }
    }

    public void startKickPlayerVoting(EntityPlayerMP playerMP, UUID kickUUID) {
        UUID playerUUID = CommonReference.uuid(playerMP);
        Group group;
        if (this.haveGroup(playerUUID)) {
            group = this.getGroup(playerUUID);
            if (!group.isVoting()) {
                group.startVote();

                SharedPlayerData kickData = OxygenHelperServer.getPersistentSharedData(kickUUID);

                EntityPlayerMP player;
                for (UUID uuid : group.getPlayers()) {
                    if (OxygenHelperServer.isOnline(uuid) && !uuid.equals(kickUUID)) {
                        player = CommonReference.playerByUUID(uuid);
                        OxygenHelperServer.sendMessage(player, GroupsMain.GROUPS_MOD_INDEX, EnumGroupsChatMessages.KICK_PLAYER_VOTING_STARTED.ordinal(), kickData.getUsername());
                        OxygenHelperServer.addNotification(player, new KickPlayerRequest(GroupsMain.READINESS_CHECK_REQUEST_ID, kickData.getUsername()));
                    }
                }

                OxygenHelperServer.addWorldProcess(new KickPlayerVotingProcess(group.getId(), kickUUID));
            }
        }
    }

    public void stopKickPlayerVoting(long groupId, UUID playerUUID) {
        if (this.groupExist(groupId)) {
            Group group = GroupsManagerServer.instance().getGroup(groupId);
            group.stopVote();
            EnumGroupsChatMessages msg = EnumGroupsChatMessages.PLAYER_NOT_KICKED;
            if (group.getVoteResult()) {
                this.leaveGroup(playerUUID);
                msg = EnumGroupsChatMessages.PLAYER_KICKED;
            }
            String username = OxygenHelperServer.getPersistentSharedData(playerUUID).getUsername();
            for (UUID uuid : group.getPlayers())
                if (OxygenHelperServer.isOnline(uuid))
                    OxygenHelperServer.sendMessage(CommonReference.playerByUUID(uuid), GroupsMain.GROUPS_MOD_INDEX, msg.ordinal(), username);
        }
    }

    //TODO onPlayerLoggedIn()
    public void onPlayerLoaded(EntityPlayer player) {    
        UUID playerUUID = CommonReference.uuid(player);
        if (this.haveGroup(playerUUID))
            GroupsMain.network().sendTo(new CPSyncGroup(this.getGroup(playerUUID)), (EntityPlayerMP) player);
    }

    //TODO onPlayerLoggedOut()
    public void onPlayerLoggedOut(EntityPlayer player) {}

    private boolean canInvite(UUID playerUUID) {
        if (!this.haveGroup(playerUUID))
            return true;
        Group group = this.getGroup(playerUUID);
        if (group.isLeader(playerUUID) && group.getSize() < GroupsConfig.PLAYERS_PER_PARTY.getIntValue())
            return true;
        return false;
    }

    private boolean canBeInvited(UUID playerUUID) {
        return !this.haveGroup(playerUUID);
    }

    public void runGroupDataSynchronization() {
        this.syncCounter++;
        if (this.syncCounter >= 20) {//TODO Sync every 20 ticks = 1 second
            this.syncCounter = 0;
            UUID[] online;
            EntityPlayerMP[] players;
            EntityPlayerMP playerMP;
            int[] indexes;
            float[] currHealth, maxHealth;
            int count;
            for (Group group : this.groups.values()) {
                count = 0;
                online = new UUID[group.getSize()];
                for (UUID uuid : group.getPlayers())
                    if (OxygenHelperServer.isOnline(uuid))
                        online[count++] = uuid;

                indexes = new int[count];
                currHealth = new float[count];
                maxHealth = new float[count];
                players = new EntityPlayerMP[count];
                count = 0;

                for (UUID uuid : online) {
                    if (uuid == null) break;
                    playerMP = CommonReference.playerByUUID(uuid);
                    if (playerMP == null) return;//TODO Debug
                    players[count] = playerMP;
                    indexes[count] = OxygenHelperServer.getPlayerIndex(uuid);
                    currHealth[count] = playerMP.getHealth();
                    maxHealth[count] = playerMP.getMaxHealth();   
                    count++;
                }

                for (EntityPlayerMP player : players)
                    GroupsMain.routineNetwork().sendTo(new CPSyncPlayersHealth(indexes, currHealth, maxHealth), player);
            }
        }
    }

    @Override
    public String getName() {
        return "groups data";
    }

    @Override
    public String getModId() {
        return GroupsMain.MODID;
    }

    @Override
    public String getPath() {
        return "groups/groups.dat";
    }

    @Override
    public void write(BufferedOutputStream bos) throws IOException {
        StreamUtils.write((short) this.groups.size(), bos);
        for (Group group : this.groups.values())
            group.write(bos);
    }

    @Override
    public void read(BufferedInputStream bis) throws IOException {
        int amount = StreamUtils.readShort(bis);
        Group group;
        for (int i = 0; i < amount; i++) {
            group = Group.read(bis);
            this.groups.put(group.getId(), group);
            for (UUID playerUUID : group.getPlayers())
                this.groupAccess.put(playerUUID, group.getId());
        }
    }

    public void reset() {
        this.groups.clear();
        this.groupAccess.clear();
    }
}
