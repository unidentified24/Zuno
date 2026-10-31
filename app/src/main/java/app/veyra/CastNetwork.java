package app.veyra;
import android.content.Context;
import android.net.*;
import java.net.*;
import java.io.IOException;

/** Select one Wi-Fi/Ethernet LAN; never bind a stream to every interface. */
final class CastNetwork {
 final Network network; final Inet4Address address; final int prefix;
 CastNetwork(Network n,Inet4Address a,int p){network=n;address=a;prefix=p;}
 static CastNetwork find(Context c)throws IOException {
  ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
  for(Network n:cm.getAllNetworks()){
   NetworkCapabilities caps=cm.getNetworkCapabilities(n);LinkProperties lp=cm.getLinkProperties(n);
   if(caps==null||lp==null||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)||!(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))continue;
   for(LinkAddress link:lp.getLinkAddresses())if(link.getAddress() instanceof Inet4Address&&privateAddress(link.getAddress())&&link.getPrefixLength()>=8&&link.getPrefixLength()<=30)return new CastNetwork(n,(Inet4Address)link.getAddress(),link.getPrefixLength());
  }
  throw new IOException("Connect this device and your TV to the same Wi-Fi or Ethernet network.");
 }
 static boolean privateAddress(InetAddress a){byte[] b=a.getAddress();return b.length==4&&((b[0]&255)==10||((b[0]&255)==172&&(b[1]&255)>=16&&(b[1]&255)<=31)||((b[0]&255)==192&&(b[1]&255)==168));}
 boolean contains(InetAddress other){byte[] a=address.getAddress(),b=other.getAddress();if(b.length!=4||!privateAddress(other))return false;for(int bit=0;bit<prefix;bit++)if(((a[bit/8]^b[bit/8])&(1<<(7-bit%8)))!=0)return false;return true;}
 InetAddress literal(String host)throws IOException {
  if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IOException("The receiver must use an IPv4 address on this network.");
  InetAddress a=InetAddress.getByName(host);if(!contains(a)||a.equals(address))throw new IOException("The receiver is outside this network.");return a;
 }
 boolean available(Context c){LinkProperties lp=((ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE)).getLinkProperties(network);if(lp==null)return false;for(LinkAddress l:lp.getLinkAddresses())if(l.getAddress().equals(address))return true;return false;}
}
