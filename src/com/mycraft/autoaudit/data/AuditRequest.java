package com.mycraft.autoaudit.data;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.bukkit.entity.Player;

import com.mycraft.autoaudit.AutoAudit;
import com.mycraft.autoaudit.utils.AuditCriteria;
import com.mycraft.autoaudit.utils.LanguageManager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class AuditRequest {
	private int id;
	private String name;
	private String group;
	private String buildName;
	private String buildDescription;
	private String buildWorld;
	private double buildX;
	private double buildY;
	private double buildZ;
	private String link;
	private Date modifyTime;
	private Date finishTime = new Date(0);

	private int auditState = 0;
	private int passCount = 0;
	private int failCount = 0;
	private double avgScore = 0;

	private ArrayList<Comment> comments;
	private ArrayList<AuditRecord> auditRecords;
	private AuditCriteria criteria;

	private LanguageManager lang;

	public AuditRequest(int id, String name, String group, String bName, String bDesc, String bWorld, double x,
			double y, double z) {
		this.id = id;
		this.comments = new ArrayList<>();
		this.auditRecords = new ArrayList<>();
		this.criteria = AuditCriteria.getInstance();
		this.lang = LanguageManager.getInstance();
		updateDetail(bName, bDesc, bWorld, x, y, z);
		this.name = name;
		this.group = group;
		this.modifyTime = new Date();
	}

	public AuditRequest(int id, String name, String group, String bName, String bDesc, String bWorld, double x,
			double y, double z, String link, Date modifyTime, Date finishTime) {
		this.id = id;
		this.comments = new ArrayList<>();
		this.auditRecords = new ArrayList<>();
		this.criteria = AuditCriteria.getInstance();
		this.lang = LanguageManager.getInstance();
		updateDetail(bName, bDesc, bWorld, x, y, z);
		this.name = name;
		this.group = group;
		this.modifyTime = modifyTime;
		this.finishTime = finishTime;
		this.link = link;
	}

	// -------- getter
	public String getGroup() {
		return group;
	}

	public String getName() {
		return name;
	}

	public double getAvgScore() {
		return avgScore;
	}

	public ArrayList<AuditRecord> getAuditRecords() {
		return auditRecords;
	}

	public int getAuditState() {
		return auditState;
	}

	public Date getFinishTime() {
		return finishTime;
	}

	public Date getModifyTime() {
		return modifyTime;
	}

	public int getId() {
		return id;
	}

	public String getBuildWorld() {
		return buildWorld;
	}

	public double getBuildX() {
		return buildX;
	}

	public double getBuildY() {
		return buildY;
	}

	public double getBuildZ() {
		return buildZ;
	}

	public String getLink() {
		return link;
	}

	// -------- update and add data
	public void addComment(int id, String name, String comment, Date date) {
		Comment c = checkIfCommented(name);
		if (c != null) { // 小概率事件，有了重复记录
			if (c.getModifyTime().after(date)) { // 已有更新记录，抛弃新记录
				String query = "DELETE FROM " + AutoAudit.getTableName() + "Comments" + " WHERE `id` = " + id;
				AutoAudit.updateSQL(query);
			} else { // 抛弃老记录
				String query = "DELETE FROM " + AutoAudit.getTableName() + "Comments" + " WHERE `id` = " + c.getId();
				AutoAudit.updateSQL(query);
				comments.remove(c);
				comments.add(new Comment(id, name, comment, date));
			}
		} else {
			comments.add(new Comment(id, name, comment, date));
		}
	}

	public void modifyComment(String name, String comment) {
		Comment c = null;
		for (Comment r : comments) {
			if (r.getCommeter().equals(name)) {
				c = r;
				break;
			}
		}
		if (c != null) {
			String query = "UPDATE " + AutoAudit.getTableName() + "Comments" + " SET `comment` = '" + comment
					+ "', `modifyTime` = '" + AutoAudit.getCurrentSQLTime() + "' WHERE `id` = " + c.getId();
			AutoAudit.updateSQL(query);
			c.updateComment(comment);
		} else {
			String query = "Insert into " + AutoAudit.getTableName() + "Comments"
					+ " (`requestID`,`commeter`,`player`,`comment`) VALUES(" + id + ", '" + name + "', '" + this.name
					+ "', '" + comment + "'); ";
			int key = AutoAudit.insertSQL(query);
			comments.add(new Comment(key, name, comment));
		}
	}

	public void appendComment(String name, String comment) {
		Comment c = null;
		for (Comment r : comments) {
			if (r.getCommeter().equals(name)) {
				c = r;
				break;
			}
		}
		if (c == null) {
			return;
		}
		String newComment = c.getComment() + comment;
		String query = "UPDATE " + AutoAudit.getTableName() + "Comments" + " SET `comment` = '" + newComment
				+ "', `modifyTime` = '" + AutoAudit.getCurrentSQLTime() + "' WHERE `id` = " + c.getId();
		AutoAudit.updateSQL(query);
		c.updateComment(newComment);
	}

	public void removeComment(int id) {
		for (Comment c : comments) {
			if (c.getId() == id) {
				comments.remove(c);
				return;
			}
		}
	}

	public void addAuditRecord(int id, String auditor, double score, String desc, Date date) {
		AuditRecord ar = checkIfScored(auditor);
		if (ar != null) { // 小概率事件，有了重复记录
			if (ar.getModifyTime().after(date)) { // 已有更新记录，抛弃新记录
				String query = "DELETE FROM " + AutoAudit.getTableName() + "Audits" + " WHERE `id` = " + id;
				AutoAudit.updateSQL(query);
			} else { // 抛弃老记录
				String query = "DELETE FROM " + AutoAudit.getTableName() + "Audits" + " WHERE `id` = " + ar.getId();
				AutoAudit.updateSQL(query);
				auditRecords.remove(ar);
				auditRecords.add(new AuditRecord(id, auditor, name, score, desc, date));
			}
		} else {
			auditRecords.add(new AuditRecord(id, auditor, name, score, desc, date));
		}
		updateStateAndScore();
	}

	public void modifyAuditRecord(String auditor, double score, String desc) {
		AuditRecord rec = checkIfScored(auditor);
		if (rec != null) {
			String query = "UPDATE " + AutoAudit.getTableName() + "Audits" + " SET `score` = " + score
					+ ", `modifyTime` = '" + AutoAudit.getCurrentSQLTime() + "', `remark` = '" + desc
					+ "' WHERE `id` = " + rec.getId();
			AutoAudit.updateSQL(query);
			rec.update(score, desc);
		} else {
			String query = "Insert into " + AutoAudit.getTableName() + "Audits"
					+ " (`requestID`,`auditor`,`player`,`score`,`remark`) VALUES(" + id + ", '" + auditor + "', '"
					+ name + "', " + score + ", '" + desc + "'); ";
			int key = AutoAudit.insertSQL(query);
			auditRecords.add(new AuditRecord(key, auditor, name, score, desc));
		}
		updateStateAndScore();
	}

	public void modifyLink(String link) {
		this.link = link;
		String query = "UPDATE " + AutoAudit.getTableName() + "Requests" + " SET `link` ='" + link + "' WHERE `id` = "
				+ id;
		AutoAudit.updateSQL(query);
	}

	public void appendAuditRemark(String auditor, String desc) {
		AuditRecord rec = checkIfScored(auditor);
		if (rec == null) {
			return;
		}
		String newRemark = rec.getRemark() + desc;
		String query = "UPDATE " + AutoAudit.getTableName() + "Audits" + " SET `remark` = '" + newRemark
				+ "' WHERE `id` = " + rec.getId();
		AutoAudit.updateSQL(query);
		rec.update(rec.getScore(), newRemark);
	}

	public void updateStateAndScore() {
		passCount = 0;
		failCount = 0;
		avgScore = 0;
		for (AuditRecord r : auditRecords) {
			avgScore += r.getScore();
			if (r.getScore() >= criteria.getScoreReq(group))
				passCount++;
			else
				failCount++;
		}
		// update request state
		if (passCount >= criteria.getPassReq(group))
			auditState = 1;
		else if (failCount >= criteria.getFailReq(group))
			auditState = -1;
		else {
			auditState = 0;
		}
		// update average score
		avgScore = avgScore / (passCount + failCount);
	}

	public void updateDetail(String bName, String bDesc, String bWorld, double x, double y, double z) {
		this.buildName = bName;
		this.buildDescription = bDesc;
		this.buildWorld = bWorld;
		this.buildX = x;
		this.buildY = y;
		this.buildZ = z;
		this.modifyTime = new Date();
	}

	public void updateFinishTime() {
		finishTime = new Date();
	}

	// -------- others
	public boolean isFinished() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(finishTime);
		return cal.get(Calendar.YEAR) > 2000;
	}

	public long remainingCooltime(double day) {
		Date now = new Date();
		Date then = finishTime;
		long reqTime = (long) (day * 1000 * 60 * 60 * 24); // day
		return reqTime - (now.getTime() - then.getTime());
	}

	public AuditRecord checkIfScored(String aname) {
		for (AuditRecord r : auditRecords) {
			if (r.getAuditor().equals(aname))
				return r;
		}
		return null;
	}

	public Comment checkIfCommented(String pname) {
		for (Comment c : comments) {
			if (c.getCommeter().equals(pname))
				return c;
		}
		return null;
	}

	public void toOutput(Player p) {
		// Detail
		SimpleDateFormat df = new SimpleDateFormat(LanguageManager.getInstance().getLang("time_format"));
		DecimalFormat df2 = new DecimalFormat(".##");
		String str = String.format(lang.getLang("checkinfo_Title_Building_Audition"), name,
				AuditCriteria.getInstance().getGroupName(group), avgScore, df.format(modifyTime));
		if (!isFinished() && p.getName().equals(this.name)) { // 只能编辑自己的未完结的申请
			TextComponent m0 = new TextComponent(str + "§7§n§l[§b§n编辑信息§7§n§l]");
			m0.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sq 新的名称 新的介绍"));
			m0.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
					new ComponentBuilder("§f点击以编辑此次申请的信息\n名称、介绍、作品地点都会更新\n§c编辑冷却时间为24小时").create()));
			p.spigot().sendMessage(m0);
		} else {
			p.sendMessage(str);
		}
		// State
		if (auditState == 1) {
			if (isFinished()) { // 通过
				p.sendMessage(lang.getLang("checkinfo_apply_success"));
			} else { // 满足通过标准 等待审核人员完结
				if (AutoAudit.isAuditorLeader(p)) {
					TextComponent message2 = new TextComponent(
							lang.getLang("checkinfo_apply_waiting") + lang.getLang("checkinfo_apply_success_unsolve"));
					message2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqsolve " + name));
					message2.setHoverEvent(
							new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§d点击以完结此申请").create()));
					p.spigot().sendMessage(message2);
				} else if (p.getName().equals(name)) { // 申请者
					TextComponent m4 = new TextComponent("§a§n恭喜，你的申请已达通过标准. 接下来请于论坛发表对应帖子以完成本次申请.");
					m4.setClickEvent(
							new ClickEvent(ClickEvent.Action.OPEN_URL, "http://bbs.mycraft.cc/forum-64-1.html"));
					m4.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new ComponentBuilder("§f请在论坛审核板块发表与本次申请对应的帖子\n§f以完成本次申请流程\n"
									+ "§e除了作品的简要介绍\n§e还需要在申请帖内附上作品的多张相关图片\n" + "§e审核人员会在检查申请帖后通过本次申请\n§f§n点击以进入论坛审核板块")
											.create()));
					p.spigot().sendMessage(m4);
				} else { // 普通玩家视角
					p.sendMessage(lang.getLang("checkinfo_apply_waiting"));
				}
			}
		} else if (auditState == -1) {
			if (isFinished()) { // 失败
				p.sendMessage(lang.getLang("checkinfo_apply_fail"));
			} else { // 满足失败标准 等待审核人员完结
				if (AutoAudit.isAuditorLeader(p)) {
					TextComponent message2 = new TextComponent(
							lang.getLang("checkinfo_apply_waiting") + lang.getLang("checkinfo_apply_fail_unsolve"));
					message2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqsolve " + name));
					message2.setHoverEvent(
							new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§d点击以完结此申请").create()));
					p.spigot().sendMessage(message2);
				} else { // 普通玩家
					p.sendMessage(lang.getLang("checkinfo_apply_waiting"));
				}
			}
		} else {
			p.sendMessage(lang.getLang("checkinfo_apply_waiting"));
		}
		// Link
		if (auditState == 1) { // 通过了的申请才出现链接
			TextComponent l1 = new TextComponent(lang.getLang("checkinfo_Title_Link"));
			if (p.getName().equals(name)) { // 申请者
				TextComponent l2;
				if (link != null)
					l2 = new TextComponent("§e§n" + link);
				else
					l2 = new TextComponent(lang.getLang("link_add"));
				l2.setClickEvent(
						new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqlink " + name + "-" + id + " 你的论坛申请帖地址"));
				l2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						new ComponentBuilder("§f在论坛发布对应申请帖后\n§f请在此处添加对应链接\n§f供审核人员查看").create()));
				l1.addExtra(l2);
			} else { // 普通玩家
				if (link != null)
					l1.addExtra("§f§n" + link);
				else
					l1.addExtra(lang.getLang("link_null"));
			}
			p.spigot().sendMessage(l1);
		}
		// Location
		String str2 = String.format(LanguageManager.getInstance().getLang("checkinfo_Request_Detail"), buildName,
				buildDescription);
		String w = AutoAudit.getWorldAlias(buildWorld);
		String strw = (AutoAudit.getWorldAlias(buildWorld) + " " + df2.format(buildX) + "," + df2.format(buildY) + ","
				+ df2.format(buildZ));
		TextComponent m1 = new TextComponent(str2);
		TextComponent m2;
		;
		if (w.equals("§7[§f不在本界§7]§f")) {
			m2 = new TextComponent(strw);
			m2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
					new ComponentBuilder("不在本界，无法传送至作品地点\n请先前往另一个界后再查看本记录").create()));
		} else {
			m2 = new TextComponent(strw + " §7§n§l[§e§n传送§7§n§l]");
			m2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqtp " + name + " " + id));
			m2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e点击前往该作品地点").create()));
		}
		m1.addExtra(m2);
		p.spigot().sendMessage(m1);
		// Audits
		String stra = lang.getLang("checkinfo_Title_Audit_Records");
		if (AutoAudit.isAuditor(p) && !isFinished()) {
			TextComponent maudit1, maudit2;
			if (checkIfScored(p.getName()) != null) { // 已评分过
				stra += " ";
				maudit1 = new TextComponent(stra);
				maudit2 = new TextComponent("§7§n§l[§b§n修改评分§7§n§l]");
				maudit2.setClickEvent(
						new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqscore " + name + " 分数[0~15] 你的点评"));
				maudit2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						new ComponentBuilder("点击对此作品进行评分\n将覆盖已有的评分和点评").create()));
				TextComponent maudit3 = new TextComponent("§7§n§l[§a§n补充点评§7§n§l]");
				maudit3.setClickEvent(
						new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqremarkadd " + name + " 你的点评"));
				maudit3.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						new ComponentBuilder("点击补充你对此作品的评论\n将直接附加在你的现有点评之后").create()));
				maudit1.addExtra(maudit2);
				maudit1.addExtra(" ");
				maudit1.addExtra(maudit3);
			} else { // 还没评分过
				stra += " §7你还没有进行评分 ";
				maudit1 = new TextComponent(stra);
				maudit2 = new TextComponent("§7§n§l[§a§n进行评分§7§n§l]");
				maudit2.setClickEvent(
						new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqscore " + name + " 分数[0~15] 你的点评"));
				maudit2.setHoverEvent(
						new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击对此作品进行评分").create()));
				maudit1.addExtra(maudit2);
			}
			p.spigot().sendMessage(maudit1);
		} else {
			p.sendMessage(stra);
		}
		// 所有申请
		if (auditRecords != null && auditRecords.size() != 0) {
			String str3 = "";
			for (AuditRecord rec : auditRecords) {
				str3 += rec.toString();
			}
			p.sendMessage(str3);
		}
		// Comments
		String strcomment = lang.getLang("checkinfo_Title_Comments");
		TextComponent mcomment1, mcomment2, mcomment3;
		if (checkIfCommented(p.getName()) != null) { // 已评论过
			strcomment += " ";
			mcomment1 = new TextComponent(strcomment);
			mcomment2 = new TextComponent("§7§n§l[§b§n修改评论§7§n§l]");
			mcomment2.setClickEvent(
					new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqcomment " + name + "-" + id + " 你的评论"));
			mcomment2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
					new ComponentBuilder("点击修改你对此作品的评论\n将覆盖现有的评论").create()));
			mcomment3 = new TextComponent("§7§n§l[§a§n补充评论§7§n§l]");
			mcomment3.setClickEvent(
					new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqcommentadd " + name + "-" + id + " 你的评论"));
			mcomment3.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
					new ComponentBuilder("点击补充你对此作品的评论\n将直接附加在现有评论之后").create()));
			mcomment1.addExtra(mcomment2);
			mcomment1.addExtra("§r ");
			mcomment1.addExtra(mcomment3);
		} else { // 还没评论过
			strcomment += " §7你还未发表过评论 ";
			mcomment1 = new TextComponent(strcomment);
			mcomment2 = new TextComponent("§7§n§l[§a§n发表评论§7§n§l]");
			mcomment2.setClickEvent(
					new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqcomment " + name + "-" + id + " 你的评论"));
			mcomment2.setHoverEvent(
					new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击评论此作品").create()));
			mcomment1.addExtra(mcomment2);
		}
		p.spigot().sendMessage(mcomment1);
		// 所有评论
		if (comments != null && comments.size() != 0) {
			for (Comment c : comments) {
				TextComponent m3 = new TextComponent(c.toString() + " ");
				if (p.hasPermission("autoaudit.admin")) {
					TextComponent m4 = new TextComponent("§7§l[§c§lX§7§l]");
					m4.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
							"/sqdelcomment " + (name + "-" + id) + " " + c.getId()));
					m4.setHoverEvent(
							new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c删除该评论").create()));
					m3.addExtra(m4);
				}
				p.spigot().sendMessage(m3);
			}
		}
	}
}
