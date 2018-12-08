package controller;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.graphstream.algorithm.*;
import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;

/**
 * Created by Hieu Nguyen on 3/11/2018.
 */
public class NetworkGraph {
    Graph graph;
    Dijkstra dijkstra;
    protected String sheetstyle =
            "node {\n" +
                    "\tsize: 15px;\n" +
                    "\tfill-color: black;\n" +
                    "\tstroke-mode: plain;\n" +
                    "\tstroke-color: yellow;\n" +
                    "\tshadow-mode: gradient-vertical;\n" +
                    "\tshadow-offset: 2;\n" +
                    "}\n" +
                    "edge {" +
                    "size: 2px;" +
                    "}" +
                    "\n" +
                    "node#0.1 {\n" +
                    "\tfill-color: #3333cc;\n" +
                    "}\n" +
                    "\n" +
                    "node:clicked {\n" +
                    "\tfill-color: red;\n" +
                    "}" ;

    public NetworkGraph() {
        graph = new SingleGraph("SDN WISE Network");
        graph.setStrict(false);
        graph.setAutoCreate(true);
        graph.addAttribute("ui.stylesheet", sheetstyle );
        graph.display();
        dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, "length");
    }
    public void updateGraph(String inComingData) {
        // 1 16 0.1 0.2 2 100 0.1 1 93 1 0 1 75
        // 1 19 0.1 0.2 2 100 0.1 1 51 2 0 1 75 0 3 78
        // 1 19 0.1 0.3 2 100 0.1 2 44 3 0 2 66 0 4 99 0 6 99
        // [RX ]: 1 69 0.1 0.16 3 100 0.1 0 0 1 1 56 0 1 0 7 0 100 0 16
        // 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45
        String reportPacket = inComingData.substring(inComingData.indexOf(":")+ 2);
        String packet[] = reportPacket.split(" ");
        String SINK = packet[2];
        String SENDER = packet[3];
        String HOPS = packet[7];
        LinkedList<String> NEIGHBORS = new LinkedList<>();

        // current sender's neighbors and RSSI
        for (int i = 1; i <= Integer.parseInt(packet[9]); i++) {
            NEIGHBORS.add(packet[9+3*i-2] + "." + packet[9+3*i-1] + "\t" + packet[9+3*i]);
        }

        // add sink and sender
        if (graph.getNode(SINK) == null)
            graph.addNode(SINK);
        if (graph.getNode(SENDER) == null)
            graph.addNode(SENDER);
        // get sender's older neighbors if exist
        //LinkedList<String> olderNeighbors = graph.getNode(SENDER).getAttribute("neighbors");
        // if oderNeighbors !- NEIGHBOR remove all older neighbors
//        if (olderNeighbors != null && !olderNeighbors.equals(NEIGHBORS)) {
//            for (String tmp : olderNeighbors) {
//                graph.removeNode(tmp);
//            }
//        }
        // update neighbors attribute
        if (graph.getNode(SENDER) != null)
            graph.getNode(SENDER).addAttribute("neighbors", NEIGHBORS);
        // add new neighbors and edges
        // test with metric = number of hops from sink
        for (String tmp: NEIGHBORS) {
            String neighbor = tmp.split("\t")[0];
            String rssi = tmp.split("\t")[1];
            graph.addNode(neighbor);
            graph.addEdge(SENDER + "-" + neighbor, SENDER, neighbor );
            if (graph.getEdge(SENDER + "-" + neighbor) != null)
                graph.getEdge(SENDER + "-" + neighbor).addAttribute("length", Integer.parseInt(rssi));
        }

        // create node's label
        for (org.graphstream.graph.Node n : graph) {
            n.addAttribute("ui.label", n.getId());
        }
        // add edge metric
//        for (Edge e : graph.getEachEdge()) {
//            e.addAttribute("length", 1);
//            e.addAttribute("label", "" + (int) e.getNumber("length"));
//        }
        for (Edge e : graph.getEachEdge()) {
            e.addAttribute("label", (int) e.getNumber("length"));
        }
        //buildSPT();
    }

    private synchronized void buildSPT() {
        dijkstra.init(graph);
        dijkstra.setSource(graph.getNode("0.1"));
        dijkstra.compute();
        for (Edge e : graph.getEachEdge()) {
            e.addAttribute("ui.style", "fill-color: #ffffcc;");
        }
        for (Edge edge : dijkstra.getTreeEdges()) {
            edge.addAttribute("ui.style", "fill-color: red;");

        }

    }
    /** return openPath packet */
    // char[] openPath = {1, 19, 0, 1, 0, 2, 5, 100, 0, 1, 0, 0, 1, 0, 2, 0, 3, 0, 4};
    public char[] getOpenPath(String destination) {
        buildSPT();
        String dest = destination;
        List<Node> path = new ArrayList<>();
        if (graph.getNode(dest) != null) {
            for (Node node : dijkstra.getPathNodes(graph.getNode(dest))) {
                path.add(0, node);
            }
        }

        System.out.print("{");
        for (Node n : path) {
            System.out.print(n.getId() + ", ");
        }
        System.out.print("}" + "\n");

        int packetLength = 11 + path.size() * 2;
        ArrayList<String> packetString = new ArrayList<>();
        // add header 1, 19, 0, 1, 0, 2, 5, 100, 0, 1, 0, 0, 1, 0, 2, 0, 3, 0, 4,
        packetString.add("1");
        packetString.add(Integer.toString(packetLength));
        packetString.add("0");
        packetString.add("1");
        packetString.add("0");
        packetString.add("1");
        packetString.add("5");
        packetString.add("90");
        packetString.add("0");
        packetString.add("1");
        packetString.add("0");
        //add path
        for (Node node : path) {
            packetString.add(node.getId().split("\\.")[0]);
            packetString.add(node.getId().split("\\.")[1]);
            node.getEnteringEdgeIterator();
        }

        char[] openPath = new char[packetLength];
        for (int i = 0; i < packetLength; i++) {
            openPath[i] = (char) (Integer.parseInt(packetString.get(i)) + 32);
            //openPath[i] = (char) (Integer.parseInt(packetString.get(i)));
        }
        // print to test openPath
        System.out.print("OpenPath :{");
        for (int i = 0;i < openPath.length; i++){
            System.out.print((int)openPath[i] + " ");
        }
        System.out.print(" }" + "\n");
        return openPath;
    }
    public String getOpenPathString(String destination){
        buildSPT();
        String dest = destination;
        List<Node> path = new ArrayList<>();
        if (graph.getNode(dest) != null) {
            for (Node node : dijkstra.getPathNodes(graph.getNode(dest))) {
                path.add(0, node);
            }
        }

        int packetLength = 11 + path.size() * 2;
        ArrayList<String> packetString = new ArrayList<>();
        // add header
        packetString.add("1");
        packetString.add(Integer.toString(packetLength));
        packetString.add("0");
        packetString.add("1");
        packetString.add("0");
        packetString.add("1");
        packetString.add("5");
        packetString.add("90");
        packetString.add("0");
        packetString.add("1");
        packetString.add("0");
        //add path
        for (Node node : path) {
            packetString.add(node.getId().split("\\.")[0]);
            packetString.add(node.getId().split("\\.")[1]);
            //node.getEnteringEdgeIterator();
        }

        StringBuilder openPath = new StringBuilder();
        for (String s: packetString){
            openPath.append(s + " ");
        }
        // print to test openPath
        System.out.print("OpenPath :{");
        System.out.print(openPath);
        System.out.print(" }" + "\n");
        return String.valueOf(openPath);
    }
    public ArrayList<String> getLeafNodes(){
        ArrayList<String> leafNodes = new ArrayList<>();
        buildSPT();
        for (Node node : graph.getEachNode()) {
            if (!node.getId().equals("0.1")) {
                ArrayList<Node> path = new ArrayList<>();
                for (Node n : dijkstra.getPathNodes(node)){
                    path.add(0, n);
                }
                for (int i = 0; i<path.size()-1; i++){
                    path.get(i).addAttribute("children", path.get(i+1).getId());
                }
            }
        }
        for (Node node : graph.getEachNode()){
            if (node.getAttribute("children") == null){
                node.addAttribute("isLeaf", true);
                node.addAttribute("label", "leaf");
                leafNodes.add(node.getId());
            }
        }
        return leafNodes;
    }
    public ArrayList<String> getAllNodes(){
        ArrayList<String> listNodes = new ArrayList<>();
        for (Node n : graph.getEachNode()){
            listNodes.add(n.getId());
        }
        return listNodes;
    }
}

