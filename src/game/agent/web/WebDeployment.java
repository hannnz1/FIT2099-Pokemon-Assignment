package game.agent.web;
import java.net.URI;
import java.util.Map;
/** Explicit reverse proxy configuration; forwarded headers never select the trusted origin. */
public final class WebDeployment {
    public final String bindAddress,host,origin;
    public final boolean secureCookie;
    private WebDeployment(String bind,String host,String origin,boolean secure){this.bindAddress=bind;this.host=host;this.origin=origin;this.secureCookie=secure;}
    public static WebDeployment fromEnvironment(Map<String,String> env,int port){
        String bind=env.getOrDefault("AGENT_BIND_ADDRESS","127.0.0.1");
        if(!bind.equals("127.0.0.1")&&!bind.equals("0.0.0.0"))throw new IllegalArgumentException("INVALID_BIND_ADDRESS");
        String publicOrigin=env.get("PUBLIC_ORIGIN");
        if(publicOrigin==null)return new WebDeployment(bind,"127.0.0.1:"+port,"http://127.0.0.1:"+port,false);
        URI u;try{u=URI.create(publicOrigin);}catch(RuntimeException e){throw new IllegalArgumentException("INVALID_PUBLIC_ORIGIN");}
        if(!"https".equals(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!"".equals(u.getRawPath())||u.getPort()==0||u.getPort()>65535)
            throw new IllegalArgumentException("INVALID_PUBLIC_ORIGIN");
        return new WebDeployment(bind,u.getRawAuthority(),publicOrigin,true);
    }
}
