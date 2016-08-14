package ai.instance.drakenspire;

import com.aionemu.gameserver.ai2.AIName;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.SkillEngine;

import ai.AggressiveNpcAI2;

/**
 * @author Estrayl
 */
@AIName("seal_guardian")
public class SealGuardianAI2 extends AggressiveNpcAI2 {

	@Override
	protected void handleDied() {
		Player killer = getAggroList().getMostPlayerDamage();
		if (killer.getLifeStats().isAlreadyDead())
			killer = getAggroList().getList().stream()
				.filter(aggroInfo -> aggroInfo.getAttacker() instanceof Player && !((Player) aggroInfo.getAttacker()).getLifeStats().isAlreadyDead())
				.findFirst().map(aggroInfo -> (Player) aggroInfo.getAttacker()).orElseGet(null);
		if (killer != null)
			SkillEngine.getInstance().applyEffect(21625, getOwner(), killer);
		super.handleDied();
		getOwner().getController().onDelete();
	}
}
