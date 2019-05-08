import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class Connection {
    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private ClientMessageCallback clientMessageCallback; //Callback used on incoming data
    private SocketDisconnectCallback socketDisconnectCallback; //Callback used when tcp connection is lost
    private int port; //This is the port the client will be listening on (used for other peers wishing to connect)
    private boolean running = false;

    public Connection(Socket socket, PrintWriter output, BufferedReader input, ClientMessageCallback clientMessageCallback, SocketDisconnectCallback socketDisconnectCallback) {
        this.socket = socket;
        this.output = output;
        this.input = input;
        this.clientMessageCallback = clientMessageCallback;
        this.socketDisconnectCallback = socketDisconnectCallback;
        this.setupReadThread();
    }

    public void send(Token token){
        System.out.println("Sending : " + token.toString());
        output.println(token.toString());
        output.flush();
    }

    private void setupReadThread(){
        running = true;
        new Thread(() -> {
            while (running){
                try {
                    String line = input.readLine();
                    if (line == null){
                        return;
                    }
                    System.out.println("Message : " + line);
                    Token token = Tokeniser.parseInput(line);
                    if (token instanceof JoinToken){
                        ((JoinToken) token).setConnection(this);
                    }
                    clientMessageCallback.call(token);
                } catch (IOException e) {
                    if(e instanceof SocketException){
                        System.out.println("Connection closed.");
                        socketDisconnectCallback.call();
                        return;
                    }
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    public void stop(){
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        running = false;
    }
}
