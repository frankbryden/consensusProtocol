import java.util.*;
import java.util.stream.Collectors;

public class Coordinator {
    //Initialisation members
    private int port;
    private int expectedParticipants;
    private String[] voteOptions;

    //Runtime members
    private ServerThread serverThread;
    private ArrayList<Integer> ports;
    private ArrayList<Integer> remainingParticipants; //Participants are removed from the list as they fail
    private Map<Integer, Connection> participants;
    private List<OutcomeToken> outcomes;

    private enum CoordinatorState {WAITING_FOR_PARTICIPANTS, SENDING_DETAILS, SENDING_VOTING_OPTIONS, WAITING_FOR_OUTCOME, VOTE_RESTART, DONE}
    private CoordinatorState currentState;

    public Coordinator(String[] args){
        parseArgs(args);
        this.serverThread = new ServerThread(port, expectedParticipants, this::onParticipantData, this::onParticipantDisconnect);
        this.ports = new ArrayList<>(expectedParticipants);
        this.remainingParticipants = new ArrayList<>(expectedParticipants);
        this.participants = new HashMap<>();
        this.outcomes = new ArrayList<>();
        start();
    }

    private void parseArgs(String[] args){
        if (args.length < 3){
            System.err.println("Missing arguments");
            System.exit(0);
        }
        this.port = Integer.parseInt(args[0]);
        this.expectedParticipants = Integer.parseInt(args[1]);
        this.voteOptions = Arrays.copyOfRange(args, 2, args.length);
    }

    private void start(){
        this.currentState = CoordinatorState.WAITING_FOR_PARTICIPANTS;
        Thread thread = new Thread(serverThread);
        thread.start();
    }

    private synchronized void onParticipantData(Token token){
        if (token instanceof JoinToken){
            onJoin((JoinToken) token);
        } else if (token instanceof OutcomeToken){
            onOutcome((OutcomeToken) token);
        }
    }

    private synchronized void onParticipantDisconnect(int port){
        System.out.println("Participant disconnect on port " + port);
        this.remainingParticipants.remove(Integer.valueOf(port));
        checkOutcomeRoundEnd();
    }

    private void onJoin(JoinToken token){
        System.out.println("A participant has joined!");
        System.out.println(token);
        this.ports.add(token.getPort());
        this.remainingParticipants.add(token.getPort());
        this.participants.put(token.getPort(), token.getConnection());
        if (checkIfAllParticipantsJoined()){
            sendDetails();
            sendVotingOptions();
        }
    }

    private void onOutcome(OutcomeToken token){
        System.out.println("Outcome received from " + token.getSourcePort() + " : " + token.getOutcome() + ", " + token.getTiedOptions());
        outcomes.add(token);
        checkOutcomeRoundEnd();
    }

    private void checkOutcomeRoundEnd(){
        if (currentState == CoordinatorState.DONE){
            return;
        }
        if (allOutcomesReceived()){
            currentState = CoordinatorState.DONE;
            conclude();
        }
    }

    private void sendDetails(){
        this.currentState = CoordinatorState.SENDING_DETAILS;
        Map<Integer, List<Integer>> detailsMapping = getDetailsMapping();
        for (int port: detailsMapping.keySet()){
            //TODO send detailsMapping.get(port) to port
            participants.get(port).send(new DetailsToken(detailsMapping.get(port).stream().mapToInt(Integer::intValue).toArray()));
        }
    }

    private void sendVotingOptions(){
        this.currentState = CoordinatorState.SENDING_VOTING_OPTIONS;
        VoteOptionsToken voteOptionsToken = new VoteOptionsToken(voteOptions);
        serverThread.sendToAll(voteOptionsToken);
        currentState = CoordinatorState.WAITING_FOR_OUTCOME;
    }

    private void restartVote(){
        currentState = CoordinatorState.VOTE_RESTART;
        outcomes.clear();
        sendVotingOptions();
    }

    private void conclude(){
        System.out.println();
        List<OutcomeToken> filtered = outcomes.stream().filter(n -> n.getOutcome() != null).filter(n -> !outcomes.get(0).getOutcome().equals(n.getOutcome())).collect(Collectors.toList());
        if (filtered.size() > 0){
            System.out.println("We were not able to conclude, not all peers agree");
            System.out.println(outcomes);
        } else {
            if (outcomes.get(0).getOutcome() == null){
                System.out.println("Tie or no majority, we need to rerun the vote with fewer options");

                List<String> tiedOptions = outcomes.get(0).getTiedOptions();
                voteOptions = new String[tiedOptions.size()];
                voteOptions = tiedOptions.toArray(voteOptions);
                restartVote();
            } else {
                System.out.println("Conclusion was made! The outcome of the vote was " + outcomes.get(0).getOutcome());
                if (outcomes.get(0).getVoters().length == expectedParticipants){
                    System.out.println("Every participant was taken into account when deciding the outcome of the vote.");
                } else {
                    System.out.println("Unfortunately, not every participant was taken into account when resolving the outcome of the vote.");
                }
                killConnections();
                System.out.println("Killed connections");
            }
        }
    }

    private void killConnections(){
        for (Connection connection : participants.values()){
            connection.stop();
        }
    }

    private boolean checkIfAllParticipantsJoined(){
        if (this.ports.size() == expectedParticipants){
            System.out.println("All participants have joined, moving on");
            return true;
        }
        return false;
    }

    private Map<Integer, List<Integer>> getDetailsMapping(){
        //Returns a mapping where the key is the port to send to and the value is the details of the other ports
        Map<Integer, List<Integer>> mapping = new HashMap<>();
        for (int port : ports){
            List<Integer> portsToSend = new ArrayList<>();
            for (int otherPort : ports){
                if (otherPort != port){
                    portsToSend.add(otherPort);
                }
            }
            mapping.put(port, portsToSend);
        }
        return mapping;
    }

    private boolean allOutcomesReceived(){
        return outcomes.size() == remainingParticipants.size();
    }

    public static void main(String[] args){
        Coordinator coordinator = new Coordinator(args);
    }
}
