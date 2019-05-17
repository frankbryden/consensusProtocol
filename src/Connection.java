import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
    private int servPort;
    private boolean running = false;

    public Connection(int servPort, int port, ClientMessageCallback clientMessageCallback, SocketDisconnectCallback socketDisconnectCallback) throws IOException {
        this.servPort = servPort;
        this.port = port;
        this.socket = new Socket("localhost", port);
        this.clientMessageCallback = clientMessageCallback;
        this.socketDisconnectCallback = socketDisconnectCallback;
        this.init();
        this.setupReadThread();
    }

    public Connection(Socket socket, int port, ClientMessageCallback clientMessageCallback, SocketDisconnectCallback socketDisconnectCallback){
        this.servPort = port;
        this.port = port;
        this.socket = socket;
        this.clientMessageCallback = clientMessageCallback;
        this.socketDisconnectCallback = socketDisconnectCallback;
        this.init();
        this.setupReadThread();
    }

    private void init(){
        try {
            this.output = new PrintWriter(socket.getOutputStream());
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Failed in init");
            e.printStackTrace();
        }
    }

    void send(Token token){
        System.out.println("[" + servPort + "] Sending : " + token.toString() + " to " + port);
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
                        onDisconnect();
                        return;
                    }
                    Token token = Tokeniser.parseInput(line);
                    if (token instanceof JoinToken){
                        ((JoinToken) token).setConnection(this);
                        this.port = ((JoinToken) token).getPort();
                    } else if (token instanceof MultiVoteToken){
                        ((MultiVoteToken) token).setSourcePort(port);
                    } else if (token instanceof VoteToken){
                        this.port = ((VoteToken) token).getPort();
                    } else if (token instanceof OutcomeToken){
                        ((OutcomeToken) token).setSourcePort(port);
                    }
                    System.out.println("Message on port " + port + ": " + line);
                    clientMessageCallback.call(token);
                } catch (IOException e) {
                    if(e instanceof SocketException){
                        onDisconnect();
                        return;
                    }
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    private void onDisconnect(){
        socketDisconnectCallback.call(port);
    }

    void stop(){
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        running = false;
    }
}
