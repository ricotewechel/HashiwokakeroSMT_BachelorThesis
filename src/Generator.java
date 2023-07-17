import org.sosy_lab.common.configuration.InvalidConfigurationException;
import java.math.BigInteger;
import java.util.*;

public class Generator {
    private final Random random;
    private final GraphSolver graphSolver = new GraphSolver();
    private final GridSolver gridSolver= new GridSolver();

    private enum Direction {
        NORTH,
        EAST,
        SOUTH,
        WEST
    }

    public Generator() throws InvalidConfigurationException {
        this.random = new Random();
    }

    public ArrayList<ArrayList<Long>> generateGames(int fieldSize, int nodeGoal, String encoding) {
        Game game = new Game(fieldSize, new ArrayList<>(), new ArrayList<>()); // Start with empty game
        game.addNode(new Node(this.random.nextInt(fieldSize), this.random.nextInt(fieldSize), 0)); // Place initial node at random
        int nodeCount = 1; // Keeps track of how many nodes have been placed
        int triesForUniqueSolution = 0; // Keeps track of how many tries ware made to get a uniquely solvable puzzle

        ArrayList<Long> maximums = new ArrayList<>(); // Keeps track of results (maxTriesForNewNode, maxTriesForUniqueSol)
        ArrayList<Long> looptimes = new ArrayList<>(); // Keeps track of total time spent per loop
        ArrayList<Long> uniquetimes = new ArrayList<>(); // Keeps track of total time spent per call of checkUniquelySolvable
        ArrayList<Long> differences = new ArrayList<>(); // Keeps track of total
        long maxTriesForUniqueSolution = 0;
        long maxTriesForNewNode = 0;
        long t0 = 0;
        long looptime = 0;
        long t1 = 0;
        long uniquetime = 0;
        long difference = 0;

        while (nodeCount < nodeGoal && triesForUniqueSolution < 1000) {
            t0 = System.currentTimeMillis();
            Game copy = new Game(game); // For restoring to state WITHOUT new node and bridges if not uniquely solvable after adding node

            // Search for a potential new node to place, reachable from current game state
            int triesForNewNode = 0; // Keeps track of how many times placing a new node was tried
            Node n = null; // The source node
            Node newNode = null; // The new node
            while (newNode == null) { // Loop until a valid new node has been found to place
                if (triesForNewNode >= 1000) { // Above this threshold we conclude no new node can be placed that meets the conditions
                    System.out.println("ERROR: " + game.getNodes().size() + "/" + nodeGoal + " nodes were able to be placed:");
                    // Finish up and return the game
                    this.setNodeValues(game); // Count all bridge weights to determine node values
                    game.removeBridges(); // Remove all bridges to change solution into puzzle
                    game.fillFieldGraphEncoding(); // Use graph encoding's fillField to make game printable
//                    System.out.println(game);
                    System.out.println(this.convertToID(game) + "\n");

                    maximums.add(maxTriesForNewNode);
                    maximums.add(maxTriesForUniqueSolution);
                    return new ArrayList<>() {
                        {
                            add(maximums);
                            add(looptimes);
                            add(uniquetimes);
                            add(differences);
                        }
                    };
                }
                n = game.getNodes().get(this.random.nextInt(game.getNodes().size())); // Choose random node to work from
                newNode = chooseNewRandomNode(n, fieldSize, game); // Place random new node. Returns null if there is no possible new bridge and node to place from n
                triesForNewNode++;
                maxTriesForNewNode = Math.max(maxTriesForNewNode, triesForNewNode);
            }

            // Actually add the new node and bridge to the game
            game.addNode(newNode); // Add new node to list of game nodes.
            this.placeBridge(n, newNode, game); // Place a bridge between the two nodes with a random weight (1 or 2)

            // Generate random bridges from newly created node to reachable nodes (this creates loops)
            int go;
            for (Node neighborNode : this.getNeighbors(newNode, getRespectiveDirection(n, newNode), game)) { // For all nodes that can be reached from the new node (excluding source node)
                go = this.random.nextInt(2); // 50% chance to set bridge or not
                if (go == 1)
                    this.placeBridge(newNode, neighborNode, game); // Place a bridge between the two nodes with a random weight (1 or 2)
            }

            Game copy2 = new Game(game); // For restoring to state WITH new node and bridges if still uniquely solvable after adding node

            // Check if game is still uniquely solvable. Continue if so, undo changes to last uniquely solvalbe state ('copy')
            t1 = System.currentTimeMillis();
            if(!this.checkUniquelySolvable(game, encoding)) {
                uniquetime = System.currentTimeMillis() - t1;
                uniquetimes.add(uniquetime);

                triesForUniqueSolution++;
                maxTriesForUniqueSolution = Math.max(maxTriesForUniqueSolution, triesForUniqueSolution);
                game = copy; // If not uniquely solvable, restore game to state WITHOUT new node and bridges
            } else {
                uniquetime = System.currentTimeMillis() - t1;
                uniquetimes.add(uniquetime);

                triesForUniqueSolution = 0;
                nodeCount++;
                game = copy2; // If uniquely solvable, restore game to state WITH new node and bridges
            }
            looptime = System.currentTimeMillis() - t0;
            looptimes.add(looptime);
            difference = looptime - uniquetime;
            differences.add(difference);
        }

        if (triesForUniqueSolution >= 1000) {
            System.out.println("Uniquely solvable puzzle not found in this iteration");

            maximums.add(maxTriesForNewNode);
            maximums.add(maxTriesForUniqueSolution);
            return new ArrayList<>() {
                {
                    add(maximums);
                    add(looptimes);
                    add(uniquetimes);
                    add(differences);
                }
            };
        }

        // Finish up and return the game
        this.setNodeValues(game); // Count all bridge weights to determine node values
        game.removeBridges(); // Remove all bridges to change solution into puzzle
        game.fillFieldGraphEncoding(); // Use graph encoding's fillField to make game printable
//        System.out.println(game);
        System.out.println(this.convertToID(game) + "\n");

        maximums.add(maxTriesForNewNode);
        maximums.add(maxTriesForUniqueSolution);
        return new ArrayList<>() {
            {
                add(maximums);
                add(looptimes);
                add(uniquetimes);
                add(differences);
            }
        };
    }

    // Given a node and a square field's size, return a random node that's reachable from at least one existing node
    private Node chooseNewRandomNode(Node node, int size, Game game) {
        List<Direction> dirs = this.getPossibleDirections(node, size, game); // dirs = directions in which is still makes sense to place a new node and bridge
        if (dirs.size() == 0) // if there are no possible directions left from node to place a new bridge and node from
            return null;
        Direction dir = dirs.get(this.random.nextInt(dirs.size()));
        int offset;
        Node newNode;
        if (dir == Direction.NORTH) {
            offset = 2 + this.random.nextInt( // New node must be 2 spaces away from original node, + buffer space to next obstacle
                    Math.min( // Lowest number decides available space
                            node.getRow()-1, Math.min( // Reach border
                            node.getRow()-this.getNearestNode(node, Direction.NORTH, game).getRow()-3,
                            node.getRow()-this.getNearestBridge(node, Direction.NORTH, game).getA().getRow()-2
                    )));
            newNode = new Node(node.getRow() - offset, node.getCol(), 0);
        } else if (dir == Direction.EAST) {
            offset = 2 + this.random.nextInt( // New node must be 2 spaces away from original node, + buffer space to next obstacle
                    Math.min( // Lowest number decides available space
                            (size-2) - node.getCol(), Math.min( // Reach border
                            this.getNearestNode(node, Direction.EAST, game).getCol()-node.getCol()-3,
                            this.getNearestBridge(node, Direction.EAST, game).getA().getCol()-node.getCol()-2
                    )));
            newNode = new Node(node.getRow(), node.getCol() + offset, 0);
        } else if (dir == Direction.SOUTH) {
            offset = 2 + this.random.nextInt( // New node must be 2 spaces away from original node, + buffer space to next obstacle
                    Math.min( // Lowest number decides available space
                            (size-2) - node.getRow(), Math.min( // Reach border
                            this.getNearestNode(node, Direction.SOUTH, game).getRow()-node.getRow()-3,
                            this.getNearestBridge(node, Direction.SOUTH, game).getA().getRow()-node.getRow()-2
                    )));
            newNode = new Node(node.getRow() + offset, node.getCol(), 0);
        } else { // if (dir == Direction.WEST)
            offset = 2 + this.random.nextInt( // New node must be 2 spaces away from original node, + buffer space to next obstacle
                    Math.min( // Lowest number decides available space
                            node.getCol() - 1, Math.min( // Reach border
                            node.getCol()-this.getNearestNode(node, Direction.WEST, game).getCol()-3,
                            node.getCol()-this.getNearestBridge(node, Direction.WEST, game).getA().getCol()-2
                    )));
            newNode = new Node(node.getRow(), node.getCol() - offset, 0);
        }
        if (this.isNextToNode(newNode, game)) // If the chosen node happens to be next to another node, retry
            return null;
        return newNode;
    }

    // Given a node, and a square field's size, return all directions in which it still makes sense to move to place a new node and bridge
    // Takes into account edges of game, existing connected bridges, existing crossing bridges, and existing nodes
    private ArrayList<Direction> getPossibleDirections(Node node, int size, Game game) {
        ArrayList<Direction> dirs = new ArrayList<>(Arrays.asList(Direction.values()));
        if (node.getRow() < 2 || // If node lies in outer two rings of grid
                this.bridgePresent(node, Direction.NORTH, game) || // If there is already a bridge present in the given direction
                this.getNearestNode(node, Direction.NORTH, game).getRow() > node.getRow()-4 || // If the nearest node is nearer than 4 steps away
                this.getNearestBridge(node, Direction.NORTH, game).getA().getRow() > node.getRow()-3) // If the nearest bridge is nearer than 3 steps away
            dirs.remove(Direction.NORTH);
        if (node.getCol() > size-3 || // If node lies in outer two rings of grid
                this.bridgePresent(node, Direction.EAST, game) || // If there is already a bridge present in the given direction
                this.getNearestNode(node, Direction.EAST, game).getCol() < node.getCol()+4 || // If the nearest node is nearer than 4 steps away
                this.getNearestBridge(node, Direction.EAST, game).getA().getCol() < node.getCol()+3) // If the nearest bridge is nearer than 3 steps away
            dirs.remove(Direction.EAST);
        if (node.getRow() > size-3 || // If node lies in outer two rings of grid
                this.bridgePresent(node, Direction.SOUTH, game) || // If there is already a bridge present in the given direction
                this.getNearestNode(node, Direction.SOUTH, game).getRow() < node.getRow()+4 || // If the nearest node is nearer than 4 steps away
                this.getNearestBridge(node, Direction.SOUTH, game).getA().getRow() < node.getRow()+3) // If the nearest bridge is nearer than 3 steps away
            dirs.remove(Direction.SOUTH);
        if (node.getCol() < 2 || // If node lies in outer two rings of grid
                this.bridgePresent(node, Direction.WEST, game) || // If there is already a bridge present in the given direction
                this.getNearestNode(node, Direction.WEST, game).getCol() > node.getCol()-4 || // If the nearest node is nearer than 4 steps away
                this.getNearestBridge(node, Direction.WEST, game).getA().getCol() > node.getCol()-3) // If the nearest bridge is nearer than 3 steps away
            dirs.remove(Direction.WEST);
        return dirs;
    }

    // Given a node and a direction, returns if there is an adjacent bridge present in that direction or not
    private Boolean bridgePresent(Node node, Direction dir, Game game) {
        if (dir == Direction.NORTH) {
            return (game.getBridgesFrom(node).stream().filter(b -> b.getDirection() == Bridge.Direction.VERTICAL && b.getB().equals(node)).toList().size() > 0);
        } else if (dir == Direction.EAST) {
            return (game.getBridgesFrom(node).stream().filter(b -> b.getDirection() == Bridge.Direction.HORIZONTAL && b.getA().equals(node)).toList().size() > 0);
        } else if (dir == Direction.SOUTH) {
            return (game.getBridgesFrom(node).stream().filter(b -> b.getDirection() == Bridge.Direction.VERTICAL && b.getA().equals(node)).toList().size() > 0);
        } else { // if (dir == Direction.WEST)
            return (game.getBridgesFrom(node).stream().filter(b -> b.getDirection() == Bridge.Direction.HORIZONTAL && b.getB().equals(node)).toList().size() > 0);
        }
    }

    // Given a node and a direction, returns the nearest node in that direction, returns dummy nodes with MIN or MAX integer values as coordinates in case no closest node exist
    private Node getNearestNode(Node node, Direction dir, Game game) {
        if (dir == Direction.NORTH) {
            Optional<Node> closest = game.getNodes().stream().filter(n -> node.getCol() == n.getCol() && node.getRow() > n.getRow()).max(Comparator.comparingInt(Node::getRow));
            return closest.orElse(new Node(Integer.MIN_VALUE/2, Integer.MIN_VALUE/2, 0));
        } else if (dir == Direction.EAST) {
            Optional<Node> closest = game.getNodes().stream().filter(n -> node.getRow() == n.getRow() && node.getCol() < n.getCol()).min(Comparator.comparingInt(Node::getCol));
            return closest.orElse(new Node(Integer.MAX_VALUE/2, Integer.MAX_VALUE/2, 0));
        } else if (dir == Direction.SOUTH) {
            Optional<Node> closest = game.getNodes().stream().filter(n -> node.getCol() == n.getCol() && node.getRow() < n.getRow()).min(Comparator.comparingInt(Node::getRow));
            return closest.orElse(new Node(Integer.MAX_VALUE/2, Integer.MAX_VALUE/2, 0));
        } else { // if (dir == Direction.WEST)
            Optional<Node> closest = game.getNodes().stream().filter(n -> node.getRow() == n.getRow() && node.getCol() > n.getCol()).max(Comparator.comparingInt(Node::getCol));
            return closest.orElse(new Node(Integer.MIN_VALUE/2, Integer.MIN_VALUE/2, 0));
        }
    }

    // Given a node and a direction, returns the nearest bridge with a different orientation in that direction, returns NULL
    private Bridge getNearestBridge(Node node, Direction dir, Game game) {
        if (dir == Direction.NORTH) {
            Optional<Bridge> closest = game.getBridges().stream().filter(b -> node.getCol() > b.getA().getCol() && node.getCol() < b.getB().getCol() && node.getRow() > b.getA().getRow())
                    .max(Comparator.comparingInt(bridge -> bridge.getA().getRow()));
            return closest.orElse(new Bridge(new Node(Integer.MIN_VALUE/2, Integer.MIN_VALUE/2, 0), new Node(Integer.MIN_VALUE/2, Integer.MAX_VALUE/2, 0), null, Bridge.Direction.HORIZONTAL));
        } else if (dir == Direction.EAST) {
            Optional<Bridge> closest = game.getBridges().stream().filter(b -> node.getRow() > b.getA().getRow() && node.getRow() < b.getB().getRow() && node.getCol() < b.getA().getCol())
                    .min(Comparator.comparingInt(bridge -> bridge.getA().getCol()));
            return closest.orElse(new Bridge(new Node(Integer.MIN_VALUE/2, Integer.MAX_VALUE/2, 0), new Node(Integer.MAX_VALUE/2, Integer.MAX_VALUE/2, 0), null, Bridge.Direction.VERTICAL));
        } else if (dir == Direction.SOUTH) {
            Optional<Bridge> closest = game.getBridges().stream().filter(b -> node.getCol() > b.getA().getCol() && node.getCol() < b.getB().getCol() && node.getRow() < b.getA().getRow())
                    .min(Comparator.comparingInt(bridge -> bridge.getA().getRow()));
            return closest.orElse(new Bridge(new Node(Integer.MAX_VALUE/2, Integer.MIN_VALUE/2, 0), new Node(Integer.MAX_VALUE/2, Integer.MAX_VALUE/2, 0), null, Bridge.Direction.HORIZONTAL));
        } else { // if (dir == Direction.WEST)
            Optional<Bridge> closest = game.getBridges().stream().filter(b -> node.getRow() > b.getA().getRow() && node.getRow() < b.getB().getRow() && node.getCol() > b.getA().getCol())
                    .max(Comparator.comparingInt(bridge -> bridge.getA().getCol()));
            return closest.orElse(new Bridge(new Node(Integer.MIN_VALUE/2, Integer.MIN_VALUE/2, 0), new Node(Integer.MAX_VALUE/2, Integer.MIN_VALUE/2, 0), null, Bridge.Direction.VERTICAL));
        }
    }

    // Given a node, returns a list of all nodes reachable from this node (so no obstructions by other nodes or bridges)
    private ArrayList<Node> getNeighbors(Node node, Direction dir, Game game) {
        ArrayList<Node> neighbors = new ArrayList<>();
        for (Direction togo : Arrays.stream(Direction.values()).filter(d -> !(d == dir)).toList()) { // Filter out the direction where we came from
            if (togo == Direction.NORTH) {
                Optional<Node> closestNode = game.getNodes().stream().filter(n -> node.getCol() == n.getCol() && node.getRow() > n.getRow()).max(Comparator.comparingInt(Node::getRow));
                Optional<Bridge> closestBridge = game.getBridges().stream().filter(b -> node.getCol() > b.getA().getCol() && node.getCol() < b.getB().getCol() && node.getRow() > b.getA().getRow())
                        .max(Comparator.comparingInt(bridge -> bridge.getA().getRow()));
                if (closestNode.isPresent()) {
                    Node n = closestNode.get();
                    if (closestBridge.isPresent()) {
                        Bridge b = closestBridge.get();
                        if (n.getRow() > b.getA().getRow())
                            neighbors.add(n);
                    } else neighbors.add(n);
                }
            } else if (togo == Direction.EAST) {
                Optional<Node> closestNode = game.getNodes().stream().filter(n -> node.getRow() == n.getRow() && node.getCol() < n.getCol()).min(Comparator.comparingInt(Node::getCol));
                Optional<Bridge> closestBridge = game.getBridges().stream().filter(b -> node.getRow() > b.getA().getRow() && node.getRow() < b.getB().getRow() && node.getCol() < b.getA().getCol())
                        .min(Comparator.comparingInt(bridge -> bridge.getA().getCol()));
                if (closestNode.isPresent()) {
                    Node n = closestNode.get();
                    if (closestBridge.isPresent()) {
                        Bridge b = closestBridge.get();
                        if (n.getCol() < b.getA().getCol())
                            neighbors.add(n);
                    } else neighbors.add(n);
                }
            } else if (togo == Direction.SOUTH) {
                Optional<Node> closestNode = game.getNodes().stream().filter(n -> node.getCol() == n.getCol() && node.getRow() < n.getRow()).min(Comparator.comparingInt(Node::getRow));
                Optional<Bridge> closestBridge = game.getBridges().stream().filter(b -> node.getCol() > b.getA().getCol() && node.getCol() < b.getB().getCol() && node.getRow() < b.getA().getRow())
                        .min(Comparator.comparingInt(bridge -> bridge.getA().getRow()));
                if (closestNode.isPresent()) {
                    Node n = closestNode.get();
                    if (closestBridge.isPresent()) {
                        Bridge b = closestBridge.get();
                        if (n.getRow() < b.getA().getRow())
                            neighbors.add(n);
                    } else neighbors.add(n);
                }
            } else { // if (togo == Direction.WEST)
                Optional<Node> closestNode = game.getNodes().stream().filter(n -> node.getRow() == n.getRow() && node.getCol() > n.getCol()).max(Comparator.comparingInt(Node::getCol));
                Optional<Bridge> closestBridge = game.getBridges().stream().filter(b -> node.getRow() > b.getA().getRow() && node.getRow() < b.getB().getRow() && node.getCol() > b.getA().getCol())
                        .max(Comparator.comparingInt(bridge -> bridge.getA().getCol()));
                if (closestNode.isPresent()) {
                    Node n = closestNode.get();
                    if (closestBridge.isPresent()) {
                        Bridge b = closestBridge.get();
                        if (n.getCol() > b.getA().getCol())
                            neighbors.add(n);
                    } else neighbors.add(n);
                }
            }
        }
        return neighbors;
    }

    // Returns n1's direction with respect to n2
    private Direction getRespectiveDirection(Node n1, Node n2) {
        if (n1.getCol() == n2.getCol() && n1.getRow() < n2.getRow())
            return Direction.NORTH;
        else if (n1.getRow() == n2.getRow() && n1.getCol() > n2.getCol())
            return Direction.EAST;
        else if (n1.getCol() == n2.getCol() && n1.getRow() > n2.getRow())
            return Direction.SOUTH;
        else if (n1.getRow() == n2.getRow() && n1.getCol() < n2.getCol())
            return Direction.WEST;
        else return null;
    }

    // Based on n1's direction with respect to n2, adds bridge to game with the correct endpoints and orientations
    private void placeBridge(Node n1, Node n2, Game game) {
        if (getRespectiveDirection(n1, n2) == Direction.NORTH) {
            game.addBridge(new Bridge(n1, n2, BigInteger.valueOf(this.random.nextInt(2) + 1), Bridge.Direction.VERTICAL));
        } else if (getRespectiveDirection(n1, n2) == Direction.EAST) {
            game.addBridge(new Bridge(n2, n1, BigInteger.valueOf(this.random.nextInt(2) + 1), Bridge.Direction.HORIZONTAL));
        } else if (getRespectiveDirection(n1, n2) == Direction.SOUTH) {
            game.addBridge(new Bridge(n2, n1, BigInteger.valueOf(this.random.nextInt(2) + 1), Bridge.Direction.VERTICAL));
        } else { // if (getRespectiveDirection(n1, n2) == Direction.WEST)
            game.addBridge(new Bridge(n1, n2, BigInteger.valueOf(this.random.nextInt(2) + 1), Bridge.Direction.HORIZONTAL));
        }
    }

    private Boolean isNextToNode(Node node, Game game) {
        if (node == null)
            return false;
        else return game.getNodes().contains(new Node(node.getRow()-1, node.getCol(), 0)) || // Northern neighboring field
                game.getNodes().contains(new Node(node.getRow(), node.getCol()+1, 0)) || // Eastern neighboring field
                game.getNodes().contains(new Node(node.getRow()+1, node.getCol(), 0)) || // Southern neighboring field
                game.getNodes().contains(new Node(node.getRow(), node.getCol()-1, 0)); // Western neighboring field
    }

    private void setNodeValues(Game game) {
        for (Node n : game.getNodes()) {
            int total = 0;
            for (Bridge b : game.getBridgesFrom(n))
                total += b.getWeight().intValue();
            n.setValue(total);
        }
    }

    private Boolean checkUniquelySolvable(Game game, String solver) {
        this.setNodeValues(game);
        game.sortNodes();
        game.setPossibleBridges();
        game.setNullBridgesToZero();
        game.sortBridges();
        switch (solver) {
            case "Graph" -> {
                return this.graphSolver.hasUniqueSolution(game);
            }
            case "Grid" -> {
                return this.gridSolver.hasUniqueSolution(game);
            }
            default -> {
                System.out.println("No such solver");
                return null;
            }
        }
    }

    private String convertToID (Game game) {
        StringBuilder s = new StringBuilder();
        s.append(game.getFieldSize()).append("x").append(game.getFieldSize()); // Append puzzle size
        s.append("m2:"); // Append maximum bridge weight (static) with + colon separator
        char[][] field = game.getField();
        int count = 0;
        for (int i = 0; i < field.length; i++) {
            for (int j = 0; j < field.length; j++) {
                char c = field[i][j];
                if (Character.isDigit(c)) {
                    s.append(this.getSpacesString(count));
                    count = 0;
                    s.append(c);
                }
                else
                    count++;
            }
        }
        if (count > 0)
            s.append(this.getSpacesString(count));
        return s.toString();
    }

    private String getSpacesString (int ctr) {
        StringBuilder s = new StringBuilder();
        while (ctr >= 26) {
            s.append('z');
            ctr -= 26;
        }
        if (ctr > 0) {
            s.append(Character.toString((char) ctr + 96));
        }
        return s.toString();
    }
}
