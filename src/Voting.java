import java.util.*;
import java.util.stream.Collectors;

public class Voting {
    private Map<Integer, String> votes; //Mapping from port to vote
    private Map<String, Integer> voteCounter; //Mapping from vote to votecount
    private ArrayList<Integer> participants;

    public Voting(){
        this.votes = new HashMap<>();
        this.participants = new ArrayList<>();
        this.voteCounter = new HashMap<>();
    }

    public void castVote(int port, String vote){
        System.out.println("Cast from " + port + " for " + vote);
        this.votes.put(port, vote);
        if (participants.contains(port)){
            System.err.println(port + " has already voted");
        }
        this.participants.add(port);
        this.voteCounter.put(vote, voteCounter.getOrDefault(vote, 0) + 1);
    }

    public List<Integer> getParticipants(){
        return participants;
    }

    public int getVoteCount(){
        return votes.size();
    }

    public boolean isTie(){
        List<Integer> votesSorted = votes.keySet().stream().sorted().collect(Collectors.toList());
        //Are the top 2 votes equal? then we have a tie
        return votesSorted.get(votesSorted.size() - 2).equals(votesSorted.get(votesSorted.size() - 1));
    }

    public String getWinningVote(){
        //TODO Need to consider own vote in the final count
        int maxVote = Collections.max(voteCounter.values());
        String winningVote;
        List<String> winningVotes = voteCounter.keySet().stream().filter(key -> voteCounter.get(key).equals(maxVote)).collect(Collectors.toList());
        System.out.println("Winning votes : " + winningVotes.toString());
        System.out.println("OUTCOME resolved : ");
        winningVote = winningVotes.get(0);
        System.out.println(winningVote);
        System.out.println(voteCounter.toString());
        return winningVote;
    }

}
