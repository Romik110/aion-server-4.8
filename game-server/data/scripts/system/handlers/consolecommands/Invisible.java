package consolecommands;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureVisualState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_STATE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.chathandlers.ConsoleCommand;

/**
 * @author ginho1
 * @modified Neon
 */
public class Invisible extends ConsoleCommand {

	public Invisible() {
		super("invisible", "Sets advanced invisibility.");
	}

	@Override
	public void execute(Player player, String... params) {
		if (!player.isInVisualState(CreatureVisualState.HIDE20)) {
			player.getEffectController().setAbnormal(AbnormalState.HIDE.getId());
			player.setVisualState(CreatureVisualState.HIDE20);
			player.getController().onHide();
			PacketSendUtility.broadcastPacket(player, new SM_PLAYER_STATE(player), true);
		}
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_SKILL_EFFECT_INVISIBLE_BEGIN);
	}
}
