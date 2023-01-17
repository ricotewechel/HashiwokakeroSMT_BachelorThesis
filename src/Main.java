import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.*;
import org.sosy_lab.java_smt.api.*;
import java.math.BigInteger;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws InvalidConfigurationException {
        Game game1 = new Game(7, "4d2b1b4a26b3m1c3c3c2d3");
        Game game2 = new Game(7, "a4d32a4b3b3g4a2c2c3g13d2a");

        Configuration config = Configuration.fromCmdLineArguments(args);
        LogManager logger = BasicLogManager.create(config);
        ShutdownManager shutdown = ShutdownManager.create();
        SolverContext context = SolverContextFactory.createSolverContext(
            config, logger, shutdown.getNotifier(), SolverContextFactory.Solvers.SMTINTERPOL);

        FormulaManager fmgr = context.getFormulaManager();
        BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();
        IntegerFormulaManager imgr = fmgr.getIntegerFormulaManager();

        // Create variables for each potential bridge
        ArrayList<BooleanFormula> variables = new ArrayList<>();
        for (int i = 0; i < (game1.getHorBridges().size()); i++) {
            String s = "H";
            s += i;
            variables.add(bmgr.makeVariable(s));
        }
        for (int i = 0; i < (game1.getVerBridges().size()); i++) {
            String s = "V";
            s += i;
            variables.add(bmgr.makeVariable(s));
        }


        // Construct constraint 1: Nodes satisfied
        ArrayList<BooleanFormula> nodesSatisfied = new ArrayList<>();
        for (Node n : game1.getNodes()) {
            int ctr = 0;
            ctr += game1.getHorBridges().stream().filter(
                (bridge) -> ( // Filter on horizontal bridges of which an endpoint matches node coords
                    n.getX() == bridge.getA().getX() && n.getY() == bridge.getA().getY()
                    || n.getX() == bridge.getB().getX() && n.getY() == bridge.getB().getY()
                )
            ).count();
            ctr += game1.getVerBridges().stream().filter(
                (bridge) -> ( // Filter on vertical bridges of which an endpoint matches node coords
                    n.getX() == bridge.getA().getX() && n.getY() == bridge.getA().getY()
                    || n.getX() == bridge.getB().getX() && n.getY() == bridge.getB().getY()
                )
            ).count();
            nodesSatisfied.add(imgr.equal(imgr.makeNumber(n.getVal()), imgr.makeNumber(ctr)));
        }


        // Construct constraint 2: Bridge sizes
        // Construct constraint 3: Everything connected
        // Construct constraint 4: Strongly connected (!)
        // Construct constraint 5: No crossing bridges
        // Construct constraint 6: No overlapping bridges or obstructions by nodes


//        System.out.println(game1.possibleBridges());
//        System.out.println(game2.possibleBridges());

//        game1.printGame();
//        System.out.println();
//        game2.printGame();

//        Sudoku.solve(args);
    }
}