import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ServerThread implements Runnable {
    private ServerSocket serverSocket;
    private int port;
    private int maxConnections;
    private Map<Integer, Connection> connections; //Map from port to connection
    private ClientMessageCallback callback; //Callback called every time a new connection is accepted

    public ServerThread(int port, int maxConnections, ClientMessageCallback callback){
        this.port = port;
        this.maxConnections = maxConnections;
        this.callback = callback;
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
                PrintWriter output = new PrintWriter(client.getOutputStream());
                BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream()));
                connections.put(client.getPort(), new Connection(client, output, input, callback));
                System.out.println("New connection on port " + client.getPort());
            } catch (IOException e) {
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
