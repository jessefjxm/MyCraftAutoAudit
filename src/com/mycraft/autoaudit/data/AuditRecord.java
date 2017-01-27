package com.mycraft.autoaudit.data;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.mycraft.autoaudit.utils.LanguageManager;

public class AuditRecord {
	private int id;
	private String auditor;
	private String player;
	private double score;
	private String remark;
	private Date modifyTime;

	public AuditRecord(int id, String a, String p, double s, String r) {
		this.id = id;
		this.auditor = a;
		this.player = p;
		update(s, r);
		this.modifyTime = new Date();
	}

	public AuditRecord(int id, String a, String p, double s, String r, Date date) {
		this.id = id;
		this.auditor = a;
		this.player = p;
		update(s, r);
		this.modifyTime = date;
	}

	public void update(double s, String r) {
		score = s;
		remark = r;
		modifyTime = new Date();
	}

	public int getId() {
		return id;
	}

	public String getAuditor() {
		return auditor;
	}

	public String getPlayer() {
		return player;
	}

	public double getScore() {
		return score;
	}

	public String getRemark() {
		return remark;
	}

	public Date getModifyTime() {
		return modifyTime;
	}

	public String toString() {
		SimpleDateFormat df = new SimpleDateFormat(LanguageManager.getInstance().getLang("time_format"));
		String s = String.format(LanguageManager.getInstance().getLang("checkinfo_Audit_Record") + "\n", auditor, score,
				remark, df.format(modifyTime));
		return s;
	}
}
