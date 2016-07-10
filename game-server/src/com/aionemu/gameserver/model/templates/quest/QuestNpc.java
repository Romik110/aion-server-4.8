package com.aionemu.gameserver.model.templates.quest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.questEngine.QuestEngine;

import javolution.util.FastTable;

/**
 * @author MrPoke
 */
public class QuestNpc {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(QuestNpc.class);

	private final Set<Integer> onQuestStart;
	private final List<Integer> onKillEvent;
	private final List<Integer> onTalkEvent;
	private final List<Integer> onAttackEvent;
	private final List<Integer> onAddAggroListEvent;
	private final List<Integer> onAtDistanceEvent;
	private final int npcId;
	private final int questRange;
	private Set<Integer> allQuestIds;
	private boolean wasSpawned;

	public QuestNpc(int npcId, int questRange) {
		this.npcId = npcId;
		this.questRange = questRange;
		onQuestStart = new HashSet<>(0);
		onKillEvent = new FastTable<>();
		onTalkEvent = new FastTable<>();
		onAttackEvent = new FastTable<>();
		onAddAggroListEvent = new FastTable<>();
		onAtDistanceEvent = new FastTable<>();
		allQuestIds = new HashSet<>(0);
	}

	public QuestNpc(int npcId) {
		this(npcId, 20);
	}

	private void registerCanAct(int questId, int npcId) {
		allQuestIds.add(questId);
		QuestEngine.getInstance().registerCanAct(questId, npcId);
	}

	public void addOnQuestStart(int questId) {
		if (!onQuestStart.contains(questId)) {
			onQuestStart.add(questId);
			allQuestIds.add(questId);
		}
	}

	public Set<Integer> getOnQuestStart() {
		return onQuestStart;
	}

	public void addOnAttackEvent(int questId) {
		if (!onAttackEvent.contains(questId)) {
			onAttackEvent.add(questId);
			allQuestIds.add(questId);
		}
	}

	public List<Integer> getOnAttackEvent() {
		return onAttackEvent;
	}

	public void addOnKillEvent(int questId) {
		if (!onKillEvent.contains(questId)) {
			onKillEvent.add(questId);
			registerCanAct(questId, npcId);
		}
	}

	public List<Integer> getOnKillEvent() {
		return onKillEvent;
	}

	public void addOnTalkEvent(int questId) {
		if (!onTalkEvent.contains(questId)) {
			onTalkEvent.add(questId);
			registerCanAct(questId, npcId);
		}
	}

	public List<Integer> getOnTalkEvent() {
		return onTalkEvent;
	}

	public void addOnAddAggroListEvent(int questId) {
		if (!onAddAggroListEvent.contains(questId)) {
			onAddAggroListEvent.add(questId);
			registerCanAct(questId, npcId);
		}
	}

	public List<Integer> getOnAddAggroListEvent() {
		return onAddAggroListEvent;
	}

	public void addOnAtDistanceEvent(int questId) {
		if (!onAtDistanceEvent.contains(questId)) {
			onAtDistanceEvent.add(questId);
			registerCanAct(questId, npcId);
		}
	}

	public List<Integer> getOnDistanceEvent() {
		return onAtDistanceEvent;
	}

	public int getNpcId() {
		return npcId;
	}

	/**
	 * The method returns quest ids which handlers must be asked for constant spawn requirements. Is cleaned once SpawnEngine spawns them.
	 * 
	 * @return
	 */
	public Set<Integer> getNotCheckedQuestIds() {
		return allQuestIds;
	}

	public boolean isWasSpawned() {
		return wasSpawned;
	}

	public void setWasSpawned(boolean wasSpawned) {
		if (wasSpawned) {
			this.wasSpawned = wasSpawned;
			allQuestIds.clear();
		}
	}

	public int getQuestRange() {
		return questRange;
	}

}
