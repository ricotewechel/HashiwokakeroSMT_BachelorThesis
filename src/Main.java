import org.sosy_lab.common.configuration.InvalidConfigurationException;

public class Main {
    public static void main(String[] args) throws InvalidConfigurationException {
        Solver solver = new Solver(args);

        Game game1 = new Game(7, "4d2b1b4a26b3m1c3c3c2d3");
        Game game2 = new Game(7, "a4d32a4b3b3g4a2c2c3g13d2a");
        Game game3 = new Game(15, "2d4c4b5a4g1e2b2a4a6e2j2f4h2g1y2c2a5g4a3s4t3a3a2d2l1c3b3c2o1a2f4d4a3");
        Game game4 = new Game(15, "3h4i1a3a2b3b2b2d2e2l2e6a5a2b4c3t2a1b2b1d4d5f34i3b2p6m4a1b1c5c4r2f5b2c4k6a3");

        solver.solveGame(game1);
        solver.solveGame(game2);
        solver.solveGame(game3);
        solver.solveGame(game4);

        System.out.println(game1);
        System.out.println(game2);
        System.out.println(game3);
        System.out.println(game4);


//        Sudoku.solve(args);
    }
}