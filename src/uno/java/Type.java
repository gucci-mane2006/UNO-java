package uno.java;

enum Type {
    NORMAL,
    WILD, 
    SKIP, 
    REVERSE,
    DRAW_TWO,
    DRAW_FOUR;

    boolean isWildType() {
        return this == WILD || this == DRAW_FOUR;
    }

    boolean isActionType() {
        return this == SKIP || this == REVERSE || this == DRAW_TWO;
    }

    boolean isNormalType() {
        return this == NORMAL;
    }
};
