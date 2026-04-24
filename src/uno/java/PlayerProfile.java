package uno.java;

public class PlayerProfile {
    private String id;
    private String name;
    private int totalScore;

    // No-arg constructor for Gson
    public PlayerProfile() {}

    public PlayerProfile(String id, String name, int totalScore) {
        if (id == null || id.isBlank())     throw new IllegalArgumentException("Profile id cannot be null or blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Profile name cannot be null or blank");
        if (totalScore < 0)                 throw new IllegalArgumentException("Total score cannot be negative");
 
        this.id = id;
        this.name = name;
        this.totalScore = totalScore;
    }

    // Factory
    public static PlayerProfile from(Player player) {
    if (player == null) throw new IllegalArgumentException("Cannot create a profile from a null player");
    return new PlayerProfile(player.getId(), player.getName(), player.getScore());
    }

    // Score mgmt
    public void addScore(int points) {
        if (points < 0) throw new IllegalArgumentException("Cannot add negative points to profile score");
        this.totalScore += points;
    }


    // Getters/setters
    public String getId()       { return id; }
    public String getName()     { return name; }
    public int getTotalScore()  { return totalScore; }

    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Profile name cannot be null or blank");
        this.name = name;
    }

    public void setTotalScore(int totalScore) {
        if (totalScore < 0) throw new IllegalArgumentException("Total score cannot be negative");
        this.totalScore = totalScore;
    }

    // overrides
    
    @Override
    public String toString() {
        return "PlayerProfile{id='" + id + "', name='" + name + "', totalScore=" + totalScore + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PlayerProfile other)) return false;
        return this.id.equals(other.id);
    }
 
    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
