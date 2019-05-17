import java.util.*;
import java.util.stream.Collectors;

public class Voting {
    private Map<Integer, String> votes; //Mapping from port to vote
    private Map<Integer, String> newVotes; //Resets every round, after votes are sent to other participants.
    private Map<String, Integer> voteCounter; //Mapping from vote to vote count
    private Map<Integer, Set<Integer>> participantVoteKnowledge;
    private ArrayList<Integer> roundParticipants; //Participants in each round. Gets cleared at the beginning of a round.
    private int roundParticipantCounter; //Count the number of participants in each round. Used to determine end of round.
    private ArrayList<Integer> participants;

    public Voting(){
        this.votes = new HashMap<>();
        this.newVotes = new HashMap<>();
        this.participants = new ArrayList<>();
        this.participantVoteKnowledge = new HashMap<>();
        this.roundParticipants = new ArrayList<>();
        this.roundParticipantCounter = 0;
        this.voteCounter = new HashMap<>();
    }

    //On a single vote ie. VOTE 12346 B the token is sent by the voter itself.
    public void castVote(int port, String vote){
        castMultiVote(port, port, vote);
    }

    //This method is needed as in multivotes the sourceport cannot be determined by the contents of the token
    // sourcePort = who cast this vote
    public void castMultiVote(int sourcePort, int port, String vote){
        if (participantVoteKnowledge.keySet().contains(sourcePort)){
            Set<Integer> knowledge = participantVoteKnowledge.get(sourcePort);
            knowledge.add(port);
            participantVoteKnowledge.put(sourcePort, knowledge);
        } else {
            Set<Integer> knowledge = new HashSet<>();
            knowledge.add(port);
            participantVoteKnowledge.put(sourcePort, knowledge);
        }
        if (Participant.verbose)
            System.out.println("Knowledge of " + sourcePort + " has expanded to " + participantVoteKnowledge.get(sourcePort).toString());
        if (participants.contains(port)){
            if (Participant.verbose)
                System.err.println(port + " has already voted");
            return;
        }
        if (votes.keySet().contains(port)){
            if (Participant.verbose)
                System.out.println("We already have " + port + "'s vote taken into account");
            return;
        }
        if (Participant.verbose)
            System.out.println("Cast from " + sourcePort + " for voter on port " + port + " for vote " + vote);
        this.votes.put(port, vote);

        if (Participant.verbose)
            System.out.println(votes.toString());

        /*if (roundParticipants.contains(sourcePort)){
            System.err.println(port + " has already voted this round");
        }*/
        roundParticipants.add(sourcePort);

        this.participants.add(port);
        this.newVotes.put(port, vote);
        this.voteCounter.put(vote, voteCounter.getOrDefault(vote, 0) + 1);
    }

    public void voteReceived(){
        roundParticipantCounter++;
    }

    public List<Integer> getParticipants(){
        return new ArrayList<>(votes.keySet());
    }

    public int getRoundVoteCount(){
        return roundParticipantCounter;
    }

    public int getVoteCount(){
        return votes.keySet().size();
    }

    public int getKnowledgeSize(int port){
        if (participantVoteKnowledge.containsKey(port)){
            return participantVoteKnowledge.get(port).size();
        }
        return -1;
    }

    public boolean hasNewVotes(){
        return !newVotes.isEmpty();
    }

    public Map<Integer, String> getNewVotes(){
        return newVotes;
    }

    public void nextRound(){
        this.roundParticipantCounter = 0;
        this.newVotes.clear();
        this.participants.clear();
    }

    /**
     * @return singleton if winning vote, list of popular options in case of majority
     */
    public List<String> getWinningVotes(){
        int maxVote = Collections.max(voteCounter.values());
        System.out.println("Votes : " + voteCounter);
        if (maxVote < votes.size()/2 + 1){
            //This is not a majority
            int minVote = Collections.min(voteCounter.values());
            for (String vote : voteCounter.keySet()){
                if (voteCounter.get(vote).equals(minVote)){
                    voteCounter.remove(vote);
                    break;
                }
            }
            return new ArrayList<>(voteCounter.keySet());
        } else {
            //This is a majority
            System.out.println("Returning majority : " + voteCounter.keySet().stream().filter(key -> voteCounter.get(key).equals(maxVote)).collect(Collectors.toList()).toString());
            return voteCounter.keySet().stream().filter(key -> voteCounter.get(key).equals(maxVote)).collect(Collectors.toList());
        }
    }

}
