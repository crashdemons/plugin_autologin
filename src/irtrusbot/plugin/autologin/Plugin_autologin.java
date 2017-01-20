/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package irtrusbot.plugin.autologin;
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
    private int timer_keepalive=0;
    private int timer_nickwait=0;
    private ArrayList<String> channels = new ArrayList<String>();
        
    public void join(String channelstr){
        IrcCommand ic = new IrcCommand("JOIN "+channelstr);
        IrcEvent event = new IrcEvent(IrcEventType.COMMAND,IrcState.UNDEFINED,IrcState.UNDEFINED,ic);
        event.direction=IrcDirection.SENDING;
        postEventNext(event);
    }
    public void pong(String data){
        IrcCommand ic = new IrcCommand("PONG :"+data);
        IrcEvent event = new IrcEvent(IrcEventType.COMMAND,IrcState.UNDEFINED,IrcState.UNDEFINED,ic);
        event.direction=IrcDirection.SENDING;
        postEventNext(event);
    }
    public void im(String to,String text){
        IrcMessage im = new IrcMessage(session.account,to,text);
        IrcCommand ic = new IrcCommand(im.toOutgoing());
        IrcEvent event = new IrcEvent(IrcEventType.COMMAND,IrcState.UNDEFINED,IrcState.UNDEFINED,ic);
        event.direction=IrcDirection.SENDING;
        postEventNext(event);
    }
    
    
    public IrcEventAction handleCommand(IrcEvent event,IrcState state,IrcCommand ic) throws Exception{
        if(ic.type.equals("PING")) pong(ic.parameters.get(0));
        boolean do_quit=false;
        if(state==IrcState.LOGIN_WAIT){
            IrcLoginState check = session.logincheck(ic);
            switch(check)
            {
                case SUCCESS:
                    bot.updateState(IrcState.LOGGED_IN);
                    break;
                case FAILURE:
                    System.out.println("autologin: login failed");
                    do_quit=true;
                    break;
            }
        }
        if(session.isFatalCommand(ic)){
            if(ic.type.equals("433")){//nickname in use
                System.out.println("autologin: nickname in use - retry with incremental delay");
                timer_nickwait+=60*2;
                bot.disconnect();
                do_quit=false;
            }else{
                System.out.println("autologin: fatal command received");
                do_quit=true;
            }
        }
        if(do_quit) bot.quit();
        return IrcEventAction.CONTINUE;
    }
    public IrcEventAction handleStateChange(IrcEvent event,IrcState state) throws Exception{
        switch(state){
            case CONNECTED:
               System.out.println("autologin: connected - sending login information...");
               session.login();
               bot.updateState(IrcState.LOGIN_WAIT);
               break;
            case LOGGED_IN://TODO: send userinit to capture full origin string for self!
                timer_nickwait=0;
                System.out.println("autologin: logged in.");
                System.out.println("autologin: retrieving self origin string...");
                im(session.account.nick,"++autologin-userinit++");
                if(doAutoJoin){
                    System.out.println("autologin: autojoining channels...");
                    for(String channel : channels) join(channel);
                    bot.updateState(IrcState.JOINED);
                }
                break;
            case DISCONNECTED:
               System.out.println("autologin: disconnected");
               //we could reconnect here, but we need to handle this multiple times, not just on state change. see TICK
               break;
        }
        return IrcEventAction.CONTINUE;
    }
    
    
    @Override
    public IrcEventAction handleEvent(IrcEvent event) throws Exception
    {
        //if(event.type==IrcEventType.STATE && event.state==event.lastState) ;
        //else System.out.println("EVENT: "+event.type.toString()+" "+event.lastState.toString()+" "+event.state.toString());
        
        switch(event.type){
            case BOT_START:
                System.out.println("autologin: getting session config");
                getSessionConfig();
                System.out.println("autologin: connecting...");
                bot.connect();
                System.out.println("autologin: connect finished");
                break;
            case STATE:
                if(event.state!=event.lastState)
                    return handleStateChange(event,event.state);
                break;
            case COMMAND:
                if(event.command!=null) return handleCommand(event,event.state,event.command);
                else System.out.println("autologin: null command received via event");
                break;
            case CHAT:
                if(event.message.from.nick.equals(session.account.nick))
                    if(event.message.text.equals("++autologin-userinit++")){
                        session.account=event.message.from;
                        System.out.println("autologin: updated self origin string");
                    }
                break;
            case BOT_STOP:
                System.out.println("autologin: stop received - saving config and cleaning up...");
                bot.session.disconnect();
                saveConfig();
                break;
            case TICK:
                timer_keepalive+=1;
                if(timer_keepalive==(20+timer_nickwait)){
                    timer_keepalive=0;
                    bot.session.sendKeepalive();
                    if(doAutoReconnect && bot.state==IrcState.DISCONNECTED){
                        System.out.println("autologin: nonfatal disconnect - autoreconnecting...");
                        bot.connect();
                    }
                }
        }
        
        return IrcEventAction.CONTINUE;
    }
    
    public void getSessionConfig(){
        String server = config.getProperty("server","irc.freenode.net");
        int port = Integer.parseInt(config.getProperty("port","6667"));
        if(session==null) System.out.println("autologin: Session null during config load");
        else session.setConnectionDetails(server, port);
        String nick=config.getProperty("nick");
        String user=config.getProperty("user");
        String realname=config.getProperty("realname");
        String password=config.getProperty("password");
        if(session==null)  System.out.println("autologin: Session null during config load");
        else session.setAccountDetails(nick,user,realname,password);
        String channelstr=config.getProperty("channels");
        channels=new ArrayList<String>(Arrays.asList(channelstr.split(",+")));
        doAutoJoin=Boolean.parseBoolean(config.getProperty("autojoin"));
        doAutoReconnect=Boolean.parseBoolean(config.getProperty("autoreconnect"));
    }
    
    
    public Plugin_autologin(){
        name="autologin";
        version="2.1";
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

        