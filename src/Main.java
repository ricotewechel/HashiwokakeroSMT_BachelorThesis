import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.SolverException;

public class Main {
    public static void main(String[] args) throws InvalidConfigurationException, SolverException {
        GraphSolver graphSolver = new GraphSolver(args);
        GridSolver gridSolver = new GridSolver(args);

        // Game 0
        Game game0A = new Game("improved", "3x3m2:2a2c2a2");
        graphSolver.solveGame(game0A);
        System.out.println(game0A);
        Game game0B = new Game("naive", "3x3m2:2a2c2a2");
        gridSolver.solveGame(game0B);
        System.out.println(game0B);

        // Game 1
        Game game1A = new Game("improved", "7x7m2:4d2b1b4a26b3m1c3c3c2d3");
        graphSolver.solveGame(game1A);
        System.out.println(game1A);
        Game game1B = new Game("naive", "7x7m2:4d2b1b4a26b3m1c3c3c2d3");
        gridSolver.solveGame(game1B);
        System.out.println(game1B);

        // Game 2
        Game game2A = new Game("improved", "7x7m2:a4d32a4b3b3g4a2c2c3g13d2a");
        graphSolver.solveGame(game2A);
        System.out.println(game2A);
        Game game2B = new Game("naive", "7x7m2:a4d32a4b3b3g4a2c2c3g13d2a");
        gridSolver.solveGame(game2B);
        System.out.println(game2B);

        // Game 3
        Game game3A = new Game("improved", "15x15m2:2d4c4b5a4g1e2b2a4a6e2j2f4h2g1y2c2a5g4a3s4t3a3a2d2l1c3b3c2o1a2f4d4a3");
        graphSolver.solveGame(game3A);
        System.out.println(game3A);
        Game game3B = new Game("naive", "15x15m2:2d4c4b5a4g1e2b2a4a6e2j2f4h2g1y2c2a5g4a3s4t3a3a2d2l1c3b3c2o1a2f4d4a3");
        gridSolver.solveGame(game3B);
        System.out.println(game3B);

        // Game 4
        Game game4A = new Game("improved", "15x15m2:3h4i1a3a2b3b2b2d2e2l2e6a5a2b4c3t2a1b2b1d4d5f34i3b2p6m4a1b1c5c4r2f5b2c4k6a3");
        graphSolver.solveGame(game4A);
        System.out.println(game4A);
        Game game4B = new Game("naive", "15x15m2:3h4i1a3a2b3b2b2d2e2l2e6a5a2b4c3t2a1b2b1d4d5f34i3b2p6m4a1b1c5c4r2f5b2c4k6a3");
        gridSolver.solveGame(game4B);
        System.out.println(game4B);

        // Game 5
        Game game5A = new Game("improved", "15x15m2:4c5b5a5d3j3a3g2s2d5b3a4a7a6b4c2f2l2b1b1c3c1o3a6b3b3c2e3c2i4g5b5a2b3d5b1e3c1c2c1a5f42a2a4g2b");
        graphSolver.solveGame(game5A);
        System.out.println(game5A);
        Game game5B = new Game("naive", "15x15m2:4c5b5a5d3j3a3g2s2d5b3a4a7a6b4c2f2l2b1b1c3c1o3a6b3b3c2e3c2i4g5b5a2b3d5b1e3c1c2c1a5f42a2a4g2b");
        gridSolver.solveGame(game5B);
        System.out.println(game5B);

        // Game 6
        Game game6A = new Game("improved", "30x30m2:a4z4c2a4r5a4a2b2zi4o2e3a4ze3zm2zzzzzd2zb1zd1zzs1b5e4m4v2zj2zb6b7a5k4zp6a3a2b4g1za2m3b2a1a3a2v4c5c5e5a1n1z2f1b3i4a3a2g3o1h1e2b2n1c4b4b14b3d3a1e2a2k");
        graphSolver.solveGame(game6A);
        System.out.println(game6A);
        Game game6B = new Game("naive", "30x30m2:a4z4c2a4r5a4a2b2zi4o2e3a4ze3zm2zzzzzd2zb1zd1zzs1b5e4m4v2zj2zb6b7a5k4zp6a3a2b4g1za2m3b2a1a3a2v4c5c5e5a1n1z2f1b3i4a3a2g3o1h1e2b2n1c4b4b14b3d3a1e2a2k");
        gridSolver.solveGame(game6B);
        System.out.println(game6B);

        // Game 7
        Game game7A = new Game("improved", "30x30m2:3r4a3d2a1b4d3a1a1e5a2c1a4d3ze5n4b2e3b3za2d3g5i3zo2c3b2b2zk1j5h6a4t1k1d1zy4b4d3i2i2v4h8a3k1c2zf1e2b4a1g1g44w5a5h2o3za1ze2d5a4zd3z3c2r6a5a2h1a4m2k3b2f2e4b6c2g3b4d4n4ze3a4b4b3b1a5c3h1g2w5d3");
        graphSolver.solveGame(game7A);
        System.out.println(game7A);
        Game game7B = new Game("naive", "30x30m2:3r4a3d2a1b4d3a1a1e5a2c1a4d3ze5n4b2e3b3za2d3g5i3zo2c3b2b2zk1j5h6a4t1k1d1zy4b4d3i2i2v4h8a3k1c2zf1e2b4a1g1g44w5a5h2o3za1ze2d5a4zd3z3c2r6a5a2h1a4m2k3b2f2e4b6c2g3b4d4n4ze3a4b4b3b1a5c3h1g2w5d3");
        gridSolver.solveGame(game7B);
        System.out.println(game7B);



//        Sudoku.solve(args);
    }
}