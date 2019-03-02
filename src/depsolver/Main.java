package depsolver;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

class Package {
    private String name;
    private String version;
    private Integer size;
    private List<List<String>> depends = new ArrayList<>();
    private List<String> conflicts = new ArrayList<>();

    public String getName() { return name; }
    public String getVersion() { return version; }
    public Integer getSize() { return size; }
    public List<List<String>> getDepends() { return depends; }
    public List<String> getConflicts() { return conflicts; }
    public void setName(String name) { this.name = name; }
    public void setVersion(String version) { this.version = version; }
    public void setSize(Integer size) { this.size = size; }
    public void setDepends(List<List<String>> depends) { this.depends = depends; }
    public void setConflicts(List<String> conflicts) { this.conflicts = conflicts; }

    @Override
    public String toString() {
        return "[" + name + "=" + version + "]";
    }
}

public class Main {

    private static List<Package> repo;
    private static List<String> initial;
    private static List<String> constraints;
    private static HashMap<String, Package> repoMap;

    public static void main(String[] args) throws IOException {
        TypeReference<List<Package>> repoType = new TypeReference<List<Package>>() {};
        repo = JSON.parseObject(readFile(args[0]), repoType);
        TypeReference<List<String>> strListType = new TypeReference<List<String>>() {};
        initial = JSON.parseObject(readFile(args[1]), strListType);
        constraints = JSON.parseObject(readFile(args[2]), strListType);

        repoMap = new HashMap<>();

//        printRepo();

        new Main().start();
    }

    static String readFile(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        StringBuilder sb = new StringBuilder();
        br.lines().forEach(sb::append);
        return sb.toString();
    }

    private static void printRepo() {
        for (Package p : repo) {
            System.out.printf("package %s version %s\n", p.getName(), p.getVersion());
            for (List<String> clause : p.getDepends()) {
                System.out.print("  dep:");
                for (String q : clause) {
                    System.out.printf(" %s", q);
                }
                System.out.print("\n");
            }
            if (p.getConflicts().size() != 0) System.out.println("  conflict: " + p.getConflicts());
        }
    }

    public void addPackage(Package pack) {
        repoMap.put(pack.getName(), pack);
    }

    public void start() {
        List<List<String>> packagesToInstall = new ArrayList<>();
//        for (String str : initial) {
//            String[] split = str.split("=");
//
//        }
        for (String str : constraints) {
            System.out.println(str);
            if (str.startsWith("+")) {
                System.out.println("HERE");
                System.out.println(findMatchingPackages(str.substring(1)));
            }
        }
        System.out.println(packagesToInstall);
    }

    private void installPackage(Package pack) {
        installPackage(pack, new ArrayList<>());
    }

    private void installPackage(Package pack, ArrayList<String> path) {
        for (List<String> list : pack.getDepends()) {
            for (String str : list) {
                // Find every package that corresponds to the string
                for (Package p : findMatchingPackages(str));
            }
        }
//        path.add("+" + nodePackage.name);
    }

    private List<Package> findMatchingPackages(String str) {
        List<Package> matchingPackages = new ArrayList<>();
        if (str.contains(">=")) {
            String[] split = str.split(">=");
            for (Package pack : repo) {
                if (pack.getName().equals(split[0]) && compareVersions(pack.getVersion(), ">=", split[1])) {
                    matchingPackages.add(pack);
                }
            }
        } else if (str.contains("<=")) {
            String[] split = str.split("<=");
            for (Package pack : repo) {
                if (pack.getName().equals(split[0]) && compareVersions(pack.getVersion(), "<=", split[1])) {
                    matchingPackages.add(pack);
                }
            }
        } else if (str.contains("=")) {
            String[] split = str.split("=");
            for (Package pack : repo) {
                if (pack.getName().equals(split[0]) && pack.getVersion().equals(split[1])) {
                    matchingPackages.add(pack);
                    break;
                }
            }
        } else if (str.contains(">")) {
            String[] split = str.split(">");
            for (Package pack : repo) {
                if (pack.getName().equals(split[0]) && compareVersions(pack.getVersion(), ">", split[1])) {
                    matchingPackages.add(pack);
                }
            }
        } else if (str.contains("<")) {
            String[] split = str.split("<");
            for (Package pack : repo) {
                if (pack.getName().equals(split[0]) && compareVersions(pack.getVersion(), "<", split[1])) {
                    matchingPackages.add(pack);
                }
            }
        }
        return matchingPackages;
    }

    private static boolean compareVersions(String pack1VerStr, String comparator, String pack2VerStr) {
        String res = compareVersions(pack1VerStr, pack2VerStr);
        System.out.println(res);
        switch (comparator) {
            case ">=": return (res.equals(">") || res.equals("="));
            case "<=": return (res.equals("<") || res.equals("="));
            case ">": return (res.equals(">"));
            case "<": return (res.equals("<"));
            case "=": return (res.equals("="));
        }
        return false;
    }

    private static String compareVersions(String pack1VerStr, String pack2VerStr) {
        // Convert version string to list of numbers
        List<Integer> pack1VerList = Arrays.stream(pack1VerStr.split("\\.")).map(Integer::parseInt).collect(Collectors.toList());
        List<Integer> pack2VerList = Arrays.stream(pack2VerStr.split("\\.")).map(Integer::parseInt).collect(Collectors.toList());
        // Make the two lists the same length
        while (pack1VerList.size() != pack2VerList.size()) {
            if (pack1VerList.size() < pack2VerList.size()) pack1VerList.add(0);
            else pack2VerList.add(0);
        }
        // Compare each number 1 by 1
        int i = 0;
        while (i < pack1VerList.size()) {
            if (pack1VerList.get(i) > pack2VerList.get(i)) return ">";
            else if (pack1VerList.get(i) < pack2VerList.get(i)) return "<";
            else if (pack1VerList.get(i).equals(pack2VerList.get(i))) i++;
        }
        return "=";
    }
}

class NodePackage {

    String name;
    String version;
    List<List<NodePackage>> prerequisit;

    NodePackage(String name, String version) {
        this.name = name;
        this.version = version;
    }
}
