public class Node{
    private final int x;
    private final int y;
    private final int value;

    public Node(int xCoord, int yCoord, int value) {
        this.x = xCoord;
        this.y = yCoord;
        this.value = value;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getVal() { return this.value; }

    @Override
    public String toString() {
        return (Integer.toString(this.value - 48));
    }

/*    @Override
    public int compareTo(Node node) {
        if (this.x > node.x) {
            return 1;
        }
        else if (this.x < node.x) {
            return -1;
        }
        else {
            return Integer.compare(this.y, node.y);
        }
    }*/
}
