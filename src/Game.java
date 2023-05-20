import java.math.BigInteger;
import java.util.*;

public class Game {
    private int fieldSize;
    private char[][] field;
    private final ArrayList<Node> nodes;
    private final ArrayList<Bridge> bridges = new ArrayList<>();

    public Game(String encoding, String id) {
        this.nodes = parseID(id);
        if (encoding.equals("improved")) {
            System.out.println("Trying to solve puzzle with improved encoding...");
            for (Node node : this.nodes) { // Determine all possible bridges
                Bridge east = this.findBridgeEast(node);
                if (east != null)
                    this.bridges.add(east);
                Bridge south = this.findBridgeSouth(node);
                if (south != null)
                    this.bridges.add(south);
            }
        }
        else if (encoding.equals("naive")) {
            System.out.println("Trying to solve puzzle with naive encoding...");
        }
        else throw new RuntimeException("Invalid encoding option");
    }

    // Parses the url suffix. Assumes a valid encoding is given
    private ArrayList<Node> parseID (String encoding) {
        String[] parts = encoding.split(":");
        if (parts[0].endsWith("L"))
            throw new RuntimeException("Loops may not be prohibited");

        String[] properties = parts[0].split("m");
        if(!properties[1].equals("2"))
            throw new RuntimeException("Max bridge size should be 2");

        String[] dimensions = properties[0].split("x");
        if (!(Integer.parseInt(dimensions[0]) == Integer.parseInt(dimensions[1])))
            throw new RuntimeException("Puzzles should be square");

        this.fieldSize = Integer.parseInt(dimensions[0]);
        this.field = new char[this.fieldSize][this.fieldSize];
        ArrayList<Node> nodeList = new ArrayList<>();
        int count = 0;
        for (char c : parts[1].toCharArray()) { // Decode puzzle
            if (Character.isAlphabetic(c)) // Letter case
                count += c - 96;
            else if (Character.isDigit(c)) { // Number case
                nodeList.add(new Node(count / this.fieldSize, count % this.fieldSize, Character.getNumericValue(c)));
                count++;
            } else System.out.println("Invalid puzzle encoding");
        }
        return nodeList;
    }

    public ArrayList<Node> getNodes() {
        return this.nodes;
    }

    public int getFieldSize() {
        return this.fieldSize;
    }

    public ArrayList<Bridge> getBridges() {
        return this.bridges;
    }

    // For improved encoding only
    // Works because list is sorted from left to right top to bottom
    private Bridge findBridgeEast(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getRow() == this.nodes.get(i).getRow())
                return new Bridge(node, this.nodes.get(i), null, Bridge.Direction.HORIZONTAL);
        }
        return null;
    }

    // For improved encoding only
    // Works because list is sorted from left to right top to bottom
    private Bridge findBridgeSouth(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getCol() == this.nodes.get(i).getCol())
                return new Bridge(node, this.nodes.get(i), null, Bridge.Direction.VERTICAL);
        }
        return null;
    }

    // For improved encoding only
    // Returns all bridges connected to a node, regardless of weight
    public ArrayList<Bridge> getBridgesFrom(Node node) {
        ArrayList<Bridge> temp = new ArrayList<>();
        for (Bridge b : this.bridges) {
            if (b.getA().equals(node) || b.getB().equals(node))
                temp.add(b);
        }
        return temp;
    }

    // For improved encoding only
    // Sets bridges based on solution of SMT solver
    public void setBridgeWeights(ArrayList<BigInteger> solution) {
        if (solution.size() != this.bridges.size())
            throw new RuntimeException("Solution size error");
        else {
            for (int i = 0; i < this.bridges.size(); i++) {
                this.bridges.get(i).setWeight(solution.get(i));
            }
        }
    }

    // For improved encoding only
    public void fillFieldImproved() {
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
                if (this.field[i][j] == '\0')
                    this.field[i][j] = ' ';
            }
        }
    }

    public void fillFieldNaive(BigInteger[][] solution) {
        HashMap<BigInteger, Character> mapping = new HashMap<>() {{
            put(BigInteger.valueOf(0), ' ');
            put(BigInteger.valueOf(1), '─');
            put(BigInteger.valueOf(2), '═');
            put(BigInteger.valueOf(3), '|');
            put(BigInteger.valueOf(4), '‖');
        }};

        int n = 0;
        for (int i = 0; i < this.fieldSize; i++) {
            for (int j = 0; j < this.fieldSize; j++) {
                if (solution[i][j].equals(BigInteger.valueOf(5))) {
                    this.field[i][j] = (char) (this.nodes.get(n).getValue() + '0');
                    n++;
                } else this.field[i][j] = mapping.get(solution[i][j]);
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
