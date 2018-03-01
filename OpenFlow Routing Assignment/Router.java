import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;

public class Router extends NetworkNode {
	
	private static byte id = 0;

	private final byte idNumber;
	private DatagramSocket controllerSocket;
	
	private static final int DELAY = 250;
	
	
	public Router(HashMap<Integer, SocketAddress> portConnections, HashMap<Integer, Integer> connectedRouterIDs, HashMap<Integer, Integer> connectedEndpointIDs, 
			int controllerListeningPort)
	{
		super(portConnections, connectedEndpointIDs, connectedEndpointIDs);
		this.idNumber = id++;
		try 
		{
			this.controllerSocket = new DatagramSocket(controllerListeningPort);
			
			byte[] controllerInfoData = new byte[PackageContent.RTOC_INFO_PACKET_MAX_LENGTH];
			controllerInfoData[PackageContent.HEADER_INDEX] = PackageContent.RTOC_INFO_PACKET_HEADER_VALUE;
			controllerInfoData[PackageContent.RTOC_INFO_PACKET_ROUTER_ID_INDEX] = this.idNumber;
			String endpointInfo = "";
			boolean isFirst = true;
			for(Integer port:connectedEndpointIDs.keySet())
			{
				if(isFirst)
				{
					isFirst = false;
				}
				else
				{
					endpointInfo += ";";
				}
				endpointInfo += connectedEndpointIDs.get(port) + "," + port;
			}
			char[] epInfoAsChar = endpointInfo.toCharArray();
			controllerInfoData[PackageContent.RTOC_INFO_PACKET_ENDPOINT_CONNS_DATA_LENGTH_INDEX] = (byte) epInfoAsChar.length;
			for(int index = 0; index < epInfoAsChar.length; index++)
			{
				controllerInfoData[PackageContent.RTOC_INFO_PACKET_ENDPOINT_CONNS_DATA_INDEX + index] = (byte) epInfoAsChar[index];
			}
			
			String routerInfo = "";
			isFirst = true;
			for(Integer port:connectedRouterIDs.keySet())
			{
				if(connectedEndpointIDs.containsKey(port))
				{
					continue;
				}
				
				if(isFirst)
				{
					isFirst = false;
				}
				else
				{
					routerInfo += ";";
				}
				InetSocketAddress nextRouterAdr = (InetSocketAddress) portConnections.get(port);
				routerInfo += connectedRouterIDs.get(port) + "," + port + "," + nextRouterAdr.getPort();
			}
			char[] rInfoAsChar = routerInfo.toCharArray();
			controllerInfoData[PackageContent.RTOC_INFO_PACKET_ENDPOINT_CONNS_DATA_INDEX + epInfoAsChar.length] = (byte) rInfoAsChar.length;
			for(int index = 0; index < rInfoAsChar.length; index++)
			{
				controllerInfoData[PackageContent.RTOC_INFO_PACKET_ENDPOINT_CONNS_DATA_INDEX + 1 + epInfoAsChar.length + index] = (byte) rInfoAsChar[index];
			}			
			
			DatagramPacket packet = new DatagramPacket(controllerInfoData, controllerInfoData.length);
			packet.setSocketAddress(new InetSocketAddress("localhost", Controller.CONTROLLER_PORT));
			controllerSocket.send(packet);
			System.out.println("Router " + this.idNumber + " online");
			
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					while(true)
					{
						try
						{
							DatagramPacket responsePacket = new DatagramPacket(new byte[PackageContent.RESPONSE_PACKET_LENGTH], PackageContent.RESPONSE_PACKET_LENGTH);
							controllerSocket.receive(responsePacket);
							byte[] responseInfo = responsePacket.getData();
							int inPort = responseInfo[PackageContent.RESPONSE_PACKET_IN_PORT_INDEX + 3];
							inPort &= 0x0000FF;
							inPort |= ((((int)responseInfo[PackageContent.RESPONSE_PACKET_IN_PORT_INDEX]) << 24) 
									| (((int)responseInfo[PackageContent.RESPONSE_PACKET_IN_PORT_INDEX + 1]) << 16) 
									| (((int)responseInfo[PackageContent.RESPONSE_PACKET_IN_PORT_INDEX + 2]) << 8));
							
							int outPort = responseInfo[PackageContent.RESPONSE_PACKET_OUT_PORT_INDEX + 3]; 
							outPort &= 0x000000FF;
							outPort |= ((((int)responseInfo[PackageContent.RESPONSE_PACKET_OUT_PORT_INDEX]) << 24) 
									| (((int)responseInfo[PackageContent.RESPONSE_PACKET_OUT_PORT_INDEX + 1]) << 16) 
									| (((int)responseInfo[PackageContent.RESPONSE_PACKET_OUT_PORT_INDEX + 2]) << 8));
							
							String routingInfoKey = Byte.toString(responseInfo[PackageContent.RESPONSE_PACKET_DEST_ENDPOINT_INDEX]);
							String routingInfo = "" + inPort + "," + outPort;
							putRoutingInfo(routingInfoKey, routingInfo);
						}
						catch(IOException e)
						{
							System.err.println("Unable to receive instructions from the Controller...");
						}
					}				
					
				}
			}).start();
		}
		catch (IOException e) 
		{
			System.err.println("Unable to send info to the Controller...");
		}
	}

	@Override
	public void onReceipt(DatagramPacket receivedPacket) 
	{
		if(receivedPacket != null && receivedPacket.getData() != null)
		{
			byte[] incomingData = receivedPacket.getData();
			String routingInfoKey = Byte.toString(incomingData[PackageContent.DATA_AND_REQUEST_DEST_ENDPOINT_INDEX]);
			/*
			 * check if the key is in the hashmap; if not get the info from the controller
			 * use the info to forward the packet
			 */
			String routingInfo = getRoutingInfo(routingInfoKey);
			if(routingInfo == null)
			{				
				DatagramPacket requestPacket = new DatagramPacket(incomingData, incomingData.length);
				requestPacket.setSocketAddress(new InetSocketAddress("localhost", Controller.CONTROLLER_PORT));
				try 
				{
					controllerSocket.send(requestPacket);
					while((routingInfo = getRoutingInfo(routingInfoKey)) == null)
					{
						Thread.sleep(DELAY);
					}
				}
				catch (IOException e) 
				{
					System.err.println("Unable to send request to the Controller...");
				} catch (InterruptedException e) {
					System.err.println("Unable to wait for a response from the Controller...");
				}
			}
			String[] inOut = routingInfo.split(",");
			int out = Integer.parseInt(inOut[1]);
			int in = Integer.parseInt(inOut[0]);
			
			String src = Byte.toString(incomingData[PackageContent.DATA_AND_REQUEST_SRC_ENDPOINT_INDEX]);;
			System.out.println("Router " + this.idNumber + " is sending a packet to EndPoint " + routingInfoKey + " from EndPoint " + src + 
					"; came in through port " + in + " and is leaving through port " + out);
			
			sendThroughPort(incomingData, out);
		}
		

	}

	public int getIdNumber() 
	{
		return idNumber;
	}
	
	public static void main(String[] args)
	{
		HashMap<Integer, SocketAddress> rZeroConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rOneConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rTwoConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rThreeConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rFourConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rFiveConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rSixConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rSevenConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rEightConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rNineConnections = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> rTenConnections = new HashMap<Integer, SocketAddress>();
		
		rZeroConnections.put(2000, new InetSocketAddress("localhost", 1025));//endPointZero
		rZeroConnections.put(2001, new InetSocketAddress("localhost", 2003));//routerOne
		rZeroConnections.put(2002, new InetSocketAddress("localhost", 2019));//routerEight
		
		rOneConnections.put(2003, new InetSocketAddress("localhost", 2001));//routerZero
		rOneConnections.put(2004, new InetSocketAddress("localhost", 2005));//routerTwo
		
		rTwoConnections.put(2005, new InetSocketAddress("localhost", 2004));//routerOne
		rTwoConnections.put(2006, new InetSocketAddress("localhost", 1026));//endPointOne
		
		rThreeConnections.put(2008, new InetSocketAddress("localhost", 2018));//routerEight
		rThreeConnections.put(2007, new InetSocketAddress("localhost", 2009));//routerFour
		
		rFourConnections.put(2009, new InetSocketAddress("localhost", 2007));//routerThree
		rFourConnections.put(2010, new InetSocketAddress("localhost", 1027));//endPointTwo
		rFourConnections.put(2011, new InetSocketAddress("localhost", 2012));//routerFive
		
		rFiveConnections.put(2012, new InetSocketAddress("localhost", 2011));//routerFour
		rFiveConnections.put(2025, new InetSocketAddress("localhost", 2015));//routerSeven
		
		rSixConnections.put(2013, new InetSocketAddress("localhost", 2016));//routerSeven
		rSixConnections.put(2014, new InetSocketAddress("localhost", 1028));//endPointThree
		
		rSevenConnections.put(2015, new InetSocketAddress("localhost", 2025));//routerFive
		rSevenConnections.put(2016, new InetSocketAddress("localhost", 2013));//routerSix
		rSevenConnections.put(2017, new InetSocketAddress("localhost", 1029));//endPointFour
		
		rEightConnections.put(2018, new InetSocketAddress("localhost", 2008));//routerThree
		rEightConnections.put(2019, new InetSocketAddress("localhost", 2002));//routerZero
		rEightConnections.put(2020, new InetSocketAddress("localhost", 2021));//routerNine
		
		rNineConnections.put(2021, new InetSocketAddress("localhost", 2020));//routerEight
		rNineConnections.put(2022, new InetSocketAddress("localhost", 2023));//routerTen
		
		rTenConnections.put(2023, new InetSocketAddress("localhost", 2022));//routerNine
		rTenConnections.put(2024, new InetSocketAddress("localhost", 1030));//endPointFive
		
		HashMap<Integer, Integer> rZeroRouterIDs = new HashMap<Integer, Integer>();
		rZeroRouterIDs.put(2001, 1);//routerOne
		rZeroRouterIDs.put(2002, 8);//routerEight
		HashMap<Integer, Integer> rZeroEndpointIDs = new HashMap<Integer, Integer>();		
		rZeroEndpointIDs.put(2000, 0);//endPointZero
		
		HashMap<Integer, Integer> rOneRouterIDs = new HashMap<Integer, Integer>();
		rOneRouterIDs.put(2003, 0);//routerZero
		rOneRouterIDs.put(2004, 2);//routerTwo
		HashMap<Integer, Integer> rOneEndpointIDs = new HashMap<Integer, Integer>();		
		
		HashMap<Integer, Integer> rTwoRouterIDs = new HashMap<Integer, Integer>();
		rTwoRouterIDs.put(2005, 1);//routerOne
		HashMap<Integer, Integer> rTwoEndpointIDs = new HashMap<Integer, Integer>();
		rTwoEndpointIDs.put(2006, 1);//endPointOne
		
		HashMap<Integer, Integer> rThreeRouterIDs = new HashMap<Integer, Integer>();
		rThreeRouterIDs.put(2007, 4);//routerFour
		rThreeRouterIDs.put(2008, 8);//routerEight
		HashMap<Integer, Integer> rThreeEndpointIDs = new HashMap<Integer, Integer>();
		
		HashMap<Integer, Integer> rFourRouterIDs = new HashMap<Integer, Integer>();
		rFourRouterIDs.put(2009, 3);//routerThree
		rFourRouterIDs.put(2011, 5);//routerFive
		HashMap<Integer, Integer> rFourEndpointIDs= new HashMap<Integer, Integer>();
		rFourEndpointIDs.put(2010, 2);//endPointTwo
		
		HashMap<Integer, Integer> rFiveRouterIDs = new HashMap<Integer, Integer>();
		rFiveRouterIDs.put(2012, 4);//routerFour
		rFiveRouterIDs.put(2025, 7);//routerSeven
		HashMap<Integer, Integer> rFiveEndpointIDs = new HashMap<Integer, Integer>();
		
		HashMap<Integer, Integer> rSixRouterIDs = new HashMap<Integer, Integer>();
		rSixRouterIDs.put(2013, 7);//routerSeven
		HashMap<Integer, Integer> rSixEndpointIDs = new HashMap<Integer, Integer>();
		rSixEndpointIDs.put(2014, 3);//endPointThree
		
		HashMap<Integer, Integer> rSevenRouterIDs = new HashMap<Integer, Integer>();
		rSevenRouterIDs.put(2015, 5);//routerFive
		rSevenRouterIDs.put(2016, 6);//routerSix
		HashMap<Integer, Integer> rSevenEndpointIDs = new HashMap<Integer, Integer>();
		rSevenEndpointIDs.put(2017, 4);//endPointFour
		
		HashMap<Integer, Integer> rEightRouterIDs = new HashMap<Integer, Integer>();
		rEightRouterIDs.put(2018, 3);//endPointThree
		rEightRouterIDs.put(2019, 0);//routerZero
		rEightRouterIDs.put(2020, 9);//routerNine
		HashMap<Integer, Integer> rEightEndpointIDs = new HashMap<Integer, Integer>();
		
		HashMap<Integer, Integer> rNineRouterIDs = new HashMap<Integer, Integer>();
		rNineRouterIDs.put(2021, 8);//routerEight
		rNineRouterIDs.put(2022, 10);//routerTen
		HashMap<Integer, Integer> rNineEndpointIDs = new HashMap<Integer, Integer>();
		
		
		HashMap<Integer, Integer> rTenRouterIDs = new HashMap<Integer, Integer>();
		rTenRouterIDs.put(2023, 9);//routerNine
		HashMap<Integer, Integer> rTenEndpointIDs = new HashMap<Integer, Integer>();
		rTenEndpointIDs.put(2024, 5);//endPointFive
		
		Router routerZero = new Router(rZeroConnections, rZeroRouterIDs, rZeroEndpointIDs, 50000);
		Router routerOne = new Router(rOneConnections, rOneRouterIDs, rOneEndpointIDs, 50001);
		Router routerTwo = new Router(rTwoConnections, rTwoRouterIDs, rTwoEndpointIDs, 50002);
		Router routerThree = new Router(rThreeConnections, rThreeRouterIDs, rThreeEndpointIDs, 50003);
		Router routerFour = new Router(rFourConnections, rFourRouterIDs, rFourEndpointIDs, 50004);
		Router routerFive = new Router(rFiveConnections, rFiveRouterIDs, rFiveEndpointIDs, 50005);
		Router routerSix = new Router(rSixConnections, rSixRouterIDs, rSixEndpointIDs, 50006);
		Router routerSeven = new Router(rSevenConnections, rSevenRouterIDs, rSevenEndpointIDs, 50007);
		Router routerEight = new Router(rEightConnections, rEightRouterIDs, rEightEndpointIDs, 50008);
		Router routerNine = new Router(rNineConnections, rNineRouterIDs, rNineEndpointIDs, 50009);
		Router routerTen = new Router(rTenConnections, rTenRouterIDs, rTenEndpointIDs, 50010);
		
	}

}
