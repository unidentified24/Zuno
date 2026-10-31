package app.veyra;
import android.content.Context;
import android.net.wifi.WifiManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** UPnP AVTransport only. No model-name guesses, vendor logins, arbitrary deep links or Roku ECP. */
final class DlnaReceiver {
 final String name,service;final URI control;final CastNetwork network;
 DlnaReceiver(String n,String s,URI u,CastNetwork lan){name=n;service=s;control=u;network=lan;}
 static List<DlnaReceiver> discover(Context c,CastNetwork network)throws Exception {
  WifiManager wm=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);WifiManager.MulticastLock lock=wm==null?null:wm.createMulticastLock("Velixio receiver discovery");
  Map<String,URI> descriptions=new LinkedHashMap<>();
  try {if(lock!=null)lock.acquire();try(DatagramSocket socket=new DatagramSocket(null)){socket.bind(new InetSocketAddress(network.address,0));network.network.bindSocket(socket);socket.setSoTimeout(400);
   byte[] query=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n").getBytes(StandardCharsets.US_ASCII);socket.send(new DatagramPacket(query,query.length,InetAddress.getByName("239.255.255.250"),1900));
   long end=android.os.SystemClock.elapsedRealtime()+4500;byte[] buffer=new byte[8192];while(android.os.SystemClock.elapsedRealtime()<end&&!Thread.currentThread().isInterrupted()&&descriptions.size()<16){DatagramPacket packet=new DatagramPacket(buffer,buffer.length);try{socket.receive(packet);if(!network.contains(packet.getAddress())||packet.getLength()==buffer.length)continue;URI location=location(new String(buffer,0,packet.getLength(),StandardCharsets.US_ASCII),packet.getAddress(),network);if(location!=null)descriptions.put(location.toString(),location);}catch(SocketTimeoutException ignored){}}
  }}finally{if(lock!=null&&lock.isHeld())lock.release();}
  List<DlnaReceiver> found=new ArrayList<>();Set<String> controls=new HashSet<>();for(URI description:descriptions.values()){if(Thread.currentThread().isInterrupted())break;try{DlnaReceiver receiver=parse(request(network,description,"GET",null,null),description,network);if(receiver!=null&&controls.add(receiver.control.toString()))found.add(receiver);}catch(Exception ignored){}}
  return found;
 }
 static URI location(String response,InetAddress peer,CastNetwork lan){try{if(!response.startsWith("HTTP/1.1 200"))return null;for(String line:response.split("\\r?\\n"))if(line.regionMatches(true,0,"location:",0,9)){URI u=URI.create(line.substring(9).trim());check(lan,u);if(!u.getHost().equals(peer.getHostAddress()))return null;return u;}}catch(Exception ignored){}return null;}
 static DlnaReceiver parse(byte[] xml,URI description,CastNetwork lan)throws Exception {
  DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
  Document doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));String name=text(doc.getDocumentElement(),"friendlyName");if(name.isEmpty())name="DLNA receiver";if(name.length()>100)name=name.substring(0,100);
  URI base=description;String urlBase=text(doc.getDocumentElement(),"URLBase");if(!urlBase.isEmpty())base=description.resolve(urlBase);check(lan,base);if(!base.getHost().equals(description.getHost()))throw new IOException("Receiver host changed");
  NodeList services=doc.getElementsByTagName("service");for(int i=0;i<services.getLength();i++){Element e=(Element)services.item(i);String type=text(e,"serviceType");if(!type.matches("urn:schemas-upnp-org:service:AVTransport:[1-3]"))continue;URI control=base.resolve(text(e,"controlURL"));check(lan,control);if(!control.getHost().equals(description.getHost()))throw new IOException("Receiver host changed");return new DlnaReceiver(name,type,control,lan);}return null;
 }
 private static String text(Element e,String tag){NodeList nodes=e.getElementsByTagName(tag);return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();}
 void load(String url,String title,String mime,long position)throws Exception {
  String metadata="<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>"+escape(title)+"</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo=\"http-get:*:"+escape(mime)+":*\">"+escape(url)+"</res></item></DIDL-Lite>";
  action("SetAVTransportURI","<CurrentURI>"+escape(url)+"</CurrentURI><CurrentURIMetaData>"+escape(metadata)+"</CurrentURIMetaData>");play();if(position>0)try{seek(position);}catch(Exception ignored){/* Seek is optional on receivers; playback has started. */}
 }
 void play()throws Exception{action("Play","<Speed>1</Speed>");}void pause()throws Exception{action("Pause","");}void stop()throws Exception{action("Stop","");}
 void seek(long millis)throws Exception {long seconds=Math.max(0,millis/1000);action("Seek","<Unit>REL_TIME</Unit><Target>"+String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60)+"</Target>");}
 private void action(String name,String arguments)throws Exception {String body="<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:"+name+" xmlns:u=\""+service+"\"><InstanceID>0</InstanceID>"+arguments+"</u:"+name+"></s:Body></s:Envelope>";request(network,control,"POST",service+"#"+name,body.getBytes(StandardCharsets.UTF_8));}
 static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
 static void check(CastNetwork lan,URI u)throws IOException {if(!"http".equals(u.getScheme())||u.getRawUserInfo()!=null||u.getRawFragment()!=null||u.getPort()==0||u.getPort()>65535)throw new IOException("Unsafe receiver endpoint");lan.literal(u.getHost());}
 // Narrow HTTP exception for a selected LAN receiver. The app's global TLS/cleartext policy stays strict.
 static byte[] request(CastNetwork lan,URI uri,String method,String soap,byte[] body)throws IOException {
  check(lan,uri);try(Socket socket=new Socket()){lan.network.bindSocket(socket);socket.connect(new InetSocketAddress(lan.literal(uri.getHost()),uri.getPort()<0?80:uri.getPort()),3000);socket.setSoTimeout(4000);String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();if(path.length()>4096)throw new IOException("Receiver path too long");
   String headers=method+" "+path+" HTTP/1.1\r\nHost: "+uri.getRawAuthority()+"\r\nConnection: close\r\n";if(body!=null)headers+="Content-Type: text/xml; charset=utf-8\r\nSOAPAction: \""+soap+"\"\r\nContent-Length: "+body.length+"\r\n";OutputStream out=socket.getOutputStream();out.write((headers+"\r\n").getBytes(StandardCharsets.US_ASCII));if(body!=null)out.write(body);out.flush();
   InputStream in=socket.getInputStream();String first=line(in);if(!first.matches("HTTP/1\\.[01] 2[0-9]{2}.*"))throw new IOException("The receiver rejected this action or format.");int length=-1,total=first.length();boolean chunked=false;
   for(int i=0;i<64;i++){String h=line(in);total+=h.length();if(total>16384)throw new IOException("Receiver headers too large");if(h.isEmpty())break;if(i==63)throw new IOException("Receiver headers too large");if(h.regionMatches(true,0,"content-length:",0,15))try{length=Integer.parseInt(h.substring(15).trim());}catch(NumberFormatException e){throw new IOException("Invalid receiver response");}if(h.equalsIgnoreCase("transfer-encoding: chunked"))chunked=true;}
   if(length>1024*1024)throw new IOException("Receiver response too large");ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(chunked){while(true){String size=line(in).split(";",2)[0];int chunk;try{chunk=Integer.parseInt(size,16);}catch(NumberFormatException e){throw new IOException("Invalid chunk");}if(chunk==0)break;if(chunk<0||bytes.size()+chunk>1024*1024)throw new IOException("Receiver response too large");copy(in,bytes,chunk);if(!line(in).isEmpty())throw new IOException("Invalid chunk boundary");}}else if(length>=0)copy(in,bytes,length);else{byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("Receiver response too large");bytes.write(b,0,n);}}return bytes.toByteArray();
  }
 }
 private static void copy(InputStream in,OutputStream out,int count)throws IOException{byte[] b=new byte[4096];while(count>0){int n=in.read(b,0,Math.min(count,b.length));if(n<0)throw new EOFException();out.write(b,0,n);count-=n;}}
 private static String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int i=0;i<4096;i++){int v=in.read();if(v<0)throw new EOFException();if(v=='\n')return out.toString(StandardCharsets.US_ASCII.name()).replace("\r","");out.write(v);}throw new IOException("Receiver line too long");}
}
