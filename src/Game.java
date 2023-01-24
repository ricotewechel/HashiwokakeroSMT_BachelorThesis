import scala.concurrent.impl.FutureConvertersImpl;

import java.math.BigInteger;
import java.util.*;

public class Game {
    private final int fieldSize;
    private final ArrayList<Node> nodes = new ArrayList<>();
    private final ArrayList<Bridge> bridges = new ArrayList<>();

    public Game(int size, String encoding) {
        this.fieldSize = size;
        int count = 0;
        for (char c : encoding.toCharArray()) { // Decode puzzle
            if (Character.isAlphabetic(c)) { // Letter case
                count += c - 96;
            } else if (Character.isDigit(c)) { // Number case
                Node node = new Node(count % this.fieldSize, count / this.fieldSize, Character.getNumericValue(c));
                this.nodes.add(node);
                count++;
            } else System.out.println("Error");
        }
        for (Node node : this.nodes) { // Determine all possible bridges
            this.findNodeEast(node);
            this.findNodeSouth(node);
        }
    }

    // Works because list is sorted from left to right top to bottom
    private void findNodeEast(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getRow() == this.nodes.get(i).getRow()) {
                this.bridges.add(new Bridge(node, this.nodes.get(i), null, Bridge.Direction.HORIZONTAL));
                return; // TODO geen void maar return bridge
            }
        }
    }

    // Works because list is sorted from left to right top to bottom
    private void findNodeSouth(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getCol() == this.nodes.get(i).getCol()) {
                this.bridges.add(new Bridge(node, this.nodes.get(i), null, Bridge.Direction.VERTICAL));
                return; // TODO geen void maar return bridge
            }
        }
    }

    public ArrayList<Node> getNodes() {
        return this.nodes;
    }

    public ArrayList<Bridge> getBridges() {
        return this.bridges;
    }

    // Sets bridges based on solution of SMT solver
    public void setBridgeWeights(ArrayList<BigInteger> solution) {
        if (solution.size() != this.bridges.size()) {
            System.out.println("Solution error");
        }
        else {
            for (int i = 0; i < this.bridges.size(); i++) {
                this.bridges.get(i).setWeight(solution.get(i));
            }
        }
    }

    public void printGame() { // TODO tostring
        // Define field
        char[][] field = new char[this.fieldSize][this.fieldSize];
        for (int i = 0; i < this.fieldSize; i++) {
            for (int j = 0; j < this.fieldSize; j++) {
                field[i][j] = '.';
            }
        }

        // Fill field with node values (setup)
        for (Node n : this.nodes) {
            field[n.getRow()][n.getCol()] = (char) (n.getValue() + '0');
        }

        // Fill field with bridges (solution)   TODO dit wil je eigenlijk met graphics
        for (Bridge b : this.bridges) {
            if (b.getWeight().intValue() == 1) {
                if (b.getDirection() == Bridge.Direction.HORIZONTAL) {
                    for (int i = b.getA().getCol() + 1; i < b.getB().getCol(); i++) {
                        field[b.getA().getRow()][i] = '─';
                    }
                } else if (b.getDirection() == Bridge.Direction.VERTICAL) {
                    for (int i = b.getA().getRow() + 1; i < b.getB().getRow(); i++) {
                        field[i][b.getA().getCol()] = '│';
                    }
                }
            } else if (b.getWeight().intValue() == 2) {
                if (b.getDirection() == Bridge.Direction.HORIZONTAL) {
                    for (int i = b.getA().getCol() + 1; i < b.getB().getCol(); i++) {
                        field[b.getA().getRow()][i] = '═';
                    }
                } else if (b.getDirection() == Bridge.Direction.VERTICAL) {
                    for (int i = b.getA().getRow() + 1; i < b.getB().getRow(); i++) {
                        field[i][b.getA().getCol()] = '‖';
                    }
                }
            }
        }

        // Print    TODO split print van logica
        for (int i = 0; i < this.fieldSize; i++) {
            for (int j = 0; j < this.fieldSize; j++) {
                System.out.print(field[i][j]);
                System.out.print('\t');
            }
            System.out.print('\n');
        }
    }

}
