import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.*;

import java.math.BigInteger;
import java.util.ArrayList;

public class Solver {
    private final SolverContext context;
    private final BooleanFormulaManager bmgr;
    private final IntegerFormulaManager imgr;

    public Solver(String[] args) throws InvalidConfigurationException {
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
        // Create variables for each potential bridge (a.k.a. moves to make)
        ArrayList<NumeralFormula.IntegerFormula> variables = new ArrayList<>();
        for (int i = 0; i < (game.getBridges().size()); i++) {
            variables.add(this.imgr.makeVariable(Integer.toString(i)));
        }

        // Constraint 1: Bridges are either non-existent, single, or double
        ArrayList<BooleanFormula> validBridgeSizesList = new ArrayList<>();
        for (NumeralFormula.IntegerFormula v : variables) {
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
            for (int i = 0; i < variables.size(); i++) {
                if (game.getBridges().get(i).getA().getCol() == n.getCol() && game.getBridges().get(i).getA().getRow() == n.getRow()
                        || game.getBridges().get(i).getB().getCol() == n.getCol() && game.getBridges().get(i).getB().getRow() == n.getRow()) {
                    ctr = this.imgr.add(ctr, variables.get(i)); // ctr = sum of amount of bridge endpoints (including weight) on one node
                }
            }
            nodesSatisfiedList.add(
                    this.imgr.equal(ctr, this.imgr.makeNumber(n.getValue()))
            );
        }
        BooleanFormula nodesSatisfiedConstraint = this.bmgr.and(nodesSatisfiedList);

        // Constraint 3: Bridges don't cross
        ArrayList<BooleanFormula> bridgesDontCrossList = new ArrayList<>();
        for (int i = 0; i < variables.size()-1; i++) {
            for (int j = i+1; j < variables.size(); j++) {
                if (game.getBridges().get(i).getDirection() == Bridge.Direction.HORIZONTAL
                        && game.getBridges().get(j).getDirection() == Bridge.Direction.VERTICAL) { // Compare every horizontal bridge with all verticals
                    bridgesDontCrossList.add(this.bmgr.not(this.bmgr.and( // It should not be the case that all cases below are true
                            this.bmgr.and( // Horizontal bridge exists
                                    this.imgr.greaterOrEquals(variables.get(i), this.imgr.makeNumber(1)),
                                    this.imgr.lessOrEquals(variables.get(i), this.imgr.makeNumber(2))
                            ),
                            this.bmgr.and( // Vertical bridge exists
                                    this.imgr.greaterOrEquals(variables.get(j), this.imgr.makeNumber(1)),
                                    this.imgr.lessOrEquals(variables.get(j), this.imgr.makeNumber(2))
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
                }
            }
        }
        BooleanFormula bridgesDontCrossConstraint = this.bmgr.and(bridgesDontCrossList);

        // Constraint 4: All nodes are strongly connected

        // Solve with SMT solver
        Model model = null;
        try (ProverEnvironment prover = this.context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            // Add constraints
            prover.addConstraint(validBridgeSizesConstraint);
            prover.addConstraint(nodesSatisfiedConstraint);
            prover.addConstraint(bridgesDontCrossConstraint);

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
        for (NumeralFormula.IntegerFormula v : variables) {
            solution.add(model.evaluate(v));
        }

        game.setBridgeWeights(solution);
        game.fillField();
    }
}
