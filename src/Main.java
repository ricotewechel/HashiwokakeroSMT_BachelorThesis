import org.checkerframework.checker.units.qual.A;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.SolverException;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws InvalidConfigurationException, SolverException {
        GraphSolver graphSolver = new GraphSolver(args);
        GridSolver gridSolver = new GridSolver(args);

        ArrayList<String> puzzles = new ArrayList<>(){
                {
                        add("3x3m2:2a2c2a2");
                        add("7x7m2:4d2b1b4a26b3m1c3c3c2d3");
                        add("7x7m2:a4d32a4b3b3g4a2c2c3g13d2a");
                        add("15x15m2:2d4c4b5a4g1e2b2a4a6e2j2f4h2g1y2c2a5g4a3s4t3a3a2d2l1c3b3c2o1a2f4d4a3");
                        add("15x15m2:3h4i1a3a2b3b2b2d2e2l2e6a5a2b4c3t2a1b2b1d4d5f34i3b2p6m4a1b1c5c4r2f5b2c4k6a3");
                        add("15x15m2:4c5b5a5d3j3a3g2s2d5b3a4a7a6b4c2f2l2b1b1c3c1o3a6b3b3c2e3c2i4g5b5a2b3d5b1e3c1c2c1a5f42a2a4g2b");
                        add("30x30m2:a4z4c2a4r5a4a2b2zi4o2e3a4ze3zm2zzzzzd2zb1zd1zzs1b5e4m4v2zj2zb6b7a5k4zp6a3a2b4g1za2m3b2a1a3a2v4c5c5e5a1n1z2f1b3i4a3a2g3o1h1e2b2n1c4b4b14b3d3a1e2a2k");
                        add("30x30m2:3r4a3d2a1b4d3a1a1e5a2c1a4d3ze5n4b2e3b3za2d3g5i3zo2c3b2b2zk1j5h6a4t1k1d1zy4b4d3i2i2v4h8a3k1c2zf1e2b4a1g1g44w5a5h2o3za1ze2d5a4zd3z3c2r6a5a2h1a4m2k3b2f2e4b6c2g3b4d4n4ze3a4b4b3b1a5c3h1g2w5d3");
                        add("30x30m2:3a2d3a3a3h4g3b3a4a6r4b3a2f1p3a2g3a6h3d3a1i1zg2b1p3e3a6f1r3ze2zf3r5i4u2a4c3b4e4c2n3zg6s6g3zn4e6d4zs2b6e6c2h3a4t4b3x1b1g2n1zj2a2e5r5a6c2e1g4e4d3k4a4c2n3b42n2b4a1b3k4f7k4a6a4d4e4a1a2b3d4c2z1c2b1a2a3b2b5b6h4f3a");
                }
        };

//        ArrayList<String> puzzles = new ArrayList<>();
//        try {
//            File file = new File(args[0]);
//            Scanner scanner = new Scanner(file);
//            while (scanner.hasNextLine()) {
//                puzzles.add(scanner.nextLine());
//            }
//            scanner.close();
//        } catch (FileNotFoundException e) {
//            System.out.println("An error occurred.");
//            e.printStackTrace();
//        }

        for (String s : puzzles) {
            System.out.println("Trying to solve puzzle with graph encoding...");
            Game a = new Game(s);
            graphSolver.solveGame(a);
            System.out.println(a);

            System.out.println("Trying to solve puzzle with grid encoding...");
            Game b = new Game(s);
            gridSolver.solveGame(b);
            System.out.println(b);
        }


//        Sudoku.solve(args);
    }
}