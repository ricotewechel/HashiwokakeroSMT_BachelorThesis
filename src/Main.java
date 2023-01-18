import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.*;
import org.sosy_lab.java_smt.api.*;
import scala.Boolean;

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
        for (int i = 0; i < (game1.getBridges().size()); i++) {
            variables.add(bmgr.makeVariable(Integer.toString(i)));
        }

        // Construct constraint 1: Nodes satisfied
        ArrayList<BooleanFormula> nodeSatisfiedList = new ArrayList<>();
        for (Node n : game1.getNodes()) {
            nodeSatisfiedList.add(imgr.equal(imgr.makeNumber(n.getVal()), imgr.makeNumber(
                game1.getBridges().stream().filter(
                    (bridge) -> ( // Filter on bridges of which an endpoint matches node coordinates
                        n.getX() == bridge.getA().getX() && n.getY() == bridge.getA().getY()
                                || n.getX() == bridge.getB().getX() && n.getY() == bridge.getB().getY()
                    )
                ).count()
            )));
        }
        BooleanFormula nodesSatisfied = bmgr.and(nodeSatisfiedList);

        // Construct constraint 2: Bridge widths
        ArrayList<BooleanFormula> bridgeWidthValidList = new ArrayList<>();
        for (Bridge b : game1.getBridges()) {
            bridgeWidthValidList.add(
                    bmgr.and(
                            imgr.greaterOrEquals(imgr.makeNumber(b.getAmount()), imgr.makeNumber(0)),
                            imgr.lessOrEquals(imgr.makeNumber(b.getAmount()), imgr.makeNumber(2))
                    )
            );
        }
        BooleanFormula bridgeWidthsValid = bmgr.and(bridgeWidthValidList);

        // Construct constraint 3: No crossing bridges  TODO can probably be stripped on redundant clauses
        ArrayList<BooleanFormula> bridgesDontCrossList = new ArrayList<>();
        for (Bridge b : game1.getBridges()) {
            if (b.getDirection() == Bridge.Direction.HORIZONTAL) { // Compare every horizontal bridge with all verticals
                List<Bridge> verticals = game1.getBridges().stream().filter((bridge) ->
                        (bridge.getDirection() == Bridge.Direction.VERTICAL)).toList();
                for (Bridge v : verticals) {
                    bridgesDontCrossList.add(bmgr.not(bmgr.and( // It should not be the case that all cases below are true
                            bmgr.and( // x coord of upper endpoint of ver. bridge is in between x coords of hor. bridge
                                    imgr.greaterThan(imgr.makeNumber(v.getA().getX()), imgr.makeNumber(b.getA().getX())),
                                    imgr.lessThan(imgr.makeNumber(v.getA().getX()), imgr.makeNumber(b.getB().getX()))
                            ),
                            bmgr.and( // x coord of bottom endpoint of ver. bridge is in between x coords of hor. bridge    TODO probably redundant
                                    imgr.greaterThan(imgr.makeNumber(v.getB().getX()), imgr.makeNumber(b.getA().getX())),
                                    imgr.lessThan(imgr.makeNumber(v.getB().getX()), imgr.makeNumber(b.getB().getX()))
                            ),
                            bmgr.and( // y coord of upper endpoint of ver. bridge is above y coords of hor. bridge
                                    imgr.lessThan(imgr.makeNumber(v.getA().getY()), imgr.makeNumber(b.getA().getY())),
                                    imgr.lessThan(imgr.makeNumber(v.getA().getY()), imgr.makeNumber(b.getB().getY()))
                            ),
                            bmgr.and( // y coord of bottom endpoint of ver. bridge is under y coords of hor. bridge
                                    imgr.greaterThan(imgr.makeNumber(v.getB().getY()), imgr.makeNumber(b.getA().getY())),
                                    imgr.greaterThan(imgr.makeNumber(v.getB().getY()), imgr.makeNumber(b.getB().getY()))
                            )
                    )));
                }
            } else if (b.getDirection() == Bridge.Direction.VERTICAL) { // Compare every horizontal bridge with all verticals   TODO might not be needed, comparing vert with hor == hor with vert
                List<Bridge> horizontals = game1.getBridges().stream().filter((bridge) ->
                        (bridge.getDirection() == Bridge.Direction.HORIZONTAL)).toList();
                for (Bridge h : horizontals) {
                    bridgesDontCrossList.add(bmgr.not(bmgr.and( // It should not be the case that all cases below are true
                            bmgr.and( // y coord of left endpoint of hor. bridge is in between y coords of ver. bridge
                                    imgr.greaterThan(imgr.makeNumber(h.getA().getY()), imgr.makeNumber(b.getA().getY())),
                                    imgr.lessThan(imgr.makeNumber(h.getA().getY()), imgr.makeNumber(b.getB().getY()))
                            ),
                            bmgr.and( // y coord of right endpoint of hor. bridge is in between y coords of ver. bridge
                                    imgr.greaterThan(imgr.makeNumber(h.getB().getY()), imgr.makeNumber(b.getA().getY())),
                                    imgr.lessThan(imgr.makeNumber(h.getB().getY()), imgr.makeNumber(b.getB().getY()))
                            ),
                            bmgr.and( // x coord of left endpoint of hor. bridge is left of x coords of ver. bridge
                                    imgr.lessThan(imgr.makeNumber(h.getA().getX()), imgr.makeNumber(b.getA().getX())),
                                    imgr.lessThan(imgr.makeNumber(h.getA().getX()), imgr.makeNumber(b.getB().getX()))
                            ),
                            bmgr.and( // x coord of right endpoint of hor. bridge is right of x coords of ver. bridge
                                    imgr.greaterThan(imgr.makeNumber(h.getB().getX()), imgr.makeNumber(b.getA().getX())),
                                    imgr.greaterThan(imgr.makeNumber(h.getB().getX()), imgr.makeNumber(b.getB().getX()))
                            )
                    )));
                }
            }
        }
        BooleanFormula bridgesDontCross = bmgr.and(bridgesDontCrossList);

        // Construct constraint 4: No overlapping bridges or obstructions by nodes


        // Construct constraint 5: Everything strongly connected


//        game1.printGame();
//        System.out.println();
//        game2.printGame();

//         Sudoku.solve(args);
    }
}