package com.infilos;

import org.scalameta.ascii.graph.Graph;
import org.scalameta.ascii.java.GraphBuilder;
import org.scalameta.ascii.java.GraphLayouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread safe directed graph.
 *
 * @param <Node>     node type
 * @param <NodeMeta> node meta info type
 * @param <EdgeMeta> edge meta info type
 */
public class DAGraph<Node, NodeMeta, EdgeMeta> {
    private final static Logger logger = LoggerFactory.getLogger(DAGraph.class);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // node map, key is node, value is node meta info
    private volatile Map<Node, NodeMeta> nodes;

    // edge map. key is source node, value is Map with all the targets and related edges
    private volatile Map<Node, Map<Node, EdgeMeta>> forwardEdges;

    // reversed edge map，key is target nodes, value is Map with sources node and related edges
    private volatile Map<Node, Map<Node, EdgeMeta>> reverseEdges;

    public DAGraph() {
        this.nodes = new HashMap<>();
        this.forwardEdges = new HashMap<>();
        this.reverseEdges = new HashMap<>();
    }

    /**
     * @return all the nodes without meta info
     */
    public Set<Node> allNodes() {
        lock.readLock().lock();

        try {
            return Collections.unmodifiableSet(nodes.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return all the edges without meta info
     */
    public Map<Node, Set<Node>> allEdges() {
        lock.readLock().lock();

        try {
            Map<Node, Set<Node>> edges = new HashMap<>();
            for (Map.Entry<Node, Map<Node, EdgeMeta>> entry : forwardEdges.entrySet()) {
                edges.put(entry.getKey(), entry.getValue().keySet());
            }

            return edges;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String render() {
        GraphBuilder<Node> graphBuilder = new GraphBuilder<>();

        for (Node node : allNodes()) {
            graphBuilder.addVertex(node);
        }
        for (Map.Entry<Node, Set<Node>> edges : allEdges().entrySet()) {
            Node source = edges.getKey();
            for (Node target : edges.getValue()) {
                graphBuilder.addEdge(source, target);
            }
        }

        Graph<Node> graph = graphBuilder.build();
        GraphLayouter<Node> graphLayouter = new GraphLayouter<>();
        graphLayouter.setVertical(false);
        graphLayouter.setCompactify(false);

        return graphLayouter.layout(graph);
    }

    /**
     * add node with meta info
     *
     * @param node node
     * @param meta node meta info
     */
    public void addNode(Node node, NodeMeta meta) {
        lock.writeLock().lock();

        try {
            nodes.put(node, meta);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * add edge with node, prevent add node if node not contains
     *
     * @param source node of edge
     * @param target node of edge
     *
     * @return false if the DAG result a ring
     */
    public boolean addEdge(Node source, Node target) {
        return addEdge(source, target, false);
    }

    /**
     * add edge with node, add node if node not contains
     *
     * @param source     node of origin
     * @param target     node of destination
     * @param createable whether the node needs to be created if it does not exist
     *
     * @return The result of adding an edge. returns false if the DAG result is a ring result
     */
    private boolean addEdge(Node source, Node target, boolean createable) {
        return addEdge(source, target, null, createable);
    }

    /**
     * add edge
     *
     * @param source     node of origin
     * @param target     node of destination
     * @param edge       edge description
     * @param createable whether the node needs to be created if it does not exist
     *
     * @return The result of adding an edge. returns false if the DAG result is a ring result
     */
    public boolean addEdge(Node source, Node target, EdgeMeta edge, boolean createable) {
        lock.writeLock().lock();

        try {

            // Whether an edge can be successfully added(source -> target)
            if (!isLegalAddEdge(source, target, createable)) {
                logger.error("Serious error: add edge({} -> {}) is invalid, cause cycle！", source, target);
                return false;
            }

            addNodeIfAbsent(source, null);
            addNodeIfAbsent(target, null);

            addEdge(source, target, edge, forwardEdges);
            addEdge(target, source, edge, reverseEdges);

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * whether this node is contained
     *
     * @param node is source or target node
     *
     * @return true if contains
     */
    public boolean containsNode(Node node) {
        lock.readLock().lock();

        try {
            return nodes.containsKey(node);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * whether this edge is contained
     *
     * @param source node of edge
     * @param target node of edge
     *
     * @return true if contains
     */
    public boolean containsEdge(Node source, Node target) {
        lock.readLock().lock();

        try {
            Map<Node, EdgeMeta> outEdges = forwardEdges.get(source);

            return outEdges!=null && outEdges.containsKey(target);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * get node meta info
     *
     * @param node node
     *
     * @return node meta info
     */
    public NodeMeta getNode(Node node) {
        lock.readLock().lock();

        try {
            return nodes.get(node);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the number of nodes
     *
     * @return the number of nodes
     */
    public int getNodesCount() {
        lock.readLock().lock();

        try {
            return nodes.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the number of edges
     *
     * @return the number of edges
     */
    public int getEdgesCount() {
        lock.readLock().lock();

        try {
            int count = 0;

            for (Map.Entry<Node, Map<Node, EdgeMeta>> entry : forwardEdges.entrySet()) {
                count += entry.getValue().size();
            }

            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * get the start node of DAG
     *
     * @return the start node of DAG
     */
    public Collection<Node> getBeginNode() {
        lock.readLock().lock();

        try {
            return subtract(nodes.keySet(), reverseEdges.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * get the end node of DAG
     *
     * @return the end node of DAG
     */
    public Collection<Node> getEndNode() {
        lock.readLock().lock();

        try {
            return subtract(nodes.keySet(), forwardEdges.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    private <T> Collection<T> subtract(Set<T> big, Set<T> small) {
        List<T> result = new ArrayList<>();

        for (T node : big) {
            if (!small.contains(node)) {
                result.add(node);
            }
        }

        return result;
    }

    /**
     * Gets all previous nodes of the node
     *
     * @param node node id to be calculated
     *
     * @return all previous nodes of the node
     */
    public Set<Node> getPreviousNodes(Node node) {
        lock.readLock().lock();

        try {
            return getNeighborNodes(node, reverseEdges);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all subsequent nodes of the node
     *
     * @param node node id to be calculated
     *
     * @return all subsequent nodes of the node
     */
    public Set<Node> getSubsequentNodes(Node node) {
        lock.readLock().lock();

        try {
            return getNeighborNodes(node, forwardEdges);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the degree of entry of the node
     *
     * @param node node id
     *
     * @return the degree of entry of the node
     */
    public int getIndegree(Node node) {
        lock.readLock().lock();

        try {
            return getPreviousNodes(node).size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * whether the graph has a ring
     *
     * @return true if has cycle, else return false.
     */
    public boolean hasCycle() {
        lock.readLock().lock();

        try {
            return !topologicalSortImpl().getKey();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Only DAG has a topological sort
     *
     * @return topologically sorted results, returns false if the DAG result is a ring result
     *
     * @throws Exception errors
     */
    public List<Node> topologicalSort() throws Exception {
        lock.readLock().lock();

        try {
            Map.Entry<Boolean, List<Node>> entry = topologicalSortImpl();

            if (entry.getKey()) {
                return entry.getValue();
            }

            throw new Exception("Serious error: graph has cycle!");
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * if tho node does not exist,add this node
     *
     * @param node     node
     * @param nodeInfo node meta info
     */
    private void addNodeIfAbsent(Node node, NodeMeta nodeInfo) {
        if (!containsNode(node)) {
            addNode(node, nodeInfo);
        }
    }

    /**
     * add edge
     *
     * @param fromNode node of origin
     * @param toNode   node of destination
     * @param edge     edge description
     * @param edges    edge set
     */
    private void addEdge(Node fromNode, Node toNode, EdgeMeta edge, Map<Node, Map<Node, EdgeMeta>> edges) {
        edges.putIfAbsent(fromNode, new HashMap<>());
        edges.get(fromNode).put(toNode, edge);
    }

    /**
     * Whether an edge can be successfully added(source -> target) need to determine whether the DAG has cycle
     *
     * @param source     node of origin
     * @param target     node of destination
     * @param createable whether to create a node
     *
     * @return true if added
     */
    private boolean isLegalAddEdge(Node source, Node target, boolean createable) {
        if (source.equals(target)) {
            logger.error("edge source({}) can't equals target({})", source, target);
            return false;
        }

        if (!createable) {
            if (!containsNode(source) || !containsNode(target)) {
                logger.error("edge source({}) or target({}) is not in vertices map", source, target);
                return false;
            }
        }

        // whether an egde can be sucessfully added(from-to), need determine whether the DAG has cycle.
        int verticesCount = getNodesCount();

        Queue<Node> queue = new LinkedList<Node>() {{
            add(target);
        }};

        // if DAG doesn't find source, it's not has cycle
        while (!queue.isEmpty() && (--verticesCount > 0)) {
            Node key = queue.poll();

            for (Node subsenquentNode : getSubsequentNodes(key)) {
                if (subsenquentNode.equals(source)) {
                    return false;
                }

                queue.add(subsenquentNode);
            }
        }

        return true;
    }

    /**
     * Get all neighbor nodes of the node
     *
     * @param node  Node id to be calculated
     * @param edges neighbor edge meta info
     *
     * @return all neighbor nodes of the node
     */
    private Set<Node> getNeighborNodes(Node node, final Map<Node, Map<Node, EdgeMeta>> edges) {
        final Map<Node, EdgeMeta> neighborEdges = edges.get(node);

        if (neighborEdges==null) {
            return Collections.emptySet();
        }

        return neighborEdges.keySet();
    }

    /**
     * <pre>
     * Determine whether there are ring and topological sorting results
     *
     * Directed acyclic graph (DAG) has topological ordering Breadth First Search：
     *  1: Traversal of all the vertices in the graph, the degree of entry is 0 vertex into the queue
     *  2: Poll a vertex in the queue to update its adjacency (minus 1) and queue the adjacency if it is 0 after minus 1
     *  3: Do step 2 until the queue is empty If you cannot traverse all the nodes, it means that the current graph is not a directed acyclic graph. There is no topological sort.
     * </pre>
     *
     * @return key Returns the state if success (acyclic) is true, failure (acyclic) is looped, and value (possibly one of the topological sort results)
     */
    private Map.Entry<Boolean, List<Node>> topologicalSortImpl() {
        // node queue with degree of entry 0
        Queue<Node> zeroIndegreeNodeQueue = new LinkedList<>();
        // save result
        List<Node> topoResultList = new ArrayList<>();
        // save the node whose degree is not 0
        Map<Node, Integer> notZeroIndegreeNodeMap = new HashMap<>();

        // scan all the vertices and push vertex with an entry degree of 0 to queue
        for (Map.Entry<Node, NodeMeta> vertices : nodes.entrySet()) {
            Node node = vertices.getKey();
            int inDegree = getIndegree(node);

            if (inDegree==0) {
                zeroIndegreeNodeQueue.add(node);
                topoResultList.add(node);
            } else {
                notZeroIndegreeNodeMap.put(node, inDegree);
            }
        }

        /*
         * After scanning, there is no node with 0 degree of entry,
         * indicating that there is a ring, and return directly
         */
        if (zeroIndegreeNodeQueue.isEmpty()) {
            return new AbstractMap.SimpleEntry<>(false, topoResultList);
        }

        // The topology algorithm is used to delete nodes with 0 degree of entry and its associated edges
        while (!zeroIndegreeNodeQueue.isEmpty()) {
            Node v = zeroIndegreeNodeQueue.poll();
            // get the neighbor node
            Set<Node> subsequentNodes = getSubsequentNodes(v);

            for (Node subsequentNode : subsequentNodes) {
                Integer degree = notZeroIndegreeNodeMap.get(subsequentNode);

                if (--degree==0) {
                    topoResultList.add(subsequentNode);
                    zeroIndegreeNodeQueue.add(subsequentNode);
                    notZeroIndegreeNodeMap.remove(subsequentNode);
                } else {
                    notZeroIndegreeNodeMap.put(subsequentNode, degree);
                }
            }
        }

        // if notZeroIndegreeNodeMap is empty,there is no ring!
        return new AbstractMap.SimpleEntry<>(
            notZeroIndegreeNodeMap.size()==0,
            topoResultList
        );
    }
}
