package com.aionemu.gameserver.model.stats.container;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai2.AI2Logger;
import com.aionemu.gameserver.ai2.AISubState;
import com.aionemu.gameserver.configs.main.SiegeConfig;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.skill.NpcSkillEntry;
import com.aionemu.gameserver.model.stats.calc.Stat2;
import com.aionemu.gameserver.model.templates.npc.AbyssNpcType;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.model.templates.spawns.siegespawns.SiegeSpawnTemplate;
import com.aionemu.gameserver.model.templates.stats.NpcStatsTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author xavier
 * @modified Estrayl
 */
public class NpcGameStats extends CreatureGameStats<Npc> {

	int currentRunSpeed = 0;
	private long lastAttackTime = 0;
	private long lastAttackedTime = 0;
	private long nextAttackTime = 0;
	private long lastSkillTime = 0;
	private long nextSkillTime = 0;
	private NpcSkillEntry lastSkill = null;
	private long fightStartingTime = 0;
	private int cachedState;
	private AISubState cachedSubState;
	private Stat2 cachedSpeedStat;
	private long lastGeoZUpdate;
	private long lastChangeTarget = 0;

	public NpcGameStats(Npc owner) {
		super(owner);
	}

	@Override
	protected void onStatsChange() {
		super.onStatsChange();
		checkSpeedStats();
	}

	private void checkSpeedStats() {
		Stat2 oldSpeed = cachedSpeedStat;
		cachedSpeedStat = null;
		Stat2 newSpeed = getMovementSpeed();
		cachedSpeedStat = newSpeed;
		if (oldSpeed == null || oldSpeed.getCurrent() != newSpeed.getCurrent()) {
			updateSpeedInfo();
		}
	}

	@Override
	public Stat2 getMaxHp() {
		Stat2 stat = getStat(StatEnum.MAXHP, owner.getObjectTemplate().getStatsTemplate().getMaxHp());
		if (owner.getSpawn() instanceof SiegeSpawnTemplate
			&& owner.getRating() == NpcRating.LEGENDARY
			&& (owner.getObjectTemplate().getAbyssNpcType() == AbyssNpcType.BOSS || owner.getObjectTemplate().getAbyssNpcType() == AbyssNpcType.GUARD
				|| owner.getRace() == Race.GHENCHMAN_LIGHT || owner.getRace() == Race.GHENCHMAN_DARK))
			stat.setBaseRate(SiegeConfig.SIEGE_HEALTH_MULTIPLIER);
		return stat;
	}

	@Override
	public Stat2 getMaxMp() {
		return getStat(StatEnum.MAXMP, owner.getObjectTemplate().getStatsTemplate().getMaxMp());
	}

	@Override
	public Stat2 getAttackSpeed() {
		return getStat(StatEnum.ATTACK_SPEED, owner.getObjectTemplate().getAttackDelay());
	}

	@Override
	public Stat2 getPCR() {
		return getStat(StatEnum.PHYSICAL_CRITICAL_RESIST, owner.getObjectTemplate().getStatsTemplate().getStrikeResist());
	}

	@Override
	public Stat2 getMCR() {
		return getStat(StatEnum.MAGICAL_CRITICAL_RESIST, owner.getObjectTemplate().getStatsTemplate().getSpellResist());
	}

	@Override
	public Stat2 getAllSpeed() {
		return getStat(StatEnum.ALLSPEED, 7500); // TODO current value
	}

	@Override
	public Stat2 getMovementSpeed() {
		int currentState = owner.getState();
		AISubState currentSubState = owner.getAi2().getSubState();
		Stat2 cachedSpeed = cachedSpeedStat;
		if (cachedSpeed != null && cachedState == currentState && cachedSubState == currentSubState) {
			return cachedSpeed;
		}
		Stat2 newSpeedStat = null;
		if (owner.isInState(CreatureState.WEAPON_EQUIPPED)) {
			float speed = 0;
			if (owner.getWalkerGroup() != null)
				speed = owner.getObjectTemplate().getStatsTemplate().getGroupRunSpeedFight();
			else
				speed = owner.getObjectTemplate().getStatsTemplate().getRunSpeedFight();
			newSpeedStat = getStat(StatEnum.SPEED, Math.round(speed * 1000));
		} else if (owner.isInState(CreatureState.WALKING)) {
			float speed = 0;
			if (owner.getWalkerGroup() != null && owner.getAi2().getSubState() == AISubState.WALK_PATH)
				speed = owner.getObjectTemplate().getStatsTemplate().getGroupWalkSpeed();
			else
				speed = owner.getObjectTemplate().getStatsTemplate().getWalkSpeed();
			newSpeedStat = getStat(StatEnum.SPEED, Math.round(speed * 1000));
		} else {
			float multiplier = owner.isFlying() ? 1.3f : 1.0f;
			newSpeedStat = getStat(StatEnum.SPEED, Math.round(owner.getObjectTemplate().getStatsTemplate().getRunSpeed() * multiplier * 1000));
		}
		cachedState = currentState;
		cachedSpeedStat = newSpeedStat;
		return newSpeedStat;
	}

	@Override
	public Stat2 getAttackRange() {
		return getStat(StatEnum.ATTACK_RANGE, owner.getObjectTemplate().getAttackRange() * 1000);
	}

	@Override
	public Stat2 getPDef() {
		int pdef = owner.getObjectTemplate().getStatsTemplate().getPdef();
		return getStat(StatEnum.PHYSICAL_DEFENSE, owner.getAi2().modifyPdef(pdef));
	}

	@Override
	public Stat2 getMDef() {
		return getStat(StatEnum.MAGICAL_DEFEND, 0);
	}

	@Override
	public Stat2 getMResist() {
		return getStat(StatEnum.MAGICAL_RESIST, owner.getObjectTemplate().getStatsTemplate().getMresist());
	}

	@Override
	public Stat2 getMBResist() {
		return getStat(StatEnum.MAGIC_SKILL_BOOST_RESIST, owner.getObjectTemplate().getStatsTemplate().getMsup());
	}

	@Override
	public Stat2 getPower() {
		return getStat(StatEnum.POWER, 100);
	}

	@Override
	public Stat2 getHealth() {
		return getStat(StatEnum.HEALTH, 100);
	}

	@Override
	public Stat2 getAccuracy() {
		return getStat(StatEnum.ACCURACY, 100);
	}

	@Override
	public Stat2 getAgility() {
		return getStat(StatEnum.AGILITY, 100);
	}

	@Override
	public Stat2 getKnowledge() {
		return getStat(StatEnum.KNOWLEDGE, 100);
	}

	@Override
	public Stat2 getWill() {
		return getStat(StatEnum.WILL, 100);
	}

	@Override
	public Stat2 getEvasion() {
		return getStat(StatEnum.EVASION, owner.getObjectTemplate().getStatsTemplate().getEvasion());
	}

	@Override
	public Stat2 getParry() {
		return getStat(StatEnum.PARRY, owner.getObjectTemplate().getStatsTemplate().getParry());
	}

	@Override
	public Stat2 getBlock() {
		return getStat(StatEnum.BLOCK, owner.getObjectTemplate().getStatsTemplate().getBlock());
	}

	@Override
	public Stat2 getMainHandPAttack() {
		int atk = owner.getObjectTemplate().getStatsTemplate().getAttack();
		if (owner.getRating() == NpcRating.NORMAL) { // FIXME: fix templates or formula
			if (owner.getLevel() <= 50)
				atk *= 0.75f;
			else
				atk *= 0.8f;
		}
		return getStat(StatEnum.PHYSICAL_ATTACK, atk);
	}

	@Override
	public Stat2 getMainHandPCritical() {
		return getStat(StatEnum.PHYSICAL_CRITICAL, owner.getObjectTemplate().getStatsTemplate().getPcrit());
	}

	@Override
	public Stat2 getMainHandPAccuracy() {
		return getStat(StatEnum.PHYSICAL_ACCURACY, owner.getObjectTemplate().getStatsTemplate().getAccuracy());
	}

	@Override
	public Stat2 getMainHandMAttack() {
		int matk = owner.getObjectTemplate().getStatsTemplate().getMagicalAttack();
		return getStat(StatEnum.MAGICAL_ATTACK, owner.getAi2().modifyMattack(matk));
	}

	@Override
	public Stat2 getMBoost() {
		return getStat(StatEnum.BOOST_MAGICAL_SKILL, owner.getObjectTemplate().getStatsTemplate().getMagicBoost());
	}

	@Override
	public Stat2 getMAccuracy() {
		int base = owner.getAi2().modifyMaccuracy(owner.getObjectTemplate().getStatsTemplate().getMacc());
		return getStat(StatEnum.MAGICAL_ACCURACY, base);
	}

	@Override
	public Stat2 getMCritical() {
		int value = owner.getObjectTemplate().getStatsTemplate().getMcrit();
		if (value == 0)
			value = 50;
		return getStat(StatEnum.MAGICAL_CRITICAL, value);
	}

	@Override
	public Stat2 getHpRegenRate() {
		NpcStatsTemplate nst = owner.getObjectTemplate().getStatsTemplate();
		return getStat(StatEnum.REGEN_HP, nst.getMaxHp() / 4);
	}

	@Override
	public Stat2 getMpRegenRate() {
		throw new IllegalStateException("No mp regen for NPC");
	}

	public int getLastAttackTimeDelta() {
		return Math.round((System.currentTimeMillis() - lastAttackTime) / 1000f);
	}

	public int getLastAttackedTimeDelta() {
		return Math.round((System.currentTimeMillis() - lastAttackedTime) / 1000f);
	}

	public void renewLastAttackTime() {
		this.lastAttackTime = System.currentTimeMillis();
	}

	public void renewLastAttackedTime() {
		this.lastAttackedTime = System.currentTimeMillis();
	}

	public boolean isNextAttackScheduled() {
		return nextAttackTime - System.currentTimeMillis() > 50;
	}

	public void setFightStartingTime() {
		this.fightStartingTime = System.currentTimeMillis();
	}

	public long getFightStartingTime() {
		return this.fightStartingTime;
	}

	public void setNextAttackTime(long nextAttackTime) {
		this.nextAttackTime = nextAttackTime;
	}

	/**
	 * @return next possible attack time depending on stats
	 */
	public int getNextAttackInterval() {
		long attackDelay = System.currentTimeMillis() - lastAttackTime;
		int attackSpeed = getAttackSpeed().getCurrent();
		if (attackSpeed == 0) {
			attackSpeed = 2000;
		}
		if (owner.getAi2().isLogging()) {
			AI2Logger.info(owner.getAi2(), "adelay = " + attackDelay + " aspeed = " + attackSpeed);
		}
		int nextAttack = 1000;
		if (this instanceof TrapGameStats || this instanceof SummonedObjectGameStats) {
			nextAttack = 0;
		} else if (lastAttackTime == 0 && owner.getTarget() != null && owner.getTarget() instanceof Creature
				&& !MathUtil.isInAttackRange(owner, (Creature) owner.getTarget(), getAttackRange().getCurrent() / 1000f)) {
			nextAttack = 0;
		}
		if (attackDelay < attackSpeed) {
			nextAttack = (int) (attackSpeed - attackDelay);
		}
		return nextAttack;
	}

	public void renewLastSkillTime() {
		this.lastSkillTime = System.currentTimeMillis();
	}

	// not used at the moment
	/*
	 * public void renewLastSkilledTime() { this.lastSkilledTime = System.currentTimeMillis(); }
	 */

	public void renewLastChangeTargetTime() {
		this.lastChangeTarget = System.currentTimeMillis();
	}

	public int getLastSkillTimeDelta() {
		return Math.round((System.currentTimeMillis() - lastSkillTime) / 1000f);
	}

	// not used at the moment
	/*
	 * public int getLastSkilledTimeDelta() { return Math.round((System.currentTimeMillis() - lastSkilledTime) / 1000f); }
	 */

	public int getLastChangeTargetTimeDelta() {
		return Math.round((System.currentTimeMillis() - lastChangeTarget) / 1000f);
	}
	
	public long getLastSkillTime() {
		return lastSkillTime;
	}

	// only use skills after a minimum cooldown of 3 to 9 seconds
	// TODO: Check wether this is a suitable time or not
	public boolean canUseNextSkill() {
		if (nextSkillTime < 0) {
			if (getLastSkillTimeDelta() >= 6 + Rnd.get(-3, 3))
				return true;
		} else {
			if (System.currentTimeMillis() >= lastSkillTime + nextSkillTime)
				return true;
		}
		return false;
	}

	public void setNextSkillTime(int nextSkillTime) {
		this.nextSkillTime = nextSkillTime;
	}

	public void setLastSkill(NpcSkillEntry lastSkill) {
		this.lastSkill = lastSkill;
	}
	
	public NpcSkillEntry getLastSkill() {
		return lastSkill;
	}
	
	@Override
	public void updateSpeedInfo() {
		PacketSendUtility.broadcastPacket(owner, new SM_EMOTION(owner, EmotionType.START_EMOTE2, 0, 0));
	}

	public final long getLastGeoZUpdate() {
		return lastGeoZUpdate;
	}

	/**
	 * @param lastGeoZUpdate
	 *          the lastGeoZUpdate to set
	 */
	public void setLastGeoZUpdate(long lastGeoZUpdate) {
		this.lastGeoZUpdate = lastGeoZUpdate;
	}

}
