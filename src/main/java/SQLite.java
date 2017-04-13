import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.utils.SimpleLog;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Created by Xavie on 4/1/2017.
 */
public class SQLite
{
    private Connection connection;
    private Statement statement = null;
    private PreparedStatement pStatement = null;

    public void dbChecks(JDA instance) {
        List<Guild> guilds = instance.getGuilds();
        //Verifying database integrity.
        try {
            initConnection("tags");
            for(int i=0;i<guilds.size();i++) {
                statement.execute(
                        "create table if not exists [" + guilds.get(i).getId() + "] (uid integer constraint constraint_name primary key, "
                                + "name text, tag text, user text, userid text, date numeric)"
                );
            }
            closeConnection();
            initConnection("userLog");
            /*for(int i=0;i<guilds.size();i++) {
                statement.execute(
                        ""
                );
            }*/
            closeConnection();
            initConnection("messageCache");
            for(int i=0;i<guilds.size();i++) {
                statement.execute("create table if not exists [" + guilds.get(i).getId() + "] (id text, date integer, message text)");
            }
            closeConnection();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            SimpleLog.getLog("SQLite").warn("Error in post initialization!");
        }
    }

    public void cacheStore(String message, String messageID, String guildID) {
        try {
            initConnection("messageCache");
            ResultSet rs = statement.executeQuery("select date from [" + guildID + "]");
            if (!rs.next()) {
                SimpleLog.getLog("SQLite").info("MessageCache DB empty!");
            } else {
                statement.executeUpdate("delete from [" + guildID + "] where date < '" + new java.util.Date(System.currentTimeMillis() - 3600 * 1000).getTime() +"'");
            }
            pStatement = connection.prepareStatement("insert into [" + guildID + "] (id, date, message) values (?," + new java.util.Date().getTime() + ",?)");
            pStatement.setString(1,messageID);
            pStatement.setString(2, message);
            pStatement.executeUpdate();
            closeConnection();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    public String cacheGet(String messageID, String guildID) {
        String returnMessage = "ERROR";
        try {
            initConnection("messageCache");
            ResultSet rs = statement.executeQuery("select message from [" + guildID + "] where id == '" + messageID + "'");
            if(!rs.next()) {
                SimpleLog.getLog("SQLite").warn("No such id found! Is the prune time too short?");
            } else if (rs.getFetchSize() != 0) {
                SimpleLog.getLog("SQLite").warn("Multiple instances of this id! This should never happen!");
            } else {
                do {
                    returnMessage = rs.getString("message");
                } while (rs.next());
            }
            closeConnection();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
        return returnMessage;
    }

    public String tagi(String tag, String guildID) {
        String returnMsg = "No tag found by this name!";
        try {
            initConnection("tags");
            pStatement = connection.prepareStatement("select * from [" + guildID + "] where name = (?)");
            pStatement.setString(1,tag);
            ResultSet rs = pStatement.executeQuery();
            if(!rs.next()) {closeConnection();}
            else if (rs.getFetchSize() != 0) {closeConnection(); returnMsg = "Multiple tags found by this name!";}
            else {
                do {
                    returnMsg = "Tag " + rs.getString("tag") + " made by " + rs.getString("user") +
                            "(" + rs.getString("userid") + ") on " + new java.util.Date(rs.getLong("date"));
                }while(rs.next());
                closeConnection();
            }
            closeConnection();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            SimpleLog.getLog("SQLite").warn("Error occured fetching tag information!");
            returnMsg = "Something went wrong fetching information for this tag!";
        }
        return returnMsg;
    }

    public String tagc(String name, String tag, String guildID, String user, String userID) {
        try {
            initConnection("tags");
            pStatement = connection.prepareStatement("select name from [" + guildID + "] where name = ?");
            pStatement.setString(1,name);
            ResultSet rs = pStatement.executeQuery();
            System.out.println(rs.getFetchSize());
            if(rs.next()) {return "This tag already exists!";}
            pStatement = null;
            pStatement = connection.prepareStatement("insert into [" + guildID + "] (name,tag,user,userid,date) " +
                    "values (?,?,?,?," + new java.util.Date(System.currentTimeMillis() - 3600 * 1000).getTime() + ")");
            pStatement.setString(1, name);
            pStatement.setString(2, tag);
            pStatement.setString(3, user);
            pStatement.setString(4, userID);
            pStatement.executeUpdate();
            closeConnection();
            SimpleLog.getLog("SQLite").info("Created a tag: " + name);
            return "Tag " + name + " created!";
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            SimpleLog.getLog("SQLite").warn("Failure writing a tag!");
            return "Failed to create tag. If this error persists, contact Laeron!";
        }
    }

    public String tagd(String tag, String guildID) {
        try {
            initConnection("tags");
            pStatement = connection.prepareStatement("delete from [" + guildID + "] where name == ?");
            pStatement.setString(1,tag);
            pStatement.executeUpdate();
            closeConnection();
            return "Tag " + tag + " successfully deleted!";
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            SimpleLog.getLog("SQLite").warn("Failure deleting tag " + tag + "!");
            return "Error occurred in tag deletion!";
        }
    }

    public String tag(String tag, String guildID) {
        try {
            String tagFinal;
            initConnection("tags");
            pStatement = connection.prepareStatement("select tag from [" + guildID + "] where name = (?)");
            pStatement.setString(1,tag);
            ResultSet rs = pStatement.executeQuery();
            if(!rs.next()) {closeConnection(); return "No tag found by this name!";}
            else if (rs.getFetchSize() != 0) {closeConnection(); return "Multiple tags found by this name!";}
            else {do{tagFinal = rs.getString("tag");}while(rs.next()); closeConnection(); return tagFinal;}
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            SimpleLog.getLog("SQLite").warn("Error occurred fetching tag!");
            return "Something went wrong getting this tag!";
        }
    }

    public Message tagl(String guildID) {
        MessageBuilder messageBuilder = new MessageBuilder();
        try {
            initConnection("tags");
            ResultSet rs = statement.executeQuery("select name from [" + guildID + "]");
            if(!rs.next()) {closeConnection(); return messageBuilder.append("No tags found!").build();}
            else {do{messageBuilder.append(rs.getString("name") + "\n");}while (rs.next()); closeConnection(); return messageBuilder.build();}
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            SimpleLog.getLog("SQLite").warn("Error fetching list of tags for this guild!");
            return messageBuilder.append("Something went wrong retrieving the list!").build();
        }
    }

    private void initConnection(String db) throws ClassNotFoundException, SQLException {
        String dir = "." + File.separator + "databases" + File.separator;
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dir + db + ".db");
        statement = connection.createStatement();
        statement.setQueryTimeout(30);
    }

    private void closeConnection() throws SQLException {
        connection.close();
        statement = null;
        connection = null;
    }
}
