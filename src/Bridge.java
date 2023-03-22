import java.math.BigInteger;

public class Bridge {
    private final Node a;
    private final Node b;
    private BigInteger weight;
    public enum Direction {
        HORIZONTAL,
        VERTICAL
    }
    private final Direction direction;

    public Bridge(Node a, Node b, BigInteger w, Direction d) {
        this.a = a;
        this.b = b;
        this.weight = w;
        this.direction = d;
    }

    public Node getA() {
        return this.a;
    }

    public Node getB() {
        return this.b;
    }

    public BigInteger getWeight() {
        return this.weight;
    }

    public void setWeight(BigInteger w) {
        this.weight = w;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public String toString() {
        if (this.weight.intValue() == 1) {
            if (this.direction == Direction.HORIZONTAL)
                return (this.a.getCol() + "\t" + this.a.getRow() + "\t\t" + "─" + "\t\t" + this.b.getCol() + "\t" + this.b.getRow());
            else if (this.direction == Direction.VERTICAL)
                return (this.a.getCol() + "\t" + this.a.getRow() + "\t\t" + "|" + "\t\t" + this.b.getCol() + "\t" + this.b.getRow());
            else return "";
        } else if (this.weight.intValue() == 2) {
            if (this.direction == Direction.HORIZONTAL)
                return (this.a.getCol() + "\t" + this.a.getRow() + "\t\t" + "═" + "\t\t" + this.b.getCol() + "\t" + this.b.getRow());
            else if (this.direction == Direction.VERTICAL)
                return (this.a.getCol() + "\t" + this.a.getRow() + "\t\t" + "‖" + "\t\t" + this.b.getCol() + "\t" + this.b.getRow());
            else return "";
        } else return "";
    }
}