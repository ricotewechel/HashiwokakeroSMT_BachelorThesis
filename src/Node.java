public class Node{
    private final int col;
    private final int row;
    private final int value;

    public Node(int col, int row, int value) {
        this.col = col;
        this.row = row;
        this.value = value;
    }

    public int getCol() {
        return this.col;
    }

    public int getRow() {
        return this.row;
    }

    public int getValue() { return this.value; }

    @Override
    public String toString() {
        return (Integer.toString(this.value));
    }
}
