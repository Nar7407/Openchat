package com.example.domain.model

import com.example.data.local.MessageEntity

/**
 * Resolved message along the active branch of a conversation tree, including
 * sibling branch metadata for `< 1 / 2 >` switchers.
 */
data class BranchMessageNode(
    val message: MessageEntity,
    val parentKey: Long, // ROOT_PARENT_KEY (-1L) for root messages
    val siblingIds: List<Long>,
    val siblingIndex: Int, // 0-based
    val compareSiblingContent: String? = null,
    val compareSiblingIndex: Int? = null
) {
    val siblingCount: Int get() = siblingIds.size
    val hasBranches: Boolean get() = siblingIds.size > 1
}

object ConversationTree {
    const val ROOT_PARENT_KEY: Long = -1L

    /**
     * Normalizes both v1 linear messages (where every row had parentMessageId = null)
     * and v2 tree-linked messages into a clean parent-key -> ordered children map.
     */
    fun buildChildrenMap(allMessages: List<MessageEntity>): Map<Long, List<MessageEntity>> {
        if (allMessages.isEmpty()) return emptyMap()
        val sorted = allMessages.sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id })
        val validIds = sorted.map { it.id }.toSet()
        val children = linkedMapOf<Long, MutableList<MessageEntity>>()

        var previousId: Long? = null
        var previousWasLegacyNullAssistant = false

        for (msg in sorted) {
            val rawParent = msg.parentMessageId
            val effectiveParent: Long = when {
                rawParent != null && rawParent in validIds -> {
                    previousWasLegacyNullAssistant = false
                    rawParent
                }
                msg.role == "ASSISTANT" && previousId != null -> {
                    // Legacy v1 assistant message had parentMessageId == null; attach to preceding message.
                    previousWasLegacyNullAssistant = true
                    previousId
                }
                previousWasLegacyNullAssistant && previousId != null -> {
                    // Legacy v1 follow-up user message immediately after a legacy null-parent assistant.
                    previousWasLegacyNullAssistant = false
                    previousId
                }
                else -> {
                    previousWasLegacyNullAssistant = false
                    ROOT_PARENT_KEY
                }
            }
            children.getOrPut(effectiveParent) { mutableListOf() }.add(msg)
            previousId = msg.id
        }
        return children
    }

    /**
     * Resolves the currently visible linear branch from root to leaf given per-parent
     * user selections (`parentKey -> selectedChildId`). Defaults to the latest sibling
     * when no explicit selection is stored for a parent slot.
     */
    fun resolveActiveBranch(
        allMessages: List<MessageEntity>,
        selectedChildByParent: Map<Long, Long>
    ): List<BranchMessageNode> {
        val childrenByParent = buildChildrenMap(allMessages)
        val path = mutableListOf<BranchMessageNode>()
        val visited = hashSetOf<Long>()
        var currentParentKey = ROOT_PARENT_KEY

        while (true) {
            val siblings = childrenByParent[currentParentKey].orEmpty()
            if (siblings.isEmpty()) break

            val preferredId = selectedChildByParent[currentParentKey]
            val selectedIndex = siblings.indexOfFirst { it.id == preferredId }
                .takeIf { it >= 0 }
                ?: siblings.lastIndex
            val chosen = siblings[selectedIndex]
            if (!visited.add(chosen.id)) break

            val compareIdx = when {
                selectedIndex > 0 -> selectedIndex - 1
                siblings.size > 1 -> 1
                else -> null
            }
            path += BranchMessageNode(
                message = chosen,
                parentKey = currentParentKey,
                siblingIds = siblings.map { it.id },
                siblingIndex = selectedIndex,
                compareSiblingContent = compareIdx?.let { siblings[it].content },
                compareSiblingIndex = compareIdx
            )
            currentParentKey = chosen.id
        }
        return path
    }

    /**
     * Computes the exact ancestor chain ending at [leafMessageId] (inclusive)
     * so model context only includes the active branch path.
     */
    fun getAncestorChain(
        allMessages: List<MessageEntity>,
        leafMessageId: Long?
    ): List<MessageEntity> {
        if (leafMessageId == null || allMessages.isEmpty()) return emptyList()
        val childrenByParent = buildChildrenMap(allMessages)
        val parentOf = hashMapOf<Long, Long>()
        val byId = allMessages.associateBy { it.id }
        childrenByParent.forEach { (parentKey, kids) ->
            kids.forEach { child -> parentOf[child.id] = parentKey }
        }

        val chain = mutableListOf<MessageEntity>()
        val visited = hashSetOf<Long>()
        var cursor: Long? = leafMessageId
        while (cursor != null && cursor != ROOT_PARENT_KEY && visited.add(cursor)) {
            val msg = byId[cursor] ?: break
            chain += msg
            cursor = parentOf[cursor]
        }
        return chain.reversed()
    }

    /**
     * Returns the effective parent ID (nullable for root) of [messageId] inside [allMessages].
     */
    fun findEffectiveParentId(allMessages: List<MessageEntity>, messageId: Long): Long? {
        val childrenByParent = buildChildrenMap(allMessages)
        childrenByParent.forEach { (parentKey, kids) ->
            if (kids.any { it.id == messageId }) {
                return if (parentKey == ROOT_PARENT_KEY) null else parentKey
            }
        }
        return null
    }
}
