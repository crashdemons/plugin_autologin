/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plugin_autologin;
import irtrusbot.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

/**
 *
 * @author crash
 */
public class Plugin_autologin extends IrcPlugin {
    private boolean doAutoJoin=true;
    private boolean doAutoReconnect=true;
    private ArrayList<String> channels = new ArrayList<String>();
        
    public void join(String channelstr){
        IrcCommand ic = new IrcCommand("JOIN "+channelstr);
        IrcEvent event = new IrcEvent(IrcEventType.COMMAND,IrcState.UNDEFINED,IrcState.UNDEFINED,ic);
        event.direction=IrcDirection.SENDING;
        postEventNext(event);
    }
    
    public IrcEventAction handleCommand(IrcEvent event,IrcState state,IrcCommand ic) throws Exception{
        System.out.println(ic.type+" | STATE: "+state.toString());
        if(state==IrcState.LOGIN_WAIT){
            IrcLoginState check = session.logincheck(ic);
            System.out.println(ic.type+" | LOGINSTATE: "+check.toString());
            switch(check)
            {
                case SUCCESS:
                    bot.updateState(IrcState.LOGGED_IN);
                    break;
                case FAILURE:
                    bot.quit();
                    break;
            }
            
        }
        return IrcEventAction.CONTINUE;
    }
    public IrcEventAction handleStateChange(IrcEvent event,IrcState state) throws Exception{
        switch(state){
            case CONNECTED:
               session.login();
               bot.updateState(IrcState.LOGIN_WAIT);
               break;
            case LOGGED_IN://TODO: send userinit to capture full origin string for self!
                if(doAutoJoin){
                    for(String channel : channels) join(channel);
                    bot.updateState(IrcState.JOINED);
                }
                break;
            case DISCONNECTED:
               if(doAutoReconnect) bot.connect();
               break;
        }
        return IrcEventAction.CONTINUE;
    }
    
    
    @Override
    public IrcEventAction handleEvent(IrcEvent event) throws Exception
    {
        if(event.type==IrcEventType.STATE && event.state==event.lastState) ;
        else System.out.println("EVENT: "+event.type.toString()+" "+event.lastState.toString()+" "+event.state.toString());
        
        switch(event.type){
            case BOT_START:
                System.out.println("getting session config");
                getSessionConfig();
                System.out.println("calling bot connect");
                bot.connect();
                System.out.println("should be connected");
                break;
            case STATE:
                if(event.state!=event.lastState)
                    return handleStateChange(event,event.state);
                break;
            case COMMAND:
                if(event.command!=null) return handleCommand(event,event.state,event.command);
                else System.out.println("COMMAND NULL");
                break;
            case BOT_STOP:
                bot.session.disconnect();
                saveConfig();
                break;
        }
        
        return IrcEventAction.CONTINUE;
    }
    
    public void getSessionConfig(){
        String server = config.getProperty("server","irc.freenode.net");
        int port = Integer.parseInt(config.getProperty("port","6667"));
        if(session==null) System.out.println("Session null");
        else session.setConnectionDetails(server, port);
        String nick=config.getProperty("nick");
        String user=config.getProperty("user");
        String realname=config.getProperty("realname");
        String password=config.getProperty("password");
        if(session==null)  System.out.println("Session null");
        else session.setAccountDetails(nick,user,realname,password);
        String channelstr=config.getProperty("channels");
        System.out.println(defaults.getProperty("channels"));
        System.out.println(channelstr);
        channels=new ArrayList<String>(Arrays.asList(channelstr.split(",+")));
        doAutoJoin=Boolean.parseBoolean(config.getProperty("autojoin"));
        doAutoReconnect=Boolean.parseBoolean(config.getProperty("autoreconnect"));
    }
    
    
    public Plugin_autologin(){
        System.out.println("Constructor called - setting defaults.");
        name="autologin";
        version="1.0-pre";
        description="Performs automatic connection, login, and channel join operations.";
        priority=IrcPluginPriority.RAW;
        
        defaults.setProperty("server","irc.freenode.net");
        defaults.setProperty("port","6667");
        defaults.setProperty("nick","IrtrusBot");
        defaults.setProperty("user","IrtrusBot");
        defaults.setProperty("realname","Java Hacks Bot");
        defaults.setProperty("password","");
        defaults.setProperty("channels","#IrtrusBot,#cicada,##426699k");
        defaults.setProperty("autojoin","true");
        defaults.setProperty("autoreconnect","true");
    }
    
    public static void main(String[] args) {
        Plugin_autologin p = new Plugin_autologin();
        p.config=new Properties(p.defaults);
        p.getSessionConfig();
        // unused method
    }
    
    
}

        