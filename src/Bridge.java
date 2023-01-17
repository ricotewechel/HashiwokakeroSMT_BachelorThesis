public class Bridge {
    private final Node a;
    private final Node b;
    private int amount;

    public Bridge(Node a, Node b) {
        this.a = a;
        this.b = b;
        this.amount = 0;
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
}