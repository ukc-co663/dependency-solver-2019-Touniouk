package depsolver;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.io.*;
import java.util.*;
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
        return "(" + name + "=" + version + ")";
    }
}

public class Main {

    private List<String> initial;
    private List<String> constraints;
    private HashMap<String, HashMap<String, Pack>> repoMap;
    private Stack<Pack> packagesToInstall;
    private Stack<Pack> installedPackages;
    private Pack latestPack;
    private final int uninstallCost = 1000000;

    /*
    Accessor function
     */
    public static void main(String[] args) throws IOException {
        Main main = new Main();
        List<Package> repo = main.createRepo(args);
        main.makeHashMapRepo(repo);
        main.start();
    }

    /*
    Creates all the Packages and puts them in repo
    Also parses the other files
     */
    private List<Package> createRepo(String[] args) throws IOException {
        TypeReference<List<Package>> repoType = new TypeReference<List<Package>>() {};
        List<Package> repo = JSON.parseObject(readFile(args[0]), repoType);
        TypeReference<List<String>> strListType = new TypeReference<List<String>>() {};
        initial = JSON.parseObject(readFile(args[1]), strListType);
        constraints = JSON.parseObject(readFile(args[2]), strListType);
        return repo;
    }

    /*
    Makes the repo into a HashMap format where we can easily look up packages
     */
    private void makeHashMapRepo(List<Package> repo) {
        repoMap = new HashMap<>();
        for (Package pack : repo) {
            // Check if this pack exists (any version)
            repoMap.computeIfAbsent(pack.getName(), k -> new HashMap<>());
            // Check if the specific version exists
            repoMap.get(pack.getName()).computeIfAbsent(pack.getVersion(), k -> new Pack(pack.getName(), pack.getVersion(), pack.getSize()));
        }
        // Add the dependencies and conflicts
        for (Package pack : repo) {
            // Add dependencies
            List<List<Pack>> prerequisite = new ArrayList<>();
            for (List<String> list : pack.getDepends()) {
                List<Pack> deps = new ArrayList<>();
                list.forEach(str -> deps.addAll(new ArrayList<>(findMatchingPackagesInMap(str))));
                prerequisite.add(deps);
            }
            repoMap.get(pack.getName()).get(pack.getVersion()).prerequisite = makeIntoOrList(prerequisite);
        }
//        System.out.println(repoMap);
    }

    /*
    Transform dependencies from [[A or B] and [C or D]] to [[A and C] or [A and D] or [B and C] or [B and D]]
     */
    private List<Dependency> makeIntoOrList(List<List<Pack>> prerequisite) {
        List<Dependency> updatedPrerequisite = new ArrayList<>();

        int currentSize;
        for (List<Pack> andList : prerequisite) {
            if (updatedPrerequisite.size() == 0) {
                for (Pack pack : andList) updatedPrerequisite.add(new Dependency(pack));
            } else {
                currentSize = updatedPrerequisite.size();
                for (int i = 0; i < andList.size() - 1; i++) {
                    for (int j = 0; j < currentSize; j++) {
                        updatedPrerequisite.add(updatedPrerequisite.get(j).cloneDep());
                    }
                }
                for (int i = 0; i < updatedPrerequisite.size(); i++) updatedPrerequisite.get(i).add(andList.get((i / currentSize)));
            }
        }
        return updatedPrerequisite;
    }

    /*
    Given an expression such as "A>=3", finds all the matching packages in the repoMap
     */
    private List<Pack> findMatchingPackagesInMap(String str) {
        try {
            if (str.contains(">=")) {
                String[] split = str.split(">=");
                return repoMap.get(split[0]).values().stream().filter(p -> compareVersions(p.version, ">=", split[1])).collect(Collectors.toList());
            } else if (str.contains("<=")) {
                String[] split = str.split("<=");
                return repoMap.get(split[0]).values().stream().filter(p -> compareVersions(p.version, "<=", split[1])).collect(Collectors.toList());
            } else if (str.contains("=")) {
                String[] split = str.split("=");
                List<Pack> lk = new ArrayList<>();
                lk.add(repoMap.get(split[0]).get(split[1]));
                return lk;
            } else if (str.contains(">")) {
                String[] split = str.split(">");
                return repoMap.get(split[0]).values().stream().filter(p -> compareVersions(p.version, ">", split[1])).collect(Collectors.toList());
            } else if (str.contains("<")) {
                String[] split = str.split("<");
                return repoMap.get(split[0]).values().stream().filter(p -> compareVersions(p.version, "<", split[1])).collect(Collectors.toList());
            } else return new ArrayList<>(repoMap.get(str).values());
        } catch (NullPointerException ex) {
            // This is here cause sometimes packages depend on packages that don't exist
            return new ArrayList<>();
        }
    }

    /*
    Used to create the repo, reads the entire file
     */
    private String readFile(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        StringBuilder sb = new StringBuilder();
        br.lines().forEach(sb::append);
        return sb.toString();
    }

    /*
    Start
     */
    public void start() {
        packagesToInstall = new Stack<>();
        installedPackages = new Stack<>();
        Solution bestSolution = null;

        // Make the constraints into a list of dependencies
        List<List<Pack>> constraintsList = new ArrayList<>();
        constraints.stream()
                .filter(c -> c.startsWith("+"))
                .forEach(c -> constraintsList.add(new ArrayList<>(findMatchingPackagesInMap(c.substring(1)))));
        List<Dependency> constraintsDependency = makeIntoOrList(constraintsList);

        // Try to find a solution for every constraint combination and keep the best
        for (int i = 0; i < constraintsDependency.size(); i++) {
            Dependency dep = constraintsDependency.get(i);
            packagesToInstall.addAll(dep.packList);
            latestPack = packagesToInstall.peek();
            while (packagesToInstall.size() != 0) {
                checkInstall(packagesToInstall.peek(), i);
            }
            // Check if this solution is valid and has a better cost than the previous one
            if (installedPackages.containsAll(dep.packList)) {
                Solution sol = getSolutionFromPackageList();
                if (bestSolution == null || sol.cost < bestSolution.cost) bestSolution = sol;
            }
            // Clear installedPackages for other solutions
            installedPackages.forEach(p -> p.isInstalled = false);
            installedPackages.clear();
        }

        assert bestSolution != null;
        System.out.println(bestSolution.jsonSolution);
    }

    /*
    Tries to install the package at the top of the stack of packages to install
     */
    private void checkInstall(Pack pack, int currentIteration) {
        // Check if it's installed already, if it is, remove it from the stack
        if (pack.isInstalled) {
            packagesToInstall.pop();
            return;
        }
        // Check if it conflicts with anything already installed
        if (pack.conflicts.stream().anyMatch(p -> p.isInstalled)) {
            cantInstall(pack);
            return;
        }
        // Check if anything already installed conflicts with it
        if (installedPackages.stream().anyMatch(installedPack -> installedPack.conflicts.stream().anyMatch(p -> p == pack))) {
            cantInstall(pack);
            return;
        }
        // Check if this package has any uninstalled dependencies, if it doesn't, install it
        if (pack.prerequisite.size() == 0 || pack.prerequisite.stream().anyMatch(Dependency::checkIfSatisfied)) {
            installPackage(packagesToInstall.pop());
            return;
        }
        // if it does, add the first non-tried one to the stack
        latestPack = pack;
        Optional<Dependency> untriedDep = pack.prerequisite.stream().filter(d -> d.hasBeenTried != currentIteration).findFirst();
        if (untriedDep.isPresent()) {
            // Check for circular dependency (we need to install a package that's already in the stack)
            if (Collections.disjoint(packagesToInstall, untriedDep.get().packList)) packagesToInstall.addAll(untriedDep.get().packList);
            untriedDep.get().hasBeenTried = currentIteration;
        } else {
            // We've tried every dependency for this package
            packagesToInstall.pop();
        }
    }

    private void installPackage(Pack pack) {
        latestPack.packagesInstalledForIt.add(pack);
        installedPackages.add(pack);
        pack.isInstalled = true;
    }

    private void cantInstall(Pack pack) {
        latestPack.packagesInstalledForIt.forEach(this::unInstall);
        latestPack.packagesInstalledForIt.clear();
        // Since the dependency failed, pop all the other packages in this dependency
        while (packagesToInstall.peek() != latestPack) packagesToInstall.pop();
    }

    private void unInstall(Pack pack) {
        installedPackages.remove(pack);
        pack.isInstalled = false;
    }

    /*
    Creates the json String from the list of packages
    Takes into account the initial state
    calculates the cost
     */
    private Solution getSolutionFromPackageList() {
        // Building the result json string
        long cost = 0L;
        StringBuilder resultString = new StringBuilder("[\n");
        // Go through the initial state and see what we can't have
        for (String str : initial) {
            // Get the corresponding package
            String[] split = str.split("=");
            Pack pack = repoMap.get(split[0]).get(split[1]);
            // Check if there's any conflict with installed packages
            if (
                // Check if it conflicts with anything already installed
                pack.conflicts.stream().anyMatch(p -> p.isInstalled) ||
                // Check if anything already installed conflicts with it
                installedPackages.stream().anyMatch(installedPack -> installedPack.conflicts.stream().anyMatch(p -> p == pack))
            ) {
                // Uninstall the package before anything else
                resultString.append("\"-").append(str).append("\",\n");
                cost += uninstallCost;
            }
            // Check if we need to install it
            installedPackages.remove(pack);
        }
        // Add the packages to install
        for (Pack p : installedPackages) {
            resultString.append("\"+").append(p.name).append("=").append(p.version).append("\",\n");
            cost += p.size;
        }
        return new Solution(cost, resultString.delete(resultString.length()-2, resultString.length()-1).append("]").toString());
    }

    private boolean compareVersions(String pack1VerStr, String comparator, String pack2VerStr) {
        String res = compareVersions(pack1VerStr, pack2VerStr);
        switch (comparator) {
            case ">=": return (res.equals(">") || res.equals("="));
            case "<=": return (res.equals("<") || res.equals("="));
            case ">": return (res.equals(">"));
            case "<": return (res.equals("<"));
            case "=": return (res.equals("="));
        }
        return false;
    }

    private String compareVersions(String pack1VerStr, String pack2VerStr) {
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

    /*
    A Package class that links directly to the other packages
     */
    private class Pack {

        String name;
        String version;
        long size;
        List<Dependency> prerequisite;
        List<Pack> conflicts;
        List<Pack> packagesInstalledForIt; // List of packages installed in order to install this one
        boolean isInstalled = false; // Used to check conflicts

        private Pack(String name, String version, int size) {
            this.name = name;
            this.version = version;
            this.size = size;
            prerequisite = new ArrayList<>();
            conflicts = new ArrayList<>();
            packagesInstalledForIt = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "(" + name + "=" + version + ")";
        }
    }

    /*
    List of Packs that need to be installed
    A pack has a list of dependencies, at least one of which should be satisfied
    for a dependency to be satisfied, every package in it should be installed
     */
    private class Dependency {
        private List<Pack> packList;
        int hasBeenTried = -1; // Has been tried is a number so that I don't have to recursively reset it after trying one set of constraints

        private Dependency(Pack... packs) {
            packList = new ArrayList<>(Arrays.asList(packs));
        }

        private Dependency cloneDep() {
            Dependency dep = new Dependency();
            dep.packList = new ArrayList<>(packList);
            dep.hasBeenTried = hasBeenTried;
            return dep;
        }

        private boolean checkIfSatisfied() {
            return packList.stream().allMatch(p -> p.isInstalled);
        }

        private void add(Pack pack) {
            packList.add(pack);
        }
    }

    /*
    Just a way to keep the solution and its cost associated
     */
    private class Solution {
        long cost;
        String jsonSolution;

        private Solution(long cost, String jsonSolution) {
            this.cost = cost;
            this.jsonSolution = jsonSolution;
        }
    }
}

