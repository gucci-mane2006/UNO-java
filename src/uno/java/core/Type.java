package uno.java.core;

public enum Type {
    NORMAL,
    WILD, 
    SKIP, 
    REVERSE,
    DRAW_TWO,
    DRAW_FOUR;

    public boolean isWildType() {
        return this == WILD || this == DRAW_FOUR;
    }

    public boolean isActionType() {
        return this == SKIP || this == REVERSE || this == DRAW_TWO;
    }

    public boolean isNormalType() {
        return this == NORMAL;
    }
};
