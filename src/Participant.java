import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

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

    private synchronized void onData(Token token){
        if (token instanceof DetailsToken){
            onDetails((DetailsToken) token);
        } else if (token instanceof VoteOptionsToken){
            onVoteOptions((VoteOptionsToken) token);
        } else if (token instanceof  VoteToken) {
            onParticipantVote((VoteToken) token);
        } else if (token instanceof MultiVoteToken){
            onParticipantMultiVote((MultiVoteToken) token);
        } else {
            System.err.println("Unknown token : " + token.toString());
        }
    }

    private synchronized void onParticipantDisconnect(int port){
        System.out.println("Participant on port " + port + " has disconnected.");
        this.connectionsToOtherParticipants.remove(port);
        //TODO we might have been waiting for this guy's vote - check if we were, and if so, proceed to next step
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
            //We have finished a round. Either move on to the next round, or if there are no new votes, resolve the outcome.
            System.out.println("All votes have been cast");
            if (!voteTracker.hasNewVotes()){
                System.out.println("Moving on to next round");
                nextRound();
            } else {
                //Move on to outcome determination
                System.out.println("No new votes, moving on to resolution of outcome.");
                resolveOutcome();
            }
        }
    }

    private void onParticipantMultiVote(MultiVoteToken token){
        for (int port : token.getVotes().keySet()){
            voteTracker.castVote(port, token.getVotes().get(port));
        }
    }

    private void vote(){
        System.out.println("VOTE STARTING");
        castSelfVote();
        listenForParticipants();
        connectToOtherParticipants();
        sendVoteToParticipants();
    }

    private void nextRound(){
        sendNewVotes();
        voteTracker.nextRound();
    }

    private void sendNewVotes(){
        MultiVoteToken newVotes = new MultiVoteToken(voteTracker.getNewVotes());
        System.out.println("Sending multivote token " + newVotes.getVotes().toString());
        sendTokenToParticipants(newVotes);
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
        voteTracker.castVote(this.port, this.ownVote);
    }

    private void sendVoteToParticipants(){
        Token voteToken = new VoteToken(this.port, ownVote);
        if (failureCondition == FailureCondition.DURING_STEP_4){
            List<Integer> ports = new ArrayList<>(connectionsToOtherParticipants.keySet());
            Random r = new Random(System.currentTimeMillis());
            int randIndex = r.nextInt(ports.size());
            ports.remove(randIndex);
            System.out.println("Sending vote to " + ports.toString());
            for (int port : ports){
                connectionsToOtherParticipants.get(port).send(voteToken);
            }
            fail();
        } else {
            sendTokenToParticipants(voteToken);
        }

    }

    private void sendTokenToParticipants(Token token){
        for (int port : this.connectionsToOtherParticipants.keySet()){
            this.connectionsToOtherParticipants.get(port).send(token);
        }
    }

    private boolean allVotesCast(){
        System.out.println("voteTracker.getVoteCount() = " + voteTracker.getVoteCount());
        System.out.println("ports.size() = " + ports.size());
        return voteTracker.getVoteCount() - 1 == ports.size();
    }


    private void sendOutcome(){
        String winningVote = voteTracker.getWinningVote();
        coordinatorConnection.getConnection().send(new OutcomeToken(winningVote, voteTracker.getParticipants().stream().mapToInt(Integer::intValue).toArray()));
        shutdown();
    }

    private void shutdown(){
        System.out.println("Shutting down process by terminating remote connections.");
        for (int port : connectionsToOtherParticipants.keySet()){
            connectionsToOtherParticipants.get(port).stop();
        }
    }

    private void fail(){
        System.out.println("We are in failure condition : " + this.failureCondition);
        System.out.println("Fail triggered");
        connectionsToOtherParticipants.values().forEach(Connection::stop);
        System.exit(-1);
    }

    private void sendJoin(){
        coordinatorConnection.getConnection().send(new JoinToken(this.port));
    }

    public static void main(String[] args){
        Participant participant = new Participant(args);
    }
}
