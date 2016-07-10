package com.aionemu.gameserver.network.aion.clientpackets;

import com.aionemu.gameserver.configs.main.CustomConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.services.ClassChangeService;
import com.aionemu.gameserver.services.QuestService;

/**
 * @author KKnD , orz, avol
 * @modified Pad
 */
public class CM_DIALOG_SELECT extends AionClientPacket {

	/**
	 * Target object id that client wants to TALK WITH or 0 if wants to unselect
	 */
	private int targetObjectId;
	private int dialogId;
	private int extendedRewardIndex;
	private int lastPage;
	private int questId;
	@SuppressWarnings("unused")
	private int unk;

	/**
	 * Constructs new instance of <tt>CM_DIALOG_SELECT</tt> packet
	 * 
	 * @param opcode
	 * @param state
	 * @param restStates
	 */
	public CM_DIALOG_SELECT(int opcode, State state, State... restStates) {
		super(opcode, state, restStates);
	}

	@Override
	protected void readImpl() {
		targetObjectId = readD();
		dialogId = readH();
		extendedRewardIndex = readH();
		lastPage = readH();
		questId = readD();
		unk = readH(); // unk 4.7
	}

	@Override
	protected void runImpl() {
		final Player player = getConnection().getActivePlayer();
		QuestTemplate questTemplate = DataManager.QUEST_DATA.getQuestById(questId);
		QuestEnv env = new QuestEnv(null, player, questId, 0);
		if (player.isTrading())
			return;

		if (targetObjectId == 0 || targetObjectId == player.getObjectId()) {
			if (questTemplate != null) {
				if (!questTemplate.isCannotShare() && (dialogId == DialogAction.QUEST_ACCEPT_1.id() || dialogId == DialogAction.QUEST_ACCEPT_SIMPLE.id())) {
					QuestService.startQuest(env);
					return;
				} else if (questTemplate.isCanReport() && (dialogId == DialogAction.SELECTED_QUEST_AUTO_REWARD.id()
					|| (dialogId >= DialogAction.SELECTED_QUEST_AUTO_REWARD1.id() && dialogId <= DialogAction.SELECTED_QUEST_AUTO_REWARD15.id()))) {
					QuestService.finishQuest(env);
					return;
				}
			}
			if (QuestEngine.getInstance().onDialog(new QuestEnv(null, player, questId, dialogId)))
				return;
			// FIXME client sends unk1=1, targetObjectId=0, dialogId=2 (trader) => we miss some packet to close window
			if (CustomConfig.ENABLE_SIMPLE_2NDCLASS)
				ClassChangeService.changeClassToSelection(player, dialogId);
			return;
		}

		VisibleObject obj = player.getKnownList().getObject(targetObjectId);
		if (obj instanceof Creature) {
			Creature creature = (Creature) obj;
			creature.getController().onDialogSelect(dialogId, lastPage, player, questId, extendedRewardIndex);
		}
	}
}
