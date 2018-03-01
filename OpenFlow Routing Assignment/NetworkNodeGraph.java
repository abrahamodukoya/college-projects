import java.util.LinkedList;
/*
 * This class represents a graph of all of the routers in the network
 * The routers array uses the routers' id as the index. At each router's entry is a linked list of vertices
 * Each vertex represents a router's connection to another router
 * The ID is the id of the router this router is connected to
 * The outgoingPort in a Vertex is the port in the router represented by the index in the array
 * The incomingPort is the port of the router the original router is connected to
 */
public class NetworkNodeGraph 
{
	
	private LinkedList<Vertex>[] routers;
	/*
	 * edgesTo is a 2D array of dimensions [routers.length][routers.length]
	 * each row in edgesTo maintains an array that says the shortest distance from every other router to that router
	 * basically, use edgesTo[0], edgesTo[1], etc., as the edgeTo array for router 0, router 1, etc.  
	 */
	private int[][] edgesTo;
	private boolean[] bfsCalcd;
	
	public NetworkNodeGraph(int numOfRouters)
	{
		routers = (LinkedList<Vertex>[]) new LinkedList[numOfRouters];
		for(int index = 0; index < routers.length; index++)
		{
			routers[index] = new LinkedList<Vertex>();
		}
		edgesTo = new int[routers.length][routers.length];
		bfsCalcd = new boolean[routers.length];
	}
	
	/*
	 * Connections in the form "otherRouterID,outgoingPort,incomingPort;otherRouterID,outgoingPort,incomingPort"
	 */
	public void addRouter(int routerID, String connections)
	{
		String[] links = connections.split(";");
		for(String link:links)
		{
			String[] vertexInfo = link.split(",");
			routers[routerID].add(new Vertex(Integer.parseInt(vertexInfo[0]), Integer.parseInt(vertexInfo[1]), Integer.parseInt(vertexInfo[2])));
		}
	}
	
	/*
	 * This method finds the shortest path between every router
	 * have an edgeTo for each router that contains the shortest path from it to every other router
	 * 
	 */
	public void bfs(int startRouter)
	{
		boolean[] marked = new boolean[routers.length];
		edgesTo[startRouter] = new int[routers.length];
		
		LinkedList<Integer> queue = new LinkedList<Integer>();
		queue.add(startRouter);
		marked[startRouter] = true;
		while(!queue.isEmpty())
		{
			int routerID = queue.remove();			
			for(Vertex connRouter:routers[routerID])
			{
				if(!marked[connRouter.routerId])
				{
					queue.add(connRouter.routerId);
					edgesTo[startRouter][connRouter.routerId] = routerID;
					marked[connRouter.routerId] = true;
				}
			}
		}
		bfsCalcd[startRouter] = true;
	}
	
	/*
	 * this method uses the paths created with bfs to creating the routing info String for the Controller
	 * The String is of the form ",outgoingPort;RouterID,incomingPort,outgoingPort;RouterID,incomingPort,outgoingPort;RouterID,incomingPort"
	 * Based on the endpoints being used, the Controller adds on the startRouterID and incomingPort at the start of the string and the outgoingPort
	 * at the end of the String
	 */
	public String getRoutingInfo(int startRouter, int endRouter)
	{
		if(!bfsCalcd[startRouter])
		{
			bfs(startRouter);
		}
		int currRouterID = endRouter;
		int nextRouterID = -1;
		String info = "";
		
		String inPort = "";
		String outPort = "";

		nextRouterID = edgesTo[startRouter][currRouterID];
		
		while(currRouterID != startRouter)
		{
			inPort = "";
			outPort = "";
			for(Vertex conn:routers[edgesTo[startRouter][currRouterID]])
			{
				if(conn.routerId == currRouterID)
				{
					outPort += conn.outgoingPort;
					inPort += conn.incomingPort;
				}
			}
			
			info = "," + outPort + ";" + currRouterID + "," + inPort + info;
			
			currRouterID = nextRouterID;
			nextRouterID = edgesTo[startRouter][currRouterID];
		}
		
		return info;		
	}
	
	private class Vertex
	{
		int routerId, outgoingPort, incomingPort;
		
		Vertex(int id, int outPort, int inPort)
		{
			this.routerId = id;
			this.outgoingPort = outPort;
			this.incomingPort = inPort;
		}
	}
}
