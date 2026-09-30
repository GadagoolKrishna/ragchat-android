package com.ragchat.storage.util

import com.ragchat.api.model.DistanceMetric
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.ln
import kotlin.random.Random

/**
 * Lightweight Hierarchical Navigable Small World (HNSW) graph for fast approximate vector search.
 *
 * Designed for memory-efficient on-device indexing with int8 quantized vectors.
 */
public class HnswIndex(
    public val dimensions: Int,
    public val metric: DistanceMetric = DistanceMetric.COSINE,
    private val m: Int = 16,
    private val efConstruction: Int = 100,
    private val efSearch: Int = 50,
    randomSeed: Long? = null,
) {
    private val mL: Double = 1.0 / ln(m.toDouble())
    private val random = if (randomSeed != null) Random(randomSeed) else Random.Default
    private val rwLock = ReentrantReadWriteLock()

    private val vectors = ConcurrentHashMap<String, QuantizedVector>()
    private val graph = ConcurrentHashMap<String, ConcurrentHashMap<Int, MutableList<String>>>()
    private var entryPointId: String? = null
    private var maxLevel: Int = -1

    /**
     * Inserts a quantized vector into the HNSW graph.
     */
    public fun insert(
        id: String,
        qv: QuantizedVector,
    ) {
        rwLock.write {
            vectors[id] = qv
            val nodeLevels = ConcurrentHashMap<Int, MutableList<String>>()
            graph[id] = nodeLevels

            val level = assignRandomLevel()
            for (l in 0..level) {
                nodeLevels[l] = mutableListOf()
            }

            val ep = entryPointId
            if (ep == null) {
                entryPointId = id
                maxLevel = level
                return@write
            }

            var currObj: String = ep
            for (l in maxLevel downTo level + 1) {
                currObj = findGreedyNext(currObj, qv, l)
            }

            connectLevels(id, qv, currObj, minOf(level, maxLevel), nodeLevels)

            if (level > maxLevel) {
                maxLevel = level
                entryPointId = id
            }
        }
    }

    private fun findGreedyNext(
        startNode: String,
        qv: QuantizedVector,
        level: Int,
    ): String {
        var curr = startNode
        var currDist = distance(qv, vectors[curr]!!)
        val neighbors = graph[curr]?.get(level) ?: return curr
        for (neighbor in neighbors) {
            val nQv = vectors[neighbor] ?: continue
            val d = distance(qv, nQv)
            if (d < currDist) {
                currDist = d
                curr = neighbor
            }
        }
        return curr
    }

    private fun connectLevels(
        id: String,
        qv: QuantizedVector,
        startNode: String,
        topInsertLevel: Int,
        nodeLevels: ConcurrentHashMap<Int, MutableList<String>>,
    ) {
        var enterNode = startNode
        for (l in topInsertLevel downTo 0) {
            val neighbors = searchLevel(enterNode, qv, efConstruction, l)
            val selected = neighbors.take(m)

            for (selectedNeighbor in selected) {
                nodeLevels[l]?.add(selectedNeighbor.id)
                val neighborLevels = graph[selectedNeighbor.id]?.get(l)
                if (neighborLevels != null) {
                    neighborLevels.add(id)
                    pruneNeighbors(selectedNeighbor.id, neighborLevels)
                }
            }
            enterNode = neighbors.firstOrNull()?.id ?: enterNode
        }
    }

    /**
     * Searches for topK nearest neighbors to the query vector.
     */
    public fun search(
        query: QuantizedVector,
        topK: Int,
    ): List<Pair<String, Float>> {
        rwLock.read {
            val ep = entryPointId ?: return emptyList()
            var currObj = ep
            for (l in maxLevel downTo 1) {
                currObj = findGreedyNext(currObj, query, l)
            }
            val ef = maxOf(efSearch, topK)
            val candidates = searchLevel(currObj, query, ef, 0)

            return candidates.take(topK).map { node ->
                val candQv = vectors[node.id]!!
                val sim = QuantizedVectorMath.computeQuantizedSimilarity(query, candQv, metric)
                node.id to sim
            }
        }
    }

    private fun searchLevel(
        enterNode: String,
        query: QuantizedVector,
        ef: Int,
        level: Int,
    ): List<CandidateNode> {
        val context = SearchContext(query, ef, level)
        val enterDist = distance(query, vectors[enterNode]!!)
        val first = CandidateNode(enterNode, enterDist)
        context.candidates.add(first)
        context.result.add(first)
        context.visited.add(enterNode)

        var keepSearching = true
        while (keepSearching && context.candidates.isNotEmpty()) {
            val current = context.candidates.poll()
            if (current == null) {
                keepSearching = false
            } else {
                val furthest = context.result.peek()?.distance ?: Float.MAX_VALUE
                if (current.distance > furthest && context.result.size >= ef) {
                    keepSearching = false
                } else {
                    exploreNeighbors(current.id, context)
                }
            }
        }
        return context.result.sortedBy { it.distance }
    }

    private fun exploreNeighbors(
        nodeId: String,
        ctx: SearchContext,
    ) {
        val neighbors = graph[nodeId]?.get(ctx.level) ?: return
        for (neighbor in neighbors) {
            if (ctx.visited.add(neighbor)) {
                checkAndAddCandidate(neighbor, ctx)
            }
        }
    }

    private fun checkAndAddCandidate(
        neighbor: String,
        ctx: SearchContext,
    ) {
        val nQv = vectors[neighbor] ?: return
        val d = distance(ctx.query, nQv)
        val furthest = ctx.result.peek()?.distance ?: Float.MAX_VALUE
        if (ctx.result.size < ctx.ef || d < furthest) {
            val cand = CandidateNode(neighbor, d)
            ctx.candidates.add(cand)
            ctx.result.add(cand)
            if (ctx.result.size > ctx.ef) {
                ctx.result.poll()
            }
        }
    }

    private fun pruneNeighbors(
        nodeId: String,
        neighbors: MutableList<String>,
    ) {
        val nodeQv = vectors[nodeId] ?: return
        neighbors.sortBy { distance(nodeQv, vectors[it] ?: return@sortBy Float.MAX_VALUE) }
        while (neighbors.size > m) {
            neighbors.removeAt(neighbors.size - 1)
        }
    }

    private fun assignRandomLevel(): Int {
        val r = random.nextDouble().coerceIn(1e-7, 1.0)
        return (-ln(r) * mL).toInt()
    }

    private fun distance(
        q1: QuantizedVector,
        q2: QuantizedVector,
    ): Float {
        val sim = QuantizedVectorMath.computeQuantizedSimilarity(q1, q2, metric)
        return 1f - sim
    }

    private data class CandidateNode(
        val id: String,
        val distance: Float,
    )

    private class SearchContext(
        val query: QuantizedVector,
        val ef: Int,
        val level: Int,
    ) {
        val visited = HashSet<String>()
        val candidates = PriorityQueue<CandidateNode>(compareBy { it.distance })
        val result = PriorityQueue<CandidateNode>(compareByDescending { it.distance })
    }
}
