import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;
import java.math.BigInteger;
import java.util.ArrayList;

public class SmartSolver {
    private final SolverContext context;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;
    private NumeralFormula.IntegerFormula[] bridgeVariables;
    private BooleanFormula[][][] connectionVariables;

    public SmartSolver(String[] args) throws InvalidConfigurationException {
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
            this.bridgeVariables[i] = this.imgr.makeVariable("B" + i); // TODO change B name
        }

        // Create variables for connectedness of each node pair in AT MOST i amount of steps, where i is at most edges-1
        // Indices i and j of these variables match directly with the indices in game.nodes
        this.connectionVariables = new BooleanFormula[game.getNodes().size()][game.getNodes().size()][game.getBridges().size()];
        for (int i = 0; i < (game.getNodes().size()); i++) {
            for (int j = 0; j < (game.getNodes().size()); j++) {
                for (int k = 1; k < (game.getBridges().size()); k++) {
                    this.connectionVariables[i][j][k] = this.bmgr.makeVariable("C" + i + "," + j + "," + k); // TODO i,j == j,i and change C name
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

        for (int n1 = 0; n1 < game.getNodes().size(); n1++) {
            for (int n2 = 0; n2 < game.getNodes().size(); n2++) {
                for (int i = 1; i < game.getBridges().size(); i++) {
                    if (n1 == n2) { // C2,2,i <=> True
                        everythingConnectedList.add(this.areNodesConnectedTrue(n1, n2, i));
                    } else if (i == 1) { // C1,3,1 <=> x2  or  C1,2,1 <=> False
                        everythingConnectedList.add(this.areNodesConnectedInOneStep(n1, n2, game));
                    } else { // C1,3,2 <=> C1,3,1 \/ (x0 /\ C0,3,1) \/ (x2 /\ C3,3,1)
                        everythingConnectedList.add(this.areNodesConnectedInISteps(n1, n2, i, game));
                    }
                    if (i == game.getNodes().size() - 1) { // Cx,y,e-1 <=> True
                        everythingConnectedList.add(this.areNodesConnectedTrue(n1, n2, i));
                    }
                }
            }
        }
        return bmgr.and(everythingConnectedList);
    }

    // Set a C to true (if n1 == n2 (vacuously) or if Cx,y,e-1 (force connectedness))
    private BooleanFormula areNodesConnectedTrue(int n1, int n2, int i) {
        return this.bmgr.equivalence(
                this.connectionVariables[n1][n2][i],
                this.bmgr.makeTrue()
        );
    }

    // Set a C variable equivalent to a direct bridge or to false if not applicable
    private BooleanFormula areNodesConnectedInOneStep(int n1, int n2, Game game) {
        ArrayList<Bridge> neighbors = game.getBridgesFrom(game.getNodes().get(n1)); // Retrieve bridges connected to n1
        for (Bridge b : neighbors) {
            if ((b.getA().equals(game.getNodes().get(n1)) && b.getB().equals(game.getNodes().get(n2))) ||
                    (b.getA().equals(game.getNodes().get(n2)) && b.getB().equals(game.getNodes().get(n1)))) {
                // If n1 and n2 form the two bridge endpoints of one of the adjacent bridges
                return this.bmgr.equivalence( // Connected in 1 <=> bridge should exist
                        this.connectionVariables[n1][n2][1],
                        this.imgr.greaterThan(
                                this.bridgeVariables[game.getBridges().indexOf(b)],
                                this.imgr.makeNumber(0)
                        )
                );
            }
        }
        // If n1 and n2 don't form an adjacent bridge and thus not reachable in 1 step
        return this.bmgr.equivalence(
                this.connectionVariables[n1][n2][1],
                this.bmgr.makeFalse()
        );
    }

    // Set a C variable equivalent to a shorter connection or express in neighbors perspective
    private BooleanFormula areNodesConnectedInISteps(int n1, int n2, int i, Game game) {
        ArrayList<Bridge> neighbors = game.getBridgesFrom(game.getNodes().get(n1)); // Retrieve bridges connected to n1
        ArrayList<BooleanFormula> temp = new ArrayList<>(); // Temporary list of conjunctions (x* /\ Cn3,n2,i-1)
        for (Bridge b : neighbors) { // for every neighboring node describe what reaching n2 from there means
            int n3; // n3 will be the node we will try to reach n2 from in one less step
            if (game.getNodes().get(n1).equals(b.getA())) {
                n3 = game.getNodes().indexOf(b.getB()); // n3 should not be n1
            } else if (game.getNodes().get(n1).equals(b.getB())) {
                n3 = game.getNodes().indexOf(b.getA()); // n3 should not be n1
            } else continue; // error (
            temp.add(
                    this.bmgr.and(
                            this.imgr.greaterThan(
                                    this.bridgeVariables[game.getBridges().indexOf(b)],
                                    imgr.makeNumber(0)
                            ),
                            this.connectionVariables[n3][n2][i - 1]
                    )
            );
        }
        BooleanFormula neighborDisjunction = this.bmgr.or(temp); // at least one case should be true

        return this.bmgr.equivalence(
                        this.connectionVariables[n1][n2][i],
                        this.bmgr.or( // at least one case should be true
                                this.connectionVariables[n1][n2][i - 1],
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



