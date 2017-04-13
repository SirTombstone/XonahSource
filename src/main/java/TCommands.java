/**
 * Created by Xavie on 1/17/2017.
 */
public class TCommands
{
    SQLite sqLite = new SQLite();

    public String tagc(String arg, String guildID) {
        char currentChar = 'a';
        int nameEnd;
        for(nameEnd=0;!(Character.isWhitespace(currentChar));nameEnd++) {
            currentChar = arg.charAt(nameEnd);
        }
        return "asd";
        //return sqLite.tagc(arg.substring(0,nameEnd-1), arg.substring(nameEnd), guildID);
    }

    public String tagd(String tag, String guildID) {return sqLite.tagd(tag, guildID);}

    public String tag(String tag, String guildID) {return sqLite.tag(tag,guildID);}

    //public Message tagl(String guildID) {return sqLite.tagl(guildID);}

}
