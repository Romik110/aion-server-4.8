package com.aionemu.gameserver.controllers;

import java.util.Collection;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.ai2.NpcAI2;
import com.aionemu.gameserver.ai2.event.AIEventType;
import com.aionemu.gameserver.ai2.handler.ShoutEventHandler;
import com.aionemu.gameserver.ai2.poll.AIQuestion;
import com.aionemu.gameserver.controllers.attack.AggroInfo;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.controllers.attack.AttackStatus;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.animations.ObjectDeleteAnimation;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.gameobjects.AionObject;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.Summon;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RewardType;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.team2.TemporaryPlayerTeam;
import com.aionemu.gameserver.model.team2.common.service.PlayerTeamDistributionService;
import com.aionemu.gameserver.model.templates.pet.PetFunctionType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.LOG;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.TYPE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PET;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.RespawnService;
import com.aionemu.gameserver.services.SiegeService;
import com.aionemu.gameserver.services.abyss.AbyssPointsService;
import com.aionemu.gameserver.services.drop.DropRegistrationService;
import com.aionemu.gameserver.services.drop.DropService;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.stats.StatFunctions;
import com.aionemu.gameserver.world.zone.ZoneInstance;

/**
 * This class is for controlling Npc's
 * 
 * @author -Nemesiss-, ATracer (2009-09-29), Sarynth modified by Wakizashi
 */
public class NpcController extends CreatureController<Npc> {

	private static final Logger log = LoggerFactory.getLogger(NpcController.class);

	@Override
	public void notSee(VisibleObject object, ObjectDeleteAnimation animation) {
		super.notSee(object, animation);
		if (object instanceof Creature) {
			Creature creature = (Creature) object;
			getOwner().getAi2().onCreatureEvent(AIEventType.CREATURE_NOT_SEE, creature);
			getOwner().getAggroList().remove(creature);
		}
	}

	@Override
	public void see(VisibleObject object) {
		super.see(object);
		Npc owner = getOwner();
		if (object instanceof Creature) {
			Creature creature = (Creature) object;
			owner.getAi2().onCreatureEvent(AIEventType.CREATURE_SEE, creature);
			if (creature instanceof Player) {
				if (owner.getLifeStats().isAlreadyDead())
					DropService.getInstance().see((Player) creature, owner);
			}
		}
	}

	@Override
	public void onBeforeSpawn() {
		super.onBeforeSpawn();
		Npc owner = getOwner();

		// set state from npc templates
		if (owner.getObjectTemplate().getState() != 0)
			owner.setState(owner.getObjectTemplate().getState());
		else
			owner.setState(CreatureState.NPC_IDLE);

		owner.getLifeStats().setCurrentHpPercent(100);
		owner.getAi2().onGeneralEvent(AIEventType.BEFORE_SPAWNED);

		if (owner.getSpawn().canFly()) {
			owner.setState(CreatureState.FLYING);
		}
		if (owner.getSpawn().getState() != 0) {
			owner.setState(owner.getSpawn().getState());
		}
	}

	@Override
	public void onAfterSpawn() {
		super.onAfterSpawn();
		getOwner().getAi2().onGeneralEvent(AIEventType.SPAWNED);
	}

	@Override
	public void onDespawn() {
		Npc owner = getOwner();
		cancelCurrentSkill(null);
		DropService.getInstance().unregisterDrop(owner);
		owner.getAi2().onGeneralEvent(AIEventType.DESPAWNED);
		super.onDespawn();
	}

	@Override
	public void onDie(@Nonnull Creature lastAttacker) {
		Npc owner = getOwner();
		if (owner.getSpawn().hasPool())
			owner.getSpawn().setUse(owner.getInstanceId(), false);

		boolean shouldDecay = true;
		boolean shouldRespawn = true;
		boolean shouldLoot = true;
		try {
			shouldDecay = owner.getAi2().ask(AIQuestion.SHOULD_DECAY);
			shouldRespawn = owner.getAi2().ask(AIQuestion.SHOULD_RESPAWN);
			shouldLoot = owner.getAi2().ask(AIQuestion.SHOULD_LOOT);
			if (owner.getAi2().ask(AIQuestion.SHOULD_REWARD))
				doReward();
			owner.getPosition().getWorldMapInstance().getInstanceHandler().onDie(owner);
			owner.getAi2().onGeneralEvent(AIEventType.DIED);
		} catch (Exception e) {
			log.error("onDie() exception for " + owner + ":", e);
		}

		super.onDie(lastAttacker);

		if (shouldRespawn && SiegeService.getInstance().isRespawnAllowed(owner))
			RespawnService.scheduleRespawnTask(getOwner());

		if (shouldDecay) {
			RespawnService.scheduleDecayTask(owner);
			if (lastAttacker instanceof Player && shouldLoot) { // pet loot
				Player player = (Player) lastAttacker;
				int npcObjId = owner.getObjectId();
				if (player.getPet() != null && player.getPet().getPetTemplate().getPetFunction(PetFunctionType.LOOT) != null
					&& player.getPet().getCommonData().isLooting()) {
					PacketSendUtility.sendPacket(player, new SM_PET(true, npcObjId));
					Set<DropItem> drops = DropRegistrationService.getInstance().getCurrentDropMap().get(npcObjId);
					if (drops != null) {
						for (DropItem dropItem : drops.toArray(new DropItem[drops.size()])) // array copy since the drops get removed on retrieval
							DropService.getInstance().requestDropItem(player, npcObjId, dropItem.getIndex(), true);
					}
					PacketSendUtility.sendPacket(player, new SM_PET(false, npcObjId));
					if (shouldDecay && (drops == null || drops.size() == 0)) // without drop it's 2 seconds, re-schedule it
						RespawnService.scheduleDecayTask(owner, RespawnService.IMMEDIATE_DECAY);
				}
			}
		} else { // instant despawn (no decay time = no loot)
			delete();
		}
	}

	@Override
	public void doReward() {
		super.doReward();
		AggroList list = getOwner().getAggroList();
		Collection<AggroInfo> finalList = list.getFinalDamageList(true);
		AionObject winner = list.getMostDamage();

		if (winner == null) {
			return;
		}

		float totalDmg = 0;
		for (AggroInfo info : finalList) {
			totalDmg += info.getDamage();
		}

		if (totalDmg <= 0) {
			log.warn("WARN total damage to " + getOwner().getName() + " is " + totalDmg + " reward process was skiped!");
			return;
		}

		for (AggroInfo info : finalList) {
			AionObject attacker = info.getAttacker();

			if (attacker instanceof Npc) // don't reward npcs or summons
				continue;

			float percentage = info.getDamage() / totalDmg;
			if (percentage > 1) {
				log.warn("WARN BIG REWARD PERCENTAGE: " + percentage + " damage: " + info.getDamage() + " total damage: " + totalDmg + " name: "
					+ info.getAttacker().getName() + " obj: " + info.getAttacker().getObjectId() + " owner: " + getOwner().getName() + " player was skiped");
				continue;
			}
			if (attacker instanceof TemporaryPlayerTeam<?>) {
				PlayerTeamDistributionService.doReward((TemporaryPlayerTeam<?>) attacker, percentage, getOwner(), winner);
			} else if (attacker instanceof Player && ((Player) attacker).isInGroup2()) {
				PlayerTeamDistributionService.doReward(((Player) attacker).getPlayerGroup2(), percentage, getOwner(), winner);
			} else if (attacker instanceof Player) {
				Player player = (Player) attacker;
				if (!player.getLifeStats().isAlreadyDead()) {
					// Reward init
					long rewardXp = StatFunctions.calculateExperienceReward(player.getLevel(), getOwner());
					int rewardDp = StatFunctions.calculateDPReward(player, getOwner());
					float rewardAp = 1;

					// Dmg percent correction
					rewardXp *= percentage;
					rewardDp *= percentage;
					rewardAp *= percentage;

					QuestEngine.getInstance().onKill(new QuestEnv(getOwner(), player, 0, 0));
					player.getCommonData().addExp(rewardXp, RewardType.HUNTING, this.getOwner().getObjectTemplate().getNameId());
					player.getCommonData().addDp(rewardDp);
					if (getOwner().getAi2().ask(AIQuestion.SHOULD_REWARD_AP)) {
						int calculatedAp = StatFunctions.calculatePvEApGained(player, getOwner());
						rewardAp *= calculatedAp;
						if (rewardAp >= 1) {
							AbyssPointsService.addAp(player, getOwner(), (int) rewardAp);
						}
					}
				}
				if (attacker.equals(winner) && getOwner().getAi2().ask(AIQuestion.SHOULD_LOOT))
					DropRegistrationService.getInstance().registerDrop(getOwner(), player, player.getLevel(), null);
			}
		}
	}

	@Override
	public Npc getOwner() {
		return (Npc) super.getOwner();
	}

	@Override
	public void onDialogRequest(Player player) {
		// notify npc dialog request observer
		if (!getOwner().getObjectTemplate().canInteract())
			return;
		if (!MathUtil.isInRange(getOwner(), player, getOwner().getObjectTemplate().getTalkDistance() + 1, false)) {
			if (getOwner().getObjectTemplate().isDialogNpc())
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_DIALOG_TOO_FAR_TO_TALK());
			else
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_WAREHOUSE_TOO_FAR_FROM_NPC());
			return;
		}
		player.getObserveController().notifyRequestDialogObservers(getOwner());

		getOwner().getAi2().onCreatureEvent(AIEventType.DIALOG_START, player);
	}

	@Override
	public void onDialogSelect(int dialogId, int prevDialogId, final Player player, int questId, int extendedRewardIndex) {
		QuestEnv env = new QuestEnv(getOwner(), player, questId, dialogId);
		if (!MathUtil.isInRange(getOwner(), player, getOwner().getObjectTemplate().getTalkDistance() + 1, false)
			&& !QuestEngine.getInstance().onDialog(env)) {
			return;
		}
		if (!getOwner().getAi2().onDialogSelect(player, dialogId, questId, extendedRewardIndex)) {
			DialogService.onDialogSelect(dialogId, player, getOwner(), questId, extendedRewardIndex);
		}
	}

	@Override
	public void onAddHate(Creature attacker, boolean isNewInAggroList) {
		if (isNewInAggroList && attacker instanceof Player) {
			if (((Player) attacker).isInTeam()) {
				for (Player player : ((Player) attacker).getCurrentTeam().filterMembers(m -> MathUtil.isIn3dRange(getOwner(), m, 50)))
					QuestEngine.getInstance().onAddAggroList(new QuestEnv(getOwner(), player, 0, 0));
			} else {
				QuestEngine.getInstance().onAddAggroList(new QuestEnv(getOwner(), (Player) attacker, 0, 0));
			}
		}
		super.onAddHate(attacker, isNewInAggroList);
	}

	@Override
	public void onAttack(Creature attacker, int skillId, TYPE type, int damage, boolean notifyAttack, LOG logId, AttackStatus attackStatus) {
		if (getOwner().getLifeStats().isAlreadyDead())
			return;
		final Creature actingCreature;

		// summon should gain its own aggro
		if (attacker instanceof Summon)
			actingCreature = attacker;
		else
			actingCreature = attacker.getActingCreature();

		super.onAttack(actingCreature, skillId, type, damage, notifyAttack, logId, attackStatus);

		Npc npc = getOwner();
		ShoutEventHandler.onEnemyAttack((NpcAI2) npc.getAi2(), attacker);
		if (actingCreature instanceof Player)
			QuestEngine.getInstance().onAttack(new QuestEnv(npc, (Player) actingCreature, 0, 0));
	}

	@Override
	public void onStopMove() {
		getOwner().getMoveController().setInMove(false);
		super.onStopMove();
	}

	@Override
	public void onStartMove() {
		getOwner().getMoveController().setInMove(true);
		super.onStartMove();
	}

	@Override
	public void onEnterZone(ZoneInstance zoneInstance) {
		if (zoneInstance.getAreaTemplate().getZoneName() == null) {
			log.error("No name found for a Zone in the map " + zoneInstance.getAreaTemplate().getWorldId());
		}
	}

	public final float getAttackDistanceToTarget() {
		return getOwner().getGameStats().getAttackRange().getCurrent() / 1000f;
	}

	@Override
	public boolean useSkill(int skillId, int skillLevel) {
		SkillTemplate skillTemplate = DataManager.SKILL_DATA.getSkillTemplate(skillId);
		if (!getOwner().isSkillDisabled(skillTemplate)) {
			getOwner().getGameStats().renewLastSkillTime();
			return super.useSkill(skillId, skillLevel);
		}
		return false;
	}

}
