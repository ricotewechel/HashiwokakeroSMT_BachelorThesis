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

    public String toString() {
        return (Integer.toString(this.col) + '\t' + this.row + '\t' + this.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Node node)) return false;
        return this.col == node.col && this.row == node.row && this.value == node.value;
    }
}
