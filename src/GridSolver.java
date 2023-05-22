import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;
import java.math.BigInteger;
import java.util.*;

public class GridSolver {
    private final SolverContext context;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;
    private NumeralFormula.IntegerFormula[][] fieldVariables;
    public GridSolver(String[] args) throws InvalidConfigurationException {
        Configuration config = Configuration.fromCmdLineArguments(args);
        LogManager logger = BasicLogManager.create(config);
        ShutdownManager shutdown = ShutdownManager.create();
        this.context = SolverContextFactory.createSolverContext(
                config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.SMTINTERPOL);

        FormulaManager fmgr = this.context.getFormulaManager();
        this.bmgr = fmgr.getBooleanFormulaManager();
        this.imgr = fmgr.getIntegerFormulaManager();
    }


    public void solveGame(Game game) {
        this.createVariables(game);

        // Solve with SMT solver
        Model model = null;
        try (ProverEnvironment prover = this.context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            // Add constraints
            prover.addConstraint(validCellsConstraint(game));
            prover.addConstraint(nodesConstraint(game));
            prover.addConstraint(neighborConstraint(game));
            prover.addConstraint(nodesSatisfiedConstraint(game));

            boolean isUnsat = prover.isUnsat();
                if (!isUnsat) {
                model = prover.getModel();
            };
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

        game.fillFieldNaive(solution);
    }


    private enum Direction {
        NORTH,
        EAST,
        SOUTH,
        WEST
    }

    // Retrieves all directions that have neighbors
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
        // Create variables per field cell
        this.fieldVariables = new NumeralFormula.IntegerFormula[game.getFieldSize()][game.getFieldSize()];
        for (int i = 0; i < game.getFieldSize(); i++) {
            for (int j = 0; j < game.getFieldSize(); j++) {
                this.fieldVariables[i][j] = this.imgr.makeVariable("F" + i + "," + j);
            }
        }
    }

    // Cells can either be empty, single horizontal, double horizontal, single vertical, double vertical, or nodes
    // Respectively encoded as 0, 1, 2, 3, 4, 5
    // Corners may not be bridge pieces, edges may only be bridge pieces along the axis
    private BooleanFormula validCellsConstraint(Game game) {
        ArrayList<BooleanFormula> validCellsList = new ArrayList<>();

        for (int row = 0; row < game.getFieldSize(); row++) {
            for (int col = 0; col < game.getFieldSize(); col++) {
                ArrayList<BooleanFormula> cellValidList = new ArrayList<>();
                cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(0))); // e
                cellValidList.add(this.imgr.equal(this.fieldVariables[row][col], this.imgr.makeNumber(5))); // o
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
        return this.bmgr.and(validCellsList);
    }


    // Set constraints for all input nodes, prohibit other cells from becoming nodes
    private BooleanFormula nodesConstraint(Game game) {
        ArrayList<BooleanFormula> nodesList = new ArrayList<>();
        for (int i = 0; i < game.getFieldSize(); i++) {
            for (int j = 0; j < game.getFieldSize(); j++) {
                Node n = new Node(i, j, 0);
                if (game.getNodes().contains(n)) {
                    nodesList.add(
                            this.imgr.equal(this.fieldVariables[i][j], imgr.makeNumber(5))
                    );
                }
                else {
                    nodesList.add(
                            this.bmgr.not(this.imgr.equal(this.fieldVariables[i][j], imgr.makeNumber(5)))
                    );
                }
            }
        }
        return this.bmgr.and(nodesList);
    }


    // Creates restrictions for possible neighbors
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

    // Bridges must be between two nodes, must be vertical or horizontal, must be single or double, and may not cross
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


    // All nodes must have neighboring bridge pieces that add up to node value.
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
        return bmgr.and(nodesSatisfiedList);
    }
}