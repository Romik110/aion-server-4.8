package com.aionemu.gameserver.network.aion.instanceinfo;

import java.nio.ByteBuffer;
import java.util.List;

import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.DredgionRooms;
import com.aionemu.gameserver.model.instance.instancereward.PvpInstanceReward;
import com.aionemu.gameserver.model.instance.playerreward.PvpInstancePlayerReward;

/**
 * @author xTz
 */
public class DredgionScoreInfo extends InstanceScoreInfo<PvpInstanceReward<PvpInstancePlayerReward>> {

	private final List<Player> players;
	private final List<DredgionRooms> dredgionRooms;

	public DredgionScoreInfo(PvpInstanceReward<PvpInstancePlayerReward> reward, List<Player> players, List<DredgionRooms> dredgionRooms) {
		super(reward);
		this.players = players;
		this.dredgionRooms = dredgionRooms;
	}

	@Override
	public void writeMe(ByteBuffer buf) {
		fillTableWithGroup(buf, Race.ELYOS);
		fillTableWithGroup(buf, Race.ASMODIANS);
		int elyosScore = reward.getElyosPoints();
		int asmosScore = reward.getAsmodiansPoints();
		writeD(buf, reward.getInstanceProgressionType().isEndProgress() ? (asmosScore > elyosScore ? 1 : 0) : 255);
		writeD(buf, elyosScore);
		writeD(buf, asmosScore);
		dredgionRooms.forEach(d -> writeC(buf, d.getState()));
	}

	private void fillTableWithGroup(ByteBuffer buf, Race race) {
		int count = 0;
		for (Player player : players) {
			if (race != player.getRace()) {
				continue;
			}
			PvpInstancePlayerReward playerReward = reward.getPlayerReward(player.getObjectId());
			writeD(buf, playerReward.getOwnerId()); // playerObjectId
			writeD(buf, player.getAbyssRank().getRank().getId()); // playerRank
			writeD(buf, playerReward.getPvPKills()); // pvpKills
			writeD(buf, playerReward.getMonsterKills()); // monsterKills
			writeD(buf, playerReward.getCapturedZones()); // captured
			writeD(buf, playerReward.getPoints()); // playerScore

			if (reward.getInstanceProgressionType().isEndProgress()) {
				writeD(buf, playerReward.getBonusAp() + playerReward.getBaseAp()); // Client needs to know the sum here
				writeD(buf, playerReward.getBaseAp());
			} else {
				writeB(buf, new byte[8]);
			}

			writeC(buf, player.getPlayerClass().getClassId()); // playerClass
			writeC(buf, 0); // unk
			writeS(buf, player.getName(), 54); // playerName
			count++;
		}
		if (count < 6) {
			writeB(buf, new byte[88 * (6 - count)]); // spaces
		}
	}

}
