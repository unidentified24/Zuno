package app.veyra;
import androidx.media3.datasource.*;
import app.veyra.data.StreamHttp;
import app.veyra.model.StreamSource;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Session-only, bounded, read-only media relay. The client supplies opaque IDs, never URLs. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class CastRelay implements AutoCloseable {
 private final ServerSocket server;private final ExecutorService accept=Executors.newSingleThreadExecutor();
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(0,6,30,TimeUnit.SECONDS,new SynchronousQueue<>());
 private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
 private final Map<String,String> urls=new LinkedHashMap<>(),ids=new HashMap<>();
 private final DataSource.Factory upstream;private final String token=secret(),base,root,mime;
 private volatile boolean closed;volatile long lastRequest=android.os.SystemClock.elapsedRealtime();
 volatile long startPosition;private String pairCode;private long pairExpires;private int pairFailures;
 static String secret(){byte[] b=new byte[24];new SecureRandom().nextBytes(b);StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();}
 CastRelay(CastNetwork network,StreamSource source)throws IOException {this(network.address,source,StreamHttp.castFactory(source));}
 // Injectable upstream is used by hermetic socket tests, never by playback-file imports.
 CastRelay(InetAddress bind,StreamSource source,DataSource.Factory factory)throws IOException {
  mime=source.mimeType;if(mime.equals("application/dash+xml"))throw new IOException("DASH casting is not supported for this receiver. Use screen mirroring.");
  upstream=factory;server=new ServerSocket();server.setReuseAddress(false);server.bind(new InetSocketAddress(bind,0),8);
  base="http://"+bind.getHostAddress()+":"+server.getLocalPort()+"/"+token+"/";
  root=register(source.url);accept.execute(()->{while(!closed)try{Socket socket=server.accept();socket.setSoTimeout(15000);sockets.add(socket);try{workers.execute(()->serve(socket));}catch(RejectedExecutionException e){sockets.remove(socket);socket.close();}}catch(IOException e){if(!closed)close();}});
 }
 String mediaUrl(){return root;}String browserUrl(){return base+"watch";}
 String pairingUrl(){return base.substring(0,base.indexOf('/',7))+"/";}
 synchronized String newPairCode(){pairCode=String.format(Locale.ROOT,"%06d",new SecureRandom().nextInt(1000000));pairExpires=android.os.SystemClock.elapsedRealtime()+120000;pairFailures=0;return pairCode;}
 private synchronized boolean pair(String code){if(pairCode==null||android.os.SystemClock.elapsedRealtime()>pairExpires||pairFailures>=5)return false;if(pairCode.equals(code)){pairCode=null;return true;}if(++pairFailures>=5)pairCode=null;return false;}
 private synchronized String register(String url)throws IOException {
  if(closed)throw new IOException("Session ended");URI u;try{u=URI.create(url);}catch(IllegalArgumentException e){throw new IOException("Invalid media reference");}
  if(u.getHost()==null||u.getRawUserInfo()!=null||u.getRawFragment()!=null||!("https".equals(u.getScheme())||("http".equals(u.getScheme())&&u.getHost().equals("127.0.0.1"))))throw new IOException("Unsafe media reference");
  if(url.length()>16384)throw new IOException("Media reference too long");String existing=ids.get(url);if(existing!=null)return base+existing;
  if(urls.size()>=32768)throw new IOException("Playlist has too many resources");String id=secret()+(u.getPath()!=null&&u.getPath().endsWith(".m3u8")?".m3u8":".bin");urls.put(id,url);ids.put(url,id);return base+id;
 }
 private void serve(Socket socket){try(socket){InputStream input=socket.getInputStream();OutputStream out=socket.getOutputStream();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);String first=line(input,4096,deadline);String[] fields=first.split(" ");if(fields.length!=3||!fields[2].startsWith("HTTP/1.")){reply(out,400,"Bad request");return;}
  String method=fields[0],path=fields[1],range=null;int contentLength=-1;int headerBytes=first.length();for(int i=0;i<64;i++){String h=line(input,4096,deadline);headerBytes+=h.length();if(headerBytes>16384)throw new IOException("Headers too large");if(h.isEmpty())break;if(i==63)throw new IOException("Too many headers");if(h.regionMatches(true,0,"Range:",0,6)){if(range!=null)throw new IOException("Duplicate Range");range=h.substring(6).trim();}if(h.regionMatches(true,0,"Content-Length:",0,15)){if(contentLength>=0)throw new IOException("Duplicate length");try{contentLength=Integer.parseInt(h.substring(15).trim());}catch(NumberFormatException e){throw new IOException("Invalid length");}}}
  if(path.equals("/")&&method.equals("GET")){synchronized(this){if(pairCode==null||android.os.SystemClock.elapsedRealtime()>pairExpires){reply(out,404,"Open TV browser pairing on your phone first");return;}}byte[] form="<!doctype html><meta name='viewport' content='width=device-width'><title>Velixio pairing</title><style>body{background:#090d16;color:white;font:24px system-ui;padding:5vw}input,button{font:inherit;padding:12px;margin:12px 0}</style><h1>Velixio</h1><p>Enter the six-digit code displayed on your phone.</p><form method='post' action='/pair'><input name='code' pattern='[0-9]{6}' inputmode='numeric' maxlength='6' autocomplete='off' required aria-label='Pairing code'><button>Connect</button></form>".getBytes(StandardCharsets.UTF_8);headers(out,200,"OK","Content-Type: text/html; charset=utf-8\r\nContent-Length: "+form.length+"\r\nReferrer-Policy: no-referrer\r\n");out.write(form);return;}
  if(path.equals("/pair")&&method.equals("POST")){if(contentLength!=11){reply(out,403,"Invalid or expired pairing code");return;}byte[] bytes=new byte[contentLength];int read=0;while(read<bytes.length){if(System.nanoTime()>deadline)throw new IOException("Request timed out");int n=input.read(bytes,read,bytes.length-read);if(n<0)throw new EOFException();read+=n;}String code=new String(bytes,StandardCharsets.US_ASCII);if(!code.matches("code=[0-9]{6}")||!pair(code.substring(5))){reply(out,403,"Invalid or expired pairing code. Generate a new code on your phone.");return;}headers(out,303,"See Other","Location: "+browserUrl()+"\r\nContent-Length: 0\r\nReferrer-Policy: no-referrer\r\n");return;}
  if(!path.startsWith("/"+token+"/")||path.contains("?")||path.contains("#")){reply(out,404,"Not found");return;}
  if(!method.equals("GET")&&!method.equals("HEAD")&&!method.equals("OPTIONS")){reply(out,405,"Method not allowed");return;}
  if(method.equals("OPTIONS")){headers(out,204,"No Content","Content-Length: 0\r\n");return;}
  String id=path.substring(token.length()+2);if(id.equals("watch")){byte[] page=page().getBytes(StandardCharsets.UTF_8);headers(out,200,"OK","Content-Type: text/html; charset=utf-8\r\nContent-Length: "+page.length+"\r\nContent-Security-Policy: default-src 'none'; media-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'\r\nReferrer-Policy: no-referrer\r\n");if(method.equals("GET"))out.write(page);lastRequest=android.os.SystemClock.elapsedRealtime();return;}
  String url;synchronized(this){url=urls.get(id);}if(url==null){reply(out,404,"Not found");return;}lastRequest=android.os.SystemClock.elapsedRealtime();
  relay(out,method,url,range);
 }catch(Exception ignored){/* Close the failed request; never include signed URLs in responses/logs. */}finally{sockets.remove(socket);}}
 private void relay(OutputStream out,String method,String url,String range)throws IOException {
  long[] limits;try{limits=parseRange(range);}catch(IOException e){reply(out,416,"Unsupported range");return;}
  DataSource data=upstream.createDataSource();boolean opened=false;
  try {
   long start=limits[0],length=limits[1];
   if(start<0){long total=data.open(new DataSpec(Uri.parse(url)));data.close();if(total<=0){reply(out,416,"Unsupported range");return;}start=Math.max(0,total+start);length=total-start;}
   long available=data.open(new DataSpec.Builder().setUri(url).setPosition(start).setLength(length).build());opened=true;
   String finalUrl=data.getUri()==null?url:data.getUri().toString();String type=header(data,"Content-Type");
   boolean playlist=(url.equals(urlsRoot())&&mime.equals("application/x-mpegURL"))||URI.create(url).getPath().endsWith(".m3u8")||type.toLowerCase(Locale.ROOT).contains("mpegurl");
   if(playlist){if(range!=null){reply(out,416,"Playlist ranges unsupported");return;}byte[] bytes=readBounded(data,2*1024*1024);String rewritten=CastPlaylist.rewrite(new String(bytes,StandardCharsets.UTF_8),finalUrl,this::register);byte[] body=rewritten.getBytes(StandardCharsets.UTF_8);headers(out,200,"OK","Content-Type: application/vnd.apple.mpegurl\r\nContent-Length: "+body.length+"\r\n");if(method.equals("GET"))out.write(body);return;}
   String contentRange=header(data,"Content-Range");
   if(contentRange.matches("bytes [0-9]+-[0-9]+/([0-9]+|\\*)"))try{String[] bounds=contentRange.substring(6).split("[-/]");long actual=Long.parseLong(bounds[1])-Long.parseLong(bounds[0])+1;if(actual>0)available=available<0?actual:Math.min(available,actual);}catch(NumberFormatException ignored){}
   else if(range!=null)try{long total=Long.parseLong(header(data,"Content-Length"));long actual=Math.max(0,total-start);available=available<0?actual:Math.min(available,actual);}catch(NumberFormatException ignored){}
   if(range!=null&&available==0){reply(out,416,"Range outside resource");return;}
   String extra="Content-Type: "+safeType(type,url)+"\r\nAccept-Ranges: bytes\r\n";
   if(available>=0)extra+="Content-Length: "+available+"\r\n";
   if(range!=null){String cr=contentRange;if(!cr.matches("bytes [0-9]+-[0-9]+/([0-9]+|\\*)")){if(available<0){reply(out,416,"Unknown resource length");return;}cr="bytes "+start+"-"+(start+available-1)+"/"+(length<0?Long.toString(start+available):"*");}extra+="Content-Range: "+cr+"\r\n";}
   headers(out,range==null?200:206,range==null?"OK":"Partial Content",extra);if(method.equals("HEAD"))return;
   byte[] buffer=new byte[64*1024];long remaining=available;while(!closed&&remaining!=0){int n=data.read(buffer,0,remaining<0?buffer.length:(int)Math.min(buffer.length,remaining));if(n<0)break;out.write(buffer,0,n);if(remaining>0)remaining-=n;lastRequest=android.os.SystemClock.elapsedRealtime();}
  }catch(IOException|RuntimeException e){if(!opened)reply(out,502,"Media unavailable");}finally{try{data.close();}catch(IOException ignored){}}
 }
 private synchronized String urlsRoot(){return urls.values().iterator().next();}
 static long[] parseRange(String value)throws IOException {
  if(value==null)return new long[]{0,-1};if(!value.matches("bytes=([0-9]+-[0-9]*|-[0-9]+)"))throw new IOException("Invalid Range");
  try{String[] parts=value.substring(6).split("-",-1);if(parts[0].isEmpty()){long suffix=Long.parseLong(parts[1]);if(suffix<=0)throw new NumberFormatException();return new long[]{-suffix,-1};}long start=Long.parseLong(parts[0]),end=parts[1].isEmpty()?-1:Long.parseLong(parts[1]);if(end>=0&&(end<start||end==Long.MAX_VALUE))throw new NumberFormatException();return new long[]{start,end<0?-1:end-start+1};}catch(NumberFormatException e){throw new IOException("Invalid Range");}
 }
 static String header(DataSource data,String name){for(Map.Entry<String,List<String>> h:data.getResponseHeaders().entrySet())if(name.equalsIgnoreCase(h.getKey())&&!h.getValue().isEmpty())return h.getValue().get(0);return "";}
 private static String safeType(String type,String url){if(type.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+(;[ A-Za-z0-9=._-]+)?"))return type;String path=URI.create(url).getPath();return path.endsWith(".mp4")?"video/mp4":path.endsWith(".ts")?"video/mp2t":"application/octet-stream";}
 private static byte[] readBounded(DataSource data,int limit)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=data.read(b,0,b.length))!=-1){if(out.size()+n>limit)throw new IOException("Playlist too large");out.write(b,0,n);}return out.toByteArray();}
 private static String line(InputStream in,int limit,long deadline)throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();for(int i=0;i<limit;i++){if(System.nanoTime()>deadline)throw new IOException("Request timed out");int v=in.read();if(v<0)throw new EOFException();if(v=='\n')return b.toString(StandardCharsets.US_ASCII.name()).replace("\r","");b.write(v);}throw new IOException("Line too long");}
 private static void headers(OutputStream out,int code,String reason,String extra)throws IOException {out.write(("HTTP/1.1 "+code+" "+reason+"\r\nConnection: close\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\nAccess-Control-Allow-Headers: Range\r\nAccess-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\nX-Content-Type-Options: nosniff\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));}
 private static void reply(OutputStream out,int code,String text)throws IOException {byte[] body=text.getBytes(StandardCharsets.UTF_8);headers(out,code,"Request failed","Content-Length: "+body.length+"\r\nContent-Type: text/plain\r\n");out.write(body);}
 private String page(){return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Velixio TV</title><style>body{background:#090d16;color:#f4f6f7;font:20px system-ui;margin:4vw}video{width:100%;max-height:80vh}button{padding:14px;border:0;border-radius:24px;background:#ff795f;font:inherit}p{color:#a5adb5}</style><h1>Velixio</h1><video id='v' controls playsinline src='"+root+"'></video><p id='s'>Press Play on your TV remote. Keep your phone connected to this network.</p><button onclick='v.requestFullscreen?v.requestFullscreen():v.webkitEnterFullscreen()'>Full screen</button><script>const v=document.getElementById('v');v.addEventListener('loadedmetadata',()=>{v.currentTime="+(startPosition/1000)+"},{once:true});v.addEventListener('error',()=>document.getElementById('s').textContent='This browser cannot play this format. Try Google Cast, a compatible DLNA receiver or screen mirroring.');</script>";}
 public void close(){if(closed)return;closed=true;try{server.close();}catch(IOException ignored){}for(Socket s:sockets)try{s.close();}catch(IOException ignored){}sockets.clear();workers.shutdownNow();accept.shutdownNow();synchronized(this){urls.clear();ids.clear();}}
}
