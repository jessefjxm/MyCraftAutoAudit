package com.mycraft.autoaudit.utils;

import java.util.HashMap;
import java.util.List;

import org.bukkit.configuration.Configuration;

public class AuditCriteria {
	private static AuditCriteria auditCriteria;

	private String defaultGroup;
	private List<String> groups;
	private HashMap<String, Criteria> criteria;

	public static AuditCriteria getInstance() {
		if (auditCriteria == null) {
			auditCriteria = new AuditCriteria();
		}
		return auditCriteria;
	}

	public void loadCriteria(Configuration config) {
		defaultGroup = config.getString("default_group");
		groups = config.getStringList("auditable_groups");
		criteria = new HashMap<>();
		int p, f;
		double r;
		String n;
		for (String s : groups) {
			n = config.getString("audit_criteria." + s + ".name");
			p = config.getInt("audit_criteria." + s + ".pass");
			f = config.getInt("audit_criteria." + s + ".fail");
			r = config.getInt("audit_criteria." + s + ".req_score");
			criteria.put(s, new Criteria(n, p, f, r));
		}
	}

	public String getDefaultGroup() {
		return defaultGroup;
	}

	public List<String> getGroups() {
		return groups;
	}

	public int getPassReq(String group) {
		return criteria.get(group).pass;
	}

	public int getFailReq(String group) {
		return criteria.get(group).fail;
	}

	public double getScoreReq(String group) {
		return criteria.get(group).reqScore;
	}

	public String getGroupName(String group) {
		return criteria.get(group).name;
	}

	private class Criteria {
		public String name;
		public int pass;
		public int fail;
		public double reqScore;

		public Criteria(String n, int p, int f, double r) {
			name = n;
			pass = p;
			fail = f;
			reqScore = r;
		}
	}
}
