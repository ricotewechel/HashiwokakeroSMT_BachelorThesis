import java.math.BigInteger;
import java.util.*;

public class Generator {
    private final Random random;
    private enum Direction {
        NORTH,
        EAST,
        SOUTH,
        WEST
    }

    public Generator() {
        this.random = new Random();
    }

    public Game generateGame(int fieldSize, int nodeCount) {
        Game game = new Game(fieldSize, new ArrayList<>(), new ArrayList<>());
        game.addNode(new Node(this.random.nextInt(fieldSize), this.random.nextInt(fieldSize), 0));
        int i = 1;
        while (i < nodeCount) {
            int triesForNewNode = 0;
            Node n = null;
            Node newNode = null;

            while (newNode == null) { // Loop until a valid new node has been found to place
                n = game.getNodes().get(this.random.nextInt(game.getNodes().size())); // Choose random node to work from
                newNode = chooseNewRandomNode(n, fieldSize, game); // Place random new node. Returns null if there is no possible new bridge and node to place from n
                triesForNewNode++;
                if (this.isNextToNode(newNode, game)) // If the chosen node happens to be next to another node, retry
                    newNode = null;
                if (triesForNewNode > 1000) { // Above this threshold we conclude no new node can be placed that meets the conditions
                    System.out.println("ERROR: " + game.getNodes().size() + "/" + nodeCount + " nodes were able to be placed:");
                    this.setNodeValues(game); // Finish up and return the game
                    game.fillFieldGraphEncoding();
                    return game;
                }
            }
            game.addNode(newNode); // Add new node to list of game nodes.
            this.placeBridge(n, newNode, game, 1, 2); // Place a bridge between the two nodes with a random weight (1 or 2)

            int go;
            for (Node neighborNode : this.getNeighbors(newNode, getRespectiveDirection(n, newNode), game)) { // For all nodes that can be reached from the new node
                go = this.random.nextInt(2); // 50% chance to set bridge or not (this creates loops)
                if (go == 1)
                    this.placeBridge(newNode, neighborNode, game, 1, 2); // Place a bridge between the two nodes with a random weight (1 or 2)
            }
            i++;
        }
        this.setNodeValues(game);
        game.fillFieldGraphEncoding();
        return game;
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
            offset = 2 + random.nextInt( // New node must be 2 spaces away from original node, + buffer space to next obstacle
                    Math.min( // Lowest number decides available space
                            node.getRow()-1, Math.min( // Reach border
                            node.getRow()-this.getNearestNode(node, Direction.NORTH, game).getRow()-3,
                            node.getRow()-this.getNearestBridge(node, Direction.NORTH, game).getA().getRow()-2
                    )));
            newNode = new Node(node.getRow() - offset, node.getCol(), 0);
        } else if (dir == Direction.EAST) {
            offset = 2 + random.nextInt( // New node must be 2 spaces away from original node, + buffer space to next obstacle
                    Math.min( // Lowest number decides available space
                            (size-2) - node.getCol(), Math.min( // Reach border
                            this.getNearestNode(node, Direction.EAST, game).getCol()-node.getCol()-3,
                            this.getNearestBridge(node, Direction.EAST, game).getA().getCol()-node.getCol()-2
                    )));
            newNode = new Node(node.getRow(), node.getCol() + offset, 0);
        } else if (dir == Direction.SOUTH) {
            offset = 2 + random.nextInt( // New node must be 2 spaces away from original node, + buffer space to next obstacle
                    Math.min( // Lowest number decides available space
                            (size-2) - node.getRow(), Math.min( // Reach border
                            this.getNearestNode(node, Direction.SOUTH, game).getRow()-node.getRow()-3,
                            this.getNearestBridge(node, Direction.SOUTH, game).getA().getRow()-node.getRow()-2
                    )));
            newNode = new Node(node.getRow() + offset, node.getCol(), 0);
        } else { // if (dir == Direction.WEST)
            offset = 2 + random.nextInt( // New node must be 2 spaces away from original node, + buffer space to next obstacle
                    Math.min( // Lowest number decides available space
                            node.getCol() - 1, Math.min( // Reach border
                            node.getCol()-this.getNearestNode(node, Direction.WEST, game).getCol()-3,
                            node.getCol()-this.getNearestBridge(node, Direction.WEST, game).getA().getCol()-2
                    )));
            newNode = new Node(node.getRow(), node.getCol() - offset, 0);
        }
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

    // Given a node and a direction, returns if there is a adjacent bridge present in that direction or not
    private Boolean bridgePresent(Node node, Direction dir, Game game) {
        if (dir == Direction.NORTH) {
            return (game.getBridgesFrom(node).stream().filter(b -> b.getDirection() == Bridge.Direction.VERTICAL && b.getB() == node).toList().size() > 0);
        } else if (dir == Direction.EAST) {
            return (game.getBridgesFrom(node).stream().filter(b -> b.getDirection() == Bridge.Direction.HORIZONTAL && b.getA() == node).toList().size() > 0);
        } else if (dir == Direction.SOUTH) {
            return (game.getBridgesFrom(node).stream().filter(b -> b.getDirection() == Bridge.Direction.VERTICAL && b.getA() == node).toList().size() > 0);
        } else { // if (dir == Direction.WEST)
            return (game.getBridgesFrom(node).stream().filter(b -> b.getDirection() == Bridge.Direction.HORIZONTAL && b.getB() == node).toList().size() > 0);
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

    // Given a node, return a list of all nodes reachable from this node (so no obstructions by other nodes or bridges)
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
    private void placeBridge(Node n1, Node n2, Game game, int min, int max) { // TODO minmax not needed
        if (getRespectiveDirection(n1, n2) == Direction.NORTH) {
            game.addBridge(new Bridge(n1, n2, BigInteger.valueOf(this.random.nextInt(max+1 - min) + min), Bridge.Direction.VERTICAL));
        } else if (getRespectiveDirection(n1, n2) == Direction.EAST) {
            game.addBridge(new Bridge(n2, n1, BigInteger.valueOf(this.random.nextInt(max+1 - min) + min), Bridge.Direction.HORIZONTAL));
        } else if (getRespectiveDirection(n1, n2) == Direction.SOUTH) {
            game.addBridge(new Bridge(n2, n1, BigInteger.valueOf(this.random.nextInt(max+1 - min) + min), Bridge.Direction.VERTICAL));
        } else { // if (getRespectiveDirection(n1, n2) == Direction.WEST)
            game.addBridge(new Bridge(n1, n2, BigInteger.valueOf(this.random.nextInt(max+1 - min) + min), Bridge.Direction.HORIZONTAL));
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
            for (Bridge b : game.getBridgesFrom(n)) {
                total += b.getWeight().intValue();
            }
            n.setValue(total);
        }
    }
}
