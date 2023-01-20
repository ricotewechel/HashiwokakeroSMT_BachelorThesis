import scala.concurrent.impl.FutureConvertersImpl;

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
                Node node = new Node(count / this.fieldSize, count % this.fieldSize, c);
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
            if (node.getX() == this.nodes.get(i).getX()) {
                this.bridges.add(new Bridge(node, this.nodes.get(i), Bridge.Direction.HORIZONTAL));
                return; // TODO geen void maar return bridge
            }
        }
    }

    // Works because list is sorted from left to right top to bottom
    private void findNodeSouth(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getY() == this.nodes.get(i).getY()) {
                this.bridges.add(new Bridge(node, this.nodes.get(i), Bridge.Direction.VERTICAL));
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

    public void printGame() { // TODO tostring
        // Define field
        char[][] field = new char[this.fieldSize][this.fieldSize];
        for (int i = 0; i < this.fieldSize; i++) {
            for (int j = i; j < this.fieldSize; j++) {
                field[i][j] = '.';
            }
        }

        // Fill field with node values (setup)
        for (Node n : this.nodes) {
            field[n.getX()][n.getY()] = (char) n.getVal();
        }

        // Fill field with bridges (solution)
        // TODO


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
