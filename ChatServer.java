import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

public class ChatServer {

	protected int serverPort = 1234;
	protected static List<Socket> clients = new ArrayList<Socket>(); // list of clients
	protected static List<String> usernames = new ArrayList<String>(); // list of client's usernames

	

	public static void main(String[] args) throws Exception {
		new ChatServer();
	}

	public ChatServer() {
		ServerSocket serverSocket = null;

		// create socket
		try {
			serverSocket = new ServerSocket(this.serverPort); // create the ServerSocket
		} catch (Exception e) {
			System.err.println("[system] could not create socket on port " + this.serverPort);
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// start listening for new connections
		System.out.println("[system] listening ...");
		try {
			while (true) {
				Socket newClientSocket = serverSocket.accept(); // wait for a new client connection
				synchronized(this) {
					clients.add(newClientSocket); // add client to the list of clients 
					usernames.add(addUsername(newClientSocket)); // dodam username v seznam
				}
				ChatServerConnector conn = new ChatServerConnector(this, newClientSocket); // create a new thread for communication with the new client
				conn.start(); // run the new thread
			}
		} catch (Exception e) {
			System.err.println("[error] Accept failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// close socket
		System.out.println("[system] closing server socket ...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	// send a message to all clients connected to the server
	public void sendToAllClients(String message) throws Exception {
		Iterator<Socket> i = clients.iterator();
		while (i.hasNext()) { // iterate through the client list
			Socket socket = (Socket) i.next(); // get the socket for communicating with this client
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	// send a message to one client, connected to the server
	public void sendToOneClient(String message, String username2, Socket socketSender) throws Exception {
		Socket socket = clients.get(0);
		boolean uspesno = false;

		for (int i=0; i<usernames.size(); i++) { // najde socket clienta z uporabniskim imenom username2
			if (username2.matches(usernames.get(i))) {
				socket = clients.get(i);
				uspesno = true;
			}
		}
		try {
			DataOutputStream out = new DataOutputStream(socket.getOutputStream()); 
			if (uspesno) {
				out.writeUTF(message); // send message to the client
			
			if(socketSender != socket) { // sebi ne poslje sporocila 2x
				out = new DataOutputStream(socketSender.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message);
			}
			} else {
				out.writeUTF("Prišlo je do napake, uporabnik ne obstaja!");
			}
		} catch (Exception e) {
			System.err.println("[system] could not send message to a client");
			e.printStackTrace(System.err);
		}
	}
	// send list of usernames currently connected to server
	public void sendList(Socket socket) {
		String message = "Seznam uporabnikov: \n";
		for (int i=0; i<usernames.size(); i++) {
			message += i+1 + ") " + usernames.get(i) + "\n";
		}
		try {
			DataOutputStream out = new DataOutputStream(socket.getOutputStream()); 
			out.writeUTF(message);
		} catch (Exception e) {
			System.err.println("[system] could not send message to a client");
			e.printStackTrace(System.err);
		}
	}

	public void removeClient(Socket socket) {
		synchronized(this) {
			clients.remove(socket);
		}
	}

	public static String addUsername(Socket socket) {
			DataInputStream in;
		try {
			in = new DataInputStream(socket.getInputStream());
			String currentUsername = in.readUTF();;

			return currentUsername;
		} catch (IOException e) {
			e.printStackTrace(System.err);
			return null;
		}
	}

	public static List<Socket> getClients() {
		return clients;
	}

	public static List<String> getUsernames() {
		return usernames;
	}
}
// -------------------------------------------------------------------------------//
// ---------------------- CLASS THREAD -------------------------------------------//
class ChatServerConnector extends Thread {
	private ChatServer server;
	private Socket socket;
	protected List<Socket> clients = ChatServer.getClients();
	protected List<String> usernames = ChatServer.getUsernames();
	static int counter = 0;
	String username;
	String username2;

	public ChatServerConnector(ChatServer server, Socket socket) {
		this.server = server;
		this.socket = socket;
	}

	public void run() {
		System.out.println("[system] "+ usernames.get(counter)+" connected with " + this.socket.getInetAddress().getHostName() + ":" + this.socket.getPort());
		counter++;

		DataInputStream in;
		try {
			in = new DataInputStream(this.socket.getInputStream()); // create input stream for listening for incoming messages
		} catch (IOException e) {
			System.err.println("[system] could not open input stream!");
			e.printStackTrace(System.err);
			this.server.removeClient(socket);
			return;
		}

		while (true) { // infinite loop in which this thread waits for incoming messages and processes them
			String msg_received;
			try {
				msg_received = in.readUTF(); // read the message from the client
			} catch (Exception e) {
				System.err.println("[system] there was a problem while reading message client on port " + this.socket.getPort());
				e.printStackTrace(System.err);
				this.server.removeClient(this.socket);
				return;
			}

			if (msg_received.length() == 0) // invalid message
				continue;

			for (int i = 0; i< clients.size() ; i++) { // najde username uporabnika z dolocenim
				if (this.socket == clients.get(i)) 
					username = usernames.get(i);
			}

			System.out.println("[RKchat] ["+ time() + "] [" + username + "] : " + msg_received); // print the incoming message in the console
			String msg_send = null; // TODO

			try {
				if (msg_received.startsWith("/pvt")) { // send message to one client
					username2 = msg_received.substring(6, msg_received.length());
					username2 = username2.substring(0, username2.indexOf("'"));
					msg_received = msg_received.substring(6 + username2.length()+2, msg_received.length()); // odstrani zacetek sporocila: /pvt 'username2'
					msg_send = "["+ time() + "] " + "[" + username +"]" + " privately said: " + msg_received.toUpperCase(); // TODO
					this.server.sendToOneClient(msg_send, username2, this.socket);
				} else if (msg_received.startsWith("/userlist")) {
					this.server.sendList(this.socket);
				} else {
					msg_send = "["+ time() + "] " + "[" + username +"]" + " said: " + msg_received.toUpperCase(); // TODO
					this.server.sendToAllClients(msg_send); // send message to all clients
				}
			} catch (Exception e) {
				System.err.println("[system] there was a problem while sending the message to all clients");
				e.printStackTrace(System.err);
				continue;
			}
		}
	}

	public static String time() {
		DateFormat dateFormat = new SimpleDateFormat("HH:mm");
		Date date = new Date();
    	return dateFormat.format(date);
	}
}
