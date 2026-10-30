package travel.roame.takehome.domain.search

/**
 * Prefix tree over normalised phrases (DOMAIN_RULES S4).
 *
 * Typing "new" walks n → e → w, one node per character, so a lookup costs O(m) for a query of
 * length m, regardless of how many phrases are indexed. Every node stores, precomputed at build
 * time, the ids of all entries whose phrases pass through it, so the matching ids are available
 * as soon as the walk ends. [completions] branches from that node into the full phrases beneath
 * it ("new york", "newark", …).
 */
internal class PrefixTrie private constructor(private val root: Node) {

    private class Node {
        val children = HashMap<Char, Node>()
        val idSet = LinkedHashSet<Int>()
        var ids: IntArray = IntArray(0)
        val labels = LinkedHashSet<String>()
    }

    /** Ids of every entry with a phrase starting with [prefix]; empty when there is none. O(m). */
    fun idsWithPrefix(prefix: String): IntArray = find(prefix)?.ids ?: IntArray(0)

    /**
     * Display labels of the phrases starting with [prefix], shortest phrase first, then
     * alphabetical, without duplicates, at most [limit].
     */
    fun completions(prefix: String, limit: Int = 10): List<String> {
        val start = find(prefix) ?: return emptyList()
        val out = LinkedHashSet<String>()
        var level = listOf(start)
        while (level.isNotEmpty() && out.size < limit) {
            val next = ArrayList<Node>()
            for (node in level) {
                for (label in node.labels) if (out.size < limit) out.add(label)
                node.children.keys.sorted().forEach { next.add(node.children.getValue(it)) }
            }
            level = next
        }
        return out.toList()
    }

    private fun find(prefix: String): Node? {
        if (prefix.isEmpty()) return null
        var node = root
        for (ch in prefix) node = node.children[ch] ?: return null
        return node
    }

    class Builder {
        private val root = Node()

        /**
         * Indexes [phrase] (already normalised) for entry [id]. [label] is what [completions] shows
         * for it: index-only keys such as "york" or "newyork" use the full name "new york".
         * Adding the same entry twice is harmless.
         */
        fun add(phrase: String, id: Int, label: String = phrase): Builder {
            if (phrase.isEmpty()) return this
            var node = root
            for (ch in phrase) {
                node = node.children.getOrPut(ch) { Node() }
                node.idSet.add(id)
            }
            node.labels.add(label)
            return this
        }

        fun build(): PrefixTrie {
            freeze(root)
            return PrefixTrie(root)
        }

        private fun freeze(node: Node) {
            node.ids = node.idSet.toIntArray().apply { sort() }
            node.idSet.clear()
            node.children.values.forEach(::freeze)
        }
    }
}
