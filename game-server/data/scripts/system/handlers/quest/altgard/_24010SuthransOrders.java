package quest.altgard;

import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.QuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.world.WorldMapType;

/**
 * @author Artur
 * @modified Majka
 */
public class _24010SuthransOrders extends QuestHandler {

	public _24010SuthransOrders() {
		super(24010);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(203557).addOnTalkEvent(questId);
		qe.registerOnEnterWorld(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		final Player player = env.getPlayer();
		final QuestState qs = player.getQuestStateList().getQuestState(questId);
		if (qs == null)
			return false;

		int targetId = env.getTargetId();
		DialogAction dialog = env.getDialog();
		if (targetId != 203557)
			return false;
		if (qs.getStatus() == QuestStatus.START) {
			if (dialog == DialogAction.QUEST_SELECT) {
				qs.setStatus(QuestStatus.REWARD);
				updateQuestStatus(env);
				return sendQuestDialog(env, 1011);
			} else
				return sendQuestStartDialog(env);
		} else if (qs.getStatus() == QuestStatus.REWARD) {
			return sendQuestEndDialog(env);
		}
		return false;
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		Player player = env.getPlayer();
		if (player.getWorldId() == WorldMapType.ALTGARD.getId() && !player.getQuestStateList().hasQuest(questId))
			return QuestService.startQuest(env);
		return false;
	}
}
