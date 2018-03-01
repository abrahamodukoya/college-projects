import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.HashMap;

public class Controller 
{
	public static final int CONTROLLER_PORT = 60000;
	private static final HashMap<Integer, SocketAddress> NETWORK_ROUTER_ADDRESSES = new HashMap<Integer, SocketAddress>();
	
	static
	{
		NETWORK_ROUTER_ADDRESSES.put(0, new InetSocketAddress("localhost", 50000));
		NETWORK_ROUTER_ADDRESSES.put(1, new InetSocketAddress("localhost", 50001));
		NETWORK_ROUTER_ADDRESSES.put(2, new InetSocketAddress("localhost", 50002));
		NETWORK_ROUTER_ADDRESSES.put(3, new InetSocketAddress("localhost", 50003));
		NETWORK_ROUTER_ADDRESSES.put(4, new InetSocketAddress("localhost", 50004));
		NETWORK_ROUTER_ADDRESSES.put(5, new InetSocketAddress("localhost", 50005));
		NETWORK_ROUTER_ADDRESSES.put(6, new InetSocketAddress("localhost", 50006));
		NETWORK_ROUTER_ADDRESSES.put(7, new InetSocketAddress("localhost", 50007));
		NETWORK_ROUTER_ADDRESSES.put(8, new InetSocketAddress("localhost", 50008));
		NETWORK_ROUTER_ADDRESSES.put(9, new InetSocketAddress("localhost", 50009));
		NETWORK_ROUTER_ADDRESSES.put(10, new InetSocketAddress("localhost", 50010));
	}
	
	public static byte[] getPortNum(String portString)
	{
		int portInt = Integer.parseInt(portString);
		byte[] portArray = new byte[4];
		portArray[0] = (byte) ((portInt & 0xFF000000) >> 24);
		portArray[1] = (byte) ((portInt & 0x00FF0000) >> 16);
		portArray[2] = (byte) ((portInt & 0x0000FF00) >> 8);
		portArray[3] = (byte) (portInt & 0xFF);
		return portArray;
	}
	
	private HashMap<Integer, SocketAddress> routerAddresses; //key is the router's id
	private HashMap<String, String> routingInfo;
	private DatagramSocket routerConnectedSocket;
	private NetworkNodeGraph networkGraph;
	private int numOfRoutersExpected;
	private int numOfOnlineRouters;
	/*
	 * the array indices are the endpoints IDs; the values are the port numbers on the router connected to the endpoints
	 */
	private int endpointsAndRouterPorts[];
	private int endpointsAndRouterIDs[];
	
	public Controller(HashMap<String, String> routingInfo, HashMap<Integer, SocketAddress> routerAddresses, int numOfRouters, int numOfEndpoints)
	{
		this.routingInfo = routingInfo;
		this.routerAddresses = routerAddresses;
		this.networkGraph = new NetworkNodeGraph(numOfRouters);
		this.endpointsAndRouterPorts = new int[numOfEndpoints];
		this.endpointsAndRouterIDs = new int[numOfEndpoints];
		this.numOfRoutersExpected = numOfRouters;
		this.numOfOnlineRouters = 0;
		try 
		{
			routerConnectedSocket = new DatagramSocket(CONTROLLER_PORT);
		}
		catch (SocketException e) 
		{
			System.err.println("Unable to create a socket to communication with the routers...");
		}
	}
	
	public void start()
	{
		while(true)
		{
			DatagramPacket requestPacket = new DatagramPacket(new byte[515], 515);
			try 
			{
				routerConnectedSocket.receive(requestPacket);
				if(requestPacket.getData()[PackageContent.HEADER_INDEX] == PackageContent.DATA_AND_REQUEST_PACKET_HEADER_VALUE)
				{
					new Thread(new InfoRequestProcesser(requestPacket)).start();
				}
				else if(requestPacket.getData()[PackageContent.HEADER_INDEX] == PackageContent.RTOC_INFO_PACKET_HEADER_VALUE)
				{
					new Thread(new TableCreator(requestPacket)).start();
				}
			}
			catch (IOException e) 
			{
				System.err.println("Unable to receive packets from the routers...");
			}
		}
	}
	
	class InfoRequestProcesser implements Runnable
	{

		DatagramPacket requestPacket;
		
		public InfoRequestProcesser(DatagramPacket requestPacket) 
		{
			this.requestPacket = requestPacket;
		}
		
		//sends the info to the routers
		@Override
		public void run() 
		{
			byte[] requestData = requestPacket.getData();
			byte dest = requestData[PackageContent.DATA_AND_REQUEST_DEST_ENDPOINT_INDEX];
			byte src = requestData[PackageContent.DATA_AND_REQUEST_SRC_ENDPOINT_INDEX];
			String infoKey = Byte.toString(dest) + "," + Byte.toString(src);
			String route = routingInfo.get(infoKey);
			//if the route isn't in the map, it is built using the graph
			if(route == null)
			{
				route = Integer.toString(endpointsAndRouterIDs[src]) + "," + Integer.toString(endpointsAndRouterPorts[src]) 
					+ networkGraph.getRoutingInfo(endpointsAndRouterIDs[src], endpointsAndRouterIDs[dest]) + "," + endpointsAndRouterPorts[dest];
				routingInfo.put(infoKey, route);
			}
			
			System.out.println("dest,src => route: " + infoKey + " => " + route);
			
			String[] routersWithPortInfo = route.split(";");
			for(String routerInfo:routersWithPortInfo)
			{
				String[] info = routerInfo.split(",");
				int routerID = Integer.parseInt(info[0]);
				SocketAddress routerAdr = routerAddresses.get(routerID);

				String in = info[1];
				String out = info[2];
				byte[] inPort = getPortNum(in);
				byte[] outPort = getPortNum(out);
				
				byte[] responseData = new byte[PackageContent.RESPONSE_PACKET_LENGTH];
				responseData[PackageContent.HEADER_INDEX] = 1;
				for(int index = 0; index < PackageContent.RESPONSE_PACKET_IN_PORT_LENGTH; index++)
				{
					responseData[index + PackageContent.RESPONSE_PACKET_IN_PORT_INDEX] = inPort[index];
				}
				
				for(int index = 0; index < PackageContent.RESPONSE_PACKET_OUT_PORT_LENGTH; index++)
				{
					responseData[index + PackageContent.RESPONSE_PACKET_OUT_PORT_INDEX] = outPort[index];
				}
				
				responseData[PackageContent.RESPONSE_PACKET_DEST_ENDPOINT_INDEX] = dest;
				responseData[PackageContent.RESPONSE_PACKET_FOOTER_INDEX] = PackageContent.FOOTER_VALUE;
				
				DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length);
				responsePacket.setSocketAddress(routerAdr);
				try 
				{
					routerConnectedSocket.send(responsePacket);
				} 
				catch (IOException e) 
				{
					System.err.println("Unable to send routing info to the routers...");
				}
			}
		}
		
	}
	
	class TableCreator implements Runnable
	{

		DatagramPacket requestPacket;
		
		public TableCreator(DatagramPacket requestPacket) 
		{
			this.requestPacket = requestPacket;
		}
		
		@Override
		public void run() 
		{
			byte[] configData = requestPacket.getData();
			int routerID = configData[PackageContent.RTOC_INFO_PACKET_ROUTER_ID_INDEX];
			int endpointDataLength = configData[PackageContent.RTOC_INFO_PACKET_ENDPOINT_CONNS_DATA_LENGTH_INDEX];
			char[] endpointData = new char[endpointDataLength];
			for(int index = 0; index < endpointDataLength; index++)
			{
				endpointData[index] = (char) configData[PackageContent.RTOC_INFO_PACKET_ENDPOINT_CONNS_DATA_INDEX + index];
			}
			String endpointConns = String.copyValueOf(endpointData);
			if(endpointConns.length() > 0)
			{
				String[] endpoints = endpointConns.split(";");
				for(String units:endpoints)
				{
					String[] unitData = units.split(",");
					int endpointID = Integer.parseInt(unitData[0]);
					int portNum = Integer.parseInt(unitData[1]);
					endpointsAndRouterPorts[endpointID] = portNum;
					endpointsAndRouterIDs[endpointID] = routerID;
				}
			}
			
			int routerDataLength = configData[3 + endpointDataLength];
			char[] routerData = new char[routerDataLength];
			for(int index = 0; index < routerDataLength; index++)
			{
				routerData[index] = (char) configData[4 + endpointDataLength + index];
			}
			String connections = String.copyValueOf(routerData);
			networkGraph.addRouter(routerID, connections);	
			
			Controller.this.numOfOnlineRouters++;
			if(Controller.this.numOfOnlineRouters == Controller.this.numOfRoutersExpected)
			{
				System.out.println("All routers online; endpoints can be launched now"); 
			}
		}
		
	}
	
	public static void main(String[] args)
	{
		Controller testController = new Controller(new HashMap<String, String>(), NETWORK_ROUTER_ADDRESSES, 11, 6);
		testController.start();
	}
}
