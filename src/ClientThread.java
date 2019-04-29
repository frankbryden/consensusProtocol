import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientThread {
    private int port;
    private Socket socket;
    private Connection connection;

    public ClientThread(int port){
        this.port = port;
    }

    public void connect(ClientMessageCallback callback){
        try {
            this.socket = new Socket("localhost", port);
            PrintWriter output = new PrintWriter(socket.getOutputStream());
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.connection = new Connection(socket, output, input, callback);
            System.out.println("Connected to " + socket.getRemoteSocketAddress().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }
}
