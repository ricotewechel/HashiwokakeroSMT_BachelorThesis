public class Bridge {
    private final Node a;
    private final Node b;
    private int amount;
    public enum Direction {
        HORIZONTAL,
        VERTICAL
    }
    private final Direction direction;

    public Bridge(Node a, Node b, Direction d) {
        this.a = a;
        this.b = b;
        this.amount = 0;
        this.direction = d;
    }

    public Node getA() {
        return a;
    }

    public Node getB() {
        return b;
    }

    public int getAmount() {
        return amount;
    }

    public Direction getDirection() {
        return direction;
    }
}