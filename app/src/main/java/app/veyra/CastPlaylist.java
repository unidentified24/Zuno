package app.veyra;
import java.io.IOException;
import java.net.URI;
import java.util.regex.*;

/** Rewrite every HLS media/key/variant URI through the session registry. Never transcode. */
final class CastPlaylist {
 interface Register {String add(String url)throws IOException;}
 private static final Pattern ATTRIBUTE=Pattern.compile("(?<![A-Za-z0-9_-])URI=\"([^\"\\r\\n]*)\"");
 static String rewrite(String input,String base,Register register)throws IOException {
  if(!input.startsWith("#EXTM3U")||input.length()>2*1024*1024)throw new IOException("Unsupported HLS playlist");
  StringBuilder out=new StringBuilder();
  for(String line:input.split("\\r?\\n",-1)){
   if(line.startsWith("#")){
    // Variable expansion and content steering need receiver-specific processing.
    if(line.startsWith("#EXT-X-DEFINE")||line.startsWith("#EXT-X-CONTENT-STEERING"))throw new IOException("This HLS feature cannot be cast safely.");
    Matcher m=ATTRIBUTE.matcher(line);StringBuffer changed=new StringBuffer();
    while(m.find())m.appendReplacement(changed,Matcher.quoteReplacement("URI=\""+register.add(resolve(base,m.group(1)))+"\""));m.appendTail(changed);out.append(changed);
   }else if(!line.trim().isEmpty())out.append(register.add(resolve(base,line.trim())));
   out.append('\n');
  }return out.toString();
 }
 static String resolve(String base,String ref)throws IOException {try{URI u=URI.create(base).resolve(ref);if(u.getRawUserInfo()!=null||u.getRawFragment()!=null||u.getHost()==null)throw new IllegalArgumentException();return u.toString();}catch(IllegalArgumentException e){throw new IOException("Invalid media reference");}}
}
