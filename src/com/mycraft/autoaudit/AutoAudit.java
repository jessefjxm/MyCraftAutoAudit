package com.mycraft.autoaudit;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.jar.Attributes.Name;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.mycraft.autoaudit.data.AuditRequest;
import com.mycraft.autoaudit.utils.AuditCriteria;
import com.mycraft.autoaudit.utils.LanguageManager;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.permission.Permission;

public class AutoAudit extends JavaPlugin implements Listener {
	// -------- variables
	private HashMap<String, ArrayList<AuditRequest>> auditRequests;
	private HashMap<String, Integer> removedCount;
	private static MVWorldManager worldManager;

	// Config vars.
	private YamlConfiguration config;
	private AuditCriteria auditCriteria;
	private LanguageManager lang;
	private double applyCooltime;
	private double modifyCooltime;

	// Bukkit vars.
	private ConsoleCommandSender consoleSender = Bukkit.getServer().getConsoleSender();
	private static final Logger log = Logger.getLogger("Minecraft");

	// Vault vars.
	public static Permission perms = null;

	// DataBase vars.
	private static Connection connection;
	private String host;
	private String database;
	private static String tableName;
	private String username;
	private String password;
	private int port = 3306;

	// -------- Bukkit related
	@Override
	public void onEnable() {
		initCommands();
		loadConfig();
		loadData();
		if (!initVault()) {
			log.severe(String.format(lang.getLang("plugin_name") + lang.getLang("vault_not_found")));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		log.info(lang.getLang("plugin_name") + lang.getLang("vault_initilized"));
		getServer().getPluginManager().registerEvents(this, this);
		loadMultiverseCore();
		// 自动定时同步数据
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override
			public void run() {
				log.info(lang.getLang("plugin_name") + "自动同步数据");
				reload();
			}
		}, 0L, 1200L);
		log.info(lang.getLang("plugin_name") + lang.getLang("plugin_enabled"));
		super.onEnable();
	}

	@Override
	public void onDisable() {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info(lang.getLang("plugin_name") + lang.getLang("plugin_disabled"));
		super.onDisable();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(lang.getLang("plugin_name") + lang.getLang("must_be_player"));
			return false;
		}
		Player player = (Player) sender;
		switch (cmd.getName()) {
		// ------ 玩家指令
		case "sq":
			if (args.length == 0) {
				checkRequest(player, player.getName(), "all", "0");
			} else if (args.length == 2) {
				player.sendMessage(modifyRequest(player, args[0], args[1]));
			} else {
				showHelp(player);
			}
			break;
		case "sqcheck":
			if (args.length == 0) {
				checkRequest(player, player.getName(), "all", "0");
			} else if (args.length == 1) { // 查看他人资料
				checkRequest(player, args[0], "all", "0");
			} else if (args.length == 2) {
				checkRequest(player, args[0], args[1], "0");
			} else if (args.length == 3) {
				checkRequest(player, args[0], args[1], args[2]);
			} else {
				showHelp(player);
			}
			break;
		case "sqcomment":
			if (args.length == 2) {
				player.sendMessage(commentRequest(player, args[0], args[1], false));
			} else {
				showHelp(player);
			}
			break;
		case "sqcommentadd":
			if (args.length == 2) {
				player.sendMessage(commentRequest(player, args[0], args[1], true));
			} else {
				showHelp(player);
			}
			break;
		case "sqhelp":
			if (args.length == 1) {
				showHelp(player, args[0]);
			} else {
				showHelp(player);
			}
			break;
		case "sqlist":
			if (args.length == 0) {
				listRequest(player, "1", "all", "0");
			} else if (args.length == 1) {
				listRequest(player, args[0], "all", "0");
			} else if (args.length == 2) {
				listRequest(player, args[0], args[1], "0");
			} else if (args.length == 3) {
				listRequest(player, args[0], args[1], args[2]);
			} else {
				showHelp(player);
			}
			break;
		case "sqlink":
			if (args.length == 2) {
				player.sendMessage(modifyLink(player, args[0], args[1]));
			} else {
				showHelp(player);
			}
			break;
		// ------ 审核指令
		case "sqscore":
			if (!isAuditor(player)) {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("no_perm"));
				return false;
			}
			if (args.length == 3) {
				scoreRequest(player, args[0], args[1], args[2]);
			} else {
				showHelp(player);
			}
			break;
		case "sqremarkadd":
			if (!isAuditor(player)) {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("no_perm"));
				return false;
			}
			if (args.length == 2) {
				appendScoreRemark(player, args[0], args[1]);
			}
			break;
		case "sqsolve":
			if (!isAuditorLeader(player)) {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("no_perm"));
				return false;
			}
			if (args.length == 1) {
				closeRequest(player, args[0], 0);
			} else if (args.length == 2) {
				closeRequest(player, args[0], args[1].equals("true") ? 1 : -1);
			}
			break;
		// ------ 管理指令
		case "sqstats":
			if (!player.hasPermission("autoaudit.admin")) {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("no_perm"));
				return false;
			}
			player.sendMessage(requestsStatissitc());
			break;
		case "sqreload":
			if (!player.hasPermission("autoaudit.admin")) {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("no_perm"));
				return false;
			}
			player.sendMessage(reload());
			break;
		case "sqdelcomment":
			if (!player.hasPermission("autoaudit.admin")) {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("no_perm"));
				return false;
			}
			if (args.length == 2) {
				removeComment(player, args[0], args[1], 0);
			} else if (args.length == 3) {
				removeComment(player, args[0], args[1], args[2].equals("true") ? 1 : -1);
			}
			break;
		case "sqscorelist":
			if (!player.hasPermission("autoaudit.admin")) {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("no_perm"));
				return false;
			}
			listUnsolveRequest(player);
			break;
		// ------ 隐藏指令
		case "sqtp":
			if (args.length == 2) {
				player.sendMessage(tpBuilding(player, args[0], Integer.parseInt(args[1])));
			}
			break;
		default:
			return false;
		}
		return true;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		org.bukkit.entity.Player player = event.getPlayer();
		if (isAuditor(player)) {
			listUnsolveRequest(player);
		}
	}

	// public method
	public static ResultSet runSQL(String arg) {
		try {
			Statement statement = connection.createStatement();
			return statement.executeQuery(arg);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void updateSQL(String arg) {
		try {
			Statement statement = connection.createStatement();
			statement.executeUpdate(arg);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static int insertSQL(String arg) {
		try {
			PreparedStatement pstmt = connection.prepareStatement(arg, Statement.RETURN_GENERATED_KEYS);
			pstmt.executeUpdate();
			ResultSet keys = pstmt.getGeneratedKeys();
			keys.next();
			return keys.getInt(1);
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	public static String getTableName() {
		return tableName;
	}

	public static String getCurrentSQLTime() {
		java.util.Date dt = new java.util.Date();
		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(dt);
	}

	public static String getWorldAlias(String name) {
		try {
			String w = worldManager.getMVWorld(name).getAlias();
			return w;
		} catch (NullPointerException e) {
			return "§7[§f不在本界§7]§f";
		}
	}

	public static boolean isAuditor(Player p) {
		return perms.playerInGroup(p, "MyCraft_Auditor") || perms.playerInGroup(p, "EW_Auditor");
	}

	public static boolean isAuditorLeader(Player p) {
		return perms.playerInGroup(p, "MyCraft_Auditor_Leader");
	}

	// -------- private methods | initialization
	private void initCommands() {
		this.getCommand("sq").setExecutor(this);
		this.getCommand("sqcomment").setExecutor(this);
		this.getCommand("sqcommentadd").setExecutor(this);
		this.getCommand("sqcheck").setExecutor(this);
		this.getCommand("sqscore").setExecutor(this);
		this.getCommand("sqremarkadd").setExecutor(this);
		this.getCommand("sqstats").setExecutor(this);
		this.getCommand("sqhelp").setExecutor(this);
		this.getCommand("sqtp").setExecutor(this);
		this.getCommand("sqlist").setExecutor(this);
		this.getCommand("sqscorelist").setExecutor(this);
		this.getCommand("sqdelcomment").setExecutor(this);
		this.getCommand("sqreload").setExecutor(this);
		this.getCommand("sqsolve").setExecutor(this);
		this.getCommand("sqlink").setExecutor(this);
	}

	private boolean initVault() {
		if (getServer().getPluginManager().getPlugin("Vault") == null)
			return false;
		RegisteredServiceProvider<Permission> rspp = getServer().getServicesManager().getRegistration(Permission.class);
		if (rspp == null)
			return false;
		perms = rspp.getProvider();
		return perms != null;
	}

	private void loadConfig() {
		try {
			if (!getDataFolder().exists()) {
				getDataFolder().mkdirs();
			}
			File fileConf = new File(getDataFolder(), "config.yml");
			File fileLang = new File(getDataFolder(), "language.yml");
			if (!fileConf.exists()) {
				saveResource("config.yml", false);
			}
			if (!fileLang.exists()) {
				saveResource("language.yml", false);
			}
			config = new YamlConfiguration();
			config.load(fileConf);
			auditCriteria = AuditCriteria.getInstance();
			auditCriteria.loadCriteria(config);

			applyCooltime = config.getDouble("apply_cooltime");
			modifyCooltime = config.getDouble("modify_cooltime");

			YamlConfiguration langConfig = new YamlConfiguration();
			langConfig.load(fileLang);
			lang = LanguageManager.getInstance();
			lang.loadLanguage(langConfig);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadData() {
		if (config.getBoolean("mysql.enable") == true) {
			host = config.getString("mysql.host");
			database = config.getString("mysql.database");
			tableName = config.getString("mysql.tablename");
			username = config.getString("mysql.username");
			password = config.getString("mysql.password");
			port = config.getInt("mysql.port");
			try {
				openConnection();
				Statement statement = connection.createStatement();
				String sqlCreate1 = "CREATE TABLE IF NOT EXISTS " + tableName + "Requests"
						+ "  (`id`         							INTEGER not null primary key auto_increment,"
						+ "   `name`         						VARCHAR(32),"
						+ "   `group`         						VARCHAR(32),"
						+ "   `buildName`     						VARCHAR(64),"
						+ "   `buildDescription`  					VARCHAR(256),"
						+ "   `buildWorld`  						VARCHAR(64),"
						+ "   `buildX`  							DOUBLE,"
						+ "   `buildY`  							DOUBLE,"
						+ "   `buildZ`  							DOUBLE,"
						+ "   `link`  								VARCHAR(64) DEFAULT NULL,"
						+ "   `modifyTime`        				  	TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
						+ "   `finishTime`       				   	DATETIME DEFAULT '1900-01-01 00:00:00');";
				statement.execute(sqlCreate1);
				String sqlCreate2 = "CREATE TABLE IF NOT EXISTS " + tableName + "Audits"
						+ "  (`id`         	   						INTEGER not null primary key auto_increment,"
						+ "   `requestID`        					INTEGER,"
						+ "   `auditor`         					VARCHAR(32),"
						+ "   `player`         						VARCHAR(32),"
						+ "   `score`        		 				FLOAT,"
						+ "   `remark`           					TEXT,"
						+ "   `modifyTime`          				TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
				statement.execute(sqlCreate2);
				String sqlCreate3 = "CREATE TABLE IF NOT EXISTS " + tableName + "Comments"
						+ "  (`id`         	   						INTEGER not null primary key auto_increment,"
						+ "   `requestID`        					INTEGER,"
						+ "   `commeter`         					VARCHAR(32),"
						+ "   `player`         						VARCHAR(32),"
						+ "   `comment`         					TEXT,"
						+ "   `modifyTime`          				TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
				statement.execute(sqlCreate3);
				String sqlCreate4 = "CREATE TABLE IF NOT EXISTS " + tableName + "RemovedCount"
						+ "  (`id`         	   						INTEGER not null primary key auto_increment,"
						+ "   `player`        						VARCHAR(32),"
						+ "   `count`         						INTEGER);";
				statement.execute(sqlCreate4);

				auditRequests = new HashMap<String, ArrayList<AuditRequest>>();
				ResultSet resultSet = runSQL("SELECT * FROM `" + tableName + "Requests" + "` ;");
				ArrayList<AuditRequest> req;
				while (resultSet.next()) {
					String name = resultSet.getString("name");
					if (auditRequests.containsKey(name)) {
						req = auditRequests.get(name);
					} else {
						req = new ArrayList<>();
						auditRequests.put(name, req);
					}
					int id = resultSet.getInt("id");
					String bName = resultSet.getString("buildName");
					String bDesc = resultSet.getString("buildDescription");
					String bWorld = resultSet.getString("buildWorld");
					String group = resultSet.getString("group");
					String link = resultSet.getString("link");
					double x = resultSet.getDouble("buildX");
					double y = resultSet.getDouble("buildY");
					double z = resultSet.getDouble("buildZ");
					Date modifyTime = resultSet.getDate("modifyTime");
					Date finishTime = resultSet.getDate("finishTime");
					req.add(new AuditRequest(id, name, group, bName, bDesc, bWorld, x, y, z, link, modifyTime,
							finishTime));
				}

				ResultSet resultSet2 = runSQL("SELECT * FROM `" + tableName + "Audits" + "` ;");
				while (resultSet2.next()) {
					String name = resultSet2.getString("player");
					if (auditRequests.containsKey(name)) {
						req = auditRequests.get(name);
					} else {
						req = new ArrayList<>();
						auditRequests.put(name, req);
					}
					int id = resultSet2.getInt("id");
					int requestID = resultSet2.getInt("requestID");
					String auditor = resultSet2.getString("auditor");
					String remark = resultSet2.getString("remark");
					float score = resultSet2.getFloat("score");
					Date modifyTime = resultSet2.getDate("modifyTime");
					AuditRequest ar = null;
					for (AuditRequest r : req) {
						if (r.getId() == requestID) {
							ar = r;
							break;
						}
					}
					if (ar == null)
						continue;
					ar.addAuditRecord(id, auditor, score, remark, modifyTime);
				}

				ResultSet resultSet3 = runSQL("SELECT * FROM `" + tableName + "Comments" + "` ;");
				while (resultSet3.next()) {
					String player = resultSet3.getString("player");
					if (auditRequests.containsKey(player)) {
						req = auditRequests.get(player);
					} else {
						continue;
					}
					int id = resultSet3.getInt("id");
					String name = resultSet3.getString("commeter");
					int requestID = resultSet3.getInt("requestID");
					String comment = resultSet3.getString("comment");
					Date modifyTime = resultSet3.getDate("modifyTime");
					AuditRequest ar = null;
					for (AuditRequest r : req) {
						if (r.getId() == requestID) {
							ar = r;
							break;
						}
					}
					if (ar == null)
						continue;
					ar.addComment(id, name, comment, modifyTime);
				}

				ResultSet resultSet4 = runSQL("SELECT * FROM `" + tableName + "RemovedCount" + "` ;");
				removedCount = new HashMap<>();
				while (resultSet4.next()) {
					String player = resultSet4.getString("player");
					int count = resultSet4.getInt("count");
					removedCount.put(player, count);
				}
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private void openConnection() throws SQLException, ClassNotFoundException {
		if (connection != null && !connection.isClosed()) {
			return;
		}
		synchronized (this) {
			if (connection != null && !connection.isClosed()) {
				return;
			}
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection(
					"jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database + "?user=" + this.username
							+ "&password=" + this.password + "&useUnicode=true&characterEncoding=utf-8");
		}
	}

	private void loadMultiverseCore() {
		Plugin plugin = getServer().getPluginManager().getPlugin("Multiverse-Core");
		if (plugin != null && plugin instanceof MultiverseCore) {
			log.warning("[DEBUG] MultiverseCore loaded!");
			worldManager = ((MultiverseCore) plugin).getMVWorldManager();
		}
	}

	// -------- private detail methods | response to commands
	private String modifyRequest(Player p, String name, String desc) {
		ArrayList<AuditRequest> reqList = auditRequests.get(p.getName());
		if (reqList == null) {
			reqList = new ArrayList<AuditRequest>();
			auditRequests.put(p.getName(), reqList);
		}
		String goalGroup;
		if (perms.playerInGroup(p, auditCriteria.getDefaultGroup())) {
			goalGroup = auditCriteria.getGroups().get(0);
		} else {
			goalGroup = getFitGroupName(auditCriteria.getGroups(), perms.getPlayerGroups(p), +1);
			if (goalGroup == null)
				return lang.getLang("plugin_name") + lang.getLang("req_no_appliable_group");
		}
		if (reqList.size() == 0 || reqList.get(reqList.size() - 1).isFinished()) { // new
			if (reqList.size() != 0) {
				long l = reqList.get(reqList.size() - 1).remainingCooltime(applyCooltime);
				if (l > 0) {
					long day = l / (24 * 60 * 60 * 1000);
					long hour = (l / (60 * 60 * 1000) - day * 24);
					long min = ((l / (60 * 1000)) - day * 24 * 60 - hour * 60);
					long s = (l / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);
					String str = day + " 天 " + hour + "时 " + min + "分 " + s + "秒";
					return lang.getLang("plugin_name") + String.format(lang.getLang("req_time_cooldown"), str);
				}
			}
			String query = "Insert into " + tableName + "Requests"
					+ " (`name`,`group`,`buildName`,`buildDescription`,`buildWorld`,`buildX`,`buildY`,`buildZ`) VALUES('"
					+ p.getName() + "', '" + goalGroup + "', '" + name + "', '" + desc + "', '" + p.getWorld().getName()
					+ "', " + p.getLocation().getX() + ", " + p.getLocation().getY() + ", " + p.getLocation().getZ()
					+ "); ";
			int key = insertSQL(query);
			AuditRequest req = new AuditRequest(key, p.getName(), goalGroup, name, desc, p.getWorld().getName(),
					p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
			reqList.add(req);
			return lang.getLang("plugin_name") + lang.getLang("req_create_success");
		} else { // modify request
			Date now = new Date();
			Date then = reqList.get(reqList.size() - 1).getModifyTime();
			long reqTime = (long) (modifyCooltime * 1000 * 60 * 60 * 24); // day
			long l = reqTime - (now.getTime() - then.getTime());
			if (l > 0) {
				long day = l / (24 * 60 * 60 * 1000);
				long hour = (l / (60 * 60 * 1000) - day * 24);
				long min = ((l / (60 * 1000)) - day * 24 * 60 - hour * 60);
				long s = (l / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);
				String str = day + " 天 " + hour + "时 " + min + "分 " + s + "秒";
				return lang.getLang("plugin_name") + String.format(lang.getLang("modify_time_cooldown"), str);
			}
			AuditRequest req = reqList.get(reqList.size() - 1);
			String query = "UPDATE " + tableName + "Requests" + " SET `buildName` = '" + p.getName()
					+ "',  `buildDescription` = '" + desc + "', `buildWorld` = '" + p.getWorld().getName()
					+ "', `buildX` = " + p.getLocation().getX() + ", `buildY` = " + p.getLocation().getY()
					+ ", `buildZ` = " + p.getLocation().getZ() + ", `modifyTime` = '" + getCurrentSQLTime()
					+ "' WHERE `id` = " + req.getId();
			updateSQL(query);
			req.updateDetail(name, desc, p.getWorld().getName(), p.getLocation().getX(), p.getLocation().getY(),
					p.getLocation().getZ());
			return lang.getLang("plugin_name") + lang.getLang("req_modify_success");
		}
	}

	private void checkRequest(Player player, String pname, String group, String ip) {
		ArrayList<AuditRequest> arrayList = auditRequests.get(pname);
		if (arrayList == null || arrayList.size() == 0) {
			if (player.getName().equals(pname)) {
				TextComponent m = new TextComponent(
						lang.getLang("plugin_name") + lang.getLang("req_check_no_result_yourself"));
				m.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sq 你的作品名称 你的作品描述"));
				m.setHoverEvent(
						new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以创建你的建筑审核申请").create()));
				player.spigot().sendMessage(m);
			} else {
				player.sendMessage(
						String.format(lang.getLang("plugin_name") + lang.getLang("req_check_no_result"), pname));
			}
			return;
		}
		int isPass;
		try {
			isPass = Integer.valueOf(ip); // 0 all, 1 pass, -1 fail
		} catch (NumberFormatException e) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
		if (isPass < -1 && isPass > 1) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
		}
		if (player.getName().equals(pname) && arrayList.get(arrayList.size() - 1).isFinished()
				&& arrayList.get(arrayList.size() - 1).remainingCooltime(applyCooltime) < 0) {
			TextComponent m = new TextComponent(
					lang.getLang("plugin_name") + lang.getLang("req_check_can_apply_yourself"));
			m.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sq 你的作品名称 你的作品描述"));
			m.setHoverEvent(
					new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以创建你的建筑审核申请").create()));
			player.spigot().sendMessage(m);
		}
		if (group.equals("all")) {
			if (isPass == 0) {
				for (int i = 0; i < arrayList.size(); i++) {
					player.sendMessage(String.format(lang.getLang("checkinfo_Title"), arrayList.size() - i));
					arrayList.get(i).toOutput(player);
				}
			} else {
				int count = 1;
				for (int i = 0; i < arrayList.size(); i++) {
					if (arrayList.get(i).getAuditState() == isPass) {
						player.sendMessage(String.format(lang.getLang("checkinfo_Title"), count));
						arrayList.get(i).toOutput(player);
						count++;
					}
				}
			}
			player.sendMessage(String.format(lang.getLang("checkinfo_Title"), "∞"));
		} else {
			int count = 1;
			if (isPass == 0) {
				for (int i = 0; i < arrayList.size(); i++) {
					if (arrayList.get(i).getGroup().equals(group)) {
						player.sendMessage(String.format(lang.getLang("checkinfo_Title"), count));
						arrayList.get(i).toOutput(player);
						count++;
					}
				}
			} else {
				for (int i = 0; i < arrayList.size(); i++) {
					if (arrayList.get(i).getGroup().equals(group) && arrayList.get(i).getAuditState() == isPass) {
						player.sendMessage(String.format(lang.getLang("checkinfo_Title"), count));
						arrayList.get(i).toOutput(player);
						count++;
					}
				}
			}
			player.sendMessage(String.format(lang.getLang("checkinfo_Title"), "∞"));
		}
	}

	private String commentRequest(Player p, String arg, String desc, boolean isAppend) {
		String[] s = arg.split("-");
		String name = s[0];
		int id = -1;
		if (s.length == 2) {
			id = Integer.parseInt(s[1]);
		}
		ArrayList<AuditRequest> arrayList = auditRequests.get(name);
		if (arrayList == null || arrayList.size() == 0)
			return lang.getLang("plugin_name") + lang.getLang("req_no_player");
		if (id == -1) { // 默认评论最新的作品
			if (isAppend) {
				arrayList.get(arrayList.size() - 1).appendComment(p.getName(), desc);
				return lang.getLang("plugin_name") + lang.getLang("req_add_comment_success");
			} else {
				arrayList.get(arrayList.size() - 1).modifyComment(p.getName(), desc);
				return lang.getLang("plugin_name") + lang.getLang("req_comment_success");
			}
		}
		for (AuditRequest req : arrayList) {
			if (req.getId() == id) {
				if (isAppend) {
					req.appendComment(p.getName(), desc);
					return lang.getLang("plugin_name") + lang.getLang("req_add_comment_success");
				} else {
					req.modifyComment(p.getName(), desc);
					return lang.getLang("plugin_name") + lang.getLang("req_comment_success");
				}
			}
		}
		return lang.getLang("plugin_name") + lang.getLang("req_no_player");
	}

	private void removeComment(Player p, String arg, String id, int confirm) {
		String s = lang.getLang("plugin_name");
		String str[] = arg.split("-");
		String pname = str[0];
		int reqId = Integer.parseInt(str[1]);
		int commentId = -1;
		try {
			commentId = Integer.valueOf(id);
		} catch (NumberFormatException e) {
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
		if (commentId < 0) {
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
		if (confirm == 0) {
			TextComponent message = new TextComponent(s + lang.getLang("confirm_delete_comment"));
			TextComponent messageyes = new TextComponent(lang.getLang("confirm_yes"));
			messageyes.setClickEvent(
					new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqdelcomment " + arg + " " + id + " true"));
			messageyes.setHoverEvent(
					new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以确定删除此评论").create()));
			TextComponent messageno = new TextComponent(lang.getLang("confirm_no"));
			messageno.setClickEvent(
					new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqdelcomment " + arg + " " + id + " false"));
			messageno.setHoverEvent(
					new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以放弃删除此评论").create()));
			message.addExtra(messageyes);
			message.addExtra(new TextComponent("§f/"));
			message.addExtra(messageno);
			p.spigot().sendMessage(message);
			return;
		} else if (confirm == -1) {
			p.sendMessage(s + lang.getLang("confirm_drop_delete"));
			return;
		} else if (confirm == 1) {
			ArrayList<AuditRequest> a = auditRequests.get(pname);
			AuditRequest r = findRequest(pname, reqId);
			if (r == null) {
				p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
				return;
			}
			r.removeComment(commentId);
			String query = "DELETE FROM " + tableName + "Comments" + " WHERE `id` = " + commentId;
			updateSQL(query);
			if (removedCount.get(pname) == null) {
				removedCount.put(pname, 1);
				String query2 = "Insert into " + tableName + "RemovedCount" + " (`player`,`count`) VALUES('" + pname
						+ "', " + 1 + "); ";
				updateSQL(query2);
			} else {
				int count = removedCount.get(pname);
				removedCount.put(pname, count + 1);
				String query2 = "UPDATE " + tableName + "RemovedCount" + " SET `count` = " + (count + 1)
						+ " WHERE `player` = '" + pname + "'";
				updateSQL(query2);
			}
			p.sendMessage(s + lang.getLang("remove_comment_success"));
		}
	}

	private void scoreRequest(Player auditor, String pname, String score, String desc) {
		double sc;
		try {
			sc = Double.valueOf(score);
		} catch (NumberFormatException e) {
			auditor.sendMessage(lang.getLang("plugin_name") + lang.getLang("req_score_illegal"));
			return;
		}
		if (sc < 0 || sc > 15) {
			auditor.sendMessage(lang.getLang("plugin_name") + lang.getLang("req_score_illegal"));
			return;
		}
		String s = lang.getLang("plugin_name");
		ArrayList<AuditRequest> arrayList = auditRequests.get(pname);
		if (arrayList == null || arrayList.size() == 0) { // 找不到被评分的玩家
			s += lang.getLang("req_no_player");
			auditor.sendMessage(s);
			return;
		}
		AuditRequest req = arrayList.get(arrayList.size() - 1);
		if (req.isFinished()) {
			s += lang.getLang("req_closed");
			auditor.sendMessage(s);
			return;
		}
		req.modifyAuditRecord(auditor.getName(), sc, desc);
		s += lang.getLang("req_score_success");
		auditor.sendMessage(s);
		if (req.getAuditState() != 0) {
			TextComponent message;
			if (req.getAuditState() == 1) {
				message = new TextComponent(
						lang.getLang("plugin_name") + lang.getLang("checkinfo_apply_success_unsolve"));
			} else {
				message = new TextComponent(lang.getLang("plugin_name") + lang.getLang("checkinfo_apply_fail_unsolve"));
			}
			message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqsolve " + pname));
			message.setHoverEvent(
					new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以完结此申请").create()));
			auditor.spigot().sendMessage(message);
		}
	}

	private void appendScoreRemark(Player auditor, String pname, String desc) {
		String s = lang.getLang("plugin_name");
		ArrayList<AuditRequest> arrayList = auditRequests.get(pname);
		if (arrayList == null || arrayList.size() == 0) { // 找不到被评分的玩家
			s += lang.getLang("req_no_player");
			auditor.sendMessage(s);
			return;
		}
		AuditRequest req = arrayList.get(arrayList.size() - 1);
		if (req.isFinished()) { // 不能评价已关闭申请
			s += lang.getLang("req_closed");
			auditor.sendMessage(s);
			return;
		}
		req.appendAuditRemark(auditor.getName(), desc);
		s += lang.getLang("req_add_remark_success");
		auditor.sendMessage(s);
	}

	private void listUnsolveRequest(Player player) {
		ArrayList<String> unscored = new ArrayList<>();
		ArrayList<String> scoredUnsolved = new ArrayList<>();
		ArrayList<String> unsolved = new ArrayList<>();
		for (Entry<String, ArrayList<AuditRequest>> r : auditRequests.entrySet()) {
			ArrayList<AuditRequest> arrayList = r.getValue();
			for (AuditRequest req : arrayList) {
				if (req.getAuditState() == 0) { // 审核中
					if (req.checkIfScored(player.getName()) == null) // 还没评分
						unscored.add(req.getName());
					else // 已评分
						scoredUnsolved.add(req.getName());
				}
				if (req.getAuditState() != 0 && !req.isFinished()) { // 待完结中
					unsolved.add(req.getName());
				}
			}
		}
		if (unscored.isEmpty() && scoredUnsolved.isEmpty() && unsolved.isEmpty()) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("empty_unscored_list"));
			return;
		}
		if (!unscored.isEmpty()) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("notice_have_unscored"));
			for (int i = 0; i < unscored.size(); i++) {
				TextComponent message = new TextComponent("§7§l[§e" + (i + 1) + "§7§l] §f§n" + unscored.get(i));
				message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqcheck " + unscored.get(i)));
				message.setHoverEvent(
						new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击查看该玩家的申请记录").create()));
				player.spigot().sendMessage(message);
			}
		}
		if (!unsolved.isEmpty()) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("notice_have_unsolved"));
			for (int i = 0; i < unsolved.size(); i++) {
				TextComponent message = new TextComponent("§7§l[§e" + (i + 1) + "§7§l] §f" + unsolved.get(i));
				message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqcheck " + unsolved.get(i)));
				message.setHoverEvent(
						new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击查看该玩家的申请记录").create()));
				player.spigot().sendMessage(message);
			}
		}
		if (!scoredUnsolved.isEmpty()) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("notice_have_scoredUnsolved"));
			for (int i = 0; i < scoredUnsolved.size(); i++) {
				TextComponent message = new TextComponent("§7§l[§e" + (i + 1) + "§7§l] §f§n" + scoredUnsolved.get(i));
				message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqcheck " + scoredUnsolved.get(i)));
				message.setHoverEvent(
						new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击查看该玩家的申请记录").create()));
				player.spigot().sendMessage(message);
			}
		}
	}

	private void listRequest(Player player, String p, String group, String ip) {
		int page, isPass;
		try {
			page = Integer.valueOf(p);
			isPass = Integer.valueOf(ip); // 0 all, 1 pass, -1 fail
		} catch (NumberFormatException e) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
		if (page < 0 || isPass < -1 || isPass > 1) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
		ArrayList<String> requests = new ArrayList<>();
		for (Entry<String, ArrayList<AuditRequest>> r : auditRequests.entrySet()) {
			ArrayList<AuditRequest> arrayList = r.getValue();
			if (group.equals("all")) { // 所有用户组
				if (isPass == 0) { // 所有状态
					if (arrayList.size() > 0)
						requests.add(arrayList.get(0).getName());
				} else { // 指定状态
					for (AuditRequest req : arrayList) {
						if (req.getAuditState() == isPass) {
							requests.add(req.getName());
							break;
						}
					}
				}
			} else { // 指定用户组
				if (isPass == 0) {
					for (AuditRequest req : arrayList) {
						if (req.getGroup().equals(group)) {
							requests.add(req.getName());
							break;
						}
					}
				} else {
					for (AuditRequest req : arrayList) {
						if (req.getGroup().equals(group) && req.getAuditState() == isPass) {
							requests.add(req.getName());
							break;
						}
					}
				}
			}
		}
		if (requests.isEmpty()) {
			player.sendMessage(lang.getLang("plugin_name") + lang.getLang("empty_list"));
			return;
		}
		if (group.equals("all")) {
			switch (isPass) {
			case 1:
				player.sendMessage(String.format(lang.getLang("checkinfo_Title_group_state"), 1, "所有", "已通过"));
				break;
			case -1:
				player.sendMessage(String.format(lang.getLang("checkinfo_Title_group_state"), 1, "所有", "未通过"));
				break;
			default:
				player.sendMessage(String.format(lang.getLang("checkinfo_Title_group"), 1, "所有"));
				break;
			}
		} else {
			String groupName = auditCriteria.getGroupName(group);
			switch (isPass) {
			case 1:
				player.sendMessage(String.format(lang.getLang("checkinfo_Title_group_state"), 1, groupName, "已通过"));
				break;
			case -1:
				player.sendMessage(String.format(lang.getLang("checkinfo_Title_group_state"), 1, groupName, "未通过"));
				break;
			default:
				player.sendMessage(String.format(lang.getLang("checkinfo_Title_group"), 1, groupName));
				break;
			}
		}
		for (int i = (page - 1) * 10; i < requests.size() && i < page * 10; i++) {
			String str = "/sqcheck " + requests.get(i) + " " + group + " " + isPass;
			TextComponent message = new TextComponent("§7§l[§e" + (i + 1) + "§7§l] §f§n" + requests.get(i));
			message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, str));
			message.setHoverEvent(
					new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击查看该玩家的申请记录").create()));
			player.spigot().sendMessage(message);
		}
		int maxPage = requests.size();
		maxPage = maxPage % 10 == 0 ? maxPage / 10 : maxPage / 10 + 1;
		TextComponent m, m1, m2, m3, mm;
		m = new TextComponent("§8§l[§7§l翻页§8§l]§7§m =================== ");
		mm = new TextComponent("§7§m =================== ");
		// 上一页
		if (page == 1) {
			m1 = new TextComponent("§8§l[§7上一页§8§l]");
			m1.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7已经是第一页").create()));
		} else {
			m1 = new TextComponent("§8§l§n[§e§n上一页§8§l§n]");
			m1.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
					"/sqlist " + (page - 1) + " " + group + " " + isPass));
			m1.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以查看上一页记录").create()));
		}
		// 跳页
		m2 = new TextComponent("§8§l§n[§f§n" + page + "§e§n/" + maxPage + "§8§l§n]");
		String str;
		if (group.equals("all") && isPass == 0) {
			str = "/sqlist 要跳转的页数[1-" + maxPage + "]";
		} else if (isPass == 0) {
			str = "/sqlist 要跳转的页数[1-" + maxPage + "] " + group;
		} else {
			str = "/sqlist 要跳转的页数[1-" + maxPage + "] " + group + " " + isPass;
		}
		m2.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, str));
		m2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以跳转到指定页数").create()));
		// 下一页
		if (page == maxPage) {
			m3 = new TextComponent("§8§l[§7下一页§8§l]");
			m3.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7已经是最后一页").create()));
		} else {
			m3 = new TextComponent("§8§l§n[§e§n下一页§n§8§l§n]");
			m3.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
					"/sqlist " + (page + 1) + " " + group + " " + isPass));
			m3.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以查看下一页记录").create()));

		}
		m.addExtra(m1);
		m.addExtra(" ");
		m.addExtra(m2);
		m.addExtra(" ");
		m.addExtra(m3);
		m.addExtra(mm);
		player.spigot().sendMessage(m);
	}

	private void closeRequest(Player p, String pname, int confirm) {
		String s = lang.getLang("plugin_name");
		if (confirm == 0) {
			TextComponent message = new TextComponent(s + lang.getLang("confirm_close"));
			TextComponent messageyes = new TextComponent(lang.getLang("confirm_yes"));
			messageyes.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqsolve " + pname + " true"));
			messageyes.setHoverEvent(
					new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以确定完结此申请").create()));
			TextComponent messageno = new TextComponent(lang.getLang("confirm_no"));
			messageno.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqsolve " + pname + " false"));
			messageno.setHoverEvent(
					new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以放弃完结此申请").create()));
			message.addExtra(messageyes);
			message.addExtra(new TextComponent("§f/"));
			message.addExtra(messageno);
			p.spigot().sendMessage(message);
			return;
		} else if (confirm == -1) {
			p.sendMessage(s + lang.getLang("confirm_drop_close"));
			return;
		} else if (confirm == 1) {
			ArrayList<AuditRequest> a = auditRequests.get(pname);
			AuditRequest r = a.get(a.size() - 1);
			String pgroup = r.getGroup();
			if (r.getAuditState() == 1) {
				s += String.format(lang.getLang("req_pass"), pname);
				// 获取当前建筑类用户组
				OfflinePlayer op = getServer().getOfflinePlayer(r.getName());
				String curGroup = getFitGroupName(auditCriteria.getGroups(),
						perms.getPlayerGroups(r.getBuildWorld(), op), 0);
				;
				if (curGroup == null && perms.playerInGroup(r.getBuildWorld(), op, auditCriteria.getDefaultGroup())) {
					curGroup = auditCriteria.getDefaultGroup();
				}
				Bukkit.dispatchCommand(consoleSender,
						"sync console Su pex user " + pname + " group remove " + curGroup);
				Bukkit.dispatchCommand(consoleSender, "sync console Su pex user " + pname + " group add " + pgroup);
				Bukkit.dispatchCommand(consoleSender,
						"sync console Cr pex user " + pname + " group remove " + curGroup);
				Bukkit.dispatchCommand(consoleSender, "sync console Cr pex user " + pname + " group add " + pgroup);
			} else {
				s += String.format(lang.getLang("req_fail"), pname);
			}
			String query = "UPDATE " + tableName + "Requests" + " SET `finishTime` = '" + getCurrentSQLTime()
					+ "' WHERE `id` = " + r.getId();
			r.updateFinishTime();
			updateSQL(query);
			p.sendMessage(s);
			getServer().dispatchCommand(getServer().getConsoleSender(), "broadcast " + s);
		}
	}

	private String getFitGroupName(List<String> list, String[] pgroup, int modifier) {
		for (int i = 0; i < list.size(); i++) {
			if (Arrays.asList(pgroup).contains(list.get(i)) && i != list.size() - modifier) {
				return list.get(i + modifier);
			}
		}
		return null;
	}

	private String requestsStatissitc() {
		int reqSum = 0, passSum = 0, failSum = 0;
		double avgScore = 0;
		for (Entry<String, ArrayList<AuditRequest>> r : auditRequests.entrySet()) {
			ArrayList<AuditRequest> arrayList = r.getValue();
			reqSum += arrayList.size();
			for (AuditRequest req : arrayList) {
				if (req.getAuditState() == 1) {
					passSum++;
				} else if (req.getAuditState() == -1) {
					failSum++;
				}
				avgScore += req.getAvgScore();
			}
		}
		avgScore /= reqSum;
		DecimalFormat df2 = new DecimalFormat(".##");
		String s = lang.getLang("Total_applied_players") + auditRequests.size() + "\n"
				+ lang.getLang("Total_Audit_Requests") + reqSum + "\n" + lang.getLang("Total_Passed_Requests") + passSum
				+ "/" + reqSum + "\n" + lang.getLang("Total_Failed_Requests") + failSum + "/" + reqSum + "\n"
				+ lang.getLang("Total_Avg_Score") + df2.format(avgScore) + "/15" + "\n";
		return s;
	}

	private void showHelp(Player p) {
		p.sendMessage(lang.getLang("plugin_name") + lang.getLang("help"));
		// 玩家指令
		p.sendMessage(lang.getLang("help_player"));
		String str = "§7§l[§71§7§l] §a/sq - §f" + lang.getLang("help_sq");
		TextComponent message = new TextComponent(str);
		message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sq"));
		message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
		p.spigot().sendMessage(message);
		str = "§7§l[§72§7§l] §a/sq 作品名称 作品描述 - §f" + lang.getLang("help_sq_name_desc");
		message = new TextComponent(str);
		message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sq 作品名称 作品描述"));
		message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
		p.spigot().sendMessage(message);
		// str = "§7§l[§73§7§l] §a/sqcomment 玩家名称 你的评论 - §f" +
		// lang.getLang("help_sqcomment");
		// message = new TextComponent(str);
		// message.setClickEvent(new
		// ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqcomment 玩家名称
		// 你的评论"));
		// message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new
		// ComponentBuilder("点击使用指令").create()));
		// p.spigot().sendMessage(message);
		str = "§7§l[§74§7§l] §a/sqcheck - §f" + lang.getLang("help_sq");
		message = new TextComponent(str);
		message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqcheck"));
		message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
		p.spigot().sendMessage(message);
		str = "§7§l[§75§7§l] §a/sqcheck 玩家名称 - §f" + lang.getLang("help_sqcheck_name");
		message = new TextComponent(str);
		message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqcheck 玩家名称"));
		message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
		p.spigot().sendMessage(message);
		str = "§7§l[§76§7§l] §a/sqhelp - §f" + lang.getLang("help_sqhelp");
		message = new TextComponent(str);
		message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqhelp"));
		message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
		p.spigot().sendMessage(message);
		// str = "§7§l[§77§7§l] §a/sqlist [页数] [职位] [申请状态] - §f" +
		// lang.getLang("help_sqlist");
		// message = new TextComponent(str);
		// message.setClickEvent(new
		// ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqlist 页数 职位 申请状态"));
		// message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new
		// ComponentBuilder("点击使用指令").create()));
		// p.spigot().sendMessage(message);
		// 审核指令
		if (AutoAudit.isAuditor(p)) {
			p.sendMessage(lang.getLang("help_auditor"));
			str = "§7§l[§71§7§l] §a/sqscore 玩家名称 分数[0~15] 你的点评  - §f" + lang.getLang("help_sqscore");
			message = new TextComponent(str);
			message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqscore 玩家名称 分数[0~15] 你的点评"));
			message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
			p.spigot().sendMessage(message);
		}
		// 管理指令
		if (p.hasPermission("autoaudit.admin")) {
			p.sendMessage(lang.getLang("help_admin"));
			str = "§7§l[§71§7§l] §a/sqscorelist - §f" + lang.getLang("help_sqscorelist");
			message = new TextComponent(str);
			message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqscorelist"));
			message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
			p.spigot().sendMessage(message);
			str = "§7§l[§72§7§l] §a/sqstats - §f" + lang.getLang("help_sqstats");
			message = new TextComponent(str);
			message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqstats"));
			message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
			p.spigot().sendMessage(message);
			str = "§7§l[§73§7§l] §a/sqreload - §f" + lang.getLang("help_sqreload");
			message = new TextComponent(str);
			message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sqreload"));
			message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
			p.spigot().sendMessage(message);
		}
	}

	private void showHelp(Player p, String arg) {
		switch (arg) {
		case "sqcheck":
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("help"));
			p.sendMessage(lang.getLang("help_player"));

			String str = "§7§l[§71§7§l] §a/sqcheck 玩家名称 - §f" + lang.getLang("help_sqcheck_name");
			TextComponent message = new TextComponent(str);
			message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sqcheck 玩家名称"));
			message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击使用指令").create()));
			p.spigot().sendMessage(message);
			break;
		default:
			break;
		}
	}

	private String modifyLink(Player p, String arg, String link) {
		String str[] = arg.split("-");
		String pname = str[0];
		int reqId;
		try {
			reqId = Integer.parseInt(str[1]);
		} catch (NumberFormatException e) {
			return lang.getLang("plugin_name") + lang.getLang("format_error");
		}
		AuditRequest r = findRequest(pname, reqId);
		if (r == null) {
			return lang.getLang("plugin_name") + lang.getLang("format_error");
		}

		try {
			URL u = new URL(link); // this would check for the protocol
			u.toURI();
			r.modifyLink(link);
			return lang.getLang("plugin_name") + lang.getLang("link_added");
		} catch (MalformedURLException | URISyntaxException e) {
			return lang.getLang("plugin_name") + lang.getLang("link_format_error");
		}
	}

	private String tpBuilding(Player p, String name, int reqID) {
		ArrayList<AuditRequest> a = auditRequests.get(name);
		if (a != null && a.size() > 0) {
			for (AuditRequest r : a) {
				if (r.getId() == reqID) {
					p.teleport(new Location(Bukkit.getWorld(r.getBuildWorld()), r.getBuildX(), r.getBuildY(),
							r.getBuildZ()));
					return lang.getLang("plugin_name") + lang.getLang("tp_success");
				}
			}
		}
		return lang.getLang("plugin_name") + lang.getLang("format_error");
	}

	private String reload() {
		loadConfig();
		loadData();
		return lang.getLang("reload_complete");
	}

	private AuditRequest findRequest(String name, int reqId) {
		ArrayList<AuditRequest> a = auditRequests.get(name);
		AuditRequest r = null;
		for (AuditRequest ar : a) {
			if (ar.getId() == reqId) {
				r = ar;
				break;
			}
		}
		return r;
	}
}