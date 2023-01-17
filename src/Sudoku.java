import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.*;
import org.sosy_lab.java_smt.api.*;
import java.math.BigInteger;
import java.util.ArrayList;

public class Sudoku {
    public static void solve(String[] args) throws InvalidConfigurationException {
        Configuration config = Configuration.fromCmdLineArguments(args);
        LogManager logger = BasicLogManager.create(config);
        ShutdownManager shutdown = ShutdownManager.create();
        SolverContext context = SolverContextFactory.createSolverContext(
                config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.SMTINTERPOL);

        FormulaManager fmgr = context.getFormulaManager();
        BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();
        IntegerFormulaManager imgr = fmgr.getIntegerFormulaManager();


        // Create variables 1 to 81
        ArrayList<NumeralFormula.IntegerFormula> variables = new ArrayList<>();
        for (int i = 0; i < 81; i++) {
            variables.add(imgr.makeVariable(Integer.toString(i)));
        }


        // Construct constraint: forall entries it holds that its value is 1 <= x <= 9
        ArrayList<BooleanFormula> entryConstraints = new ArrayList<>();
        for (int i = 0; i < 81; i++) {
            entryConstraints.add(
                    bmgr.and(
                            imgr.greaterOrEquals(variables.get(i), imgr.makeNumber(1)),
                            imgr.lessOrEquals(variables.get(i), imgr.makeNumber(9))
                    )
            );
        }
        BooleanFormula eachEntryFilledConstraint = bmgr.and(entryConstraints);


        // Construct constraint: Each number appears at most once in each row
        ArrayList<BooleanFormula> rowConstraints = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 8; j++) {
                for (int k = 1; j+k < 9; k++) {
                    rowConstraints.add(
                            bmgr.not(imgr.equal(variables.get(9*i+j), variables.get(9*i+j+k)))
                    );
                }
            }
        }
        BooleanFormula eachNumberAtMostOnceRowsConstraint = bmgr.and(rowConstraints);


        // Construct constraint: Each number appears at most once in each column
        ArrayList<BooleanFormula> columnConstraints = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 8; j++) {
                for (int k = 1; j+k < 9; k++) {
                    columnConstraints.add(
                            bmgr.not(imgr.equal(variables.get(i+9*j), variables.get(i+9*j+9*k)))
                    );
                }
            }
        }
        BooleanFormula eachNumberAtMostOnceColumnsConstraint = bmgr.and(columnConstraints);


        // Construct constraint: Each number appears at most once in each subgrid
        // Very hacky code
        int[] indexList = {0,1,2,9,10,11,18,19,20,3,4,5,12,13,14,21,22,23,6,7,8,15,16,17,24,25,26,
                27,28,29,36,37,38,45,46,47,30,31,32,39,40,41,48,49,50,33,34,35,42,43,44,51,52,53,
                54,55,56,63,64,65,72,73,74,57,58,59,66,67,68,75,76,77,60,61,62,69,70,71,78,79,80};
        ArrayList<BooleanFormula> gridConstraints = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 8; j++) {
                for (int k = 1; j+k < 9; k++) {
                    gridConstraints.add(
                            bmgr.not(imgr.equal(variables.get(indexList[9*i+j]), variables.get(indexList[9*i+j+k])))
                    );
                }
            }
        }
        BooleanFormula eachNumberAtMostOnceGridConstraint = bmgr.and(gridConstraints);


        // Create example Sudoku
        BooleanFormula test1 = bmgr.and(
                imgr.equal(variables.get(1), imgr.makeNumber(6)),
                imgr.equal(variables.get(2), imgr.makeNumber(4)),
                imgr.equal(variables.get(5), imgr.makeNumber(5)),
                imgr.equal(variables.get(6), imgr.makeNumber(3)),
                imgr.equal(variables.get(11), imgr.makeNumber(2)),
                imgr.equal(variables.get(18), imgr.makeNumber(5)),
                imgr.equal(variables.get(19), imgr.makeNumber(8)),
                imgr.equal(variables.get(22), imgr.makeNumber(6)),
                imgr.equal(variables.get(24), imgr.makeNumber(7)),
                imgr.equal(variables.get(26), imgr.makeNumber(9)),
                imgr.equal(variables.get(30), imgr.makeNumber(6)),
                imgr.equal(variables.get(31), imgr.makeNumber(1)),
                imgr.equal(variables.get(35), imgr.makeNumber(4)),
                imgr.equal(variables.get(39), imgr.makeNumber(5)),
                imgr.equal(variables.get(41), imgr.makeNumber(9)),
                imgr.equal(variables.get(45), imgr.makeNumber(6)),
                imgr.equal(variables.get(49), imgr.makeNumber(4)),
                imgr.equal(variables.get(50), imgr.makeNumber(8)),
                imgr.equal(variables.get(54), imgr.makeNumber(1)),
                imgr.equal(variables.get(56), imgr.makeNumber(3)),
                imgr.equal(variables.get(58), imgr.makeNumber(9)),
                imgr.equal(variables.get(61), imgr.makeNumber(6)),
                imgr.equal(variables.get(62), imgr.makeNumber(7)),
                imgr.equal(variables.get(69), imgr.makeNumber(8)),
                imgr.equal(variables.get(74), imgr.makeNumber(6)),
                imgr.equal(variables.get(75), imgr.makeNumber(7)),
                imgr.equal(variables.get(78), imgr.makeNumber(5)),
                imgr.equal(variables.get(79), imgr.makeNumber(1))
        );

        BooleanFormula test2 = bmgr.and(
                imgr.equal(variables.get(5), imgr.makeNumber(4)),
                imgr.equal(variables.get(6), imgr.makeNumber(2)),
                imgr.equal(variables.get(7), imgr.makeNumber(9)),
                imgr.equal(variables.get(8), imgr.makeNumber(1)),
                imgr.equal(variables.get(11), imgr.makeNumber(9)),
                imgr.equal(variables.get(12), imgr.makeNumber(6)),
                imgr.equal(variables.get(14), imgr.makeNumber(5)),
                imgr.equal(variables.get(16), imgr.makeNumber(8)),
                imgr.equal(variables.get(22), imgr.makeNumber(1)),
                imgr.equal(variables.get(33), imgr.makeNumber(5)),
                imgr.equal(variables.get(35), imgr.makeNumber(2)),
                imgr.equal(variables.get(38), imgr.makeNumber(5)),
                imgr.equal(variables.get(39), imgr.makeNumber(9)),
                imgr.equal(variables.get(41), imgr.makeNumber(2)),
                imgr.equal(variables.get(42), imgr.makeNumber(1)),
                imgr.equal(variables.get(45), imgr.makeNumber(6)),
                imgr.equal(variables.get(47), imgr.makeNumber(4)),
                imgr.equal(variables.get(58), imgr.makeNumber(8)),
                imgr.equal(variables.get(64), imgr.makeNumber(9)),
                imgr.equal(variables.get(66), imgr.makeNumber(5)),
                imgr.equal(variables.get(68), imgr.makeNumber(1)),
                imgr.equal(variables.get(69), imgr.makeNumber(6)),
                imgr.equal(variables.get(72), imgr.makeNumber(1)),
                imgr.equal(variables.get(73), imgr.makeNumber(3)),
                imgr.equal(variables.get(74), imgr.makeNumber(7)),
                imgr.equal(variables.get(75), imgr.makeNumber(2))
        );


        // Solve with SMT solver
        Model model = null;
        try (ProverEnvironment prover = context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            // Add constraints
            prover.addConstraint(eachEntryFilledConstraint);
            prover.addConstraint(eachNumberAtMostOnceRowsConstraint);
            prover.addConstraint(eachNumberAtMostOnceColumnsConstraint);
            prover.addConstraint(eachNumberAtMostOnceGridConstraint);

            // Add input puzzle as constraint
            prover.addConstraint(test1); // https://www.websudoku.com/?level=3&set_id=8324610838
//            prover.addConstraint(test2); // https://www.websudoku.com/?level=4&set_id=8109613335

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
        for (int i = 0; i < 81; i++) {
            solution.add(model.evaluate(variables.get(i)));
        }

        // Print solution
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                System.out.print(solution.get(i*9+j));
                System.out.print(' ');
                if (j % 3 == 2) {
                    System.out.print(' ');
                }
            }
            System.out.println();
            if (i % 3 == 2) {
                System.out.println();
            }
        }
    }
}