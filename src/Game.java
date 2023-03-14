import java.math.BigInteger;
import java.util.*;

public class Game {
    private final int fieldSize;
    private final char[][] field;
    private final ArrayList<Node> nodes = new ArrayList<>();
    private final ArrayList<Bridge> bridges = new ArrayList<>();

    public Game(int size, String encoding) { // TODO Extend parser to include fieldsize, loops, and bridge width
        this.fieldSize = size;
        this.field = new char[this.fieldSize][this.fieldSize];
        int count = 0;
        for (char c : encoding.toCharArray()) { // Decode puzzle
            if (Character.isAlphabetic(c)) { // Letter case
                count += c - 96;
            } else if (Character.isDigit(c)) { // Number case
                Node node = new Node(count % this.fieldSize, count / this.fieldSize, Character.getNumericValue(c));
                this.nodes.add(node);
                count++;
            } else System.out.println("Error while decoding");
        }
        for (Node node : this.nodes) { // Determine all possible bridges
            Bridge east = this.findBridgeEast(node);
            if (east != null) {
                this.bridges.add(east);
            }
            Bridge south = this.findBridgeSouth(node);
            if (south != null) {
                this.bridges.add(south);
            }
        }
    }

    // Works because list is sorted from left to right top to bottom
    private Bridge findBridgeEast(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getRow() == this.nodes.get(i).getRow()) {
                return new Bridge(node, this.nodes.get(i), null, Bridge.Direction.HORIZONTAL);
            }
        }
        return null;
    }

    // Works because list is sorted from left to right top to bottom
    private Bridge findBridgeSouth(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getCol() == this.nodes.get(i).getCol()) {
                return new Bridge(node, this.nodes.get(i), null, Bridge.Direction.VERTICAL);
            }
        }
        return null;
    }

    public ArrayList<Node> getNodes() {
        return this.nodes;
    }

    public ArrayList<Bridge> getBridges() {
        return this.bridges;
    }

    // Returns all bridges connected to a node, regardless of weight
    public ArrayList<Bridge> findNeighbors(Node node) {
        ArrayList<Bridge> temp = new ArrayList<>();
        for (Bridge b : this.bridges) {
            if (b.getA().equals(node) && b.getDirection() == Bridge.Direction.HORIZONTAL) { // East neighbor
                temp.add(b);
            }
            else if (b.getA().equals(node) && b.getDirection() == Bridge.Direction.VERTICAL) { // South neighbor
                temp.add(b);
            }
            else if (b.getB().equals(node) && b.getDirection() == Bridge.Direction.HORIZONTAL) { // West neighbor
                temp.add(b);
            }
            else if (b.getB().equals(node) && b.getDirection() == Bridge.Direction.VERTICAL) { // North neighbor
                temp.add(b);
            }
        }
        return temp;
    }

    // Sets bridges based on solution of SMT solver
    public void setBridgeWeights(ArrayList<BigInteger> solution) {
        if (solution.size() != this.bridges.size()) {
            System.out.println("Solution size error");
        }
        else {
            for (int i = 0; i < this.bridges.size(); i++) {
                this.bridges.get(i).setWeight(solution.get(i));
            }
        }
    }

    public void fillField() { // TODO implement this with graphics
        // Fill field with node values (setup)
        for (Node n : this.nodes) {
            this.field[n.getRow()][n.getCol()] = (char) (n.getValue() + '0');
        }

        // Fill field with bridges (solution)
        for (Bridge b : this.bridges) {
            if (b.getWeight().intValue() == 1) {
                if (b.getDirection() == Bridge.Direction.HORIZONTAL) {
                    for (int i = b.getA().getCol() + 1; i < b.getB().getCol(); i++) {
                        this.field[b.getA().getRow()][i] = '─';
                    }
                } else if (b.getDirection() == Bridge.Direction.VERTICAL) {
                    for (int i = b.getA().getRow() + 1; i < b.getB().getRow(); i++) {
                        this.field[i][b.getA().getCol()] = '|';
                    }
                }
            } else if (b.getWeight().intValue() == 2) {
                if (b.getDirection() == Bridge.Direction.HORIZONTAL) {
                    for (int i = b.getA().getCol() + 1; i < b.getB().getCol(); i++) {
                        this.field[b.getA().getRow()][i] = '═';
                    }
                } else if (b.getDirection() == Bridge.Direction.VERTICAL) {
                    for (int i = b.getA().getRow() + 1; i < b.getB().getRow(); i++) {
                        this.field[i][b.getA().getCol()] = '‖';
                    }
                }
            }
        }

        // Fill the rest with spaces
        for (int i = 0; i < this.fieldSize; i++) {
            for (int j = 0; j < this.fieldSize; j++) {
                if (this.field[i][j] == '\0') {
                    this.field[i][j] = ' ';
                }
            }
        }
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        for (int i = 0 ; i < this.fieldSize; i++) {
            for (int j = 0 ; j < this.fieldSize; j++) {
                s.append(this.field[i][j]).append(" ");
            }
            s.append("\n");
        }
        return s.toString();
    }

    // TODO solution check function
}
