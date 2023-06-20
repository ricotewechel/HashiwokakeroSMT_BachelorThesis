import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;
import java.math.BigInteger;
import java.util.ArrayList;

public class GraphSolver {
    private final SolverContext context;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;
    private NumeralFormula.IntegerFormula[] bridgeVariables;
    private BooleanFormula[][][] connectionVariables;

    public GraphSolver(String[] args) throws InvalidConfigurationException {
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
            prover.addConstraint(this.validBridgeSizesConstraint());
            prover.addConstraint(this.bridgesDontCrossConstraint(game));
            prover.addConstraint(this.nodesSatisfiedConstraint(game));
            prover.addConstraint(this.nodesConnectedConstraint(game));

            boolean isUnsat = prover.isUnsat();
            if (!isUnsat) {
                model = prover.getModel();
            }
        } catch (SolverException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        assert model != null;

        // Retrieve solution
        ArrayList<BigInteger> solution = new ArrayList<>();
        for (NumeralFormula.IntegerFormula v : this.bridgeVariables) {
            solution.add(model.evaluate(v));
        }

        game.setBridgeWeights(solution);
        game.fillFieldImproved();

//        this.printConnectionVariables(game, model);
    }


    private void createVariables(Game game) {
        // Create variables for each potential bridge (a.k.a. moves to make)
        // Indices of these variables match directly with the indices in game.bridges
        this.bridgeVariables = new NumeralFormula.IntegerFormula[game.getBridges().size()];
        for (int i = 0; i < (game.getBridges().size()); i++) {
            this.bridgeVariables[i] = this.imgr.makeVariable("β" + i);
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


    // Constraint 3: Bridges are either non-existent, single, or double
    private BooleanFormula validBridgeSizesConstraint() {
        ArrayList<BooleanFormula> validBridgeSizesList = new ArrayList<>();
        for (NumeralFormula.IntegerFormula v : this.bridgeVariables) {
            validBridgeSizesList.add(
                    this.bmgr.and(
                            this.imgr.greaterOrEquals(v, this.imgr.makeNumber(0)),
                            this.imgr.lessOrEquals(v, this.imgr.makeNumber(2))
                    )
            );
        }
        return this.bmgr.and(validBridgeSizesList);
    }


    // Constraint 4: Bridges don't cross
    private BooleanFormula bridgesDontCrossConstraint(Game game) {
        ArrayList<BooleanFormula> bridgesDontCrossList = new ArrayList<>();
        for (int i = 0; i < this.bridgeVariables.length; i++) {
            for (int j = 0; j < this.bridgeVariables.length; j++) {
                if (game.getBridges().get(i).getDirection() == Bridge.Direction.HORIZONTAL
                        && game.getBridges().get(j).getDirection() == Bridge.Direction.VERTICAL) { // Compare every horizontal bridge with all verticals
                    bridgesDontCrossList.add(
                            this.bmgr.implication(
                                    this.bmgr.and( // If both bridges actually exist
                                            this.imgr.greaterThan(this.bridgeVariables[i], this.imgr.makeNumber(0)),
                                            this.imgr.greaterThan(this.bridgeVariables[j], this.imgr.makeNumber(0))
                                    ),
                                    this.bmgr.not(this.bmgr.and( // Then the bridges may not cross
                                            // x coord of upper (and implicitly bottom) endpoint of ver. bridge is in between x coords of hor. bridge
                                            this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getCol()), this.imgr.makeNumber(game.getBridges().get(i).getA().getCol())),
                                            this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getCol()), this.imgr.makeNumber(game.getBridges().get(i).getB().getCol())),
                                            // y coord of upper endpoint of ver. bridge is above y coords of hor. bridge
                                            this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getA().getRow())),
                                            this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getB().getRow())),
                                            // y coord of bottom endpoint of ver. bridge is under y coords of hor. bridge
                                            this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getB().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getA().getRow())),
                                            this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getB().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getB().getRow()))
                                    ))
                            )
                    );
                }
            }
        }
        return this.bmgr.and(bridgesDontCrossList);
    }


    // Constraint 5: Node values are satisfied by bridge endpoints
    private BooleanFormula nodesSatisfiedConstraint(Game game) {
        ArrayList<BooleanFormula> nodesSatisfiedList = new ArrayList<>();
        for (Node n : game.getNodes()) {
            NumeralFormula.IntegerFormula ctr = this.imgr.makeNumber(0);
            for (int i = 0; i < this.bridgeVariables.length; i++) {
                if (game.getBridges().get(i).getA().equals(n) || game.getBridges().get(i).getB().equals(n)) {
                    ctr = this.imgr.add(ctr, this.bridgeVariables[i]); // ctr = sum of amount of bridge endpoints (including weight) on one node
                }
            }
            nodesSatisfiedList.add(
                    this.imgr.equal(ctr, this.imgr.makeNumber(n.getValue()))
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
                } else { // γ0,3,2 <=> γ0,3,1 \/ (x2 /\ γ0,1,1) \/ (x3 /\ γ0,2,1)
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
            if ((b.getA().equals(game.getNodes().get(0)) && b.getB().equals(game.getNodes().get(dest)))) { // Only need to check one direction since node 0 is always in top left
                // If node 0 and destination node form the two bridge endpoints of one of the adjacent bridges
                return this.bmgr.equivalence( // Connected in 1 <=> bridge should exist
                        this.connectionVariables[0][dest][1],
                        this.imgr.greaterThan(
                                this.bridgeVariables[game.getBridges().indexOf(b)],
                                this.imgr.makeNumber(0)
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
        ArrayList<BooleanFormula> temp = new ArrayList<>(); // Temporary list of conjunctions (x* /\ γ0,n3,i-1) // TODO change order
        for (Bridge b : neighbors) { // for every neighboring node describe what reaching destination from there means
            int n3; // n3 will be the node we will try to reach destination node from in one step
            if (game.getNodes().get(dest).equals(b.getA())) {
                n3 = game.getNodes().indexOf(b.getB()); // n3 should not be destination (take the other bridge endpoint)
            } else if (game.getNodes().get(dest).equals(b.getB())) {
                n3 = game.getNodes().indexOf(b.getA()); // n3 should not be destination (take the other bridge endpoint)
            } else continue; // error
            temp.add(
                    this.bmgr.and(
                            this.imgr.greaterThan(
                                    this.bridgeVariables[game.getBridges().indexOf(b)],
                                    imgr.makeNumber(0)
                            ),
                            this.connectionVariables[0][n3][i-1]
                    )
            );
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




