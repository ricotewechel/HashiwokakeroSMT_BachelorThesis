import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Solver {
    private final SolverContext context;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;

    public Solver(String[] args) throws InvalidConfigurationException { // TODO implement Solver as something that takes Class Encoding as argument
        Configuration config = Configuration.fromCmdLineArguments(args);
        LogManager logger = BasicLogManager.create(config);
        ShutdownManager shutdown = ShutdownManager.create();
        this.context = SolverContextFactory.createSolverContext(
                config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.SMTINTERPOL);

        FormulaManager fmgr = this.context.getFormulaManager();
        this.bmgr = fmgr.getBooleanFormulaManager();
        this.imgr = fmgr.getIntegerFormulaManager();
    }


    public void solveGame(Game game) { // TODO versimpel alles mbv equals
        // Create variables for each potential bridge (a.k.a. moves to make)
        // Indices of these variables match directly with the indices in game.bridges
        NumeralFormula.IntegerFormula[] bridgeVariables = new NumeralFormula.IntegerFormula[game.getBridges().size()];
        for (int i = 0; i < (game.getBridges().size()); i++) {
            bridgeVariables[i] = this.imgr.makeVariable("B" + i);
        }

        // Create variables for connectedness of each node pair in AT MOST i amount of steps, where i is at most edges-1
        // Indices i and j of these variables match directly with the indices in game.nodes
        BooleanFormula[][][] connectionVariables = new BooleanFormula[game.getNodes().size()][game.getNodes().size()][game.getBridges().size()];
        for (int i = 0; i < (game.getNodes().size()); i++) {
            for (int j = 0; j < (game.getNodes().size()); j++) {
                for (int k = 1; k < (game.getBridges().size()); k++) {
                    connectionVariables[i][j][k] = this.bmgr.makeVariable("C" + i + "," + j + "," + k);
                }
            }
        }
        

        // Constraint 1: Bridges are either non-existent, single, or double
        ArrayList<BooleanFormula> validBridgeSizesList = new ArrayList<>();
        for (NumeralFormula.IntegerFormula v : bridgeVariables) {
            validBridgeSizesList.add(
                    this.bmgr.and(
                            this.imgr.greaterOrEquals(v, this.imgr.makeNumber(0)),
                            this.imgr.lessOrEquals(v, this.imgr.makeNumber(2))
                    )
            );
        }
        BooleanFormula validBridgeSizesConstraint = this.bmgr.and(validBridgeSizesList);


        // Constraint 2: Node values are satisfied by bridge endpoints
        ArrayList<BooleanFormula> nodesSatisfiedList = new ArrayList<>();
        for (Node n : game.getNodes()) {
            NumeralFormula.IntegerFormula ctr = this.imgr.makeNumber(0);
            for (int i = 0; i < bridgeVariables.length; i++) {
                if (game.getBridges().get(i).getA().getCol() == n.getCol() && game.getBridges().get(i).getA().getRow() == n.getRow()
                        || game.getBridges().get(i).getB().getCol() == n.getCol() && game.getBridges().get(i).getB().getRow() == n.getRow()) {
                    ctr = this.imgr.add(ctr, bridgeVariables[i]); // ctr = sum of amount of bridge endpoints (including weight) on one node
                }
            }
            nodesSatisfiedList.add(
                    this.imgr.equal(ctr, this.imgr.makeNumber(n.getValue()))
            );
        }
        BooleanFormula nodesSatisfiedConstraint = this.bmgr.and(nodesSatisfiedList);


        // Constraint 3: Bridges don't cross
        ArrayList<BooleanFormula> bridgesDontCrossList = new ArrayList<>(); // TODO mss veranderen naar ifelse
        for (int i = 0; i < bridgeVariables.length - 1; i++) {
            for (int j = i + 1; j < bridgeVariables.length; j++) {
                if (game.getBridges().get(i).getDirection() == Bridge.Direction.HORIZONTAL
                        && game.getBridges().get(j).getDirection() == Bridge.Direction.VERTICAL) { // Compare every horizontal bridge with all verticals
                    bridgesDontCrossList.add(this.bmgr.not(this.bmgr.and( // It should not be the case that all cases below are true
                            this.bmgr.and( // Horizontal bridge exists
                                    this.imgr.greaterOrEquals(bridgeVariables[i], this.imgr.makeNumber(1)),
                                    this.imgr.lessOrEquals(bridgeVariables[i], this.imgr.makeNumber(2))
                            ),
                            this.bmgr.and( // Vertical bridge exists
                                    this.imgr.greaterOrEquals(bridgeVariables[j], this.imgr.makeNumber(1)),
                                    this.imgr.lessOrEquals(bridgeVariables[j], this.imgr.makeNumber(2))
                            ),
                            this.bmgr.and( // x coord of upper (and implicitly bottom) endpoint of ver. bridge is in between x coords of hor. bridge
                                    this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getCol()), this.imgr.makeNumber(game.getBridges().get(i).getA().getCol())),
                                    this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getCol()), this.imgr.makeNumber(game.getBridges().get(i).getB().getCol()))
                            ),
                            this.bmgr.and( // y coord of upper endpoint of ver. bridge is above y coords of hor. bridge
                                    this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getA().getRow())),
                                    this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getB().getRow()))
                            ),
                            this.bmgr.and( // y coord of bottom endpoint of ver. bridge is under y coords of hor. bridge
                                    this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getB().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getA().getRow())),
                                    this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getB().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getB().getRow()))
                            )
                    )));
                } else if (game.getBridges().get(i).getDirection() == Bridge.Direction.VERTICAL
                        && game.getBridges().get(j).getDirection() == Bridge.Direction.HORIZONTAL) { // Compare every vertical bridge with all horizontals
                    bridgesDontCrossList.add(this.bmgr.not(this.bmgr.and( // It should not be the case that all cases below are true
                            this.bmgr.and( // Vertical bridge exists
                                    this.imgr.greaterOrEquals(bridgeVariables[i], this.imgr.makeNumber(1)),
                                    this.imgr.lessOrEquals(bridgeVariables[i], this.imgr.makeNumber(2))
                            ),
                            this.bmgr.and( // Horizontal bridge exists
                                    this.imgr.greaterOrEquals(bridgeVariables[j], this.imgr.makeNumber(1)),
                                    this.imgr.lessOrEquals(bridgeVariables[j], this.imgr.makeNumber(2))
                            ),
                            this.bmgr.and( // y coord of left (and implicitly right) endpoint of hor. bridge is in between y coords of ver. bridge
                                    this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getA().getRow())),
                                    this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getRow()), this.imgr.makeNumber(game.getBridges().get(i).getB().getRow()))
                            ),
                            this.bmgr.and( // x coord of left endpoint of hor. bridge is left of x coords of ver. bridge
                                    this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getCol()), this.imgr.makeNumber(game.getBridges().get(i).getA().getCol())),
                                    this.imgr.lessThan(this.imgr.makeNumber(game.getBridges().get(j).getA().getCol()), this.imgr.makeNumber(game.getBridges().get(i).getB().getCol()))
                            ),
                            this.bmgr.and( // x coord of right endpoint of hor. bridge is right of x coords of ver. bridge
                                    this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getB().getCol()), this.imgr.makeNumber(game.getBridges().get(i).getA().getCol())),
                                    this.imgr.greaterThan(this.imgr.makeNumber(game.getBridges().get(j).getB().getCol()), this.imgr.makeNumber(game.getBridges().get(i).getB().getCol()))
                            )
                    )));
                }
            }
        }
        BooleanFormula bridgesDontCrossConstraint = this.bmgr.and(bridgesDontCrossList);


        // Constraint 4: Everything is strongly connected // TODO subfuncties (Formula makeConstrConnected(n1,n2))
        ArrayList<BooleanFormula> everythingConnectedList = new ArrayList<>();
        for (int n1 = 0; n1 < (game.getNodes().size()); n1++) {
            for (int n2 = 0; n2 < (game.getNodes().size()); n2++) {
                for (int i = 1; i < (game.getBridges().size()); i++) {
                    if (n1 == n2) { // Nodes are connected to themselves in any amount of steps (always true)
                        everythingConnectedList.add(
                                this.bmgr.equivalence(
                                        connectionVariables[n1][n2][i],
                                        this.bmgr.makeTrue()
                                )
                        );
                    }
                    else if (i == 1) { // Two different nodes that are connected to each other in 1 step or less
                        ArrayList<Bridge> neighbors = game.findNeighbors(game.getNodes().get(n1)); // list of bridges possibly connected to n1
                        boolean found = false;
                        for (Bridge b : neighbors) {
                            if (b.getA() == game.getNodes().get(n1) && b.getB() == game.getNodes().get(n2)
                                    || b.getB() == game.getNodes().get(n1) && b.getA() == game.getNodes().get(n2)) { // if a node on one of those bridges equals n2
                                found = true; // then we found the bridge that resembles Cn1,n2,1
                                everythingConnectedList.add(
                                        this.bmgr.equivalence(
                                                connectionVariables[n1][n2][i],
                                                this.imgr.greaterThan(
                                                        bridgeVariables[game.getBridges().indexOf(b)],
                                                        imgr.makeNumber(0)
                                                )
                                        )
                                );
                                break; // Doesn't make sense to look at the other neighboring bridges
                            }
                        }
                        if (!found) { // if n2 is not directly neighboring, connection must be > 1
                            everythingConnectedList.add(
                                    this.bmgr.equivalence(
                                            connectionVariables[n1][n2][i],
                                            this.bmgr.makeFalse()
                                    )
                            );
                        }
                    }
                    else { // Two different nodes that are connected to each other in i steps or less (from 2 on)
                        ArrayList<Bridge> neighbors = game.findNeighbors(game.getNodes().get(n1));
                        ArrayList<BooleanFormula> temp = new ArrayList<>();
                        for (Bridge b : neighbors) { // for every neighboring node describe what reaching n2 from there means
                            int n3; // n3 will be the node we will try to reach n2 from in one less step
                            if (b.getA() == game.getNodes().get(n1)) {
                                n3 = game.getNodes().indexOf(b.getB()); // n3 should not be n1
                            } else if (b.getB() == game.getNodes().get(n1)) {
                                n3 = game.getNodes().indexOf(b.getA()); // n3 should not be n1
                            }
                            else continue; // error
                            temp.add(
                                    this.bmgr.and(
                                            this.imgr.greaterThan(
                                                    bridgeVariables[game.getBridges().indexOf(b)],
                                                    imgr.makeNumber(0)
                                            ),
                                            connectionVariables[n3][n2][i-1] // xi /\ C*,j,i-1
                                    )
                            );
                        }
                        BooleanFormula neighborDisjunction = this.bmgr.or(temp); // at least one case should be true

                        everythingConnectedList.add(
                                this.bmgr.equivalence(
                                        connectionVariables[n1][n2][i],
                                        this.bmgr.or(
                                                connectionVariables[n1][n2][i-1],
                                                neighborDisjunction
                                        )
                                )
                        );
                        if (i == game.getNodes().size()-1) { // When max path length, add that
                            everythingConnectedList.add(
                                    this.bmgr.equivalence(
                                            connectionVariables[n1][n2][i],
                                            this.bmgr.makeTrue()
                                    )
                            );
                        }
                    }
                }
            }
        }
        BooleanFormula nodesConnectedConstraint = this.bmgr.and(everythingConnectedList);


        // Solve with SMT solver
        Model model = null;
        try (ProverEnvironment prover = this.context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            // Add constraints
            prover.addConstraint(validBridgeSizesConstraint);
            prover.addConstraint(nodesSatisfiedConstraint);
            prover.addConstraint(bridgesDontCrossConstraint);
            prover.addConstraint(nodesConnectedConstraint);

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
        for (NumeralFormula.IntegerFormula v : bridgeVariables) {
            solution.add(model.evaluate(v));
        }

        game.setBridgeWeights(solution);
        game.fillField();

//        boolean[][][] solution2 = new boolean[game.getNodes().size()][game.getNodes().size()][game.getBridges().size()];
//        for (int i = 0; i < (game.getNodes().size()); i++) {
//            for (int j = 0; j < (game.getNodes().size()); j++) {
//                for (int k = 1; k < (game.getBridges().size()); k++) {
//                    solution2[i][j][k] = model.evaluate(connectionVariables[i][j][k]);
//                }
//            }
//        }
//
//        for (int i = 0; i < (game.getNodes().size()); i++) {
//            for (int j = 0; j < (game.getNodes().size()); j++) {
//                for (int k = 1; k < (game.getBridges().size()); k++) {
//                    System.out.print(connectionVariables[i][j][k]);
//                    System.out.print(": ");
//                    System.out.println(solution2[i][j][k]);
//                }
//            }
//        }
    }


    private int countNodes(Game game, ArrayList<NumeralFormula.IntegerFormula> variables) { // TODO mss subcomp
        ArrayList<Node> queue = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        Node start = game.getNodes().get(0);
        queue.add(start);
        int ctr = 0;

        while (!queue.isEmpty()) {
            Node node = queue.remove(0);
            for (int i = 0; i < game.getBridges().size(); i++) {
                if (game.getBridges().get(i).getA().equals(node)) {
                    Node temp = game.getBridges().get(i).getB();
                    if (!visited.contains(temp)) {
                        visited.add(temp);
                        queue.add(temp);
                        ctr++;
                    }
                } else if (game.getBridges().get(i).getB().equals(node)) {
                    Node temp = game.getBridges().get(i).getA();
                    if (!visited.contains(temp)) {
                        visited.add(temp);
                        queue.add(temp);
                        ctr++;
                    }
                }
            }
        }
        return ctr;
    }
}

