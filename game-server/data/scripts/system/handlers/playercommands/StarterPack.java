package playercommands;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.Month;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.dao.StarterPackDAO;
import com.aionemu.gameserver.model.account.PlayerAccountData;
import com.aionemu.gameserver.model.gameobjects.LetterType;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.services.mail.SystemMailService;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.Util;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

/**
 * @author Estrayl
 * @modified Neon
 * TODO: Remove me fast pls by sir!
 */
public class StarterPack extends PlayerCommand {
	
	private final LocalDateTime maxCreationTime = LocalDateTime.of(2015, Month.NOVEMBER, 15, 23, 59, 59);
	private final StarterPackDAO dao = DAOManager.getDAO(StarterPackDAO.class);

	public StarterPack() {
		super("starterpack", "Sends the starter pack to the specified player.\n" + ChatUtil.color("ATTENTION!", Color.RED) 
			+ " This will work only one time per account!");

		setParamInfo("<character name> - Sets the specified character as receiver of the pack.");
	}

	@Override
	protected void execute(Player player, String... params) {
		if (params.length == 0) {
			sendInfo(player);
			return;
		}

		synchronized (player) {
			int receivingPlayerId = dao.loadReceivingPlayer(player);
			if (receivingPlayerId > 0) {
				sendInfo(player, "You already got your starter pack!");
				return;
			}
			String name = Util.convertName(params[0]);
	
			for (PlayerAccountData pad : player.getPlayerAccount().getSortedAccountsList()) {
				PlayerCommonData pcd = pad.getPlayerCommonData();
				if (pcd != null && pcd.getName().equals(name)) {
					LocalDateTime creationTime = pad.getCreationDate().toLocalDateTime();
					if (!creationTime.isBefore(maxCreationTime)) {
						sendInfo(player, "The character " + pcd.getName() + "'s creation date is too late.");
						return;
					}
					if (pcd.getMailboxLetters() >= 100) {
						sendInfo(player, "The mailbox of " + pcd.getName() + " is full.");
						return;
					}
					sendReward(player.getPlayerAccount().getId(), pcd);
					sendInfo(player, "Starter Pack successfully sent to character " + pcd.getName());
					return;
				}
			}
			
			sendInfo(player, "Character " + name + " was not found on your account!");
		}
	}
	
	private void sendReward(int accId, PlayerCommonData pcd) {
		try {
			SystemMailService.getInstance().sendMail("Beyond Aion",	pcd.getName(), "Starter Pack",
				"Greetings Daeva!\n\n" + "With this, you received your exclusive starter package which marks you as a backer of the Beyond Aion project."
					+ " We thank you for your support and wish you the best fun for the future of this server.\n\n" + "Enjoy your stay on Beyond Aion!"
					, 188051867, 1, 0, LetterType.EXPRESS);
		} finally {
			dao.storeReceivingPlayer(accId, pcd.getPlayerObjId());
		}
	}
}
