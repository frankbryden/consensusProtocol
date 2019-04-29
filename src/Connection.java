import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class Connection {
    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private ClientMessageCallback callback; //Callback used on incoming data
    private int port; //This is the port the client will be listening on (used for other peers wishing to connect)

    public Connection(Socket socket, PrintWriter output, BufferedReader input, ClientMessageCallback callback) {
        this.socket = socket;
        this.output = output;
        this.input = input;
        this.callback = callback;
        this.setupReadThread();
    }

    public Socket getSocket() {
        return socket;
    }

    public PrintWriter getOutput() {
        return output;
    }

    public BufferedReader getInput() {
        return input;
    }

    public void send(Token token){
        System.out.println("Sending : " + token.toString());
        output.println(token.toString());
        output.flush();
    }

    private void setupReadThread(){
        new Thread(() -> {
            while (true){
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
                    callback.call(token);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }
}
