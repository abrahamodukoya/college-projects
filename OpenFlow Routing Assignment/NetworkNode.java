import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public abstract class NetworkNode 
{
	
	public static final int EXTRACT_DEST = 0;
	public static final int EXTRACT_SOURCE = 1;
	
	private HashMap<String, String> routingInfo;
	private HashMap<Integer, SocketAddress> portConnections;
	private ArrayList<DatagramSocket> sockets;
	
	//these HashMaps associate ports on this NetworkNode with the ID of the NetworkNode they're connected to  
	private HashMap<Integer, Integer> connectedRouterIDs, connectedEndpointIDs;
	
	public NetworkNode(HashMap<Integer, SocketAddress> portConnections, HashMap<Integer, Integer> connectedRouterIDs, HashMap<Integer, Integer> connectedEndpointIDs) 
			throws IllegalArgumentException
	{
		if(portConnections == null || connectedRouterIDs == null || connectedEndpointIDs == null)
		{
			throw new IllegalArgumentException("Port connections cannot be null");
		}
		routingInfo = new HashMap<String, String>();
		sockets = new ArrayList<DatagramSocket>();
		this.portConnections = portConnections;
		this.connectedRouterIDs = connectedRouterIDs;
		this.connectedEndpointIDs = connectedEndpointIDs;
		Set<Integer>ports = this.portConnections.keySet();
		for(int port:ports)
		{
			try 
			{
				DatagramSocket sock = new DatagramSocket(port);
				sockets.add(sock);
				new Thread(new Listener(sock)).start();
			}
			catch (SocketException e) 
			{
				System.err.println("Unable to create a socket bound to port " + port);
			}
		}
	}
	
	public void sendThroughPort(byte[] data, int port)
	{
		for(DatagramSocket socket:sockets)
		{
			if(socket.getLocalPort() == port)
			{
				DatagramPacket packet = new DatagramPacket(data, data.length);
				try 
				{
					packet.setSocketAddress(portConnections.get(port));
					socket.send(packet);
				} 
				catch (IOException e) 
				{
					System.err.println("Unable to send packet...");
				}
				break;
			}
		}
	}

	public String getRoutingInfo(String key)
	{
		return routingInfo.get(key);
	}
	
	public void putRoutingInfo(String key, String value)
	{
		routingInfo.put(key, value);
	}
	
	public abstract void onReceipt(DatagramPacket receivedPacket);
	
	class Listener implements Runnable
	{
		
		DatagramSocket socket;

		Listener(DatagramSocket socket)
		{
			this.socket = socket;
		}
		
		@Override
		public void run() 
		{
			while(true)
			{
				try 
				{
					byte[] buffer = new byte[PackageContent.DATA_AND_REQUEST_PACKET_LENGTH];
					DatagramPacket incomingPacket = new DatagramPacket(buffer, buffer.length);
					socket.receive(incomingPacket);
					onReceipt(incomingPacket);
				}
				catch (Exception e) 
				{
					System.err.println("Unable to listen for incoming data");
				}		
			}
		}
		
	}
}