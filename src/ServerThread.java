import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

public class ServerThread implements Runnable {
    private ServerSocket serverSocket;
    private int port;
    private int maxConnections;
    private Map<Integer, Connection> connections; //Map from port to connection
    private ClientMessageCallback clientMessageCallback; //Callback called every time a new connection is accepted
    private SocketDisconnectCallback socketDisconnectCallback; //Callback used when tcp connection is lost

    public ServerThread(int port, int maxConnections, ClientMessageCallback clientMessageCallback, SocketDisconnectCallback socketDisconnectCallback){
        this.port = port;
        this.maxConnections = maxConnections;
        this.clientMessageCallback = clientMessageCallback;
        this.socketDisconnectCallback = socketDisconnectCallback;
        try {
            System.out.println("Creating server socket on port " + port);
            this.serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.connections = new HashMap<>();
    }

    @Override
    public void run() {
        if (this.serverSocket == null){
            return;
        }
        while (connections.size() < maxConnections){
            try {
                Socket client = serverSocket.accept();
                connections.put(client.getPort(), new Connection(client, port, clientMessageCallback, socketDisconnectCallback));
                System.out.println("New connection on port " + client.getPort());
            } catch (IOException e) {

                System.err.println("[ServerThread] Connection to client failed");
                e.printStackTrace();
            }
        }
    }

    public void sendToAll(Token token){
        this.connections.values().forEach(connection -> connection.send(token));
    }

    public void stop(){
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
