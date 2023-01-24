import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.*;
import org.sosy_lab.java_smt.api.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws InvalidConfigurationException {
        Game game1 = new Game(7, "4d2b1b4a26b3m1c3c3c2d3");
        Game game2 = new Game(7, "a4d32a4b3b3g4a2c2c3g13d2a");

        for (Bridge bridge : game1.getBridges()) {
            System.out.print(bridge.getA().getX());
            System.out.print(' ');
            System.out.print(bridge.getA().getY());
            System.out.print('\t');
            System.out.print('\t');
            System.out.print(bridge.getB().getX());
            System.out.print(' ');
            System.out.println(bridge.getB().getY());
        }



        Configuration config = Configuration.fromCmdLineArguments(args);    // TODO class solver
        LogManager logger = BasicLogManager.create(config);
        ShutdownManager shutdown = ShutdownManager.create();
        SolverContext context = SolverContextFactory.createSolverContext(
            config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.SMTINTERPOL);

        FormulaManager fmgr = context.getFormulaManager();
        BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();
        IntegerFormulaManager imgr = fmgr.getIntegerFormulaManager();

        // Create variables for each potential bridge (a.k.a. moves to make)
        ArrayList<NumeralFormula.IntegerFormula> variables = new ArrayList<>();
        for (int i = 0; i < (game1.getBridges().size()); i++) {
            variables.add(imgr.makeVariable(Integer.toString(i)));
        }

        // Bridges are either non-existent, single, or double
        ArrayList<BooleanFormula> bridgesHaveCorrectSizesList = new ArrayList<>();
        for (NumeralFormula.IntegerFormula v : variables) {
            bridgesHaveCorrectSizesList.add(
                    bmgr.and(
                            imgr.greaterOrEquals(v, imgr.makeNumber(0)),
                            imgr.lessOrEquals(v, imgr.makeNumber(2))
                    )
            );
        }
        BooleanFormula bridgesHaveCorrectSizesConstraint = bmgr.and(bridgesHaveCorrectSizesList);

        // Bridges satisfy nodes
        ArrayList<BooleanFormula> nodesSatisfiedList = new ArrayList<>();
        for (Node n : game1.getNodes()) {
            NumeralFormula.IntegerFormula ctr = imgr.makeNumber(0);
            for (int i = 0; i < variables.size(); i++) {
                if (game1.getBridges().get(i).getA().getX() == n.getX() && game1.getBridges().get(i).getA().getY() == n.getY()
                        || game1.getBridges().get(i).getB().getX() == n.getX() && game1.getBridges().get(i).getB().getY() == n.getY()) {
                    ctr = imgr.add(ctr, variables.get(i)); // ctr = sum of amount of bridge endpoints (including weight) corresponding to one node
                }
            }
            nodesSatisfiedList.add(
                    imgr.equal(ctr, imgr.makeNumber(n.getVal()))
            );
        }
        BooleanFormula nodesSatisfiedConstraint = bmgr.and(nodesSatisfiedList);



        // Solve with SMT solver
        Model model = null;
        try (ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            // Add constraints
            prover.addConstraint(bridgesHaveCorrectSizesConstraint);
            prover.addConstraint(nodesSatisfiedConstraint);

            // Add input puzzle as constraint
//            prover.addConstraint(null);

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

//        for (BigInteger b : solution) {
//            System.out.println(b);
//        }

        game1.setBridgeWeights(solution);

        game1.printGame();
//        System.out.println();
//        game2.printGame();
//
//        Sudoku.solve(args);
    }
}