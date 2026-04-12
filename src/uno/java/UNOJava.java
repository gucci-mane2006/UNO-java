package uno.java;

public class UNOJava {
    public static void main(String[] args) {
        Card c1 = Card.numberCard(Color.RED, 5);
        Card c2 = Card.actionCard(Color.BLUE, Type.SKIP);
        Card c3 = Card.wildCard();
        Card c4 = Card.drawFourCard();

        System.out.println(c1);
        System.out.println(c2);
        System.out.println(c3);
        System.out.println(c4);
    }
}
