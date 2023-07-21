import java.io.*;
import java.math.BigInteger;
import java.util.*;

public class Game implements Serializable {
    private int fieldSize;
    private char[][] field;
    private final ArrayList<Node> nodes;
    private final ArrayList<Bridge> bridges;

    public Game(int fieldSize, ArrayList<Node> nodes, ArrayList<Bridge> bridges) { // For creating game from node and bridge lists
        this.fieldSize = fieldSize;
        this.field = new char[this.fieldSize][this.fieldSize];
        this.nodes = nodes;
        this.bridges = bridges;
    }

    public Game(String id) { // For creating game from GameID strings
        this.nodes = parseID(id);
        this.bridges = new ArrayList<>();
        this.setPossibleBridges(); // Determine all possible bridges
        this.setNullBridgesToZero();
    }

    public Game(Game other) { // Copy constructor
        this.fieldSize = other.getFieldSize();
        this.field = new char[this.fieldSize][this.fieldSize];

        ArrayList<Node> nodeListCopy = new ArrayList<>();
        for (Node n : other.getNodes()) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(n);
                oos.flush();
                ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(bis);
                Node nodeCopy = (Node) ois.readObject();
                nodeListCopy.add(nodeCopy);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        this.nodes = nodeListCopy;

        ArrayList<Bridge> bridgeListCopy = new ArrayList<>();
        for (Bridge b : other.getBridges()) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(b);
                oos.flush();
                ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(bis);
                Bridge bridgeCopy = (Bridge) ois.readObject();
                bridgeListCopy.add(bridgeCopy);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        this.bridges = bridgeListCopy;
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
            } else throw new RuntimeException("Invalid puzzle encoding");
        }
        return nodeList;
    }

    // Works because list is sorted from left to right top to bottom
    private Bridge findBridgeEast(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getRow() == this.nodes.get(i).getRow())
                return new Bridge(node, this.nodes.get(i), null, Bridge.Direction.HORIZONTAL);
        }
        return null;
    }

    // Works because list is sorted from left to right top to bottom
    private Bridge findBridgeSouth(Node node) {
        for (int i = this.nodes.indexOf(node) + 1; i < this.nodes.size(); i++) {
            if (node.getCol() == this.nodes.get(i).getCol())
                return new Bridge(node, this.nodes.get(i), null, Bridge.Direction.VERTICAL);
        }
        return null;
    }

    public int getFieldSize() {
        return this.fieldSize;
    }

    public char[][] getField() {
        return field;
    }

    public ArrayList<Node> getNodes() {
        return this.nodes;
    }

    public ArrayList<Bridge> getBridges() {
        return this.bridges;
    }

    public void addNode(Node node) {
        this.nodes.add(node);
    }

    public void addBridge(Bridge bridge) {
        this.bridges.add(bridge);
    }

    public void removeBridges() {
        this.bridges.clear();
    }

    public void sortNodes() {
        this.nodes.sort(Comparator.comparingInt(Node::getRow).thenComparingInt(Node::getCol));
    }

    public void sortBridges() {
        this.bridges.sort((bridge1, bridge2) -> {
            // Compare row values of Node a first
            int rowComparison = Integer.compare(bridge1.getA().getRow(), bridge2.getA().getRow());
            if (rowComparison != 0) {
                return rowComparison;
            }
            // If row values are equal, compare col values of Node a
            int colComparison = Integer.compare(bridge1.getA().getCol(), bridge2.getA().getCol());
            if (colComparison != 0) {
                return colComparison;
            }
            // If col values are equal, compare the direction
            return bridge1.getDirection().compareTo(bridge2.getDirection());
        });
    }

    public void setNullBridgesToZero() {
        for (Bridge b : this.bridges) {
            if (b.getWeight() == null)
                b.setWeight(BigInteger.ZERO);
        }
    }

    public void setPossibleBridges() {
        for (Node node : this.nodes) { // Determine all possible bridges
            Bridge east = this.findBridgeEast(node);
            if (east != null && !this.bridges.contains(east))
                this.bridges.add(east);
            Bridge south = this.findBridgeSouth(node);
            if (south != null && !this.bridges.contains(south))
                this.bridges.add(south);
        }
    }

    // Returns all bridges connected to a node, regardless of weight
    public ArrayList<Bridge> getBridgesFrom(Node node) {
        ArrayList<Bridge> temp = new ArrayList<>();
        for (Bridge b : this.bridges) {
            if (b.getA().equals(node) || b.getB().equals(node))
                temp.add(b);
        }
        return temp;
    }

    // For graph encoding only
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

    // For graph encoding only (and generating when this.bridges is empty)
    public void fillFieldGraphEncoding() {
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

    public void fillFieldGridEncoding(BigInteger[][] solution) {
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

    public BigInteger[][] getFieldEncoding() {
        BigInteger[][] encoding = new BigInteger[this.getFieldSize()][this.getFieldSize()];

        // All nodes should have encoding value 5
        for (Node n : this.nodes) {
            encoding[n.getRow()][n.getCol()] = BigInteger.valueOf(5);
        }

        // The bridges should be translated to pieces and their encoding numbers
        for (Bridge b : this.bridges) {
            if (b.getWeight().equals(BigInteger.ONE)) {
                if (b.getDirection() == Bridge.Direction.HORIZONTAL) {
                    for (int i = b.getA().getCol() + 1; i < b.getB().getCol(); i++) {
                        encoding[b.getA().getRow()][i] = BigInteger.valueOf(1);
                    }
                } else if (b.getDirection() == Bridge.Direction.VERTICAL) {
                    for (int i = b.getA().getRow() + 1; i < b.getB().getRow(); i++) {
                        encoding[i][b.getA().getCol()] = BigInteger.valueOf(3);
                    }
                }
            } else if (b.getWeight().equals(BigInteger.TWO)) {
                if (b.getDirection() == Bridge.Direction.HORIZONTAL) {
                    for (int i = b.getA().getCol() + 1; i < b.getB().getCol(); i++) {
                        encoding[b.getA().getRow()][i] = BigInteger.valueOf(2);
                    }
                } else if (b.getDirection() == Bridge.Direction.VERTICAL) {
                    for (int i = b.getA().getRow() + 1; i < b.getB().getRow(); i++) {
                        encoding[i][b.getA().getCol()] = BigInteger.valueOf(4);
                    }
                }
            }
        }

        // The rest is empty, should be 0
        for (int i = 0; i < this.fieldSize; i++) {
            for (int j = 0; j < this.fieldSize; j++) {
                if (encoding[i][j] == null)
                    encoding[i][j] = BigInteger.valueOf(0);
            }
        }

        return encoding;
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
