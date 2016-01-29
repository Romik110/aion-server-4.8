package com.aionemu.gameserver.network.aion;

import java.util.List;

import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Letter;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.network.aion.iteminfo.ItemInfoBlob;

/**
 * @author kosyachok, Source
 * @modified Neon
 */
public abstract class MailServicePacket extends AionServerPacket {

	protected Player player;

	/**
	 * @param player
	 */
	public MailServicePacket(Player player) {
		this.player = player;
	}

	protected void writeLettersList(List<Letter> letters) {
		int maxDisplayableSlots = 256; // 0 to 255
		int lettersToDisplay = Math.min(maxDisplayableSlots, letters.size());
		writeD(player.getObjectId());
		writeC(0);
		writeC(maxDisplayableSlots - lettersToDisplay); // freeSlots
		writeC(maxDisplayableSlots - 1); // last displayable slot number (255)
		for (int i = 0; i < lettersToDisplay; i++) {
			Letter letter = letters.get(i);
			writeD(letter.getObjectId());
			writeS(letter.getSenderName());
			writeS(letter.getTitle());
			writeC(letter.isUnread() ? 0 : 1); // isRead
			if (letter.getAttachedItem() != null) {
				writeD(letter.getAttachedItem().getObjectId());
				writeD(letter.getAttachedItem().getItemTemplate().getTemplateId());
			} else {
				writeD(0);
				writeD(0);
			}
			writeQ(letter.getAttachedKinah());
			writeC(letter.getLetterType().getId());
		}
	}

	protected void writeMailMessage(int messageId) {
		writeC(messageId);
	}

	protected void writeMailboxState(int totalCount, int unreadCount, int expressCount, int blackCloudCount) {
		writeH(totalCount);
		writeH(unreadCount);
		writeH(expressCount);
		writeH(blackCloudCount);
	}

	protected void writeLetterRead(Letter letter, long time, int totalCount, int unreadCount, int expressCount, int blackCloudCount) {
		writeD(letter.getRecipientId());
		writeD(totalCount + unreadCount * 0x10000); // total count + unread hex
		writeD(expressCount + blackCloudCount); // unread express + BC letters count
		writeD(letter.getObjectId());
		writeD(letter.getRecipientId());
		writeS(letter.getSenderName());
		writeS(letter.getTitle());
		writeS(letter.getMessage());

		Item item = letter.getAttachedItem();
		if (item != null) {
			ItemTemplate itemTemplate = item.getItemTemplate();

			writeD(item.getObjectId());
			writeD(itemTemplate.getTemplateId());
			writeD(1); // unk
			writeD(0); // unk
			writeNameId(itemTemplate.getNameId());

			ItemInfoBlob itemInfoBlob = ItemInfoBlob.getFullBlob(player, item);
			itemInfoBlob.writeMe(getBuf());
		} else {
			writeQ(0);
			writeQ(0);
			writeD(0);
		}

		writeD((int) letter.getAttachedKinah());
		writeD(0); // AP reward for castle assault/defense (in future)
		writeC(0);
		writeD((int) (time / 1000));
		writeC(letter.getLetterType().getId()); // mail type
	}

	protected void writeLetterState(int letterId, int attachmentType) {
		writeD(letterId);
		writeC(attachmentType);
		writeC(1);
	}

	protected void writeLetterDelete(int totalCount, int unreadCount, int expressCount, int blackCloudCount, int... letterIds) {
		writeD(totalCount + unreadCount * 0x10000); // total count + unread hex
		writeD(expressCount + blackCloudCount); // unread express + BC letters count
		writeH(letterIds.length);
		for (int letterId : letterIds)
			writeD(letterId);
	}

}
