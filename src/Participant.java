import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class Participant {
    private enum FailureCondition {NONE, DURING_STEP_4, AFTER_STEP_4}
    //Initialisation members
    private int cPort; //Coordinator Port
    private int port;
    private int timeout; // in ms
    private FailureCondition failureCondition;

    //Runtime members
    private ServerThread serverThread;
    private Connection coordinatorConnection; //Used to connect to coordinator
    private ArrayList<Integer> ports;
    private int expectedParticipants;
    private String[] voteOptions;
    private ServerThread otherParticipantsThread;
    private Map<Integer, Connection> connectionsToOtherParticipants;
    private List<Integer> remainingParticipants;
    private String ownVote; //This will be a random element of voteOptions
    private Voting voteTracker;

    private enum ParticipantState {JOIN_COORDINATOR, WAITING_FOR_DETAILS, WAITING_FOR_VOTE_OPTIONS, VOTING, SEND_OUTCOME, DONE}
    private ParticipantState currentState;

    public Participant(String[] args){
        parseArgs(args);
        this.ports = new ArrayList<>();
        this.connectionsToOtherParticipants = new HashMap<>();
        this.remainingParticipants = new ArrayList<>();
        this.voteTracker = new Voting();
        start();
    }

    private void parseArgs(String[] args){
        this.cPort = Integer.parseInt(args[0]);
        this.port = Integer.parseInt(args[1]);
        this.timeout = Integer.parseInt(args[2]);
        this.failureCondition = getFailureCondition(Integer.parseInt(args[3]));
        if (args.length > 4){
            this.ownVote = args[4];
            System.out.println("Own vote is " + ownVote);
        }

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
        try {
            this.coordinatorConnection = new Connection(cPort, this::onData, this::onParticipantDisconnect);
        } catch (IOException e) {
            System.err.println("Failed to connect to coordinator");
            e.printStackTrace();
        }
        sendJoin();
    }

    private synchronized void onData(Token token){
        System.out.println("++++ ON DATA ++++");
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
        //TODO when a participant disconnects, we don't know if he sent his vote to anyone still alive.
        System.out.println("Participant on port " + port + " has disconnected.");
        if (port == cPort){
            System.out.println("Coordinator disconnected.");
            shutdown();
            //TODO uncomment this return;
        }
        System.out.println("Should not be outputted if coordinator disconnected, as we should be shutdown");
        this.connectionsToOtherParticipants.remove(port);
        this.remainingParticipants.remove(Integer.valueOf(port));
        //TODO we might have been waiting for this guy's vote - check if we were, and if so, proceed to next step
        checkRoundEnd();
    }

    private void onDetails(DetailsToken token){
        System.out.println("Other participants include : " + token.toString());
        for (int port : token.getPorts()){
            this.ports.add(port);
        }
        this.remainingParticipants.addAll(ports);
    }

    private void onVoteOptions(VoteOptionsToken token){
        System.out.println("Our options for the vote include : " + token.toString());
        this.voteOptions = token.getOptions();
        if (currentState == ParticipantState.SEND_OUTCOME){
            //TODO restart vote
        } else {
            this.vote();
        }

    }

    private void onParticipantVote(VoteToken token){
        System.out.println("Vote received from " + token.getPort() + " (voted for " + token.getVote() + ")");
        voteTracker.voteReceived();
        voteTracker.castVote(token.getPort(), token.getVote());
        checkRoundEnd();
    }

    private void onParticipantMultiVote(MultiVoteToken token){
        System.out.println("Vote received from " + token.getSourcePort() + " (voted for " + token.getVotes().toString() + ")");
        voteTracker.voteReceived();
        for (int port : token.getVotes().keySet()){
            voteTracker.castMultiVote(token.getSourcePort(), port, token.getVotes().get(port));
        }
        checkRoundEnd();
    }

    private void checkRoundEnd(){
        System.out.println("Checking if round is over...");
        if (allVotesCast()){
            //We have finished a round. Either move on to the next round, or if there are no new votes, resolve the outcome.
            System.out.println("All votes have been cast");
            if (voteTracker.hasNewVotes()){
                if (allParticipantsKnowledgeable()){
                    System.out.println("There are new votes, but everyone knows about them");
                    resolveOutcome();
                } else {
                    System.out.println("There are still new votes and not everyone knows about them - moving on to next round");
                    nextRound();
                }

            } else {
                //Move on to outcome determination
                System.out.println("No new votes, moving on to resolution of outcome.");
                resolveOutcome();
            }
        } else {
            System.out.println("Not all votes have been cast");
        }
    }

    private boolean allParticipantsKnowledgeable(){
        System.out.println("Remaining participants : " + remainingParticipants.toString());
        for (int port : remainingParticipants){
            int knowledgeSize = voteTracker.getKnowledgeSize(port);
            System.out.println("Knowledge size of " + port + " is " + knowledgeSize);
            if (knowledgeSize < ports.size()){
                return false;
            }
        }
        return true;
    }

    private void vote(){
        System.out.println("VOTE STARTING");
        castSelfVote();
        listenForParticipants();
        connectToOtherParticipants();
        sendVoteToParticipants();
    }

    private void nextRound(){
        System.out.println("== NEW ROUND ==");
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
        this.otherParticipantsThread = new ServerThread(port, ports.size(), this::onData, this::onParticipantDisconnect);
        new Thread(otherParticipantsThread).start();
    }

    private void connectToOtherParticipants(){
        Iterator<Integer> it = ports.iterator();
        while (it.hasNext()){
            int port = it.next();
            Connection connection = connectToParticipant(port);
            if (connection != null){
                connectionsToOtherParticipants.put(port, connection);
                System.out.println("Connected to " + port + ".");
            } else {
                System.err.println("Failed to connect to port " + port + ". Removing that participant from the list of known ports.");
                it.remove();
                System.out.println("Remaining ports : " + ports.toString());
            }

        }
    }

    private Connection connectToParticipant(int port){
        try {
            System.out.println("Connecting to port " + port);
            return new Connection(port, this::onData, this::onParticipantDisconnect);
        } catch (IOException e) {
            return null;
        }
    }

    private void castSelfVote(){
        if (this.ownVote != null){
            System.out.println("Own vote was given as arg, not casting it");
        } else {
            Random r = new Random(System.currentTimeMillis());
            this.ownVote = voteOptions[r.nextInt(voteOptions.length)];
        }

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
            System.out.println("Sending token " + token.name + " to " + port);
            this.connectionsToOtherParticipants.get(port).send(token);
        }
    }

    private boolean allVotesCast(){
        System.out.println("voteTracker.getVoteCount() = " + voteTracker.getVoteCount());
        System.out.println("ports.size() = " + ports.size());
        return voteTracker.getVoteCount() - 1 == ports.size();
    }


    private void sendOutcome(){
        List<String> winningVotes = voteTracker.getWinningVotes();
        if (winningVotes.size() == 1){
            coordinatorConnection.send(new OutcomeToken(winningVotes.get(0), voteTracker.getParticipants().stream().mapToInt(Integer::intValue).toArray()));
        } else {
            coordinatorConnection.send(new OutcomeToken(null, winningVotes));
        }
        currentState = ParticipantState.SEND_OUTCOME;
        //TODO only shutdown when coordinator decides to, ie when connection with coordinator is closed
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
        coordinatorConnection.send(new JoinToken(this.port));
    }

    public static void main(String[] args){
        Participant participant = new Participant(args);
    }
}
