package dbconnect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.vsy.interfaces.tictactoe.GameStatus;
import main.Main;
import objects.GameServer;

public class DBConnection {
	private static Connection connection;
	private Statement statement;

	public DBConnection(){
		try{
			Class.forName("com.mysql.cj.jdbc.Driver");
			if(connection == null || connection.isClosed())
				connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/vsygame?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false", "vsyuser", "vsyPasswort18!");
			this.statement = connection.createStatement();
			ResultSet rs = this.statement.executeQuery("SELECT version()");
			if (rs.next()) {
		        System.out.println("Test DB connection: Version ==> " + rs.getString(1));
			}
		} catch (SQLException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	
	public String execute(String query){
		try {
			ResultSet rs = this.statement.executeQuery(query);
			String ret = "";
			while (rs.next()){
				ret += rs.getString(1);
				ret += " " + rs.getString(2);
				ret += " " + rs.getString(3);
				ret += " " + rs.getString(4);
				ret += "\n";
			}
			return ret;
		} catch (SQLException e) {
			return e.getMessage();
		}
	}
	
	public void loginUser(String username) throws Exception{
		try {
			String query = "INSERT INTO `player`(`name`, `loggedin`) VALUES ('" + username + "', 1) on DUPLICATE key update loggedin=1";
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim login.\n" +e.getMessage());
		}
	}
	
	public void logoutUser(String username) throws Exception {
		try {
			String query = "UPDATE `player` SET `loggedin`=0 WHERE name = '" + username + "'";
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim logout.\n" +e.getMessage());
		}
	}
	
	public boolean isUserLoggedIn(String username) throws Exception {
		try {
			String query = "SELECT `loggedin` FROM player WHERE name = '" + username + "'";
			ResultSet rs = statement.executeQuery(query);
			if(rs.next())
				return rs.getBoolean(1);
			return false;
		} catch (SQLException e) {
			throw new Exception("Fehler bei der Abfrage, ob der User eingeloggt ist.\n" +e.getMessage());
		}
	}
	
	public void logoutAllUsers() throws Exception {
		try {
			String query = "UPDATE `player` SET `loggedin`=0";
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim logout aller User.\n" +e.getMessage());
		}
	}
	
	public Integer getGameIdForUser(String username) throws Exception {
		try {
			String query = "SELECT game.id FROM game " 
					+ "INNER JOIN player ON game.player1 = player.id OR game.player2 = player.id "
					+ "WHERE player.name = '" + username + "' AND game.state IN (" + GameStatus.Running.getNummer() + ", " + GameStatus.Waiting.getNummer() + ")";
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()) {
				return rs.getInt(1);
			}
			return null;
		} catch (SQLException e) {
			throw new Exception("Fehler beim suchen der GameId.\n" +e.getMessage());
		}
	}
	
	public void insertNewGame(GameServer game, String player1) throws Exception{
		try {
			//Das holen der ID ist nicht multiuser Save
			int userId = getUserId(player1);
			String query = "INSERT INTO game (`player1`, `gamesize`, `state`) VALUES ('" + userId + "', '" + game.getGameSize() + "', '" + game.getStatus().getNummer() + "')";
			statement.execute(query);
			query = "SELECT max(id) FROM game";
			ResultSet rs = statement.executeQuery(query);
			rs.next();
			int id = rs.getInt(1);
			game.setId(id);
			initNewGame(game);
		} catch (SQLException e) {
			throw new Exception("Fehler beim Game insert.\n" + e.getMessage());
		}
	}
	
	private void initNewGame(GameServer game) throws Exception {
		try {
			String query = "START TRANSACTION;";
			statement.execute(query);
			try {
				for(String key : game.getInitCells().keySet())	{
					query = "INSERT INTO fieldvalue (`gameid`, `key`) VALUES ('" + game.getId() + "', '" + key + "')";
					statement.execute(query);
				}			
			} catch (SQLException e) {
				query = "ROLLBACK;";
				statement.execute(query);
				throw e;
			}
			query = "COMMIT;";
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception(e.getMessage());
		}
	}
	
	private int getUserId(String username) throws Exception{
		try {
			String query = "SELECT id FROM player WHERE name='" + username + "'";
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()){
				return rs.getInt(1);
			} else {
				throw new Exception("User mit name '" + username + "' nicht vorhanden!");
			}
		} catch (SQLException e) {
			throw new Exception("SQL Fehler bei suchen der Userid");
		}
	}
	
	public HashMap<String, Boolean> getCells(int gameId) throws Exception{
		try{
			String query = "SELECT `key`, `value` FROM fieldvalue WHERE gameid = " + gameId + " ORDER BY `key`";
			ResultSet rs = statement.executeQuery(query);
			HashMap<String, Boolean> cells = new HashMap<String, Boolean>();
			while(rs.next()) {
				String key = rs.getString(1);
				Boolean value = rs.getBoolean(2);
				if(rs.wasNull())
					value = null;
				cells.put(key, value);
			}
			return cells;
		} catch (SQLException e) {
			throw new Exception("Fehler beim Laden der Feldinhalte.\n" + e.getMessage());
		}
	}
	
	public void setCell(int gameId, String key, Boolean value) throws Exception {
		try {
			String val = "NULL";
			if(value == true)
				val = "1";
			else if (value == false)
				val = "0";
			String query = "UPDATE fieldvalue SET `value`=" +  val + " WHERE `key`='" + key + "' AND `gameid`='" + gameId + "'";
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim ändern eines Feldwertes.\n" + e.getMessage());
		}
	}
	
	public GameServer getGame(int id) throws Exception {
		try {
			String query = "SELECT game.gamesize, game.state, player1.name player1, player2.name player2, playernext.name nextplayer "
					+ "FROM game "
					+ "INNER JOIN player player1 ON game.player1 = player1.id "
					+ "LEFT JOIN player player2 ON game.player2 = player2.id "
					+ "LEFT JOIN player playernext ON game.nextplayer = playernext.id "
					+ "WHERE game.id=" + id;
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()){
				String player1 = rs.getString("player1");
				String player2 = rs.getString("player2");
				String nextPlayer = rs.getString("nextplayer");
				int gameStatus = rs.getInt("state");
				GameServer game = new GameServer(id, rs.getInt("gamesize"));
				game.setPlayer1(player1);
				if(player2 != null)
					game.setPlayer2(player2);
				if(nextPlayer != null)
					game.setNextPlayer(nextPlayer);
				game.setStatus(GameStatus.getEnum(gameStatus));
				return game;
			}
			throw new Exception("Game mit id " + id + " nicht gefunden.");
		} catch (SQLException e) {
			throw new Exception("Fehler beim laden des Games.\n" + e.getMessage());
		}
	}

	public GameServer findWaitingGame(String user) throws Exception {
		try {
			String query = "SELECT game.id FROM game WHERE game.player2 IS NULL AND game.state = " + GameStatus.Waiting.getNummer() + " ORDER BY game.datetime ASC";
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()){
				int gameId = rs.getInt(1);
				query = "UPDATE game SET player2=(SELECT id FROM player WHERE name='" + user + "'), state=" + GameStatus.Running.getNummer() + " WHERE id=" + gameId;
				statement.execute(query);
				return getGame(gameId);
			}
			return null;
		} catch (SQLException e) {
			throw new Exception("Fehler beim Suchen vom wartendem Spiel.\n" + e.getMessage());
		}
	}
	
	public void setStatus(int gameId, GameStatus gameStatus) throws Exception {
		try {
			if(gameStatus == null)
				return;
			String query = "UPDATE game SET state=" + gameStatus.getNummer() + " WHERE id=" + gameId;
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim setzen des Status.\n" + e.getMessage());
		}
	}

	public String getWinner(int gameId) throws Exception {
		try {
			String query = "SELECT name FROM game "
					+ "INNER JOIN player on winner = player.id WHERE game.id=" + gameId;
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()) {
				String winner = rs.getString(1);
				if(!rs.wasNull())
					return winner;
			}
			return null;
		} catch (SQLException e) {
			throw new Exception("Fehler beim holen des Gewinners.\n" + e.getMessage());
		}
	}
	
	public void setWinner(int gameId, String currentPlayer) throws Exception {
		try {
			String idWinner = "NULL";
			if(currentPlayer != null)
				idWinner = String.valueOf(getUserId(currentPlayer));
			String query = "UPDATE game SET winner=" + idWinner + " WHERE id=" + gameId;
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim setzen des Status.\n" + e.getMessage());
		}
	}
	
	public void setNextPlayer(int gameId, String nextPlayer) throws Exception {
		try {
			String idNext = "NULL";
			if(nextPlayer != null)
				idNext = String.valueOf(getUserId(nextPlayer));
			String query = "UPDATE game SET nextplayer=" + idNext + " WHERE id=" + gameId;
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim setzen des Status.\n" + e.getMessage());
		}
	}
	
	public String getNextPlayer(int gameId) throws Exception {
		try {
			String query = "SELECT name FROM game "
					+ "INNER JOIN player on nextplayer = player.id WHERE game.id=" + gameId;
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()) {
				String nextPlayer = rs.getString(1);
				if(!rs.wasNull())
					return nextPlayer;
			}
			return null;
		} catch (SQLException e) {
			throw new Exception("Fehler beim holen des nächsten Spielers.\n" + e.getMessage());
		}
	}
	
	public String getPlayer1 (int gameId) throws Exception {
		try {
			String query = "SELECT name FROM game "
					+ "INNER JOIN player on player1 = player.id WHERE game.id=" + gameId;
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()) {
				String nextPlayer = rs.getString(1);
				if(!rs.wasNull())
					return nextPlayer;
			}
			return null;
		} catch (SQLException e) {
			throw new Exception("Fehler beim holen des nächsten Spielers.\n" + e.getMessage());
		}
	}
	
	public String getPlayer2 (int gameId) throws Exception {
		try {
			String query = "SELECT name FROM game "
					+ "INNER JOIN player on player2 = player.id WHERE game.id=" + gameId;
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()) {
				String nextPlayer = rs.getString(1);
				if(!rs.wasNull())
					return nextPlayer;
			}
			return null;
		} catch (SQLException e) {
			throw new Exception("Fehler beim holen des nächsten Spielers.\n" + e.getMessage());
		}
	}
	
	///////////////////////////// SERVER MANAGEMENT ///////////////////////////////////////////////////
	public void loginServer(String host, int port)  throws Exception{
		try {
			String query = "INSERT IGNORE INTO serverliste (ip, port) VALUES ('" + host + "', " + port +")";
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim anmelden des Servers." + e.getMessage());
		}
	}
	
	public List<String> getServerlist() throws Exception {
		try {
			String query = "SELECT ip, port FROM serverliste";
			ResultSet rs = statement.executeQuery(query);
			List<String> listServer = new ArrayList<String>();
			while (rs.next()) {
				String server = rs.getString(1) + ":" + rs.getString(2);
				listServer.add(server);
			}
			return listServer;
		} catch (Exception e) {
			throw new Exception("Fehler beim Laden der Serverliste.");
		}
	}
	
	public void deleteServer(String host, int port) throws Exception {
		try {
			String query = "DELETE FROM serverliste WHERE ip='" + host + "' AND port=" + port;
			statement.execute(query);
		} catch (SQLException e) {
			throw new Exception("Fehler beim abmelden des Servers." + e.getMessage());
		}
	}
	
	public int getFreePort(String host) throws Exception {
		try {
			String query = "SELECT MAX(port), MIN(port) FROM serverliste WHERE ip = '" + host + "'";
			ResultSet rs = statement.executeQuery(query);
			if(rs.next()) {
				int maxPort = rs.getInt(1);
				int minPort = rs.getInt(2);
				if(maxPort == 0) {
					return Main.RMI_PORT_MIN;
				}
				if (minPort > Main.RMI_PORT_MIN) {
					return Main.RMI_PORT_MIN;
				}
				return maxPort + 1;
			}
			return Main.RMI_PORT_MIN;
		} catch (SQLException e) {
			throw new Exception("Fehler beim laden des nächsten freien Ports");
		}
	}
}
