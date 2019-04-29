import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

public class Participant {
    private enum FailureCondition {NONE, DURING_STEP_4, AFTER_STEP_4}
    //Initialisation members
    private int cPort; //Coordinator Port
    private int port;
    private int timeout; // in ms
    private FailureCondition failureCondition;

    //Runtime members
    private ServerThread serverThread;
    private ClientThread coordinatorConnection; //Used to connect to coordinator
    private ArrayList<Integer> ports;
    private int expectedParticipants;
    private String[] voteOptions;
    private ServerThread otherParticipantsThread;
    private Map<Integer, Connection> connectionsToOtherParticipants;
    private String ownVote; //This will be a random element of voteOptions
    private Voting voteTracker;

    private enum ParticipantState {JOIN_COORDINATOR, WAITING_FOR_DETAILS, WAITING_FOR_VOTE_OPTIONS, VOTING, SEND_OUTCOME, DONE}
    private ParticipantState currentState;

    public Participant(String[] args){
        parseArgs(args);
        this.ports = new ArrayList<>();
        this.connectionsToOtherParticipants = new HashMap<>();
        this.voteTracker = new Voting();
        start();
    }

    private void parseArgs(String[] args){
        this.cPort = Integer.parseInt(args[0]);
        this.port = Integer.parseInt(args[1]);
        this.timeout = Integer.parseInt(args[2]);
        this.failureCondition = getFailureCondition(Integer.parseInt(args[3]));
        this.expectedParticipants = Integer.parseInt(args[1]);
        this.voteOptions = Arrays.copyOfRange(args, 2, args.length);
    }

    private FailureCondition getFailureCondition(int failureCode) {
        switch (failureCode){
            case 0:
                return FailureCondition.NONE;
            case 1:
                return FailureCondition.DURING_STEP_4;
            case 2:
                return FailureCondition.AFTER_STEP_4;
            default:
                System.err.println("We have a problem, unknown failure code " + failureCode);
                return null;
        }
    }

    private void start(){
        this.currentState = ParticipantState.JOIN_COORDINATOR;
        this.coordinatorConnection = new ClientThread(cPort);
        this.coordinatorConnection.connect(this::onData);
        sendJoin();
    }

    private void onData(Token token){
        if (token instanceof DetailsToken){
            onDetails((DetailsToken) token);
        } else if (token instanceof VoteOptionsToken){
            onVoteOptions((VoteOptionsToken) token);
        } else if (token instanceof  VoteToken){
            onParticipantVote((VoteToken) token);
        } else {
            System.err.println("Unknown token : " + token.toString());
        }
    }

    private void onDetails(DetailsToken token){
        System.out.println("Other participants include : " + token.toString());
        for (int port : token.getPorts()){
            this.ports.add(port);
        }
    }

    private void onVoteOptions(VoteOptionsToken token){
        System.out.println("Our options for the vote include : " + token.toString());
        this.voteOptions = token.getOptions();
        this.vote();
    }

    private void onParticipantVote(VoteToken token){
        System.out.println("Vote received from " + token.getPort() + " (voted for " + token.getVote() + ")");
        voteTracker.castVote(token.getPort(), token.getVote());
        if (allVotesCast()){
            //Move on to outcome determination
            resolveOutcome();
        }
    }

    private void vote(){
        System.out.println("VOTE STARTING");
        castSelfVote();
        listenForParticipants();
        connectToOtherParticipants();
        sendVoteToParticipants();
    }

    private void resolveOutcome(){
        //TODO use voting to resolve, then send conclusion to coordinator
        voteTracker.castVote(port, ownVote);
        sendOutcome();
    }

    private void listenForParticipants(){
        this.otherParticipantsThread = new ServerThread(port, ports.size(), this::onData);
        new Thread(otherParticipantsThread).start();
    }

    private void connectToOtherParticipants(){
        for (int port : ports){
            connectionsToOtherParticipants.put(port, connectToParticipant(port));
        }
    }

    private Connection connectToParticipant(int port){
        try {
            System.out.println("Connecting to port " + port);
            Socket socket = new Socket("localhost", port);
            PrintWriter output = new PrintWriter(socket.getOutputStream());
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            return new Connection(socket, output, input, this::onData);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void castSelfVote(){
        Random r = new Random(System.currentTimeMillis());
        this.ownVote = voteOptions[r.nextInt(voteOptions.length)];
    }

    private void sendVoteToParticipants(){
        for (int port : this.connectionsToOtherParticipants.keySet()){
            this.connectionsToOtherParticipants.get(port).send(new VoteToken(this.port, ownVote));
        }
    }

    private boolean allVotesCast(){
        return voteTracker.getVoteCount() == ports.size();
    }


    private void sendOutcome(){
        String winningVote = voteTracker.getWinningVote();
        coordinatorConnection.getConnection().send(new OutcomeToken(winningVote, voteTracker.getParticipants().stream().mapToInt(Integer::intValue).toArray()));
    }

    private void sendJoin(){
        coordinatorConnection.getConnection().send(new JoinToken(this.port));
    }

    public static void main(String[] args){
        Participant participant = new Participant(args);
    }
}
