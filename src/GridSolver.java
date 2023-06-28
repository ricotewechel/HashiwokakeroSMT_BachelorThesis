import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

public class GridSolver {
    private final SolverContext context;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;
    private NumeralFormula.IntegerFormula[][] fieldVariables;
    private BooleanFormula[][][] connectionVariables;
    public GridSolver() throws InvalidConfigurationException {
        Configuration config = Configuration.defaultConfiguration();
        LogManager logger = BasicLogManager.create(config);
        ShutdownManager shutdown = ShutdownManager.create();
        this.context = SolverContextFactory.createSolverContext(
                config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.SMTINTERPOL);

        FormulaManager fmgr = this.context.getFormulaManager();
        this.bmgr = fmgr.getBooleanFormulaManager();
        this.imgr = fmgr.getIntegerFormulaManager();
    }


    public void solveGame(Game game) {
//        Game secondgame = game;
        this.createVariables(game);

        long t0 = 0;
        long constrTime = 0;
        long t1 = 0;
        long unsatTime = 0;
        long t2 = 0;
        long satTime = 0;
        long totalTime = 0;

        // Solve with SMT solver
        Model model = null;
        try (ProverEnvironment prover = this.context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            // Add constraints
            t0 = System.currentTimeMillis();
            prover.addConstraint(this.validCellsConstraint2(game));
            prover.addConstraint(this.neighborConstraint(game));
            prover.addConstraint(this.nodesSatisfiedConstraint(game));
            prover.addConstraint(this.nodesConnectedConstraint(game));
            constrTime = System.currentTimeMillis() - t0;

            t1 = System.currentTimeMillis();
            boolean isUnsat = prover.isUnsat();
            unsatTime = System.currentTimeMillis() - t1;
            if (!isUnsat) {
                t2 = System.currentTimeMillis();
                model = prover.getModel();
                satTime = System.currentTimeMillis() - t2;
            }
            totalTime = System.currentTimeMillis() - t1;

        } catch (SolverException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        assert model != null;

        // Retrieve solution
        BigInteger[][] solution = new BigInteger[game.getFieldSize()][game.getFieldSize()];

        for (int i = 0; i < game.getFieldSize(); i++) {
            for (int j = 0; j < game.getFieldSize(); j++) {
                solution[i][j] = model.evaluate(this.fieldVariables[i][j]);
            }
        }

        System.out.println("Grid:\t" + constrTime + "\t" + unsatTime + "\t" + satTime + "\t" + totalTime);

        game.fillFieldNaive(solution);


//        // Solution using validCellsConstraint2:
//        this.createVariables(secondgame);
//
//        ArrayList<BooleanFormula> solList = new ArrayList<>();
//        for (int i = 0; i < secondgame.getFieldSize(); i++) {
//            for (int j = 0; j < secondgame.getFieldSize(); j++) {
//                solList.add(this.imgr.equal(this.fieldVariables[i][j], imgr.makeNumber(solution[i][j])));
//            }
//        }
//
//        BooleanFormula isNotFirstSolution = this.bmgr.not(this.bmgr.and(solList));
//
//
//        // Solve with SMT solver
//        Model model2 = null;
//        try (ProverEnvironment prover2 = this.context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
//            // Add constraints
//            prover2.addConstraint(validCellsConstraint2(secondgame));
//            prover2.addConstraint(neighborConstraint(secondgame));
//            prover2.addConstraint(nodesSatisfiedConstraint(secondgame));
//            prover2.addConstraint(nodesConnectedConstraint(secondgame));
//
//            prover2.addConstraint(isNotFirstSolution);
//
//            boolean isUnsat = prover2.isUnsat();
//            if (!isUnsat) {
//                model2 = prover2.getModel();
//            }
//        } catch (SolverException | InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        assert model2 != null;
//
//        // Retrieve solution
//        try {
//            BigInteger[][] solution2 = new BigInteger[secondgame.getFieldSize()][secondgame.getFieldSize()];
//            for (int i = 0; i < secondgame.getFieldSize(); i++) {
//                for (int j = 0; j < secondgame.getFieldSize(); j++) {
//                    solution2[i][j] = model2.evaluate(this.fieldVariables[i][j]);
//                }
//            }
//            secondgame.fillFieldNaive(solution2);
//        } catch (NullPointerException ignored) {
//            System.out.println("Unique solution");
//        }


//        this.printConnectionVariables(game, model);
    }


    // Used for neighbor directions
    private enum Direction {
        NORTH,
        EAST,
        SOUTH,
        WEST
    }

    // Retrieves all directions that have neighbors. Used in other functions
    private ArrayList<Direction> getPossibleNeighbors(Game game, int row, int col) {
        ArrayList<Direction> directions = new ArrayList<>(
                Arrays.asList(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        );
        if (row == 0)
            directions.remove(Direction.NORTH);
        if (col == game.getFieldSize()-1)
            directions.remove(Direction.EAST);
        if (row == game.getFieldSize()-1)
            directions.remove(Direction.SOUTH);
        if (col == 0)
            directions.remove(Direction.WEST);
        return directions;
    }


    private void createVariables(Game game) {
        // Create variables for each grid cell (each cell can be empty, a node, or a bridge piece)
        this.fieldVariables = new NumeralFormula.IntegerFormula[game.getFieldSize()][game.getFieldSize()];
        for (int i = 0; i < game.getFieldSize(); i++) {
            for (int j = 0; j < game.getFieldSize(); j++) {
                this.fieldVariables[i][j] = this.imgr.makeVariable("φ" + i + "," + j);
            }
        }

        // Create variables for connectedness of each node pair in AT MOST i amount of steps, where i is at most edges-1
        // Indices i and j of these variables match directly with the indices in game.nodes
        this.connectionVariables = new BooleanFormula[game.getNodes().size()][game.getNodes().size()][game.getBridges().size()];
        for (int i = 0; i < (game.getNodes().size()); i++) { // TODO this outer loop is not needed
            for (int j = 0; j < (game.getNodes().size()); j++) {
                for (int k = 1; k < (game.getBridges().size()); k++) {
                    this.connectionVariables[i][j][k] = this.bmgr.makeVariable("γ" + i + "," + j + "," + k);
                }
            }
        }
    }


    // Cells can either be empty, single horizontal, double horizontal, single vertical, double vertical, or nodes respectively encoded as 0, 1, 2, 3, 4, 5
    // Corners may not be bridge pieces, edges may only be bridge pieces along the axis. Only cells that have same coordinates as nodes list may be nodes
    //      1:		A
    //          /\
    //      2:      (B \/ C \/ D \/ E \/ F)
    private BooleanFormula validCellsConstraint1(Game game) {
        ArrayList<BooleanFormula> validCellsList = new ArrayList<>();

        for (int row = 0; row < game.getFieldSize(); row++) {
            for (int col = 0; col < game.getFieldSize(); col++) {
                // Only cells with same coords as nodes list may be nodes
                Node n = new Node(row, col, 0);
                if (game.getNodes().contains(n)) {
                    validCellsList.add(
                            this.imgr.equal(this.fieldVariables[row][col], imgr.makeNumber(5))
                    );
                } else {
                    ArrayList<BooleanFormula> cellValidList = new ArrayList<>();
                    cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(0))); // e
                    if (0 < col && col < game.getFieldSize()-1) {
                        cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(1))); // ─
                        cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(2))); // ═
                    }
                    if (0 < row && row < game.getFieldSize()-1) {
                        cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(3))); // |
                        cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(4))); // ‖
                    }

                    validCellsList.add(this.bmgr.or(cellValidList));
                }
            }
        }
        return this.bmgr.and(validCellsList);
    }


    // Cells can either be empty, single horizontal, double horizontal, single vertical, double vertical, or nodes respectively encoded as 0, 1, 2, 3, 4, 5
    // Corners may not be bridge pieces, edges may only be bridge pieces along the axis. Only cells that have same coordinates as nodes list may be nodes
    //      1:		A
    //          /\
    //      2:      (B \/ C \/ D \/ E \/ F) /\ (~A)
    private BooleanFormula validCellsConstraint2(Game game) {
        ArrayList<BooleanFormula> validCellsList = new ArrayList<>();

        for (int row = 0; row < game.getFieldSize(); row++) {
            for (int col = 0; col < game.getFieldSize(); col++) {
                Node n = new Node(row, col, 0);
                // Only cells with same coords as nodes list may be nodes
                if (game.getNodes().contains(n)) {
                    validCellsList.add(
                            this.imgr.equal(this.fieldVariables[row][col], imgr.makeNumber(5))
                    );
                } else { // All other cells can be other pieces (depending on edges and corners), AND SHOULD NOT BE A NODE
                    ArrayList<BooleanFormula> cellValidList = new ArrayList<>();
                    cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(0))); // e
                    if (0 < col && col < game.getFieldSize()-1) {
                        cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(1))); // ─
                        cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(2))); // ═
                    }
                    if (0 < row && row < game.getFieldSize()-1) {
                        cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(3))); // |
                        cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(4))); // ‖
                    }

                    validCellsList.add(this.bmgr.or(cellValidList));
                    validCellsList.add( // Adding this proposition explicitly significantly improves speed
                            this.bmgr.not(this.imgr.equal(this.fieldVariables[row][col], imgr.makeNumber(5)))
                    );
                }
            }
        }
        return this.bmgr.and(validCellsList);
    }


    // Constraint 1 to 4: Bridges must be between two nodes, must be vertical or horizontal, must be single or double, and may not cross
    private BooleanFormula neighborConstraint(Game game) {
        ArrayList<BooleanFormula> NeighborList = new ArrayList<>();
        for (int i = 0; i < game.getFieldSize(); i++) {
            for (int j = 0; j < game.getFieldSize(); j++) {
                for (int p = 1; p <= 4; p++) {
                    NeighborList.add(
                            this.bmgr.implication(
                                    this.imgr.equal(this.fieldVariables[i][j], imgr.makeNumber(p)),
                                    this.bmgr.and(
                                            getNeighborRestrictionList(game, i, j, p)
                                    )
                            )
                    );
                }
            }
        }
        return this.bmgr.and(NeighborList);
    }

    // Creates restrictions for possible neighbors, used in neighborConstraint()
    private ArrayList<BooleanFormula> getNeighborRestrictionList(Game game, int i, int j, int piece) {
        ArrayList<BooleanFormula> neighborRestrictionList = new ArrayList<>();
        if (piece == 1 || piece == 2) { // ─ or ═
            for (Direction dir : this.getPossibleNeighbors(game, i, j)) {
                if (dir == Direction.NORTH) {
                    neighborRestrictionList.add(
                            this.bmgr.or(
                                    this.imgr.equal(this.fieldVariables[i-1][j], imgr.makeNumber(0)), // e
                                    this.imgr.equal(this.fieldVariables[i-1][j], imgr.makeNumber(1)), // ─
                                    this.imgr.equal(this.fieldVariables[i-1][j], imgr.makeNumber(2)), // ═
                                    this.imgr.equal(this.fieldVariables[i-1][j], imgr.makeNumber(5)) // o
                            )
                    );
                } else if (dir == Direction.EAST) {
                    neighborRestrictionList.add(
                            this.bmgr.or(
                                    this.imgr.equal(this.fieldVariables[i][j+1], imgr.makeNumber(piece)), // piece
                                    this.imgr.equal(this.fieldVariables[i][j+1], imgr.makeNumber(5)) // o
                            )
                    );
                } else if (dir == Direction.SOUTH) {
                    neighborRestrictionList.add(
                            this.bmgr.or(
                                    this.imgr.equal(this.fieldVariables[i+1][j], imgr.makeNumber(0)), // e
                                    this.imgr.equal(this.fieldVariables[i+1][j], imgr.makeNumber(1)), // ─
                                    this.imgr.equal(this.fieldVariables[i+1][j], imgr.makeNumber(2)), // ═
                                    this.imgr.equal(this.fieldVariables[i+1][j], imgr.makeNumber(5)) // o
                            )
                    );
                } else if (dir == Direction.WEST) {
                    neighborRestrictionList.add(
                            this.bmgr.or(
                                    this.imgr.equal(this.fieldVariables[i][j-1], imgr.makeNumber(piece)), // piece
                                    this.imgr.equal(this.fieldVariables[i][j-1], imgr.makeNumber(5)) // o
                            )
                    );
                }
            }
        } else if (piece == 3 || piece == 4) { // | or ‖
            for (Direction dir : this.getPossibleNeighbors(game, i, j)) {
                if (dir == Direction.NORTH) {
                    neighborRestrictionList.add(
                            this.bmgr.or(
                                    this.imgr.equal(this.fieldVariables[i-1][j], imgr.makeNumber(piece)), // piece
                                    this.imgr.equal(this.fieldVariables[i-1][j], imgr.makeNumber(5)) // o
                            )
                    );
                } else if (dir == Direction.EAST) {
                    neighborRestrictionList.add(
                            this.bmgr.or(
                                    this.imgr.equal(this.fieldVariables[i][j+1], imgr.makeNumber(0)), // e
                                    this.imgr.equal(this.fieldVariables[i][j+1], imgr.makeNumber(3)), // |
                                    this.imgr.equal(this.fieldVariables[i][j+1], imgr.makeNumber(4)), // ‖
                                    this.imgr.equal(this.fieldVariables[i][j+1], imgr.makeNumber(5)) // o
                            )
                    );
                } else if (dir == Direction.SOUTH) {
                    neighborRestrictionList.add(
                            this.bmgr.or(
                                    this.imgr.equal(this.fieldVariables[i+1][j], imgr.makeNumber(piece)), // piece
                                    this.imgr.equal(this.fieldVariables[i+1][j], imgr.makeNumber(5)) // o
                            )
                    );
                } else if (dir == Direction.WEST) {
                    neighborRestrictionList.add(
                            this.bmgr.or(
                                    this.imgr.equal(this.fieldVariables[i][j-1], imgr.makeNumber(0)), // e
                                    this.imgr.equal(this.fieldVariables[i][j-1], imgr.makeNumber(3)), // |
                                    this.imgr.equal(this.fieldVariables[i][j-1], imgr.makeNumber(4)), // ‖
                                    this.imgr.equal(this.fieldVariables[i][j-1], imgr.makeNumber(5)) // o
                            )
                    );
                }
            }
        }
        return neighborRestrictionList;
    }


    // Constraint 5: All nodes must have neighboring bridge pieces that add up to node value.
    private BooleanFormula nodesSatisfiedConstraint(Game game) {
        ArrayList<BooleanFormula> nodesSatisfiedList = new ArrayList<>();
        for (Node n : game.getNodes()) {
            ArrayList<NumeralFormula.IntegerFormula> sumList = new ArrayList<>();
            for (Direction d : this.getPossibleNeighbors(game, n.getRow(), n.getCol())) {
                if (d == Direction.NORTH)
                    sumList.add(
                            this.bmgr.ifThenElse(
                                    this.imgr.equal(
                                            this.fieldVariables[n.getRow()-1][n.getCol()],
                                            this.imgr.makeNumber(3)
                                    ),
                                    this.imgr.makeNumber(1),
                                    this.bmgr.ifThenElse(
                                            this.imgr.equal(
                                                    this.fieldVariables[n.getRow()-1][n.getCol()],
                                                    this.imgr.makeNumber(4)
                                            ),
                                            this.imgr.makeNumber(2),
                                            this.imgr.makeNumber(0)
                                    )
                            )
                    );
                else if (d == Direction.EAST)
                    sumList.add(
                            this.bmgr.ifThenElse(
                                    this.imgr.equal(
                                            this.fieldVariables[n.getRow()][n.getCol()+1],
                                            this.imgr.makeNumber(1)
                                    ),
                                    this.imgr.makeNumber(1),
                                    this.bmgr.ifThenElse(
                                            this.imgr.equal(
                                                    this.fieldVariables[n.getRow()][n.getCol()+1],
                                                    this.imgr.makeNumber(2)
                                            ),
                                            this.imgr.makeNumber(2),
                                            this.imgr.makeNumber(0)
                                    )
                            )
                    );
                else if (d == Direction.SOUTH)
                    sumList.add(
                            this.bmgr.ifThenElse(
                                    this.imgr.equal(
                                            this.fieldVariables[n.getRow()+1][n.getCol()],
                                            this.imgr.makeNumber(3)
                                    ),
                                    this.imgr.makeNumber(1),
                                    this.bmgr.ifThenElse(
                                            this.imgr.equal(
                                                    this.fieldVariables[n.getRow()+1][n.getCol()],
                                                    this.imgr.makeNumber(4)
                                            ),
                                            this.imgr.makeNumber(2),
                                            this.imgr.makeNumber(0)
                                    )
                            )
                    );
                else if (d == Direction.WEST)
                    sumList.add(
                            this.bmgr.ifThenElse(
                                    this.imgr.equal(
                                            this.fieldVariables[n.getRow()][n.getCol()-1],
                                            this.imgr.makeNumber(1)
                                    ),
                                    this.imgr.makeNumber(1),
                                    this.bmgr.ifThenElse(
                                            this.imgr.equal(
                                                    this.fieldVariables[n.getRow()][n.getCol()-1],
                                                    this.imgr.makeNumber(2)
                                            ),
                                            this.imgr.makeNumber(2),
                                            this.imgr.makeNumber(0)
                                    )
                            )
                    );
            }
            nodesSatisfiedList.add(
                    this.imgr.equal(
                            this.imgr.sum(sumList),
                            this.imgr.makeNumber(n.getValue())
                    )
            );
        }
        return this.bmgr.and(nodesSatisfiedList);
    }


    // Constraint 6: Everything is strongly connected
    BooleanFormula nodesConnectedConstraint(Game game) {
        ArrayList<BooleanFormula> everythingConnectedList = new ArrayList<>();
        for (int dest = 0; dest < game.getNodes().size(); dest++) {
            for (int i = 1; i < game.getBridges().size(); i++) {
                if (0 == dest) { // γ0,0,i <=> True
                    everythingConnectedList.add(this.areNodesConnectedTrue(dest, i));
                } else if (i == 1) { // γ0,2,1 <=> x1  or  γ0,3,1 <=> False
                    everythingConnectedList.add(this.areNodesConnectedInOneStep(dest, game));
                } else { // γ0,3,2 <=> γ0,3,1 \/ (γ0,1,1 /\ x2) \/ (γ0,2,1 /\ x3)
                    everythingConnectedList.add(this.areNodesConnectedInISteps(dest, i, game));
                }
                if (i == game.getNodes().size()-1) { // γ0,x,e-1 <=> True
                    everythingConnectedList.add(this.areNodesConnectedTrue(dest, i));
                }
            }
        }
        return bmgr.and(everythingConnectedList);
    }

    // Set a γ to true (if 0 == destination (vacuously) or if γx,y,e-1 (force connectedness))
    private BooleanFormula areNodesConnectedTrue(int dest, int i) {
        return this.connectionVariables[0][dest][i];
    }

    // Set a γ variable equivalent to a direct bridge or to false if not applicable
    private BooleanFormula areNodesConnectedInOneStep(int dest, Game game) {
        ArrayList<Bridge> neighbors = game.getBridgesFrom(game.getNodes().get(dest)); // Retrieve bridges connected to destination node
        for (Bridge b : neighbors) {
            if (b.getA().equals(game.getNodes().get(0)) && b.getA().getRow() == b.getB().getRow()){ // Only need to check one direction since node 0 is always in top left
                // If node 0 and destination node form the two bridge endpoints of the adjacent horizontal bridge
                return this.bmgr.equivalence( // Connected in 1 <=> Hbridge should exist
                        this.connectionVariables[0][dest][1],
                        this.bmgr.or(
                                this.imgr.equal(
                                        this.fieldVariables[b.getA().getRow()][b.getA().getCol()+1],
                                        this.imgr.makeNumber(1)
                                ),
                                this.imgr.equal(
                                        this.fieldVariables[b.getA().getRow()][b.getA().getCol()+1],
                                        this.imgr.makeNumber(2)
                                )
                        )
                );
            } else if (b.getA().equals(game.getNodes().get(0)) && b.getA().getCol() == b.getB().getCol()){ // Only need to check one direction since node 0 is always in top left
                // If node 0 and destination node form the two bridge endpoints of the adjacent vertical bridge
                return this.bmgr.equivalence( // Connected in 1 <=> bridge should exist
                        this.connectionVariables[0][dest][1],
                        this.bmgr.or(
                                this.imgr.equal(
                                        this.fieldVariables[b.getA().getRow()+1][b.getA().getCol()],
                                        this.imgr.makeNumber(3)
                                ),
                                this.imgr.equal(
                                        this.fieldVariables[b.getA().getRow()+1][b.getA().getCol()],
                                        this.imgr.makeNumber(4)
                                )
                        )
                );
            }
        }
        // If node 0 and destination node don't form an adjacent bridge and thus not reachable in 1 step
        return this.bmgr.not(
                this.connectionVariables[0][dest][1]
        );
    }

    // Set a γ variable equivalent to a shorter connection or express in neighbors perspective
    private BooleanFormula areNodesConnectedInISteps(int dest, int i, Game game) {
        ArrayList<Bridge> neighbors = game.getBridgesFrom(game.getNodes().get(dest)); // Retrieve bridges connected to destination
        ArrayList<BooleanFormula> temp = new ArrayList<>(); // Temporary list of conjunctions (γ0,n3,i-1 /\ x*)
        for (Bridge b : neighbors) { // for every neighboring node describe what reaching destination from there means
            int n3; // n3 will be the node we will try to reach destination node from in one step
            if (game.getNodes().get(dest).equals(b.getA())) { // East or south bridge
                n3 = game.getNodes().indexOf(b.getB()); // n3 should not be destination (take the other bridge endpoint)
                if (b.getDirection().equals(Bridge.Direction.HORIZONTAL)) { // East
                    temp.add(
                            this.bmgr.and(
                                    this.connectionVariables[0][n3][i-1],
                                    this.bmgr.or(
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getA().getRow()][b.getA().getCol()+1],
                                                    this.imgr.makeNumber(1)
                                            ),
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getA().getRow()][b.getA().getCol()+1],
                                                    this.imgr.makeNumber(2)
                                            )
                                    )
                            )
                    );
                } else if (b.getDirection().equals(Bridge.Direction.VERTICAL)) { // South
                    temp.add(
                            this.bmgr.and(
                                    this.connectionVariables[0][n3][i-1],
                                    this.bmgr.or(
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getA().getRow()+1][b.getA().getCol()],
                                                    this.imgr.makeNumber(3)
                                            ),
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getA().getRow()+1][b.getA().getCol()],
                                                    this.imgr.makeNumber(4)
                                            )
                                    )
                            )
                    );
                }
            } else if (game.getNodes().get(dest).equals(b.getB())) { // West or north bridge
                n3 = game.getNodes().indexOf(b.getA()); // n3 should not be destination (take the other bridge endpoint)
                if (b.getDirection().equals(Bridge.Direction.HORIZONTAL)) { // West
                    temp.add(
                            this.bmgr.and(
                                    this.connectionVariables[0][n3][i-1],
                                    this.bmgr.or(
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getB().getRow()][b.getB().getCol()-1],
                                                    this.imgr.makeNumber(1)
                                            ),
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getB().getRow()][b.getB().getCol()-1],
                                                    this.imgr.makeNumber(2)
                                            )
                                    )
                            )
                    );
                } else if (b.getDirection().equals(Bridge.Direction.VERTICAL)) { // North
                    temp.add(
                            this.bmgr.and(
                                    this.connectionVariables[0][n3][i-1],
                                    this.bmgr.or(
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getB().getRow()-1][b.getB().getCol()],
                                                    this.imgr.makeNumber(3)
                                            ),
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getB().getRow()-1][b.getB().getCol()],
                                                    this.imgr.makeNumber(4)
                                            )
                                    )
                            )
                    );
                }
            }
        }
        BooleanFormula neighborDisjunction = this.bmgr.or(temp); // at least one case should be true

        return this.bmgr.equivalence(
                this.connectionVariables[0][dest][i],
                this.bmgr.or( // at least one case should be true
                        this.connectionVariables[0][dest][i-1],
                        neighborDisjunction
                )
        );
    }

    private void printConnectionVariables(Game game, Model model) {
        boolean[][][] solution2 = new boolean[game.getNodes().size()][game.getNodes().size()][game.getBridges().size()];
        for (int i = 0; i < (game.getNodes().size()); i++) {
            for (int j = 0; j < (game.getNodes().size()); j++) {
                for (int k = 1; k < (game.getBridges().size()); k++) {
                    solution2[i][j][k] = model.evaluate(connectionVariables[i][j][k]);
                }
            }
        }

        for (int i = 0; i < (game.getNodes().size()); i++) {
            for (int j = 0; j < (game.getNodes().size()); j++) {
                for (int k = 1; k < (game.getBridges().size()); k++) {
                    System.out.print(connectionVariables[i][j][k]);
                    System.out.print(": ");
                    System.out.println(solution2[i][j][k]);
                }
            }
        }
    }
}