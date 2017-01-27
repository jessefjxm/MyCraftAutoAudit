package com.mycraft.autoaudit.data;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.mycraft.autoaudit.utils.LanguageManager;

public class Comment {
	private int id;
	private String commeter;
	private String comment;
	private Date modifyTime;

	public Comment(int id, String name, String desc) {
		this.id = id;
		this.commeter = name;
		updateComment(desc);
	}

	public Comment(int id, String name, String desc, Date date) {
		this.id = id;
		this.commeter = name;
		updateComment(desc);
		modifyTime = date;
	}

	public String getComment() {
		return comment;
	}

	public String getCommeter() {
		return commeter;
	}

	public Date getModifyTime() {
		return modifyTime;
	}

	public int getId() {
		return id;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public void updateComment(String desc) {
		this.comment = desc;
		this.modifyTime = new Date();
	}

	public String toString() {
		SimpleDateFormat df = new SimpleDateFormat(LanguageManager.getInstance().getLang("time_format"));
		String s = String.format(LanguageManager.getInstance().getLang("checkinfo_Comment"), commeter, comment,
				df.format(modifyTime));
		return s;
	}
}
