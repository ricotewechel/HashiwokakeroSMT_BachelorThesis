import org.sosy_lab.common.configuration.InvalidConfigurationException;

public class Main {
    public static void main(String[] args) throws InvalidConfigurationException {
        Solver solver = new Solver(args);

        Game game1 = new Game(7, "4d2b1b4a26b3m1c3c3c2d3");
        Game game2 = new Game(7, "a4d32a4b3b3g4a2c2c3g13d2a");

        solver.solveGame(game1);
        solver.solveGame(game2);

        System.out.println(game1);
        System.out.println(game2);


//        Sudoku.solve(args);
    }
}