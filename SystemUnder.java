import java.io.*;
import java.net.*;
import com.sun.nio.sctp.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.*;

class SctpServer implements Runnable
{
	int serverPortNumber;						//Port Number on which the Node will be Listening to accept connection
	int myNodeID;								//NodeID of the node
	int waitingForResponse;						//Variable to prevent the node from sending extra NEB(neighbour) request
	int parentNode;								//Parent node in the Spanning Tree
	int totalNodes=0;							//Variable to hold the number of nodes in the Graph
	int countNodes=0;							//Variable used to count the number of nodes who found their neighbours during Spanning Tree formation
	
	Boolean startingNode;						//This variable is true if current node is the designated node to start building spanning tree
	Boolean allResponse;
	

	ArrayList<Integer> neighbourNodes=new ArrayList<Integer>();						//List to hold the neighbours information of a node and its duplicate for program logic
	ArrayList<Integer> duplicateNeighbourList=new ArrayList<Integer>();
	
	ArrayList<Integer> treeNeighbours=new ArrayList<Integer>();						//List of nodes after Spanning Tree is formed and its duplicate for logic

	Map<Integer,ArrayList<Object>> mNodes=new HashMap<Integer,ArrayList<Object>>();	//Map for storing the Server Name and port number of nodes


	Semaphore semaphore = new Semaphore(1,true); // Fair Semaphore to make sure things happend in order
	Semaphore sem_treedone = new Semaphore(1,true);	

	 //** (Raymonds) **
	 
	 // if I don't have the token who has it
	 //HOLDER as described in [1]
	 volatile int token_parent;	
	 // is the token currently in use 
	 //USING as in [1]
	 volatile boolean token_busy; 
	// Queue of the requests for token
	//REQUEST_Q as in [1]
	 volatile Queue<Integer> que = new ConcurrentLinkedQueue<Integer>();
	 // Has a request been made already
	 //ASKED as in [1]
	 volatile boolean token_req_done;  
	 
	 //** (Raymonds) **
		
	// does this node have the token							
	 volatile boolean token_present;  
	 volatile boolean treeDone;		// is spanning tree formed
	 volatile boolean svr_lock;
	 volatile int[] vector;

	//Singleton Object shared between Various Threads
	static SctpServer serverObj = null;

	public static SctpServer getServer(int serverPortNumber,ArrayList<Integer> neighbourNodes,Map<Integer,ArrayList<Object>> mNodes,int myNodeID,Boolean startingNode,int totalNodes){
		if(serverObj == null){
			serverObj = new SctpServer(serverPortNumber,neighbourNodes,mNodes,myNodeID,startingNode,totalNodes);
		}
		return serverObj;
	}

	public static SctpServer getServer(){
		return serverObj;
	}

	//Constructor to initialize the node data structures
	private SctpServer(int serverPortNumber,ArrayList<Integer> neighbourNodes,Map<Integer,ArrayList<Object>> mNodes,int myNodeID,Boolean startingNode,int totalNodes){
		this.serverPortNumber=serverPortNumber;
		this.neighbourNodes=neighbourNodes;
		this.mNodes=mNodes;
		this.myNodeID=myNodeID;
		this.startingNode=startingNode;
		this.waitingForResponse=0;
		this.totalNodes=totalNodes;
		this.allResponse=false;
		this.treeDone = false;		
		
		this.token_parent = -1;			
		this.token_busy = false; 	 
		this.token_req_done = false;   
		this.token_present = false; 

		this.vector = new int[totalNodes+1];
		
		try{	
			sem_treedone.acquire();					
		}
		catch(InterruptedException ex){
			ex.printStackTrace();
		}
		
		if(!startingNode){	
			try{	
				System.out.println(myNodeID+": Start Lock Request");
				semaphore.acquire();
				this.svr_lock=true;
				System.out.println(myNodeID+": Start Lock Response");
			}catch(InterruptedException ex){
				ex.printStackTrace();
			}
		}			
	}
	
	public static final int MESSAGE_SIZE = 10000;											//Size of the message
	public void run(){
		//List to hold a copy of the neighbours of the node
		for(Integer i:neighbourNodes){
			duplicateNeighbourList.add(i);
		}
		
		//Variable to hold the received message
		String message;
		try
		{
			//Open a server channel
			SctpServerChannel sctpServerChannel = SctpServerChannel.open();
			//Create a socket address in the current machine at port 5000
			InetSocketAddress serverAddr = new InetSocketAddress(serverPortNumber);
			//Bind the channel's socket to the server in the current machine at port serverPortNumber
			sctpServerChannel.bind(serverAddr);
			//Server goes into a permanent loop accepting connections from clients			
			while(true)
			{
				//Buffer to hold messages in byte format
				ByteBuffer byteBuffer = ByteBuffer.allocate(MESSAGE_SIZE);
				//Listen for a connection to be made to this socket and accept it
				//The method blocks until a connection is made
				//Returns a new SCTPChannel between the server and client
				
				System.out.println(myNodeID+": I am waiting SVR");
				SctpChannel sctpChannel = sctpServerChannel.accept();
				System.out.println(myNodeID+": I am got message SVR");
				
				//Receive message in the channel (byte format) and store it in buf
				//Note: Actual message is in byre format stored in buf
				//MessageInfo has additional details of the message
				MessageInfo messageInfo = sctpChannel.receive(byteBuffer,null,null);
				//Just seeing what gets stored in messageInfo
				//System.out.println(messageInfo);
				
				//Converting bytes to string. This looks nastier than in TCP
				//So better use a function call to write once and forget it :)
				message = byteToString(byteBuffer);								

				//Call the parsing function when a message arrives
				parseResponse(message);

				//Finally the actual message
				//System.out.println(message);
				sctpChannel.close();
			}
		}
		catch(IOException ex){
			ex.printStackTrace();
		}
	}

	/*
	Convert incoming bytes into string
	*/
	public String byteToString(ByteBuffer byteBuffer){
		byteBuffer.position(0);
		byteBuffer.limit(MESSAGE_SIZE);
		byte[] bufArr = new byte[byteBuffer.remaining()];
		byteBuffer.get(bufArr);
		return new String(bufArr);
	}
	
	/*
	Respond to the incoming message by parsing the message
	*/
	public void parseResponse(String message){
		//System.out.println(message);
		//Split the message arrived
		String[] parsedMessage=message.split("[*]");
		//Store the split message and get information like message type, message code, NodeId which sent the message
		int messageType=Integer.parseInt(parsedMessage[0]);
		int nodeID=Integer.parseInt(parsedMessage[1]);	
		String nodeMessage=parsedMessage[2].substring(0,3);
		
		//Extract the node address and port number using its NodeID
		String nodeName=(mNodes.get(nodeID)).get(0).toString();	
		int nodePortNumber=(Integer)(mNodes.get(nodeID)).get(1);

		//System.out.println(myNodeID+": I received the message: "+nodeMessage+" from "+nodeID+"\n");

		if(messageType == 1){
		//If the neighbour request(NEB) arrives either accept it with ACK or decline it with NAC
			if(nodeMessage.equals("NEB")){
				//If no one is a Tree Neighbour List then accept the NEB with ACK			
				if(treeNeighbours.isEmpty()){							
					parentNode=nodeID;
					
					token_parent = nodeID; // (Raymonds) Initially token is with starting node

					String sendMessage="1*"+myNodeID+"*ACK";
					
					System.out.println(myNodeID+": Sending ACK to "+nodeID+"\n");	
					sendMessage(nodeID,nodePortNumber,sendMessage);
					treeNeighbours.add(nodeID);
					duplicateNeighbourList.remove(new Integer(nodeID));
					
					//Apart from the node from which NEB arrived send the NEB request to all its neighbours
					if(!(duplicateNeighbourList.isEmpty())&&(waitingForResponse==0)){
						for(Integer nodes:duplicateNeighbourList){		
							System.out.println(myNodeID+": Sending Spanning Tree Request to "+nodes+"\n");	
							sendMessage="1*"+myNodeID+"*NEB";
							sendMessage(nodes,(Integer)(mNodes.get(nodes)).get(1),sendMessage);			
						}
						System.out.println("\n");
						waitingForResponse=1;	
					}
					else if(duplicateNeighbourList.isEmpty()){
						System.out.println(myNodeID+" :Sending DNE to parent "+parentNode);
						System.out.println("\n"+myNodeID+": My Final Neighbours List: "+treeNeighbours+"\n");						
						sendDoneMessage(1,myNodeID,parentNode,"DNE");
					}
				}
				else{
					System.out.println(myNodeID+": Sending NAC to "+nodeID+"\n");
					String sendMessage="1*"+myNodeID+"*NAC";
					sendMessage(nodeID,nodePortNumber,sendMessage);
				}
			}
			//If the ACK arrives from the node to whom we sent NEB request add them into the Tree Neighbour List
			else if(nodeMessage.equals("ACK")){
				treeNeighbours.add(nodeID);		
				duplicateNeighbourList.remove(new Integer(nodeID));
				
				if(duplicateNeighbourList.isEmpty()&&!startingNode){
					System.out.println(myNodeID+" :sending DNE to parent "+parentNode);
					sendDoneMessage(1,myNodeID,parentNode,"DNE");								
					System.out.println("\n"+myNodeID+": My Final Neighbours List: "+treeNeighbours+"\n");		
				}

				if(duplicateNeighbourList.isEmpty()&&startingNode){
					System.out.println("\n"+myNodeID+": My Final Neighbours List: "+treeNeighbours+"\n");
					
					allResponse=true;
					if(treeDone && allResponse){
						System.out.println("\n"+myNodeID+": Spanning Tree is formed !!!!!!\n\n");
						for(int toNode : treeNeighbours){
							sendDoneMessage(1,myNodeID,toNode,"TRD");
						}
					}
				}
			}
		//If the NAC arrives from the node to whom we sent NEB request then don't add them into the Tree Neighbour List
			else if(nodeMessage.equals("NAC")){
				System.out.println(myNodeID+": Negative ACK from "+nodeID+"\n");
				duplicateNeighbourList.remove(new Integer(nodeID));
				
				if(duplicateNeighbourList.isEmpty()&&!startingNode){
					System.out.println(myNodeID+" :sending DNE to parent "+parentNode);
					sendDoneMessage(1,myNodeID,parentNode,"DNE");

					System.out.println("\n"+myNodeID+": My Final Neighbours List: "+treeNeighbours+"\n");
				}
				if(duplicateNeighbourList.isEmpty()&&startingNode){
					System.out.println("\n"+myNodeID+": My Final Neighbours List: "+treeNeighbours+"\n");
					
					allResponse=true;
					if(treeDone && allResponse){
						System.out.println("\n"+myNodeID+": Spanning Tree is formed !!!!!!\n\n");
						for(int toNode : treeNeighbours){
							sendDoneMessage(1,myNodeID,toNode,"TRD");
						}
					}
				}
			}
			//If we received all the responses from our neighbours then send done message(DNE) to the parent of the current node in the Spanning Tree
			else if(nodeMessage.equals("DNE")){
				if(startingNode){
					System.out.println(myNodeID+": Done message received from "+nodeID+"\n");			
					countNodes=countNodes+1;
					System.out.println(myNodeID+": Still I have to hear from: "+(totalNodes-countNodes-1)+" nodes\n");
					
					if(countNodes==(totalNodes-1)){
						
						treeDone = true;
						token_present = true;

						sem_treedone.release();					
				
						// TRD - Tree Done message broadcast from distinguished node 	
						if(treeDone && allResponse){
							System.out.println("\n"+myNodeID+": Spanning Tree is formed !!!!!!\n\n");
							for(int toNode : treeNeighbours){
								sendDoneMessage(1,myNodeID,toNode,"TRD");
							}
						}
					}
				}
				else{
					System.out.println(myNodeID+": Done message received from "+nodeID+"\n");
					System.out.println(myNodeID+": Escalating to my Parent. I am not the designated node\n");
					
					sendMessage(parentNode,(Integer)(mNodes.get(parentNode)).get(1),message);
				}
			}else if(nodeMessage.equals("TRD")){

				treeDone = true; // Local Setting of Tree Done as the message is received from the parent
				sem_treedone.release();

				// Forward TRD (Tree done) message to other spanning tree neighbours
				for(int toNode : treeNeighbours){
					if(toNode!=nodeID){ // No point in sending TRD to parent node
						sendDoneMessage(1,myNodeID,toNode,"TRD");
					}
				}	
			}
		}else if(messageType == 2){
			 if(nodeMessage.equals("TRQ")){
			 	// Will have to watch for synchronization problems	
			 	System.out.println(myNodeID+": TRQ <- "+nodeID);
			 	que.add(nodeID); // Add the request to the queue
				
				//System.out.println(myNodeID+": TRQ - The Following is the queue:"+que);
				//System.out.println(myNodeID+": TRQ - token_parent:"+token_parent);
				
				assignPrivilege("SVR",arrayToString(vector));	
				makeRequest();				
			}
			else if(nodeMessage.equals("TOK")){
			 	System.out.println(myNodeID+": SVR TOK <- "+nodeID);
			 	token_parent=-1;
				
				assignPrivilege("SVR",parsedMessage[3]);	
				makeRequest();
			}
		}	 	
		else{		
			//Handle erroneous messages that may be received
			System.out.println(myNodeID+": Unknown message format: "+message+"\n");
		}		
	}	
	/*
	Send the given message to a NodeID 
	*/
	public synchronized void sendMessage(int nodeID,int nodePortNumber,String sendMessage){
		
		SctpClient SctpClientObj = new SctpClient((mNodes.get(nodeID)).get(0).toString(),nodePortNumber,sendMessage);
		Thread tClient=new Thread(SctpClientObj);
		tClient.start();
		System.out.println(myNodeID+": Following message has been sent to "+nodeID+" :"+sendMessage+"\n");
		//SctpClientObj.run();
	}
	
	/*
	Send Done message to the parent node
	*/
	public void sendDoneMessage(int controlMsg,int nodeID,int toNode,String message){
		String sendMessage=controlMsg+"*"+nodeID+"*"+message;
		String nodeName=(mNodes.get(toNode)).get(0).toString();		
		int nodePortNumber=(Integer)(mNodes.get(toNode)).get(1);
		sendMessage(toNode,nodePortNumber,sendMessage);
	}

	/*
	Send Done message function overloaded to send out control 2 messages
	*/
	public void sendMessage(int toNode,String message){
		String sendMessage="2*"+myNodeID+"*"+message;
		String nodeName=(mNodes.get(toNode)).get(0).toString();		
		int nodePortNumber=(Integer)(mNodes.get(toNode)).get(1);		
		sendMessage(toNode,nodePortNumber,sendMessage);
	}

	/*
	Send the token to the requesting node
	*/
	public synchronized void assignPrivilege(String id,String vectorStr){
		if(token_parent==-1 && !token_busy && !que.isEmpty()){
			System.out.println(myNodeID+": The Following is the queue:"+que);
			token_parent=que.remove();		//Queue is not empty and the last request is not self	
			token_req_done = false;		
			
			if(token_parent==myNodeID){
				token_parent=-1;
			}
			
			if (token_parent==-1){
				
				token_busy=true;
				mergeVectors(stringToArray(vectorStr));
				System.out.println(myNodeID+": SVR trying to release the token");
				if(svr_lock){	
					releaseSemaphore(true);
					svr_lock = false;
				}
				//lockSemaphore();
			}
			else{
				/*try{
					Thread.sleep(1000);						
				}
				catch(InterruptedException e){
					e.printStackTrace();
				}*/
				System.out.println(myNodeID+": "+id+" Send TOK -> "+token_parent);
				sendMessage(token_parent,"TOK"+"*"+vectorStr+"*");	
				lockSemaphore();
			}	
		}	
	}
	/*
	Make token request 
	*/
	public synchronized void makeRequest(){
		if(token_parent!=-1 && !que.isEmpty() && !token_req_done){
			System.out.println(myNodeID+ ": SVR TRQ -> "+token_parent);
			token_req_done = true;
			try{
				Thread.sleep(1000);						
			}
			catch(InterruptedException e){
				e.printStackTrace();
			}
			sendMessage(token_parent,"TRQ");		
		}
	}
	/*
	Lock the semaphore when the process has sent the token out
	*/
	public synchronized void lockSemaphore(){
		if(!svr_lock){
			try{	
				System.out.println(myNodeID+": SVR Locking");
				svr_lock = true;
				semaphore.acquire();
				System.out.println(myNodeID+": SVR Locked");
			}catch(InterruptedException ex){
				ex.printStackTrace();
			}
		}
	}
	/*
	Release the semaphore when our own request need to be satisfied
	*/
	public synchronized void releaseSemaphore(boolean value)
	{
		token_busy=value;
		//token_present = true;
		System.out.println(myNodeID+": SVR Releasing Lock");
		semaphore.release();
		System.out.println(myNodeID+": SVR Released Lock");
	}
	/*
	Converts an Integer Array Representation of the Vector Clock into string array
	*/
	public String arrayToString(int[] vector){
		String vectorStr = "[";
		for(int i=1;i<vector.length;i++){
			vectorStr+=vector[i];
			if(i!=vector.length-1){
				vectorStr+=",";
			}
		}
		vectorStr+="]";
		return vectorStr;
	}
	/*
	Converts a string Vector Clock to Integer Array Representation
	*/
	public int[] stringToArray(String vectorStr){
		vectorStr = vectorStr.substring(1,vectorStr.length()-1);
		String[] svector = vectorStr.split(",");	
		int vector[] = new int[totalNodes+1];
		for(int i=1;i<totalNodes+1;i++){
			vector[i] = Integer.parseInt(svector[i-1]);	
		}
		return vector;
	}
	/*
	Function to merge two vector clock's
	*/
	public void mergeVectors(int[] messageVector){
		for(int i=1;i<messageVector.length;i++){
			if(this.vector[i]>=messageVector[i]){
				this.vector[i] = this.vector[i];
			}else{
				this.vector[i] = messageVector[i];
			}
		}		
	}
	/*
	Increment the vector clock for ECS and LCS events
	*/
	public void incrementVector(){
		this.vector[myNodeID]=this.vector[myNodeID]+1;
	}
	/*
	Write the vector clock into the text file
	*/
	public synchronized void vectorWrite(){
		try{	
			PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter("v-data.txt",true)));
			printWriter.println (arrayToString(this.vector));
			//System.out.println(csStatus+" "+myNodeID);
			printWriter.flush();
			printWriter.close();
		}catch(Exception ex){
			ex.printStackTrace();
		}
	}
}
	
class SctpClient implements Runnable
{
	String serverName;				//Name of the server with which the node is trying to communicate
	String message;					//Message to be sent to the destination node
	int serverPortNumber;			//Port Number of the node we are going to communicate
	
	//Constructor for initializing the local variables
	SctpClient(String serverName,int serverPortNumber,String message){
		this.serverName=serverName;
		this.serverPortNumber=serverPortNumber;
		this.message=message;
	}
	
	public static final int MESSAGE_SIZE = 100000;
	public void run(){
		//Buffer to hold messages in byte format
		ByteBuffer byteBuffer = ByteBuffer.allocate(MESSAGE_SIZE);
		try{
			//Create a socket address for  server at serverName at port serverPortNumber
			SocketAddress socketAddress = new InetSocketAddress(serverName,serverPortNumber);
			//Open a channel. NOT SERVER CHANNEL
			SctpChannel sctpChannel = SctpChannel.open();
			//Bind the channel's socket to a local port. Again this is not a server bind
			//sctpChannel.bind(new InetSocketAddress(clientPortNumber));
			//Connect the channel's socket to  the remote server
			sctpChannel.connect(socketAddress);
			//Before sending messages add additional information about the message
			MessageInfo messageInfo = MessageInfo.createOutgoing(null,0);
			//convert the string message into bytes and put it in the byte buffer
			byteBuffer.put(message.getBytes());
			//Reset a pointer to point to the start of buffer 
			byteBuffer.flip();
			//Send a message in the channel (byte format)
			sctpChannel.send(byteBuffer,messageInfo);
			//System.out.println(SystemUnder.myNodeID+": Client Following message has been sent to "+serverName+" :"+message+"\n");
			//Close the connection
			sctpChannel.close();
		}
		catch(IOException ex){
			System.out.println(SystemUnder.myNodeID+": Timed out trying to send- "+message+" to "+serverName);
			ex.printStackTrace();
		}
	}
}


public class SystemUnder{
	static PrintWriter printWriter;
	
	static int myNodeID = -1;

    public static void launch(String args)
	{
		Map<Integer,ArrayList<Object>> mNodes=new HashMap<Integer,ArrayList<Object>>(); //Information of name of the nodes and respective port numbers with node id
		ArrayList<Integer> neighboursList=new ArrayList<Integer>();						//Neighbours List of the node before the Spanning Tree is formed
		Boolean start;																	//check weather the current node is the Designated node to start Spanning Tree construction
		myNodeID=Integer.parseInt(args);											//Node Id of the present node
		int nodeID=1;
		
		int startNode=0,totalNodes=0;													//start node and the total number of nodes in the graph
		
		//Reading information from the configuration file
		try{
			String s;
			
			//Object to read the configuration file
			FileReader readConfig=new FileReader("config.txt");							
			BufferedReader brReader=new BufferedReader(readConfig);
		
			while((s=brReader.readLine())!=null){
				String[] str=s.split("\\s+");
				//finding out who is the starting node 
				if(str[0].equals("start")){	
					startNode=Integer.parseInt(str[1]);									
				}
				//get the total number of nodes count in the graph
				else if(str[0].equals("total")){										
					totalNodes=Integer.parseInt(str[1]);
				}
				
				else if(str[0].equals("nocsreq")){										
					//nocsreq=Integer.parseInt(str[1]);
				}
				else if(str[0].equals("csduration")){										
					//csduration=Integer.parseInt(str[1]);
				}
				else if(str[0].equals("delaybtwcs")){										
					//delaybtwcs=Integer.parseInt(str[1]);
				}
				//Collecting the neighbouring nodes list, port number and server name and map it to a node ID
				else{
					
					ArrayList<Object> nodeDetails=new ArrayList<Object>();				
					nodeDetails.add(str[0]+".utdallas.edu");
					nodeDetails.add(Integer.parseInt(str[1]));
					mNodes.put(nodeID,nodeDetails);
					if(nodeID==myNodeID){
						for(int i=2;i<str.length;i++){
						neighboursList.add(Integer.parseInt(str[i]));	
						}
					}
					nodeID+=1;
				}
			}
			//close the file here after getting all the necessary information 
			brReader.close();
		}
		catch(IOException e){
			System.err.println(e.getMessage());
		}
		
		//checking to see if the present node is the starting node
		if(startNode==myNodeID){
			start=true;
		}else{
			start=false;
		}
		
		//Start the listening server thread of the node
		SctpServer SctpServerObj = SctpServer.getServer((Integer)(mNodes.get(myNodeID)).get(1),neighboursList,mNodes,myNodeID,start,totalNodes);
		Thread tServer=new Thread(SctpServerObj);
		tServer.start();
		
		System.out.println("Node "+myNodeID+" started at port number "+(Integer)(mNodes.get(myNodeID)).get(1)+"\n");
		
		//send the spanning tree neighbour request to the neighbours if the current node is the Starting node
		if(start){
			String message="1*"+myNodeID+"*NEB";
			for(Integer nodes:neighboursList){
				System.out.println(myNodeID+": Sending Spanning Tree Request to "+nodes+"\n");	
				SctpServerObj.sendMessage(nodes,(Integer)(mNodes.get(nodes)).get(1),message);
			}
		}
	}
	/*
	Function for writing the details of when we entered and excited CS
	*/
	public static synchronized void fileWrite(String csStatus){
		try{	
			printWriter = new PrintWriter(new BufferedWriter(new FileWriter("data.txt",true)));
			printWriter.println (csStatus+" "+myNodeID);
			System.out.println(csStatus+" "+myNodeID);
			printWriter.flush();
			printWriter.close();
		}catch(Exception ex){
			ex.printStackTrace();
		}
	}
	/*
	Function which an application should call when having to enter CS
	*/
	public static synchronized void enter_cs(){
		SctpServer serverObj = SctpServer.getServer();
		
		//Don't accept any enter CS request till the tree gets formed keep block waiting
		try{
			serverObj.sem_treedone.acquire();
		}catch(InterruptedException ex){
			ex.printStackTrace();
		}
	
		System.out.println(serverObj.myNodeID+": Raise Request");
		serverObj.que.add(serverObj.myNodeID);
		System.out.println(myNodeID+": ECS - The Following is the queue:"+serverObj.que);
		
		serverObj.assignPrivilege("ECS",serverObj.arrayToString(serverObj.vector));	
		serverObj.makeRequest();
		
		//System.out.println(serverObj.myNodeID+": ECS token_parent-"+serverObj.token_parent+": token_req_done-"+serverObj.token_req_done);
		System.out.println(serverObj.myNodeID+": ECS Lock Request");
		try{
			serverObj.semaphore.acquire();
		}catch(InterruptedException ex){
			ex.printStackTrace();
		}
		System.out.println(serverObj.myNodeID+": I am entering CS");

		fileWrite("ECS");
		serverObj.incrementVector();
		serverObj.vectorWrite();
		
		System.out.println(SystemUnder.myNodeID+": ECS Lock Response");
	}	
	/*
	Function which an application calls when it has to leave CS
	*/
	public static synchronized void leave_cs(){
		SctpServer serverObj = SctpServer.getServer();	
		
		fileWrite("LCS");
		serverObj.incrementVector();
		serverObj.vectorWrite();
		
		serverObj.sem_treedone.release();
		serverObj.semaphore.release();	
		serverObj.token_busy = false;
		
		//System.out.println(serverObj.myNodeID+": LCS token_parent-"+serverObj.token_parent+": token_req_done-"+serverObj.token_req_done);
		serverObj.assignPrivilege("LCS",serverObj.arrayToString(serverObj.vector));	
		serverObj.makeRequest();
		
		System.out.println(serverObj.myNodeID+": I am leaving CS");
	}
}
