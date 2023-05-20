public class Node{
    private final int col;
    private final int row;
    private final int value;

    public Node(int row, int col, int value) {
        this.row = row;
        this.col = col;
        this.value = value;
    }

    public int getCol() {
        return this.col;
    }

    public int getRow() {
        return this.row;
    }

    public int getValue() { return this.value; }

    public String toString() {
        return (Integer.toString(this.col) + '\t' + this.row + '\t' + this.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Node node))
            return false;
        return this.col == node.col && this.row == node.row;
    }
}
