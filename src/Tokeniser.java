import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Tokeniser {
    public static Token parseInput(String input){
        String[] parts = input.split("\\s");
        switch (parts[0]){
            case "JOIN":
                int port = Integer.parseInt(parts[1]);
                return new JoinToken(port);
            case "DETAILS":
                ArrayList<String> portsRaw = new ArrayList<>();
                portsRaw.addAll(Arrays.asList(parts));
                portsRaw.remove(0);
                int[] ports = portsRaw.stream().map(Integer::parseInt).mapToInt(Integer::intValue).toArray();
                return new DetailsToken(ports);
            case "VOTE_OPTIONS":
                return new VoteOptionsToken(Arrays.copyOfRange(parts, 1, parts.length));
            case "VOTE":
                if (parts.length > 3){
                    //This is a multivote voken (used in subsequent rounds)
                    Map<Integer, String> votes = new HashMap<>();
                    for (int i = 1; i < parts.length - 1; i += 2){
                        int p = Integer.parseInt(parts[i]);
                        String v = parts[i+1];
                        votes.put(p, v);
                    }
                    return new MultiVoteToken(votes);
                } else {
                    //This is a single vote token (used in the first round)
                    return new VoteToken(Integer.parseInt(parts[1]), parts[2]);
                }

            case "OUTCOME":
                int[] participants = new int[parts.length - 2];
                int i = 0;
                for (String participant : Arrays.copyOfRange(parts, 2, parts.length)){
                    participants[i] = Integer.parseInt(participant);
                    i++;
                }
                return new OutcomeToken(parts[1], participants);
            default:
                System.err.println("Unknown vote : " + parts[0]);
        }
        return new JoinToken(637);
    }

}

abstract class Token {
    String name;

    public Token(String name){
        this.name = name;
    }
}

class JoinToken extends Token {

    private int port;
    private Connection connection;

    public JoinToken(int port){
        super("JOIN");
        this.port = port;
    }

    @Override
    public String toString(){
        return "JOIN " + port;
    }

    public int getPort() {
        return port;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }
}

class DetailsToken extends Token {
    //DETAILS 12347 12348
    private int[] ports;

    public DetailsToken(int[] ports){
        super("DETAILS");
        this.ports = ports;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        for (int port : ports){
            sb.append(port);
            sb.append(" ");
        }
        sb.reverse().delete(0, 1).reverse();
        return "DETAILS " + sb.toString();
    }

    public int[] getPorts() {
        return ports;
    }
}

class VoteOptionsToken extends Token {
    //VOTE_OPTIONS A B
    private String[] options;

    public VoteOptionsToken(String[] options){
        super("VOTE_OPTIONS");
        this.options = options;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        for (String option : options){
            sb.append(option);
            sb.append(" ");
        }
        sb.reverse().delete(0, 1).reverse();
        return "VOTE_OPTIONS " + sb.toString();
    }

    public String[] getOptions() {
        return options;
    }
}

class VoteToken extends Token {
    //VOTE 12346 A
    private int port;
    private String vote;

    public VoteToken(int port, String vote){
        super("VOTE");
        this.port = port;
        this.vote = vote;
    }

    @Override
    public String toString(){
        return "VOTE " + port + " " + vote;
    }

    public int getPort() {
        return port;
    }

    public String getVote() {
        return vote;
    }
}

class MultiVoteToken extends Token {
    //VOTE 12346 A 12347 B
    private Map<Integer, String> votes;

    public MultiVoteToken(Map<Integer, String> votes){
        super("VOTE");
        this.votes = votes;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        for (int port : votes.keySet()){
            sb.append(port);
            sb.append(" ");
            sb.append(votes.get(port));
            sb.append(" ");
        }
        return "VOTE " + sb.toString();
    }

    public Map<Integer, String> getVotes(){
        return votes;
    }
}

class OutcomeToken extends Token {
    //OUTCOME A 12346 12347 12348
    private String outcome;
    private int[] voters; //ie. voters taken into account when determining the outcome

    public OutcomeToken(String outcome, int[] voters){
        super("OUTCOME");
        this.outcome = outcome;
        this.voters = voters;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        for (int voter : voters){
            sb.append(voter);
            sb.append(" ");
        }
        sb.reverse().delete(0, 1).reverse();
        return "OUTCOME " + outcome + " " + sb.toString();
    }

    public String getOutcome() {
        return outcome;
    }

    public int[] getVoters() {
        return voters;
    }
}
