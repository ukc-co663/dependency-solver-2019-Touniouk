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
        return "[" + name + "=" + version + "]";
    }
}

public class Main {

    private List<Package> repo;
    private List<String> initial;
    private List<String> constraints;
    private HashMap<String, HashMap<String, Pack>> repoMap;
    private Stack<Pack> packagesToInstall;
    private Stack<Pack> installedPackages;
    private Pack latestPack;

    /*
    Accessor function
     */
    public static void main(String[] args) throws IOException {
        Main main = new Main();
        main.createRepo(args);
        main.makeHashMapRepo();
        main.start();
    }

    /*
    Creates all the Packages and puts them in repo
     */
    private void createRepo(String[] args) throws IOException {
        TypeReference<List<Package>> repoType = new TypeReference<List<Package>>() {};
        repo = JSON.parseObject(readFile(args[0]), repoType);
        TypeReference<List<String>> strListType = new TypeReference<List<String>>() {};
        initial = JSON.parseObject(readFile(args[1]), strListType);
        constraints = JSON.parseObject(readFile(args[2]), strListType);
    }

    /*
    Makes the repo into a HashMap format where we can easily look up packages
     */
    private void makeHashMapRepo() {
        repoMap = new HashMap<>();
        for (Package pack : repo) {
            // Check if this pack exists (any version)
            repoMap.computeIfAbsent(pack.getName(), k -> new HashMap<>());
            // Check if the specific version exists
            repoMap.get(pack.getName()).computeIfAbsent(pack.getVersion(), k -> new Pack(pack.getName(), pack.getVersion()));
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
            repoMap.get(pack.getName()).get(pack.getVersion()).prerequisite = makeIntoOrList(repoMap.get(pack.getName()).get(pack.getVersion()), prerequisite);
            // Add conflicts
            for (String str : pack.getConflicts()) {
                repoMap.get(pack.getName()).get(pack.getVersion()).conflicts.addAll(new ArrayList<>(findMatchingPackagesInMap(str)));
            }
        }
        System.out.println(repoMap);
    }

    /*
    Transform dependencies from [[A or B] and [C or D]] to [[A and C] or [A and D] or [B and C] or [B and D]]
     */
    private List<Dependency> makeIntoOrList(Pack parentPack, List<List<Pack>> prerequisite) {
        List<Dependency> updatedPrerequisite = new ArrayList<>();

        int currentSize;
        for (List<Pack> andList : prerequisite) {
            if (updatedPrerequisite.size() == 0) {
                for (Pack pack : andList) updatedPrerequisite.add(new Dependency(parentPack, pack));
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
    }

    /*
    Given an expression such as "A>=3", finds all the matching packages in the repo
    Does involve going through the entire repo every time tho
     */
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
        } else {
            for (Package pack : repo) {
                if (pack.getName().equals(str)) {
                    matchingPackages.add(pack);
                }
            }
        }
        return matchingPackages;
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

        // TODO: Consider the initial state

        for (String str : constraints) {
            if (str.startsWith("+")) {
                for (Package pack : findMatchingPackages(str.substring(1))) {
                    packagesToInstall.add(repoMap.get(pack.getName()).get(pack.getVersion()));
                }
            }
        }
        System.out.println("HERE");
        // Depth first search (probably)
        latestPack = packagesToInstall.peek();
        while (packagesToInstall.size() != 0) {
            checkInstall(packagesToInstall.peek());
        }

        System.out.println(installedPackages);
    }

    private void checkInstall(Pack pack) {
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
        if (pack.prerequisite.size() == 0 || pack.prerequisite.stream().anyMatch(Dependency::checkIfSatisfied)) installPackage(packagesToInstall.pop());
        // if it does, add the first non-tried one to the stack
        else {
            latestPack = pack;
            pack.prerequisite.stream().filter(d -> !d.hasBeenTried).findFirst().ifPresent(d -> {
                packagesToInstall.addAll(d.packList);
                d.hasBeenTried = true;
            });
        }
    }

    private void installPackage(Pack pack) {
        latestPack.packagesInstalledForIt.add(pack);
        installedPackages.add(pack);
        pack.hasBeenInstalled(true);
    }

    private void cantInstall(Pack pack) {
        latestPack.packagesInstalledForIt.forEach(this::unInstall);
        latestPack.packagesInstalledForIt.clear();
        // Since the dependency failed op all the other packages in this dependency
        while (packagesToInstall.peek() != latestPack) packagesToInstall.pop();
    }

    private void unInstall(Pack pack) {
        installedPackages.remove(pack);
        pack.hasBeenInstalled(false);
    }

//    private List<Package> findMatchingPackages(String str) {
//        List<Package> matchingPackages = new ArrayList<>();
//
//        if (str.contains(">=")) {
//            String[] split = str.split(">=");
//            for (Package pack : repo) {
//                if (pack.getName().equals(split[0]) && compareVersions(pack.getVersion(), ">=", split[1])) {
//                    matchingPackages.add(pack);
//                }
//            }
//        } else if (str.contains("<=")) {
//            String[] split = str.split("<=");
//            for (Package pack : repo) {
//                if (pack.getName().equals(split[0]) && compareVersions(pack.getVersion(), "<=", split[1])) {
//                    matchingPackages.add(pack);
//                }
//            }
//        } else if (str.contains("=")) {
//            String[] split = str.split("=");
//            for (Package pack : repo) {
//                if (pack.getName().equals(split[0]) && pack.getVersion().equals(split[1])) {
//                    matchingPackages.add(pack);
//                    break;
//                }
//            }
//        } else if (str.contains(">")) {
//            String[] split = str.split(">");
//            for (Package pack : repo) {
//                if (pack.getName().equals(split[0]) && compareVersions(pack.getVersion(), ">", split[1])) {
//                    matchingPackages.add(pack);
//                }
//            }
//        } else if (str.contains("<")) {
//            String[] split = str.split("<");
//            for (Package pack : repo) {
//                if (pack.getName().equals(split[0]) && compareVersions(pack.getVersion(), "<", split[1])) {
//                    matchingPackages.add(pack);
//                }
//            }
//        } else {
//            for (Package pack : repo) {
//                if (pack.getName().equals(str)) {
//                    matchingPackages.add(pack);
//                }
//            }
//        }
//        return matchingPackages;
//    }

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

    private class Pack {

        String name;
        String version;
        List<Dependency> prerequisite;
        List<Pack> conflicts;
//        List<Dependency> isInDependency; // Dependency that has this package in it, when this package is installed, the dep is satisfied
        List<Pack> packagesInstalledForIt; // List of packages installed in order to install this one
        boolean hasBeenTried;
        boolean isInstalled; // Used to check conflicts
        boolean hasToBeInstalled; // Absolutely has to be installed, no other way around it

        private Pack(String name, String version) {
            this.name = name;
            this.version = version;
            prerequisite = new ArrayList<>();
            conflicts = new ArrayList<>();
            packagesInstalledForIt = new ArrayList<>();
//            isInDependency = new ArrayList<>();
            hasBeenTried = false;
            isInstalled = false;
            hasToBeInstalled = false;
        }

        @Override
        public String toString() {
            return "[" + name + "=" + version + "]";
        }

        private void hasBeenInstalled(boolean flag) {
            isInstalled = flag;
//            isInDependency.forEach(d -> d.isSatisfied = true);
        }
    }

    /*
    List of Packs that need to be installed
    A pack has a list of dependencies, at least one of which should be satisfied
    for a dependency to be satisfied, every package in it should be installed
     */
    private class Dependency {
        private List<Pack> packList;
        boolean hasBeenTried; // keeping track of whether or not we tried this dependency already
//        boolean isSatisfied; // If one of the packages in the packList has been installed
        Pack belongsTo;

        private Dependency(Pack parentPack, Pack... packs) {
            belongsTo = parentPack;
            hasBeenTried = false;
//            for (Pack pack : packs) pack.isInDependency.add(this);
            packList = new ArrayList<>(Arrays.asList(packs));
        }

        private Dependency cloneDep() {
            Dependency dep = new Dependency(belongsTo);
            dep.packList = new ArrayList<>(packList);
            dep.hasBeenTried = hasBeenTried;
//            dep.isSatisfied = isSatisfied;
            return dep;
        }

        private boolean checkIfSatisfied() {
            return packList.stream().allMatch(p -> p.isInstalled);
        }

        private void add(Pack pack) {
            packList.add(pack);
        }
    }
}

