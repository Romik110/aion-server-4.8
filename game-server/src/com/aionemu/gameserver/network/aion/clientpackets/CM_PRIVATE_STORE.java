package com.aionemu.gameserver.network.aion.clientpackets;

import com.aionemu.gameserver.configs.main.AntiHackConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.trade.TradePSItem;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.restrictions.RestrictionsManager;
import com.aionemu.gameserver.services.PrivateStoreService;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author Simple
 */
public class CM_PRIVATE_STORE extends AionClientPacket {

	/**
	 * Private store information
	 */
	private Player player;
	private TradePSItem[] tradePSItems;

	public CM_PRIVATE_STORE(int opcode, State state, State... restStates) {
		super(opcode, state, restStates);
	}

	@Override
	protected void readImpl() {
		int itemCount = readH();
		tradePSItems = new TradePSItem[itemCount];
		for (int i = 0; i < itemCount; i++) {
			int itemObjId = readD();
			int itemId = readD();
			int count = readH();
			long price = readQ();
			tradePSItems[i] = new TradePSItem(itemObjId, itemId, count, price);
		}
	}

	@Override
	protected void runImpl() {
		player = getConnection().getActivePlayer();
		if (player.getPlayerAccount().isHacked() && !AntiHackConfig.HDD_SERIAL_HACKED_ACCOUNTS_ALLOW_PRIVATESTORE) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_L2AUTH_S_KICKED_DOUBLE_LOGIN);
			PacketSendUtility.sendMessage(player,
				"Account hacking attempt detected. You can't use this function. Please, contact your server support.");
			return;
		}
		if (!RestrictionsManager.canPrivateStore(player))
			return;

		if (tradePSItems.length <= 0)
			PrivateStoreService.closePrivateStore(player);
		else
			PrivateStoreService.addItems(player, tradePSItems);
	}
}
