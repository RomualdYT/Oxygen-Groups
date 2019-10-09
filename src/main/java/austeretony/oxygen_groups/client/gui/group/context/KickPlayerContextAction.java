package austeretony.oxygen_groups.client.gui.group.context;

import java.util.UUID;

import austeretony.alternateui.screen.core.GUIBaseElement;
import austeretony.oxygen_core.client.api.ClientReference;
import austeretony.oxygen_core.client.api.OxygenHelperClient;
import austeretony.oxygen_core.client.gui.IndexedGUIButton;
import austeretony.oxygen_core.client.gui.elements.OxygenGUIContextMenuElement.ContextMenuAction;
import austeretony.oxygen_groups.client.GroupsManagerClient;
import austeretony.oxygen_groups.client.gui.group.GroupGUISection;

public class KickPlayerContextAction implements ContextMenuAction {

    public final GroupGUISection section;

    public KickPlayerContextAction(GroupGUISection section) {
        this.section = section; 
    }

    @Override
    public String getName(GUIBaseElement currElement) {
        return ClientReference.localize("oxygen_groups.gui.action.kick");
    }

    @Override
    public boolean isValid(GUIBaseElement currElement) {
        UUID playerUUID = ((IndexedGUIButton<UUID>) currElement).index;   
        return !playerUUID.equals(OxygenHelperClient.getPlayerUUID())
                && GroupsManagerClient.instance().getGroupDataManager().getGroupData().getSize() > 2;
    }

    @Override
    public void execute(GUIBaseElement currElement) {
        this.section.openKickPlayerCallback();
    }
}
