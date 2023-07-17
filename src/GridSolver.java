import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;
import java.math.BigInteger;
import java.util.ArrayList;

public class GridSolver {
    private final SolverContext context;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;
    private NumeralFormula.IntegerFormula[][] fieldVariables;
    private BooleanFormula[][] connectionVariables;

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


    public ArrayList<Long> solveGame(Game game) {
//        Game secondgame = game;
        this.createVariables(game);

        long t0 = 0;
        long constrTime = 0;
        long t1 = 0;
        long unsatTime = 0;
        long t2 = 0;
        long satTime = 0;
        long totalTime = 0;
        ArrayList<Long> times = new ArrayList<>();

        // Solve with SMT solver
        Model model = null;
        try (ProverEnvironment prover = this.context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            // Add constraints
            t0 = System.currentTimeMillis();
            prover.addConstraint(this.validCellsConstraint1(game));
            prover.addConstraint(this.neighborConstraint(game));
            prover.addConstraint(this.nodesSatisfiedConstraint(game));
            prover.addConstraint(this.nodesConnectedConstraint(game));
            constrTime = System.currentTimeMillis() - t0; // Time it takes to construct all constraints
            times.add(constrTime);

            t1 = System.currentTimeMillis();
            boolean isUnsat = prover.isUnsat();
            unsatTime = System.currentTimeMillis() - t1; // Time it takes to verify that the puzzle is unsatisfiable
            times.add(unsatTime);

            if (!isUnsat) {
                t2 = System.currentTimeMillis();
                model = prover.getModel();
                satTime = System.currentTimeMillis() - t2; // Time it takes to retrieve the solution model
                times.add(satTime);
            }
            totalTime = System.currentTimeMillis() - t1; // Total it takes to verify and retrieve solution (unsatTime + satTime)
            times.add(totalTime);

        } catch (SolverException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        assert model != null;

        // Retrieve solution
        BigInteger[][] solution = new BigInteger[game.getFieldSize()][game.getFieldSize()];

        for (int i = 1; i < game.getFieldSize()+1; i++) {
            for (int j = 1; j < game.getFieldSize()+1; j++) {
                solution[i-1][j-1] = model.evaluate(this.fieldVariables[i][j]);
            }
        }

        game.fillFieldGridEncoding(solution);

//        this.printConnectionVariables(game, model);

        return times;
    }

    public Boolean hasUniqueSolution (Game game) {
        this.createVariables(game);

        try (ProverEnvironment prover = this.context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            BigInteger[][] encoding = game.getFieldEncoding();
            ArrayList<BooleanFormula> solList = new ArrayList<>();

            for (int i = 0; i < this.fieldVariables.length; i++) {
                for (int j = 0; j < this.fieldVariables.length; j++) {
                    if (i == 0 || j == 0 || i == game.getFieldSize()+1 || j == game.getFieldSize()+1)
                        solList.add(this.imgr.equal(this.fieldVariables[i][j], this.imgr.makeNumber(0)));
                    else
                        solList.add(this.imgr.equal(this.fieldVariables[i][j], this.imgr.makeNumber(encoding[i-1][j-1])));
                }
            }
            BooleanFormula isNotFirstSolution = this.bmgr.not(this.bmgr.and(solList));

            // Add constraints
            prover.addConstraint(isNotFirstSolution);
            prover.addConstraint(this.validCellsConstraint1(game));
            prover.addConstraint(this.neighborConstraint(game));
            prover.addConstraint(this.nodesSatisfiedConstraint(game));
            prover.addConstraint(this.nodesConnectedConstraint(game));

            return prover.isUnsat();
        } catch (SolverException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    // Used for neighbor directions
    private enum Direction {
        NORTH,
        EAST,
        SOUTH,
        WEST
    }


    private void createVariables(Game game) {
        // Create variables for each grid cell (each cell can be empty, a node, or a bridge piece)
        this.fieldVariables = new NumeralFormula.IntegerFormula[game.getFieldSize()+2][game.getFieldSize()+2];
        for (int i = 0; i < game.getFieldSize()+2; i++) { // +2 to introduce a boundary around the game for empty cells
            for (int j = 0; j < game.getFieldSize()+2; j++) { // +2 to introduce a boundary around the game for empty cells
                this.fieldVariables[i][j] = this.imgr.makeVariable("φ" + i + "," + j); // φi,j represents field i-1,j-1 in the actual game, because of the boundary
            }
        }

        // Create variables for connectedness of each node pair in AT MOST i amount of steps, where i is at most edges-1
        // Indices i and j of these variables match directly with the indices in game.nodes
        this.connectionVariables = new BooleanFormula[game.getNodes().size()][game.getNodes().size()];
        for (int n = 0; n < (game.getNodes().size()); n++) {
            for (int i = 1; i < (game.getNodes().size()); i++) {
                this.connectionVariables[n][i] = this.bmgr.makeVariable("γ0," + n + "," + i);
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

        for (int row = 0; row < game.getFieldSize()+2; row++) { // Loop through field excluding boundaries
            for (int col = 0; col < game.getFieldSize()+2; col++) { // Loop through field excluding boundaries
                if (row == 0 || col == 0 || row == game.getFieldSize()+1 || col == game.getFieldSize()+1) {
                    validCellsList.add(
                            this.bmgr.and( // Force outer bound to be empty in any case
                                    this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(0))
                            )
                    );
                } else {
                    // Only cells with same coords as nodes list may be nodes
                    Node n = new Node(row-1, col-1, 0);
                    if (game.getNodes().contains(n)) {
                        validCellsList.add(
                                this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(5))
                        );
                    } else { // All other cells can be other pieces
                        validCellsList.add(
                                this.bmgr.and(
                                        this.imgr.greaterOrEquals(this.fieldVariables[row][col], this.imgr.makeNumber(0)),
                                        this.imgr.lessOrEquals(this.fieldVariables[row][col], this.imgr.makeNumber(4))
                                )
                        );
                    }
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

        for (int row = 0; row < game.getFieldSize()+2; row++) { // Loop through field excluding boundaries
            for (int col = 0; col < game.getFieldSize()+2; col++) { // Loop through field excluding boundaries
                if (row == 0 || col == 0 || row == game.getFieldSize()+1 || col == game.getFieldSize()+1) {
                    validCellsList.add(
                            this.bmgr.and( // Force outer bound to be empty in any case
                                    this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(0))
                            )
                    );
                } else {
                    // Only cells with same coords as nodes list may be nodes
                    Node n = new Node(row-1, col-1, 0);
                    if (game.getNodes().contains(n)) {
                        validCellsList.add(
                                this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(5))
                        );
                    } else { // All other cells can be other pieces AND SHOULD NOT BE A NODE
                        validCellsList.add(
                                this.bmgr.and(
                                        this.imgr.greaterOrEquals(this.fieldVariables[row][col], this.imgr.makeNumber(0)),
                                        this.imgr.lessOrEquals(this.fieldVariables[row][col], this.imgr.makeNumber(4))
                                )
                        );
                        validCellsList.add( // Adding this proposition explicitly significantly improves speed (Only in old encoding without boundaries?)
                                this.bmgr.not(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(5)))
                        );
                    }
                }
            }
        }
        return this.bmgr.and(validCellsList);
    }


    // Constraint 1 to 4: Bridges must be between two nodes, must be vertical or horizontal, must be single or double, and may not cross
    private BooleanFormula neighborConstraint(Game game) {
        ArrayList<BooleanFormula> neighborList = new ArrayList<>();
        for (int i = 1; i < game.getFieldSize()+1; i++) { // Loop through field excluding boundaries
            for (int j = 1; j < game.getFieldSize()+1; j++) { // Loop through field excluding boundaries
                for (int p = 1; p <= 4; p++) {
                    neighborList.add(
                            this.bmgr.implication(
                                    this.imgr.equal(this.fieldVariables[i][j], this.imgr.makeNumber(p)),
                                    this.bmgr.and(
                                            getNeighborRestrictionList(i, j, p)
                                    )
                            )
                    );
                }
            }
        }
        return this.bmgr.and(neighborList);
    }

    // Creates restrictions for possible neighbors, used in neighborConstraint()
    private ArrayList<BooleanFormula> getNeighborRestrictionList(int i, int j, int piece) {
        ArrayList<BooleanFormula> neighborRestrictionList = new ArrayList<>();
        if (piece == 1 || piece == 2) { // ─ or ═
            neighborRestrictionList.add(
                    this.bmgr.or(
                            this.imgr.equal(this.fieldVariables[i][j-1], this.imgr.makeNumber(piece)), // same piece west
                            this.imgr.equal(this.fieldVariables[i][j-1], this.imgr.makeNumber(5)) // cell west
                    )
            );
            neighborRestrictionList.add(
                    this.bmgr.or(
                            this.imgr.equal(this.fieldVariables[i][j+1], this.imgr.makeNumber(piece)), // same piece east
                            this.imgr.equal(this.fieldVariables[i][j+1], this.imgr.makeNumber(5)) // cell east
                    )
            );
        } else if (piece == 3 || piece == 4) { // | or ‖
            neighborRestrictionList.add(
                    this.bmgr.or(
                            this.imgr.equal(this.fieldVariables[i-1][j], this.imgr.makeNumber(piece)), // same piece north
                            this.imgr.equal(this.fieldVariables[i-1][j], this.imgr.makeNumber(5)) // cell north
                    )
            );
            neighborRestrictionList.add(
                    this.bmgr.or(
                            this.imgr.equal(this.fieldVariables[i+1][j], this.imgr.makeNumber(piece)), // same piece south
                            this.imgr.equal(this.fieldVariables[i+1][j], this.imgr.makeNumber(5)) // cell south
                    )
            );
        }
        return neighborRestrictionList;
    }


    // Constraint 5: All nodes must have neighboring bridge pieces that add up to node value.
    private BooleanFormula nodesSatisfiedConstraint(Game game) {
        ArrayList<BooleanFormula> nodesSatisfiedList = new ArrayList<>();
        for (Node n : game.getNodes()) { // n is located at [row+1][col+1] in fieldvariables, so all fieldVariables +1
            ArrayList<NumeralFormula.IntegerFormula> sumList = new ArrayList<>();
            for (Direction dir : Direction.values()) {
                if (dir == Direction.NORTH)
                    sumList.add(
                            this.bmgr.ifThenElse(
                                    this.imgr.equal(
                                            this.fieldVariables[n.getRow()][n.getCol()+1],
                                            this.imgr.makeNumber(3)
                                    ),
                                    this.imgr.makeNumber(1),
                                    this.bmgr.ifThenElse(
                                            this.imgr.equal(
                                                    this.fieldVariables[n.getRow()][n.getCol()+1],
                                                    this.imgr.makeNumber(4)
                                            ),
                                            this.imgr.makeNumber(2),
                                            this.imgr.makeNumber(0)
                                    )
                            )
                    );
                else if (dir == Direction.EAST)
                    sumList.add(
                            this.bmgr.ifThenElse(
                                    this.imgr.equal(
                                            this.fieldVariables[n.getRow()+1][n.getCol()+2],
                                            this.imgr.makeNumber(1)
                                    ),
                                    this.imgr.makeNumber(1),
                                    this.bmgr.ifThenElse(
                                            this.imgr.equal(
                                                    this.fieldVariables[n.getRow()+1][n.getCol()+2],
                                                    this.imgr.makeNumber(2)
                                            ),
                                            this.imgr.makeNumber(2),
                                            this.imgr.makeNumber(0)
                                    )
                            )
                    );
                else if (dir == Direction.SOUTH)
                    sumList.add(
                            this.bmgr.ifThenElse(
                                    this.imgr.equal(
                                            this.fieldVariables[n.getRow()+2][n.getCol()+1],
                                            this.imgr.makeNumber(3)
                                    ),
                                    this.imgr.makeNumber(1),
                                    this.bmgr.ifThenElse(
                                            this.imgr.equal(
                                                    this.fieldVariables[n.getRow()+2][n.getCol()+1],
                                                    this.imgr.makeNumber(4)
                                            ),
                                            this.imgr.makeNumber(2),
                                            this.imgr.makeNumber(0)
                                    )
                            )
                    );
                else if (dir == Direction.WEST)
                    sumList.add(
                            this.bmgr.ifThenElse(
                                    this.imgr.equal(
                                            this.fieldVariables[n.getRow()+1][n.getCol()],
                                            this.imgr.makeNumber(1)
                                    ),
                                    this.imgr.makeNumber(1),
                                    this.bmgr.ifThenElse(
                                            this.imgr.equal(
                                                    this.fieldVariables[n.getRow()+1][n.getCol()],
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
            for (int i = 1; i < game.getNodes().size(); i++) {
                if (0 == dest) { // γ0,0,i <=> True
                    everythingConnectedList.add(this.areNodesConnectedTrue(dest, i));
                } else if (i == 1) { // γ0,2,1 <=> x1  or  γ0,3,1 <=> False
                    everythingConnectedList.add(this.areNodesConnectedInOneStep(dest, game));
                } else { // γ0,3,2 <=> γ0,3,1 \/ (γ0,1,1 /\ x2) \/ (γ0,2,1 /\ x3)
                    everythingConnectedList.add(this.areNodesConnectedInISteps(dest, i, game));
                }
                if (i == game.getNodes().size()-1) { // γ0,x,n-1 <=> True
                    everythingConnectedList.add(this.areNodesConnectedTrue(dest, i));
                }
            }
        }
        return this.bmgr.and(everythingConnectedList);
    }

    // Set a γ to true (if 0 == destination (vacuously) or if γx,y,n-1 (force connectedness))
    private BooleanFormula areNodesConnectedTrue(int dest, int i) {
        return this.connectionVariables[dest][i];
    }

    // Set a γ variable equivalent to a direct bridge or to false if not applicable
    private BooleanFormula areNodesConnectedInOneStep(int dest, Game game) { // dest is located at [row+1][col+1] in fieldvariables, so all fieldVariables +1
        ArrayList<Bridge> neighbors = game.getBridgesFrom(game.getNodes().get(dest)); // Retrieve bridges connected to destination node
        for (Bridge b : neighbors) {
            if (b.getA().equals(game.getNodes().get(0)) && b.getA().getRow() == b.getB().getRow()){ // Only need to check one direction since node 0 is always in top left
                // If node 0 and destination node form the two bridge endpoints of the adjacent horizontal bridge
                return this.bmgr.equivalence( // Connected in 1 <=> Hbridge should exist
                        this.connectionVariables[dest][1],
                        this.bmgr.or(
                                this.imgr.equal(
                                        this.fieldVariables[b.getA().getRow()+1][b.getA().getCol()+2],
                                        this.imgr.makeNumber(1)
                                ),
                                this.imgr.equal(
                                        this.fieldVariables[b.getA().getRow()+1][b.getA().getCol()+2],
                                        this.imgr.makeNumber(2)
                                )
                        )
                );
            } else if (b.getA().equals(game.getNodes().get(0)) && b.getA().getCol() == b.getB().getCol()){ // Only need to check one direction since node 0 is always in top left
                // If node 0 and destination node form the two bridge endpoints of the adjacent vertical bridge
                return this.bmgr.equivalence( // Connected in 1 <=> bridge should exist
                        this.connectionVariables[dest][1],
                        this.bmgr.or(
                                this.imgr.equal(
                                        this.fieldVariables[b.getA().getRow()+2][b.getA().getCol()+1],
                                        this.imgr.makeNumber(3)
                                ),
                                this.imgr.equal(
                                        this.fieldVariables[b.getA().getRow()+2][b.getA().getCol()+1],
                                        this.imgr.makeNumber(4)
                                )
                        )
                );
            }
        }
        // If node 0 and destination node don't form an adjacent bridge and thus not reachable in 1 step
        return this.bmgr.not(
                this.connectionVariables[dest][1]
        );
    }

    // Set a γ variable equivalent to a shorter connection or express in neighbors perspective
    private BooleanFormula areNodesConnectedInISteps(int dest, int i, Game game) { // dest is located at [row+1][col+1] in fieldvariables, so all fieldVariables +1
        ArrayList<Bridge> neighbors = game.getBridgesFrom(game.getNodes().get(dest)); // Retrieve bridges connected to destination
        ArrayList<BooleanFormula> temp = new ArrayList<>(); // Temporary list of conjunctions (γ0,n3,i-1 /\ x*)
        for (Bridge b : neighbors) { // for every neighboring node describe what reaching destination from there means
            int n3; // n3 will be the node we will try to reach destination node from in one step
            if (game.getNodes().get(dest).equals(b.getA())) { // East or south bridge
                n3 = game.getNodes().indexOf(b.getB()); // n3 should not be destination (take the other bridge endpoint)
                if (b.getDirection().equals(Bridge.Direction.HORIZONTAL)) { // East
                    temp.add(
                            this.bmgr.and(
                                    this.connectionVariables[n3][i-1],
                                    this.bmgr.or(
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getA().getRow()+1][b.getA().getCol()+2],
                                                    this.imgr.makeNumber(1)
                                            ),
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getA().getRow()+1][b.getA().getCol()+2],
                                                    this.imgr.makeNumber(2)
                                            )
                                    )
                            )
                    );
                } else if (b.getDirection().equals(Bridge.Direction.VERTICAL)) { // South
                    temp.add(
                            this.bmgr.and(
                                    this.connectionVariables[n3][i-1],
                                    this.bmgr.or(
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getA().getRow()+2][b.getA().getCol()+1],
                                                    this.imgr.makeNumber(3)
                                            ),
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getA().getRow()+2][b.getA().getCol()+1],
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
                                    this.connectionVariables[n3][i-1],
                                    this.bmgr.or(
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getB().getRow()+1][b.getB().getCol()],
                                                    this.imgr.makeNumber(1)
                                            ),
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getB().getRow()+1][b.getB().getCol()],
                                                    this.imgr.makeNumber(2)
                                            )
                                    )
                            )
                    );
                } else if (b.getDirection().equals(Bridge.Direction.VERTICAL)) { // North
                    temp.add(
                            this.bmgr.and(
                                    this.connectionVariables[n3][i-1],
                                    this.bmgr.or(
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getB().getRow()][b.getB().getCol()+1],
                                                    this.imgr.makeNumber(3)
                                            ),
                                            this.imgr.equal(
                                                    this.fieldVariables[b.getB().getRow()][b.getB().getCol()+1],
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
                this.connectionVariables[dest][i],
                this.bmgr.or( // at least one case should be true
                        this.connectionVariables[dest][i-1],
                        neighborDisjunction
                )
        );
    }

    private void printConnectionVariables(Game game, Model model) {
        boolean[][] solution2 = new boolean[game.getNodes().size()][game.getNodes().size()];
        for (int n = 0; n < (game.getNodes().size()); n++) {
            for (int i = 1; i < (game.getNodes().size()); i++) {
                solution2[n][i] = model.evaluate(this.connectionVariables[n][i]);
            }
        }

        for (int n = 0; n < (game.getNodes().size()); n++) {
            for (int i = 1; i < (game.getNodes().size()); i++) {
                System.out.println(this.connectionVariables[n][i] + ": " + solution2[n][i]);
            }
        }
    }
}