import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        System.out.println("Hello World!");
        File file = new File("mock.txt");
        System.out.println(file.exists());
        System.out.println(new File(".").getAbsoluteFile());
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        String line;
        while (true){
            try {
                line = bufferedReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
            if (line == null){
                break;
            }
            System.out.println(Tokeniser.parseInput(line));
        }
        try {
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ArrayList<Integer> ts = new ArrayList<>();
        ts.add(12345);
        ts.add(12346);
        ts.add(12347);
        ts.add(12348);

        ts.forEach(n -> {
            ts.stream().filter(k -> !k.equals(n)).forEach(p -> System.out.println("Sending " + p + " to " + n));
        });

        ArrayList<Integer> is = new ArrayList<>();
        is.add(2);
        is.add(2);
        is.add(2);
        is.add(2);

        outcome(is);
        is.add(3);
        outcome(is);

        /*List<> data = ts.stream().map(n -> {
            ts.stream().filter(k -> !k.equals(n)).collect(Collectors.toMap(p -> n, Function.identity()));
        }).collect(Collectors.toList());*/
    }

    public static void outcome(List<Integer> ns){
        List<Integer> filtered = ns.stream().filter(n -> !ns.get(0).equals(n)).collect(Collectors.toList());
        System.out.println(filtered);
    }

}
