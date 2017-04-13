import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.utils.SimpleLog;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by Xavie on 4/1/2017.
 */


public class Xonah extends ListenerAdapter {
    static JDA jda; //Declare the JDA object in this scope, for easier access instead of accessing it through events.

    public static void main(String[] args) throws LoginException, RateLimitedException, IOException, InterruptedException, ParseException {
        new Xonah();
    }

    public Xonah() throws LoginException, RateLimitedException, IOException, InterruptedException, ParseException {
        String token = init(); //Fetch properties, make sure files/folders exist.
        SimpleLog.getLog("Init").info("Initializing with token: " + token);

        jda = new JDABuilder(AccountType.BOT).setToken(token)
                .addEventListener(this).buildAsync(); //Start the bot, store it in our earlier declared JDA object.

        while (!(jda.getStatus().equals(JDA.Status.CONNECTED))) {Thread.sleep(10);} //Wait until connection is established.

        SimpleLog.getLog("Post-Init").info("Beginning verification that couldn't be done without API calls.");
        //Checks on JSON objects, SQLite tables, etc. Return a list of guilds that had errors in the JSON file.
        ArrayList<Guild> resetGuilds = postInit();
        SimpleLog.getLog("All initializations finished, continuing as normal, prompting for setup to guilds with missing data");
        //Prompt guild owners that have missing data to do setup again.
        for(int i=0;i<resetGuilds.size();i++) {new GuildSettings().setup(true,resetGuilds.get(i));}
    }

    private String init() throws IOException {
        String token;
        SimpleLog.getLog("Init").info("Retrieving properties and reading JSON files...");
        //Open a file reader to the properties file, then load it into a properties object and fetch the token, write it to string.\
        FileReader reader = new FileReader(new File("config.properties"));
        Properties props = new Properties();
        props.load(reader);

        token = props.getProperty("token");
        reader.close();

        SimpleLog.getLog("Init").info("Retrieved token OK");
        SimpleLog.getLog("Init").info("Checking SQLite database integrity (Stage 1 of 2)...");

        //Declare the director for, and the the individual database files, check for their existence, create them if not there.
        File dir = new File('.' + File.separator + "Databases");
        File[] dbs = new File[]
                {new File(dir,"tags.db"), new File(dir, "userlog.db"), new File(dir, "messagecache")};

        if(!dir.exists()) {SimpleLog.getLog("Init").warn("Root database director missing!"); dir.mkdir();}
        for(int i=0;i!=dbs.length;i++) {
            if(!dbs[i].exists()){SimpleLog.getLog("Init").warn(dbs[i].getName() + " is missing!"); dbs[i].createNewFile();}
        }

        SimpleLog.getLog("Init").info("Finished stage 1 of 2 of SQLite database integrity check.");
        SimpleLog.getLog("Init").info("Checking JSON configuration files(Stage 1 of 3)...");

        //Do the same thing for JSON
        File jsonConfig = new File("guildconfig.json");

        if(!jsonConfig.exists()) {SimpleLog.getLog("Init").warn("Json file missing!");jsonConfig.createNewFile();}

        SimpleLog.getLog("Init").info("Finished stage 1 of 2 of JSON configuration check.");
        SimpleLog.getLog("Init").info("Initialization completed successfully!");

        return token;
    }

    private ArrayList<Guild> postInit() throws IOException, ParseException {
        ArrayList<Guild> returnArray = new ArrayList<>();
        SimpleLog.getLog("Checking JSON configuration files(Stage 2 of 3)...");

        String[] modules = new String[]{"tags","logging","roles"};
        List<Guild> guilds = jda.getGuilds();

        //Open the JSON file, then parse it into a JSONObject.
        FileReader reader = new FileReader(new File("guildconfig.json"));
        JSONObject jsonObject = (JSONObject) new JSONParser().parse(reader);

        for(int i=0;i<guilds.size();i++) {
            if(!jsonObject.containsKey(guilds.get(i).getId())) { //Check to make sure this guild has an object, recreate it if not.
                SimpleLog.getLog("Post-Init").warn("Guild " + guilds.get(i).getName() + " is missing their JSON entry!"
                + " Setting reset flag and regenerating...");
                JSONObject guildObject = new JSONObject();
                JSONObject jsonModules = new JSONObject();
                JSONObject jsonMasters = new JSONObject();

                for(int i2=0;i<modules.length;i++) {jsonModules.put(modules[i], true);}
                guildObject.put("modules", jsonModules);
                guildObject.put("botmasters", jsonMasters);
                guildObject.put("reset", true); //Sets a flag that indicated the information for this guild needs to be re-built.
                returnArray.add(guilds.get(i));
                jsonObject.put(guilds.get(i).getId(),guildObject);
            } else {
                JSONObject guildObject = (JSONObject) jsonObject.get(guilds.get(i).getId());
                JSONObject jsonModules;

                if(guildObject.containsKey("modules")) { //Check for the modules object, recreate if not exists.
                    jsonModules = (JSONObject) guildObject.get("modules");
                    for(int i2=0;i<modules.length;i++) {
                        if(!jsonModules.containsKey(modules[i2])) {
                            SimpleLog.getLog("Post-Init").warn("JSON config for " + guilds.get(i).getName()
                                    + " is partially corrupted, setting reset flag and regenerating...");
                            jsonModules.put(modules[i2], true);
                            guildObject.put("reset", true); //Sets a flag that indicated the information for this guild needs to be re-built.
                            if(!returnArray.contains(guilds.get(i).getId())){returnArray.add(guilds.get(i));}
                        }
                    }
                    guildObject.put("modules", jsonModules);
                } else {
                    SimpleLog.getLog("Post-Init").warn("JSON config for " + guilds.get(i).getName()
                            + " is partially corrupted, setting reset flag and regenerating...");
                    jsonModules = new JSONObject();
                    for(int i2=0;i<modules.length;i++) {jsonModules.put(modules[i2], true);}
                    guildObject.put("reset", true); //Sets a flag that indicated the information for this guild needs to be re-built.
                    if(!returnArray.contains(guilds.get(i).getId())){returnArray.add(guilds.get(i));}
                }
                if(jsonModules!=null){guildObject.put("modules", jsonModules);}
                //Check to make sure the botmasters object exists, recreate if not.
                if(!guildObject.containsKey("botmasters")){guildObject.put("reset",true); guildObject.put("botmasters", new JSONObject());}
            }
        }
        //Now we close the reader and write the changes to the JSON file.
        reader.close();
        new FileWriter(new File("guildconfig.json")).write(jsonObject.toJSONString());
        SimpleLog.getLog("Post-Init").info("JSON configuration verified, and all changes written to file.");
        SimpleLog.getLog("Post-Init").info("Checking SQLite database files(Stage 2 of 2)...");
        new SQLite().dbChecks(jda); //Run through basic table checking in the SQLite class.
        SimpleLog.getLog("Post-Init").info("SQLite databases verified, all missing tables replaced.");

        return returnArray;
    }
}