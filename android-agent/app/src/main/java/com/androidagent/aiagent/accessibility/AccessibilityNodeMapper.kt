package com.androidagent.aiagent.accessibility

import android.graphics.Rect as AndroidRect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.androidagent.aiagent.agent.Rect
import com.androidagent.aiagent.agent.UiNode


object AccessibilityNodeMapper {

    private const val TAG = "NodeMapper"
    private const val MAX_TEXT_LENGTH = 200
    private const val MAX_DEPTH = 50

    /**
     * Map an [AccessibilityNodeInfo] tree into a flat list of [UiNode] objects.
     * @param root The root of the accessibility node tree. Can be null.
     * @return A list of mapped UI nodes, preserving parent-child relationships via IDs.
     */
    fun mapNodeTree(root: AccessibilityNodeInfo?): List<UiNode> {
        if (root == null) {
            Log.w(TAG, "mapNodeTree called with null root node")
            return emptyList()
        }

        val nodes = mutableListOf<UiNode>()
        val androidRect = AndroidRect()
        traverseNode(root, parentId = null, depth = 0, nodes = nodes, androidRect = androidRect)
        return nodes
    }

    /**
     * Produce a compact, indented text representation of the UI tree
     * suitable for sending to an AI model.
     *
     * Rules applied:
     * - Skip nodes with no useful info at depth > 10
     * - Deduplicate consecutive siblings that share the same text
     * - Stop after [maxNodes] nodes
     *
     * @param nodes The flat list of [UiNode] to serialize.
     * @param maxNodes Maximum number of nodes to include in the output.
     * @return A compact string representation of the UI tree.
     */
    fun serializeCompact(nodes: List<UiNode>, maxNodes: Int = 150): String {
        if (nodes.isEmpty()) return "(empty UI tree)"

        val nodeMap = nodes.associateBy { it.nodeId }
        val sb = StringBuilder(8192)
        var count = 0

        // Find root nodes (nodes with no parent in the list)
        val roots = nodes.filter { it.parentId == null || it.parentId !in nodeMap }

        for (root in roots) {
            if (count >= maxNodes) break
            count = serializeNode(
                node = root,
                nodeMap = nodeMap,
                sb = sb,
                depth = 0,
                count = count,
                maxNodes = maxNodes,
                lastSiblingText = null
            )
        }

        if (count >= maxNodes) {
            sb.appendLine("\n... (truncated at $maxNodes nodes)")
        }

        return sb.toString().trimEnd()
    }

    // ---------------------------------------------------------------------------
    // Recursive traversal
    // ---------------------------------------------------------------------------

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        parentId: String?,
        depth: Int,
        nodes: MutableList<UiNode>,
        androidRect: AndroidRect
    ) {
        if (depth > MAX_DEPTH) return

        try {
            val nodeId = "node_${node.hashCode()}"

            // Gather child IDs
            val childCount = try { node.childCount } catch (_: Exception) { 0 }
            val childIds = mutableListOf<String>()
            for (i in 0 until childCount) {
                try {
                    val child = node.getChild(i)
                    if (child != null) {
                        childIds.add("node_${child.hashCode()}")
                    }
                } catch (_: Exception) {
                    // Child may have become stale
                }
            }

            // Extract text
            val text = try {
                node.text?.toString()?.take(MAX_TEXT_LENGTH)
            } catch (_: Exception) { null }

            // Extract content description
            val contentDescription = try {
                node.contentDescription?.toString()?.take(MAX_TEXT_LENGTH)
            } catch (_: Exception) { null }

            // Extract resource ID
            val resourceId = try {
                node.viewIdResourceName?.toString()
            } catch (_: Exception) { null }

            // Extract class name
            val className = try {
                node.className?.toString()
            } catch (_: Exception) { null }

            // Extract boolean properties
            val isClickable = try { node.isClickable } catch (_: Exception) { false }
            val isEditable = try {
                node.isEditable
            } catch (_: Exception) { false }
            val isScrollable = try { node.isScrollable } catch (_: Exception) { false }
            val isFocusable = try { node.isFocusable } catch (_: Exception) { false }
            val isEnabled = try { node.isEnabled } catch (_: Exception) { true }

            // Extract bounds
            val bounds = try {
                node.getBoundsInScreen(androidRect)
                Rect(
                    left = androidRect.left,
                    top = androidRect.top,
                    right = androidRect.right,
                    bottom = androidRect.bottom
                )
            } catch (_: Exception) {
                Rect(0, 0, 0, 0)
            }

            val uiNode = UiNode(
                nodeId = nodeId,
                className = className,
                text = text,
                contentDescription = contentDescription,
                resourceId = resourceId,
                isClickable = isClickable,
                isEditable = isEditable,
                isScrollable = isScrollable,
                isFocusable = isFocusable,
                isEnabled = isEnabled,
                bounds = bounds,
                parentId = parentId,
                childIds = childIds,
                depth = depth
            )
            nodes.add(uiNode)

            // Recurse into children
            for (i in 0 until childCount) {
                try {
                    val child = node.getChild(i)
                    if (child != null) {
                        traverseNode(
                            node = child,
                            parentId = nodeId,
                            depth = depth + 1,
                            nodes = nodes,
                            androidRect = androidRect
                        )
                        // Only recycle child if we're not going to use it further.
                        // Since we've already extracted all data, it's safe to recycle.
                        child.recycle()
                    }
                } catch (_: Exception) {
                    // Child became stale or was recycled
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error traversing node at depth $depth", e)
        }
    }

    // ---------------------------------------------------------------------------
    // Compact serialization
    // ---------------------------------------------------------------------------

    /**
     * Recursively serialize a node and its children into the StringBuilder.
     * @return The updated node count.
     */
    private fun serializeNode(
        node: UiNode,
        nodeMap: Map<String, UiNode>,
        sb: StringBuilder,
        depth: Int,
        count: Int,
        maxNodes: Int,
        lastSiblingText: String?
    ): Int {
        if (count >= maxNodes) return count

        // Skip nodes with no useful info at depth > 10
        val hasUsefulInfo = hasUsefulInformation(node)
        if (depth > 10 && !hasUsefulInfo) {
            // Still need to count children towards maxNodes indirectly
            return count
        }

        // Deduplicate consecutive siblings with the same text
        val displayText = node.text ?: node.contentDescription
        if (displayText != null && displayText == lastSiblingText && !hasUsefulInfo) {
            // Skip this node, it's a duplicate of the previous sibling
            // But still process its children
            return serializeChildren(node, nodeMap, sb, depth, count, maxNodes)
        }

        // Write indentation
        val indent = "  ".repeat(depth.coerceAtMost(20))

        // Build the node line
        sb.append(indent)
        sb.append(node.nodeId)

        if (!node.className.isNullOrEmpty()) {
            sb.append(" [${node.className?.substringAfterLast('.')}")
        }

        // Append flags
        val flags = mutableListOf<String>()
        if (node.isClickable) flags.add("clickable")
        if (node.isEditable) flags.add("editable")
        if (node.isScrollable) flags.add("scrollable")
        if (node.isFocusable) flags.add("focusable")
        if (!node.isEnabled) flags.add("disabled")

        if (flags.isNotEmpty()) {
            sb.append(" | ${flags.joinToString(",")}")
        }

        if (!node.className.isNullOrEmpty()) {
            sb.append("]")
        }

        // Append text
        if (!node.text.isNullOrEmpty()) {
            sb.append(" text=\"${escapeForCompact(node.text)}\"")
        }

        // Append content description
        if (!node.contentDescription.isNullOrEmpty()) {
            sb.append(" desc=\"${escapeForCompact(node.contentDescription)}\"")
        }

        // Append resource ID (shortened)
        if (!node.resourceId.isNullOrEmpty()) {
            val shortId = node.resourceId.substringAfterLast('/')
            sb.append(" id=$shortId")
        }

        // Append bounds
        val b = node.bounds
        if (!b.isEmpty) {
            sb.append(" bounds=[${b.left},${b.top},${b.right},${b.bottom}]")
        }

        sb.appendLine()

        val newCount = count + 1

        // Recurse into children
        return serializeChildren(node, nodeMap, sb, depth, newCount, maxNodes)
    }

    private fun serializeChildren(
        node: UiNode,
        nodeMap: Map<String, UiNode>,
        sb: StringBuilder,
        depth: Int,
        count: Int,
        maxNodes: Int
    ): Int {
        var currentCount = count
        for (childId in node.childIds) {
            if (currentCount >= maxNodes) break
            val child = nodeMap[childId] ?: continue
            val lastSiblingText = if (currentCount == count) {
                null
            } else {
                // Find the previous sibling's text for dedup check
                val prevSiblingIndex = node.childIds.indexOf(childId) - 1
                if (prevSiblingIndex >= 0) {
                    nodeMap[node.childIds[prevSiblingIndex]]?.text
                        ?: nodeMap[node.childIds[prevSiblingIndex]]?.contentDescription
                } else {
                    null
                }
            }
            currentCount = serializeNode(child, nodeMap, sb, depth + 1, currentCount, maxNodes, lastSiblingText)
        }
        return currentCount
    }

    /**
     * Determine if a node carries meaningful information for the AI model.
     */
    private fun hasUsefulInformation(node: UiNode): Boolean {
        return node.isClickable ||
                node.isEditable ||
                node.isScrollable ||
                !node.text.isNullOrEmpty() ||
                !node.contentDescription.isNullOrEmpty() ||
                !node.resourceId.isNullOrEmpty() ||
                !node.bounds.isEmpty
    }

    /**
     * Escape special characters for compact text representation.
     */
    private fun escapeForCompact(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ")
            .trim()
            .take(120)
    }
}
