import org.sosy_lab.common.configuration.InvalidConfigurationException;

public class Main {
    public static void main(String[] args) throws InvalidConfigurationException {
        Solver solver = new Solver(args);

        Game game0 = new Game(3, "2a2c2a2");
        Game game1 = new Game(7, "4d2b1b4a26b3m1c3c3c2d3");
        Game game2 = new Game(7, "a4d32a4b3b3g4a2c2c3g13d2a");
        Game game3 = new Game(15, "2d4c4b5a4g1e2b2a4a6e2j2f4h2g1y2c2a5g4a3s4t3a3a2d2l1c3b3c2o1a2f4d4a3");
        Game game4 = new Game(15, "3h4i1a3a2b3b2b2d2e2l2e6a5a2b4c3t2a1b2b1d4d5f34i3b2p6m4a1b1c5c4r2f5b2c4k6a3");
        Game game5 = new Game(15, "4c5b5a5d3j3a3g2s2d5b3a4a7a6b4c2f2l2b1b1c3c1o3a6b3b3c2e3c2i4g5b5a2b3d5b1e3c1c2c1a5f42a2a4g2b");
//        Game game6 = new Game(30, "a4z4c2a4r5a4a2b2zi4o2e3a4ze3zm2zzzzzd2zb1zd1zzs1b5e4m4v2zj2zb6b7a5k4zp6a3a2b4g1za2m3b2a1a3a2v4c5c5e5a1n1z2f1b3i4a3a2g3o1h1e2b2n1c4b4b14b3d3a1e2a2k");
//        Game game7 = new Game(30, "3r4a3d2a1b4d3a1a1e5a2c1a4d3ze5n4b2e3b3za2d3g5i3zo2c3b2b2zk1j5h6a4t1k1d1zy4b4d3i2i2v4h8a3k1c2zf1e2b4a1g1g44w5a5h2o3za1ze2d5a4zd3z3c2r6a5a2h1a4m2k3b2f2e4b6c2g3b4d4n4ze3a4b4b3b1a5c3h1g2w5d3");

        solver.solveGame(game0);
        solver.solveGame(game1);
        solver.solveGame(game2);
        solver.solveGame(game3);
        solver.solveGame(game4);
        solver.solveGame(game5);
//        solver.solveGame(game6);
//        solver.solveGame(game7);

        System.out.println(game0);
        System.out.println(game1);
        System.out.println(game2);
        System.out.println(game3);
        System.out.println(game4);
        System.out.println(game5);
//        System.out.println(game6);
//        System.out.println(game7);


//        Sudoku.solve(args);
    }
}