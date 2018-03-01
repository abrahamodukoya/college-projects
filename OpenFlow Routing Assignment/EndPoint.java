import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class EndPoint extends NetworkNode {

	private static byte id = 0;
	private final byte idNumber;
	private int port;
	
	private JFrame windowFrame;
	private JButton sendButton;
	private JComboBox<Byte> endPointChoicesComboBox;
	private JTextArea receivedTextArea;
	
	public EndPoint(HashMap<Integer, SocketAddress> portConnections, HashMap<Integer, Integer> connectedRouterIDs, HashMap<Integer, Integer> connectedEndpointIDs, int port) 
			throws IllegalArgumentException 
	{
		super(portConnections, connectedEndpointIDs, connectedEndpointIDs);
		this.idNumber = id++;
		this.port = port;
		launchGUI();
	}

	@Override
	public void onReceipt(DatagramPacket receivedPacket) 
	{
		byte[] data = receivedPacket.getData();
		receivedTextArea.append("Received a packet from EndPoint " + data[PackageContent.DATA_AND_REQUEST_SRC_ENDPOINT_INDEX] + "\n");
	}
	
	public void send(byte[] data)
	{
		sendThroughPort(data, this.port);
	}
	
	public byte getIdNumber() 
	{
		return idNumber;
	}
	
	public void launchGUI()
	{
		windowFrame = new JFrame("EndPoint " + this.idNumber);
		windowFrame.setSize(200, 100);
		
		sendButton = new JButton("Send a packet to EndPoint: ");
		Byte[] choices = {0, 1, 2, 3, 4, 5};
		endPointChoicesComboBox = new JComboBox<Byte>(choices);
		receivedTextArea = new JTextArea(20, 20);
		receivedTextArea.setEditable(false);
		receivedTextArea.setLineWrap(true);
		JScrollPane textAreaScrollPane = new JScrollPane(receivedTextArea);
		
		sendButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				byte destID = choices[endPointChoicesComboBox.getSelectedIndex()];

				byte[] data = new byte[4];
				data[PackageContent.HEADER_INDEX] = 0;
				data[PackageContent.DATA_AND_REQUEST_DEST_ENDPOINT_INDEX] = destID;
				data[PackageContent.DATA_AND_REQUEST_SRC_ENDPOINT_INDEX] = getIdNumber();
				data[PackageContent.DATA_AND_REQUEST_DATA_AND_REQUEST_FOOTER_INDEX] = PackageContent.FOOTER_VALUE;
				send(data);				
			}
		});
		
		windowFrame.add(sendButton, BorderLayout.WEST);
		windowFrame.add(endPointChoicesComboBox, BorderLayout.EAST);
		windowFrame.add(textAreaScrollPane, BorderLayout.SOUTH);
		windowFrame.pack();
		windowFrame.setResizable(false);
		windowFrame.setVisible(true);
	}

	public static void main(String[] args)
	{		
		HashMap<Integer, SocketAddress> endZeroConns = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> endOneConns = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> endTwoConns = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> endThreeConns = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> endFourConns = new HashMap<Integer, SocketAddress>();
		HashMap<Integer, SocketAddress> endFiveConns = new HashMap<Integer, SocketAddress>();
		
		endZeroConns.put(1025, new InetSocketAddress("localhost", 2000));
		endOneConns.put(1026, new InetSocketAddress("localhost", 2006));
		endTwoConns.put(1027, new InetSocketAddress("localhost", 2010));
		endThreeConns.put(1028, new InetSocketAddress("localhost", 2014));
		endFourConns.put(1029, new InetSocketAddress("localhost", 2017));
		endFiveConns.put(1030, new InetSocketAddress("localhost", 2024));
		
		
		HashMap<Integer, Integer> endZeroConnectedRouterIDs = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> endZeroConnectedEndpointIDs = new HashMap<Integer, Integer>();
		
		HashMap<Integer, Integer> endOneConnectedRouterIDs = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> endOneConnectedEndpointIDs = new HashMap<Integer, Integer>();
		
		HashMap<Integer, Integer> endTwoConnectedRouterIDs = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> endTwoConnectedEndpointIDs = new HashMap<Integer, Integer>();
		
		HashMap<Integer, Integer> endThreeConnectedRouterIDs = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> endThreeConnectedEndpointIDs = new HashMap<Integer, Integer>();
		
		HashMap<Integer, Integer> endFourConnectedRouterIDs = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> endFourConnectedEndpointIDs = new HashMap<Integer, Integer>();
		
		HashMap<Integer, Integer> endFiveConnectedRouterIDs = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> endFiveConnectedEndpointIDs = new HashMap<Integer, Integer>();
		
		endZeroConnectedRouterIDs.put(1025, 0);		
		endOneConnectedRouterIDs.put(1026, 2);
		endTwoConnectedRouterIDs.put(1027, 4);
		endThreeConnectedRouterIDs.put(1028, 6);
		endFourConnectedRouterIDs.put(1029, 7);
		endFiveConnectedRouterIDs.put(1030, 10);
		
		EndPoint endZero = new EndPoint(endZeroConns, endZeroConnectedRouterIDs, endZeroConnectedEndpointIDs, 1025);
		EndPoint endOne = new EndPoint(endOneConns, endOneConnectedRouterIDs, endOneConnectedEndpointIDs, 1026);
		EndPoint endTwo = new EndPoint(endTwoConns, endTwoConnectedRouterIDs, endTwoConnectedEndpointIDs, 1027);
		EndPoint endThree = new EndPoint(endThreeConns, endThreeConnectedRouterIDs, endThreeConnectedEndpointIDs, 1028);
		EndPoint endFour = new EndPoint(endFourConns, endFourConnectedRouterIDs, endFourConnectedEndpointIDs, 1029);
		EndPoint endFive = new EndPoint(endFiveConns, endFiveConnectedRouterIDs, endFiveConnectedEndpointIDs, 1030);
	}

}
